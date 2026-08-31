package com.rudra.expensetracker.core.money

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Currency amount stored as an integer number of minor units (paise for INR).
 *
 * Financial values are never held as Double anywhere in this project: repeated
 * addition of binary floating point silently drifts, and a ledger that drifts is
 * worse than no ledger at all.
 */
@JvmInline
value class Money(val minorUnits: Long) : Comparable<Money> {

    val isZero: Boolean get() = minorUnits == 0L
    val isNegative: Boolean get() = minorUnits < 0L

    operator fun plus(other: Money) = Money(minorUnits + other.minorUnits)
    operator fun minus(other: Money) = Money(minorUnits - other.minorUnits)
    operator fun times(factor: Int) = Money(minorUnits * factor)
    operator fun unaryMinus() = Money(-minorUnits)

    fun abs() = if (minorUnits < 0) Money(-minorUnits) else this

    override fun compareTo(other: Money): Int = minorUnits.compareTo(other.minorUnits)

    fun toBigDecimal(): BigDecimal = BigDecimal.valueOf(minorUnits, 2)

    /** Percentage of [total] this amount represents, 0 when [total] is zero. */
    fun percentOf(total: Money): Int =
        if (total.minorUnits == 0L) 0
        else ((minorUnits.toDouble() / total.minorUnits.toDouble()) * 100.0).toInt()

    override fun toString(): String = toBigDecimal().toPlainString()

    companion object {
        val ZERO = Money(0)

        fun ofMajor(major: Long): Money = Money(major * 100)

        fun ofBigDecimal(value: BigDecimal): Money =
            Money(value.setScale(2, RoundingMode.HALF_UP).movePointRight(2).longValueExact())

        /**
         * Parses an amount as it appears inside an Indian bank SMS.
         *
         * Handles the Indian digit grouping ("1,23,456.78"), a stray trailing
         * period from sentence punctuation, and amounts written without decimals.
         * Returns null rather than guessing when the token is not a clean amount.
         */
        fun parseOrNull(raw: String): Money? {
            var text = raw.trim()
                .replace(",", "")
                .replace("₹", "")
                .replace(Regex("(?i)^(?:INR|RS\\.?|MRP)\\s*"), "")
                .trim()
            // Bank copy routinely ends a sentence right after the amount: "Rs 32.00."
            while (text.endsWith(".")) text = text.dropLast(1)
            if (text.isEmpty()) return null
            if (!text.matches(Regex("\\d+(?:\\.\\d{1,2})?"))) return null
            return try {
                ofBigDecimal(BigDecimal(text))
            } catch (e: ArithmeticException) {
                null
            } catch (e: NumberFormatException) {
                null
            }
        }
    }
}

fun Iterable<Money>.sum(): Money = Money(sumOf { it.minorUnits })
