package com.rudra.expensetracker.core.category

import com.google.common.truth.Truth.assertThat
import com.rudra.expensetracker.core.sms.IngestResult
import com.rudra.expensetracker.core.sms.ParsedTransaction
import com.rudra.expensetracker.core.sms.RawSms
import com.rudra.expensetracker.core.sms.SmsFixtures
import com.rudra.expensetracker.core.sms.TransactionSmsPipeline
import org.junit.Test

class CategorySuggestionEngineTest {

    private val pipeline = TransactionSmsPipeline()
    private val engine = CategorySuggestionEngine()

    private fun parse(sms: RawSms): ParsedTransaction =
        (pipeline.ingest(sms) as IngestResult.Transaction).parsed

    @Test
    fun `suggests Food for a well known delivery merchant`() {
        val suggestion = engine.suggest(parse(SmsFixtures.HDFC_UPI_DEBIT), emptyList())
        assertThat(suggestion?.categoryId).isEqualTo(DefaultCategories.FOOD)
        assertThat(suggestion?.source).isEqualTo(SuggestionSource.KEYWORD)
    }

    @Test
    fun `a learned mapping beats the built-in keyword rule`() {
        val parsed = parse(SmsFixtures.HDFC_UPI_DEBIT)
        val learned = MerchantMapping(
            key = engine.payeeKey(parsed),
            categoryId = DefaultCategories.WORK,
            description = "Team lunch",
            hitCount = 3,
            lastUsedAtEpochMillis = 1_756_000_000_000L,
        )
        val suggestion = engine.suggest(parsed, listOf(learned))
        assertThat(suggestion?.categoryId).isEqualTo(DefaultCategories.WORK)
        assertThat(suggestion?.description).isEqualTo("Team lunch")
        assertThat(suggestion?.source).isEqualTo(SuggestionSource.LEARNED)
    }

    @Test
    fun `a repeatedly confirmed mapping is more confident than a one-off`() {
        val parsed = parse(SmsFixtures.INDUSIND_UPI_DEBIT)
        val key = engine.payeeKey(parsed)
        fun mapping(hits: Int) = MerchantMapping(key, DefaultCategories.FOOD, "College Canteen", hits, 1L)

        assertThat(engine.suggest(parsed, listOf(mapping(1)))!!.confidence).isLessThan(0.7f)
        assertThat(engine.suggest(parsed, listOf(mapping(5)))!!.confidence).isAtLeast(0.9f)
    }

    @Test
    fun `an opaque UPI id gets no suggestion until the user teaches one`() {
        val parsed = parse(SmsFixtures.INDUSIND_UPI_DEBIT)
        assertThat(engine.suggest(parsed, emptyList())).isNull()

        val taught = MerchantMapping(
            engine.payeeKey(parsed), DefaultCategories.FOOD, "College Canteen", 1, 1L,
        )
        assertThat(engine.suggest(parsed, listOf(taught))?.description).isEqualTo("College Canteen")
    }

    @Test
    fun `the payee key is stable across two alerts naming the same VPA`() {
        val a = parse(SmsFixtures.INDUSIND_UPI_DEBIT)
        val b = parse(
            SmsFixtures.sms(
                "AD-INDUSB-S",
                "A/C *XX7375 debited by Rs 55.00 towards Q528800175@ybl. RRN:155948254777. Avl Bal:11158.98.",
                id = "sms-x",
            ),
        )
        assertThat(engine.payeeKey(a)).isEqualTo(engine.payeeKey(b))
    }

    @Test
    fun `income is never keyword-guessed into an expense category`() {
        val parsed = parse(SmsFixtures.REFUND_CREDIT)
        assertThat(engine.suggest(parsed, emptyList())).isNull()
    }
}
