package com.huangder.lumibooks.service

import android.view.KeyEvent
import com.huangder.lumibooks.tts.TtsController
import com.huangder.lumibooks.tts.TtsPlaybackState

/**
 * 耳机线控 / 蓝牙（AVRCP）媒体按键到听书控制的映射。
 *
 * 同一按键可能通过两条路径送达：MediaSession 回调（本应用是系统当前的媒体按键会话时）
 * 或 MEDIA_BUTTON 广播（否则投递到应用声明的媒体按键接收器）。两条路径共用这里的逻辑，
 * 并对同一按键的重复投递做短时间去重，避免一次按键触发两次暂停/播放。
 */
internal object TtsMediaButtons {
    /** 同一次按键若被两条路径同时投递，在此时间窗内只处理一次。 */
    const val DEDUPE_WINDOW_MS = 250L

    enum class Command {
        TOGGLE,
        PLAY,
        PAUSE,
        STOP,
        NEXT,
        PREVIOUS
    }

    enum class Outcome {
        DISPATCHED,
        DUPLICATE,
        STATE_NOT_ALLOWED,
        UNSUPPORTED
    }

    data class Result(
        val command: Command?,
        val outcome: Outcome,
        val stateBefore: TtsPlaybackState,
        val consumed: Boolean
    )

    private var lastKeyCode = 0
    private var lastHandledAtMs = Long.MIN_VALUE

    fun commandFor(keyCode: Int): Command? = when (keyCode) {
        KeyEvent.KEYCODE_HEADSETHOOK,
        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> Command.TOGGLE
        KeyEvent.KEYCODE_MEDIA_PLAY -> Command.PLAY
        KeyEvent.KEYCODE_MEDIA_PAUSE -> Command.PAUSE
        KeyEvent.KEYCODE_MEDIA_STOP -> Command.STOP
        KeyEvent.KEYCODE_MEDIA_NEXT -> Command.NEXT
        KeyEvent.KEYCODE_MEDIA_PREVIOUS -> Command.PREVIOUS
        else -> null
    }

    fun isSupportedKey(keyCode: Int): Boolean = commandFor(keyCode) != null

    /** 只接受按下事件，忽略抬起与长按重复。 */
    fun isSupportedEvent(event: KeyEvent): Boolean =
        isSupportedKey(event.keyCode) &&
            event.action == KeyEvent.ACTION_DOWN &&
            event.repeatCount == 0

    /**
     * 处理一次媒体按键。
     * @return true 表示这是受支持的按键（已消费；听书未启动时无副作用）。
     */
    fun handleEvent(
        controller: TtsController,
        event: KeyEvent,
        nowMs: Long = android.os.SystemClock.uptimeMillis()
    ): Result {
        val state = controller.playbackState.value
        val command = commandFor(event.keyCode)
        if (command == null || event.action != KeyEvent.ACTION_DOWN || event.repeatCount != 0) {
            return Result(command, Outcome.UNSUPPORTED, state, consumed = false)
        }
        return handle(controller, event.keyCode, nowMs)
    }

    fun handle(
        controller: TtsController,
        keyCode: Int,
        nowMs: Long = android.os.SystemClock.uptimeMillis()
    ): Result {
        val state = controller.playbackState.value
        val command = commandFor(keyCode)
            ?: return Result(null, Outcome.UNSUPPORTED, state, consumed = false)
        val outcome = outcomeFor(command, state, duplicate = !shouldProcess(keyCode, nowMs))
        if (outcome != Outcome.DISPATCHED) {
            return Result(command, outcome, state, consumed = true)
        }
        when (command) {
            Command.TOGGLE -> toggle(controller)
            Command.PLAY -> controller.resume()
            Command.PAUSE -> controller.pause()
            Command.STOP -> controller.stop()
            Command.NEXT -> controller.skip(forward = true)
            Command.PREVIOUS -> controller.skip(forward = false)
        }
        return Result(command, Outcome.DISPATCHED, state, consumed = true)
    }

    internal fun outcomeFor(
        command: Command,
        state: TtsPlaybackState,
        duplicate: Boolean
    ): Outcome {
        if (duplicate) return Outcome.DUPLICATE
        val allowed = when (command) {
            Command.TOGGLE -> state == TtsPlaybackState.PLAYING || state == TtsPlaybackState.PAUSED
            Command.PLAY -> state == TtsPlaybackState.PAUSED
            Command.PAUSE -> state == TtsPlaybackState.PLAYING
            Command.STOP,
            Command.NEXT,
            Command.PREVIOUS -> state == TtsPlaybackState.PLAYING || state == TtsPlaybackState.PAUSED
        }
        return if (allowed) Outcome.DISPATCHED else Outcome.STATE_NOT_ALLOWED
    }

    /**
     * 记录本次按键并判断是否需要处理：同一次按键被 MediaSession 与广播重复投递时返回 false。
     */
    fun shouldProcess(
        keyCode: Int,
        nowMs: Long = android.os.SystemClock.uptimeMillis()
    ): Boolean {
        if (keyCode == lastKeyCode && nowMs - lastHandledAtMs < DEDUPE_WINDOW_MS) return false
        lastKeyCode = keyCode
        lastHandledAtMs = nowMs
        return true
    }

    fun toggle(controller: TtsController) {
        if (controller.playbackState.value == TtsPlaybackState.PLAYING) {
            controller.pause()
        } else {
            controller.resume()
        }
    }

    internal fun resetDedupe() {
        lastKeyCode = 0
        lastHandledAtMs = Long.MIN_VALUE
    }
}
