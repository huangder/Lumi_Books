package com.huangder.lumibooks.service

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Unlocks the touch-through floating subtitles from a notification. A broadcast works whether or
 * not the playback service is running (for example while previewing the overlay in settings).
 */
@AndroidEntryPoint
class FloatingSubtitleUnlockReceiver : BroadcastReceiver() {
    @Inject lateinit var overlayController: FloatingSubtitleOverlayController

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_UNLOCK) return
        overlayController.setLocked(false)
    }

    companion object {
        const val ACTION_UNLOCK = "com.huangder.lumibooks.floating.UNLOCK"

        fun pendingIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, FloatingSubtitleUnlockReceiver::class.java).setAction(ACTION_UNLOCK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
