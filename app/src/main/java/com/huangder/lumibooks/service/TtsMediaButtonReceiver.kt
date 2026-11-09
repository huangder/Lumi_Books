package com.huangder.lumibooks.service

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import android.view.KeyEvent
import androidx.core.content.IntentCompat
import com.huangder.lumibooks.tts.TtsController
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/** The system supplies EXTRA_KEY_EVENT via fill-in, so this explicit broadcast must be mutable. */
internal fun ttsMediaButtonPendingIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
    context,
    1,
    Intent(Intent.ACTION_MEDIA_BUTTON).setClass(context, TtsMediaButtonReceiver::class.java),
    PendingIntent.FLAG_UPDATE_CURRENT or
        (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
)

/**
 * 接收耳机线控 / 蓝牙（AVRCP）媒体按键。
 *
 * 活跃播放由 MediaSession 接收；系统恢复最近媒体会话时可向本接收器投递
 * ACTION_MEDIA_BUTTON。注册接收器不会抢占其它应用的活跃媒体会话。
 */
@AndroidEntryPoint
class TtsMediaButtonReceiver : BroadcastReceiver() {
    @Inject lateinit var ttsController: TtsController

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_MEDIA_BUTTON) return
        val event = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_KEY_EVENT, KeyEvent::class.java)
            ?: return
        val result = TtsMediaButtons.handleEvent(ttsController, event)
        logMediaButtonDelivery("broadcast_receiver", event, result, ttsController)
        Log.i(
            TAG,
            "media button keyCode=${event.keyCode} outcome=${result.outcome} " +
                "state=${ttsController.playbackState.value}"
        )
    }

    private companion object {
        const val TAG = "TtsMediaButton"
    }
}
