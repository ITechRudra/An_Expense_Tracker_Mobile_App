package com.rudra.expensetracker.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rudra.expensetracker.core.money.Money
import com.rudra.expensetracker.ui.components.ChartLegend
import com.rudra.expensetracker.ui.components.ChartSlice
import com.rudra.expensetracker.ui.components.DonutChart
import com.rudra.expensetracker.ui.components.EmptyState
import com.rudra.expensetracker.ui.components.LoadingState
import com.rudra.expensetracker.ui.components.SectionHeader
import com.rudra.expensetracker.ui.components.StatTile
import com.rudra.expensetracker.ui.components.TransactionRow
import com.rudra.expensetracker.ui.theme.expenseColor
import com.rudra.expensetracker.ui.theme.incomeColor
import com.rudra.expensetracker.util.formatRupees
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onAddTransaction: () -> Unit,
    onOpenTransaction: (String) -> Unit,
    onSeeAll: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val monthLabel = state.month.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault()))

    Scaffold(
        topBar = { TopAppBar(title = { Text(monthLabel) }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddTransaction,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Add") },
            )
        },
    ) { padding ->
        if (state.loading) {
            LoadingState(Modifier.padding(padding))
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 96.dp),
        ) {
            item {
                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    StatTile("Income", state.monthIncome.formatRupees(), Modifier.weight(1f), incomeColor())
                    StatTile("Expenses", state.monthExpense.formatRupees(), Modifier.weight(1f), expenseColor())
                }
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    StatTile("Net", state.monthNet.formatRupees(), Modifier.weight(1f))
                    StatTile("Today", state.todayExpense.formatRupees(), Modifier.weight(1f))
                    StatTile("This week", state.weekExpense.formatRupees(), Modifier.weight(1f))
                }
            }

            if (state.unconfirmed.isNotEmpty()) {
                item {
                    SectionHeader("Waiting for a category (${state.unconfirmed.size})")
                }
                items(state.unconfirmed, key = { it.id }) { transaction ->
                    Card(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        ),
                    ) {
                        TransactionRow(
                            transaction = transaction,
                            categoryName = null,
                            categoryColor = null,
                            onClick = { onOpenTransaction(transaction.id) },
                        )
                    }
                }
            }

            if (state.topCategories.isNotEmpty()) {
                item {
                    SectionHeader("Spending by category")
                    val slices = state.topCategories.map { total ->
                        val category = total.categoryId?.let { state.categoriesById[it] }
                        ChartSlice(
                            label = category?.name ?: "Uncategorised",
                            value = total.total.minorUnits,
                            color = category?.let { Color(it.colorArgb) }
                                ?: MaterialTheme.colorScheme.outlineVariant,
                        )
                    }
                    DonutChart(
                        slices = slices,
                        centerLabel = "spent",
                        centerValue = state.monthExpense.formatRupees(),
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                    ChartLegend(slices) { Money(it).formatRupees() }
                }
            }

            if (state.largestExpenses.isNotEmpty()) {
                item { SectionHeader("Largest this month") }
                items(state.largestExpenses, key = { "largest-${it.id}" }) { transaction ->
                    TransactionRow(
                        transaction = transaction,
                        categoryName = transaction.categoryId?.let { state.categoriesById[it]?.name },
                        categoryColor = transaction.categoryId
                            ?.let { state.categoriesById[it] }?.let { Color(it.colorArgb) },
                        onClick = { onOpenTransaction(transaction.id) },
                    )
                }
            }

            item {
                SectionHeader("Recent") {
                    TextButton(onClick = onSeeAll) { Text("See all") }
                }
            }
            if (state.recent.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Filled.Insights,
                        title = "No transactions yet",
                        message = "When a bank SMS arrives, it will appear here and ask what it was for. " +
                            "You can also add one yourself.",
                    )
                }
            } else {
                items(state.recent, key = { it.id }) { transaction ->
                    TransactionRow(
                        transaction = transaction,
                        categoryName = transaction.categoryId?.let { state.categoriesById[it]?.name },
                        categoryColor = transaction.categoryId
                            ?.let { state.categoriesById[it] }?.let { Color(it.colorArgb) },
                        onClick = { onOpenTransaction(transaction.id) },
                    )
                }
            }
        }
    }
}
