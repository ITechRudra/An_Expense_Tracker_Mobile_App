package com.rudra.expensetracker.ui.quickadd

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.rudra.expensetracker.core.money.Money
import com.rudra.expensetracker.core.sms.TransactionType
import com.rudra.expensetracker.ui.components.CategoryPicker
import com.rudra.expensetracker.util.accountLabel
import com.rudra.expensetracker.util.formatRupees

/**
 * The one-question sheet.
 *
 * When categorising, the amount and payee are already known and shown as read
 * only facts -- the user's whole job is the category and, if they want, a note.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickAddSheet(
    transactionId: String?,
    state: QuickAddUiState,
    viewModel: QuickAddViewModel,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(transactionId) { viewModel.load(transactionId) }
    LaunchedEffect(state.saved) { if (state.saved) onDismiss() }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val detected = state.detected
            if (detected != null) {
                val amount = Money(detected.amountMinorUnits).formatRupees()
                Text(
                    text = if (detected.type == TransactionType.EXPENSE) "$amount spent" else "$amount received",
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                val payee = detected.merchant ?: detected.upiId
                val subtitle = listOfNotNull(
                    payee,
                    accountLabel(detected.bankName, detected.accountTail),
                    detected.paymentMethod.name.replace('_', ' '),
                ).joinToString(" • ")
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                Text(
                    if (detected.type == TransactionType.EXPENSE) {
                        "What was this payment for?"
                    } else {
                        "What is this income?"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            } else {
                Text(
                    "Quick add",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    SegmentedButton(
                        selected = state.type == TransactionType.EXPENSE,
                        onClick = { viewModel.onTypeChange(TransactionType.EXPENSE) },
                        shape = SegmentedButtonDefaults.itemShape(0, 2),
                    ) { Text("Expense") }
                    SegmentedButton(
                        selected = state.type == TransactionType.INCOME,
                        onClick = { viewModel.onTypeChange(TransactionType.INCOME) },
                        shape = SegmentedButtonDefaults.itemShape(1, 2),
                    ) { Text("Income") }
                }
                OutlinedTextField(
                    value = state.amountText,
                    onValueChange = viewModel::onAmountChange,
                    label = { Text("Amount") },
                    prefix = { Text("₹") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                )
            }

            state.suggestion?.let { suggestion ->
                val name = state.categories.firstOrNull { it.id == suggestion.categoryId }?.name
                if (name != null) {
                    Text(
                        "Suggested: $name — tap another to change it",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
            }

            CategoryPicker(
                categories = state.visibleCategories,
                selectedId = state.categoryId,
                suggestedId = state.suggestion?.categoryId,
                onSelect = viewModel::onCategorySelect,
            )

            OutlinedTextField(
                value = state.description,
                onValueChange = viewModel::onDescriptionChange,
                label = { Text("Add a description") },
                placeholder = { Text("Chocolate, auto to college, coffee…") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            )

            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (state.detected != null) {
                    TextButton(onClick = viewModel::dismissDetected) { Text("Not mine") }
                }
                Button(
                    onClick = viewModel::save,
                    enabled = state.categoryId != null &&
                        (state.isCategorising || Money.parseOrNull(state.amountText)?.isZero == false),
                    modifier = Modifier.weight(1f),
                ) { Text("Save") }
            }
        }
    }
}
