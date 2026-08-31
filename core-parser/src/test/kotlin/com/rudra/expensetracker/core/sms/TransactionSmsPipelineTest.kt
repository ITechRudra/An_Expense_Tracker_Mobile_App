package com.rudra.expensetracker.core.sms

import com.google.common.truth.Truth.assertThat
import com.rudra.expensetracker.core.money.Money
import org.junit.Test

class TransactionSmsPipelineTest {

    private val pipeline = TransactionSmsPipeline()

    private fun parse(sms: RawSms): ParsedTransaction {
        val result = pipeline.ingest(sms)
        assertThat(result).isInstanceOf(IngestResult.Transaction::class.java)
        return (result as IngestResult.Transaction).parsed
    }

    // The scenario specified end-to-end in the product brief.
    @Test
    fun `parses the IndusInd UPI debit exactly as specified`() {
        val p = parse(SmsFixtures.INDUSIND_UPI_DEBIT)

        assertThat(p.type).isEqualTo(TransactionType.EXPENSE)
        assertThat(p.amount).isEqualTo(Money.ofBigDecimal(java.math.BigDecimal("32.00")))
        assertThat(p.accountTail).isEqualTo("XX7375")
        assertThat(p.upiId).isEqualTo("Q528800175@ybl")
        assertThat(p.rrn).isEqualTo("155948254191")
        assertThat(p.availableBalance).isEqualTo(Money.ofBigDecimal(java.math.BigDecimal("11213.98")))
        assertThat(p.bankName).isEqualTo("IndusInd Bank")
        assertThat(p.paymentMethod).isEqualTo(PaymentMethod.UPI)
        assertThat(p.parserId).isEqualTo("indusind")
    }

    @Test
    fun `does not mistake the helpline number for the amount or reference`() {
        val p = parse(SmsFixtures.INDUSIND_UPI_DEBIT)
        assertThat(p.amount.minorUnits).isEqualTo(3200L)
        assertThat(p.rrn).doesNotContain("18602677777")
        assertThat(p.referenceId ?: "").doesNotContain("18602677777")
    }

    @Test
    fun `parses HDFC UPI debit`() {
        val p = parse(SmsFixtures.HDFC_UPI_DEBIT)
        assertThat(p.type).isEqualTo(TransactionType.EXPENSE)
        assertThat(p.amount.minorUnits).isEqualTo(45_000L)
        assertThat(p.bankCode).isEqualTo("HDFC")
        assertThat(p.accountTail).isEqualTo("X1234")
        assertThat(p.merchant).isEqualTo("ZOMATO LTD")
        assertThat(p.referenceId).isEqualTo("512345678901")
    }

    @Test
    fun `parses SBI UPI debit written without a currency symbol`() {
        val p = parse(SmsFixtures.SBI_UPI_DEBIT)
        assertThat(p.type).isEqualTo(TransactionType.EXPENSE)
        assertThat(p.amount.minorUnits).isEqualTo(15_000L)
        assertThat(p.bankCode).isEqualTo("SBI")
        assertThat(p.accountTail).isEqualTo("X8912")
    }

    @Test
    fun `parses ICICI debit where the merchant follows a semicolon`() {
        val p = parse(SmsFixtures.ICICI_UPI_DEBIT)
        assertThat(p.type).isEqualTo(TransactionType.EXPENSE)
        assertThat(p.amount.minorUnits).isEqualTo(125_000L)
        assertThat(p.merchant).isEqualTo("BIGBASKET")
        assertThat(p.accountTail).isEqualTo("XX4455")
    }

    @Test
    fun `parses Axis card spend and reads the merchant before the limit clause`() {
        val p = parse(SmsFixtures.AXIS_CARD_SPEND)
        assertThat(p.type).isEqualTo(TransactionType.EXPENSE)
        assertThat(p.amount.minorUnits).isEqualTo(249_900L)
        assertThat(p.merchant).isEqualTo("MYNTRA DESIGNS")
        assertThat(p.accountTail).isEqualTo("XX9012")
    }

    @Test
    fun `parses Kotak debit`() {
        val p = parse(SmsFixtures.KOTAK_UPI_DEBIT)
        assertThat(p.type).isEqualTo(TransactionType.EXPENSE)
        assertThat(p.amount.minorUnits).isEqualTo(8_500L)
        assertThat(p.upiId).isEqualTo("chaiwala@okaxis")
        assertThat(p.accountTail).isEqualTo("X6677")
    }

    // Bank of Baroda words one payment as both a debit and a credit; the debit
    // comes first and is the side that belongs to this account.
    @Test
    fun `treats a debited-and-credited message as an expense`() {
        val p = parse(SmsFixtures.BOB_UPI_DEBIT)
        assertThat(p.type).isEqualTo(TransactionType.EXPENSE)
        assertThat(p.amount.minorUnits).isEqualTo(60_000L)
        assertThat(p.upiId).isEqualTo("rent.owner@okhdfcbank")
    }

    @Test
    fun `parses IDFC debit with an Info merchant and a balance`() {
        val p = parse(SmsFixtures.IDFC_DEBIT)
        assertThat(p.amount.minorUnits).isEqualTo(19_900L)
        assertThat(p.merchant).isEqualTo("NETFLIX SUBSCRIPTION")
        assertThat(p.availableBalance?.minorUnits).isEqualTo(842_055L)
    }

    @Test
    fun `detects ATM withdrawals as an ATM expense`() {
        val p = parse(SmsFixtures.ATM_WITHDRAWAL)
        assertThat(p.type).isEqualTo(TransactionType.EXPENSE)
        assertThat(p.amount.minorUnits).isEqualTo(200_000L)
        assertThat(p.paymentMethod).isEqualTo(PaymentMethod.ATM)
    }

    @Test
    fun `detects NEFT transfers as a bank transfer`() {
        val p = parse(SmsFixtures.NEFT_TRANSFER_OUT)
        assertThat(p.type).isEqualTo(TransactionType.EXPENSE)
        assertThat(p.amount.minorUnits).isEqualTo(1_000_000L)
        assertThat(p.paymentMethod).isEqualTo(PaymentMethod.BANK_TRANSFER)
    }

    // ------------------------------------------------------------- credits

    @Test
    fun `parses a salary credit as income`() {
        val p = parse(SmsFixtures.SALARY_CREDIT)
        assertThat(p.type).isEqualTo(TransactionType.INCOME)
        assertThat(p.amount.minorUnits).isEqualTo(4_500_000L)
        assertThat(p.availableBalance?.minorUnits).isEqualTo(5_621_398L)
    }

    @Test
    fun `parses a UPI credit as income`() {
        val p = parse(SmsFixtures.UPI_CREDIT)
        assertThat(p.type).isEqualTo(TransactionType.INCOME)
        assertThat(p.amount.minorUnits).isEqualTo(50_000L)
        assertThat(p.upiId).isEqualTo("friend@okicici")
    }

    @Test
    fun `parses a refund as income`() {
        val p = parse(SmsFixtures.REFUND_CREDIT)
        assertThat(p.type).isEqualTo(TransactionType.INCOME)
        assertThat(p.amount.minorUnits).isEqualTo(79_900L)
    }

    // --------------------------------------------------------- confidence

    @Test
    fun `a fully identified transaction scores high confidence`() {
        assertThat(parse(SmsFixtures.INDUSIND_UPI_DEBIT).confidence).isAtLeast(0.9f)
    }

    @Test
    fun `an unrecognised bank without an account is queued for review`() {
        val result = pipeline.ingest(
            SmsFixtures.sms("AD-NEWBNK-S", "Rs 500.00 debited for your purchase."),
        )
        assertThat(result).isInstanceOf(IngestResult.NeedsReview::class.java)
        val review = result as IngestResult.NeedsReview
        assertThat(review.missing).containsExactly("bank", "account")
        assertThat(review.parsed.amount.minorUnits).isEqualTo(50_000L)
    }
}
