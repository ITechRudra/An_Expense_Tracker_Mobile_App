package com.rudra.expensetracker.di

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.rudra.expensetracker.core.category.CategorySuggestionEngine
import com.rudra.expensetracker.core.dedupe.DuplicateDetector
import com.rudra.expensetracker.core.sms.ParserRegistry
import com.rudra.expensetracker.core.sms.TransactionSmsPipeline
import com.rudra.expensetracker.data.local.BudgetDao
import com.rudra.expensetracker.data.local.CategoryDao
import com.rudra.expensetracker.data.local.AccountDao
import com.rudra.expensetracker.data.local.DefaultCategorySeed
import com.rudra.expensetracker.data.local.ExpenseDatabase
import com.rudra.expensetracker.data.local.IngestLogDao
import com.rudra.expensetracker.data.local.MerchantMappingDao
import com.rudra.expensetracker.data.local.TransactionDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Provider
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun database(
        @ApplicationContext context: Context,
        // A Provider, not the database itself: the seed callback runs while the
        // instance is still being constructed, so it must resolve lazily.
        databaseProvider: Provider<ExpenseDatabase>,
    ): ExpenseDatabase =
        Room.databaseBuilder(context, ExpenseDatabase::class.java, ExpenseDatabase.NAME)
            .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
            .addCallback(object : RoomDatabase.Callback() {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    // Seeded off the main thread; the UI shows its empty state
                    // until the categories flow emits.
                    CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                        val dao = databaseProvider.get().categoryDao()
                        if (dao.count() == 0) dao.upsertAll(DefaultCategorySeed.categories())
                    }
                }
            })
            .build()

    @Provides fun transactionDao(db: ExpenseDatabase): TransactionDao = db.transactionDao()
    @Provides fun accountDao(db: ExpenseDatabase): AccountDao = db.accountDao()
    @Provides fun categoryDao(db: ExpenseDatabase): CategoryDao = db.categoryDao()
    @Provides fun budgetDao(db: ExpenseDatabase): BudgetDao = db.budgetDao()
    @Provides fun merchantMappingDao(db: ExpenseDatabase): MerchantMappingDao = db.merchantMappingDao()
    @Provides fun ingestLogDao(db: ExpenseDatabase): IngestLogDao = db.ingestLogDao()

    @Provides
    @Singleton
    fun parserRegistry(): ParserRegistry = ParserRegistry()

    @Provides
    @Singleton
    fun smsPipeline(registry: ParserRegistry): TransactionSmsPipeline = TransactionSmsPipeline(registry)

    @Provides
    @Singleton
    fun duplicateDetector(): DuplicateDetector = DuplicateDetector()

    @Provides
    @Singleton
    fun suggestionEngine(): CategorySuggestionEngine = CategorySuggestionEngine()
}
