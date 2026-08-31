package com.rudra.expensetracker.ingest.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.rudra.expensetracker.core.sms.RawSms
import com.rudra.expensetracker.domain.SmsIngestOutcome
import com.rudra.expensetracker.domain.IngestSmsUseCase
import com.rudra.expensetracker.notification.TransactionNotifier
import com.rudra.expensetracker.data.prefs.SettingsRepository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Receives bank alerts as they arrive.
 *
 * The app is not the default SMS handler and does not consume the broadcast --
 * the user's messaging app still receives every message normally.
 *
 * Work is done inside [goAsync] rather than a foreground service: parsing plus
 * one insert takes milliseconds, and the platform already guarantees the
 * process stays alive for the duration. Nothing here needs to keep running.
 */
@AndroidEntryPoint
class SmsReceiver : BroadcastReceiver() {

    @Inject lateinit var ingest: IngestSmsUseCase
    @Inject lateinit var notifier: TransactionNotifier
    @Inject lateinit var settings: SettingsRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val messages = runCatching { assemble(intent) }.getOrNull().orEmpty()
        if (messages.isEmpty()) return

        val pendingResult = goAsync()
        scope.launch {
            try {
                // Comfortably inside the ~10s the platform allows a receiver.
                withTimeoutOrNull(SMS_PROCESSING_TIMEOUT_MILLIS) {
                    if (!settings.smsDetectionEnabled.first()) return@withTimeoutOrNull
                    for (sms in messages) {
                        when (val outcome = ingest(sms)) {
                            is SmsIngestOutcome.Prompt ->
                                notifier.promptForCategory(outcome.transaction, outcome.suggestion)
                            // Duplicates and filtered messages are silent by design.
                            is SmsIngestOutcome.Duplicate -> Unit
                            is SmsIngestOutcome.Ignored -> Unit
                        }
                    }
                }
            } catch (t: Throwable) {
                // A single malformed alert must never crash the receiver and
                // cost the user every future one.
                SmsIngestFailureQueue.enqueue(context, messages)
            } finally {
                pendingResult.finish()
            }
        }
    }

    /**
     * Rebuilds whole messages from the delivered PDUs.
     *
     * A long bank alert arrives as several parts; joining them by sender is
     * required or the amount and the reference end up in different "messages".
     */
    private fun assemble(intent: Intent): List<RawSms> =
        Telephony.Sms.Intents.getMessagesFromIntent(intent)
            .orEmpty()
            .filterNotNull()
            .groupBy { it.originatingAddress.orEmpty() }
            .map { (sender, parts) ->
                val body = parts.joinToString("") { it.displayMessageBody.orEmpty() }
                val timestamp = parts.first().timestampMillis
                RawSms(
                    id = "$sender:$timestamp:${body.length}",
                    sender = sender,
                    body = body,
                    receivedAtEpochMillis = timestamp,
                )
            }
            .filter { it.body.isNotBlank() }

    private companion object {
        const val SMS_PROCESSING_TIMEOUT_MILLIS = 8_000L
    }
}
