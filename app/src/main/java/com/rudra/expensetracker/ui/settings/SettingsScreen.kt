package com.rudra.expensetracker.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rudra.expensetracker.data.prefs.ThemeMode
import com.rudra.expensetracker.ui.components.SectionHeader

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onOpenCategories: () -> Unit,
    onOpenAccounts: () -> Unit,
    onOpenLearned: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val message by viewModel.messages.collectAsStateWithLifecycle()
    val snackbarHost = remember { SnackbarHostState() }
    var pinDialog by remember { mutableStateOf(false) }
    var confirmWipe by remember { mutableStateOf(false) }

    val exportCsv = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv"),
    ) { uri -> uri?.let(viewModel::exportCsv) }

    val exportJson = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri -> uri?.let(viewModel::exportJson) }

    val restore = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(viewModel::restore) }

    LaunchedEffect(message) {
        message?.let {
            val text = when (it) {
                is SettingsMessage.Info -> it.text
                is SettingsMessage.Error -> it.text
            }
            snackbarHost.showSnackbar(text)
            viewModel.consumeMessage()
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Settings") }) },
        snackbarHost = { SnackbarHost(snackbarHost) },
    ) { padding ->
        LazyColumn(Modifier.padding(padding)) {
            item {
                SectionHeader("Automatic detection")
                SwitchRow(
                    title = "Detect transactions from SMS",
                    subtitle = "Reads bank alerts as they arrive and asks what each payment was for. " +
                        "Turn this off and the app keeps working for transactions you add yourself.",
                    checked = state.smsDetectionEnabled,
                    onChange = viewModel::setSmsDetection,
                )
                ClickableRow(
                    title = "Import recent bank messages",
                    subtitle = "One-off scan of the last 30 days. Needs the SMS read permission; " +
                        "everything found lands uncategorised for you to confirm.",
                    onClick = viewModel::importRecentMessages,
                )
                ClickableRow("Detection diagnostics", "See which messages were used and which were skipped", onOpenDiagnostics)
            }

            item {
                SectionHeader("Device features")
                state.capabilities?.let { capabilities ->
                    CapabilityRow(
                        "Live notification prompts",
                        supported = capabilities.supportsLiveUpdates,
                        supportedText = "Prompts appear as a live status-bar chip and on the lock screen.",
                        unsupportedText = "This device runs an Android release without Live Updates, " +
                            "or you have turned them off for this app. Prompts still arrive as normal notifications.",
                    )
                    if (capabilities.isSamsung) {
                        CapabilityRow(
                            "Samsung Now Bar",
                            supported = capabilities.supportsNowBar,
                            supportedText = "Transaction prompts appear in the Now Bar" +
                                (capabilities.oneUiMajorVersion?.let { " (One UI $it)." } ?: "."),
                            unsupportedText = "The Now Bar shows third-party activity from One UI 8 " +
                                "(Android 16) onward. Prompts still arrive as normal notifications here.",
                        )
                    }
                    CapabilityRow(
                        "Quick Add tile",
                        supported = capabilities.supportsQuickSettingsTile,
                        supportedText = "Add the Expense Tracker tile from the Quick Settings edit screen " +
                            "to record a payment from anywhere, including the lock screen shade.",
                        unsupportedText = "Quick Settings tiles are unavailable on this device.",
                    )
                    CapabilityRow(
                        "Quick Add widget",
                        supported = capabilities.supportsLockScreenWidgets,
                        supportedText = "Long-press your home screen (or the lock screen widget area, " +
                            "where your device offers one) and add the Quick Add widget.",
                        unsupportedText = "This device does not offer a widget area the app can appear in.",
                    )
                    if (!capabilities.notificationsEnabled) {
                        Text(
                            "Notifications are turned off for this app, so transaction prompts cannot be shown. " +
                                "Detected transactions still appear in the app under \"Waiting for a category\".",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                }
            }

            item {
                SectionHeader("Organise")
                ClickableRow("Categories", "Add, rename, reorder or remove categories", onOpenCategories)
                ClickableRow("Accounts", "Rename the accounts detected from your bank messages", onOpenAccounts)
                ClickableRow("Learned categories", "What the app has remembered about your payees", onOpenLearned)
                SwitchRow(
                    "Budget alerts",
                    "Notify once when a budget passes 80% and again if it is exceeded",
                    state.budgetAlertsEnabled,
                    viewModel::setBudgetAlerts,
                )
            }

            item {
                SectionHeader("Privacy and security")
                SwitchRow(
                    title = "App lock",
                    subtitle = if (state.isPinSet) {
                        "A PIN is required to open the app."
                    } else {
                        "Require a PIN to open the app."
                    },
                    checked = state.appLockEnabled,
                    onChange = { enabled -> if (enabled) pinDialog = true else viewModel.disableAppLock() },
                )
                if (state.appLockEnabled) {
                    SwitchRow(
                        "Unlock with biometrics",
                        "Use your fingerprint or face instead of typing the PIN",
                        state.biometricEnabled,
                        viewModel::setBiometric,
                    )
                }
                Text(
                    "This app has no internet permission. Your transactions, bank details and message contents " +
                        "never leave this device -- there is no server to send them to.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }

            item {
                SectionHeader("Appearance")
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    for (mode in ThemeMode.entries) {
                        androidx.compose.material3.FilterChip(
                            selected = state.themeMode == mode,
                            onClick = { viewModel.setThemeMode(mode) },
                            label = { Text(mode.name.lowercase().replaceFirstChar { it.uppercase() }) },
                        )
                    }
                }
                SwitchRow(
                    "Use wallpaper colours",
                    "Match the app's palette to your wallpaper where Android supports it",
                    state.dynamicColor,
                    viewModel::setDynamicColor,
                )
            }

            item {
                SectionHeader("Your data")
                ClickableRow("Export as CSV", "Opens in Excel, Sheets or Numbers") {
                    exportCsv.launch("expense-tracker-${System.currentTimeMillis()}.csv")
                }
                ClickableRow("Export as JSON backup", "Complete backup including budgets and categories") {
                    exportJson.launch("expense-tracker-backup-${System.currentTimeMillis()}.json")
                }
                ClickableRow("Restore from backup", "Merges a JSON backup; duplicates are skipped") {
                    restore.launch(arrayOf("application/json"))
                }
                ClickableRow("Forget learned categories", "The app stops suggesting based on past choices") {
                    viewModel.forgetLearnedCategories()
                }
                ClickableRow("Delete all transactions", "Cannot be undone") { confirmWipe = true }
                HorizontalDivider()
            }
        }
    }

    if (pinDialog) {
        PinDialog(
            onDismiss = { pinDialog = false },
            onConfirm = { pin ->
                viewModel.setPin(pin)
                pinDialog = false
            },
        )
    }

    if (confirmWipe) {
        AlertDialog(
            onDismissRequest = { confirmWipe = false },
            title = { Text("Delete every transaction?") },
            text = { Text("Your categories, budgets and accounts are kept. The transactions themselves cannot be recovered unless you have a backup.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteAllTransactions()
                    confirmWipe = false
                }) { Text("Delete everything") }
            },
            dismissButton = { TextButton(onClick = { confirmWipe = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun ClickableRow(title: String, subtitle: String, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(16.dp)) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Text(
            subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * States plainly whether a feature works on this device and what happens if it
 * does not, rather than hiding the row and leaving the user to wonder.
 */
@Composable
private fun CapabilityRow(
    title: String,
    supported: Boolean,
    supportedText: String,
    unsupportedText: String,
) {
    Column(Modifier.fillMaxWidth().padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                if (supported) "Available" else "Not available",
                style = MaterialTheme.typography.labelMedium,
                color = if (supported) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
            )
        }
        Text(
            if (supported) supportedText else unsupportedText,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PinDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var pin by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    val matches = pin.length >= 4 && pin == confirmation

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Set an app PIN") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = pin,
                    onValueChange = { value -> pin = value.filter { it.isDigit() }.take(8) },
                    label = { Text("PIN (4-8 digits)") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = confirmation,
                    onValueChange = { value -> confirmation = value.filter { it.isDigit() }.take(8) },
                    label = { Text("Confirm PIN") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    isError = confirmation.isNotEmpty() && !matches,
                    singleLine = true,
                )
                Text(
                    "If you forget this PIN there is no way to recover it -- the app has no account and no server. " +
                        "You would have to reinstall, so keep a backup.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(pin) }, enabled = matches) { Text("Enable") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
