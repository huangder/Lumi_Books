package com.huangder.lumibooks.service

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.huangder.lumibooks.tts.TtsController
import com.huangder.lumibooks.tts.TtsPlaybackState

/** 音频焦点变化后要执行的动作，抽成纯函数以便单测。 */
internal enum class TtsAudioFocusAction {
    IGNORE,
    PAUSE_FOR_LOSS,
    RESUME_AFTER_LOSS
}

internal fun ttsAudioFocusActionForChange(
    change: Int,
    pausedForLoss: Boolean
): TtsAudioFocusAction = when (change) {
    AudioManager.AUDIOFOCUS_LOSS,
    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> TtsAudioFocusAction.PAUSE_FOR_LOSS
    AudioManager.AUDIOFOCUS_GAIN ->
        if (pausedForLoss) TtsAudioFocusAction.RESUME_AFTER_LOSS else TtsAudioFocusAction.IGNORE
    else -> TtsAudioFocusAction.IGNORE
}

/**
 * 系统 TTS 引擎播放时自身不持有音频焦点，部分机型因此把耳机媒体按键路由给“上次播放音频的应用”。
 * 听书（系统引擎）播放期间由本控制器持有焦点：既让系统把本应用视为当前媒体应用，
 * 也让其它播放器让出声音；被电话/导航等抢占时自动暂停，短暂中断结束后自动继续。
 *
 * AI/第三方 TTS 引擎由它自己的播放器管理焦点，这里不重复请求。
 */
internal class TtsAudioFocusController(
    context: Context,
    private val ttsController: TtsController
) {
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()

    private var focusRequest: AudioFocusRequest? = null
    private var holding = false
    private var pausedForLoss = false

    private val listener = AudioManager.OnAudioFocusChangeListener { change ->
        when (ttsAudioFocusActionForChange(change, pausedForLoss)) {
            TtsAudioFocusAction.PAUSE_FOR_LOSS -> {
                if (ttsController.playbackState.value == TtsPlaybackState.PLAYING) {
                    pausedForLoss = true
                    ttsController.pause()
                }
            }
            TtsAudioFocusAction.RESUME_AFTER_LOSS -> {
                if (pausedForLoss) {
                    pausedForLoss = false
                    ttsController.resume()
                }
            }
            TtsAudioFocusAction.IGNORE -> Unit
        }
    }

    /** 按当前会话状态与引擎类型同步焦点持有状态。 */
    fun update(playbackState: TtsPlaybackState, usesSystemEngine: Boolean) {
        if (playbackState == TtsPlaybackState.PLAYING) pausedForLoss = false
        val shouldHold = usesSystemEngine &&
            (playbackState == TtsPlaybackState.PLAYING ||
                playbackState == TtsPlaybackState.INITIALIZING)
        if (shouldHold) acquire() else release()
    }

    fun release() {
        if (!holding && focusRequest == null) return
        focusRequest?.let { request -> runCatching { audioManager.abandonAudioFocusRequest(request) } }
        focusRequest = null
        holding = false
        pausedForLoss = false
    }

    private fun acquire() {
        if (holding) return
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(attributes)
            .setWillPauseWhenDucked(true)
            .setOnAudioFocusChangeListener(listener, mainHandler)
            .build()
        val result = runCatching { audioManager.requestAudioFocus(request) }
            .getOrElse { error ->
                Log.w(TAG, "Unable to request audio focus for TTS playback", error)
                AudioManager.AUDIOFOCUS_REQUEST_FAILED
            }
        focusRequest = request
        holding = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        if (!holding) {
            Log.i(TAG, "Audio focus not granted for TTS playback (result=$result)")
        }
    }

    private companion object {
        const val TAG = "TtsAudioFocus"
    }
}
