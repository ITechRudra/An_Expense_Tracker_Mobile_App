package com.rudra.expensetracker.ui.quickadd

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rudra.expensetracker.ui.theme.ExpenseTrackerTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * The lightweight surface for the two things a user does most: saying what a
 * detected payment was for, and jotting down a cash payment.
 *
 * It is a separate, transparent, single-top activity shown over the lock screen
 * so neither task ever requires opening the whole app.
 */
@AndroidEntryPoint
class QuickAddActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val transactionId = intent.getStringExtra(EXTRA_TRANSACTION_ID)

        setContent {
            val viewModel: QuickAddViewModel = hiltViewModel()
            val state by viewModel.state.collectAsStateWithLifecycle()

            ExpenseTrackerTheme(
                themeMode = state.themeMode,
                dynamicColor = state.dynamicColor,
            ) {
                QuickAddSheet(
                    transactionId = transactionId,
                    state = state,
                    viewModel = viewModel,
                    onDismiss = { finish() },
                )
            }
        }
    }

    companion object {
        private const val EXTRA_TRANSACTION_ID = "transaction_id"

        /** Opens the sheet to categorise an already-detected transaction. */
        fun categoriseIntent(context: Context, transactionId: String): Intent =
            Intent(context, QuickAddActivity::class.java).apply {
                putExtra(EXTRA_TRANSACTION_ID, transactionId)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }

        /** Opens the sheet for a fresh manual entry, from the tile or the widget. */
        fun quickAddIntent(context: Context): Intent =
            Intent(context, QuickAddActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
    }
}
