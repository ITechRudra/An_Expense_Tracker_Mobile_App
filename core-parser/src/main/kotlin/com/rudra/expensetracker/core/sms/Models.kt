package com.rudra.expensetracker.core.sms

import com.rudra.expensetracker.core.money.Money
import java.time.LocalDateTime

/** A single inbound SMS handed to the parsing pipeline. Platform-free by design. */
data class RawSms(
    val id: String,
    val sender: String,
    val body: String,
    val receivedAtEpochMillis: Long,
)

enum class TransactionType { EXPENSE, INCOME }

enum class PaymentMethod {
    UPI,
    DEBIT_CARD,
    CREDIT_CARD,
    BANK_TRANSFER,
    ATM,
    CASH,
    OTHER,
}

/**
 * Why a message was not turned into a transaction. Surfaced in the debug screen
 * so an unrecognised bank format is diagnosable without reading logs.
 */
enum class RejectionReason {
    NOT_FROM_BANK_SENDER,
    OTP_OR_VERIFICATION,
    PROMOTIONAL,
    BALANCE_ENQUIRY_ONLY,
    SECURITY_OR_LOGIN_ALERT,
    PAYMENT_REQUEST_OR_REMINDER,
    NO_AMOUNT_FOUND,
    NO_DIRECTION_FOUND,
    DECLINED_OR_FAILED,
    UNRECOGNISED_FORMAT,
}

/**
 * Everything the parser could recover from one SMS.
 *
 * [merchant]/[upiId] describe who the bank says was paid; the user's own
 * "what was this for" answer is stored separately on the transaction record and
 * never overwrites these fields.
 */
data class ParsedTransaction(
    val type: TransactionType,
    val amount: Money,
    val bankCode: String?,
    val bankName: String?,
    val accountTail: String?,
    val upiId: String? = null,
    val merchant: String? = null,
    val counterparty: String? = null,
    val referenceId: String? = null,
    val rrn: String? = null,
    val availableBalance: Money? = null,
    val paymentMethod: PaymentMethod = PaymentMethod.OTHER,
    val occurredAt: LocalDateTime? = null,
    val confidence: Float = 0f,
    val parserId: String = "",
    val smsId: String = "",
    val smsSender: String = "",
    val receivedAtEpochMillis: Long = 0L,
) {
    /** Best single label to show before the user has said what it was for. */
    val displayCounterparty: String?
        get() = merchant ?: counterparty ?: upiId
}

/** Outcome of running one SMS through the pipeline. */
sealed interface IngestResult {

    /** Confidently a transaction. Safe to prompt the user about. */
    data class Transaction(val parsed: ParsedTransaction) : IngestResult

    /**
     * Looks financial but the parse is incomplete or weakly signalled. Never
     * auto-saved; offered in a "needs review" queue instead.
     */
    data class NeedsReview(
        val parsed: ParsedTransaction,
        val missing: List<String>,
    ) : IngestResult

    data class Ignored(val reason: RejectionReason, val detail: String? = null) : IngestResult
}

/** Last four digits of the account/card mask, for grouping and matching. */
val ParsedTransaction.accountLast4: String?
    get() = accountTail?.filter { it.isDigit() }?.takeLast(4)?.takeIf { it.length == 4 }
