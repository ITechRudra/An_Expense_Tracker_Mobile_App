package com.rudra.expensetracker.widget

import android.app.PendingIntent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.rudra.expensetracker.ui.quickadd.QuickAddActivity

/**
 * Quick Settings tile that opens the quick-add sheet.
 *
 * This is the officially supported way to reach the app from the lock screen on
 * every current Android version and every OEM skin, which is why it exists
 * alongside the widget rather than instead of it.
 */
class QuickAddTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        qsTile?.apply {
            state = Tile.STATE_INACTIVE
            label = getString(com.rudra.expensetracker.R.string.tile_label)
            updateTile()
        }
    }

    override fun onClick() {
        super.onClick()
        val intent = QuickAddActivity.quickAddIntent(this)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // From API 34 the pending-intent form is required; the older call
            // is a no-op there and the sheet would never appear.
            startActivityAndCollapse(
                PendingIntent.getActivity(
                    this,
                    0,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}
