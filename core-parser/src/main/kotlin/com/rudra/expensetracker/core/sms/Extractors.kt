package com.rudra.expensetracker.core.sms

import com.rudra.expensetracker.core.money.Money
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Field-level extraction shared by every bank parser.
 *
 * Each extractor is deliberately conservative: returning null is always
 * preferable to returning a plausible-looking wrong value, because a wrong
 * amount silently corrupts the user's ledger while a null merely asks them.
 */
object Extractors {

    private const val CURRENCY = "(?:INR|Rs\\.?|RS\\.?|₹)"
    /**
     * Indian-grouped ("1,23,456.78") or plain ("2000.00") figures.
     *
     * The grouped branch requires a comma: with `*` instead of `+`, ordered
     * alternation matched "2000.00" as the three digits "200" and silently
     * produced a hundredfold-wrong amount.
     */
    private const val NUMBER = "\\d{1,3}(?:,\\d{2,3})+(?:\\.\\d{1,2})?|\\d+(?:\\.\\d{1,2})?"

    private val DEBIT_WORDS = listOf(
        "debited", "debit", "spent", "withdrawn", "withdrawal", "paid", "payment of",
        "purchase", "deducted", "transferred to", "trf to", "sent to", "sent", "charged",
    )
    private val CREDIT_WORDS = listOf(
        "credited", "credit", "received", "deposited", "refunded", "refund of",
        "added to", "transferred from", "trf from",
    )

    private val BALANCE_CONTEXT = Regex(
        "(?i)(?:avl|avbl|available|a/c|ac|acct|account|closing|clsg|clr|cleared|total|net|current)\\s*" +
            "(?:bal|balance)\\b[^0-9₹]{0,12}(?:$CURRENCY)?\\s*($NUMBER)"
    )
    private val BALANCE_SUFFIX = Regex(
        "(?i)\\bbal(?:ance)?\\b\\s*(?:is|:|-)?\\s*(?:$CURRENCY)?\\s*($NUMBER)"
    )

    // ---------------------------------------------------------------- amount

    private val ACTION_VERB =
        "\\b(?:" + (DEBIT_WORDS + CREDIT_WORDS).joinToString("|") { Regex.escape(it) } + ")\\b"
    private const val ACTION_LINK = "(?:\\s+(?:by|for|with|of|amount|amt|a\\s+sum\\s+of|:))?\\s*"

    private val AMOUNT_AFTER_ACTION = Regex(
        "(?i)$ACTION_VERB$ACTION_LINK(?:$CURRENCY)\\s*($NUMBER)"
    )

    /**
     * Same shape but with the currency token optional, for issuers such as SBI
     * that write "debited by 150.0". Only tried after every currency-anchored
     * pattern has failed, and the figure may not run into a date separator.
     */
    private val AMOUNT_AFTER_ACTION_NO_CURRENCY = Regex(
        "(?i)$ACTION_VERB$ACTION_LINK($NUMBER)(?![\\d/-])"
    )
    private val AMOUNT_BEFORE_ACTION = Regex(
        "(?i)(?:$CURRENCY)\\s*($NUMBER)\\s*(?:\\S+\\s+){0,3}?" + ACTION_VERB
    )
    private val AMOUNT_ANY = Regex("(?i)(?:$CURRENCY)\\s*($NUMBER)")

    /**
     * Pulls the transaction amount, preferring an amount that sits next to the
     * debit/credit verb over any other currency figure in the message.
     * Balance figures are masked out first so "Avl Bal:11213.98" can never win.
     */
    fun amount(body: String): Money? {
        val masked = maskBalances(body)
        AMOUNT_AFTER_ACTION.find(masked)?.groupValues?.get(1)?.let { m ->
            Money.parseOrNull(m)?.let { return it }
        }
        AMOUNT_BEFORE_ACTION.find(masked)?.groupValues?.get(1)?.let { m ->
            Money.parseOrNull(m)?.let { return it }
        }
        AMOUNT_ANY.find(masked)?.groupValues?.get(1)?.let { m ->
            Money.parseOrNull(m)?.let { return it }
        }
        AMOUNT_AFTER_ACTION_NO_CURRENCY.find(masked)?.groupValues?.get(1)?.let { m ->
            Money.parseOrNull(m)?.let { return it }
        }
        return null
    }

    /** Replaces balance figures with spaces so amount scanning cannot pick them up. */
    private fun maskBalances(body: String): String {
        var out = body
        for (regex in listOf(BALANCE_CONTEXT, BALANCE_SUFFIX)) {
            out = regex.replace(out) { match -> " ".repeat(match.value.length) }
        }
        return out
    }

    // --------------------------------------------------------------- balance

    fun availableBalance(body: String): Money? {
        val match = BALANCE_CONTEXT.find(body) ?: BALANCE_SUFFIX.find(body) ?: return null
        return Money.parseOrNull(match.groupValues[1])
    }

    // ------------------------------------------------------------- direction

    /**
     * Decides debit vs credit from whichever verb appears first, since Indian
     * bank copy tends to lead with the action and trail with the balance.
     */
    fun direction(body: String): TransactionType? {
        val lower = body.lowercase(Locale.ROOT)
        val debitAt = DEBIT_WORDS.mapNotNull { indexOfWord(lower, it) }.minOrNull()
        val creditAt = CREDIT_WORDS.mapNotNull { indexOfWord(lower, it) }.minOrNull()
        return when {
            debitAt != null && creditAt != null -> if (debitAt <= creditAt) TransactionType.EXPENSE else TransactionType.INCOME
            debitAt != null -> TransactionType.EXPENSE
            creditAt != null -> TransactionType.INCOME
            else -> null
        }
    }

    private fun indexOfWord(haystack: String, word: String): Int? {
        val idx = Regex("\\b${Regex.escape(word)}\\b").find(haystack)?.range?.first
        return idx?.takeIf { it >= 0 }
    }

    // --------------------------------------------------------------- account

    private val ACCOUNT_PATTERNS = listOf(
        Regex("(?i)\\b(?:a/c|a/c no|acct|account|ac)\\b[^A-Za-z0-9]{0,4}((?:x|\\*){1,8}\\d{3,6})"),
        Regex("(?i)\\b(?:a/c|a/c no|acct|account|ac)\\b[^A-Za-z0-9]{0,4}(?:no\\.?|number)?[^A-Za-z0-9]{0,4}(\\d{4})\\b"),
        Regex("(?i)\\b(?:card|credit card|debit card)\\b[^A-Za-z0-9]{0,6}(?:ending(?:\\s+with)?|no\\.?|number)?[^A-Za-z0-9]{0,4}((?:x|\\*){2,}\\d{3,6})"),
        Regex("(?i)\\bending(?:\\s+with)?\\b[^A-Za-z0-9]{0,4}(\\d{4})\\b"),
        Regex("((?:X|\\*){2,}\\d{3,6})"),
    )

    /**
     * Returns the masked account/card token as printed by the bank, e.g. "XX7375",
     * uppercased and stripped of leading punctuation.
     */
    fun accountTail(body: String): String? {
        for (pattern in ACCOUNT_PATTERNS) {
            val raw = pattern.find(body)?.groupValues?.get(1) ?: continue
            val cleaned = raw.trim().trimStart('*', '-', ' ').uppercase(Locale.ROOT)
            if (cleaned.any { it.isDigit() }) return cleaned
        }
        return null
    }

    // ------------------------------------------------------------------- UPI

    private val UPI_ID = Regex("\\b([A-Za-z0-9][A-Za-z0-9._-]{1,64}@[A-Za-z]{2,20})\\b")

    /** Extracts a VPA such as "Q528800175@ybl", rejecting e-mail-looking tokens. */
    fun upiId(body: String): String? {
        val candidate = UPI_ID.find(body)?.groupValues?.get(1) ?: return null
        // A VPA handle has no dot: name@okhdfcbank is a VPA, name@gmail.com is not.
        val handle = candidate.substringAfterLast('@')
        if (handle.contains('.')) return null
        return candidate
    }

    // ------------------------------------------------------- reference / RRN

    private val RRN = Regex("(?i)\\bRRN\\b[^A-Za-z0-9]{0,4}([A-Za-z0-9]{6,20})")
    private val REFERENCE_PATTERNS = listOf(
        Regex("(?i)\\bUPI\\s*(?:Ref(?:erence)?)?\\s*(?:No\\.?|Num(?:ber)?|ID|#)?[^A-Za-z0-9]{0,4}([A-Za-z0-9]{6,25})"),
        Regex("(?i)\\b(?:Txn|Transaction)\\s*(?:ID|No\\.?|Num(?:ber)?|Ref(?:erence)?)?[^A-Za-z0-9]{0,4}([A-Za-z0-9]{6,25})"),
        Regex("(?i)\\bRef(?:erence)?\\s*(?:No\\.?|Num(?:ber)?|ID|#)?[^A-Za-z0-9]{0,4}([A-Za-z0-9]{6,25})"),
        Regex("(?i)\\bIMPS\\s*(?:Ref)?[^A-Za-z0-9]{0,4}(\\d{8,20})"),
    )

    fun rrn(body: String): String? = RRN.find(body)?.groupValues?.get(1)

    fun referenceId(body: String): String? {
        for (pattern in REFERENCE_PATTERNS) {
            val value = pattern.find(body)?.groupValues?.get(1) ?: continue
            // Guard against swallowing the helpline number in "Not you? Call 18602677777".
            if (value.length in 6..25) return value.uppercase(Locale.ROOT)
        }
        return null
    }

    // -------------------------------------------------------------- merchant

    private val MERCHANT_PATTERNS = listOf(
        Regex("(?i)\\b(?:towards|to VPA|to vpa)\\s+([^.;,\\n]{2,60})"),
        Regex("(?i)\\b(?:at)\\s+([A-Za-z0-9][^.;,\\n]{1,50}?)\\s+on\\b"),
        Regex("(?i)\\b(?:trf to|transferred to|sent to|paid to|credited to)\\s+([^.;,\\n]{2,60})"),
        Regex("(?i)\\bto\\s+([A-Za-z0-9][^.;,\\n]{1,50}?)\\s+on\\b"),
        Regex("(?i)\\b(?:from)\\s+([A-Za-z0-9][^.;,\\n]{1,50}?)\\s+(?:on|Ref|UPI)\\b"),
        Regex("(?i)\\bInfo[:\\-]\\s*([^.;,\\n]{2,60})"),
        Regex("(?i)\\b(?:VPA)\\s+([^.;,\\n]{2,60})"),
    )

    private val MERCHANT_NOISE = Regex("(?i)\\b(?:on|ref|rrn|upi|avl|bal|a/c|dated)\\b.*$")

    /**
     * Returns a human-facing counterparty label. Falls back to null rather than
     * emitting a fragment, because a wrong merchant poisons category learning.
     */
    fun merchant(body: String): String? {
        for (pattern in MERCHANT_PATTERNS) {
            val raw = pattern.find(body)?.groupValues?.get(1) ?: continue
            val cleaned = raw
                .replace(MERCHANT_NOISE, "")
                .trim()
                .trim('.', '-', ':', ' ', '*')
            if (cleaned.length < 2) continue
            if (cleaned.all { it.isDigit() }) continue
            return cleaned
        }
        return null
    }

    // ------------------------------------------------------------ date/time

    private val DATE_TIME_FORMATS = listOf(
        "dd-MM-yy HH:mm:ss", "dd-MM-yyyy HH:mm:ss", "dd/MM/yy HH:mm:ss", "dd/MM/yyyy HH:mm:ss",
        "dd-MM-yy HH:mm", "dd-MM-yyyy HH:mm", "dd/MM/yy HH:mm", "dd/MM/yyyy HH:mm",
        "dd-MMM-yy HH:mm", "dd-MMM-yyyy HH:mm", "ddMMMyy HH:mm",
    )
    private val DATE_ONLY_FORMATS = listOf(
        "dd-MM-yy", "dd-MM-yyyy", "dd/MM/yy", "dd/MM/yyyy",
        "dd-MMM-yy", "dd-MMM-yyyy", "ddMMMyy", "ddMMMyyyy", "dd MMM yy", "dd MMM yyyy",
    )

    private val DATE_TOKEN = Regex(
        "(?i)\\b(?:on|dated|date)\\s*[:\\-]?\\s*" +
            "(\\d{1,2}[-/ ]?[A-Za-z]{3}[-/ ]?\\d{2,4}|\\d{1,2}[-/]\\d{1,2}[-/]\\d{2,4})" +
            "(?:\\s*(?:at)?\\s*(\\d{1,2}:\\d{2}(?::\\d{2})?))?"
    )

    /** Parses the bank-stated transaction time; null means "use SMS receipt time". */
    fun occurredAt(body: String): LocalDateTime? {
        val match = DATE_TOKEN.find(body) ?: return null
        val datePart = match.groupValues[1].trim()
        val timePart = match.groupValues.getOrNull(2)?.trim().orEmpty()

        if (timePart.isNotEmpty()) {
            val normalisedTime = if (timePart.count { it == ':' } == 1) "$timePart:00" else timePart
            for (fmt in DATE_TIME_FORMATS) {
                runCatching {
                    return LocalDateTime.parse(
                        "$datePart ${normalisedTime.take(8)}",
                        DateTimeFormatter.ofPattern(fmt, Locale.ENGLISH)
                    )
                }
            }
        }
        for (fmt in DATE_ONLY_FORMATS) {
            runCatching {
                val date = LocalDate.parse(datePart, DateTimeFormatter.ofPattern(fmt, Locale.ENGLISH))
                return LocalDateTime.of(date, LocalTime.MIDNIGHT)
            }
        }
        return null
    }

    // -------------------------------------------------------- payment method

    fun paymentMethod(body: String, upiId: String?): PaymentMethod {
        val lower = body.lowercase(Locale.ROOT)
        return when {
            upiId != null || lower.contains("upi") || lower.contains("vpa") -> PaymentMethod.UPI
            lower.contains("atm") || lower.contains("cash withdrawal") -> PaymentMethod.ATM
            lower.contains("credit card") || Regex("\\bcc\\b").containsMatchIn(lower) -> PaymentMethod.CREDIT_CARD
            lower.contains("debit card") -> PaymentMethod.DEBIT_CARD
            lower.contains("neft") || lower.contains("imps") || lower.contains("rtgs") ||
                lower.contains("transfer") || lower.contains("trf") -> PaymentMethod.BANK_TRANSFER
            lower.contains("card") -> PaymentMethod.DEBIT_CARD
            else -> PaymentMethod.OTHER
        }
    }
}
