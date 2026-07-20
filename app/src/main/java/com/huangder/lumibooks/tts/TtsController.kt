package com.huangder.lumibooks.tts

import android.speech.tts.UtteranceProgressListener
import com.huangder.lumibooks.data.local.DataStoreManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TtsController(
    private val ttsEngine: TtsEngine,
    private val textExtractor: TtsTextExtractor,
    private val dataStoreManager: DataStoreManager
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _playbackState = MutableStateFlow(TtsPlaybackState.IDLE)
    val playbackState: StateFlow<TtsPlaybackState> = _playbackState.asStateFlow()

    private val _speechRate = MutableStateFlow(1f)
    val speechRate: StateFlow<Float> = _speechRate.asStateFlow()

    private val _activeBookId = MutableStateFlow<String?>(null)
    val activeBookId: StateFlow<String?> = _activeBookId.asStateFlow()

    private val _currentPage = MutableStateFlow<TtsPageContent?>(null)
    val currentPage: StateFlow<TtsPageContent?> = _currentPage.asStateFlow()

    private val _pageTurnRequests = MutableSharedFlow<TtsPageTurnRequest>(replay = 1, extraBufferCapacity = 1)
    val pageTurnRequests: SharedFlow<TtsPageTurnRequest> = _pageTurnRequests.asSharedFlow()

    private val _errors = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val errors: SharedFlow<Unit> = _errors.asSharedFlow()

    private var pageProvider: (suspend (chapterIndex: Int, pageIndex: Int) -> TtsPageContent?)? = null
    private var sentences: List<String> = emptyList()
    private var sentenceIndex = 0
    private var activeUtteranceId: String? = null
    private var sessionGeneration = 0L
    private var utteranceSequence = 0L
    private var pageLoadToken = 0L

    init {
        ttsEngine.setOnUtteranceListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit

            override fun onDone(utteranceId: String?) {
                scope.launch { handleUtteranceDone(utteranceId) }
            }

            @Deprecated("Deprecated by Android")
            override fun onError(utteranceId: String?) {
                scope.launch { handleUtteranceError(utteranceId) }
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                scope.launch { handleUtteranceError(utteranceId) }
            }
        })

        scope.launch {
            dataStoreManager.ttsSpeechRate.collectLatest { rate ->
                _speechRate.value = rate.coerceIn(0.5f, 2f)
                ttsEngine.setSpeechRate(_speechRate.value)
            }
        }
        scope.launch {
            dataStoreManager.ttsPitch.collectLatest { pitch ->
                ttsEngine.setPitch(pitch.coerceIn(0.5f, 2f))
            }
        }
    }

    suspend fun start(
        bookId: String,
        provider: suspend (chapterIndex: Int, pageIndex: Int) -> TtsPageContent?,
        startChapter: Int,
        startPage: Int
    ): Result<Unit> = withContext(Dispatchers.Main.immediate) {
        stopInternal()
        val generation = ++sessionGeneration
        pageProvider = provider
        _activeBookId.value = bookId
        _playbackState.value = TtsPlaybackState.INITIALIZING

        ttsEngine.setSpeechRate(_speechRate.value)
        val initializeResult = ttsEngine.initialize()
        if (generation != sessionGeneration) {
            return@withContext Result.success(Unit)
        }
        if (initializeResult.isFailure) {
            stopInternal()
            return@withContext initializeResult
        }

        _playbackState.value = TtsPlaybackState.PLAYING
        val moved = moveToPage(
            location = TtsPageLocation(startChapter, startPage),
            startAtEnd = false,
            publishLocation = true
        )
        if (generation != sessionGeneration) {
            return@withContext Result.success(Unit)
        }
        if (!moved) {
            stopInternal()
            return@withContext Result.failure(IllegalStateException("No readable text found"))
        }
        Result.success(Unit)
    }

    fun pause() {
        scope.launch {
            if (_playbackState.value != TtsPlaybackState.PLAYING) return@launch
            activeUtteranceId = null
            ttsEngine.stop()
            _playbackState.value = TtsPlaybackState.PAUSED
        }
    }

    fun resume() {
        scope.launch {
            if (_playbackState.value != TtsPlaybackState.PAUSED || sentences.isEmpty()) return@launch
            _playbackState.value = TtsPlaybackState.PLAYING
            speakCurrentSentence()
        }
    }

    fun stop() {
        scope.launch { stopInternal() }
    }

    fun skip(forward: Boolean = true) {
        scope.launch {
            val state = _playbackState.value
            if (state != TtsPlaybackState.PLAYING && state != TtsPlaybackState.PAUSED) return@launch
            activeUtteranceId = null
            ttsEngine.stop()

            if (forward) {
                if (sentenceIndex + 1 < sentences.size) {
                    sentenceIndex++
                    if (state == TtsPlaybackState.PLAYING) speakCurrentSentence()
                } else {
                    moveToAdjacentPage(forward = true)
                }
            } else {
                if (sentenceIndex > 0) {
                    sentenceIndex--
                    if (state == TtsPlaybackState.PLAYING) speakCurrentSentence()
                } else {
                    moveToAdjacentPage(forward = false)
                }
            }
        }
    }

    fun onPageVisible(bookId: String, chapterIndex: Int, pageIndex: Int) {
        scope.launch {
            if (_activeBookId.value != bookId) return@launch
            val state = _playbackState.value
            if (state != TtsPlaybackState.PLAYING && state != TtsPlaybackState.PAUSED) return@launch
            val location = TtsPageLocation(chapterIndex, pageIndex)
            if (_currentPage.value?.location == location) return@launch
            if (!moveToPage(location, startAtEnd = false, publishLocation = true)) {
                _errors.tryEmit(Unit)
                stopInternal()
            }
        }
    }

    suspend fun setSpeechRate(rate: Float) = withContext(Dispatchers.Main.immediate) {
        val safeRate = rate.coerceIn(0.5f, 2f)
        _speechRate.value = safeRate
        dataStoreManager.saveTtsSpeechRate(safeRate)
        ttsEngine.setSpeechRate(safeRate)
        if (_playbackState.value == TtsPlaybackState.PLAYING && sentences.isNotEmpty()) {
            activeUtteranceId = null
            ttsEngine.stop()
            speakCurrentSentence()
        }
    }

    suspend fun setPitch(pitch: Float) = withContext(Dispatchers.Main.immediate) {
        val safePitch = pitch.coerceIn(0.5f, 2f)
        dataStoreManager.saveTtsPitch(safePitch)
        ttsEngine.setPitch(safePitch)
    }

    private suspend fun handleUtteranceDone(utteranceId: String?) {
        if (utteranceId == null || utteranceId != activeUtteranceId) return
        if (_playbackState.value != TtsPlaybackState.PLAYING) return
        activeUtteranceId = null
        if (sentenceIndex + 1 < sentences.size) {
            sentenceIndex++
            speakCurrentSentence()
        } else {
            moveToAdjacentPage(forward = true)
        }
    }

    private suspend fun handleUtteranceError(utteranceId: String?) {
        if (utteranceId == null || utteranceId != activeUtteranceId) return
        _errors.tryEmit(Unit)
        stopInternal()
    }

    private suspend fun moveToAdjacentPage(forward: Boolean) {
        val page = _currentPage.value ?: run {
            stopInternal()
            return
        }
        val target = if (forward) page.next else page.previous
        if (target == null || !moveToPage(target, startAtEnd = !forward, publishLocation = true)) {
            stopInternal()
        }
    }

    private suspend fun moveToPage(
        location: TtsPageLocation,
        startAtEnd: Boolean,
        publishLocation: Boolean
    ): Boolean {
        val provider = pageProvider ?: return false
        val token = ++pageLoadToken
        activeUtteranceId = null
        ttsEngine.stop()

        var target = location
        var selectedPage: TtsPageContent? = null
        var selectedSentences: List<String> = emptyList()
        var attempts = 0
        while (attempts < 50 && selectedPage == null) {
            val page = provider(target.chapterIndex, target.pageIndex) ?: return token != pageLoadToken
            if (token != pageLoadToken) return true
            val pageSentences = textExtractor.splitIntoSentences(page.text)
            if (pageSentences.isNotEmpty()) {
                selectedPage = page
                selectedSentences = pageSentences
            } else {
                target = if (startAtEnd) page.previous ?: return false else page.next ?: return false
            }
            attempts++
        }

        val page = selectedPage ?: return false
        if (token != pageLoadToken) return true
        _currentPage.value = page
        sentences = selectedSentences
        sentenceIndex = if (startAtEnd) sentences.lastIndex else 0
        if (publishLocation) {
            _pageTurnRequests.emit(TtsPageTurnRequest(_activeBookId.value ?: return false, page.location))
        }
        if (_playbackState.value == TtsPlaybackState.PLAYING) {
            speakCurrentSentence()
        }
        return true
    }

    private suspend fun speakCurrentSentence() {
        val page = _currentPage.value ?: return
        val sentence = sentences.getOrNull(sentenceIndex) ?: return
        val utteranceId = buildString {
            append(sessionGeneration)
            append(':')
            append(++utteranceSequence)
            append(":ch")
            append(page.location.chapterIndex)
            append("_pg")
            append(page.location.pageIndex)
            append("_s")
            append(sentenceIndex)
        }
        activeUtteranceId = utteranceId
        val result = ttsEngine.speak(sentence, utteranceId)
        if (result.isFailure && activeUtteranceId == utteranceId) {
            _errors.tryEmit(Unit)
            stopInternal()
        }
    }

    private suspend fun stopInternal() {
        sessionGeneration++
        pageLoadToken++
        activeUtteranceId = null
        ttsEngine.stop()
        pageProvider = null
        sentences = emptyList()
        sentenceIndex = 0
        _currentPage.value = null
        _activeBookId.value = null
        _playbackState.value = TtsPlaybackState.IDLE
    }

    fun shutdown() {
        scope.cancel()
        ttsEngine.shutdown()
    }
}
