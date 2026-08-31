package com.rudra.expensetracker.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.rudra.expensetracker.ui.quickadd.QuickAddActivity

/**
 * A one-tap "add expense" widget.
 *
 * Declared as `keyguard|home_screen` in its metadata. There is no API for an
 * app to place itself on the lock screen -- that is the system's picker to
 * offer -- so the widget simply makes itself eligible wherever the device has a
 * widget area, and Settings tells the user plainly where it can appear.
 */
class QuickAddWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            GlanceTheme {
                Column(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .background(GlanceTheme.colors.primaryContainer)
                        .padding(12.dp)
                        .clickable(actionStartActivity(QuickAddActivity.quickAddIntent(context))),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = "＋ Add expense",
                        style = TextStyle(
                            color = GlanceTheme.colors.onPrimaryContainer,
                            fontWeight = FontWeight.Medium,
                        ),
                    )
                }
            }
        }
    }

}

class QuickAddWidgetReceiver : GlanceAppWidgetReceiver() {

    override val glanceAppWidget: GlanceAppWidget = QuickAddWidget()

    companion object {
        /** Refreshes the widget after a transaction is confirmed elsewhere. */
        suspend fun refresh(context: Context) {
            QuickAddWidget().updateAll(context)
        }

        /** Fire-and-forget variant for callers that are not in a coroutine. */
        fun requestRefresh(context: Context) {
            CoroutineScope(Dispatchers.Default).launch { runCatching { refresh(context) } }
        }
    }
}
