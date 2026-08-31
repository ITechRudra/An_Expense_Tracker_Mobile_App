package com.rudra.expensetracker.ui.onboarding

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rudra.expensetracker.data.prefs.SettingsRepository
import com.rudra.expensetracker.notification.DeviceCapabilities
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class OnboardingUiState(
    val smsGranted: Boolean = false,
    val notificationsGranted: Boolean = false,
    val showQuickAddStep: Boolean = false,
    val quickAddDescription: String = "",
)

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsRepository,
    private val capabilities: DeviceCapabilities,
) : ViewModel() {

    private val _state = MutableStateFlow(buildState())
    val state: StateFlow<OnboardingUiState> = _state.asStateFlow()

    fun refresh() { _state.value = buildState() }

    fun complete() {
        viewModelScope.launch { settings.setOnboardingComplete(true) }
    }

    private fun buildState() = OnboardingUiState(
        smsGranted = hasPermission(Manifest.permission.RECEIVE_SMS),
        notificationsGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            hasPermission(Manifest.permission.POST_NOTIFICATIONS),
        showQuickAddStep = capabilities.supportsQuickSettingsTile || capabilities.supportsLockScreenWidgets,
        quickAddDescription = quickAddDescription(),
    )

    /**
     * Describes only what this device actually offers. Nothing here promises a
     * lock-screen widget on a device whose launcher has no widget area.
     */
    private fun quickAddDescription(): String = buildString {
        append("Add the Expense Tracker tile to Quick Settings to record a cash payment from anywhere, ")
        append("including over the lock screen.")
        if (capabilities.supportsLockScreenWidgets) {
            append("\n\nThe Quick Add widget can also be placed on your home screen, and on the lock screen ")
            append("on devices that offer a lock-screen widget area.")
        }
        if (capabilities.supportsNowBar) {
            append("\n\nThis Samsung device supports the Now Bar, so a detected transaction will appear there ")
            append("until you say what it was for.")
        } else if (capabilities.isSamsung) {
            append("\n\nThe Now Bar shows third-party activity from One UI 8 (Android 16) onward. ")
            append("On this device, prompts arrive as standard notifications.")
        }
    }

    private fun hasPermission(permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}
