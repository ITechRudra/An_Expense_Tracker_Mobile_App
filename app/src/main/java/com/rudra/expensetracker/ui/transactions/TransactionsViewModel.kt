package com.rudra.expensetracker.ui.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rudra.expensetracker.core.money.Money
import com.rudra.expensetracker.core.sms.PaymentMethod
import com.rudra.expensetracker.core.sms.TransactionType
import com.rudra.expensetracker.data.local.AccountEntity
import com.rudra.expensetracker.data.local.CategoryEntity
import com.rudra.expensetracker.data.local.TransactionEntity
import com.rudra.expensetracker.data.repository.AccountRepository
import com.rudra.expensetracker.data.repository.CategoryRepository
import com.rudra.expensetracker.data.repository.TransactionFilter
import com.rudra.expensetracker.data.repository.TransactionRepository
import com.rudra.expensetracker.domain.AppClock
import com.rudra.expensetracker.domain.DateRange
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class DatePreset(val label: String) {
    THIS_MONTH("This month"),
    LAST_30("Last 30 days"),
    THIS_YEAR("This year"),
    ALL("All time"),
}

data class TransactionsUiState(
    val loading: Boolean = true,
    val query: String = "",
    val transactions: List<TransactionEntity> = emptyList(),
    val categoriesById: Map<String, CategoryEntity> = emptyMap(),
    val accounts: List<AccountEntity> = emptyList(),
    val typeFilter: TransactionType? = null,
    val categoryFilter: String? = null,
    val accountFilter: String? = null,
    val methodFilter: PaymentMethod? = null,
    val datePreset: DatePreset = DatePreset.THIS_MONTH,
    val minAmount: Money? = null,
    val maxAmount: Money? = null,
) {
    val activeFilterCount: Int
        get() = listOfNotNull(typeFilter, categoryFilter, accountFilter, methodFilter, minAmount, maxAmount).size +
            if (datePreset != DatePreset.THIS_MONTH) 1 else 0
}

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class TransactionsViewModel @Inject constructor(
    private val transactions: TransactionRepository,
    categories: CategoryRepository,
    accounts: AccountRepository,
    private val clock: AppClock,
) : ViewModel() {

    private val criteria = MutableStateFlow(TransactionsUiState(loading = false))

    val state: StateFlow<TransactionsUiState> = combine(
        criteria
            // Keeps typing responsive without re-querying on every keystroke.
            .debounce { if (it.query.isEmpty()) 0L else SEARCH_DEBOUNCE_MILLIS }
            .flatMapLatest { current -> transactions.search(current.toFilter()) },
        criteria,
        categories.observeAll(),
        accounts.observeAll(),
    ) { results, current, categoryList, accountList ->
        current.copy(
            loading = false,
            // Payment method has no column filter in SQL because it would add a
            // sixth optional predicate for a field with seven values; filtering
            // the already-bounded result set is simpler and just as fast.
            transactions = results.filter { current.methodFilter == null || it.paymentMethod == current.methodFilter },
            categoriesById = categoryList.associateBy { it.id },
            accounts = accountList,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TransactionsUiState())

    fun onQueryChange(value: String) { criteria.value = criteria.value.copy(query = value) }
    fun onTypeFilter(value: TransactionType?) { criteria.value = criteria.value.copy(typeFilter = value) }
    fun onCategoryFilter(value: String?) { criteria.value = criteria.value.copy(categoryFilter = value) }
    fun onAccountFilter(value: String?) { criteria.value = criteria.value.copy(accountFilter = value) }
    fun onMethodFilter(value: PaymentMethod?) { criteria.value = criteria.value.copy(methodFilter = value) }
    fun onDatePreset(value: DatePreset) { criteria.value = criteria.value.copy(datePreset = value) }

    fun onAmountRange(min: Money?, max: Money?) {
        criteria.value = criteria.value.copy(minAmount = min, maxAmount = max)
    }

    fun clearFilters() {
        criteria.value = TransactionsUiState(loading = false, query = criteria.value.query)
    }

    fun delete(id: String) {
        viewModelScope.launch { transactions.delete(id) }
    }

    private fun TransactionsUiState.toFilter() = TransactionFilter(
        query = query,
        type = typeFilter,
        categoryId = categoryFilter,
        accountId = accountFilter,
        minAmount = minAmount,
        maxAmount = maxAmount,
        range = datePreset.toRange(clock.today()),
    )

    private fun DatePreset.toRange(today: LocalDate): DateRange = when (this) {
        DatePreset.THIS_MONTH -> DateRange.month(clock.currentMonth(), clock.zone)
        DatePreset.LAST_30 -> DateRange.of(today.minusDays(29), today, clock.zone)
        DatePreset.THIS_YEAR -> DateRange.of(today.withDayOfYear(1), today, clock.zone)
        DatePreset.ALL -> DateRange.all()
    }

    private companion object {
        const val SEARCH_DEBOUNCE_MILLIS = 250L
    }
}
