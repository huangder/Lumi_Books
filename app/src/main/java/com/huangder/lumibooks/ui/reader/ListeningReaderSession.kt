package com.huangder.lumibooks.ui.reader

import com.huangder.lumibooks.tts.TtsListeningPosition
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal data class ManualListeningBrowse(
    val sessionId: Long,
    val generation: Long,
    val settled: Boolean = false,
    val canPrompt: Boolean = true,
    val showPrompt: Boolean = false
)

/** Opening hints retain their saved audio anchor while the user turns pages. */
internal class ListeningReaderSession {
    private val _prompt = MutableStateFlow<TtsListeningPosition?>(null)
    val prompt = _prompt.asStateFlow()
    private val _jumpRequest = MutableStateFlow<TtsListeningPosition?>(null)
    val jumpRequest = _jumpRequest.asStateFlow()
    private val _followEnabled = MutableStateFlow(false)
    val followEnabled = _followEnabled.asStateFlow()
    private val _manualBrowse = MutableStateFlow<ManualListeningBrowse?>(null)
    val manualBrowse = _manualBrowse.asStateFlow()
    private var manualBrowsePromptJob: Job? = null
    private var promptedBrowseGeneration: Long? = null
    private var checked = false
    private var checkingGeneration: Long? = null
    private var generation = 0L
    private var backgrounded = false
    private var backgroundSessionId: Long? = null
    private var followedBeforeBackground = false
    private var browsedBeforeBackground = false
    private var jumpSessionId: Long? = null

    /** Lifecycle can report PAUSE/STOP and composition changes for the same transition. */
    fun suspendForBackground(sessionId: Long?): Boolean {
        if (backgrounded) return false
        backgrounded = true
        backgroundSessionId = sessionId
        followedBeforeBackground = _followEnabled.value
        browsedBeforeBackground = sessionId != null && _manualBrowse.value?.sessionId == sessionId
        detach(preservePrompt = false)
        return true
    }

    /** null means a fresh open/session and keeps the existing saved-position policy. */
    fun returnFromBackground(sessionId: Long?, followUserPageTurns: Boolean = false): Boolean? {
        if (!backgrounded) return null
        backgrounded = false
        val previousSessionId = backgroundSessionId
        val wasFollowing = followedBeforeBackground
        val wasBrowsing = browsedBeforeBackground
        backgroundSessionId = null
        followedBeforeBackground = false
        browsedBeforeBackground = false
        // STARTED during the opening transition is not a completed opening check.
        // Interrupted loads/jumps must still apply the saved policy on first RESUME.
        if (!checked || sessionId == null || sessionId != previousSessionId) return null
        if (wasFollowing || followUserPageTurns) {
            follow()
        } else if (wasBrowsing) {
            // Keep the manual browse detached until the next viewport gesture settles.
            _manualBrowse.value = ManualListeningBrowse(sessionId, generation, canPrompt = false)
        }
        return true
    }

    fun isJumpSessionCurrent(sessionId: Long?): Boolean =
        jumpSessionId == null || jumpSessionId == sessionId

    fun beginCheck(): Long? {
        if (checked || checkingGeneration != null || _jumpRequest.value != null) return null
        checkingGeneration = generation
        return generation
    }

    fun isCurrent(stamp: Long): Boolean = stamp == generation

    fun cancelCheck(stamp: Long) {
        if (checkingGeneration == stamp) checkingGeneration = null
    }

    fun offer(position: TtsListeningPosition) {
        checked = true
        checkingGeneration = null
        _prompt.value = position
    }

    fun keepReading() {
        checked = true
        detach(preservePrompt = false)
    }

    fun pageTurn() {
        checked = true
        detach(preservePrompt = true)
    }

    fun beginManualBrowse(sessionId: Long?) {
        val canResume = _followEnabled.value || _manualBrowse.value?.sessionId == sessionId
        pageTurn()
        if (sessionId != null && canResume) {
            _manualBrowse.value = ManualListeningBrowse(sessionId, generation)
        }
    }

    fun settleManualBrowse(sessionId: Long?, canPrompt: Boolean) {
        val browse = _manualBrowse.value ?: return
        if (browse.sessionId != sessionId || browse.settled) return
        _manualBrowse.value = browse.copy(settled = true, canPrompt = canPrompt)
    }

    /** A drag can offer the action immediately, but cannot hand its viewport back to audio. */
    fun updateManualBrowseVisibility(
        visible: Boolean?, allowPrompt: Boolean, promptWhileBrowsing: Boolean, scope: CoroutineScope
    ): Boolean {
        val browse = _manualBrowse.value ?: return false
        if (!allowPrompt) dismissManualBrowsePrompt()
        if (visible == true) {
            if (!browse.settled) {
                if (browse.showPrompt) dismissManualBrowsePrompt()
                return false
            }
            follow()
            return true
        }
        if (visible == false && allowPrompt && (browse.settled || promptWhileBrowsing)) {
            showManualBrowsePrompt(scope)
        }
        return false
    }

    fun showManualBrowsePrompt(scope: CoroutineScope) {
        val browse = _manualBrowse.value ?: return
        if (!browse.canPrompt || promptedBrowseGeneration == browse.generation) return
        promptedBrowseGeneration = browse.generation
        _manualBrowse.value = browse.copy(showPrompt = true)
        manualBrowsePromptJob = scope.launch {
            delay(3_000L)
            if (_manualBrowse.value?.generation == browse.generation) dismissManualBrowsePrompt()
        }
    }

    fun dismissManualBrowsePrompt() {
        manualBrowsePromptJob?.cancel()
        manualBrowsePromptJob = null
        val browse = _manualBrowse.value ?: return
        promptedBrowseGeneration = browse.generation
        if (browse.showPrompt) _manualBrowse.value = browse.copy(showPrompt = false)
    }

    fun clearManualBrowse() {
        manualBrowsePromptJob?.cancel()
        manualBrowsePromptJob = null
        _manualBrowse.value = null
    }

    fun navigate() {
        checked = true
        keepReading()
    }

    fun hide() {
        backgrounded = false
        backgroundSessionId = null
        followedBeforeBackground = false
        browsedBeforeBackground = false
        keepReading()
        checked = false
    }

    fun requestJump(position: TtsListeningPosition, sessionId: Long? = null) {
        // Queuing navigation does not mean the renderer has accepted it yet.
        checked = false
        checkingGeneration = null
        jumpSessionId = sessionId
        clearManualBrowse()
        _prompt.value = null
        _jumpRequest.value = position
    }

    fun completeJump(position: TtsListeningPosition): Boolean {
        if (!_jumpRequest.compareAndSet(position, null)) return false
        follow()
        return true
    }

    fun follow() {
        clearManualBrowse()
        checkingGeneration = null
        jumpSessionId = null
        generation++
        checked = true
        _prompt.value = null
        _jumpRequest.value = null
        _followEnabled.value = true
    }

    private fun detach(preservePrompt: Boolean) {
        clearManualBrowse()
        checkingGeneration = null
        jumpSessionId = null
        generation++
        if (!preservePrompt) _prompt.value = null
        _jumpRequest.value = null
        _followEnabled.value = false
    }
}
