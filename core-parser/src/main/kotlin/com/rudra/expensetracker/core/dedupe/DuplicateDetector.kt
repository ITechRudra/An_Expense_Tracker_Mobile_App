package com.rudra.expensetracker.core.dedupe

import com.rudra.expensetracker.core.sms.ParsedTransaction
import com.rudra.expensetracker.core.sms.TransactionType
import com.rudra.expensetracker.core.sms.accountLast4
import java.util.Locale
import kotlin.math.abs

/**
 * The identity of a transaction already stored, reduced to just what duplicate
 * detection needs. Keeping this separate from the Room entity lets the whole
 * algorithm be unit tested without a database.
 */
data class TransactionIdentity(
    val strongIds: Set<String>,
    val fuzzyKey: String,
    val amountMinorUnits: Long,
    val occurredAtEpochMillis: Long,
    val hasCounterparty: Boolean,
)

sealed interface DuplicateVerdict {
    data object Unique : DuplicateVerdict
    data class Duplicate(val of: TransactionIdentity, val rule: String) : DuplicateVerdict
}

/**
 * Detects transactions the app has already recorded.
 *
 * Two independent strategies run in order. Strong identifiers (RRN, bank
 * reference, originating SMS id) are trusted absolutely. Absent those, a fuzzy
 * key plus a time window catches the same payment arriving twice in different
 * wording — a bank alert and a card-network alert, say — without collapsing two
 * genuinely separate payments of the same amount to the same shop.
 */
class DuplicateDetector(
    private val identifiedWindowMillis: Long = DEFAULT_IDENTIFIED_WINDOW_MILLIS,
    private val anonymousWindowMillis: Long = DEFAULT_ANONYMOUS_WINDOW_MILLIS,
) {

    fun check(candidate: TransactionIdentity, existing: Iterable<TransactionIdentity>): DuplicateVerdict {
        for (other in existing) {
            if (candidate.strongIds.isNotEmpty() && other.strongIds.isNotEmpty() &&
                candidate.strongIds.any { it in other.strongIds }
            ) {
                return DuplicateVerdict.Duplicate(other, RULE_STRONG_ID)
            }
        }
        for (other in existing) {
            if (candidate.fuzzyKey != other.fuzzyKey) continue
            if (candidate.amountMinorUnits != other.amountMinorUnits) continue
            val window = if (candidate.hasCounterparty) identifiedWindowMillis else anonymousWindowMillis
            if (abs(candidate.occurredAtEpochMillis - other.occurredAtEpochMillis) <= window) {
                return DuplicateVerdict.Duplicate(other, RULE_FUZZY)
            }
        }
        return DuplicateVerdict.Unique
    }

    companion object {
        const val RULE_STRONG_ID = "strong-identifier"
        const val RULE_FUZZY = "amount-counterparty-window"

        /**
         * Where a counterparty is known, the same amount to the same payee is
         * very unlikely to be a real repeat within six hours, but a delayed or
         * re-sent bank alert easily lands that far apart.
         */
        const val DEFAULT_IDENTIFIED_WINDOW_MILLIS = 6L * 60 * 60 * 1000

        /**
         * With no counterparty the key is weak, so the window shrinks to the
         * span in which a genuine repeat purchase is implausible.
         */
        const val DEFAULT_ANONYMOUS_WINDOW_MILLIS = 3L * 60 * 1000
    }
}

object TransactionFingerprint {

    /** Identifiers that alone prove two records are the same transaction. */
    fun strongIds(parsed: ParsedTransaction): Set<String> = buildSet {
        parsed.rrn?.let { add("rrn:${it.uppercase(Locale.ROOT)}") }
        parsed.referenceId?.let { add("ref:${it.uppercase(Locale.ROOT)}") }
        parsed.smsId.takeIf { it.isNotBlank() }?.let { add("sms:$it") }
    }

    /**
     * A stable key over the fields that survive rewording between two alerts
     * describing the same payment.
     */
    fun fuzzyKey(
        type: TransactionType,
        amountMinorUnits: Long,
        accountLast4: String?,
        counterparty: String?,
    ): String = listOf(
        type.name,
        amountMinorUnits.toString(),
        accountLast4.orEmpty(),
        normaliseCounterparty(counterparty),
    ).joinToString("|")

    fun fuzzyKey(parsed: ParsedTransaction): String = fuzzyKey(
        parsed.type,
        parsed.amount.minorUnits,
        parsed.accountLast4,
        parsed.upiId ?: parsed.merchant,
    )

    fun identity(parsed: ParsedTransaction): TransactionIdentity = TransactionIdentity(
        strongIds = strongIds(parsed),
        fuzzyKey = fuzzyKey(parsed),
        amountMinorUnits = parsed.amount.minorUnits,
        occurredAtEpochMillis = parsed.receivedAtEpochMillis,
        hasCounterparty = parsed.upiId != null || parsed.merchant != null,
    )

    /** Case, spacing and trailing reference noise differ between alerts; strip them. */
    fun normaliseCounterparty(value: String?): String =
        value.orEmpty()
            .lowercase(Locale.ROOT)
            .replace(Regex("[^a-z0-9@]"), "")
            .take(40)
}
