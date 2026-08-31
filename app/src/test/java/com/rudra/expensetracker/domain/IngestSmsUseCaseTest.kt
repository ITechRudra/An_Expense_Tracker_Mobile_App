package com.rudra.expensetracker.domain

import com.google.common.truth.Truth.assertThat
import com.rudra.expensetracker.core.category.CategorySuggestionEngine
import com.rudra.expensetracker.core.category.DefaultCategories
import com.rudra.expensetracker.core.dedupe.DuplicateDetector
import com.rudra.expensetracker.core.sms.RawSms
import com.rudra.expensetracker.core.sms.TransactionSmsPipeline
import com.rudra.expensetracker.core.sms.TransactionType
import com.rudra.expensetracker.data.repository.AccountRepository
import com.rudra.expensetracker.data.repository.MerchantMappingRepository
import com.rudra.expensetracker.data.repository.TransactionRepository
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

/**
 * End-to-end cover of the path an incoming bank alert actually takes, from raw
 * text to a stored, unconfirmed transaction with a suggestion attached.
 */
class IngestSmsUseCaseTest {

    private val transactionDao = FakeTransactionDao()
    private val accountDao = FakeAccountDao()
    private val mappingDao = FakeMerchantMappingDao()
    private val ingestLog = FakeIngestLogDao()
    private val clock = AppClock()

    private lateinit var ingest: IngestSmsUseCase
    private lateinit var confirm: ConfirmTransactionUseCase
    private lateinit var mappings: MerchantMappingRepository

    private val indusIndSms = RawSms(
        id = "sms-1",
        sender = "AD-INDUSB-S",
        body = "A/C *XX7375 debited by Rs 32.00 towards Q528800175@ybl. RRN:155948254191. " +
            "Avl Bal:11213.98. Not you? Call 18602677777 - IndusInd bank",
        receivedAtEpochMillis = 1_756_000_000_000L,
    )

    @Before
    fun setUp() {
        val transactions = TransactionRepository(transactionDao, clock)
        val accounts = AccountRepository(accountDao, clock)
        mappings = MerchantMappingRepository(mappingDao)
        val engine = CategorySuggestionEngine()

        ingest = IngestSmsUseCase(
            pipeline = TransactionSmsPipeline(),
            duplicateDetector = DuplicateDetector(),
            suggestionEngine = engine,
            transactions = transactions,
            accounts = accounts,
            mappings = mappings,
            ingestLog = ingestLog,
            clock = clock,
        )
        confirm = ConfirmTransactionUseCase(transactions, mappings, engine, clock)
    }

    @Test
    fun `stores a detected transaction unconfirmed with every parsed field`() = runTest {
        val outcome = ingest(indusIndSms)

        assertThat(outcome).isInstanceOf(SmsIngestOutcome.Prompt::class.java)
        val stored = (outcome as SmsIngestOutcome.Prompt).transaction
        assertThat(stored.type).isEqualTo(TransactionType.EXPENSE)
        assertThat(stored.amountMinorUnits).isEqualTo(3200L)
        assertThat(stored.upiId).isEqualTo("Q528800175@ybl")
        assertThat(stored.rrn).isEqualTo("155948254191")
        assertThat(stored.accountTail).isEqualTo("XX7375")
        assertThat(stored.availableBalanceMinorUnits).isEqualTo(1_121_398L)
        assertThat(stored.isConfirmed).isFalse()
        assertThat(stored.categoryId).isNull()
        assertThat(stored.description).isNull()
    }

    @Test
    fun `creates the bank account on first sight and reuses it after`() = runTest {
        ingest(indusIndSms)
        ingest(indusIndSms.copy(id = "sms-2", body = indusIndSms.body.replace("155948254191", "155948254192")))

        assertThat(accountDao.accounts.value).hasSize(1)
        val account = accountDao.accounts.value.single()
        assertThat(account.bankCode).isEqualTo("INDUSIND")
        assertThat(account.displayName).isEqualTo("IndusInd Bank ••••7375")
        // The balance stored is the one the bank stated, never a computed figure.
        assertThat(account.lastReportedBalanceMinorUnits).isEqualTo(1_121_398L)
    }

    @Test
    fun `the same alert delivered twice stores one transaction`() = runTest {
        ingest(indusIndSms)
        val second = ingest(indusIndSms.copy(id = "sms-duplicate"))

        assertThat(second).isInstanceOf(SmsIngestOutcome.Duplicate::class.java)
        assertThat(transactionDao.transactions.value).hasSize(1)
    }

    @Test
    fun `an OTP never becomes a transaction`() = runTest {
        val outcome = ingest(
            indusIndSms.copy(
                id = "sms-otp",
                body = "123456 is your OTP for a transaction of Rs 5000 at AMAZON. Do not share.",
            ),
        )
        assertThat(outcome).isInstanceOf(SmsIngestOutcome.Ignored::class.java)
        assertThat(transactionDao.transactions.value).isEmpty()
    }

    @Test
    fun `confirming a transaction records the description and teaches the payee`() = runTest {
        val prompt = ingest(indusIndSms) as SmsIngestOutcome.Prompt

        val updated = confirm(prompt.transaction.id, DefaultCategories.FOOD, "Chocolate")

        assertThat(updated?.isConfirmed).isTrue()
        assertThat(updated?.categoryId).isEqualTo(DefaultCategories.FOOD)
        assertThat(updated?.description).isEqualTo("Chocolate")
        // The bank's identifier survives the user's description.
        assertThat(updated?.upiId).isEqualTo("Q528800175@ybl")

        val learned = mappings.forKey("q528800175@ybl")
        assertThat(learned).hasSize(1)
        assertThat(learned.single().categoryId).isEqualTo(DefaultCategories.FOOD)
    }

    @Test
    fun `a later payment to a taught payee arrives with a suggestion`() = runTest {
        val first = ingest(indusIndSms) as SmsIngestOutcome.Prompt
        confirm(first.transaction.id, DefaultCategories.FOOD, "College canteen")

        val second = ingest(
            indusIndSms.copy(
                id = "sms-3",
                body = indusIndSms.body.replace("Rs 32.00", "Rs 45.00").replace("155948254191", "155948254999"),
                receivedAtEpochMillis = indusIndSms.receivedAtEpochMillis + 86_400_000L,
            ),
        ) as SmsIngestOutcome.Prompt

        assertThat(second.suggestion?.categoryId).isEqualTo(DefaultCategories.FOOD)
        assertThat(second.suggestion?.description).isEqualTo("College canteen")
        // Suggested, not applied: it is still waiting on the user.
        assertThat(second.transaction.isConfirmed).isFalse()
        assertThat(second.transaction.categoryId).isNull()
    }

    @Test
    fun `the diagnostic log records the verdict but never the message body`() = runTest {
        ingest(indusIndSms)
        val entry = ingestLog.entries.single()
        assertThat(entry.outcome).isEqualTo("PARSED")
        assertThat(entry.sender).isEqualTo("AD-INDUSB-S")
        // Only the length is kept, so no bank text can leak from the log.
        assertThat(entry.bodyLength).isEqualTo(indusIndSms.body.length)
    }
}
