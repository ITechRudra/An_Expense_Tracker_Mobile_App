package com.rudra.expensetracker.core.sms

/**
 * Turns a raw SMS into an [IngestResult].
 *
 * Filter -> identify bank -> parse -> score. The whole pipeline is pure and
 * synchronous so it can be exercised exhaustively in unit tests and reused
 * unchanged if a non-SMS ingestion source is added later.
 */
class TransactionSmsPipeline(
    private val registry: ParserRegistry = ParserRegistry(),
    private val confidenceThreshold: Float = MIN_AUTO_CONFIDENCE,
) {

    fun ingest(sms: RawSms): IngestResult {
        SmsFilter.reject(sms)?.let { return IngestResult.Ignored(it) }

        val bank = BankRegistry.identify(sms.sender, sms.body)
        val parsed = registry.parsersFor(sms, bank)
            .firstNotNullOfOrNull { it.parse(sms, bank) }
            ?: return IngestResult.Ignored(inferFailureReason(sms))

        val scored = parsed.copy(confidence = score(parsed))
        val missing = missingFields(scored)

        return if (scored.confidence >= confidenceThreshold && missing.isEmpty()) {
            IngestResult.Transaction(scored)
        } else {
            IngestResult.NeedsReview(scored, missing)
        }
    }

    private fun inferFailureReason(sms: RawSms): RejectionReason = when {
        Extractors.amount(sms.body) == null -> RejectionReason.NO_AMOUNT_FOUND
        Extractors.direction(sms.body) == null -> RejectionReason.NO_DIRECTION_FOUND
        else -> RejectionReason.UNRECOGNISED_FORMAT
    }

    /**
     * Confidence is the share of corroborating signals present. Amount and
     * direction are mandatory and form the floor; every additional identifier
     * makes it likelier this is a real posted transaction rather than a
     * narrative message that happens to contain a rupee figure.
     */
    private fun score(p: ParsedTransaction): Float {
        var score = BASE_SCORE
        if (p.bankCode != null) score += 0.15f
        if (p.accountTail != null) score += 0.12f
        if (p.rrn != null || p.referenceId != null) score += 0.12f
        if (p.upiId != null) score += 0.06f
        if (p.merchant != null) score += 0.05f
        if (p.availableBalance != null) score += 0.05f
        if (p.paymentMethod != PaymentMethod.OTHER) score += 0.05f
        return minOf(1f, score)
    }

    /** Fields whose absence makes an automatic, unreviewed save unsafe. */
    private fun missingFields(p: ParsedTransaction): List<String> = buildList {
        if (p.bankCode == null) add("bank")
        if (p.accountTail == null && p.upiId == null) add("account")
    }

    companion object {
        const val BASE_SCORE = 0.50f

        /**
         * Below this the transaction is queued for review instead of prompting.
         * Chosen so that a message needs a recognised bank plus at least one
         * hard identifier (account, reference, or VPA) to prompt automatically.
         */
        const val MIN_AUTO_CONFIDENCE = 0.70f
    }
}
