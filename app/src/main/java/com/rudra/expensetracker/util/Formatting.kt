package com.rudra.expensetracker.util

import com.rudra.expensetracker.core.money.Money
import java.text.NumberFormat
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val INDIA = Locale.forLanguageTag("en-IN")

private val rupeeFormat: NumberFormat = NumberFormat.getNumberInstance(INDIA).apply {
    minimumFractionDigits = 0
    maximumFractionDigits = 2
}

/**
 * Formats as Indian-grouped rupees, dropping ".00" so a dashboard of round
 * numbers stays scannable.
 */
fun Money.formatRupees(): String = "₹" + rupeeFormat.format(toBigDecimal())

/** Compact form for chart axes and dense tiles: ₹1.2L, ₹45.0K. */
fun Money.formatCompact(): String {
    val rupees = minorUnits / 100.0
    return when {
        rupees >= 10_000_000 -> "₹%.1fCr".format(INDIA, rupees / 10_000_000)
        rupees >= 100_000 -> "₹%.1fL".format(INDIA, rupees / 100_000)
        rupees >= 1_000 -> "₹%.1fK".format(INDIA, rupees / 1_000)
        else -> "₹%.0f".format(INDIA, rupees)
    }
}

private val dayMonthTime: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM, h:mm a", INDIA)
private val dayMonth: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy", INDIA)

fun LocalDateTime.formatTransactionTime(): String = format(dayMonthTime)
fun LocalDateTime.formatDate(): String = format(dayMonth)

/** "IndusInd ••••7375" for a transaction row. */
fun accountLabel(bankName: String?, accountTail: String?): String? {
    val last4 = accountTail?.filter { it.isDigit() }?.takeLast(4)
    return when {
        bankName != null && last4 != null -> "$bankName ••••$last4"
        bankName != null -> bankName
        last4 != null -> "••••$last4"
        else -> null
    }
}
