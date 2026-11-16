package com.huangder.lumibooks.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.Icon
import android.media.session.MediaSession
import android.os.Build
import com.huangder.lumibooks.MainActivity
import com.huangder.lumibooks.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TtsNotificationManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        const val CHANNEL_ID = "tts_playback"
        const val NOTIFICATION_ID = 2207
        const val FLOATING_LOCKED_NOTIFICATION_ID = 2208
    }

    /**
     * A plain notification while the floating subtitles are locked. Some ROMs (HyperOS, One UI)
     * render the playback notification as their own media card and drop custom actions, so the
     * unlock control must not depend on the media notification.
     */
    fun showFloatingLocked() {
        createChannel()
        val unlock = FloatingSubtitleUnlockReceiver.pendingIntent(context)
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(context, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(context)
        }
        val notification = builder
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(context.getString(R.string.floating_subtitle_locked_notification_title))
            .setContentText(context.getString(R.string.floating_subtitle_locked_notification_text))
            .setContentIntent(unlock)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .addAction(
                Notification.Action.Builder(
                    Icon.createWithResource(context, android.R.drawable.ic_lock_lock),
                    context.getString(R.string.floating_subtitle_unlock),
                    unlock
                ).build()
            )
            .build()
        runCatching {
            context.getSystemService(NotificationManager::class.java)
                .notify(FLOATING_LOCKED_NOTIFICATION_ID, notification)
        }
    }

    fun hideFloatingLocked() {
        context.getSystemService(NotificationManager::class.java)
            .cancel(FLOATING_LOCKED_NOTIFICATION_ID)
    }

    fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.tts_notification_channel),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = context.getString(R.string.tts_notification_channel_description)
            setSound(null, null)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun buildNotification(
        bookTitle: String,
        chapterIndex: Int,
        isPlaying: Boolean,
        mediaSessionToken: MediaSession.Token,
        largeIcon: Bitmap? = null,
        floatingLocked: Boolean = false
    ): Notification {
        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val previousIntent = serviceAction(TtsForegroundService.ACTION_PREVIOUS, 1)
        val playPauseIntent = serviceAction(TtsForegroundService.ACTION_PLAY_PAUSE, 2)
        val nextIntent = serviceAction(TtsForegroundService.ACTION_NEXT, 3)
        val stopIntent = serviceAction(TtsForegroundService.ACTION_STOP, 4)
        val unlockIntent = FloatingSubtitleUnlockReceiver.pendingIntent(context)

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(context, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(context)
        }

        return builder
            .setSmallIcon(R.mipmap.ic_launcher)
            .apply { largeIcon?.let(::setLargeIcon) }
            .setContentTitle(bookTitle.ifBlank { context.getString(R.string.app_name) })
            .setContentText(context.getString(R.string.tts_notification_chapter, chapterIndex + 1))
            .setContentIntent(contentIntent)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_TRANSPORT)
            .setShowWhen(false)
            .addAction(
                Notification.Action.Builder(
                    Icon.createWithResource(context, android.R.drawable.ic_media_previous),
                    context.getString(R.string.tts_previous_sentence),
                    previousIntent
                ).build()
            )
            .addAction(
                Notification.Action.Builder(
                    Icon.createWithResource(
                        context,
                        if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
                    ),
                    context.getString(if (isPlaying) R.string.tts_pause else R.string.tts_play),
                    playPauseIntent
                ).build()
            )
            .addAction(
                Notification.Action.Builder(
                    Icon.createWithResource(context, android.R.drawable.ic_media_next),
                    context.getString(R.string.tts_next_sentence),
                    nextIntent
                ).build()
            )
            .addAction(
                Notification.Action.Builder(
                    Icon.createWithResource(context, android.R.drawable.ic_menu_close_clear_cancel),
                    context.getString(R.string.tts_stop),
                    stopIntent
                ).build()
            )
            .apply {
                // Locked overlays ignore touches, so the notification is the way back to dragging them.
                if (floatingLocked) addAction(
                    Notification.Action.Builder(
                        Icon.createWithResource(context, android.R.drawable.ic_lock_lock),
                        context.getString(R.string.floating_subtitle_unlock),
                        unlockIntent
                    ).build()
                )
            }
            .setStyle(
                Notification.MediaStyle()
                    .setMediaSession(mediaSessionToken)
                    .setShowActionsInCompactView(0, 1, 2)
            )
            .build()
    }

    private fun serviceAction(action: String, requestCode: Int): PendingIntent {
        return PendingIntent.getService(
            context,
            requestCode,
            Intent(context, TtsForegroundService::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
