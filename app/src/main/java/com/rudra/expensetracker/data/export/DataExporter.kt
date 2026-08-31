package com.rudra.expensetracker.data.export

import android.content.Context
import android.net.Uri
import com.rudra.expensetracker.core.money.Money
import com.rudra.expensetracker.data.local.AccountEntity
import com.rudra.expensetracker.data.local.BudgetEntity
import com.rudra.expensetracker.data.local.CategoryEntity
import com.rudra.expensetracker.data.local.MerchantMappingEntity
import com.rudra.expensetracker.data.local.TransactionEntity
import com.rudra.expensetracker.data.repository.BudgetRepository
import com.rudra.expensetracker.data.repository.MerchantMappingRepository
import com.rudra.expensetracker.data.repository.TransactionRepository
import com.rudra.expensetracker.domain.AppClock
import com.rudra.expensetracker.util.accountLabel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Everything the app knows, in one restorable document. */
@Serializable
data class BackupDocument(
    val formatVersion: Int = FORMAT_VERSION,
    val exportedAtEpochMillis: Long,
    val transactions: List<TransactionRecord>,
    val accounts: List<AccountRecord>,
    val categories: List<CategoryRecord>,
    val budgets: List<BudgetRecord>,
    val merchantMappings: List<MappingRecord>,
) {
    companion object {
        /** Bumped whenever a field is removed or its meaning changes. */
        const val FORMAT_VERSION = 1
    }
}

@Serializable
data class TransactionRecord(
    val id: String,
    val type: String,
    val amountMinorUnits: Long,
    val currencyCode: String,
    val description: String?,
    val categoryId: String?,
    val merchant: String?,
    val upiId: String?,
    val accountId: String?,
    val bankName: String?,
    val accountTail: String?,
    val paymentMethod: String,
    val referenceId: String?,
    val rrn: String?,
    val availableBalanceMinorUnits: Long?,
    val occurredAtEpochMillis: Long,
    val source: String,
    val isConfirmed: Boolean,
    val notes: String?,
)

@Serializable
data class AccountRecord(
    val id: String,
    val displayName: String,
    val bankCode: String?,
    val bankName: String?,
    val accountTail: String?,
)

@Serializable
data class CategoryRecord(
    val id: String,
    val name: String,
    val iconName: String,
    val colorArgb: Int,
    val appliesTo: String,
    val sortOrder: Int,
    val isBuiltIn: Boolean,
)

@Serializable
data class BudgetRecord(
    val id: String,
    val categoryId: String?,
    val periodYearMonth: String,
    val limitMinorUnits: Long,
)

@Serializable
data class MappingRecord(
    val payeeKey: String,
    val categoryId: String,
    val description: String?,
    val hitCount: Int,
)

/**
 * Writes the user's data out in formats they can actually use elsewhere.
 *
 * Export is always user-initiated and always to a location the user picks
 * through the system file picker. The app has no network permission, so this is
 * the only way data ever leaves it.
 */
@Singleton
class DataExporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val transactions: TransactionRepository,
    private val budgets: BudgetRepository,
    private val mappings: MerchantMappingRepository,
    private val accountsFlow: com.rudra.expensetracker.data.repository.AccountRepository,
    private val categoriesFlow: com.rudra.expensetracker.data.repository.CategoryRepository,
    private val clock: AppClock,
) {

    private val json = Json { prettyPrint = true; encodeDefaults = true }

    suspend fun exportCsv(target: Uri): Int = withContext(Dispatchers.IO) {
        val rows = transactions.all()
        val categories = categoriesFlow.observeAll().first().associateBy { it.id }
        context.contentResolver.openOutputStream(target)?.bufferedWriter()?.use { writer ->
            writer.appendLine(CSV_HEADER)
            for (t in rows) {
                writer.appendLine(csvRow(t, categories[t.categoryId]?.name))
            }
        } ?: error("Could not open the selected file for writing")
        rows.size
    }

    suspend fun exportJson(target: Uri): Int = withContext(Dispatchers.IO) {
        val document = buildBackup()
        context.contentResolver.openOutputStream(target)?.bufferedWriter()?.use { writer ->
            writer.write(json.encodeToString(document))
        } ?: error("Could not open the selected file for writing")
        document.transactions.size
    }

    private suspend fun buildBackup(): BackupDocument = BackupDocument(
        exportedAtEpochMillis = clock.nowMillis(),
        transactions = transactions.all().map(::toRecord),
        accounts = accountsFlow.observeAll().first().map(::toRecord),
        categories = categoriesFlow.observeAll().first().map(::toRecord),
        budgets = budgets.all().map(::toRecord),
        merchantMappings = mappings.all().map(::toRecord),
    )

    private fun csvRow(t: TransactionEntity, categoryName: String?): String = listOf(
        clock.toLocalDateTime(t.occurredAtEpochMillis).toString(),
        t.type.name,
        Money(t.amountMinorUnits).toBigDecimal().toPlainString(),
        t.currencyCode,
        categoryName.orEmpty(),
        t.description.orEmpty(),
        t.merchant.orEmpty(),
        t.upiId.orEmpty(),
        accountLabel(t.bankName, t.accountTail).orEmpty(),
        t.paymentMethod.name,
        t.referenceId.orEmpty(),
        t.rrn.orEmpty(),
        t.availableBalanceMinorUnits?.let { Money(it).toBigDecimal().toPlainString() }.orEmpty(),
        t.source.name,
        if (t.isConfirmed) "yes" else "no",
        t.notes.orEmpty(),
    ).joinToString(",") { escapeCsv(it) }

    /**
     * Quotes a CSV field and neutralises spreadsheet formula injection.
     *
     * A merchant name the app never chose could begin with '=' or '+', which
     * Excel would execute on open. Prefixing an apostrophe keeps it as text.
     */
    private fun escapeCsv(value: String): String {
        val guarded = if (value.firstOrNull() in FORMULA_TRIGGERS) "'$value" else value
        val escaped = guarded.replace("\"", "\"\"")
        return "\"$escaped\""
    }

    private fun toRecord(t: TransactionEntity) = TransactionRecord(
        t.id, t.type.name, t.amountMinorUnits, t.currencyCode, t.description, t.categoryId,
        t.merchant, t.upiId, t.accountId, t.bankName, t.accountTail, t.paymentMethod.name,
        t.referenceId, t.rrn, t.availableBalanceMinorUnits, t.occurredAtEpochMillis,
        t.source.name, t.isConfirmed, t.notes,
    )

    private fun toRecord(a: AccountEntity) =
        AccountRecord(a.id, a.displayName, a.bankCode, a.bankName, a.accountTail)

    private fun toRecord(c: CategoryEntity) =
        CategoryRecord(c.id, c.name, c.iconName, c.colorArgb, c.appliesTo.name, c.sortOrder, c.isBuiltIn)

    private fun toRecord(b: BudgetEntity) =
        BudgetRecord(b.id, b.categoryId, b.periodYearMonth, b.limitMinorUnits)

    private fun toRecord(m: MerchantMappingEntity) =
        MappingRecord(m.payeeKey, m.categoryId, m.description, m.hitCount)

    private companion object {
        const val CSV_HEADER =
            "date,type,amount,currency,category,description,merchant,upi_id,account," +
                "payment_method,reference_id,rrn,available_balance,source,confirmed,notes"
        val FORMULA_TRIGGERS = setOf('=', '+', '-', '@', '\t', '\r')
    }
}
