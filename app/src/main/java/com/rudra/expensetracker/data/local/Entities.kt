package com.rudra.expensetracker.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.rudra.expensetracker.core.sms.PaymentMethod
import com.rudra.expensetracker.core.sms.TransactionType

/** How a transaction entered the app. Shown to the user and used in filters. */
enum class TransactionSource { SMS, MANUAL, IMPORTED }

@Entity(
    tableName = "accounts",
    indices = [Index(value = ["bankCode", "accountTail"], unique = true)],
)
data class AccountEntity(
    @PrimaryKey val id: String,
    /** User-editable label; defaults to "<Bank> ••••<last4>". */
    val displayName: String,
    val bankCode: String?,
    val bankName: String?,
    /** The masked token as the bank prints it, e.g. "XX7375". */
    val accountTail: String?,
    /** Last balance the bank itself reported, never a figure we computed. */
    val lastReportedBalanceMinorUnits: Long? = null,
    val lastReportedBalanceAtEpochMillis: Long? = null,
    val isArchived: Boolean = false,
    val createdAtEpochMillis: Long,
)

@Entity(tableName = "categories", indices = [Index(value = ["sortOrder"])])
data class CategoryEntity(
    @PrimaryKey val id: String,
    val name: String,
    /** Name of a Material icon, resolved in the UI layer. */
    val iconName: String,
    val colorArgb: Int,
    val appliesTo: TransactionType,
    val sortOrder: Int,
    /** Built-in categories may be renamed and reordered but not deleted. */
    val isBuiltIn: Boolean = false,
    val isArchived: Boolean = false,
)

@Entity(
    tableName = "transactions",
    indices = [
        Index("occurredAtEpochMillis"),
        Index("categoryId"),
        Index("accountId"),
        Index("fuzzyKey"),
        Index("type"),
    ],
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.SET_NULL,
        ),
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
)
data class TransactionEntity(
    @PrimaryKey val id: String,
    val type: TransactionType,
    val amountMinorUnits: Long,
    val currencyCode: String = "INR",

    /**
     * The user's own answer to "what was this for?".
     *
     * Kept strictly separate from [merchant] and [upiId]: the bank tells us who
     * was paid, the user tells us why, and neither may overwrite the other.
     */
    val description: String? = null,
    val categoryId: String? = null,

    val merchant: String? = null,
    val upiId: String? = null,
    val counterparty: String? = null,

    val accountId: String? = null,
    val bankCode: String? = null,
    val bankName: String? = null,
    val accountTail: String? = null,
    val paymentMethod: PaymentMethod = PaymentMethod.OTHER,

    val referenceId: String? = null,
    val rrn: String? = null,
    /** Balance as reported by the bank in this alert. Never a computed figure. */
    val availableBalanceMinorUnits: Long? = null,

    val occurredAtEpochMillis: Long,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,

    val source: TransactionSource,
    /** Which parser produced this, for diagnosing a misread bank format. */
    val parserId: String? = null,
    val smsId: String? = null,
    val smsSender: String? = null,

    /** False until the user has answered the categorisation prompt. */
    val isConfirmed: Boolean = false,
    val confidence: Float = 1f,
    /** Stable key used by the fuzzy arm of duplicate detection. */
    val fuzzyKey: String = "",
    val notes: String? = null,
)

/**
 * Every strong identifier that maps to a stored transaction.
 *
 * Duplicate prevention is enforced here by the primary key rather than only in
 * Kotlin, so a race between the SMS receiver and a manual save cannot slip a
 * second copy through.
 */
@Entity(
    tableName = "transaction_keys",
    foreignKeys = [
        ForeignKey(
            entity = TransactionEntity::class,
            parentColumns = ["id"],
            childColumns = ["transactionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("transactionId")],
)
data class TransactionKeyEntity(
    @PrimaryKey val key: String,
    val transactionId: String,
)

@Entity(
    tableName = "budgets",
    indices = [Index(value = ["categoryId", "periodYearMonth"], unique = true)],
)
data class BudgetEntity(
    @PrimaryKey val id: String,
    /** Null means an overall budget for the month rather than a per-category one. */
    val categoryId: String?,
    /** ISO year-month, e.g. "2026-08". */
    val periodYearMonth: String,
    val limitMinorUnits: Long,
    val alertsEnabled: Boolean = true,
    /** Prevents re-notifying on every transaction once a threshold is crossed. */
    val lastAlertedState: String? = null,
)

/**
 * What the user chose last time for a given payee. This is the whole of the
 * app's "learning": local, inspectable, and erasable from Settings.
 */
@Entity(tableName = "merchant_mappings")
data class MerchantMappingEntity(
    @PrimaryKey val payeeKey: String,
    val categoryId: String,
    val description: String?,
    val hitCount: Int,
    val lastUsedAtEpochMillis: Long,
)

/**
 * Audit of messages the pipeline declined, kept briefly so a user whose bank
 * is not recognised can see why and report the format. Only the parse verdict
 * and the sender are stored -- never the message body.
 */
@Entity(tableName = "ingest_log", indices = [Index("createdAtEpochMillis")])
data class IngestLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sender: String,
    val outcome: String,
    val reason: String?,
    @ColumnInfo(defaultValue = "0") val bodyLength: Int,
    val createdAtEpochMillis: Long,
)
