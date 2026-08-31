package com.rudra.expensetracker.data.export

import android.content.Context
import android.net.Uri
import com.rudra.expensetracker.core.sms.PaymentMethod
import com.rudra.expensetracker.core.sms.TransactionType
import com.rudra.expensetracker.data.local.AccountEntity
import com.rudra.expensetracker.data.local.BudgetEntity
import com.rudra.expensetracker.data.local.CategoryEntity
import com.rudra.expensetracker.data.local.TransactionEntity
import com.rudra.expensetracker.data.local.TransactionSource
import com.rudra.expensetracker.data.repository.AccountRepository
import com.rudra.expensetracker.data.repository.BudgetRepository
import com.rudra.expensetracker.data.repository.CategoryRepository
import com.rudra.expensetracker.data.repository.MerchantMappingRepository
import com.rudra.expensetracker.data.repository.TransactionRepository
import com.rudra.expensetracker.domain.AppClock
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

data class RestoreReport(val restored: Int, val skippedDuplicates: Int)

/**
 * Restores a JSON backup.
 *
 * The restore is additive and duplicate-safe: rows whose ids or strong
 * identifiers already exist are skipped, so restoring the same file twice, or
 * merging a backup into a device that has been in use, cannot double a balance.
 */
@Singleton
class DataImporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val transactions: TransactionRepository,
    private val accounts: AccountRepository,
    private val categories: CategoryRepository,
    private val budgets: BudgetRepository,
    private val mappings: MerchantMappingRepository,
    private val clock: AppClock,
) {

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun restore(source: Uri): RestoreReport = withContext(Dispatchers.IO) {
        val text = context.contentResolver.openInputStream(source)?.bufferedReader()?.use { it.readText() }
            ?: error("Could not read the selected file")

        val document = json.decodeFromString<BackupDocument>(text)
        require(document.formatVersion <= BackupDocument.FORMAT_VERSION) {
            "This backup was written by a newer version of the app"
        }

        for (record in document.categories) {
            categories.upsert(
                CategoryEntity(
                    id = record.id,
                    name = record.name,
                    iconName = record.iconName,
                    colorArgb = record.colorArgb,
                    appliesTo = enumOrDefault(record.appliesTo, TransactionType.EXPENSE),
                    sortOrder = record.sortOrder,
                    isBuiltIn = record.isBuiltIn,
                ),
            )
        }

        for (record in document.accounts) {
            accounts.resolveOrCreate(record.bankCode, record.bankName, record.accountTail)
        }
        val accountsByOldId = document.accounts.associate { record ->
            record.id to accounts.resolveOrCreate(record.bankCode, record.bankName, record.accountTail)?.id
        }

        var restored = 0
        var skipped = 0
        val now = clock.nowMillis()
        for (record in document.transactions) {
            val entity = TransactionEntity(
                id = record.id,
                type = enumOrDefault(record.type, TransactionType.EXPENSE),
                amountMinorUnits = record.amountMinorUnits,
                currencyCode = record.currencyCode,
                description = record.description,
                categoryId = record.categoryId,
                merchant = record.merchant,
                upiId = record.upiId,
                accountId = accountsByOldId[record.accountId] ?: record.accountId,
                bankName = record.bankName,
                accountTail = record.accountTail,
                paymentMethod = enumOrDefault(record.paymentMethod, PaymentMethod.OTHER),
                referenceId = record.referenceId,
                rrn = record.rrn,
                availableBalanceMinorUnits = record.availableBalanceMinorUnits,
                occurredAtEpochMillis = record.occurredAtEpochMillis,
                createdAtEpochMillis = now,
                updatedAtEpochMillis = now,
                source = enumOrDefault(record.source, TransactionSource.IMPORTED),
                isConfirmed = record.isConfirmed,
                notes = record.notes,
                fuzzyKey = com.rudra.expensetracker.core.dedupe.TransactionFingerprint.fuzzyKey(
                    enumOrDefault(record.type, TransactionType.EXPENSE),
                    record.amountMinorUnits,
                    record.accountTail?.filter { it.isDigit() }?.takeLast(4),
                    record.upiId ?: record.merchant,
                ),
            )
            val strongKeys = buildList {
                record.rrn?.let { add("rrn:${it.uppercase()}") }
                record.referenceId?.let { add("ref:${it.uppercase()}") }
            }
            if (transactions.byId(record.id) != null) {
                skipped++
            } else if (transactions.insertIfNew(entity, strongKeys) != null) {
                skipped++
            } else {
                restored++
            }
        }

        for (record in document.budgets) {
            budgets.setLimit(
                record.periodYearMonth,
                record.categoryId,
                com.rudra.expensetracker.core.money.Money(record.limitMinorUnits),
            )
        }
        for (record in document.merchantMappings) {
            mappings.record(record.payeeKey, record.categoryId, record.description, now)
        }

        RestoreReport(restored, skipped)
    }

    /** A value written by a newer build must not abort the whole restore. */
    private inline fun <reified T : Enum<T>> enumOrDefault(value: String, default: T): T =
        runCatching { enumValueOf<T>(value) }.getOrDefault(default)
}
