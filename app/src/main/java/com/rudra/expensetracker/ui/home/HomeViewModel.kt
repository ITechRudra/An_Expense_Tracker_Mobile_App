package com.rudra.expensetracker.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rudra.expensetracker.core.analytics.Analytics
import com.rudra.expensetracker.core.analytics.CategoryTotal
import com.rudra.expensetracker.core.money.Money
import com.rudra.expensetracker.core.sms.TransactionType
import com.rudra.expensetracker.data.local.CategoryEntity
import com.rudra.expensetracker.data.local.TransactionEntity
import com.rudra.expensetracker.data.repository.CategoryRepository
import com.rudra.expensetracker.data.repository.TransactionRepository
import com.rudra.expensetracker.domain.AppClock
import com.rudra.expensetracker.domain.DateRange
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.YearMonth
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class HomeUiState(
    val loading: Boolean = true,
    val month: YearMonth = YearMonth.now(),
    val monthIncome: Money = Money.ZERO,
    val monthExpense: Money = Money.ZERO,
    val todayExpense: Money = Money.ZERO,
    val weekExpense: Money = Money.ZERO,
    val recent: List<TransactionEntity> = emptyList(),
    val unconfirmed: List<TransactionEntity> = emptyList(),
    val topCategories: List<CategoryTotal> = emptyList(),
    val largestExpenses: List<TransactionEntity> = emptyList(),
    val categoriesById: Map<String, CategoryEntity> = emptyMap(),
) {
    val monthNet: Money get() = monthIncome - monthExpense
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    transactions: TransactionRepository,
    categories: CategoryRepository,
    private val clock: AppClock,
) : ViewModel() {

    val state: StateFlow<HomeUiState> = run {
        val month = clock.currentMonth()
        val today = clock.today()
        val monthRange = DateRange.month(month, clock.zone)

        combine(
            transactions.observeAnalytics(monthRange),
            transactions.observeInRange(monthRange),
            transactions.observeTotal(TransactionType.EXPENSE, DateRange.day(today, clock.zone)),
            transactions.observeTotal(TransactionType.EXPENSE, DateRange.trailingWeek(today, clock.zone)),
            categories.observeAll(),
        ) { analyticsRows, monthTransactions, todayExpense, weekExpense, categoryList ->
            val summary = Analytics.summary(analyticsRows)
            HomeUiState(
                loading = false,
                month = month,
                monthIncome = summary.income,
                monthExpense = summary.expense,
                todayExpense = todayExpense,
                weekExpense = weekExpense,
                // Confirmed rows only: an uncategorised entry would otherwise
                // appear twice, once in the prompt strip and once in the list.
                recent = monthTransactions.filter { it.isConfirmed }.take(RECENT_LIMIT),
                unconfirmed = monthTransactions.filter { !it.isConfirmed },
                topCategories = Analytics.byCategory(analyticsRows).take(TOP_CATEGORY_LIMIT),
                largestExpenses = monthTransactions
                    .filter { it.type == TransactionType.EXPENSE }
                    .sortedByDescending { it.amountMinorUnits }
                    .take(LARGEST_LIMIT),
                categoriesById = categoryList.associateBy { it.id },
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())
    }

    private companion object {
        const val RECENT_LIMIT = 8
        const val TOP_CATEGORY_LIMIT = 5
        const val LARGEST_LIMIT = 3
    }
}
