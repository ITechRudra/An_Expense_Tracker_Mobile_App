package com.rudra.expensetracker.ui.transactions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rudra.expensetracker.core.money.Money
import com.rudra.expensetracker.core.sms.TransactionType
import com.rudra.expensetracker.data.local.TransactionSource
import com.rudra.expensetracker.ui.components.LoadingState
import com.rudra.expensetracker.ui.theme.expenseColor
import com.rudra.expensetracker.ui.theme.incomeColor
import com.rudra.expensetracker.util.accountLabel
import com.rudra.expensetracker.util.formatRupees
import com.rudra.expensetracker.util.formatTransactionTime
import java.time.Instant
import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionDetailScreen(
    transactionId: String,
    onEdit: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: TransactionDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state(transactionId).collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Transaction") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { onEdit(transactionId) }) {
                        Icon(Icons.Filled.Edit, contentDescription = "Edit")
                    }
                    IconButton(onClick = { confirmDelete = true }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete")
                    }
                },
            )
        },
    ) { padding ->
        val transaction = state.transaction
        if (transaction == null) {
            LoadingState(Modifier.padding(padding))
            return@Scaffold
        }

        Column(
            Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            val isExpense = transaction.type == TransactionType.EXPENSE
            Text(
                text = (if (isExpense) "-" else "+") + Money(transaction.amountMinorUnits).formatRupees(),
                style = MaterialTheme.typography.displaySmall,
                color = if (isExpense) expenseColor() else incomeColor(),
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
            Text(
                text = transaction.description ?: "No description yet",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                textAlign = TextAlign.Center,
            )
            HorizontalDivider()

            DetailRow("Category", state.categoryName ?: "Uncategorised")
            DetailRow("Type", if (isExpense) "Expense" else "Income")
            DetailRow("Payment method", transaction.paymentMethod.name.replace('_', ' '))
            transaction.merchant?.let { DetailRow("Merchant", it) }
            transaction.upiId?.let { DetailRow("UPI ID", it) }
            accountLabel(transaction.bankName, transaction.accountTail)?.let { DetailRow("Account", it) }
            DetailRow(
                "Date",
                Instant.ofEpochMilli(transaction.occurredAtEpochMillis)
                    .atZone(ZoneId.systemDefault()).toLocalDateTime().formatTransactionTime(),
            )
            transaction.rrn?.let { DetailRow("RRN", it) }
            transaction.referenceId?.let { DetailRow("Reference", it) }
            transaction.availableBalanceMinorUnits?.let {
                // Labelled explicitly: this is the bank's number, not ours.
                DetailRow("Bank-reported balance", Money(it).formatRupees())
            }
            DetailRow(
                "Source",
                when (transaction.source) {
                    TransactionSource.SMS -> "Detected from SMS"
                    TransactionSource.MANUAL -> "Added manually"
                    TransactionSource.IMPORTED -> "Restored from backup"
                },
            )
            transaction.notes?.let { DetailRow("Notes", it) }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this transaction?") },
            text = { Text("It will be removed from your history and all totals. This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(transactionId)
                    confirmDelete = false
                    onBack()
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.End,
            modifier = Modifier.padding(start = 24.dp),
        )
    }
}
