package com.rudra.expensetracker.ui.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rudra.expensetracker.data.export.DataExporter
import com.rudra.expensetracker.data.export.DataImporter
import com.rudra.expensetracker.data.prefs.SettingsRepository
import com.rudra.expensetracker.data.prefs.ThemeMode
import com.rudra.expensetracker.data.repository.MerchantMappingRepository
import com.rudra.expensetracker.data.repository.TransactionRepository
import com.rudra.expensetracker.ingest.sms.BackfillReport
import com.rudra.expensetracker.ingest.sms.SmsBackfillUseCase
import com.rudra.expensetracker.notification.DeviceCapabilities
import com.rudra.expensetracker.security.AppLockManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class CapabilityInfo(
    val isSamsung: Boolean,
    val supportsLiveUpdates: Boolean,
    val supportsNowBar: Boolean,
    val supportsQuickSettingsTile: Boolean,
    val supportsLockScreenWidgets: Boolean,
    val oneUiMajorVersion: Int?,
    val notificationsEnabled: Boolean,
)

data class SettingsUiState(
    val smsDetectionEnabled: Boolean = true,
    val appLockEnabled: Boolean = false,
    val biometricEnabled: Boolean = false,
    val budgetAlertsEnabled: Boolean = true,
    val dynamicColor: Boolean = true,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val isPinSet: Boolean = false,
    val capabilities: CapabilityInfo? = null,
)

/** One-shot results surfaced as a snackbar rather than kept in screen state. */
sealed interface SettingsMessage {
    data class Info(val text: String) : SettingsMessage
    data class Error(val text: String) : SettingsMessage
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val exporter: DataExporter,
    private val importer: DataImporter,
    private val backfill: SmsBackfillUseCase,
    private val transactions: TransactionRepository,
    private val mappings: MerchantMappingRepository,
    private val appLock: AppLockManager,
    private val capabilities: DeviceCapabilities,
) : ViewModel() {

    private val _messages = MutableStateFlow<SettingsMessage?>(null)
    val messages: StateFlow<SettingsMessage?> = _messages.asStateFlow()

    private val pinSet = MutableStateFlow(appLock.isPinSet)

    val state: StateFlow<SettingsUiState> = combine(
        settings.smsDetectionEnabled,
        settings.appLockEnabled,
        settings.biometricEnabled,
        settings.budgetAlertsEnabled,
        combine(settings.dynamicColorEnabled, settings.themeMode, pinSet) { dynamic, theme, hasPin ->
            Triple(dynamic, theme, hasPin)
        },
    ) { sms, lock, biometric, budgetAlerts, (dynamic, theme, hasPin) ->
        SettingsUiState(
            smsDetectionEnabled = sms,
            appLockEnabled = lock,
            biometricEnabled = biometric,
            budgetAlertsEnabled = budgetAlerts,
            dynamicColor = dynamic,
            themeMode = theme,
            isPinSet = hasPin,
            capabilities = CapabilityInfo(
                isSamsung = capabilities.isSamsung,
                supportsLiveUpdates = capabilities.supportsLiveUpdates,
                supportsNowBar = capabilities.supportsNowBar,
                supportsQuickSettingsTile = capabilities.supportsQuickSettingsTile,
                supportsLockScreenWidgets = capabilities.supportsLockScreenWidgets,
                oneUiMajorVersion = capabilities.oneUiMajorVersion,
                notificationsEnabled = capabilities.canPostNotifications,
            ),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun consumeMessage() { _messages.value = null }

    fun setSmsDetection(enabled: Boolean) = viewModelScope.launch { settings.setSmsDetectionEnabled(enabled) }
    fun setBudgetAlerts(enabled: Boolean) = viewModelScope.launch { settings.setBudgetAlertsEnabled(enabled) }
    fun setDynamicColor(enabled: Boolean) = viewModelScope.launch { settings.setDynamicColorEnabled(enabled) }
    fun setThemeMode(mode: ThemeMode) = viewModelScope.launch { settings.setThemeMode(mode) }
    fun setBiometric(enabled: Boolean) = viewModelScope.launch { settings.setBiometricEnabled(enabled) }

    fun setPin(pin: String) {
        viewModelScope.launch {
            runCatching { appLock.setPin(pin) }
                .onSuccess {
                    pinSet.value = true
                    settings.setAppLockEnabled(true)
                    _messages.value = SettingsMessage.Info("App lock enabled")
                }
                .onFailure {
                    _messages.value = SettingsMessage.Error("PIN must be at least ${AppLockManager.MIN_PIN_LENGTH} digits")
                }
        }
    }

    fun disableAppLock() {
        viewModelScope.launch {
            appLock.clearPin()
            pinSet.value = false
            settings.setAppLockEnabled(false)
            settings.setBiometricEnabled(false)
        }
    }

    fun exportCsv(target: Uri) = runExport("CSV") { exporter.exportCsv(target) }
    fun exportJson(target: Uri) = runExport("JSON backup") { exporter.exportJson(target) }

    private fun runExport(label: String, block: suspend () -> Int) {
        viewModelScope.launch {
            runCatching { block() }
                .onSuccess { _messages.value = SettingsMessage.Info("Exported $it transactions as $label") }
                .onFailure { _messages.value = SettingsMessage.Error("Export failed: ${it.message.orEmpty()}") }
        }
    }

    fun restore(source: Uri) {
        viewModelScope.launch {
            runCatching { importer.restore(source) }
                .onSuccess {
                    _messages.value = SettingsMessage.Info(
                        "Restored ${it.restored} transactions, skipped ${it.skippedDuplicates} already present",
                    )
                }
                .onFailure { _messages.value = SettingsMessage.Error("Restore failed: ${it.message.orEmpty()}") }
        }
    }

    fun importRecentMessages() {
        viewModelScope.launch {
            runCatching { backfill() }
                .onSuccess { report: BackfillReport ->
                    _messages.value = SettingsMessage.Info(
                        "Scanned ${report.scanned} messages, imported ${report.imported}",
                    )
                }
                .onFailure {
                    _messages.value = SettingsMessage.Error(
                        "Could not read messages. Grant the SMS permission and try again.",
                    )
                }
        }
    }

    fun forgetLearnedCategories() {
        viewModelScope.launch {
            mappings.deleteAll()
            _messages.value = SettingsMessage.Info("Learned merchant categories cleared")
        }
    }

    fun deleteAllTransactions() {
        viewModelScope.launch {
            transactions.deleteAll()
            _messages.value = SettingsMessage.Info("All transactions deleted")
        }
    }
}
