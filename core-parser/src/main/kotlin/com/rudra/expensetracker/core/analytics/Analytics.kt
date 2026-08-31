package com.rudra.expensetracker.core.analytics

import com.rudra.expensetracker.core.money.Money
import com.rudra.expensetracker.core.money.sum
import com.rudra.expensetracker.core.sms.TransactionType
import java.time.LocalDate

/**
 * The minimum a transaction must expose for analytics. The Room entity
 * implements this, keeping every aggregation testable without Android.
 */
interface AnalyticsTransaction {
    val type: TransactionType
    val amount: Money
    val categoryId: String?
    val merchantKey: String?
    val date: LocalDate
}

data class CategoryTotal(val categoryId: String?, val total: Money, val count: Int, val share: Int)
data class MerchantTotal(val merchantKey: String, val total: Money, val count: Int)
data class DailyTotal(val date: LocalDate, val expense: Money, val income: Money)
data class PeriodSummary(val income: Money, val expense: Money) {
    val net: Money get() = income - expense
}

object Analytics {

    fun summary(transactions: List<AnalyticsTransaction>): PeriodSummary = PeriodSummary(
        income = transactions.filter { it.type == TransactionType.INCOME }.map { it.amount }.sum(),
        expense = transactions.filter { it.type == TransactionType.EXPENSE }.map { it.amount }.sum(),
    )

    /** Expense totals per category, largest first, with each share of the total. */
    fun byCategory(transactions: List<AnalyticsTransaction>): List<CategoryTotal> {
        val expenses = transactions.filter { it.type == TransactionType.EXPENSE }
        val overall = expenses.map { it.amount }.sum()
        return expenses.groupBy { it.categoryId }
            .map { (categoryId, items) ->
                val total = items.map { it.amount }.sum()
                CategoryTotal(categoryId, total, items.size, total.percentOf(overall))
            }
            .sortedByDescending { it.total.minorUnits }
    }

    fun topMerchants(transactions: List<AnalyticsTransaction>, limit: Int = 5): List<MerchantTotal> =
        transactions.asSequence()
            .filter { it.type == TransactionType.EXPENSE }
            .mapNotNull { txn -> txn.merchantKey?.takeIf { it.isNotBlank() }?.let { it to txn } }
            .groupBy({ it.first }, { it.second })
            .map { (key, items) -> MerchantTotal(key, items.map { it.amount }.sum(), items.size) }
            .sortedByDescending { it.total.minorUnits }
            .take(limit)

    /**
     * A dense day-by-day series across [from]..[to] inclusive. Days with no
     * activity are emitted as zero so charts do not silently compress gaps.
     */
    fun dailySeries(
        transactions: List<AnalyticsTransaction>,
        from: LocalDate,
        to: LocalDate,
    ): List<DailyTotal> {
        val byDate = transactions.groupBy { it.date }
        return generateSequence(from) { day -> day.plusDays(1).takeIf { !it.isAfter(to) } }
            .map { day ->
                val items = byDate[day].orEmpty()
                DailyTotal(
                    date = day,
                    expense = items.filter { it.type == TransactionType.EXPENSE }.map { it.amount }.sum(),
                    income = items.filter { it.type == TransactionType.INCOME }.map { it.amount }.sum(),
                )
            }
            .toList()
    }

    fun largestExpenses(transactions: List<AnalyticsTransaction>, limit: Int = 5): List<AnalyticsTransaction> =
        transactions.filter { it.type == TransactionType.EXPENSE }
            .sortedByDescending { it.amount.minorUnits }
            .take(limit)

    /**
     * Mean daily spend over the calendar span covered by [from]..[to], not over
     * the days that happen to have transactions — otherwise a quiet week would
     * inflate the average.
     */
    fun averageDailySpend(
        transactions: List<AnalyticsTransaction>,
        from: LocalDate,
        to: LocalDate,
    ): Money {
        val days = (to.toEpochDay() - from.toEpochDay() + 1).coerceAtLeast(1)
        val total = transactions.filter { it.type == TransactionType.EXPENSE }.map { it.amount }.sum()
        return Money(total.minorUnits / days)
    }
}
