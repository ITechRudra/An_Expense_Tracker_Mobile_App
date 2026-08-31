package com.rudra.expensetracker.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.rudra.expensetracker.core.sms.PaymentMethod
import com.rudra.expensetracker.core.sms.TransactionType
import com.rudra.expensetracker.data.local.CategoryEntity
import com.rudra.expensetracker.data.local.TransactionEntity
import com.rudra.expensetracker.data.local.TransactionSource
import com.rudra.expensetracker.ui.components.CategoryPicker
import com.rudra.expensetracker.ui.components.TransactionRow
import com.rudra.expensetracker.ui.theme.ExpenseTrackerTheme
import org.junit.Rule
import org.junit.Test

class QuickAddFlowTest {

    @get:Rule val compose = createComposeRule()

    private val categories = listOf(
        CategoryEntity("food", "Food", "Restaurant", 0xFFEF6C00.toInt(), TransactionType.EXPENSE, 0, true),
        CategoryEntity("travel", "Travel", "DirectionsCar", 0xFF1E88E5.toInt(), TransactionType.EXPENSE, 1, true),
    )

    private val detected = TransactionEntity(
        id = "t1",
        type = TransactionType.EXPENSE,
        amountMinorUnits = 3200,
        merchant = null,
        upiId = "Q528800175@ybl",
        bankName = "IndusInd Bank",
        accountTail = "XX7375",
        paymentMethod = PaymentMethod.UPI,
        rrn = "155948254191",
        occurredAtEpochMillis = 1_756_000_000_000L,
        createdAtEpochMillis = 1_756_000_000_000L,
        updatedAtEpochMillis = 1_756_000_000_000L,
        source = TransactionSource.SMS,
        isConfirmed = false,
    )

    @Test
    fun categoryPickerReportsTheTappedCategory() {
        var selected: String? = null
        compose.setContent {
            ExpenseTrackerTheme {
                CategoryPicker(categories, selectedId = selected, onSelect = { selected = it })
            }
        }

        compose.onNodeWithText("Food").assertIsDisplayed()
        compose.onNodeWithText("Travel").performClick()

        assert(selected == "travel")
    }

    @Test
    fun anUncategorisedTransactionIsMarkedAsNeedingOne() {
        compose.setContent {
            ExpenseTrackerTheme {
                TransactionRow(detected, categoryName = null, categoryColor = null, onClick = {})
            }
        }

        compose.onNodeWithText("Needs category").assertIsDisplayed()
        // The bank's own identifier stays visible before the user names it.
        compose.onNodeWithText("Q528800175@ybl", substring = true).assertIsDisplayed()
    }

    @Test
    fun aConfirmedTransactionLeadsWithTheUsersOwnDescription() {
        compose.setContent {
            ExpenseTrackerTheme {
                TransactionRow(
                    detected.copy(description = "Chocolate", categoryId = "food", isConfirmed = true),
                    categoryName = "Food",
                    categoryColor = null,
                    onClick = {},
                )
            }
        }

        compose.onNodeWithText("Chocolate").assertIsDisplayed()
        compose.onNodeWithText("Food", substring = true).assertIsDisplayed()
        compose.onNodeWithText("IndusInd Bank ••••7375", substring = true).assertIsDisplayed()
    }
}
