package com.rudra.expensetracker.core.sms

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SmsFilterTest {

    private val pipeline = TransactionSmsPipeline()

    private fun reasonFor(sms: RawSms): RejectionReason {
        val result = pipeline.ingest(sms)
        assertThat(result).isInstanceOf(IngestResult.Ignored::class.java)
        return (result as IngestResult.Ignored).reason
    }

    @Test
    fun `an OTP quoting an amount never becomes a transaction`() {
        assertThat(reasonFor(SmsFixtures.OTP)).isEqualTo(RejectionReason.OTP_OR_VERIFICATION)
    }

    @Test
    fun `loan advertisements are rejected`() {
        assertThat(reasonFor(SmsFixtures.PROMOTIONAL_LOAN)).isEqualTo(RejectionReason.PROMOTIONAL)
    }

    @Test
    fun `credit card offers are rejected`() {
        assertThat(reasonFor(SmsFixtures.CREDIT_CARD_OFFER)).isEqualTo(RejectionReason.PROMOTIONAL)
    }

    @Test
    fun `login alerts are rejected`() {
        assertThat(reasonFor(SmsFixtures.LOGIN_ALERT)).isEqualTo(RejectionReason.SECURITY_OR_LOGIN_ALERT)
    }

    @Test
    fun `balance enquiries are rejected`() {
        assertThat(reasonFor(SmsFixtures.BALANCE_ENQUIRY)).isEqualTo(RejectionReason.BALANCE_ENQUIRY_ONLY)
    }

    @Test
    fun `declined payments are rejected`() {
        assertThat(reasonFor(SmsFixtures.DECLINED)).isEqualTo(RejectionReason.DECLINED_OR_FAILED)
    }

    @Test
    fun `collect requests are rejected because no money has moved yet`() {
        assertThat(reasonFor(SmsFixtures.PAYMENT_REQUEST))
            .isEqualTo(RejectionReason.PAYMENT_REQUEST_OR_REMINDER)
    }

    @Test
    fun `a future autopay debit is rejected until it actually happens`() {
        assertThat(reasonFor(SmsFixtures.AUTOPAY_REMINDER))
            .isEqualTo(RejectionReason.PAYMENT_REQUEST_OR_REMINDER)
    }

    @Test
    fun `messages from a personal mobile number are ignored`() {
        assertThat(reasonFor(SmsFixtures.PERSONAL_SMS)).isEqualTo(RejectionReason.NOT_FROM_BANK_SENDER)
    }

    @Test
    fun `every genuine transaction fixture survives the filter`() {
        val genuine = listOf(
            SmsFixtures.INDUSIND_UPI_DEBIT, SmsFixtures.HDFC_UPI_DEBIT, SmsFixtures.SBI_UPI_DEBIT,
            SmsFixtures.ICICI_UPI_DEBIT, SmsFixtures.AXIS_CARD_SPEND, SmsFixtures.KOTAK_UPI_DEBIT,
            SmsFixtures.BOB_UPI_DEBIT, SmsFixtures.IDFC_DEBIT, SmsFixtures.ATM_WITHDRAWAL,
            SmsFixtures.NEFT_TRANSFER_OUT, SmsFixtures.SALARY_CREDIT, SmsFixtures.UPI_CREDIT,
            SmsFixtures.REFUND_CREDIT,
        )
        for (sms in genuine) {
            assertThat(SmsFilter.reject(sms)).isNull()
        }
    }
}
