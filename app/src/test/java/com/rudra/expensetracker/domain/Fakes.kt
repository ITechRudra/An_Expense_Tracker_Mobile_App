package com.rudra.expensetracker.domain

import com.rudra.expensetracker.data.local.AccountDao
import com.rudra.expensetracker.data.local.AccountEntity
import com.rudra.expensetracker.data.local.IngestLogDao
import com.rudra.expensetracker.data.local.IngestLogEntity
import com.rudra.expensetracker.data.local.MerchantMappingDao
import com.rudra.expensetracker.data.local.MerchantMappingEntity
import com.rudra.expensetracker.data.local.TransactionDao
import com.rudra.expensetracker.data.local.TransactionEntity
import com.rudra.expensetracker.data.local.TransactionKeyEntity
import com.rudra.expensetracker.core.sms.TransactionType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * In-memory doubles for the DAOs the ingestion path touches.
 *
 * These reproduce the behaviour the ingestion logic actually depends on -- in
 * particular that claiming a strong key twice fails -- so the use-case tests
 * exercise real duplicate handling rather than a permissive stub.
 */
class FakeTransactionDao : TransactionDao {

    val transactions = MutableStateFlow<List<TransactionEntity>>(emptyList())
    private val keys = mutableMapOf<String, String>()

    override suspend fun byId(id: String) = transactions.value.firstOrNull { it.id == id }
    override fun observeById(id: String) = transactions.map { list -> list.firstOrNull { it.id == id } }
    override fun observeBetween(from: Long, to: Long) = transactions.map { list ->
        list.filter { it.occurredAtEpochMillis in from..to }.sortedByDescending { it.occurredAtEpochMillis }
    }
    override fun observeRecent(limit: Int) =
        transactions.map { it.sortedByDescending { row -> row.occurredAtEpochMillis }.take(limit) }
    override fun observeUnconfirmed() = transactions.map { list -> list.filter { !it.isConfirmed } }
    override suspend fun all() = transactions.value

    override fun search(
        query: String, amount: Long?, type: TransactionType?, categoryId: String?,
        accountId: String?, minAmount: Long?, maxAmount: Long?, from: Long, to: Long,
    ): Flow<List<TransactionEntity>> = transactions

    override fun observeTotal(type: TransactionType, from: Long, to: Long, categoryId: String?) =
        transactions.map { list ->
            list.filter { it.type == type && it.isConfirmed && it.occurredAtEpochMillis in from..to }
                .sumOf { it.amountMinorUnits }
        }

    override suspend fun update(transaction: TransactionEntity) {
        transactions.value = transactions.value.map { if (it.id == transaction.id) transaction else it }
    }

    override suspend fun delete(id: String) {
        transactions.value = transactions.value.filterNot { it.id == id }
    }

    override suspend fun deleteAll() {
        transactions.value = emptyList()
        keys.clear()
    }

    override suspend fun insertTransaction(transaction: TransactionEntity) {
        require(transactions.value.none { it.id == transaction.id }) { "duplicate primary key" }
        transactions.value = transactions.value + transaction
    }

    override suspend fun insertKeys(keyList: List<TransactionKeyEntity>) {
        for (key in keyList) {
            require(key.key !in keys) { "duplicate key ${key.key}" }
            keys[key.key] = key.transactionId
        }
    }

    override suspend fun findByStrongKeys(keyList: List<String>): String? =
        keyList.firstNotNullOfOrNull { keys[it] }

    override suspend fun findFuzzyMatch(fuzzyKey: String, amount: Long, from: Long, to: Long) =
        transactions.value.firstOrNull {
            it.fuzzyKey == fuzzyKey && it.amountMinorUnits == amount &&
                it.occurredAtEpochMillis in from..to
        }
}

class FakeAccountDao : AccountDao {
    val accounts = MutableStateFlow<List<AccountEntity>>(emptyList())

    override fun observeAll() = accounts
    override suspend fun find(bankCode: String?, tail: String?) =
        accounts.value.firstOrNull { it.bankCode == bankCode && it.accountTail == tail }
    override suspend fun byId(id: String) = accounts.value.firstOrNull { it.id == id }
    override suspend fun upsert(account: AccountEntity) {
        accounts.value = accounts.value.filterNot { it.id == account.id } + account
    }
    override suspend fun rename(id: String, name: String) {
        accounts.value = accounts.value.map { if (it.id == id) it.copy(displayName = name) else it }
    }
    override suspend fun updateReportedBalance(id: String, balance: Long, at: Long) {
        accounts.value = accounts.value.map {
            if (it.id == id) it.copy(lastReportedBalanceMinorUnits = balance, lastReportedBalanceAtEpochMillis = at)
            else it
        }
    }
    override suspend fun archive(id: String) {
        accounts.value = accounts.value.map { if (it.id == id) it.copy(isArchived = true) else it }
    }
}

class FakeMerchantMappingDao : MerchantMappingDao {
    val mappings = MutableStateFlow<List<MerchantMappingEntity>>(emptyList())

    override suspend fun forKey(key: String) = mappings.value.filter { it.payeeKey == key }
    override fun observeAll() = mappings
    override suspend fun all() = mappings.value
    override suspend fun upsert(mapping: MerchantMappingEntity) {
        mappings.value = mappings.value.filterNot { it.payeeKey == mapping.payeeKey } + mapping
    }
    override suspend fun delete(key: String) {
        mappings.value = mappings.value.filterNot { it.payeeKey == key }
    }
    override suspend fun deleteAll() { mappings.value = emptyList() }
}

class FakeIngestLogDao : IngestLogDao {
    val entries = mutableListOf<IngestLogEntity>()

    override fun observeRecent(limit: Int) = MutableStateFlow(entries.take(limit).toList())
    override suspend fun insert(entry: IngestLogEntity) { entries += entry }
    override suspend fun pruneBefore(before: Long) { entries.removeAll { it.createdAtEpochMillis < before } }
    override suspend fun deleteAll() { entries.clear() }
}
