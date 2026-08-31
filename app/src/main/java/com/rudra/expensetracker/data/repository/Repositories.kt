package com.rudra.expensetracker.data.repository

import com.rudra.expensetracker.core.analytics.AnalyticsTransaction
import com.rudra.expensetracker.core.category.MerchantMapping
import com.rudra.expensetracker.core.money.Money
import com.rudra.expensetracker.core.sms.TransactionType
import com.rudra.expensetracker.data.local.AccountDao
import com.rudra.expensetracker.data.local.AccountEntity
import com.rudra.expensetracker.data.local.BudgetDao
import com.rudra.expensetracker.data.local.BudgetEntity
import com.rudra.expensetracker.data.local.CategoryDao
import com.rudra.expensetracker.data.local.CategoryEntity
import com.rudra.expensetracker.data.local.MerchantMappingDao
import com.rudra.expensetracker.data.local.MerchantMappingEntity
import com.rudra.expensetracker.data.local.TransactionDao
import com.rudra.expensetracker.data.local.TransactionEntity
import com.rudra.expensetracker.domain.AppClock
import com.rudra.expensetracker.domain.DateRange
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Adapts a stored row to the platform-free analytics interface. */
data class AnalyticsRow(
    override val type: TransactionType,
    override val amount: Money,
    override val categoryId: String?,
    override val merchantKey: String?,
    override val date: LocalDate,
) : AnalyticsTransaction

fun TransactionEntity.toAnalyticsRow(clock: AppClock) = AnalyticsRow(
    type = type,
    amount = Money(amountMinorUnits),
    categoryId = categoryId,
    merchantKey = upiId ?: merchant,
    date = clock.toLocalDate(occurredAtEpochMillis),
)

data class TransactionFilter(
    val query: String = "",
    val type: TransactionType? = null,
    val categoryId: String? = null,
    val accountId: String? = null,
    val minAmount: Money? = null,
    val maxAmount: Money? = null,
    val range: DateRange = DateRange.all(),
)

@Singleton
class TransactionRepository @Inject constructor(
    private val dao: TransactionDao,
    private val clock: AppClock,
) {
    fun observeRecent(limit: Int = 20): Flow<List<TransactionEntity>> = dao.observeRecent(limit)

    fun observeUnconfirmed(): Flow<List<TransactionEntity>> = dao.observeUnconfirmed()

    fun observeById(id: String): Flow<TransactionEntity?> = dao.observeById(id)

    suspend fun byId(id: String): TransactionEntity? = dao.byId(id)

    fun observeInRange(range: DateRange): Flow<List<TransactionEntity>> =
        dao.observeBetween(range.fromMillis, range.toMillis)

    fun observeAnalytics(range: DateRange): Flow<List<AnalyticsRow>> =
        observeInRange(range).map { rows -> rows.map { it.toAnalyticsRow(clock) } }

    fun observeTotal(type: TransactionType, range: DateRange, categoryId: String? = null): Flow<Money> =
        dao.observeTotal(type, range.fromMillis, range.toMillis, categoryId).map { Money(it) }

    fun search(filter: TransactionFilter): Flow<List<TransactionEntity>> = dao.search(
        query = filter.query.trim(),
        // A bare number in the search box should also match an exact amount.
        amount = Money.parseOrNull(filter.query.trim())?.minorUnits,
        type = filter.type,
        categoryId = filter.categoryId,
        accountId = filter.accountId,
        minAmount = filter.minAmount?.minorUnits,
        maxAmount = filter.maxAmount?.minorUnits,
        from = filter.range.fromMillis,
        to = filter.range.toMillis,
    )

    suspend fun all(): List<TransactionEntity> = dao.all()

    suspend fun update(transaction: TransactionEntity) =
        dao.update(transaction.copy(updatedAtEpochMillis = clock.nowMillis()))

    suspend fun delete(id: String) = dao.delete(id)

    suspend fun deleteAll() = dao.deleteAll()

    /** @return the id of a pre-existing duplicate, or null if the insert happened. */
    suspend fun insertIfNew(transaction: TransactionEntity, strongKeys: List<String>): String? =
        dao.insertIfNew(transaction, strongKeys)

    suspend fun findFuzzyDuplicate(
        fuzzyKey: String,
        amountMinorUnits: Long,
        atMillis: Long,
        windowMillis: Long,
    ): TransactionEntity? = dao.findFuzzyMatch(
        fuzzyKey, amountMinorUnits, atMillis - windowMillis, atMillis + windowMillis,
    )
}

@Singleton
class CategoryRepository @Inject constructor(private val dao: CategoryDao) {

    fun observeAll(): Flow<List<CategoryEntity>> = dao.observeAll()
    fun observeFor(type: TransactionType): Flow<List<CategoryEntity>> = dao.observeFor(type)
    suspend fun byId(id: String): CategoryEntity? = dao.byId(id)
    suspend fun upsert(category: CategoryEntity) = dao.upsert(category)
    suspend fun reorder(id: String, order: Int) = dao.reorder(id, order)

    /**
     * Built-ins are archived so that transactions already filed under them keep
     * a readable label; user-created categories are removed outright.
     */
    suspend fun delete(category: CategoryEntity) {
        if (category.isBuiltIn) dao.archiveBuiltIn(category.id) else dao.deleteCustom(category.id)
    }

    suspend fun create(name: String, iconName: String, colorArgb: Int, type: TransactionType, order: Int) {
        dao.upsert(
            CategoryEntity(
                id = "custom_${UUID.randomUUID()}",
                name = name,
                iconName = iconName,
                colorArgb = colorArgb,
                appliesTo = type,
                sortOrder = order,
                isBuiltIn = false,
            ),
        )
    }
}

@Singleton
class AccountRepository @Inject constructor(
    private val dao: AccountDao,
    private val clock: AppClock,
) {
    fun observeAll(): Flow<List<AccountEntity>> = dao.observeAll()
    suspend fun byId(id: String): AccountEntity? = dao.byId(id)
    suspend fun rename(id: String, name: String) = dao.rename(id, name)
    suspend fun archive(id: String) = dao.archive(id)

    /**
     * Finds the account this alert belongs to, creating it the first time a new
     * bank/account pair is seen so the user never has to set accounts up by hand.
     */
    suspend fun resolveOrCreate(bankCode: String?, bankName: String?, accountTail: String?): AccountEntity? {
        if (bankCode == null && accountTail == null) return null
        dao.find(bankCode, accountTail)?.let { return it }

        val last4 = accountTail?.filter { it.isDigit() }?.takeLast(4)
        val label = listOfNotNull(bankName ?: bankCode, last4?.let { "••••$it" })
            .joinToString(" ")
            .ifBlank { "Account" }
        val account = AccountEntity(
            id = "acc_${UUID.randomUUID()}",
            displayName = label,
            bankCode = bankCode,
            bankName = bankName,
            accountTail = accountTail,
            createdAtEpochMillis = clock.nowMillis(),
        )
        dao.upsert(account)
        return account
    }

    /** Records a balance the bank itself stated. Never a figure the app computed. */
    suspend fun recordReportedBalance(accountId: String, balance: Money, atMillis: Long) =
        dao.updateReportedBalance(accountId, balance.minorUnits, atMillis)
}

@Singleton
class BudgetRepository @Inject constructor(private val dao: BudgetDao) {

    fun observeForPeriod(period: String): Flow<List<BudgetEntity>> = dao.observeForPeriod(period)
    suspend fun all(): List<BudgetEntity> = dao.all()
    suspend fun find(period: String, categoryId: String?): BudgetEntity? = dao.find(period, categoryId)
    suspend fun markAlerted(id: String, state: String) = dao.markAlerted(id, state)
    suspend fun delete(id: String) = dao.delete(id)

    suspend fun setLimit(period: String, categoryId: String?, limit: Money) {
        val existing = dao.find(period, categoryId)
        dao.upsert(
            existing?.copy(limitMinorUnits = limit.minorUnits)
                ?: BudgetEntity(
                    id = "bud_${UUID.randomUUID()}",
                    categoryId = categoryId,
                    periodYearMonth = period,
                    limitMinorUnits = limit.minorUnits,
                ),
        )
    }
}

@Singleton
class MerchantMappingRepository @Inject constructor(private val dao: MerchantMappingDao) {

    fun observeAll(): Flow<List<MerchantMappingEntity>> = dao.observeAll()
    suspend fun deleteAll() = dao.deleteAll()
    suspend fun delete(key: String) = dao.delete(key)
    suspend fun all(): List<MerchantMappingEntity> = dao.all()

    suspend fun forKey(key: String): List<MerchantMapping> =
        dao.forKey(key).map { MerchantMapping(it.payeeKey, it.categoryId, it.description, it.hitCount, it.lastUsedAtEpochMillis) }

    suspend fun record(key: String, categoryId: String, description: String?, now: Long) =
        dao.record(key, categoryId, description, now)
}
