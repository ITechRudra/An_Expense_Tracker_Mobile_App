package com.rudra.expensetracker.ui.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rudra.expensetracker.data.local.TransactionEntity
import com.rudra.expensetracker.data.repository.CategoryRepository
import com.rudra.expensetracker.data.repository.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class TransactionDetailUiState(
    val transaction: TransactionEntity? = null,
    val categoryName: String? = null,
)

@HiltViewModel
class TransactionDetailViewModel @Inject constructor(
    private val transactions: TransactionRepository,
    private val categories: CategoryRepository,
) : ViewModel() {

    private val cache = mutableMapOf<String, StateFlow<TransactionDetailUiState>>()

    /** Cached per id so recomposition does not restart the query. */
    fun state(transactionId: String): StateFlow<TransactionDetailUiState> = cache.getOrPut(transactionId) {
        combine(
            transactions.observeById(transactionId),
            categories.observeAll(),
        ) { transaction, categoryList ->
            TransactionDetailUiState(
                transaction = transaction,
                categoryName = transaction?.categoryId?.let { id -> categoryList.firstOrNull { it.id == id }?.name },
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TransactionDetailUiState())
    }

    fun delete(transactionId: String) {
        viewModelScope.launch { transactions.delete(transactionId) }
    }
}
