package com.huangder.lumibooks.tts

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

enum class TtsStartChoice(val storedValue: String) {
    CURRENT_PAGE("current_page"),
    SAVED_POSITION("saved_position");

    companion object {
        fun fromStoredValue(value: String?): TtsStartChoice? = entries.firstOrNull { it.storedValue == value }
    }
}

/** Resolves a start-button request before either the reader or the player moves. */
internal class TtsStartRequest(
    private val scope: CoroutineScope,
    private val settings: TtsSettingsStore,
    private val bookId: String
) {
    private val _prompt = MutableStateFlow<TtsListeningPosition?>(null)
    val prompt = _prompt.asStateFlow()
    private var pendingStart: ((Boolean) -> Unit)? = null
    private var job: Job? = null
    private var generation = 0L

    fun request(chapterCount: Int, onStart: (restoreSavedPosition: Boolean) -> Unit) {
        dismiss()
        val requestGeneration = generation
        job = scope.launch {
            val position = settings.ttsListeningPosition(bookId).first()
                ?: settings.externalTtsResumePosition(bookId).first()?.let {
                    TtsListeningPosition(it.bookId, it.chapterIndex, it.pageIndex,
                        it.characterOffset, System.currentTimeMillis())
                }
            val savedChoice = settings.ttsStartChoice.first()
            if (generation != requestGeneration) return@launch
            if (position == null || position.bookId != bookId ||
                position.chapterIndex !in 0 until chapterCount || position.pageIndex < 0 ||
                position.characterOffset < 0
            ) {
                onStart(false)
            } else if (savedChoice != null) {
                onStart(savedChoice == TtsStartChoice.SAVED_POSITION)
            } else {
                pendingStart = onStart
                _prompt.value = position
            }
        }
    }

    fun choose(choice: TtsStartChoice, remember: Boolean) {
        val start = pendingStart ?: return
        pendingStart = null
        _prompt.value = null
        val requestGeneration = generation
        job = scope.launch {
            if (remember) settings.saveTtsStartChoice(choice)
            if (generation == requestGeneration) start(choice == TtsStartChoice.SAVED_POSITION)
        }
    }

    fun dismiss() {
        generation++
        job?.cancel()
        job = null
        pendingStart = null
        _prompt.value = null
    }
}
