package com.rudra.expensetracker.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import com.rudra.expensetracker.core.sms.TransactionType
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountDao {

    @Query("SELECT * FROM accounts WHERE isArchived = 0 ORDER BY displayName")
    fun observeAll(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts WHERE bankCode IS :bankCode AND accountTail IS :tail LIMIT 1")
    suspend fun find(bankCode: String?, tail: String?): AccountEntity?

    @Query("SELECT * FROM accounts WHERE id = :id")
    suspend fun byId(id: String): AccountEntity?

    @Upsert
    suspend fun upsert(account: AccountEntity)

    @Query("UPDATE accounts SET displayName = :name WHERE id = :id")
    suspend fun rename(id: String, name: String)

    @Query(
        """
        UPDATE accounts
        SET lastReportedBalanceMinorUnits = :balance, lastReportedBalanceAtEpochMillis = :at
        WHERE id = :id AND (lastReportedBalanceAtEpochMillis IS NULL OR lastReportedBalanceAtEpochMillis <= :at)
        """
    )
    suspend fun updateReportedBalance(id: String, balance: Long, at: Long)

    @Query("UPDATE accounts SET isArchived = 1 WHERE id = :id")
    suspend fun archive(id: String)
}

@Dao
interface CategoryDao {

    @Query("SELECT * FROM categories WHERE isArchived = 0 ORDER BY sortOrder, name")
    fun observeAll(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE isArchived = 0 AND appliesTo = :type ORDER BY sortOrder, name")
    fun observeFor(type: TransactionType): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun byId(id: String): CategoryEntity?

    @Query("SELECT COUNT(*) FROM categories")
    suspend fun count(): Int

    @Upsert
    suspend fun upsert(category: CategoryEntity)

    @Upsert
    suspend fun upsertAll(categories: List<CategoryEntity>)

    /** Built-ins are archived rather than deleted so historic rows keep their label. */
    @Query("UPDATE categories SET isArchived = 1 WHERE id = :id AND isBuiltIn = 1")
    suspend fun archiveBuiltIn(id: String)

    @Query("DELETE FROM categories WHERE id = :id AND isBuiltIn = 0")
    suspend fun deleteCustom(id: String)

    @Query("UPDATE categories SET sortOrder = :order WHERE id = :id")
    suspend fun reorder(id: String, order: Int)
}

@Dao
interface TransactionDao {

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun byId(id: String): TransactionEntity?

    @Query("SELECT * FROM transactions WHERE id = :id")
    fun observeById(id: String): Flow<TransactionEntity?>

    @Query(
        """
        SELECT * FROM transactions
        WHERE occurredAtEpochMillis BETWEEN :from AND :to
        ORDER BY occurredAtEpochMillis DESC
        """
    )
    fun observeBetween(from: Long, to: Long): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions ORDER BY occurredAtEpochMillis DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE isConfirmed = 0 ORDER BY occurredAtEpochMillis DESC")
    fun observeUnconfirmed(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions ORDER BY occurredAtEpochMillis DESC")
    suspend fun all(): List<TransactionEntity>

    /**
     * Free-text search across every identifier a user might remember, plus an
     * exact amount match when the query parses as a number.
     */
    @Query(
        """
        SELECT * FROM transactions
        WHERE (:query = ''
            OR description LIKE '%' || :query || '%'
            OR merchant LIKE '%' || :query || '%'
            OR upiId LIKE '%' || :query || '%'
            OR counterparty LIKE '%' || :query || '%'
            OR notes LIKE '%' || :query || '%'
            OR bankName LIKE '%' || :query || '%'
            OR accountTail LIKE '%' || :query || '%'
            OR referenceId LIKE '%' || :query || '%'
            OR rrn LIKE '%' || :query || '%'
            OR (:amount IS NOT NULL AND amountMinorUnits = :amount))
          AND (:type IS NULL OR type = :type)
          AND (:categoryId IS NULL OR categoryId = :categoryId)
          AND (:accountId IS NULL OR accountId = :accountId)
          AND (:minAmount IS NULL OR amountMinorUnits >= :minAmount)
          AND (:maxAmount IS NULL OR amountMinorUnits <= :maxAmount)
          AND occurredAtEpochMillis BETWEEN :from AND :to
        ORDER BY occurredAtEpochMillis DESC
        """
    )
    fun search(
        query: String,
        amount: Long?,
        type: TransactionType?,
        categoryId: String?,
        accountId: String?,
        minAmount: Long?,
        maxAmount: Long?,
        from: Long,
        to: Long,
    ): Flow<List<TransactionEntity>>

    @Query(
        """
        SELECT COALESCE(SUM(amountMinorUnits), 0) FROM transactions
        WHERE type = :type AND isConfirmed = 1
          AND occurredAtEpochMillis BETWEEN :from AND :to
          AND (:categoryId IS NULL OR categoryId = :categoryId)
        """
    )
    fun observeTotal(type: TransactionType, from: Long, to: Long, categoryId: String?): Flow<Long>

    @Update
    suspend fun update(transaction: TransactionEntity)

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM transactions")
    suspend fun deleteAll()

    // ------------------------------------------------------------ dedupe

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertTransaction(transaction: TransactionEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertKeys(keys: List<TransactionKeyEntity>)

    @Query("SELECT transactionId FROM transaction_keys WHERE `key` IN (:keys) LIMIT 1")
    suspend fun findByStrongKeys(keys: List<String>): String?

    @Query(
        """
        SELECT * FROM transactions
        WHERE fuzzyKey = :fuzzyKey AND amountMinorUnits = :amount
          AND occurredAtEpochMillis BETWEEN :from AND :to
        LIMIT 1
        """
    )
    suspend fun findFuzzyMatch(fuzzyKey: String, amount: Long, from: Long, to: Long): TransactionEntity?

    /**
     * Inserts a transaction and claims its strong identifiers atomically.
     *
     * @return the id of the existing transaction if one already owns any of
     *         these keys, or null when the insert succeeded.
     */
    @Transaction
    suspend fun insertIfNew(transaction: TransactionEntity, strongKeys: List<String>): String? {
        findByStrongKeys(strongKeys)?.let { return it }
        insertTransaction(transaction)
        if (strongKeys.isNotEmpty()) {
            insertKeys(strongKeys.map { TransactionKeyEntity(it, transaction.id) })
        }
        return null
    }
}

@Dao
interface BudgetDao {

    @Query("SELECT * FROM budgets WHERE periodYearMonth = :period")
    fun observeForPeriod(period: String): Flow<List<BudgetEntity>>

    @Query("SELECT * FROM budgets WHERE periodYearMonth = :period AND categoryId IS :categoryId LIMIT 1")
    suspend fun find(period: String, categoryId: String?): BudgetEntity?

    @Upsert
    suspend fun upsert(budget: BudgetEntity)

    @Query("UPDATE budgets SET lastAlertedState = :state WHERE id = :id")
    suspend fun markAlerted(id: String, state: String)

    @Query("DELETE FROM budgets WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT * FROM budgets")
    suspend fun all(): List<BudgetEntity>
}

@Dao
interface MerchantMappingDao {

    @Query("SELECT * FROM merchant_mappings WHERE payeeKey = :key")
    suspend fun forKey(key: String): List<MerchantMappingEntity>

    @Query("SELECT * FROM merchant_mappings ORDER BY hitCount DESC, lastUsedAtEpochMillis DESC")
    fun observeAll(): Flow<List<MerchantMappingEntity>>

    @Query("SELECT * FROM merchant_mappings")
    suspend fun all(): List<MerchantMappingEntity>

    @Upsert
    suspend fun upsert(mapping: MerchantMappingEntity)

    @Query("DELETE FROM merchant_mappings WHERE payeeKey = :key")
    suspend fun delete(key: String)

    @Query("DELETE FROM merchant_mappings")
    suspend fun deleteAll()

    /**
     * Records a confirmation, incrementing the hit count when the same category
     * is chosen again and resetting it when the user changes their mind.
     */
    @Transaction
    suspend fun record(key: String, categoryId: String, description: String?, now: Long) {
        if (key.isBlank()) return
        val existing = forKey(key).firstOrNull()
        val hits = if (existing != null && existing.categoryId == categoryId) existing.hitCount + 1 else 1
        upsert(MerchantMappingEntity(key, categoryId, description, hits, now))
    }
}

@Dao
interface IngestLogDao {

    @Query("SELECT * FROM ingest_log ORDER BY createdAtEpochMillis DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<IngestLogEntity>>

    @Insert
    suspend fun insert(entry: IngestLogEntity)

    /** Keeps the diagnostic log bounded; it is a debugging aid, not a record. */
    @Query("DELETE FROM ingest_log WHERE createdAtEpochMillis < :before")
    suspend fun pruneBefore(before: Long)

    @Query("DELETE FROM ingest_log")
    suspend fun deleteAll()
}
