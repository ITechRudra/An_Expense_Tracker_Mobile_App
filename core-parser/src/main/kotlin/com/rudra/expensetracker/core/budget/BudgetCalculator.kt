package com.rudra.expensetracker.core.budget

import com.rudra.expensetracker.core.money.Money

enum class BudgetState { UNDER, NEAR_LIMIT, EXCEEDED }

data class BudgetStatus(
    val categoryId: String?,
    val limit: Money,
    val spent: Money,
    val remaining: Money,
    val percentUsed: Int,
    val state: BudgetState,
) {
    /** True the first time a budget crosses into a state worth notifying about. */
    val isAlerting: Boolean get() = state != BudgetState.UNDER
}

object BudgetCalculator {

    /** Percentage of the limit at which the user is warned before overspending. */
    const val NEAR_LIMIT_PERCENT = 80

    /**
     * @param categoryId null for an overall monthly budget rather than a per
     *        category one.
     */
    fun status(
        categoryId: String?,
        limit: Money,
        spent: Money,
        nearLimitPercent: Int = NEAR_LIMIT_PERCENT,
    ): BudgetStatus {
        val percent = spent.percentOf(limit)
        val state = when {
            limit.isZero -> BudgetState.UNDER
            spent >= limit -> BudgetState.EXCEEDED
            percent >= nearLimitPercent -> BudgetState.NEAR_LIMIT
            else -> BudgetState.UNDER
        }
        return BudgetStatus(
            categoryId = categoryId,
            limit = limit,
            spent = spent,
            // Clamped at zero: "you are 500 over" is shown by state, not a negative remainder.
            remaining = if (spent >= limit) Money.ZERO else limit - spent,
            percentUsed = percent,
            state = state,
        )
    }
}
