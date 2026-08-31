package com.rudra.expensetracker.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.rudra.expensetracker.core.sms.PaymentMethod
import com.rudra.expensetracker.core.sms.TransactionType

class Converters {

    @TypeConverter fun typeToString(value: TransactionType): String = value.name
    @TypeConverter fun stringToType(value: String): TransactionType = TransactionType.valueOf(value)

    @TypeConverter fun methodToString(value: PaymentMethod): String = value.name

    /**
     * Unknown values decay to OTHER rather than throwing: a database written by
     * a newer build must still open after a downgrade or a partial restore.
     */
    @TypeConverter fun stringToMethod(value: String): PaymentMethod =
        runCatching { PaymentMethod.valueOf(value) }.getOrDefault(PaymentMethod.OTHER)

    @TypeConverter fun sourceToString(value: TransactionSource): String = value.name
    @TypeConverter fun stringToSource(value: String): TransactionSource =
        runCatching { TransactionSource.valueOf(value) }.getOrDefault(TransactionSource.MANUAL)
}

@Database(
    entities = [
        TransactionEntity::class,
        TransactionKeyEntity::class,
        AccountEntity::class,
        CategoryEntity::class,
        BudgetEntity::class,
        MerchantMappingEntity::class,
        IngestLogEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class ExpenseDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
    abstract fun accountDao(): AccountDao
    abstract fun categoryDao(): CategoryDao
    abstract fun budgetDao(): BudgetDao
    abstract fun merchantMappingDao(): MerchantMappingDao
    abstract fun ingestLogDao(): IngestLogDao

    companion object {
        const val NAME = "expense_tracker.db"
    }
}
