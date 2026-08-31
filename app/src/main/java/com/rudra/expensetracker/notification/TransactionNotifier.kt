package com.rudra.expensetracker.notification

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.core.app.NotificationCompat
import androidx.core.content.getSystemService
import com.rudra.expensetracker.R
import com.rudra.expensetracker.core.category.CategorySuggestion
import com.rudra.expensetracker.core.category.DefaultCategories
import com.rudra.expensetracker.core.money.Money
import com.rudra.expensetracker.core.sms.TransactionType
import com.rudra.expensetracker.data.local.TransactionEntity
import com.rudra.expensetracker.ui.quickadd.QuickAddActivity
import com.rudra.expensetracker.util.formatRupees
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.absoluteValue

/**
 * Presents the "what was this payment for?" prompt.
 *
 * The prompt is answerable without opening the app: the categories the user is
 * most likely to pick are notification actions, and the notification body opens
 * a single lightweight screen rather than the full app.
 */
@Singleton
class TransactionNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
    private val capabilities: DeviceCapabilities,
) {

    private val manager: NotificationManager? get() = context.getSystemService()

    fun promptForCategory(transaction: TransactionEntity, suggestion: CategorySuggestion?) {
        NotificationChannels.ensureCreated(context)
        val id = notificationId(transaction.id)
        val amount = Money(transaction.amountMinorUnits).formatRupees()
        val isExpense = transaction.type == TransactionType.EXPENSE

        val title = if (isExpense) {
            context.getString(R.string.notif_expense_title, amount)
        } else {
            context.getString(R.string.notif_income_title, amount)
        }
        val payee = transaction.merchant
            ?: transaction.upiId
            ?: transaction.bankName
            ?: context.getString(R.string.unknown_payee)
        val method = transaction.paymentMethod.name.replace('_', ' ')

        val builder = NotificationCompat.Builder(context, NotificationChannels.TRANSACTION_PROMPT)
            .setSmallIcon(if (isExpense) R.drawable.ic_expense else R.drawable.ic_income)
            .setContentTitle(title)
            .setContentText("$payee • $method")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(context.getString(R.string.notif_prompt_body, payee, method)),
            )
            .setSubText(context.getString(R.string.notif_prompt_question))
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setOnlyAlertOnce(true)
            .setAutoCancel(false)
            .setContentIntent(openIntent(transaction.id))
            .setDeleteIntent(dismissIntent(transaction.id))

        for (categoryId in quickCategories(transaction.type, suggestion)) {
            builder.addAction(
                0,
                categoryLabel(categoryId),
                categoryIntent(transaction.id, categoryId),
            )
        }
        // The platform shows at most three actions, so the third is always the
        // escape hatch into the full picker with a description field.
        builder.addAction(0, context.getString(R.string.notif_action_more), openIntent(transaction.id))

        applyLiveUpdate(builder, amount)
        manager?.notify(id, builder.build())
    }

    /**
     * Replaces the prompt with a short confirmation, then clears it.
     *
     * On a Live Updates device this is what ends the ongoing state, which is
     * also what removes the entry from Samsung's Now Bar.
     */
    fun showConfirmed(transaction: TransactionEntity, categoryName: String) {
        val id = notificationId(transaction.id)
        val amount = Money(transaction.amountMinorUnits).formatRupees()
        val payee = transaction.merchant ?: transaction.upiId ?: ""

        val builder = NotificationCompat.Builder(context, NotificationChannels.TRANSACTION_PROMPT)
            .setSmallIcon(R.drawable.ic_check)
            .setContentTitle(context.getString(R.string.notif_saved_title, amount))
            .setContentText(listOf(payee, categoryName).filter { it.isNotBlank() }.joinToString(" • "))
            .setContentIntent(openIntent(transaction.id))
            .setTimeoutAfter(CONFIRMATION_VISIBLE_MILLIS)
            .setAutoCancel(true)
            .setOngoing(false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
        manager?.notify(id, builder.build())
    }

    fun cancel(transactionId: String) {
        manager?.cancel(notificationId(transactionId))
    }

    /**
     * Opts the prompt into Android 16 Live Updates where the device supports it.
     *
     * The extra is set directly with the platform's documented key rather than
     * through a compat setter so the call compiles and behaves identically on
     * every AndroidX version; releases before Android 16 simply ignore it.
     */
    private fun applyLiveUpdate(builder: NotificationCompat.Builder, shortText: String) {
        if (!capabilities.supportsLiveUpdates) return

        builder.setOngoing(true)
            .addExtras(Bundle().apply { putBoolean(EXTRA_REQUEST_PROMOTED_ONGOING, true) })
        if (Build.VERSION.SDK_INT >= 36) {
            // The status-bar chip has room for the amount and nothing else.
            builder.addExtras(Bundle().apply { putCharSequence(EXTRA_SHORT_CRITICAL_TEXT, shortText) })
        }
    }

    /**
     * Up to two one-tap categories: the suggestion first when there is one, then
     * the most commonly used defaults for that direction.
     */
    private fun quickCategories(type: TransactionType, suggestion: CategorySuggestion?): List<String> {
        val defaults = if (type == TransactionType.EXPENSE) {
            listOf(DefaultCategories.FOOD, DefaultCategories.TRAVEL, DefaultCategories.SHOPPING)
        } else {
            listOf(DefaultCategories.SALARY, DefaultCategories.REFUND, DefaultCategories.TRANSFER)
        }
        val suggested = suggestion?.categoryId
        return (listOfNotNull(suggested) + defaults).distinct().take(QUICK_ACTION_COUNT)
    }

    private fun categoryLabel(categoryId: String): String =
        categoryId.removePrefix("other_")
            .split('_')
            .joinToString(" ") { part -> part.replaceFirstChar { it.uppercase() } }

    private fun categoryIntent(transactionId: String, categoryId: String): PendingIntent {
        val intent = Intent(context, TransactionActionReceiver::class.java).apply {
            action = TransactionActionReceiver.ACTION_CATEGORISE
            putExtra(TransactionActionReceiver.EXTRA_TRANSACTION_ID, transactionId)
            putExtra(TransactionActionReceiver.EXTRA_CATEGORY_ID, categoryId)
        }
        return PendingIntent.getBroadcast(
            context,
            requestCode(transactionId, categoryId),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun dismissIntent(transactionId: String): PendingIntent {
        val intent = Intent(context, TransactionActionReceiver::class.java).apply {
            action = TransactionActionReceiver.ACTION_DISMISS
            putExtra(TransactionActionReceiver.EXTRA_TRANSACTION_ID, transactionId)
        }
        return PendingIntent.getBroadcast(
            context,
            requestCode(transactionId, "dismiss"),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun openIntent(transactionId: String): PendingIntent {
        val intent = QuickAddActivity.categoriseIntent(context, transactionId)
        return PendingIntent.getActivity(
            context,
            requestCode(transactionId, "open"),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun notificationId(transactionId: String): Int = transactionId.hashCode().absoluteValue

    private fun requestCode(transactionId: String, discriminator: String): Int =
        "$transactionId#$discriminator".hashCode()

    private companion object {
        const val QUICK_ACTION_COUNT = 2
        const val CONFIRMATION_VISIBLE_MILLIS = 4_000L

        /** Platform keys for Android 16 Live Updates. */
        const val EXTRA_REQUEST_PROMOTED_ONGOING = "android.requestPromotedOngoing"
        const val EXTRA_SHORT_CRITICAL_TEXT = "android.shortCriticalText"
    }
}
