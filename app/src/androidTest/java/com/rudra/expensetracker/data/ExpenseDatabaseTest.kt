package com.rudra.expensetracker.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.rudra.expensetracker.core.sms.PaymentMethod
import com.rudra.expensetracker.core.sms.TransactionType
import com.rudra.expensetracker.data.local.CategoryEntity
import com.rudra.expensetracker.data.local.DefaultCategorySeed
import com.rudra.expensetracker.data.local.ExpenseDatabase
import com.rudra.expensetracker.data.local.TransactionEntity
import com.rudra.expensetracker.data.local.TransactionSource
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExpenseDatabaseTest {

    private lateinit var db: ExpenseDatabase

    @Before
    fun createDb() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            ExpenseDatabase::class.java,
        ).build()
    }

    @After
    fun closeDb() = db.close()

    private fun transaction(
        id: String,
        amount: Long = 3200,
        rrn: String? = "155948254191",
        occurredAt: Long = 1_756_000_000_000L,
    ) = TransactionEntity(
        id = id,
        type = TransactionType.EXPENSE,
        amountMinorUnits = amount,
        merchant = "Canteen",
        upiId = "q528800175@ybl",
        paymentMethod = PaymentMethod.UPI,
        rrn = rrn,
        occurredAtEpochMillis = occurredAt,
        createdAtEpochMillis = occurredAt,
        updatedAtEpochMillis = occurredAt,
        source = TransactionSource.SMS,
        fuzzyKey = "EXPENSE|$amount|7375|q528800175@ybl",
    )

    @Test
    fun insertsAndReadsBackATransaction() = runTest {
        val dao = db.transactionDao()
        assertThat(dao.insertIfNew(transaction("t1"), listOf("rrn:155948254191"))).isNull()

        val stored = dao.byId("t1")
        assertThat(stored?.amountMinorUnits).isEqualTo(3200L)
        assertThat(stored?.upiId).isEqualTo("q528800175@ybl")
    }

    @Test
    fun rejectsASecondTransactionClaimingTheSameStrongKey() = runTest {
        val dao = db.transactionDao()
        dao.insertIfNew(transaction("t1"), listOf("rrn:155948254191"))

        val existingId = dao.insertIfNew(transaction("t2"), listOf("rrn:155948254191"))

        assertThat(existingId).isEqualTo("t1")
        assertThat(dao.all()).hasSize(1)
    }

    @Test
    fun allowsTwoTransactionsWithDifferentReferences() = runTest {
        val dao = db.transactionDao()
        dao.insertIfNew(transaction("t1"), listOf("rrn:AAA"))
        dao.insertIfNew(transaction("t2", amount = 6400), listOf("rrn:BBB"))

        assertThat(dao.all()).hasSize(2)
    }

    @Test
    fun findsAFuzzyMatchOnlyInsideTheGivenWindow() = runTest {
        val dao = db.transactionDao()
        val base = 1_756_000_000_000L
        dao.insertIfNew(transaction("t1", occurredAt = base), emptyList())

        val inside = dao.findFuzzyMatch(
            "EXPENSE|3200|7375|q528800175@ybl", 3200, base - 60_000, base + 60_000,
        )
        val outside = dao.findFuzzyMatch(
            "EXPENSE|3200|7375|q528800175@ybl", 3200, base + 86_400_000, base + 90_000_000,
        )

        assertThat(inside?.id).isEqualTo("t1")
        assertThat(outside).isNull()
    }

    @Test
    fun updatesAndDeletesATransaction() = runTest {
        val dao = db.transactionDao()
        dao.insertIfNew(transaction("t1"), emptyList())

        dao.update(dao.byId("t1")!!.copy(description = "Chocolate", isConfirmed = true))
        assertThat(dao.byId("t1")?.description).isEqualTo("Chocolate")

        dao.delete("t1")
        assertThat(dao.byId("t1")).isNull()
    }

    @Test
    fun deletingATransactionReleasesItsStrongKeys() = runTest {
        val dao = db.transactionDao()
        dao.insertIfNew(transaction("t1"), listOf("rrn:155948254191"))
        dao.delete("t1")

        // The cascade must free the key, otherwise a deleted-then-resent alert
        // could never be recorded again.
        assertThat(dao.insertIfNew(transaction("t2"), listOf("rrn:155948254191"))).isNull()
        assertThat(dao.all()).hasSize(1)
    }

    @Test
    fun totalsCountOnlyConfirmedTransactionsOfTheRequestedType() = runTest {
        val dao = db.transactionDao()
        dao.insertIfNew(transaction("t1").copy(isConfirmed = true), listOf("k1"))
        dao.insertIfNew(transaction("t2", amount = 5000).copy(isConfirmed = false), listOf("k2"))
        dao.insertIfNew(
            transaction("t3", amount = 10_000).copy(type = TransactionType.INCOME, isConfirmed = true),
            listOf("k3"),
        )

        val expenses = dao.observeTotal(TransactionType.EXPENSE, 0, Long.MAX_VALUE, null).first()
        assertThat(expenses).isEqualTo(3200L)
    }

    @Test
    fun seedsAndQueriesCategories() = runTest {
        val dao = db.categoryDao()
        dao.upsertAll(DefaultCategorySeed.categories())

        val expense = dao.observeFor(TransactionType.EXPENSE).first()
        assertThat(expense.map { it.name }).contains("Food")
        assertThat(expense.all { it.appliesTo == TransactionType.EXPENSE }).isTrue()
    }

    @Test
    fun archivesBuiltInCategoriesButDeletesCustomOnes() = runTest {
        val dao = db.categoryDao()
        dao.upsertAll(DefaultCategorySeed.categories())
        dao.upsert(
            CategoryEntity("custom_1", "Pets", "Pets", 0x00FF00, TransactionType.EXPENSE, 99, isBuiltIn = false),
        )

        dao.archiveBuiltIn("food")
        dao.deleteCustom("custom_1")

        val visible = dao.observeAll().first().map { it.id }
        assertThat(visible).doesNotContain("food")
        assertThat(visible).doesNotContain("custom_1")
        // Archived, not gone: existing transactions keep a readable label.
        assertThat(dao.byId("food")).isNotNull()
        assertThat(dao.byId("custom_1")).isNull()
    }

    @Test
    fun recordsAndIncrementsMerchantMappings() = runTest {
        val dao = db.merchantMappingDao()
        dao.record("zomato", "food", "Dinner", 1_000L)
        dao.record("zomato", "food", "Dinner", 2_000L)

        assertThat(dao.forKey("zomato").single().hitCount).isEqualTo(2)

        // Changing the answer restarts the count rather than inheriting confidence.
        dao.record("zomato", "work", "Team lunch", 3_000L)
        val updated = dao.forKey("zomato").single()
        assertThat(updated.categoryId).isEqualTo("work")
        assertThat(updated.hitCount).isEqualTo(1)
    }
}
