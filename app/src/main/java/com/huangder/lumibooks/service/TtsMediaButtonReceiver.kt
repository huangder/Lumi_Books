package com.huangder.lumibooks.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import android.view.KeyEvent
import androidx.core.content.IntentCompat
import com.huangder.lumibooks.tts.TtsController
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * 接收耳机线控 / 蓝牙（AVRCP）媒体按键。
 *
 * Android 12+ 由 [android.media.session.MediaSession.setMediaButtonBroadcastReceiver] 指向本接收器：
 * 当本应用的听书会话不是系统当前的“媒体按键会话”时（例如系统仍把按键留给其它播放器），
 * 系统会把 ACTION_MEDIA_BUTTON 投递到这里，耳机上仍然可以暂停/继续听书。
 */
@AndroidEntryPoint
class TtsMediaButtonReceiver : BroadcastReceiver() {
    @Inject lateinit var ttsController: TtsController

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_MEDIA_BUTTON) return
        val event = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_KEY_EVENT, KeyEvent::class.java)
            ?: return
        if (!TtsMediaButtons.isSupportedEvent(event)) return
        val handled = TtsMediaButtons.handle(ttsController, event.keyCode)
        Log.i(
            TAG,
            "media button keyCode=${event.keyCode} handled=$handled " +
                "state=${ttsController.playbackState.value}"
        )
    }

    private companion object {
        const val TAG = "TtsMediaButton"
    }
}
