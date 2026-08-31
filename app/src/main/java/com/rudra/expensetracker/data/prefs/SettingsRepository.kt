package com.rudra.expensetracker.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/**
 * User preferences.
 *
 * The app-lock secret is deliberately not stored here -- see [com.rudra
 * .expensetracker.security.AppLockManager], which keeps it in EncryptedSharedPreferences.
 */
@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val store = context.dataStore

    val onboardingComplete: Flow<Boolean> = store.data.map { it[KEY_ONBOARDED] ?: false }
    val smsDetectionEnabled: Flow<Boolean> = store.data.map { it[KEY_SMS_ENABLED] ?: true }
    val appLockEnabled: Flow<Boolean> = store.data.map { it[KEY_APP_LOCK] ?: false }
    val biometricEnabled: Flow<Boolean> = store.data.map { it[KEY_BIOMETRIC] ?: false }
    val budgetAlertsEnabled: Flow<Boolean> = store.data.map { it[KEY_BUDGET_ALERTS] ?: true }
    val dynamicColorEnabled: Flow<Boolean> = store.data.map { it[KEY_DYNAMIC_COLOR] ?: true }
    val themeMode: Flow<ThemeMode> = store.data.map { prefs ->
        runCatching { ThemeMode.valueOf(prefs[KEY_THEME] ?: ThemeMode.SYSTEM.name) }
            .getOrDefault(ThemeMode.SYSTEM)
    }

    suspend fun setOnboardingComplete(value: Boolean) = put(KEY_ONBOARDED, value)
    suspend fun setSmsDetectionEnabled(value: Boolean) = put(KEY_SMS_ENABLED, value)
    suspend fun setAppLockEnabled(value: Boolean) = put(KEY_APP_LOCK, value)
    suspend fun setBiometricEnabled(value: Boolean) = put(KEY_BIOMETRIC, value)
    suspend fun setBudgetAlertsEnabled(value: Boolean) = put(KEY_BUDGET_ALERTS, value)
    suspend fun setDynamicColorEnabled(value: Boolean) = put(KEY_DYNAMIC_COLOR, value)

    suspend fun setThemeMode(mode: ThemeMode) {
        store.edit { it[KEY_THEME] = mode.name }
    }

    private suspend fun put(key: Preferences.Key<Boolean>, value: Boolean) {
        store.edit { it[key] = value }
    }

    private companion object {
        val KEY_ONBOARDED = booleanPreferencesKey("onboarding_complete")
        val KEY_SMS_ENABLED = booleanPreferencesKey("sms_detection_enabled")
        val KEY_APP_LOCK = booleanPreferencesKey("app_lock_enabled")
        val KEY_BIOMETRIC = booleanPreferencesKey("biometric_enabled")
        val KEY_BUDGET_ALERTS = booleanPreferencesKey("budget_alerts_enabled")
        val KEY_DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color_enabled")
        val KEY_THEME = stringPreferencesKey("theme_mode")
    }
}
