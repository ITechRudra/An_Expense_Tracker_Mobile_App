package com.rudra.expensetracker.ui.quickadd

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rudra.expensetracker.core.category.CategorySuggestion
import com.rudra.expensetracker.core.category.CategorySuggestionEngine
import com.rudra.expensetracker.core.dedupe.TransactionFingerprint
import com.rudra.expensetracker.core.money.Money
import com.rudra.expensetracker.core.sms.ParsedTransaction
import com.rudra.expensetracker.core.sms.PaymentMethod
import com.rudra.expensetracker.core.sms.TransactionType
import com.rudra.expensetracker.data.local.CategoryEntity
import com.rudra.expensetracker.data.local.TransactionEntity
import com.rudra.expensetracker.data.local.TransactionSource
import com.rudra.expensetracker.data.prefs.SettingsRepository
import com.rudra.expensetracker.data.prefs.ThemeMode
import com.rudra.expensetracker.data.repository.CategoryRepository
import com.rudra.expensetracker.data.repository.MerchantMappingRepository
import com.rudra.expensetracker.data.repository.TransactionRepository
import com.rudra.expensetracker.domain.AppClock
import com.rudra.expensetracker.domain.ConfirmTransactionUseCase
import com.rudra.expensetracker.notification.TransactionNotifier
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class QuickAddUiState(
    val loading: Boolean = true,
    /** Set when categorising a detected transaction rather than adding one. */
    val detected: TransactionEntity? = null,
    val suggestion: CategorySuggestion? = null,
    val categories: List<CategoryEntity> = emptyList(),
    val amountText: String = "",
    val description: String = "",
    val categoryId: String? = null,
    val type: TransactionType = TransactionType.EXPENSE,
    val saved: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
) {
    val isCategorising: Boolean get() = detected != null
    val visibleCategories: List<CategoryEntity>
        get() = categories.filter { it.appliesTo == (detected?.type ?: type) }
}

@HiltViewModel
class QuickAddViewModel @Inject constructor(
    private val transactions: TransactionRepository,
    private val categories: CategoryRepository,
    private val mappings: MerchantMappingRepository,
    private val suggestionEngine: CategorySuggestionEngine,
    private val confirm: ConfirmTransactionUseCase,
    private val notifier: TransactionNotifier,
    private val settings: SettingsRepository,
    private val clock: AppClock,
) : ViewModel() {

    private val _state = MutableStateFlow(QuickAddUiState())
    val state: StateFlow<QuickAddUiState> = _state.asStateFlow()

    fun load(transactionId: String?) {
        if (!_state.value.loading) return
        viewModelScope.launch {
            val categoryList = categories.observeAll().first()
            val theme = settings.themeMode.first()
            val dynamic = settings.dynamicColorEnabled.first()
            val detected = transactionId?.let { transactions.byId(it) }

            val suggestion = detected?.let { entity ->
                val parsed = entity.toParsed()
                suggestionEngine.suggest(parsed, mappings.forKey(suggestionEngine.payeeKey(parsed)))
            }

            _state.value = QuickAddUiState(
                loading = false,
                detected = detected,
                suggestion = suggestion,
                categories = categoryList,
                // A suggestion is shown pre-selected but always visible and
                // changeable; it is never applied without the user tapping Save.
                categoryId = suggestion?.categoryId,
                description = suggestion?.description.orEmpty(),
                themeMode = theme,
                dynamicColor = dynamic,
            )
        }
    }

    fun onAmountChange(value: String) {
        val filtered = value.filter { it.isDigit() || it == '.' }
        if (filtered.count { it == '.' } > 1) return
        _state.value = _state.value.copy(amountText = filtered)
    }

    fun onDescriptionChange(value: String) { _state.value = _state.value.copy(description = value) }
    fun onCategorySelect(id: String) { _state.value = _state.value.copy(categoryId = id) }
    fun onTypeChange(type: TransactionType) {
        _state.value = _state.value.copy(type = type, categoryId = null)
    }

    fun save() {
        val current = _state.value
        val categoryId = current.categoryId ?: return

        viewModelScope.launch {
            val detected = current.detected
            if (detected != null) {
                val updated = confirm(detected.id, categoryId, current.description)
                if (updated != null) {
                    val name = categories.byId(categoryId)?.name ?: categoryId
                    notifier.showConfirmed(updated, name)
                }
            } else {
                val amount = Money.parseOrNull(current.amountText) ?: return@launch
                if (amount.isZero) return@launch
                val now = clock.nowMillis()
                val description = current.description.trim().takeIf { it.isNotEmpty() }
                transactions.insertIfNew(
                    TransactionEntity(
                        id = "txn_${UUID.randomUUID()}",
                        type = current.type,
                        amountMinorUnits = amount.minorUnits,
                        description = description,
                        categoryId = categoryId,
                        paymentMethod = PaymentMethod.CASH,
                        occurredAtEpochMillis = now,
                        createdAtEpochMillis = now,
                        updatedAtEpochMillis = now,
                        source = TransactionSource.MANUAL,
                        isConfirmed = true,
                        fuzzyKey = TransactionFingerprint.fuzzyKey(
                            current.type, amount.minorUnits, null, description,
                        ),
                    ),
                    emptyList(),
                )
            }
            _state.value = _state.value.copy(saved = true)
        }
    }

    fun dismissDetected() {
        val detected = _state.value.detected ?: return
        viewModelScope.launch {
            if (!detected.isConfirmed) transactions.delete(detected.id)
            notifier.cancel(detected.id)
            _state.value = _state.value.copy(saved = true)
        }
    }

    private fun TransactionEntity.toParsed() = ParsedTransaction(
        type = type,
        amount = Money(amountMinorUnits),
        bankCode = bankCode,
        bankName = bankName,
        accountTail = accountTail,
        upiId = upiId,
        merchant = merchant,
    )
}
