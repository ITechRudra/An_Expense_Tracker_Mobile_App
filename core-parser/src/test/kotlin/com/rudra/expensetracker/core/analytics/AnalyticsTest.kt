package com.rudra.expensetracker.core.analytics

import com.google.common.truth.Truth.assertThat
import com.rudra.expensetracker.core.category.DefaultCategories
import com.rudra.expensetracker.core.money.Money
import com.rudra.expensetracker.core.sms.TransactionType
import java.time.LocalDate
import org.junit.Test

private data class Txn(
    override val type: TransactionType,
    override val amount: Money,
    override val categoryId: String?,
    override val merchantKey: String?,
    override val date: LocalDate,
) : AnalyticsTransaction

class AnalyticsTest {

    private val aug = LocalDate.of(2026, 8, 1)

    private val sample = listOf(
        Txn(TransactionType.INCOME, Money.ofMajor(45_000), DefaultCategories.SALARY, null, aug),
        Txn(TransactionType.EXPENSE, Money.ofMajor(3_000), DefaultCategories.FOOD, "zomato", aug.plusDays(1)),
        Txn(TransactionType.EXPENSE, Money.ofMajor(1_000), DefaultCategories.FOOD, "swiggy", aug.plusDays(1)),
        Txn(TransactionType.EXPENSE, Money.ofMajor(6_000), DefaultCategories.RENT, "landlord", aug.plusDays(2)),
        Txn(TransactionType.EXPENSE, Money.ofMajor(2_000), DefaultCategories.TRAVEL, "uber", aug.plusDays(4)),
        Txn(TransactionType.EXPENSE, Money.ofMajor(1_000), DefaultCategories.FOOD, "zomato", aug.plusDays(4)),
    )

    @Test
    fun `summarises income expense and net`() {
        val s = Analytics.summary(sample)
        assertThat(s.income).isEqualTo(Money.ofMajor(45_000))
        assertThat(s.expense).isEqualTo(Money.ofMajor(13_000))
        assertThat(s.net).isEqualTo(Money.ofMajor(32_000))
    }

    @Test
    fun `groups expenses by category largest first with shares`() {
        val byCategory = Analytics.byCategory(sample)
        assertThat(byCategory.map { it.categoryId })
            .containsExactly(DefaultCategories.RENT, DefaultCategories.FOOD, DefaultCategories.TRAVEL)
            .inOrder()
        val food = byCategory.first { it.categoryId == DefaultCategories.FOOD }
        assertThat(food.total).isEqualTo(Money.ofMajor(5_000))
        assertThat(food.count).isEqualTo(3)
        assertThat(food.share).isEqualTo(38)
        assertThat(byCategory.none { it.categoryId == DefaultCategories.SALARY }).isTrue()
    }

    @Test
    fun `ranks top merchants by spend`() {
        val top = Analytics.topMerchants(sample, limit = 2)
        assertThat(top.map { it.merchantKey }).containsExactly("landlord", "zomato").inOrder()
        assertThat(top.first { it.merchantKey == "zomato" }.count).isEqualTo(2)
    }

    @Test
    fun `daily series emits zero-valued days so gaps stay visible`() {
        val series = Analytics.dailySeries(sample, aug, aug.plusDays(4))
        assertThat(series).hasSize(5)
        assertThat(series[0].income).isEqualTo(Money.ofMajor(45_000))
        assertThat(series[1].expense).isEqualTo(Money.ofMajor(4_000))
        assertThat(series[3].expense).isEqualTo(Money.ZERO)
        assertThat(series.map { it.date }).isInOrder()
    }

    @Test
    fun `largest expenses are ordered by amount`() {
        assertThat(Analytics.largestExpenses(sample, 2).map { it.amount })
            .containsExactly(Money.ofMajor(6_000), Money.ofMajor(3_000)).inOrder()
    }

    @Test
    fun `average daily spend divides by the calendar span not by active days`() {
        // 13,000 across the 5-day window 1..5 Aug, including the two quiet days.
        assertThat(Analytics.averageDailySpend(sample, aug, aug.plusDays(4)))
            .isEqualTo(Money.ofMajor(2_600))
    }

    @Test
    fun `empty input produces zeroes rather than throwing`() {
        val s = Analytics.summary(emptyList())
        assertThat(s.income).isEqualTo(Money.ZERO)
        assertThat(s.net).isEqualTo(Money.ZERO)
        assertThat(Analytics.byCategory(emptyList())).isEmpty()
        assertThat(Analytics.averageDailySpend(emptyList(), aug, aug)).isEqualTo(Money.ZERO)
    }
}
