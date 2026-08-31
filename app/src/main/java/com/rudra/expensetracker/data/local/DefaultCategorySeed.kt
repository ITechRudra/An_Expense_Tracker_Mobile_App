package com.rudra.expensetracker.data.local

import com.rudra.expensetracker.core.category.DefaultCategories
import com.rudra.expensetracker.core.sms.TransactionType

/**
 * The categories a new install starts with.
 *
 * Ids are stable strings rather than generated, so the keyword rules in
 * core-parser and any exported file keep referring to the same category across
 * devices and restores.
 */
object DefaultCategorySeed {

    fun categories(): List<CategoryEntity> {
        val expense = listOf(
            Triple(DefaultCategories.FOOD, "Food", "Restaurant" to 0xFFEF6C00.toInt()),
            Triple(DefaultCategories.TRAVEL, "Travel", "DirectionsCar" to 0xFF1E88E5.toInt()),
            Triple(DefaultCategories.SHOPPING, "Shopping", "ShoppingBag" to 0xFF8E24AA.toInt()),
            Triple(DefaultCategories.ENTERTAINMENT, "Entertainment", "Movie" to 0xFFD81B60.toInt()),
            Triple(DefaultCategories.EDUCATION, "Education", "School" to 0xFF3949AB.toInt()),
            Triple(DefaultCategories.BILLS, "Bills", "ReceiptLong" to 0xFF00897B.toInt()),
            Triple(DefaultCategories.HEALTH, "Health", "MedicalServices" to 0xFFE53935.toInt()),
            Triple(DefaultCategories.WORK, "Work", "Work" to 0xFF546E7A.toInt()),
            Triple(DefaultCategories.PERSONAL, "Personal", "Person" to 0xFF6D4C41.toInt()),
            Triple(DefaultCategories.GROCERIES, "Groceries", "ShoppingCart" to 0xFF43A047.toInt()),
            Triple(DefaultCategories.RENT, "Rent", "Home" to 0xFF5E35B1.toInt()),
            Triple(DefaultCategories.OTHER_EXPENSE, "Other", "MoreHoriz" to 0xFF757575.toInt()),
        )
        val income = listOf(
            Triple(DefaultCategories.SALARY, "Salary", "AccountBalanceWallet" to 0xFF2E7D32.toInt()),
            Triple(DefaultCategories.REFUND, "Refund", "Undo" to 0xFF00838F.toInt()),
            Triple(DefaultCategories.GIFT, "Gift", "CardGiftcard" to 0xFFC2185B.toInt()),
            Triple(DefaultCategories.TRANSFER, "Transfer", "SwapHoriz" to 0xFF455A64.toInt()),
            Triple(DefaultCategories.INTEREST, "Interest", "TrendingUp" to 0xFF00695C.toInt()),
            Triple(DefaultCategories.OTHER_INCOME, "Other", "MoreHoriz" to 0xFF757575.toInt()),
        )

        return expense.mapIndexed { index, (id, name, iconColor) ->
            CategoryEntity(id, name, iconColor.first, iconColor.second, TransactionType.EXPENSE, index, isBuiltIn = true)
        } + income.mapIndexed { index, (id, name, iconColor) ->
            CategoryEntity(id, name, iconColor.first, iconColor.second, TransactionType.INCOME, index, isBuiltIn = true)
        }
    }
}
