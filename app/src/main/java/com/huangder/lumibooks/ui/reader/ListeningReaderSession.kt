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
        checked = false
        keepReading()
    }

    fun requestJump(position: TtsListeningPosition) {
        _prompt.value = null
        _jumpRequest.value = position
    }

    fun completeJump(position: TtsListeningPosition): Boolean {
        if (!_jumpRequest.compareAndSet(position, null)) return false
        follow()
        return true
    }

    fun follow() {
        generation++
        checked = true
        _prompt.value = null
        _jumpRequest.value = null
        _followEnabled.value = true
    }

    private fun detach(preservePrompt: Boolean) {
        generation++
        if (!preservePrompt) _prompt.value = null
        _jumpRequest.value = null
        _followEnabled.value = false
    }
}
