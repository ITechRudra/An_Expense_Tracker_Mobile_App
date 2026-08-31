package com.rudra.expensetracker.ingest.sms

import android.content.Context
import android.provider.Telephony
import com.rudra.expensetracker.core.sms.RawSms
import com.rudra.expensetracker.domain.AppClock
import com.rudra.expensetracker.domain.IngestSmsUseCase
import com.rudra.expensetracker.domain.SmsIngestOutcome
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class BackfillReport(val scanned: Int, val imported: Int, val duplicates: Int, val ignored: Int)

/**
 * One-off import of bank alerts already in the inbox.
 *
 * This is the only use of READ_SMS. It runs when the user explicitly asks for
 * it, reads a bounded recent window rather than the whole inbox, and the app is
 * fully functional without it -- detection from that point forward needs only
 * RECEIVE_SMS.
 *
 * Imported transactions land unconfirmed, so nothing is filed under a category
 * the user did not choose.
 */
@Singleton
class SmsBackfillUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val ingest: IngestSmsUseCase,
    private val clock: AppClock,
) {

    suspend operator fun invoke(days: Int = DEFAULT_DAYS): BackfillReport = withContext(Dispatchers.IO) {
        val since = clock.nowMillis() - TimeUnit.DAYS.toMillis(days.toLong())
        var scanned = 0
        var imported = 0
        var duplicates = 0
        var ignored = 0

        val projection = arrayOf(
            Telephony.Sms._ID,
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE,
        )
        context.contentResolver.query(
            Telephony.Sms.Inbox.CONTENT_URI,
            projection,
            "${Telephony.Sms.DATE} >= ?",
            arrayOf(since.toString()),
            "${Telephony.Sms.DATE} ASC",
        )?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(Telephony.Sms._ID)
            val addressIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
            val bodyIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.BODY)
            val dateIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.DATE)

            while (cursor.moveToNext()) {
                scanned++
                val sms = RawSms(
                    id = "inbox:${cursor.getString(idIndex)}",
                    sender = cursor.getString(addressIndex).orEmpty(),
                    body = cursor.getString(bodyIndex).orEmpty(),
                    receivedAtEpochMillis = cursor.getLong(dateIndex),
                )
                if (sms.body.isBlank()) continue

                // A single unreadable row must not abandon the whole import.
                when (runCatching { ingest(sms) }.getOrNull()) {
                    is SmsIngestOutcome.Prompt -> imported++
                    is SmsIngestOutcome.Duplicate -> duplicates++
                    else -> ignored++
                }
            }
        }
        BackfillReport(scanned, imported, duplicates, ignored)
    }

    companion object {
        /** Long enough to be useful, short enough not to trawl years of inbox. */
        const val DEFAULT_DAYS = 30
    }
}
