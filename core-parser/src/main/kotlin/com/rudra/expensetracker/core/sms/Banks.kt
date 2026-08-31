package com.rudra.expensetracker.core.sms

/**
 * A bank or payment provider the app can recognise.
 *
 * [senderTokens] are matched against the DLT header of the SMS (the "INDUSB" in
 * "AD-INDUSB-S"); [bodyTokens] are matched against the message text, which is
 * what saves us when a bank sends from an unregistered short code.
 */
data class Bank(
    val code: String,
    val displayName: String,
    val senderTokens: Set<String>,
    val bodyTokens: Set<String> = emptySet(),
)

object BankRegistry {

    val banks: List<Bank> = listOf(
        Bank("INDUSIND", "IndusInd Bank", setOf("INDUSB", "INDUSI", "INDBNK", "INDUSD"), setOf("indusind")),
        Bank("HDFC", "HDFC Bank", setOf("HDFCBK", "HDFCBN", "HDFCB"), setOf("hdfc bank", "hdfc")),
        Bank("SBI", "State Bank of India", setOf("SBIINB", "SBIBNK", "SBICRD", "SBIPSG", "ATMSBI", "SBIUPI"), setOf("state bank of india", "-sbi")),
        Bank("ICICI", "ICICI Bank", setOf("ICICIB", "ICICIT", "ICICIP"), setOf("icici bank", "icici")),
        Bank("AXIS", "Axis Bank", setOf("AXISBK", "AXISBN", "AXISB"), setOf("axis bank")),
        Bank("KOTAK", "Kotak Mahindra Bank", setOf("KOTAKB", "KOTAK", "KMBLNK"), setOf("kotak")),
        Bank("YES", "Yes Bank", setOf("YESBNK", "YESBK"), setOf("yes bank")),
        Bank("IDFC", "IDFC FIRST Bank", setOf("IDFCFB", "IDFCBK", "IDFCFIRST"), setOf("idfc first", "idfc")),
        Bank("BOB", "Bank of Baroda", setOf("BOBTXN", "BOBSMS", "BOBIBN", "BARODA"), setOf("bank of baroda", "bob ")),
        Bank("PNB", "Punjab National Bank", setOf("PNBSMS", "PNBBNK", "PUNBNK"), setOf("punjab national bank", "-pnb")),
        Bank("CANARA", "Canara Bank", setOf("CANBNK", "CANARA", "CBSSBI"), setOf("canara bank")),
        Bank("UNION", "Union Bank of India", setOf("UNIONB", "UBIBNK", "UNIONBK"), setOf("union bank")),
        Bank("BOI", "Bank of India", setOf("BOIIND", "BOINET"), setOf("bank of india")),
        Bank("CENTRAL", "Central Bank of India", setOf("CBIBNK", "CENTBK"), setOf("central bank of india")),
        Bank("INDIAN", "Indian Bank", setOf("INDBNK", "INDIANBK"), setOf("indian bank")),
        Bank("FEDERAL", "Federal Bank", setOf("FEDBNK", "FEDERAL"), setOf("federal bank")),
        Bank("RBL", "RBL Bank", setOf("RBLBNK", "RBLBK"), setOf("rbl bank")),
        Bank("AU", "AU Small Finance Bank", setOf("AUBANK", "AUSFBL"), setOf("au small finance", "au bank")),
        Bank("BANDHAN", "Bandhan Bank", setOf("BANDHN", "BDNBNK"), setOf("bandhan bank")),
        Bank("DBS", "DBS Bank", setOf("DBSBNK", "DBSSMS"), setOf("dbs bank", "digibank")),
        Bank("PAYTM", "Paytm Payments Bank", setOf("PAYTMB", "PYTMPB"), setOf("paytm payments bank")),
        Bank("AIRTEL", "Airtel Payments Bank", setOf("AIRPAY", "ATLBNK"), setOf("airtel payments bank")),
    )

    private val byCode = banks.associateBy { it.code }

    private val bySenderToken: Map<String, Bank> = buildMap {
        // First registration wins so that a token shared by two banks resolves
        // deterministically rather than by list order accident at lookup time.
        for (bank in banks) for (token in bank.senderTokens) putIfAbsent(token, bank)
    }

    fun byCode(code: String?): Bank? = code?.let { byCode[it] }

    /**
     * Reduces a DLT sender header to its bank token.
     * "AD-INDUSB-S" -> "INDUSB", "VM-HDFCBK" -> "HDFCBK", "51969" -> "51969".
     */
    fun normaliseSender(sender: String): String {
        var s = sender.trim().uppercase()
        s = s.removePrefix("+91")
        s = s.replace(Regex("^[A-Z]{2}-"), "")
        s = s.replace(Regex("-[A-Z]$"), "")
        return s
    }

    /** Identifies the bank from the sender header, falling back to the body text. */
    fun identify(sender: String, body: String): Bank? {
        val token = normaliseSender(sender)
        bySenderToken[token]?.let { return it }
        // Some issuers append a product suffix, e.g. "HDFCBKCC".
        bySenderToken.entries.firstOrNull { token.startsWith(it.key) || token.contains(it.key) }
            ?.let { return it.value }

        val lower = body.lowercase()
        return banks.firstOrNull { bank -> bank.bodyTokens.any { lower.contains(it) } }
    }

    /** True when the header looks like a commercial/DLT sender rather than a person. */
    fun looksLikeServiceSender(sender: String): Boolean {
        val s = sender.trim()
        if (s.isEmpty()) return false
        // A personal Indian mobile number is 10 digits, optionally +91 prefixed.
        val digitsOnly = s.removePrefix("+").all { it.isDigit() }
        if (digitsOnly) {
            val digits = s.filter { it.isDigit() }
            // Short codes (<= 8 digits) are commercial; full mobile numbers are not.
            return digits.length <= 8
        }
        return true
    }
}
