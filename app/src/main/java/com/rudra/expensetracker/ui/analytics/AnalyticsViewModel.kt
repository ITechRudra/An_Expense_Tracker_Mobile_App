package com.rudra.expensetracker.ui.analytics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rudra.expensetracker.core.analytics.Analytics
import com.rudra.expensetracker.core.analytics.CategoryTotal
import com.rudra.expensetracker.core.analytics.DailyTotal
import com.rudra.expensetracker.core.analytics.MerchantTotal
import com.rudra.expensetracker.core.money.Money
import com.rudra.expensetracker.data.local.CategoryEntity
import com.rudra.expensetracker.data.repository.CategoryRepository
import com.rudra.expensetracker.data.repository.TransactionRepository
import com.rudra.expensetracker.domain.AppClock
import com.rudra.expensetracker.domain.DateRange
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn

enum class AnalyticsPeriod(val label: String) { WEEK("Week"), MONTH("Month"), YEAR("Year") }

data class AnalyticsUiState(
    val loading: Boolean = true,
    val period: AnalyticsPeriod = AnalyticsPeriod.MONTH,
    val income: Money = Money.ZERO,
    val expense: Money = Money.ZERO,
    val averageDaily: Money = Money.ZERO,
    val byCategory: List<CategoryTotal> = emptyList(),
    val topMerchants: List<MerchantTotal> = emptyList(),
    val daily: List<DailyTotal> = emptyList(),
    val previousExpense: Money = Money.ZERO,
    val categoriesById: Map<String, CategoryEntity> = emptyMap(),
) {
    val net: Money get() = income - expense

    /** Percentage change against the comparable previous period, or null on the first one. */
    val expenseChangePercent: Int?
        get() = if (previousExpense.isZero) null
        else (((expense.minorUnits - previousExpense.minorUnits) * 100) / previousExpense.minorUnits).toInt()
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AnalyticsViewModel @Inject constructor(
    private val transactions: TransactionRepository,
    private val categories: CategoryRepository,
    private val clock: AppClock,
) : ViewModel() {

    private val period = MutableStateFlow(AnalyticsPeriod.MONTH)

    val state: StateFlow<AnalyticsUiState> = period.flatMapLatest { selected ->
        val today = clock.today()
        val current = selected.range(today)
        val previous = selected.previousRange(today)

        combine(
            transactions.observeAnalytics(current),
            transactions.observeAnalytics(previous),
            categories.observeAll(),
        ) { rows, previousRows, categoryList ->
            val summary = Analytics.summary(rows)
            val (from, to) = selected.bounds(today)
            AnalyticsUiState(
                loading = false,
                period = selected,
                income = summary.income,
                expense = summary.expense,
                averageDaily = Analytics.averageDailySpend(rows, from, to),
                byCategory = Analytics.byCategory(rows),
                topMerchants = Analytics.topMerchants(rows, limit = 5),
                daily = Analytics.dailySeries(rows, from, to),
                previousExpense = Analytics.summary(previousRows).expense,
                categoriesById = categoryList.associateBy { it.id },
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AnalyticsUiState())

    fun onPeriodChange(value: AnalyticsPeriod) { period.value = value }

    private fun AnalyticsPeriod.bounds(today: LocalDate): Pair<LocalDate, LocalDate> = when (this) {
        AnalyticsPeriod.WEEK -> today.minusDays(6) to today
        AnalyticsPeriod.MONTH -> YearMonth.from(today).atDay(1) to today
        AnalyticsPeriod.YEAR -> today.withDayOfYear(1) to today
    }

    private fun AnalyticsPeriod.range(today: LocalDate): DateRange {
        val (from, to) = bounds(today)
        return DateRange.of(from, to, clock.zone)
    }

    /**
     * The equivalent window one period back, so "up 12%" compares like with
     * like rather than a full month against a part month.
     */
    private fun AnalyticsPeriod.previousRange(today: LocalDate): DateRange {
        val (from, to) = bounds(today)
        return when (this) {
            AnalyticsPeriod.WEEK -> DateRange.of(from.minusWeeks(1), to.minusWeeks(1), clock.zone)
            AnalyticsPeriod.MONTH -> DateRange.of(from.minusMonths(1), to.minusMonths(1), clock.zone)
            AnalyticsPeriod.YEAR -> DateRange.of(from.minusYears(1), to.minusYears(1), clock.zone)
        }
    }
}
