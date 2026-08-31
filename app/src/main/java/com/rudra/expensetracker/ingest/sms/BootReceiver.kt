package com.rudra.expensetracker.ingest.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * Restores scheduled maintenance after a reboot or an app update.
 *
 * Nothing about transaction detection itself needs restoring: the SMS receiver
 * is declared in the manifest, so the system re-registers it automatically and
 * detection keeps working whether or not the app has been opened since.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> schedule(context)
        }
    }

    companion object {
        const val MAINTENANCE_WORK = "expense-tracker-maintenance"

        /** Prunes the diagnostic ingest log and refreshes budget alert state. */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<MaintenanceWorker>(1, TimeUnit.DAYS)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                MAINTENANCE_WORK,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }
}
