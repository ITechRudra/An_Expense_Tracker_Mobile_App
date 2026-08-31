package com.rudra.expensetracker.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector

/** Top-level destinations. Five is the Material 3 maximum for a nav bar. */
enum class TopLevelDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
) {
    HOME("home", "Home", Icons.Filled.Home),
    TRANSACTIONS("transactions", "Transactions", Icons.Filled.ReceiptLong),
    ANALYTICS("analytics", "Analytics", Icons.Filled.Analytics),
    BUDGETS("budgets", "Budgets", Icons.Filled.PieChart),
    SETTINGS("settings", "Settings", Icons.Filled.Settings),
}

object Routes {
    const val ONBOARDING = "onboarding"
    const val LOCK = "lock"
    const val ADD_EDIT = "transaction/edit"
    const val DETAIL = "transaction/detail"
    const val CATEGORIES = "settings/categories"
    const val ACCOUNTS = "settings/accounts"
    const val LEARNED = "settings/learned"
    const val DIAGNOSTICS = "settings/diagnostics"

    fun addEdit(transactionId: String? = null): String =
        if (transactionId == null) "$ADD_EDIT?id=" else "$ADD_EDIT?id=$transactionId"

    fun detail(transactionId: String): String = "$DETAIL/$transactionId"
}
