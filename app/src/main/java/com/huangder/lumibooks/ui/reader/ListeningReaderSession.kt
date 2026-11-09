package com.huangder.lumibooks.ui.reader

import com.huangder.lumibooks.tts.TtsListeningPosition
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Opening hints retain their saved audio anchor while the user turns pages. */
internal class ListeningReaderSession {
    private val _prompt = MutableStateFlow<TtsListeningPosition?>(null)
    val prompt = _prompt.asStateFlow()
    private val _jumpRequest = MutableStateFlow<TtsListeningPosition?>(null)
    val jumpRequest = _jumpRequest.asStateFlow()
    private val _followEnabled = MutableStateFlow(false)
    val followEnabled = _followEnabled.asStateFlow()
    private var checked = false
    private var generation = 0L
    private var backgrounded = false
    private var backgroundSessionId: Long? = null
    private var jumpSessionId: Long? = null

    /** Lifecycle can report PAUSE/STOP and composition changes for the same transition. */
    fun suspendForBackground(sessionId: Long?) {
        if (backgrounded) return
        backgrounded = true
        backgroundSessionId = sessionId
        checked = false
        detach(preservePrompt = false)
    }

    /** null means a fresh open/session and keeps the existing saved-position policy. */
    fun returnFromBackground(sessionId: Long?): Boolean? {
        if (!backgrounded) return null
        backgrounded = false
        val sameSession = sessionId != null && sessionId == backgroundSessionId
        backgroundSessionId = null
        checked = true
        if (!sameSession) return null
        return true
    }

    fun isJumpSessionCurrent(sessionId: Long?): Boolean =
        jumpSessionId == null || jumpSessionId == sessionId

    fun beginCheck(): Long? {
        if (checked) return null
        checked = true
        return generation
    }

    fun isCurrent(stamp: Long): Boolean = stamp == generation

    fun offer(position: TtsListeningPosition) { _prompt.value = position }

    fun keepReading() = detach(preservePrompt = false)

    fun pageTurn() {
        checked = true
        detach(preservePrompt = true)
    }

    fun navigate() {
        checked = true
        keepReading()
    }

    fun hide() {
        backgrounded = false
        backgroundSessionId = null
        checked = false
        keepReading()
    }

    fun requestJump(position: TtsListeningPosition, sessionId: Long? = null) {
        jumpSessionId = sessionId
        _prompt.value = null
        _jumpRequest.value = position
    }

    fun completeJump(position: TtsListeningPosition): Boolean {
        if (!_jumpRequest.compareAndSet(position, null)) return false
        follow()
        return true
    }

    fun follow() {
        jumpSessionId = null
        generation++
        checked = true
        _prompt.value = null
        _jumpRequest.value = null
        _followEnabled.value = true
    }

    private fun detach(preservePrompt: Boolean) {
        jumpSessionId = null
        generation++
        if (!preservePrompt) _prompt.value = null
        _jumpRequest.value = null
        _followEnabled.value = false
    }
}
