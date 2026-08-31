package com.rudra.expensetracker.ui.budgets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rudra.expensetracker.core.budget.BudgetState
import com.rudra.expensetracker.core.money.Money
import com.rudra.expensetracker.ui.components.CategoryPicker
import com.rudra.expensetracker.ui.components.EmptyState
import com.rudra.expensetracker.ui.components.SectionHeader
import com.rudra.expensetracker.ui.theme.expenseColor
import com.rudra.expensetracker.util.formatRupees

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetsScreen(viewModel: BudgetsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf(false) }

    LaunchedEffect(state.rows) { viewModel.checkAlerts() }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Budgets") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = { editing = true }) {
                Icon(Icons.Filled.Add, contentDescription = "Set a budget")
            }
        },
    ) { padding ->
        LazyColumn(Modifier.padding(padding)) {
            state.overall?.let { overall ->
                item {
                    SectionHeader("Overall")
                    BudgetCard(overall)
                }
            }

            if (state.rows.isEmpty() && state.overall == null) {
                item {
                    EmptyState(
                        icon = Icons.Filled.PieChart,
                        title = "No budgets set",
                        message = "Set a monthly limit for a category and this screen will track how much of it is left.",
                        modifier = Modifier.padding(top = 32.dp),
                    )
                }
            } else {
                item { SectionHeader("By category") }
                items(state.rows, key = { it.budgetId }) { row -> BudgetCard(row) }
            }
        }
    }

    if (editing) {
        SetBudgetDialog(
            categories = state.categories.filter {
                it.appliesTo == com.rudra.expensetracker.core.sms.TransactionType.EXPENSE
            },
            onDismiss = { editing = false },
            onConfirm = { categoryId, limit ->
                viewModel.setLimit(categoryId, limit)
                editing = false
            },
        )
    }
}

@Composable
private fun BudgetCard(row: BudgetRow) {
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(row.categoryName, style = MaterialTheme.typography.titleMedium)
                Text(
                    when (row.status.state) {
                        BudgetState.EXCEEDED -> "Exceeded"
                        BudgetState.NEAR_LIMIT -> "${row.status.percentUsed}%"
                        BudgetState.UNDER -> "${row.status.percentUsed}%"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = when (row.status.state) {
                        BudgetState.EXCEEDED -> expenseColor()
                        BudgetState.NEAR_LIMIT -> MaterialTheme.colorScheme.tertiary
                        BudgetState.UNDER -> MaterialTheme.colorScheme.onSurface
                    },
                )
            }
            LinearProgressIndicator(
                progress = { (row.status.percentUsed / 100f).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
                color = when (row.status.state) {
                    BudgetState.EXCEEDED -> expenseColor()
                    BudgetState.NEAR_LIMIT -> MaterialTheme.colorScheme.tertiary
                    BudgetState.UNDER -> row.colorArgb?.let { Color(it) } ?: MaterialTheme.colorScheme.primary
                },
            )
            Text(
                "${row.status.spent.formatRupees()} of ${row.status.limit.formatRupees()}" +
                    if (row.status.state == BudgetState.EXCEEDED) {
                        " · over by ${(row.status.spent - row.status.limit).formatRupees()}"
                    } else {
                        " · ${row.status.remaining.formatRupees()} left"
                    },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SetBudgetDialog(
    categories: List<com.rudra.expensetracker.data.local.CategoryEntity>,
    onDismiss: () -> Unit,
    onConfirm: (String?, Money) -> Unit,
) {
    var amountText by remember { mutableStateOf("") }
    var categoryId by remember { mutableStateOf<String?>(null) }
    val amount = Money.parseOrNull(amountText)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Set a monthly budget") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { value -> amountText = value.filter { it.isDigit() || it == '.' } },
                    label = { Text("Monthly limit") },
                    prefix = { Text("₹") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                )
                Text(
                    "Leave the category unselected for an overall monthly budget.",
                    style = MaterialTheme.typography.bodySmall,
                )
                CategoryPicker(
                    categories = categories,
                    selectedId = categoryId,
                    onSelect = { id -> categoryId = if (categoryId == id) null else id },
                    modifier = Modifier.padding(horizontal = 0.dp),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { amount?.let { onConfirm(categoryId, it) } },
                enabled = amount != null && !amount.isZero,
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
