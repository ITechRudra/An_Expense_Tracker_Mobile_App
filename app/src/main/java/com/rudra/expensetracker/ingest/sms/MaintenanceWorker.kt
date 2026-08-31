package com.rudra.expensetracker.ingest.sms

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.rudra.expensetracker.data.local.IngestLogDao
import com.rudra.expensetracker.domain.AppClock
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/** Daily housekeeping. Deliberately the only recurring background work. */
@HiltWorker
class MaintenanceWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val ingestLog: IngestLogDao,
    private val clock: AppClock,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val cutoff = clock.nowMillis() - TimeUnit.DAYS.toMillis(LOG_RETENTION_DAYS)
        return runCatching { ingestLog.pruneBefore(cutoff) }
            .fold(onSuccess = { Result.success() }, onFailure = { Result.retry() })
    }

    private companion object {
        const val LOG_RETENTION_DAYS = 14L
    }
}
