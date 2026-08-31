package com.rudra.expensetracker.ui.lock

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rudra.expensetracker.data.prefs.SettingsRepository
import com.rudra.expensetracker.security.AppLockManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class LockUiState(
    val biometricEnabled: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class LockViewModel @Inject constructor(
    private val appLock: AppLockManager,
    settings: SettingsRepository,
) : ViewModel() {

    private val error = MutableStateFlow<String?>(null)

    val state: StateFlow<LockUiState> = combine(
        settings.biometricEnabled,
        error,
    ) { biometric, currentError ->
        LockUiState(biometricEnabled = biometric, error = currentError)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LockUiState())

    fun verify(pin: String): Boolean {
        val ok = appLock.verifyPin(pin)
        error.value = if (ok) null else "Incorrect PIN"
        return ok
    }

    fun clearError() { error.value = null }
}
