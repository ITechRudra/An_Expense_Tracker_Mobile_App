package com.rudra.expensetracker.ui.transactions

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rudra.expensetracker.core.sms.TransactionType
import com.rudra.expensetracker.ui.components.EmptyState
import com.rudra.expensetracker.ui.components.TransactionRow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionsScreen(
    onAddTransaction: () -> Unit,
    onOpenTransaction: (String) -> Unit,
    viewModel: TransactionsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = onAddTransaction) {
                Icon(Icons.Filled.Add, contentDescription = "Add transaction")
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding)) {
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::onQueryChange,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("Search merchant, UPI, amount, reference…") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (state.query.isNotEmpty()) {
                        IconButton(onClick = { viewModel.onQueryChange("") }) {
                            Icon(Icons.Filled.Close, contentDescription = "Clear search")
                        }
                    }
                },
                singleLine = true,
            )

            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = state.typeFilter == TransactionType.EXPENSE,
                    onClick = {
                        viewModel.onTypeFilter(
                            if (state.typeFilter == TransactionType.EXPENSE) null else TransactionType.EXPENSE,
                        )
                    },
                    label = { Text("Expenses") },
                )
                FilterChip(
                    selected = state.typeFilter == TransactionType.INCOME,
                    onClick = {
                        viewModel.onTypeFilter(
                            if (state.typeFilter == TransactionType.INCOME) null else TransactionType.INCOME,
                        )
                    },
                    label = { Text("Income") },
                )
                for (preset in DatePreset.entries) {
                    FilterChip(
                        selected = state.datePreset == preset,
                        onClick = { viewModel.onDatePreset(preset) },
                        label = { Text(preset.label) },
                    )
                }
                if (state.activeFilterCount > 0) {
                    FilterChip(
                        selected = false,
                        onClick = viewModel::clearFilters,
                        label = { Text("Clear (${state.activeFilterCount})") },
                    )
                }
            }

            if (state.transactions.isEmpty()) {
                EmptyState(
                    icon = Icons.Filled.ReceiptLong,
                    title = if (state.query.isBlank()) "Nothing here yet" else "No matches",
                    message = if (state.query.isBlank()) {
                        "Transactions detected from bank messages, and any you add yourself, show up here."
                    } else {
                        "Try a different search, or widen the date range."
                    },
                    modifier = Modifier.padding(top = 32.dp),
                )
            } else {
                LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 96.dp)) {
                    items(state.transactions, key = { it.id }) { transaction ->
                        val category = transaction.categoryId?.let { state.categoriesById[it] }
                        TransactionRow(
                            transaction = transaction,
                            categoryName = category?.name,
                            categoryColor = category?.let { Color(it.colorArgb) },
                            onClick = { onOpenTransaction(transaction.id) },
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                    }
                }
            }
        }
    }
}
