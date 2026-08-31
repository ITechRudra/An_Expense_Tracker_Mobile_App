package com.rudra.expensetracker.core.dedupe

import com.google.common.truth.Truth.assertThat
import com.rudra.expensetracker.core.sms.IngestResult
import com.rudra.expensetracker.core.sms.RawSms
import com.rudra.expensetracker.core.sms.SmsFixtures
import com.rudra.expensetracker.core.sms.TransactionSmsPipeline
import com.rudra.expensetracker.core.sms.TransactionType
import org.junit.Test

class DuplicateDetectorTest {

    private val pipeline = TransactionSmsPipeline()
    private val detector = DuplicateDetector()

    private fun identity(sms: RawSms): TransactionIdentity {
        val result = pipeline.ingest(sms) as IngestResult.Transaction
        return TransactionFingerprint.identity(result.parsed)
    }

    @Test
    fun `the identical SMS delivered twice is a duplicate`() {
        val first = identity(SmsFixtures.INDUSIND_UPI_DEBIT)
        val again = identity(SmsFixtures.INDUSIND_UPI_DEBIT.copy(id = "sms-2"))
        assertThat(detector.check(again, listOf(first))).isInstanceOf(DuplicateVerdict.Duplicate::class.java)
    }

    @Test
    fun `the same RRN in a differently worded alert is a duplicate`() {
        val first = identity(SmsFixtures.INDUSIND_UPI_DEBIT)
        val reworded = identity(
            SmsFixtures.sms(
                sender = "AD-INDUSB-S",
                id = "sms-99",
                body = "Rs 32.00 was debited from your IndusInd A/C XX7375. RRN:155948254191.",
                receivedAt = SmsFixtures.INDUSIND_UPI_DEBIT.receivedAtEpochMillis + 90_000,
            ),
        )
        val verdict = detector.check(reworded, listOf(first))
        assertThat(verdict).isInstanceOf(DuplicateVerdict.Duplicate::class.java)
        assertThat((verdict as DuplicateVerdict.Duplicate).rule).isEqualTo(DuplicateDetector.RULE_STRONG_ID)
    }

    @Test
    fun `a delayed re-send with no reference is caught by amount payee and window`() {
        val base = TransactionIdentity(
            strongIds = emptySet(),
            fuzzyKey = TransactionFingerprint.fuzzyKey(TransactionType.EXPENSE, 3200, "7375", "Q528800175@ybl"),
            amountMinorUnits = 3200,
            occurredAtEpochMillis = 1_756_000_000_000L,
            hasCounterparty = true,
        )
        val delayed = base.copy(occurredAtEpochMillis = base.occurredAtEpochMillis + 2 * 60 * 60 * 1000)
        val verdict = detector.check(delayed, listOf(base))
        assertThat(verdict).isInstanceOf(DuplicateVerdict.Duplicate::class.java)
        assertThat((verdict as DuplicateVerdict.Duplicate).rule).isEqualTo(DuplicateDetector.RULE_FUZZY)
    }

    @Test
    fun `two genuinely separate payments to the same shop on different days are kept`() {
        val base = TransactionIdentity(
            strongIds = emptySet(),
            fuzzyKey = TransactionFingerprint.fuzzyKey(TransactionType.EXPENSE, 3200, "7375", "Q528800175@ybl"),
            amountMinorUnits = 3200,
            occurredAtEpochMillis = 1_756_000_000_000L,
            hasCounterparty = true,
        )
        val nextDay = base.copy(occurredAtEpochMillis = base.occurredAtEpochMillis + 24 * 60 * 60 * 1000)
        assertThat(detector.check(nextDay, listOf(base))).isEqualTo(DuplicateVerdict.Unique)
    }

    @Test
    fun `different amounts to the same payee are never duplicates`() {
        val a = identity(SmsFixtures.INDUSIND_UPI_DEBIT)
        val b = identity(
            SmsFixtures.sms(
                sender = "AD-INDUSB-S",
                id = "sms-3",
                body = "A/C *XX7375 debited by Rs 64.00 towards Q528800175@ybl. RRN:155948254999. Avl Bal:11149.98.",
            ),
        )
        assertThat(detector.check(b, listOf(a))).isEqualTo(DuplicateVerdict.Unique)
    }

    @Test
    fun `a debit and a credit of the same amount are not duplicates`() {
        val debit = TransactionFingerprint.fuzzyKey(TransactionType.EXPENSE, 50_000, "1234", "friend@okicici")
        val credit = TransactionFingerprint.fuzzyKey(TransactionType.INCOME, 50_000, "1234", "friend@okicici")
        assertThat(debit).isNotEqualTo(credit)
    }

    @Test
    fun `counterparty normalisation ignores case spacing and punctuation`() {
        assertThat(TransactionFingerprint.normaliseCounterparty("ZOMATO LTD."))
            .isEqualTo(TransactionFingerprint.normaliseCounterparty("zomato  ltd"))
    }
}
