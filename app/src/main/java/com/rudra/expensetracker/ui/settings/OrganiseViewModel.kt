package com.rudra.expensetracker.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rudra.expensetracker.core.sms.TransactionType
import com.rudra.expensetracker.data.local.AccountEntity
import com.rudra.expensetracker.data.local.CategoryEntity
import com.rudra.expensetracker.data.local.IngestLogDao
import com.rudra.expensetracker.data.local.IngestLogEntity
import com.rudra.expensetracker.data.local.MerchantMappingEntity
import com.rudra.expensetracker.data.repository.AccountRepository
import com.rudra.expensetracker.data.repository.CategoryRepository
import com.rudra.expensetracker.data.repository.MerchantMappingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class OrganiseUiState(
    val categories: List<CategoryEntity> = emptyList(),
    val accounts: List<AccountEntity> = emptyList(),
    val mappings: List<MerchantMappingEntity> = emptyList(),
    val ingestLog: List<IngestLogEntity> = emptyList(),
)

/** Backs the four small management screens reached from Settings. */
@HiltViewModel
class OrganiseViewModel @Inject constructor(
    private val categories: CategoryRepository,
    private val accounts: AccountRepository,
    private val mappings: MerchantMappingRepository,
    ingestLog: IngestLogDao,
) : ViewModel() {

    val state: StateFlow<OrganiseUiState> = combine(
        categories.observeAll(),
        accounts.observeAll(),
        mappings.observeAll(),
        ingestLog.observeRecent(INGEST_LOG_LIMIT),
    ) { categoryList, accountList, mappingList, log ->
        OrganiseUiState(categoryList, accountList, mappingList, log)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), OrganiseUiState())

    fun createCategory(name: String, type: TransactionType) {
        viewModelScope.launch {
            val existing = categories.observeAll().first()
            categories.create(
                name = name,
                iconName = "Label",
                colorArgb = PALETTE[existing.size % PALETTE.size],
                type = type,
                order = existing.count { it.appliesTo == type },
            )
        }
    }

    fun renameCategory(id: String, name: String) {
        viewModelScope.launch {
            categories.byId(id)?.let { categories.upsert(it.copy(name = name)) }
        }
    }

    fun deleteCategory(id: String) {
        viewModelScope.launch {
            categories.byId(id)?.let { categories.delete(it) }
        }
    }

    fun renameAccount(id: String, name: String) {
        viewModelScope.launch { accounts.rename(id, name) }
    }

    fun forget(payeeKey: String) {
        viewModelScope.launch { mappings.delete(payeeKey) }
    }

    private companion object {
        const val INGEST_LOG_LIMIT = 100

        /** Cycled through so a new custom category is never colourless. */
        val PALETTE = listOf(
            0xFFEF6C00.toInt(), 0xFF1E88E5.toInt(), 0xFF8E24AA.toInt(), 0xFFD81B60.toInt(),
            0xFF3949AB.toInt(), 0xFF00897B.toInt(), 0xFFE53935.toInt(), 0xFF43A047.toInt(),
        )
    }
}
