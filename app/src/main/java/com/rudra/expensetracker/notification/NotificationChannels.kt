package com.rudra.expensetracker.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.content.getSystemService
import com.rudra.expensetracker.R

/**
 * Separate channels so a user who finds budget warnings noisy can silence them
 * without also silencing the categorisation prompt that makes the app work.
 */
object NotificationChannels {

    const val TRANSACTION_PROMPT = "transaction_prompt"
    const val BUDGET_ALERTS = "budget_alerts"
    const val STATUS = "status"

    fun ensureCreated(context: Context) {
        val manager = context.getSystemService<NotificationManager>() ?: return
        manager.createNotificationChannels(
            listOf(
                NotificationChannel(
                    TRANSACTION_PROMPT,
                    context.getString(R.string.channel_transaction_prompt),
                    // High so the prompt is answerable straight away; a heads-up
                    // that is missed becomes an uncategorised row to clean up later.
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply {
                    description = context.getString(R.string.channel_transaction_prompt_desc)
                    setShowBadge(true)
                },
                NotificationChannel(
                    BUDGET_ALERTS,
                    context.getString(R.string.channel_budget_alerts),
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply {
                    description = context.getString(R.string.channel_budget_alerts_desc)
                },
                NotificationChannel(
                    STATUS,
                    context.getString(R.string.channel_status),
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description = context.getString(R.string.channel_status_desc)
                    setShowBadge(false)
                },
            ),
        )
    }
}
