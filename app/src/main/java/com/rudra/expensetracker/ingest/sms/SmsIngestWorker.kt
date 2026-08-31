package com.rudra.expensetracker.ingest.sms

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.rudra.expensetracker.core.sms.RawSms
import com.rudra.expensetracker.domain.IngestSmsUseCase
import com.rudra.expensetracker.domain.SmsIngestOutcome
import com.rudra.expensetracker.notification.TransactionNotifier
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/** Deferred processing for an alert whose immediate attempt failed. */
@HiltWorker
class SmsIngestWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val ingest: IngestSmsUseCase,
    private val notifier: TransactionNotifier,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val body = inputData.getString(KEY_BODY) ?: return Result.failure()
        val sms = RawSms(
            id = inputData.getString(KEY_ID).orEmpty(),
            sender = inputData.getString(KEY_SENDER).orEmpty(),
            body = body,
            receivedAtEpochMillis = inputData.getLong(KEY_RECEIVED_AT, System.currentTimeMillis()),
        )
        return try {
            when (val outcome = ingest(sms)) {
                is SmsIngestOutcome.Prompt ->
                    notifier.promptForCategory(outcome.transaction, outcome.suggestion)
                else -> Unit
            }
            Result.success()
        } catch (t: Throwable) {
            // Bounded by WorkManager's own attempt limit; a permanently broken
            // message eventually stops retrying instead of looping forever.
            if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.failure()
        }
    }

    companion object {
        const val KEY_ID = "sms_id"
        const val KEY_SENDER = "sms_sender"
        const val KEY_BODY = "sms_body"
        const val KEY_RECEIVED_AT = "sms_received_at"
        private const val MAX_ATTEMPTS = 3
    }
}
