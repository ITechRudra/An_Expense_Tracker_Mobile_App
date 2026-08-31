package com.rudra.expensetracker.core.sms

import java.util.Locale

/**
 * Rejects messages that must never become transactions.
 *
 * This runs before parsing. It is intentionally biased towards rejection: a
 * missed transaction costs the user one manual entry, whereas a phantom
 * transaction from an OTP or an offer corrupts their reports and erodes trust
 * in every number the app shows.
 */
object SmsFilter {

    private val OTP_MARKERS = listOf(
        "otp", "one time password", "one-time password", "verification code",
        "login code", "security code", "auth code", "passcode",
    )

    private val FAILURE_MARKERS = listOf(
        "declined", "failed", "unsuccessful", "not successful", "could not be processed",
        "has been rejected", "was rejected", "insufficient balance", "insufficient funds",
        "transaction timed out",
    )

    private val REQUEST_MARKERS = listOf(
        "has requested", "is requesting", "collect request", "payment request",
        "requesting money", "please pay", "pay now", "is due", "due on", "overdue",
        "payment reminder", "kindly pay", "outstanding amount", "minimum amount due",
        "total amount due", "autopay is scheduled", "will be debited on",
    )

    private val PROMO_MARKERS = listOf(
        "pre-approved", "preapproved", "pre approved", "personal loan", "loan offer",
        "instant loan", "apply now", "click here", "limited period", "limited time",
        "cashback up to", "upto", "offer ends", "hurry", "t&c apply", "terms apply",
        "congratulations", "you are eligible", "eligible for a", "avail ", "discount",
        "lowest interest", "emi offer", "credit card offer", "lifetime free",
        "download the app", "refer and earn", "sale is live", "flat ",
    )

    private val SECURITY_MARKERS = listOf(
        "logged in", "login attempt", "signed in", "new device", "password has been changed",
        "pin has been changed", "mpin", "sim swap", "profile was updated", "beneficiary added",
        "registered successfully", "has been activated", "kyc",
    )

    private val BALANCE_ONLY_MARKERS = listOf(
        "balance in your account", "your account balance", "avl bal in",
        "balance enquiry", "closing balance for",
    )

    /**
     * @return the reason this message must be ignored, or null if it may proceed
     *         to the parsers.
     */
    fun reject(sms: RawSms): RejectionReason? {
        if (!BankRegistry.looksLikeServiceSender(sms.sender)) return RejectionReason.NOT_FROM_BANK_SENDER

        val lower = sms.body.lowercase(Locale.ROOT)
        val hasDirection = Extractors.direction(sms.body) != null

        // OTP wins outright: "OTP for txn of Rs 500 at AMAZON" is not a transaction,
        // it is a message about a transaction that has not happened yet.
        if (OTP_MARKERS.any { containsToken(lower, it) }) return RejectionReason.OTP_OR_VERIFICATION

        if (FAILURE_MARKERS.any { lower.contains(it) }) return RejectionReason.DECLINED_OR_FAILED
        if (REQUEST_MARKERS.any { lower.contains(it) }) return RejectionReason.PAYMENT_REQUEST_OR_REMINDER
        if (PROMO_MARKERS.any { lower.contains(it) }) return RejectionReason.PROMOTIONAL

        if (!hasDirection) {
            if (BALANCE_ONLY_MARKERS.any { lower.contains(it) } ||
                Extractors.availableBalance(sms.body) != null
            ) {
                return RejectionReason.BALANCE_ENQUIRY_ONLY
            }
            if (SECURITY_MARKERS.any { lower.contains(it) }) return RejectionReason.SECURITY_OR_LOGIN_ALERT
            return RejectionReason.NO_DIRECTION_FOUND
        }

        // A security notice can also carry a direction word ("your card was used");
        // only reject when there is no amount to anchor a real transaction.
        if (SECURITY_MARKERS.any { lower.contains(it) } && Extractors.amount(sms.body) == null) {
            return RejectionReason.SECURITY_OR_LOGIN_ALERT
        }
        return null
    }

    /** Word-boundary match so "otp" does not fire inside "adoption". */
    private fun containsToken(haystack: String, token: String): Boolean =
        Regex("\\b${Regex.escape(token)}\\b").containsMatchIn(haystack)
}
