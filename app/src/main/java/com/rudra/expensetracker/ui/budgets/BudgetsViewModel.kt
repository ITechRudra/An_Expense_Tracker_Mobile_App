package com.rudra.expensetracker.ui.budgets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rudra.expensetracker.core.analytics.Analytics
import com.rudra.expensetracker.core.budget.BudgetCalculator
import com.rudra.expensetracker.core.budget.BudgetStatus
import com.rudra.expensetracker.core.money.Money
import com.rudra.expensetracker.data.local.CategoryEntity
import com.rudra.expensetracker.data.repository.BudgetRepository
import com.rudra.expensetracker.data.repository.CategoryRepository
import com.rudra.expensetracker.data.repository.TransactionRepository
import com.rudra.expensetracker.domain.AppClock
import com.rudra.expensetracker.domain.DateRange
import com.rudra.expensetracker.notification.BudgetAlertNotifier
import com.rudra.expensetracker.data.prefs.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class BudgetRow(
    val budgetId: String,
    val categoryId: String?,
    val categoryName: String,
    val colorArgb: Int?,
    val status: BudgetStatus,
)

data class BudgetsUiState(
    val loading: Boolean = true,
    val month: YearMonth = YearMonth.now(),
    val overall: BudgetRow? = null,
    val rows: List<BudgetRow> = emptyList(),
    val categories: List<CategoryEntity> = emptyList(),
)

@HiltViewModel
class BudgetsViewModel @Inject constructor(
    private val budgets: BudgetRepository,
    private val transactions: TransactionRepository,
    private val categories: CategoryRepository,
    private val settings: SettingsRepository,
    private val alerts: BudgetAlertNotifier,
    private val clock: AppClock,
) : ViewModel() {

    private val period: String get() = clock.currentMonth().format(PERIOD_FORMAT)

    val state: StateFlow<BudgetsUiState> = run {
        val month = clock.currentMonth()
        val range = DateRange.month(month, clock.zone)

        combine(
            budgets.observeForPeriod(month.format(PERIOD_FORMAT)),
            transactions.observeAnalytics(range),
            categories.observeAll(),
        ) { budgetList, rows, categoryList ->
            val spentByCategory = Analytics.byCategory(rows).associate { it.categoryId to it.total }
            val totalSpent = Analytics.summary(rows).expense
            val categoriesById = categoryList.associateBy { it.id }

            val overallBudget = budgetList.firstOrNull { it.categoryId == null }
            BudgetsUiState(
                loading = false,
                month = month,
                overall = overallBudget?.let {
                    BudgetRow(
                        budgetId = it.id,
                        categoryId = null,
                        categoryName = "All spending",
                        colorArgb = null,
                        status = BudgetCalculator.status(null, Money(it.limitMinorUnits), totalSpent),
                    )
                },
                rows = budgetList
                    .filter { it.categoryId != null }
                    .map { budget ->
                        val category = categoriesById[budget.categoryId]
                        BudgetRow(
                            budgetId = budget.id,
                            categoryId = budget.categoryId,
                            categoryName = category?.name ?: "Unknown category",
                            colorArgb = category?.colorArgb,
                            status = BudgetCalculator.status(
                                budget.categoryId,
                                Money(budget.limitMinorUnits),
                                spentByCategory[budget.categoryId] ?: Money.ZERO,
                            ),
                        )
                    }
                    .sortedByDescending { it.status.percentUsed },
                categories = categoryList,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BudgetsUiState())
    }

    fun setLimit(categoryId: String?, limit: Money) {
        viewModelScope.launch { budgets.setLimit(period, categoryId, limit) }
    }

    fun delete(budgetId: String) {
        viewModelScope.launch { budgets.delete(budgetId) }
    }

    /**
     * Fires an alert only when a budget has moved into a worse state than the
     * one already announced, so a user near their limit is told once.
     */
    fun checkAlerts() {
        viewModelScope.launch {
            if (!settings.budgetAlertsEnabled.first()) return@launch
            val current = state.value
            for (row in current.rows + listOfNotNull(current.overall)) {
                if (!row.status.isAlerting) continue
                val stored = budgets.find(period, row.categoryId) ?: continue
                if (stored.lastAlertedState == row.status.state.name) continue
                alerts.notify(row.status, row.categoryName)
                budgets.markAlerted(stored.id, row.status.state.name)
            }
        }
    }

    private companion object {
        val PERIOD_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM")
    }
}
