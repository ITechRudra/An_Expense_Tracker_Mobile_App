package com.rudra.expensetracker.notification

import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.getSystemService
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * What this particular device can actually do.
 *
 * Every advanced surface in the app is gated on a real runtime check rather
 * than on a guess about the phone. Where a capability is absent the app falls
 * back to a plain notification and says so in Settings, instead of pretending.
 */
@Singleton
class DeviceCapabilities @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val notificationManager: NotificationManager?
        get() = context.getSystemService()

    val isSamsung: Boolean
        get() = Build.MANUFACTURER.equals("samsung", ignoreCase = true)

    /**
     * Android 16 (API 36) Live Updates: an ongoing notification the system may
     * promote to a status-bar chip and the lock screen.
     *
     * Requires both the platform version and the per-app user setting, which is
     * why this is a runtime check and not a version constant.
     */
    val supportsLiveUpdates: Boolean
        get() = Build.VERSION.SDK_INT >= 36 &&
            runCatching { notificationManager?.canPostPromotedNotifications() == true }
                .getOrDefault(false)

    /**
     * Samsung's Now Bar surfaces Android 16 Live Updates; there is no separate
     * public Samsung SDK to integrate against, so a promoted Live Update *is*
     * the supported integration. One UI 8 is the first release that consumes
     * third-party Live Updates.
     */
    val supportsNowBar: Boolean
        get() = isSamsung && supportsLiveUpdates && oneUiMajorVersion.let { it == null || it >= ONE_UI_8 }

    /**
     * One UI major version, or null when it cannot be determined.
     *
     * Samsung exposes `Build.VERSION.SEM_PLATFORM_INT` on its own builds; it is
     * absent everywhere else, so it is read defensively and a null result is
     * treated as "unknown, assume capable" rather than as a hard no.
     */
    val oneUiMajorVersion: Int?
        get() = runCatching {
            val field = Build.VERSION::class.java.getDeclaredField("SEM_PLATFORM_INT")
            val platformInt = field.getInt(null)
            ((platformInt - SEM_PLATFORM_BASE) / 10_000).takeIf { it > 0 }
        }.getOrNull()

    /** Quick Settings tiles have existed since API 24; the app's minimum is 26. */
    val supportsQuickSettingsTile: Boolean get() = true

    /**
     * Lock screen widgets are a system picker feature, not something an app can
     * request. The app ships a normal AppWidget declared as keyguard-capable;
     * whether the launcher or lock screen offers it is the system's decision.
     */
    val supportsLockScreenWidgets: Boolean
        get() = Build.VERSION.SDK_INT >= 36 || isSamsung

    val canPostNotifications: Boolean
        get() = notificationManager?.areNotificationsEnabled() == true

    val hasTelephony: Boolean
        get() = context.packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)

    private companion object {
        const val ONE_UI_8 = 8
        /** Samsung encodes One UI N as 90000 + N * 10000 (One UI 8 -> 170000). */
        const val SEM_PLATFORM_BASE = 90_000
    }
}
