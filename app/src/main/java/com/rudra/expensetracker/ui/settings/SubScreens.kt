package com.rudra.expensetracker.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rudra.expensetracker.core.money.Money
import com.rudra.expensetracker.core.sms.TransactionType
import com.rudra.expensetracker.util.formatRupees
import com.rudra.expensetracker.ui.components.EmptyState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoriesScreen(onBack: () -> Unit, viewModel: OrganiseViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var creating by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = { BackBar("Categories", onBack) },
        floatingActionButton = {
            FloatingActionButton(onClick = { creating = true }) {
                Icon(Icons.Filled.Add, contentDescription = "New category")
            }
        },
    ) { padding ->
        LazyColumn(Modifier.padding(padding)) {
            for (type in TransactionType.entries) {
                val group = state.categories.filter { it.appliesTo == type }
                if (group.isEmpty()) continue
                item(key = "header-$type") {
                    Text(
                        if (type == TransactionType.EXPENSE) "Expense categories" else "Income categories",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(16.dp),
                    )
                }
                items(group, key = { it.id }) { category ->
                    Row(
                        Modifier.fillMaxWidth()
                            .clickable { renaming = category.id }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier
                                .padding(end = 12.dp)
                                .size(14.dp)
                                .clip(CircleShape)
                                .background(Color(category.colorArgb)),
                        )
                        Column(Modifier.weight(1f)) {
                            Text(category.name, style = MaterialTheme.typography.bodyLarge)
                            if (category.isBuiltIn) {
                                Text(
                                    "Built in",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.outline,
                                )
                            }
                        }
                        IconButton(onClick = { viewModel.deleteCategory(category.id) }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Remove ${category.name}")
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                }
            }
        }
    }

    if (creating) {
        NameDialog(
            title = "New category",
            initial = "",
            onDismiss = { creating = false },
            onConfirm = { name ->
                viewModel.createCategory(name, TransactionType.EXPENSE)
                creating = false
            },
        )
    }
    renaming?.let { id ->
        val category = state.categories.firstOrNull { it.id == id }
        if (category != null) {
            NameDialog(
                title = "Rename category",
                initial = category.name,
                onDismiss = { renaming = null },
                onConfirm = { name ->
                    viewModel.renameCategory(id, name)
                    renaming = null
                },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountsScreen(onBack: () -> Unit, viewModel: OrganiseViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var renaming by remember { mutableStateOf<String?>(null) }

    Scaffold(topBar = { BackBar("Accounts", onBack) }) { padding ->
        if (state.accounts.isEmpty()) {
            EmptyState(
                icon = Icons.Filled.Category,
                title = "No accounts yet",
                message = "Accounts are created automatically the first time a bank message for them is detected.",
                modifier = Modifier.padding(padding).padding(top = 32.dp),
            )
            return@Scaffold
        }
        LazyColumn(Modifier.padding(padding)) {
            items(state.accounts, key = { it.id }) { account ->
                Column(
                    Modifier.fillMaxWidth().clickable { renaming = account.id }.padding(16.dp),
                ) {
                    Text(account.displayName, style = MaterialTheme.typography.bodyLarge)
                    val detail = buildList {
                        account.bankName?.let { add(it) }
                        account.accountTail?.let { add(it) }
                        account.lastReportedBalanceMinorUnits?.let {
                            // Always labelled as the bank's figure, never ours.
                            add("Bank-reported balance " + Money(it).formatRupees())
                        }
                    }.joinToString(" • ")
                    Text(
                        detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
            }
        }
    }

    renaming?.let { id ->
        val account = state.accounts.firstOrNull { it.id == id }
        if (account != null) {
            NameDialog(
                title = "Rename account",
                initial = account.displayName,
                onDismiss = { renaming = null },
                onConfirm = { name ->
                    viewModel.renameAccount(id, name)
                    renaming = null
                },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LearnedMappingsScreen(onBack: () -> Unit, viewModel: OrganiseViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(topBar = { BackBar("Learned categories", onBack) }) { padding ->
        if (state.mappings.isEmpty()) {
            EmptyState(
                icon = Icons.Filled.Insights,
                title = "Nothing learned yet",
                message = "Each time you categorise a payment, the app remembers your choice for that payee " +
                    "and suggests it next time. Suggestions are never applied without you confirming.",
                modifier = Modifier.padding(padding).padding(top = 32.dp),
            )
            return@Scaffold
        }
        LazyColumn(Modifier.padding(padding)) {
            items(state.mappings, key = { it.payeeKey }) { mapping ->
                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(mapping.payeeKey, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                        Text(
                            listOfNotNull(
                                state.categories.firstOrNull { it.id == mapping.categoryId }?.name,
                                mapping.description,
                                "confirmed ${mapping.hitCount}×",
                            ).joinToString(" • "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { viewModel.forget(mapping.payeeKey) }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Forget this payee")
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticsScreen(onBack: () -> Unit, viewModel: OrganiseViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(topBar = { BackBar("Detection diagnostics", onBack) }) { padding ->
        LazyColumn(Modifier.padding(padding)) {
            item {
                Text(
                    "What the app did with each message it saw. Message contents are never stored -- " +
                        "only the sender and the decision. Use this to check whether your bank's format is recognised.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
            items(state.ingestLog, key = { it.id }) { entry ->
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(entry.sender, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            listOfNotNull(entry.outcome, entry.reason).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BackBar(title: String, onBack: () -> Unit) {
    TopAppBar(
        title = { Text(title) },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
        },
    )
}

@Composable
private fun NameDialog(
    title: String,
    initial: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var value by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                label = { Text("Name") },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(value.trim()) }, enabled = value.isNotBlank()) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
