package com.rudra.expensetracker.core.sms

import java.util.Locale

/**
 * One bank's (or one family of formats') parsing rules.
 *
 * Adding support for a new bank means implementing this interface and
 * registering it — no existing parser is touched. See docs/SMS_PARSING.md.
 */
interface TransactionSmsParser {

    /** Stable identifier persisted with each parsed transaction for diagnostics. */
    val id: String

    /** Banks this parser claims. Empty means "any bank" (the generic fallback). */
    val bankCodes: Set<String>

    fun canParse(sms: RawSms, bank: Bank?): Boolean =
        bankCodes.isEmpty() || (bank != null && bank.code in bankCodes)

    fun parse(sms: RawSms, bank: Bank?): ParsedTransaction?
}

/**
 * Format-agnostic parser built from the shared [Extractors]. Handles the
 * majority of Indian bank alerts on its own; bank parsers subclass it and
 * override only the field their issuer writes differently.
 */
open class GenericIndianBankParser(
    override val id: String = "generic",
    override val bankCodes: Set<String> = emptySet(),
) : TransactionSmsParser {

    override fun parse(sms: RawSms, bank: Bank?): ParsedTransaction? {
        val body = sms.body
        val type = extractDirection(body) ?: return null
        val amount = extractAmount(body) ?: return null
        if (amount.isZero || amount.isNegative) return null

        val upiId = extractUpiId(body)
        return ParsedTransaction(
            type = type,
            amount = amount,
            bankCode = bank?.code,
            bankName = bank?.displayName,
            accountTail = extractAccountTail(body),
            upiId = upiId,
            merchant = extractMerchant(body),
            counterparty = null,
            referenceId = Extractors.referenceId(body),
            rrn = Extractors.rrn(body),
            availableBalance = Extractors.availableBalance(body),
            paymentMethod = extractPaymentMethod(body, upiId),
            occurredAt = Extractors.occurredAt(body),
            parserId = id,
            smsId = sms.id,
            smsSender = sms.sender,
            receivedAtEpochMillis = sms.receivedAtEpochMillis,
        )
    }

    protected open fun extractDirection(body: String) = Extractors.direction(body)
    protected open fun extractAmount(body: String) = Extractors.amount(body)
    protected open fun extractAccountTail(body: String) = Extractors.accountTail(body)
    protected open fun extractUpiId(body: String) = Extractors.upiId(body)
    protected open fun extractMerchant(body: String) = Extractors.merchant(body)
    protected open fun extractPaymentMethod(body: String, upiId: String?) =
        Extractors.paymentMethod(body, upiId)
}

/**
 * IndusInd writes "A/C *XX7375 debited by Rs 32.00 towards <vpa>. RRN:<n>."
 * and closes every alert with a helpline number, which must never be mistaken
 * for a reference.
 */
class IndusIndParser : GenericIndianBankParser("indusind", setOf("INDUSIND")) {

    private val HELPLINE = Regex("(?i)(?:not you\\?|call)\\s*1[80]\\d{8,}")

    override fun extractMerchant(body: String): String? =
        super.extractMerchant(HELPLINE.replace(body, ""))
}

/**
 * Axis card alerts omit the "at <merchant> on" wording and instead place the
 * merchant between the date and the available-limit clause:
 * "Spent Card no. XX1234 INR 500 01-01-26 SWIGGY Avl Lmt INR 40000".
 */
class AxisParser : GenericIndianBankParser("axis", setOf("AXIS")) {

    private val CARD_SPEND = Regex(
        "(?i)\\bINR\\s*[\\d,.]+\\s+\\d{1,2}[-/][\\d A-Za-z]{2,9}[-/]?\\d{0,4}\\s+(.+?)\\s+Avl\\s*Lmt"
    )

    override fun extractMerchant(body: String): String? =
        CARD_SPEND.find(body)?.groupValues?.get(1)?.trim()?.takeIf { it.length >= 2 }
            ?: super.extractMerchant(body)
}

/**
 * ICICI phrases UPI debits as "Acct XX123 debited with Rs 500 on <date>;
 * <merchant> credited." — the merchant follows the semicolon, not a preposition.
 */
class IciciParser : GenericIndianBankParser("icici", setOf("ICICI")) {

    private val SEMICOLON_MERCHANT = Regex("(?i);\\s*([^.;\\n]{2,60}?)\\s+credited")

    override fun extractMerchant(body: String): String? =
        SEMICOLON_MERCHANT.find(body)?.groupValues?.get(1)?.trim()
            ?: super.extractMerchant(body)
}

/**
 * SBI uses "transfer to <NAME> Ref No <n>" and "transfer from <NAME>", where the
 * name runs up to the reference token.
 */
class SbiParser : GenericIndianBankParser("sbi", setOf("SBI")) {

    private val TRANSFER = Regex("(?i)\\btransfer\\s+(?:to|from)\\s+(.+?)\\s+Ref(?:erence)?\\s*No")

    override fun extractMerchant(body: String): String? =
        TRANSFER.find(body)?.groupValues?.get(1)?.trim()
            ?: super.extractMerchant(body)
}

/**
 * Kotak leads with the verb ("Sent Rs.500.00 from Kotak Bank AC X1234 to
 * <vpa> on <date>") and glues the reference to the previous sentence without a
 * space, which breaks a naive word-boundary scan.
 */
class KotakParser : GenericIndianBankParser("kotak", setOf("KOTAK")) {

    override fun extractMerchant(body: String): String? =
        super.extractMerchant(body.replace(Regex("(?i)\\.(UPI)"), ". $1"))
}

/**
 * Ordered lookup of parsers. The generic parser is always last so a bank
 * specific rule wins when one exists, and nothing is ever left unparsed merely
 * because its issuer has no dedicated implementation.
 */
class ParserRegistry(
    parsers: List<TransactionSmsParser> = DEFAULT_PARSERS,
    private val fallback: TransactionSmsParser = GenericIndianBankParser(),
) {
    private val parsers: List<TransactionSmsParser> = parsers

    fun parsersFor(sms: RawSms, bank: Bank?): List<TransactionSmsParser> =
        parsers.filter { it.canParse(sms, bank) } + fallback

    companion object {
        val DEFAULT_PARSERS: List<TransactionSmsParser> = listOf(
            IndusIndParser(),
            AxisParser(),
            IciciParser(),
            SbiParser(),
            KotakParser(),
        )
    }
}
