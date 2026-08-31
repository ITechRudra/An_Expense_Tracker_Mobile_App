package com.rudra.expensetracker.ingest.sms

import android.content.Context
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.rudra.expensetracker.core.sms.RawSms

/**
 * Retry path for an alert that could not be processed on arrival -- typically
 * because the database was momentarily unavailable during a restore.
 *
 * The message is handed to WorkManager, which survives process death and
 * reboot, so a transient failure costs a delay rather than a lost transaction.
 */
object SmsIngestFailureQueue {

    fun enqueue(context: Context, messages: List<RawSms>) {
        for (sms in messages) {
            val request = OneTimeWorkRequestBuilder<SmsIngestWorker>()
                .setInputData(
                    Data.Builder()
                        .putString(SmsIngestWorker.KEY_ID, sms.id)
                        .putString(SmsIngestWorker.KEY_SENDER, sms.sender)
                        .putString(SmsIngestWorker.KEY_BODY, sms.body)
                        .putLong(SmsIngestWorker.KEY_RECEIVED_AT, sms.receivedAtEpochMillis)
                        .build(),
                )
                .build()
            WorkManager.getInstance(context)
                // Keyed by message id so a redelivered broadcast cannot queue it twice.
                .enqueueUniqueWork("sms-ingest-${sms.id}", ExistingWorkPolicy.KEEP, request)
        }
    }
}
