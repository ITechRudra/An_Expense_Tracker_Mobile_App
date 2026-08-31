package com.rudra.expensetracker.core.budget

import com.google.common.truth.Truth.assertThat
import com.rudra.expensetracker.core.category.DefaultCategories
import com.rudra.expensetracker.core.money.Money
import org.junit.Test

class BudgetCalculatorTest {

    private fun status(limitMajor: Long, spentMajor: Long) = BudgetCalculator.status(
        DefaultCategories.FOOD, Money.ofMajor(limitMajor), Money.ofMajor(spentMajor),
    )

    @Test
    fun `reports usage under the warning threshold`() {
        val s = status(5000, 3850)
        assertThat(s.percentUsed).isEqualTo(77)
        assertThat(s.state).isEqualTo(BudgetState.UNDER)
        assertThat(s.remaining).isEqualTo(Money.ofMajor(1150))
        assertThat(s.isAlerting).isFalse()
    }

    @Test
    fun `warns once spending crosses the near-limit threshold`() {
        val s = status(5000, 4100)
        assertThat(s.percentUsed).isEqualTo(82)
        assertThat(s.state).isEqualTo(BudgetState.NEAR_LIMIT)
        assertThat(s.isAlerting).isTrue()
    }

    @Test
    fun `marks a budget exceeded and clamps the remainder at zero`() {
        val s = status(4000, 4500)
        assertThat(s.state).isEqualTo(BudgetState.EXCEEDED)
        assertThat(s.percentUsed).isEqualTo(112)
        assertThat(s.remaining).isEqualTo(Money.ZERO)
    }

    @Test
    fun `spending exactly the limit counts as exceeded`() {
        assertThat(status(3000, 3000).state).isEqualTo(BudgetState.EXCEEDED)
    }

    @Test
    fun `a zero limit never alerts and never divides by zero`() {
        val s = BudgetCalculator.status(null, Money.ZERO, Money.ofMajor(500))
        assertThat(s.state).isEqualTo(BudgetState.UNDER)
        assertThat(s.percentUsed).isEqualTo(0)
    }
}
