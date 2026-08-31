package com.rudra.expensetracker.notification

import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.content.getSystemService
import com.rudra.expensetracker.R
import com.rudra.expensetracker.core.budget.BudgetState
import com.rudra.expensetracker.core.budget.BudgetStatus
import com.rudra.expensetracker.util.formatRupees
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.absoluteValue

/**
 * Notifies once per threshold crossing.
 *
 * The caller records the state it alerted at, so a user who is 90% through a
 * budget is told once rather than on every subsequent coffee.
 */
@Singleton
class BudgetAlertNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun notify(status: BudgetStatus, categoryName: String) {
        if (!status.isAlerting) return
        NotificationChannels.ensureCreated(context)

        val title = when (status.state) {
            BudgetState.EXCEEDED -> context.getString(R.string.budget_exceeded_title, categoryName)
            else -> context.getString(R.string.budget_near_title, categoryName)
        }
        val body = context.getString(
            R.string.budget_alert_body,
            status.spent.formatRupees(),
            status.limit.formatRupees(),
            status.percentUsed,
        )

        val notification = NotificationCompat.Builder(context, NotificationChannels.BUDGET_ALERTS)
            .setSmallIcon(R.drawable.ic_budget)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .build()

        context.getSystemService<NotificationManager>()
            ?.notify("budget:${status.categoryId}".hashCode().absoluteValue, notification)
    }
}
