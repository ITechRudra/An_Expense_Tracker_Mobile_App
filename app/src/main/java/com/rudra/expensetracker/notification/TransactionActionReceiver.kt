package com.rudra.expensetracker.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.rudra.expensetracker.data.repository.CategoryRepository
import com.rudra.expensetracker.data.repository.TransactionRepository
import com.rudra.expensetracker.domain.ConfirmTransactionUseCase
import com.rudra.expensetracker.widget.QuickAddWidgetReceiver
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Handles a category chosen straight from the notification -- the interaction
 * that keeps the whole product to one tap.
 */
@AndroidEntryPoint
class TransactionActionReceiver : BroadcastReceiver() {

    @Inject lateinit var confirm: ConfirmTransactionUseCase
    @Inject lateinit var transactions: TransactionRepository
    @Inject lateinit var categories: CategoryRepository
    @Inject lateinit var notifier: TransactionNotifier

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onReceive(context: Context, intent: Intent) {
        val transactionId = intent.getStringExtra(EXTRA_TRANSACTION_ID) ?: return
        val pendingResult = goAsync()

        scope.launch {
            try {
                when (intent.action) {
                    ACTION_CATEGORISE -> {
                        val categoryId = intent.getStringExtra(EXTRA_CATEGORY_ID) ?: return@launch
                        val updated = confirm(transactionId, categoryId, description = null)
                        if (updated != null) {
                            val name = categories.byId(categoryId)?.name ?: categoryId
                            notifier.showConfirmed(updated, name)
                            QuickAddWidgetReceiver.requestRefresh(context)
                        } else {
                            notifier.cancel(transactionId)
                        }
                    }

                    ACTION_DISMISS -> {
                        // Swiping the prompt away deletes the unconfirmed row: the
                        // user has said this is not something they want tracked, and
                        // leaving an uncategorised entry behind would contradict that.
                        val existing = transactions.byId(transactionId)
                        if (existing != null && !existing.isConfirmed) {
                            transactions.delete(transactionId)
                        }
                        notifier.cancel(transactionId)
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_CATEGORISE = "com.rudra.expensetracker.action.CATEGORISE"
        const val ACTION_DISMISS = "com.rudra.expensetracker.action.DISMISS"
        const val EXTRA_TRANSACTION_ID = "transaction_id"
        const val EXTRA_CATEGORY_ID = "category_id"
    }
}
