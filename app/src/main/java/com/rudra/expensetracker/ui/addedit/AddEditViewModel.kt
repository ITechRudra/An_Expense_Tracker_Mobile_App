package com.rudra.expensetracker.ui.addedit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rudra.expensetracker.core.dedupe.TransactionFingerprint
import com.rudra.expensetracker.core.money.Money
import com.rudra.expensetracker.core.sms.PaymentMethod
import com.rudra.expensetracker.core.sms.TransactionType
import com.rudra.expensetracker.data.local.CategoryEntity
import com.rudra.expensetracker.data.local.TransactionEntity
import com.rudra.expensetracker.data.local.TransactionSource
import com.rudra.expensetracker.data.repository.CategoryRepository
import com.rudra.expensetracker.data.repository.MerchantMappingRepository
import com.rudra.expensetracker.data.repository.TransactionRepository
import com.rudra.expensetracker.domain.AppClock
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class AddEditUiState(
    val loading: Boolean = true,
    val isEditing: Boolean = false,
    val amountText: String = "",
    val type: TransactionType = TransactionType.EXPENSE,
    val description: String = "",
    val categoryId: String? = null,
    val paymentMethod: PaymentMethod = PaymentMethod.CASH,
    val notes: String = "",
    val categories: List<CategoryEntity> = emptyList(),
    val amountError: String? = null,
    val categoryError: String? = null,
    val saved: Boolean = false,
    /** Set for a transaction that came from SMS; those fields are not editable. */
    val detectedMerchant: String? = null,
    val detectedAccount: String? = null,
) {
    val amount: Money? get() = Money.parseOrNull(amountText)
    val visibleCategories: List<CategoryEntity> get() = categories.filter { it.appliesTo == type }
}

@HiltViewModel
class AddEditViewModel @Inject constructor(
    private val transactions: TransactionRepository,
    private val categories: CategoryRepository,
    private val mappings: MerchantMappingRepository,
    private val clock: AppClock,
) : ViewModel() {

    private val _state = MutableStateFlow(AddEditUiState())
    val state: StateFlow<AddEditUiState> = _state.asStateFlow()

    private var editingId: String? = null

    fun load(transactionId: String?) {
        if (!_state.value.loading) return
        viewModelScope.launch {
            val categoryList = categories.observeAll().first()
            val existing = transactionId?.let { transactions.byId(it) }
            editingId = existing?.id

            _state.value = if (existing != null) {
                AddEditUiState(
                    loading = false,
                    isEditing = true,
                    amountText = Money(existing.amountMinorUnits).toBigDecimal().toPlainString(),
                    type = existing.type,
                    description = existing.description.orEmpty(),
                    categoryId = existing.categoryId,
                    paymentMethod = existing.paymentMethod,
                    notes = existing.notes.orEmpty(),
                    categories = categoryList,
                    detectedMerchant = existing.merchant ?: existing.upiId,
                    detectedAccount = existing.bankName,
                )
            } else {
                AddEditUiState(loading = false, categories = categoryList)
            }
        }
    }

    fun onAmountChange(value: String) {
        // Accept only digits and a single decimal point while typing, so the
        // field cannot reach a state the parser will later reject.
        val filtered = value.filter { it.isDigit() || it == '.' }
        if (filtered.count { it == '.' } > 1) return
        _state.value = _state.value.copy(amountText = filtered, amountError = null)
    }

    fun onTypeChange(type: TransactionType) {
        val current = _state.value
        _state.value = current.copy(
            type = type,
            // The old category belongs to the other direction; drop it rather
            // than silently filing income under "Food".
            categoryId = current.categoryId?.takeIf { id ->
                current.categories.firstOrNull { it.id == id }?.appliesTo == type
            },
        )
    }

    fun onDescriptionChange(value: String) { _state.value = _state.value.copy(description = value) }
    fun onCategorySelect(id: String) { _state.value = _state.value.copy(categoryId = id, categoryError = null) }
    fun onMethodChange(method: PaymentMethod) { _state.value = _state.value.copy(paymentMethod = method) }
    fun onNotesChange(value: String) { _state.value = _state.value.copy(notes = value) }

    fun save() {
        val current = _state.value
        val amount = current.amount
        if (amount == null || amount.isZero) {
            _state.value = current.copy(amountError = "Enter an amount greater than zero")
            return
        }
        if (current.categoryId == null) {
            _state.value = current.copy(categoryError = "Pick a category")
            return
        }

        viewModelScope.launch {
            val now = clock.nowMillis()
            val existingId = editingId
            if (existingId != null) {
                val existing = transactions.byId(existingId) ?: return@launch
                transactions.update(
                    existing.copy(
                        type = current.type,
                        amountMinorUnits = amount.minorUnits,
                        description = current.description.trim().takeIf { it.isNotEmpty() },
                        categoryId = current.categoryId,
                        paymentMethod = current.paymentMethod,
                        notes = current.notes.trim().takeIf { it.isNotEmpty() },
                        isConfirmed = true,
                    ),
                )
                // An edit is a correction, so it teaches the suggester too.
                val payeeKey = TransactionFingerprint.normaliseCounterparty(existing.upiId ?: existing.merchant)
                mappings.record(payeeKey, current.categoryId, current.description.trim().takeIf { it.isNotEmpty() }, now)
            } else {
                val description = current.description.trim().takeIf { it.isNotEmpty() }
                val entity = TransactionEntity(
                    id = "txn_${UUID.randomUUID()}",
                    type = current.type,
                    amountMinorUnits = amount.minorUnits,
                    description = description,
                    categoryId = current.categoryId,
                    paymentMethod = current.paymentMethod,
                    occurredAtEpochMillis = now,
                    createdAtEpochMillis = now,
                    updatedAtEpochMillis = now,
                    source = TransactionSource.MANUAL,
                    isConfirmed = true,
                    notes = current.notes.trim().takeIf { it.isNotEmpty() },
                    fuzzyKey = TransactionFingerprint.fuzzyKey(
                        current.type, amount.minorUnits, null, description,
                    ),
                )
                // A manual entry has no bank reference, so it carries no strong
                // keys and can never collide with a detected transaction.
                transactions.insertIfNew(entity, emptyList())
            }
            _state.value = _state.value.copy(saved = true)
        }
    }
}
