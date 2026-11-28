package com.huangder.lumibooks.tts

import com.huangder.lumibooks.BuildConfig
import com.huangder.lumibooks.domain.model.BookPositionSource
import com.huangder.lumibooks.domain.model.BookPositionUse
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import com.huangder.lumibooks.util.diagnostics.DiagnosticLevel
import com.huangder.lumibooks.util.diagnostics.DiagnosticLoggerRegistry

data class TtsSubtitleFrame(
    val current: TtsTextSegment? = null,
    val next: TtsTextSegment? = null,
    val bookId: String? = null,
    val chapterIndex: Int? = null
)

class TtsController(
    private val systemTtsEngine: TtsPlaybackEngine,
    private val externalTtsEngine: TtsPlaybackEngine,
    private val textExtractor: TtsTextExtractor,
    private val dataStoreManager: TtsSettingsStore,
    private val onListeningActivity: (String?) -> Unit = {}
) {
    val currentSessionId: Long get() = sessionGeneration
    private val _providerSelection = MutableStateFlow<TtsProviderSelection>(TtsProviderSelection.SystemDefault)
    val providerSelection = _providerSelection.asStateFlow()
    private val _engineSwitchFailures = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val engineSwitchFailures = _engineSwitchFailures.asSharedFlow()
    private data class EngineRollback(val selection: TtsProviderSelection, val engine: TtsPlaybackEngine, val utterance: String?)
    private var engineRollback: EngineRollback? = null
    private val _chapterSnapshot = MutableStateFlow<TtsChapterSnapshot?>(null)
    val chapterSnapshot = _chapterSnapshot.asStateFlow()
    private val _listeningFrame = MutableStateFlow(TtsListeningFrame())
    val listeningFrame = _listeningFrame.asStateFlow()
    private var fullscreenSession: Long? = null
    private val _fullscreenVisible = MutableStateFlow(false)
    val fullscreenVisible = _fullscreenVisible.asStateFlow()
    private var fullscreenReaderLocation: TtsPageLocation? = null
    private val visualReaderAttached: Boolean get() = readerAttached && fullscreenSession != sessionGeneration

    private var sessionEngine: TtsPlaybackEngine? = null
    private val activeEngine: TtsPlaybackEngine
        get() = sessionEngine ?: systemTtsEngine
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val commandMutex = Mutex()

    private val _playbackState = MutableStateFlow(TtsPlaybackState.IDLE)
    val playbackState: StateFlow<TtsPlaybackState> = _playbackState.asStateFlow()

    private val _speechRate = MutableStateFlow(1f)
    val speechRate: StateFlow<Float> = _speechRate.asStateFlow()

    private val _speechRateMode = MutableStateFlow(TtsProsodyMode.FOLLOW_ENGINE)
    val speechRateMode: StateFlow<TtsProsodyMode> = _speechRateMode.asStateFlow()

    private val _pauseDuration = MutableStateFlow(TtsPauseDuration.STANDARD)
    val pauseDuration: StateFlow<TtsPauseDuration> = _pauseDuration.asStateFlow()

    fun setPauseDuration(duration: TtsPauseDuration) {
        scope.launch {
            commandMutex.withLock {
                dataStoreManager.saveTtsPauseDuration(duration)
                _pauseDuration.value = duration
            }
        }
    }

    private val _pitch = MutableStateFlow(1f)
    val pitch: StateFlow<Float> = _pitch.asStateFlow()

    private val _pitchMode = MutableStateFlow(TtsProsodyMode.FOLLOW_ENGINE)
    val pitchMode: StateFlow<TtsProsodyMode> = _pitchMode.asStateFlow()

    private val _usesAndroidTts = MutableStateFlow(true)
    val usesAndroidTts: StateFlow<Boolean> = _usesAndroidTts.asStateFlow()

    private val _activeBookId = MutableStateFlow<String?>(null)
    val activeBookId: StateFlow<String?> = _activeBookId.asStateFlow()

    private val _currentPage = MutableStateFlow<TtsPageContent?>(null)
    val currentPage: StateFlow<TtsPageContent?> = _currentPage.asStateFlow()

    private val _currentSentence = MutableStateFlow<TtsTextSegment?>(null)
    val currentSentence: StateFlow<TtsTextSegment?> = _currentSentence.asStateFlow()

    // Keep the display sentence separate from currentSentence: Android TTS range callbacks
    // refine currentSentence to a word or phrase for reader highlighting.
    private val _currentUtterance = MutableStateFlow<TtsTextSegment?>(null)
    val currentUtterance: StateFlow<TtsTextSegment?> = _currentUtterance.asStateFlow()

    /**
     * The sentence expected to play after [currentUtterance], for the floating lyrics preview. It
     * changes together with [currentUtterance] (or once the successor is known), never ahead of it.
     */
    private val _upcomingUtterance = MutableStateFlow<TtsTextSegment?>(null)
    val upcomingUtterance: StateFlow<TtsTextSegment?> = _upcomingUtterance.asStateFlow()
    /** Successor prepared by the prefetch job, possibly from the following page. */
    private var prefetchedSuccessor: TtsTextSegment? = null
    private var rangeLoggedUtteranceId: String? = null
    private var audibleUtteranceId: String? = null
    private var estimatedSubtitleOffset: Int? = null
    private var nativeSubtitleJob: Job? = null
    private val _subtitleFrame = MutableStateFlow(TtsSubtitleFrame())
    val subtitleFrame: StateFlow<TtsSubtitleFrame> = _subtitleFrame.asStateFlow()

    private fun publishSubtitle() {
        if (_playbackState.value != TtsPlaybackState.PLAYING ||
            audibleUtteranceId != activeUtteranceId || activeUtteranceId == null) return
        val sentence = activeSentenceSegment ?: return
        val display = textExtractor.splitIntoClauses(sentence)
        val offset = estimatedSubtitleOffset ?: _currentSentence.value?.startCharacterOffset
            ?: sentence.startCharacterOffset
        val index = textExtractor.segmentIndexForOffset(display, offset).coerceAtLeast(0)
        _subtitleFrame.value = TtsSubtitleFrame(
            current = display.getOrNull(index),
            next = display.getOrNull(index + 1) ?: _upcomingUtterance.value?.let {
                textExtractor.splitIntoClauses(it).firstOrNull()
            },
            bookId = _activeBookId.value,
            chapterIndex = _currentPage.value?.location?.chapterIndex
        )
    }


    // Native synthesis engines may omit range callbacks. Estimate only the floating subtitle
    // until the engine provides timing. Playback and saved positions still use engine callbacks.
    private fun estimateNativeSubtitleRanges(utteranceId: String, sentence: TtsTextSegment) {
        nativeSubtitleJob?.cancel()
        val display = textExtractor.splitIntoClauses(sentence)
        if (display.size < 2) return
        val rate = _speechRate.value
        nativeSubtitleJob = scope.launch {
            for ((previous, next) in display.zipWithNext()) {
                delay(estimatedSpeechMillis(previous.text, rate))
                commandMutex.withLock {
                    if (activeUtteranceId != utteranceId || completedUtteranceId == utteranceId ||
                        _playbackState.value != TtsPlaybackState.PLAYING || rangeLoggedUtteranceId == utteranceId) return@launch
                    estimatedSubtitleOffset = next.startCharacterOffset
                    publishSubtitle()
                }
            }
        }
    }

    private fun estimatedSpeechMillis(text: String, rate: Float): Long =
        (text.sumOf { char -> if (char.code >= 0x2E80) 180L else 55L } / rate).toLong().coerceAtLeast(1)

    private fun publishUpcomingUtterance() {
        _upcomingUtterance.value = clauses.getOrNull(clauseIndex + 1)
            ?: prefetchedSuccessor ?: segments.getOrNull(sentenceIndex + 1)
        publishSubtitle()
    }

    // Display whole clauses while retaining the engine's precise range for resume/progress.
    // Audio remains a complete sentence, so punctuation does not introduce extra synthesis gaps.
    val currentClause: StateFlow<TtsTextSegment?> = combine(currentUtterance, currentSentence) { utterance, range ->
        utterance?.let {
            val displayClauses = textExtractor.splitIntoClauses(it)
            val offset = range?.startCharacterOffset ?: it.startCharacterOffset
            displayClauses.getOrNull(textExtractor.segmentIndexForOffset(displayClauses, offset))
        }
    }.stateIn(scope, SharingStarted.Eagerly, null)

    private val _pageTurnRequests = MutableSharedFlow<TtsPageTurnRequest>(replay = 1, extraBufferCapacity = 1)
    val pageTurnRequests: SharedFlow<TtsPageTurnRequest> = _pageTurnRequests.asSharedFlow()
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private fun clearPageTurnReplay() = _pageTurnRequests.resetReplayCache()

    private val _errors = MutableSharedFlow<Throwable?>(extraBufferCapacity = 1)
    val errors: SharedFlow<Throwable?> = _errors.asSharedFlow()

    private var pageSource: TtsPageSource? = null
    private var segments: List<TtsTextSegment> = emptyList()
    private var sentenceIndex = 0
    private var clauses: List<TtsTextSegment> = emptyList()
    private var clauseIndex = 0
    private var playbackSentence: TtsTextSegment? = null
    private var sentencePauseDuration = TtsPauseDuration.STANDARD
    private var completedUtteranceId: String? = null
    private var pauseBetweenUtterances: Job? = null
    private var activeUtteranceId: String? = null
    /** Number of recovery attempts for the currently spoken sentence. */
    private var utteranceRetryCount = 0
    private var nextSentencePrefetchJob: Job? = null
    private var prefetchedPage: TtsPageContent? = null
    private data class QueuedSentence(
        val generation: Long, val engine: TtsPlaybackEngine, val page: TtsPageContent,
        val segment: TtsTextSegment, val text: String, val id: String
    )
    /** Sentences already submitted to a queued engine after the audible one, in order. */
    private val queuedSentences = ArrayList<QueuedSentence>()

    private fun clearQueuedSentence() {
        queuedSentences.clear()
        nextSentencePrefetchJob?.cancel()
    }

    /** A live anchor for viewport restoration, independent of throttled persistence. */
    fun liveListeningPosition(bookId: String, sessionId: Long): TtsListeningPosition? {
        if (_activeBookId.value != bookId || sessionId != sessionGeneration ||
            _playbackState.value == TtsPlaybackState.IDLE) return null
        val audible = _listeningFrame.value.takeIf { activeEngine.supportsQueuedPlayback }
        val page = audible?.page ?: _currentPage.value ?: return null
        val sentence = audible?.utterance ?: activeSentenceSegment ?: return null
        val offset = if (crossPageMerge != null && sentence === activeSentenceSegment) sentence.startCharacterOffset
            else _currentSentence.value?.startCharacterOffset ?: sentence.startCharacterOffset
        return TtsListeningPosition(bookId, page.location.chapterIndex, page.location.pageIndex,
            offset.coerceAtLeast(0), System.currentTimeMillis())
    }
    private var sessionGeneration = 0L
    private var utteranceSequence = 0L
    private var pageLoadToken = 0L
    private var pageContentError: Throwable? = null
    private var pendingResume: ExternalTtsResumePosition? = null
    private var crossPageMerge: CrossPageMergeState? = null
    private var activeSegment: TtsTextSegment? = null
    private var activeSentenceSegment: TtsTextSegment? = null
    private var pageTurnSequence = 0L
    private var pendingPageTurn: PendingPageTurn? = null
    private var pendingPageTurnTimeoutJob: Job? = null
    private var acknowledgedPageTurn: AcknowledgedPageTurn? = null
    /**
     * When false the reader keeps its own position and playback advances on its own. Cleared by a
     * user page change, restored by an explicit return to listening or a sentence seek.
     */
    private var followReaderPageTurns = true
    private val _autoPageTurn = MutableStateFlow(true)
    val autoPageTurn: StateFlow<Boolean> = _autoPageTurn.asStateFlow()
    private val canTurnReaderPage: Boolean
        get() = _autoPageTurn.value && visualReaderAttached && followReaderPageTurns
    private var readerAttached = true
    private var lastListeningWriteNanos = 0L

    // Sleep timer
    private val _sleepTimerRemainingMs = MutableStateFlow<Long?>(null)
    val sleepTimerRemainingMs: StateFlow<Long?> = _sleepTimerRemainingMs.asStateFlow()
    private val _sleepTimerDurationMs = MutableStateFlow<Long?>(null)
    val sleepTimerDurationMs: StateFlow<Long?> = _sleepTimerDurationMs.asStateFlow()
    private var sleepTimerJob: Job? = null

    private data class CrossPageMergeState(
        val landingLocation: TtsPageLocation,
        val landingStartCharacterOffset: Int
    )

    private data class PendingPageTurn(
        val request: TtsPageTurnRequest,
        val sourceLocation: TtsPageLocation?,
        val resumeWhenAcknowledged: Boolean,
        val retryCount: Int = 0
    )

    private data class AcknowledgedPageTurn(
        val sourceLocation: TtsPageLocation?,
        val targetLocation: TtsPageLocation,
        val acknowledgedAtNanos: Long
    )

    private fun playbackListener(
        engine: TtsPlaybackEngine,
        callbackSource: String
    ) = object : TtsPlaybackListener {
        override fun onPlaybackActivity(utteranceId: String, active: Boolean) {
            // Do not wait behind synthesis/network work holding commandMutex. All controller
            // state and the accounting sink are confined to Main, and stale audio is ignored.
            scope.launch {
                if (engine !== activeEngine || utteranceId != activeUtteranceId) return@launch
                if (active && _playbackState.value != TtsPlaybackState.PLAYING) return@launch
                onListeningActivity(if (active) _activeBookId.value else null)
            }
        }

        override fun onStart(utteranceId: String) {
            scope.launch {
                commandMutex.withLock {
                    if (engine !== activeEngine || utteranceId != activeUtteranceId) return@withLock
                    logTtsEvent(
                        event = "utterance_started",
                        utteranceId = utteranceId,
                        callbackSource = callbackSource,
                        sentenceOffset = activeSentenceSegment?.startCharacterOffset,
                        textLength = activeSentenceSegment?.text?.length
                    )
                    if (_playbackState.value != TtsPlaybackState.PLAYING) return@withLock
                    audibleUtteranceId = utteranceId
                    estimatedSubtitleOffset = null
                    run {
                        activeSentenceSegment?.let { sentence ->
                            _currentUtterance.value = sentence
                            _currentSentence.value = sentence
                            if (activeEngine.supportsQueuedPlayback) estimateNativeSubtitleRanges(utteranceId, sentence)
                            publishUpcomingUtterance()
                            _listeningFrame.value = TtsListeningFrame(_chapterSnapshot.value, _currentPage.value, sentence)
                        }
                    }
                    publishSubtitle()
                    persistListeningPosition(force = true)
                }
            }
        }

        override fun onDone(utteranceId: String) {
            scope.launch {
                commandMutex.withLock {
                    logTtsEvent(
                        event = "utterance_done",
                        utteranceId = utteranceId,
                        callbackSource = callbackSource
                    )
                    if (engine !== activeEngine) return@withLock
                    if (utteranceId == activeUtteranceId) onListeningActivity(null)
                    handleUtteranceDone(utteranceId)
                }
            }
        }

        override fun onError(utteranceId: String, throwable: Throwable) {
            scope.launch {
                commandMutex.withLock {
                    logTtsEvent(
                        event = "utterance_error",
                        utteranceId = utteranceId,
                        callbackSource = callbackSource
                    )
                    if (engine !== activeEngine) return@withLock
                    if (utteranceId == activeUtteranceId) onListeningActivity(null)
                    handleUtteranceError(utteranceId, throwable)
                }
            }
        }

        override fun onProgress(utteranceId: String, cacheKey: String, pcmFrameOffset: Long) {
            scope.launch {
                commandMutex.withLock {
                    if (engine !== activeEngine) return@withLock
                    updateExternalClauseProgress(utteranceId, pcmFrameOffset)
                    if (utteranceId == activeUtteranceId) persistListeningPosition()
                    persistExternalProgress(utteranceId, cacheKey, pcmFrameOffset)
                }
            }
        }

        override fun onPlaybackInterrupted() {
            scope.launch {
                commandMutex.withLock {
                    if (engine !== activeEngine) return@withLock
                    logTtsEvent(
                        event = "playback_interrupted",
                        callbackSource = callbackSource
                    )
                    if (_playbackState.value == TtsPlaybackState.PLAYING) {
                        clearQueuedSentence()
                        onListeningActivity(null)
                        _playbackState.value = TtsPlaybackState.PAUSED
                        logTtsEvent("state_changed", state = TtsPlaybackState.PAUSED)
                    }
                }
            }
        }

        override fun onRangeStart(utteranceId: String, start: Int, end: Int) {
            scope.launch {
                commandMutex.withLock {
                    if (engine === activeEngine) {
                        updateAndroidUtteranceRange(utteranceId, start, end)
                        if (utteranceId == activeUtteranceId) persistListeningPosition()
                    }
                }
            }
        }
    }

    init {
        systemTtsEngine.setListener(playbackListener(systemTtsEngine, "android"))
        externalTtsEngine.setListener(playbackListener(externalTtsEngine, "external"))

        scope.launch {
            dataStoreManager.ttsPauseDuration.collect { duration ->
                commandMutex.withLock { _pauseDuration.value = duration }
            }
        }
        scope.launch {
            dataStoreManager.ttsProsodySettings.collect { storedSettings ->
                val settings = storedSettings.normalized()
                commandMutex.withLock {
                _speechRate.value = settings.speechRate
                _speechRateMode.value = settings.speechRateMode
                _pitch.value = settings.pitch.coerceIn(0.5f, 2f)
                _pitchMode.value = settings.pitchMode
                runCatching { applyProsody(systemTtsEngine) }.exceptionOrNull()?.let { error ->
                    if (error is SystemTtsException.ProsodyRejected) {
                        fallBackRejectedProsody(error)
                    } else {
                        _errors.tryEmit(error)
                    }
                }
                if (sessionEngine?.isExternal == true) {
                    runCatching { applyProsody(externalTtsEngine) }
                        .exceptionOrNull()
                        ?.let(_errors::tryEmit)
                }
                }
            }
        }
    }

    suspend fun start(
        bookId: String,
        source: TtsPageSource,
        startChapter: Int,
        startPage: Int,
        startCharacterOffset: Int? = null,
        restoreSavedPosition: Boolean = true
    ): Result<Unit> = withContext(Dispatchers.Main.immediate) {
        val positionUse = BookPositionUse.create(BookPositionSource.LISTENING)
        commandMutex.withLock {
        // Replacing a live session must not publish IDLE: the foreground service treats IDLE as
        // final and may destroy itself before this new session reaches INITIALIZING.
        stopInternal(publishIdle = false)
        val generation = ++sessionGeneration
        val selection = dataStoreManager.ttsProviderSelection.first()
        val selectedEngine = selectPlaybackEngine(selection)
        if (selectedEngine.isFailure) {
            source.close()
            stopInternal()
            return@withContext Result.failure(checkNotNull(selectedEngine.exceptionOrNull()))
        }
        sessionEngine = selectedEngine.getOrThrow()
        _providerSelection.value = selection
        _usesAndroidTts.value = !activeEngine.isExternal
        pageSource = source
        _activeBookId.value = bookId
        com.huangder.lumibooks.data.sync.WebdavOpenBooks.listeningBookId = bookId
        _playbackState.value = TtsPlaybackState.INITIALIZING
        logTtsEvent("session_start", state = TtsPlaybackState.INITIALIZING)
        if (BuildConfig.DIAGNOSTIC_BUILD) {
            DiagnosticLoggerRegistry.logger?.log(
                category = "tts",
                event = "playback_engine_selected",
                attributes = mapOf(
                    "providerSelection" to selection.javaClass.simpleName,
                    "externalEngine" to activeEngine.isExternal,
                    "startChapter" to startChapter,
                    "startPage" to startPage,
                    "hasCharacterOffset" to (startCharacterOffset != null)
                ),
                bookId = bookId
            )
        }

        val engine = activeEngine
        var initializeResult: Result<Unit> = Result.failure(SystemTtsException.Initialization())
        var prosodyAttempts = 0
        while (prosodyAttempts < 3) {
            prosodyAttempts++
            val applyFailure = runCatching { applyProsody(engine) }.exceptionOrNull()
            if (applyFailure != null) {
                if (applyFailure is SystemTtsException.ProsodyRejected) {
                    fallBackRejectedProsody(applyFailure)
                    continue
                }
                initializeResult = Result.failure(applyFailure)
                break
            }
            initializeResult = engine.initialize()
            val initializeFailure = initializeResult.exceptionOrNull()
            if (initializeFailure is SystemTtsException.ProsodyRejected) {
                fallBackRejectedProsody(initializeFailure)
            } else {
                break
            }
        }
        if (generation != sessionGeneration) return@withContext Result.success(Unit)
        if (initializeResult.isFailure) {
            stopInternal()
            return@withContext initializeResult
        }

        val listeningPosition = if (restoreSavedPosition) {
            dataStoreManager.ttsListeningPosition(bookId).first()
        } else null
        val storedResume = if (engine.isExternal && restoreSavedPosition) {
            dataStoreManager.externalTtsResumePosition(bookId).first()
        } else {
            null
        }
        val resume = storedResume?.takeIf { candidate ->
            val fingerprint = candidate.pageFingerprint ?: return@takeIf false
            val savedPage = try {
                source.getPage(candidate.chapterIndex, candidate.pageIndex)
            } catch (error: kotlinx.coroutines.CancellationException) {
                throw error
            } catch (_: Throwable) {
                null
            }
            if (savedPage?.resumeFingerprint != fingerprint) return@takeIf false
            // A cache from an older session/provider must not override the durable listening place.
            listeningPosition == null ||
                (listeningPosition.chapterIndex == candidate.chapterIndex &&
                    textExtractor.splitIntoSegments(savedPage.text, savedPage.startCharacterOffset)
                        .any { segment ->
                            candidate.characterOffset in segment.startCharacterOffset until segment.endCharacterOffset &&
                                listeningPosition.characterOffset in segment.startCharacterOffset until segment.endCharacterOffset
                        })
        }
        if (storedResume != null && resume == null) {
            dataStoreManager.clearExternalTtsResumePosition(bookId)
        }
        if (generation != sessionGeneration) return@withContext Result.success(Unit)
        pendingResume = resume
        _playbackState.value = TtsPlaybackState.PLAYING
        logTtsEvent("state_changed", state = TtsPlaybackState.PLAYING)
        val targetChapter = listeningPosition?.chapterIndex ?: startChapter
        val requestedOffset = resume?.characterOffset ?: listeningPosition?.characterOffset ?: startCharacterOffset
        val locatedPage = requestedOffset
            ?.takeIf { resume == null }
            ?.let { offset -> locatePageWithFallback(targetChapter, offset) }
        val location = resume?.let { TtsPageLocation(it.chapterIndex, it.pageIndex) }
            ?: locatedPage?.location
            ?: listeningPosition?.let { TtsPageLocation(it.chapterIndex, it.pageIndex) }
            ?: TtsPageLocation(startChapter, startPage)
        followReaderPageTurns = true
        val moved = moveToPage(
            location = location,
            startAtEnd = false,
            publishLocation = true,
            startCharacterOffset = resume?.characterOffset,
            seekCharacterOffset = requestedOffset?.takeIf {
                resume == null && (locatedPage != null || listeningPosition != null)
            }
        )
        if (generation != sessionGeneration) return@withContext Result.success(Unit)
        if (!moved) {
            val error = pageContentError ?: IllegalStateException("No readable text found")
            stopInternal()
            return@withContext Result.failure(error)
        }
        recordListeningUse(positionUse)
        Result.success(Unit)
        }
    }

    private suspend fun selectPlaybackEngine(
        selection: TtsProviderSelection
    ): Result<TtsPlaybackEngine> = runCatching {
        when (selection) {
            TtsProviderSelection.AiModel -> externalTtsEngine
            TtsProviderSelection.SystemDefault -> {
                (systemTtsEngine as? AndroidTtsPlaybackEngine)?.selectEngine(null)
                systemTtsEngine
            }
            is TtsProviderSelection.AndroidEngine -> {
                val androidEngine = systemTtsEngine as? AndroidTtsPlaybackEngine
                    ?: throw SystemTtsException.EngineUnavailable(selection.packageName)
                androidEngine.selectEngine(selection.packageName)
                systemTtsEngine
            }
        }
    }

    fun pause() {
        scope.launch {
            commandMutex.withLock {
            if (_playbackState.value != TtsPlaybackState.PLAYING) {
                logTtsCommand("PAUSE", accepted = false, reason = "state_not_playing")
                return@withLock
            }
            logTtsCommand("PAUSE", accepted = true)
            val engine = activeEngine
            onListeningActivity(null)
            clearQueuedSentence()
            pauseBetweenUtterances?.cancel()
            if (!engine.canResumeWithoutRestart && completedUtteranceId != activeUtteranceId) activeUtteranceId = null
            engine.pause()
            if (completedUtteranceId != activeUtteranceId || completedUtteranceId == null) {
                persistListeningPosition(force = true)
            }
            _playbackState.value = TtsPlaybackState.PAUSED
            logTtsEvent("state_changed", state = TtsPlaybackState.PAUSED)
            }
        }
    }

    fun resume(recordUse: Boolean = true) {
        val positionUse = if (recordUse) BookPositionUse.create(BookPositionSource.LISTENING) else null
        scope.launch {
            commandMutex.withLock {
            if (_playbackState.value != TtsPlaybackState.PAUSED) {
                logTtsCommand("PLAY", accepted = false, reason = "state_not_paused")
                return@withLock
            }
            if (segments.isEmpty()) {
                logTtsCommand("PLAY", accepted = false, reason = "no_segments")
                return@withLock
            }
            logTtsCommand("PLAY", accepted = true)
            _playbackState.value = TtsPlaybackState.PLAYING
            logTtsEvent("state_changed", state = TtsPlaybackState.PLAYING)
            val completed = completedUtteranceId
            if (completed != null && completed == activeUtteranceId) {
                advanceAfterUtterance()
            } else if (!activeEngine.resume()) {
                val initialized = runCatching { activeEngine.initialize() }
                    .getOrElse { Result.failure(it) }
                if (initialized.isSuccess) {
                    speakCurrentSegment()
                } else {
                    _playbackState.value = TtsPlaybackState.PAUSED
                    _errors.tryEmit(initialized.exceptionOrNull())
                    logTtsEvent(
                        event = "resume_failed",
                        errorType = initialized.exceptionOrNull()?.javaClass?.simpleName,
                        result = "paused"
                    )
                }
            }
            if (_playbackState.value == TtsPlaybackState.PLAYING && positionUse != null) recordListeningUse(positionUse)
            }
        }
    }

    fun stop() {
        scope.launch {
            commandMutex.withLock {
                logTtsCommand(
                    "STOP",
                    accepted = _playbackState.value != TtsPlaybackState.IDLE,
                    reason = if (_playbackState.value == TtsPlaybackState.IDLE) "already_idle" else null
                )
                stopInternal()
            }
        }
    }

    fun onBookOpened(bookId: String) {
        scope.launch {
            commandMutex.withLock {
                if (_activeBookId.value != null && _activeBookId.value != bookId) stopInternal()
            }
        }
    }

    fun leaveReader(bookId: String) {
        setReaderAttached(bookId, false)
        scope.launch {
            commandMutex.withLock {
                if (_activeBookId.value == bookId) pageSource?.detachReader()
            }
        }
    }

    fun skip(forward: Boolean = true) {
        val positionUse = BookPositionUse.create(BookPositionSource.LISTENING)
        scope.launch {
            commandMutex.withLock {
            val state = _playbackState.value
            val command = if (forward) "NEXT" else "PREVIOUS"
            if (state != TtsPlaybackState.PLAYING && state != TtsPlaybackState.PAUSED) {
                logTtsCommand(command, accepted = false, reason = "state_not_active")
                return@withLock
            }
            logTtsCommand(command, accepted = true)
            onListeningActivity(null)
            activeUtteranceId = null
            resetClausePlayback()
            clearQueuedSentence()
            activeEngine.stop()

            if (forward) {
                if (sentenceIndex + 1 < segments.size) {
                    sentenceIndex++
                    crossPageMerge = null
                    if (state == TtsPlaybackState.PLAYING) speakCurrentSegment()
                } else {
                    val merge = crossPageMerge
                    crossPageMerge = null
                    if (merge != null) {
                        moveToPage(
                            location = merge.landingLocation,
                            startAtEnd = false,
                            publishLocation = true,
                            startCharacterOffset = merge.landingStartCharacterOffset
                        )
                    } else {
                        moveToAdjacentPage(forward = true)
                    }
                }
            } else {
                crossPageMerge = null
                if (sentenceIndex > 0) {
                    sentenceIndex--
                    if (state == TtsPlaybackState.PLAYING) speakCurrentSegment()
                } else {
                    moveToAdjacentPage(forward = false)
                }
            }
            recordListeningUse(positionUse, persistTarget = true)
            }
        }
    }

    /** A foreground reattachment can also catch the reader up to a specific live session. */
    fun setReaderAttached(
        bookId: String,
        attached: Boolean,
        resumeSessionId: Long? = null,
        resumeFromPage: TtsPageLocation? = null
    ) {
        scope.launch {
            commandMutex.withLock {
                if (_activeBookId.value != bookId ||
                    resumeSessionId != null && resumeSessionId != sessionGeneration
                ) return@withLock
                val wasAttached = readerAttached
                readerAttached = attached
                pageSource?.setReaderVisible(visualReaderAttached)
                if (attached) {
                    followReaderPageTurns = true
                    // Foregrounding and closing the fullscreen player can happen in either order.
                    if (!wasAttached && canTurnReaderPage && resumeSessionId != null) {
                        requestCurrentReaderPage(resumeFromPage ?: fullscreenReaderLocation)
                    }
                    return@withLock
                }
                cancelReaderPageTurn()
            }
        }
    }

    /** A visual lease only: source, timer, page-turn setting and engine stay alive. */
    fun setFullscreenVisible(sessionId: Long, visible: Boolean) {
        scope.launch {
            commandMutex.withLock {
                if (sessionId != sessionGeneration) return@withLock
                if (_fullscreenVisible.value == visible) return@withLock
                _fullscreenVisible.value = visible
                fullscreenSession = if (visible) sessionId else null
                pageSource?.setReaderVisible(visualReaderAttached)
                if (visible) {
                    fullscreenReaderLocation = _currentPage.value?.location
                    cancelReaderPageTurn()
                } else if (canTurnReaderPage) {
                    requestCurrentReaderPage(fullscreenReaderLocation)
                }
            }
        }
    }

    private suspend fun requestCurrentReaderPage(sourceLocation: TtsPageLocation?) {
        val page = _currentPage.value ?: return
        val bookId = _activeBookId.value ?: return
        val position = liveListeningPosition(bookId, sessionGeneration)?.takeIf {
            it.chapterIndex == page.location.chapterIndex && it.pageIndex == page.location.pageIndex
        }
        val request = TtsPageTurnRequest(
            bookId = bookId,
            sessionId = sessionGeneration, requestId = ++pageTurnSequence,
            location = page.location,
            characterOffset = position?.characterOffset ?: page.startCharacterOffset
        )
        pendingPageTurn = PendingPageTurn(request, sourceLocation, resumeWhenAcknowledged = false)
        _pageTurnRequests.emit(request)
    }

    suspend fun skipChapter(forward: Boolean): Boolean = withContext(Dispatchers.Main.immediate) {
        val positionUse = BookPositionUse.create(BookPositionSource.LISTENING)
        commandMutex.withLock {
            if (_playbackState.value !in listOf(TtsPlaybackState.PLAYING, TtsPlaybackState.PAUSED)) return@withLock false
            val chapter = _currentPage.value?.location?.chapterIndex ?: return@withLock false
            val target = chapter + if (forward) 1 else -1
            if (target < 0 || (_chapterSnapshot.value?.count?.let { target >= it } == true)) return@withLock false
            val moved = moveToPage(TtsPageLocation(target, 0), startAtEnd = false, publishLocation = true)
            if (moved) recordListeningUse(positionUse, persistTarget = true)
            moved
        }
    }

    /** Session-only engine selection. Never stop/start the session or save the global provider. */
    suspend fun switchSessionEngine(selection: TtsProviderSelection): Result<Unit> = withContext(Dispatchers.Main.immediate) {
        commandMutex.withLock {
            if (_activeBookId.value == null || _playbackState.value !in listOf(TtsPlaybackState.PLAYING, TtsPlaybackState.PAUSED)) {
                return@withLock Result.failure(IllegalStateException("No active session"))
            }
            val previous = _providerSelection.value
            if (selection == previous) return@withLock Result.success(Unit)
            val previousEngine = activeEngine
            val wasPlaying = _playbackState.value == TtsPlaybackState.PLAYING
            // A resumed session may be on a continuation page. Rewind to the canonical
            // sentence start before changing voices, without submitting to the old voice.
            val page = _currentPage.value
            val utterance = _currentUtterance.value
            val chapter = _chapterSnapshot.value
            if (page != null && utterance != null && chapter?.index == page.location.chapterIndex) {
                val start = textExtractor.splitIntoSegments(chapter.text)
                    .lastOrNull { it.startCharacterOffset <= utterance.startCharacterOffset }
                    ?.startCharacterOffset
                if (start != null && start < utterance.startCharacterOffset) {
                    val target = try { pageSource?.locatePage(chapter.index, start) }
                        catch (cancelled: CancellationException) { throw cancelled }
                        catch (_: Exception) { null }
                    if (target != null) moveToPage(target.location, startAtEnd = false,
                        publishLocation = false, seekCharacterOffset = start, speakWhenPlaying = false)
                }
            }
            engineRollback = null
            nextSentencePrefetchJob?.cancel()
            activeUtteranceId = null
            onListeningActivity(null)
            clearQueuedSentence()
            previousEngine.stop()
            pendingResume = null
            resetClausePlayback()
            val result = try {
                sessionEngine = selectPlaybackEngine(selection).getOrThrow()
                activeEngine.initialize().getOrThrow()
                applyProsody(activeEngine)
                _usesAndroidTts.value = !activeEngine.isExternal
                if (wasPlaying) speakCurrentSegment(throwOnFailure = true)
                _providerSelection.value = selection
                engineRollback = EngineRollback(previous, previousEngine, activeUtteranceId)
                Result.success(Unit)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                restoreSessionEngine(EngineRollback(previous, previousEngine, null))
                Result.failure(error)
            }
            result
        }
    }

    private suspend fun restoreSessionEngine(rollback: EngineRollback) {
        engineRollback = null
        activeUtteranceId = null
        nextSentencePrefetchJob?.cancel()
        resetClausePlayback()
        try {
            clearQueuedSentence()
            activeEngine.stop()
            sessionEngine = rollback.engine
            sessionEngine = selectPlaybackEngine(rollback.selection).getOrThrow()
            activeEngine.initialize().getOrThrow()
            applyProsody(activeEngine)
            if (_playbackState.value == TtsPlaybackState.PLAYING) speakCurrentSegment(throwOnFailure = true)
        } catch (cancelled: CancellationException) { throw cancelled }
          catch (error: Exception) {
            _playbackState.value = TtsPlaybackState.PAUSED
            _errors.tryEmit(error)
        } finally {
            _providerSelection.value = rollback.selection
            _usesAndroidTts.value = !activeEngine.isExternal
        }
    }

    fun onPageVisible(
        bookId: String,
        chapterIndex: Int,
        pageIndex: Int,
        origin: TtsPageChangeOrigin = TtsPageChangeOrigin.USER
    ) {
        scope.launch {
            commandMutex.withLock {
            if (_activeBookId.value != bookId || !visualReaderAttached) return@withLock
            val state = _playbackState.value
            if (state != TtsPlaybackState.PLAYING && state != TtsPlaybackState.PAUSED) return@withLock
            val location = TtsPageLocation(chapterIndex, pageIndex)
            logTtsEvent(
                event = "page_visible",
                location = location,
                callbackOrigin = origin
            )
            val pending = pendingPageTurn
            if (pending != null) {
                if (pending.request.sessionId != sessionGeneration) return@withLock
                if (pending.request.location == location) {
                    acknowledgePendingPageTurn(pending)
                    return@withLock
                }
                if (origin != TtsPageChangeOrigin.USER) return@withLock
                followReaderPageTurns = false
                cancelReaderPageTurn()
                return@withLock
            }
            // Duplicated callbacks from one page animation must not count as user navigation.
            val acknowledged = acknowledgedPageTurn
            if (acknowledged != null) {
                val withinAnimationWindow = System.nanoTime() - acknowledged.acknowledgedAtNanos <=
                    PAGE_TURN_CALLBACK_GRACE_NANOS
                if (withinAnimationWindow &&
                    (location == acknowledged.sourceLocation ||
                        location == acknowledged.targetLocation)
                ) return@withLock
                acknowledgedPageTurn = null
            }
            // TTS_FOLLOW is visual feedback, not a seek command. Only the pending target above
            // may acknowledge it: a delayed animation callback can outlive its turn or session.
            if (origin == TtsPageChangeOrigin.TTS_FOLLOW) return@withLock
            if (origin == TtsPageChangeOrigin.USER && location != _currentPage.value?.location) {
                followReaderPageTurns = false
            }
            }
        }
    }

    /**
     * Jumps playback to [characterOffset] inside [chapterIndex] without moving the reading page.
     * Used by the double-tap gesture on a sentence.
     */
    fun seekTo(chapterIndex: Int, characterOffset: Int, resumeIfPaused: Boolean = true) {
        val positionUse = BookPositionUse.create(BookPositionSource.LISTENING)
        scope.launch {
            commandMutex.withLock {
                val state = _playbackState.value
                if (state != TtsPlaybackState.PLAYING && state != TtsPlaybackState.PAUSED) {
                    return@withLock
                }
                if (pageSource == null) return@withLock
                val target = locatePageWithFallback(chapterIndex, characterOffset)
                    ?: return@withLock
                val targetClauses = textExtractor.splitIntoSegments(target.text, target.startCharacterOffset)
                    .flatMap { textExtractor.splitIntoClauses(it) }
                val clauseStart = targetClauses.getOrNull(
                    textExtractor.segmentIndexForOffset(targetClauses, characterOffset)
                )?.startCharacterOffset
                if (!_fullscreenVisible.value) followReaderPageTurns = true
                if (state == TtsPlaybackState.PAUSED && resumeIfPaused) {
                    _playbackState.value = TtsPlaybackState.PLAYING
                    logTtsEvent("state_changed", state = TtsPlaybackState.PLAYING)
                }
                logTtsEvent(
                    event = "sentence_seek",
                    location = target.location,
                    sentenceOffset = characterOffset
                )
                if (!moveToPage(
                        location = target.location,
                        startAtEnd = false,
                        publishLocation = false,
                        startCharacterOffset = clauseStart,
                        seekCharacterOffset = characterOffset
                    )
                ) {
                    pageContentError?.let(_errors::tryEmit)
                    stopInternal()
                } else {
                    recordListeningUse(positionUse, persistTarget = true, characterOffset = clauseStart)
                }
            }
        }
    }

    private suspend fun locatePageWithFallback(
        chapterIndex: Int,
        characterOffset: Int
    ): TtsPageContent? {
        val source = pageSource ?: return null
        val located = try {
            source.locatePage(chapterIndex, characterOffset)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            pageContentError = error
            null
        }
        if (located != null) return located
        val current = _currentPage.value
        return current?.takeIf { page ->
            page.location.chapterIndex == chapterIndex &&
                characterOffset in page.startCharacterOffset until
                (page.startCharacterOffset + page.text.length)
        }
    }

    fun isPageTurnRequestActive(request: TtsPageTurnRequest): Boolean =
        request.bookId == _activeBookId.value &&
            request.sessionId == sessionGeneration &&
            request.requestId == pageTurnSequence &&
            canTurnReaderPage &&
            request.location == _currentPage.value?.location &&
            _playbackState.value != TtsPlaybackState.IDLE

    fun setAutoPageTurn(enabled: Boolean) {
        scope.launch {
            commandMutex.withLock {
                if (_autoPageTurn.value == enabled) return@withLock
                _autoPageTurn.value = enabled
                if (!enabled) cancelReaderPageTurn()
            }
        }
    }

    private suspend fun cancelReaderPageTurn() {
        val resume = pendingPageTurn?.resumeWhenAcknowledged == true
        cancelPageTurnAckTimeout()
        pendingPageTurn = null
        acknowledgedPageTurn = null
        clearPageTurnReplay()
        pageTurnSequence++
        if (resume && _playbackState.value == TtsPlaybackState.PLAYING && activeUtteranceId == null) {
            speakCurrentSegment()
        }
    }

    fun pageChangeOriginFor(
        bookId: String,
        chapterIndex: Int,
        pageIndex: Int
    ): TtsPageChangeOrigin {
        val pending = pendingPageTurn?.request
        val source = pendingPageTurn?.sourceLocation
        return if (pending?.bookId == bookId &&
            pending.sessionId == sessionGeneration
        ) {
            if (pending.location == TtsPageLocation(chapterIndex, pageIndex) ||
                source == TtsPageLocation(chapterIndex, pageIndex)
            ) {
                TtsPageChangeOrigin.TTS_FOLLOW
            } else {
                TtsPageChangeOrigin.LAYOUT
            }
        } else {
            TtsPageChangeOrigin.USER
        }
    }

    fun acknowledgePageTurnRequest(request: TtsPageTurnRequest) {
        scope.launch {
            commandMutex.withLock {
            if (!isPageTurnRequestActive(request)) return@withLock
            val pending = pendingPageTurn ?: return@withLock
            if (pending.request.sessionId == request.sessionId &&
                pending.request.requestId == request.requestId &&
                pending.request.location == request.location
            ) {
                acknowledgePendingPageTurn(pending)
            }
            }
        }
    }

    private suspend fun acknowledgePendingPageTurn(pending: PendingPageTurn) {
        cancelPageTurnAckTimeout()
        acknowledgedPageTurn = AcknowledgedPageTurn(
            sourceLocation = pending.sourceLocation,
            targetLocation = pending.request.location,
            acknowledgedAtNanos = System.nanoTime()
        )
        pendingPageTurn = null
        if (pending.resumeWhenAcknowledged &&
            _playbackState.value == TtsPlaybackState.PLAYING &&
            activeUtteranceId == null
        ) {
            speakCurrentSegment()
        }
    }

    /** Retry attached-reader turns instead of letting speech silently outrun the visible page. */
    private fun schedulePageTurnAckTimeout(
        request: TtsPageTurnRequest,
        cancelCurrent: Boolean = true
    ) {
        if (cancelCurrent) cancelPageTurnAckTimeout()
        val timeoutJob = scope.launch {
            delay(PAGE_TURN_ACK_TIMEOUT_MS)
            commandMutex.withLock {
                val pending = pendingPageTurn ?: return@withLock
                if (pending.request.requestId != request.requestId) return@withLock
                val canRetry = pending.resumeWhenAcknowledged && canTurnReaderPage &&
                    pending.retryCount < MAX_PAGE_TURN_RETRIES
                if (canRetry) {
                    val retryRequest = pending.request.copy(requestId = ++pageTurnSequence)
                    val retry = pending.copy(request = retryRequest, retryCount = pending.retryCount + 1)
                    pendingPageTurn = retry
                    logTtsEvent(
                        event = "page_turn_ack_timeout",
                        location = retryRequest.location,
                        requestId = request.requestId,
                        retryAttempt = retry.retryCount,
                        result = "retrying"
                    )
                    _pageTurnRequests.emit(retryRequest)
                    schedulePageTurnAckTimeout(retryRequest, cancelCurrent = false)
                } else {
                    pendingPageTurn = null
                    val mayContinueWithoutReader = !canTurnReaderPage
                    logTtsEvent(
                        event = "page_turn_ack_timeout",
                        location = pending.request.location,
                        requestId = request.requestId,
                        retryAttempt = pending.retryCount,
                        result = if (mayContinueWithoutReader) "continue_without_reader" else "paused"
                    )
                    if (pending.resumeWhenAcknowledged &&
                        _playbackState.value == TtsPlaybackState.PLAYING &&
                        activeUtteranceId == null
                    ) {
                        if (mayContinueWithoutReader) {
                            speakCurrentSegment()
                        } else {
                            activeEngine.pause()
                            _playbackState.value = TtsPlaybackState.PAUSED
                            logTtsEvent("state_changed", state = TtsPlaybackState.PAUSED)
                        }
                    }
                }
            }
        }
        pendingPageTurnTimeoutJob = timeoutJob
    }

    private fun cancelPageTurnAckTimeout() {
        pendingPageTurnTimeoutJob?.cancel()
        pendingPageTurnTimeoutJob = null
    }

    suspend fun setSpeechRate(rate: Float) = withContext(Dispatchers.Main.immediate) {
        commandMutex.withLock {
        val safeRate = rate.coerceIn(0.5f, 5f)
        val applied = runCatching {
            applyProsody(
                activeEngine,
                rate = safeRate,
                rateMode = TtsProsodyMode.OVERRIDE
            )
        }
        if (applied.isFailure) {
            _errors.tryEmit(applied.exceptionOrNull())
            return@withContext
        }
        _speechRate.value = safeRate
        _speechRateMode.value = TtsProsodyMode.OVERRIDE
        dataStoreManager.saveTtsProsodySettings(currentProsodySettings())
        restartAndroidUtteranceAfterProsodyChange()
        }
    }

    suspend fun setSpeechRateMode(mode: TtsProsodyMode) = withContext(Dispatchers.Main.immediate) {
        commandMutex.withLock {
        if (_speechRateMode.value == mode) return@withContext
        val applied = runCatching { applyProsody(activeEngine, rateMode = mode) }
        if (applied.isFailure) {
            _errors.tryEmit(applied.exceptionOrNull())
            return@withContext
        }
        _speechRateMode.value = mode
        dataStoreManager.saveTtsProsodySettings(currentProsodySettings())
        restartAndroidUtteranceAfterProsodyChange()
        }
    }

    suspend fun setPitch(pitch: Float) = withContext(Dispatchers.Main.immediate) {
        commandMutex.withLock {
        val safePitch = pitch.coerceIn(0.5f, 2f)
        if (!activeEngine.isExternal) {
            val applied = runCatching {
                applyProsody(
                    activeEngine,
                    pitch = safePitch,
                    pitchMode = TtsProsodyMode.OVERRIDE
                )
            }
            if (applied.isFailure) {
                _errors.tryEmit(applied.exceptionOrNull())
                return@withContext
            }
        }
        _pitch.value = safePitch
        _pitchMode.value = TtsProsodyMode.OVERRIDE
        dataStoreManager.saveTtsProsodySettings(currentProsodySettings())
        if (!activeEngine.isExternal) restartAndroidUtteranceAfterProsodyChange()
        }
    }

    suspend fun setPitchMode(mode: TtsProsodyMode) = withContext(Dispatchers.Main.immediate) {
        commandMutex.withLock {
        if (_pitchMode.value == mode) return@withContext
        if (!activeEngine.isExternal) {
            val applied = runCatching { applyProsody(activeEngine, pitchMode = mode) }
            if (applied.isFailure) {
                _errors.tryEmit(applied.exceptionOrNull())
                return@withContext
            }
        }
        _pitchMode.value = mode
        dataStoreManager.saveTtsProsodySettings(currentProsodySettings())
        if (!activeEngine.isExternal) restartAndroidUtteranceAfterProsodyChange()
        }
    }

    private suspend fun applyProsody(
        engine: TtsPlaybackEngine,
        rate: Float = _speechRate.value,
        rateMode: TtsProsodyMode = _speechRateMode.value,
        pitch: Float = _pitch.value,
        pitchMode: TtsProsodyMode = _pitchMode.value
    ) {
        if (engine.isExternal) {
            engine.applyProsody(rate = rate, pitch = null)
        } else {
            engine.applyProsody(
                rate = rate.takeIf { rateMode == TtsProsodyMode.OVERRIDE },
                pitch = pitch.takeIf { pitchMode == TtsProsodyMode.OVERRIDE }
            )
        }
    }

    private suspend fun fallBackRejectedProsody(error: SystemTtsException.ProsodyRejected) {
        when (error.setting) {
            TtsProsodySetting.RATE -> {
                _speechRateMode.value = TtsProsodyMode.FOLLOW_ENGINE
            }
            TtsProsodySetting.PITCH -> {
                _pitchMode.value = TtsProsodyMode.FOLLOW_ENGINE
            }
        }
        dataStoreManager.saveTtsProsodySettings(currentProsodySettings())
        _errors.tryEmit(error)
    }

    private suspend fun restartAndroidUtteranceAfterProsodyChange() {
        if (activeEngine.isExternal || segments.isEmpty() ||
            completedUtteranceId != null && completedUtteranceId == activeUtteranceId) return
        val state = _playbackState.value
        if (state != TtsPlaybackState.PLAYING && state != TtsPlaybackState.PAUSED) return
        onListeningActivity(null)
        activeUtteranceId = null
        activeSegment = null
        activeSentenceSegment = null
        _currentSentence.value = null
        clearQueuedSentence()
        activeEngine.stop()
        val result = activeEngine.initialize()
        if (result.isFailure) {
            _errors.tryEmit(result.exceptionOrNull())
            stopInternal()
        } else if (state == TtsPlaybackState.PLAYING) {
            speakCurrentSegment()
        }
    }

    private suspend fun handleUtteranceDone(utteranceId: String?) {
        if (utteranceId == null || utteranceId != activeUtteranceId) return
        if (engineRollback?.utterance == utteranceId) engineRollback = null
        if (_playbackState.value != TtsPlaybackState.PLAYING) return
        if (completedUtteranceId == utteranceId) return
        completedUtteranceId = utteranceId
        nativeSubtitleJob?.cancel()
        persistListeningPosition(force = true, completed = clauseIndex == clauses.lastIndex)
        onListeningActivity(null)
        val pauseMillis = pauseAfterCurrentPart()
        if (pauseMillis == 0L) {
            advanceAfterUtterance()
        } else {
            pauseBetweenUtterances = scope.launch {
                delay(pauseMillis)
                commandMutex.withLock {
                    if (activeUtteranceId == utteranceId && _playbackState.value == TtsPlaybackState.PLAYING) {
                        advanceAfterUtterance()
                    }
                }
            }
        }
    }

    private fun pauseAfterCurrentPart(): Long {
        val part = activeSegment ?: return 0L
        val duration = sentencePauseDuration
        if (duration == TtsPauseDuration.STANDARD) return 0L
        val sentence = playbackSentence ?: return 0L
        val next = clauses.getOrNull(clauseIndex + 1)
        if (next != null) {
            val start = (part.endCharacterOffset - sentence.startCharacterOffset).coerceIn(0, sentence.text.length)
            val end = (next.startCharacterOffset - sentence.startCharacterOffset).coerceIn(start, sentence.text.length)
            if (sentence.text.substring(start, end).contains('\n')) return duration.paragraphMillis
            return textExtractor.boundaryPause(part.text, duration)
        }
        val page = _currentPage.value ?: return 0L
        val offset = (sentence.endCharacterOffset - page.startCharacterOffset).coerceIn(0, page.text.length)
        val separator = page.text.asSequence().drop(offset).takeWhile(Char::isWhitespace)
        val chapterEnds = sentenceIndex == segments.lastIndex && page.next?.chapterIndex != page.location.chapterIndex
        return if (chapterEnds || page.text.getOrNull(offset - 1)?.let(::isTtsLineBreak) == true ||
            separator.any(::isTtsLineBreak)) {
            duration.paragraphMillis
        } else textExtractor.boundaryPause(part.text, duration)
    }

    private fun isTtsLineBreak(char: Char): Boolean = char == '\n' || char == '\r'

    private suspend fun advanceAfterUtterance() {
        completedUtteranceId = null
        pauseBetweenUtterances = null
        if (clauseIndex < clauses.lastIndex) {
            activeUtteranceId = null
            clauseIndex++
            speakCurrentSegment()
            return
        }
        activeUtteranceId = null
        utteranceRetryCount = 0
        activeSegment = null
        activeSentenceSegment = null
        pendingResume = null
        resetClausePlayback()
        if (sentenceIndex + 1 < segments.size) {
            sentenceIndex++
            speakCurrentSegment()
            return
        }

        val merge = crossPageMerge
        if (merge != null) {
            crossPageMerge = null
            moveToPage(
                location = merge.landingLocation,
                startAtEnd = false,
                publishLocation = true,
                startCharacterOffset = merge.landingStartCharacterOffset,
                preservePreparedAudio = true
            )
            return
        }

        moveToAdjacentPage(forward = true)
    }

    private suspend fun handleUtteranceError(utteranceId: String?, throwable: Throwable) {
        if (utteranceId == null || utteranceId != activeUtteranceId || utteranceId == completedUtteranceId) return
        engineRollback?.takeIf { it.utterance == utteranceId }?.let {
            restoreSessionEngine(it)
            _engineSwitchFailures.tryEmit(Unit)
            return
        }
        recoverOrPauseAfterPlaybackError(utteranceId, throwable)
    }

    private suspend fun recoverOrPauseAfterPlaybackError(
        failedUtteranceId: String,
        throwable: Throwable
    ) {
        if (failedUtteranceId != activeUtteranceId) return
        val recoverable = !activeEngine.isExternal && isRecoverableSystemFailure(throwable)
        if (recoverable && utteranceRetryCount < MAX_UTTERANCE_RETRIES) {
            utteranceRetryCount++
            val retry = utteranceRetryCount
            logTtsEvent(
                event = "utterance_retry",
                utteranceId = failedUtteranceId,
                errorType = throwable.javaClass.simpleName,
                retryAttempt = retry,
                result = "reinitializing"
            )
            // Invalidate the failed callback before stopping the engine. The sentence/page indices
            // remain untouched, so speakCurrentSegment() starts the same sentence again.
            onListeningActivity(null)
            activeUtteranceId = null
            clearQueuedSentence()
            val initialized = runCatching { activeEngine.reinitialize() }
                .getOrElse { Result.failure(it) }
            if (initialized.isSuccess && _playbackState.value == TtsPlaybackState.PLAYING) {
                logTtsEvent(
                    event = "utterance_retry",
                    errorType = throwable.javaClass.simpleName,
                    retryAttempt = retry,
                    result = "replaying"
                )
                speakCurrentSegment()
                return
            }
            val initializationFailure = initialized.exceptionOrNull()
            logTtsEvent(
                event = "utterance_retry",
                errorType = (initializationFailure ?: throwable).javaClass.simpleName,
                retryAttempt = retry,
                result = "initialization_failed"
            )
            finishPlaybackError(initializationFailure ?: throwable, keepSession = true)
            return
        }

        finishPlaybackError(throwable, keepSession = recoverable)
    }

    private fun isRecoverableSystemFailure(error: Throwable): Boolean = when (error) {
        is SystemTtsException.Playback,
        is SystemTtsException.Initialization,
        is IOException -> true
        else -> false
    }

    private suspend fun finishPlaybackError(error: Throwable, keepSession: Boolean) {
        val failedUtterance = activeUtteranceId
        onListeningActivity(null)
        activeUtteranceId = null
        clearQueuedSentence()
        activeEngine.stop()
        _errors.tryEmit(error)
        logTtsEvent(
            event = "utterance_failed",
            utteranceId = failedUtterance,
            errorType = error.javaClass.simpleName,
            retryAttempt = utteranceRetryCount,
            result = if (keepSession) "paused" else "stopped"
        )
        if (keepSession && _currentPage.value != null) {
            _playbackState.value = TtsPlaybackState.PAUSED
            logTtsEvent("state_changed", state = TtsPlaybackState.PAUSED)
        } else {
            stopInternal()
        }
    }

    private suspend fun moveToAdjacentPage(forward: Boolean) {
        val page = _currentPage.value ?: run {
            stopInternal()
            return
        }
        val target = if (forward) page.next else page.previous
        if (target == null) {
            stopInternal(clearExternalResume = forward)
            return
        }
        if (!moveToPage(target, startAtEnd = !forward, publishLocation = true,
                preservePreparedAudio = forward)) {
            pageContentError?.let(_errors::tryEmit)
            stopInternal()
        }
    }

    private suspend fun moveToPage(
        location: TtsPageLocation,
        startAtEnd: Boolean,
        publishLocation: Boolean,
        startCharacterOffset: Int? = null,
        seekCharacterOffset: Int? = null,
        preservePreparedAudio: Boolean = false,
        speakWhenPlaying: Boolean = true
    ): Boolean {
        val source = pageSource ?: return false
        val token = ++pageLoadToken
        pageContentError = null
        onListeningActivity(null)
        activeUtteranceId = null
        utteranceRetryCount = 0
        resetClausePlayback()
        if (preservePreparedAudio) activeEngine.stopForPageTurn() else {
            clearQueuedSentence()
            activeEngine.stop()
        }
        crossPageMerge = null
        // Any new playback decision supersedes a turn we were still waiting for.
        cancelPageTurnAckTimeout()
        pendingPageTurn = null

        var target = location
        var selectedPage: TtsPageContent? = null
        var selectedSegments: List<TtsTextSegment> = emptyList()
        var chapterSnapshot: TtsChapterSnapshot? = null
        var attempts = 0
        while (attempts < 50 && selectedPage == null) {
            val page = try {
                prefetchedPage?.takeIf { preservePreparedAudio && it.location == target }
                    ?: source.getPage(target.chapterIndex, target.pageIndex)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                pageContentError = error
                return false
            }
            if (page == null) return token != pageLoadToken
            var playablePage = page
            var pageSegments = textExtractor.splitIntoSegments(
                text = page.text,
                baseCharacterOffset = page.startCharacterOffset,
                startCharacterOffset = startCharacterOffset.takeIf { page.location == location }
            ).filter { segment -> textExtractor.hasSpeakableContent(segment.text) }
            if (pageSegments.isEmpty() && page.endCharacterOffset > page.startCharacterOffset) {
                val chapter = chapterSnapshot?.takeIf { it.index == page.location.chapterIndex }
                    ?: try {
                        source.getChapter(page.location.chapterIndex)?.also { chapterSnapshot = it }
                    } catch (error: CancellationException) {
                        throw error
                    } catch (_: Exception) {
                        null
                    }
                val recovered = chapter?.let { recoverTtsPageText(page, it) }
                val recoveredSegments = recovered?.let { recoveredPage ->
                    textExtractor.splitIntoSegments(
                        text = recoveredPage.text,
                        baseCharacterOffset = recoveredPage.startCharacterOffset,
                        startCharacterOffset = startCharacterOffset.takeIf { recoveredPage.location == location }
                    ).filter { segment -> textExtractor.hasSpeakableContent(segment.text) }
                }.orEmpty()
                if (recovered != null && recoveredSegments.isNotEmpty()) {
                    playablePage = recovered
                    pageSegments = recoveredSegments
                    logTtsEvent("page_text_recovered", location = page.location,
                        result = "chapter_range", characterStart = recovered.startCharacterOffset,
                        characterEnd = recovered.endCharacterOffset, textLength = recovered.text.length)
                }
            }
            if (pageSegments.isNotEmpty()) {
                selectedPage = playablePage
                selectedSegments = pageSegments
            } else {
                logTtsEvent("page_skipped", location = page.location, result = "no_speakable_text",
                    characterStart = page.startCharacterOffset, characterEnd = page.endCharacterOffset,
                    textLength = page.text.length)
                target = if (startAtEnd) page.previous ?: return false else page.next ?: return false
            }
            attempts++
        }

        val page = selectedPage ?: return false
        if (token != pageLoadToken) return true
        prefetchedPage = null
        val sourceLocation = _currentPage.value?.location
        _currentPage.value = page
        segments = selectedSegments
        sentenceIndex = when {
            startAtEnd -> segments.lastIndex
            seekCharacterOffset != null ->
                textExtractor.segmentIndexForOffset(segments, seekCharacterOffset).coerceAtLeast(0)
            else -> 0
        }
        if (_chapterSnapshot.value?.index != page.location.chapterIndex) {
            _chapterSnapshot.value = try {
                source.getChapter(page.location.chapterIndex)
            } catch (cancelled: CancellationException) { throw cancelled }
              catch (_: Exception) { null }
        }
        prefetchedSuccessor = null
        if (!activeEngine.supportsQueuedPlayback) {
            _currentUtterance.value = segments.getOrNull(sentenceIndex)
            _currentSentence.value = _currentUtterance.value
            publishUpcomingUtterance()
            _listeningFrame.value = TtsListeningFrame(_chapterSnapshot.value, page, _currentUtterance.value)
        }
        if (publishLocation && canTurnReaderPage) {
            val request = TtsPageTurnRequest(
                bookId = _activeBookId.value ?: return false,
                sessionId = sessionGeneration,
                requestId = ++pageTurnSequence,
                location = page.location
            )
            logTtsEvent(
                event = "page_turn_requested",
                location = page.location,
                requestId = request.requestId
            )
            // Native successors have already been submitted to Android. A view animation must
            // not hold their adoption (and callbacks) behind a page-turn acknowledgement.
            val waitForAcknowledgement = sourceLocation != null && sourceLocation != page.location &&
                !activeEngine.supportsQueuedPlayback
            pendingPageTurn = if (sourceLocation != null && sourceLocation != page.location) {
                PendingPageTurn(request, sourceLocation, resumeWhenAcknowledged = waitForAcknowledgement)
            } else {
                null
            }
            _pageTurnRequests.emit(request)
            if (waitForAcknowledgement) {
                schedulePageTurnAckTimeout(request)
                return true
            }
        }
        if (speakWhenPlaying && _playbackState.value == TtsPlaybackState.PLAYING) speakCurrentSegment()
        return true
    }


    /**
     * Looks ahead across page boundaries to merge an incomplete trailing sentence
     * with continuation text from subsequent pages. Returns a synthetic segment
     * with merged text and first-page offsets, or null when no merge is possible.
     * Sets [crossPageMerge] so utterance completion can advance to the correct
     * landing page while skipping already-consumed text.
     */
    private suspend fun mergeAcrossPages(
        trailingText: String,
        trailingStartOffset: Int,
        trailingEndOffset: Int,
        generation: Long,
        initialPage: TtsPageContent? = _currentPage.value,
        recordLanding: Boolean = true
    ): TtsTextSegment? {
        if (generation != sessionGeneration) return null
        val preparedKey = initialPage?.let { landingKey(it, trailingStartOffset) }
        preparedKey?.let { key ->
            preparedSentences[key]?.let { prepared ->
                if (recordLanding) crossPageMerge = preparedLandings[key]
                return prepared
            }
        }
        val source = pageSource ?: return null
        val maxLength = TtsTextExtractor.MAX_SENTENCE_LENGTH
        var mergedText = trailingText
        // Segment normalization trims page-edge whitespace. Retain that boundary
        // when joining raw next-page text so English words do not run together.
        initialPage?.text?.lastOrNull()?.takeIf { it.isWhitespace() }?.let { mergedText += it }
        var lastPage = initialPage ?: return null
        var landingLocation = lastPage.location
        var landingStartOffset = lastPage.startCharacterOffset

        while (mergedText.length < maxLength) {
            if (generation != sessionGeneration) return null
            val nextLocation = lastPage.next ?: break
            if (nextLocation.chapterIndex != lastPage.location.chapterIndex) break
            val nextPage = try {
                source.getPage(nextLocation.chapterIndex, nextLocation.pageIndex)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                null
            } ?: break
            if (generation != sessionGeneration) return null

            val mergeResult = textExtractor.mergeTrailingSentence(
                trailing = mergedText,
                continuationRawText = nextPage.text,
                maxTotalLength = maxLength
            )
            if (mergeResult.consumedSourceChars == 0 && mergeResult.mergedText == mergedText) break

            mergedText = mergeResult.mergedText
            landingLocation = nextPage.location
            landingStartOffset = nextPage.startCharacterOffset + mergeResult.consumedSourceChars
            lastPage = nextPage

            val endedAtSentenceBoundary = mergedText.trimEnd().lastOrNull()?.let { last ->
                last in TtsTextExtractor.TERMINATORS || last in TtsTextExtractor.CLOSING_PUNCTUATION
            } == true
            // A paragraph boundary can stop consumption before the page ends. Never look
            // past its unread remainder: without a complete sentence the merge is discarded.
            if (endedAtSentenceBoundary || mergeResult.consumedSourceChars < nextPage.text.length) break
        }

        val endedAtSentenceBoundary = mergedText.trimEnd().lastOrNull()?.let { last ->
            last in TtsTextExtractor.TERMINATORS || last in TtsTextExtractor.CLOSING_PUNCTUATION
        } == true
        if (mergedText == trailingText || !endedAtSentenceBoundary) return null
        if (generation != sessionGeneration) return null
        val mergeLanding = CrossPageMergeState(landingLocation, landingStartOffset)
        initialPage?.let { preparedLandings[landingKey(it, trailingStartOffset)] = mergeLanding }
        if (recordLanding) crossPageMerge = mergeLanding
        return TtsTextSegment(
            text = mergedText,
            startCharacterOffset = trailingStartOffset,
            endCharacterOffset = trailingEndOffset
        ).also { prepared -> preparedKey?.let { preparedSentences[it] = prepared } }
    }
    private suspend fun speakCurrentSegment(throwOnFailure: Boolean = false) {
        val generation = sessionGeneration
        val page = _currentPage.value ?: return
        val sentence = prepareCurrentSentence(page, generation) ?: return
        val segment = clauses.getOrNull(clauseIndex) ?: return
        if (generation != sessionGeneration ||
            _currentPage.value?.location != page.location ||
            _playbackState.value != TtsPlaybackState.PLAYING
        ) return
        val spokenText = spokenText(segment, activeEngine, sentencePauseDuration)
        val queued = queuedSentences.firstOrNull()?.takeIf {
            it.generation == generation && it.engine === activeEngine &&
                it.page.location == page.location && it.segment.startCharacterOffset == segment.startCharacterOffset &&
                it.text == spokenText
        }
        val utteranceId = queued?.id ?: buildString {
            append(sessionGeneration)
            append(':')
            append(++utteranceSequence)
            append(":ch")
            append(page.location.chapterIndex)
            append("_pg")
            append(page.location.pageIndex)
            append("_s")
            append(sentenceIndex)
            append("_c")
            append(clauseIndex)
        }
        // Adopt only the head; any mismatch means the engine flushes its whole queue on speak().
        if (queued != null) queuedSentences.removeAt(0) else queuedSentences.clear()
        completedUtteranceId = null
        activeUtteranceId = utteranceId
        engineRollback?.takeIf { it.utterance == null }?.let { engineRollback = it.copy(utterance = utteranceId) }
        activeSegment = segment
        activeSentenceSegment = segment
        prefetchedSuccessor = null
        if (!activeEngine.supportsQueuedPlayback) {
            _currentUtterance.value = segment
            publishUpcomingUtterance()
            _listeningFrame.value = TtsListeningFrame(_chapterSnapshot.value, page, segment)
            _currentSentence.value = segment
        }
        logTtsEvent(
            event = "utterance_requested",
            location = page.location,
            utteranceId = utteranceId
        )
        val resume = pendingResume?.takeIf {
            it.chapterIndex == page.location.chapterIndex &&
                it.pageIndex == page.location.pageIndex &&
                it.characterOffset == sentence.startCharacterOffset &&
                it.cacheKey != null && it.cacheKey == activeEngine.cacheKey(spokenText)
        }
        pendingResume = null
        val result = activeEngine.speak(
            text = spokenText,
            utteranceId = utteranceId,
            cacheKey = resume?.cacheKey,
            startFrame = resume?.pcmFrameOffset ?: 0L
        )
        if (generation != sessionGeneration || activeUtteranceId != utteranceId) return
        if (result.isFailure && activeUtteranceId == utteranceId) {
            if (throwOnFailure) throw result.exceptionOrNull() ?: SystemTtsException.Playback()
            recoverOrPauseAfterPlaybackError(
                utteranceId,
                result.exceptionOrNull() ?: SystemTtsException.Playback()
            )
            return
        }
        if (activeEngine.isExternal) {
            val cacheKey = activeEngine.cacheKey(spokenText)
            dataStoreManager.saveExternalTtsResumePosition(
                ExternalTtsResumePosition(
                    bookId = _activeBookId.value ?: return,
                    chapterIndex = page.location.chapterIndex,
                    pageIndex = page.location.pageIndex,
                    characterOffset = playbackSentence?.startCharacterOffset ?: sentence.startCharacterOffset,
                    spokenCharacterOffset = segment.startCharacterOffset,
                    pageFingerprint = page.resumeFingerprint,
                    cacheKey = cacheKey,
                    pcmFrameOffset = resume?.pcmFrameOffset ?: 0L
                )
            )
        }
        scheduleNextSentencePrefetch(utteranceId)
    }

    private suspend fun prepareCurrentSentence(
        page: TtsPageContent,
        generation: Long
    ): TtsTextSegment? {
        playbackSentence?.let { return it }
        var sentence = segments.getOrNull(sentenceIndex) ?: return null
        if (
            crossPageMerge == null &&
            sentenceIndex == segments.lastIndex &&
            sentence.canContinueAcrossPage &&
            textExtractor.isTrailing(sentence.text)
        ) {
            val merged = mergeAcrossPages(
                sentence.text,
                sentence.startCharacterOffset,
                sentence.endCharacterOffset,
                generation
            )
            if (merged != null) sentence = merged
        }
        if (generation != sessionGeneration || _currentPage.value?.location != page.location) return null

        sentencePauseDuration = if (queuedSentences.isEmpty()) _pauseDuration.value else TtsPauseDuration.STANDARD
        playbackSentence = sentence
        clauses = playbackParts(sentence, sentencePauseDuration)
        pendingResume?.takeIf {
            it.chapterIndex == page.location.chapterIndex && it.pageIndex == page.location.pageIndex &&
                it.characterOffset == sentence.startCharacterOffset
        }?.let { resume -> restorePlaybackParts(sentence, resume) }
        clauseIndex = 0
        return sentence
    }

    private fun spokenText(
        segment: TtsTextSegment,
        engine: TtsPlaybackEngine,
        duration: TtsPauseDuration
    ): String {
        val text = if (duration == TtsPauseDuration.SHORT || duration == TtsPauseDuration.VERY_SHORT) {
            textExtractor.shortenPlaybackPause(segment.text)
        } else segment.text
        return if (engine.isExternal) text else textExtractor.playbackText(text)
    }

    private fun playbackParts(sentence: TtsTextSegment, duration: TtsPauseDuration): List<TtsTextSegment> =
        if (duration == TtsPauseDuration.STANDARD) listOf(sentence)
        else textExtractor.splitIntoClauses(sentence, Int.MAX_VALUE)
            .filter { textExtractor.hasSpeakableContent(it.text) }
            .ifEmpty { listOf(sentence) }

    private suspend fun restorePlaybackParts(sentence: TtsTextSegment, resume: ExternalTtsResumePosition) {
        val savedKey = resume.cacheKey
        if (savedKey != null && activeEngine.cacheKey(sentence.text) == savedKey) {
            clauses = listOf(sentence)
            return
        }
        val legacyPart = if (resume.spokenCharacterOffset == null) {
            textExtractor.splitIntoClauses(sentence).getOrNull(resume.clauseIndex)
        } else null
        val start = (resume.spokenCharacterOffset ?: legacyPart?.startCharacterOffset ?: return)
            .coerceIn(sentence.startCharacterOffset, sentence.startCharacterOffset + sentence.text.lastIndex)
        val offset = start - sentence.startCharacterOffset
        if (legacyPart != null && savedKey != null && activeEngine.cacheKey(legacyPart.text) == savedKey) {
            val end = legacyPart.endCharacterOffset - sentence.startCharacterOffset
            val remainder = sentence.text.substring(end)
            clauses = listOf(legacyPart) + if (remainder.isBlank()) emptyList() else playbackParts(
                sentence.copy(text = remainder, startCharacterOffset = legacyPart.endCharacterOffset), sentencePauseDuration
            )
        } else if (offset > 0) {
            clauses = playbackParts(sentence.copy(text = sentence.text.substring(offset), startCharacterOffset = start),
                sentencePauseDuration)
        }
    }

    private val preparedLandings = HashMap<String, CrossPageMergeState>()
    private val preparedSentences = HashMap<String, TtsTextSegment>()
    private fun landingKey(page: TtsPageContent, offset: Int) = "$sessionGeneration:${page.location}:$offset"

    private fun scheduleNextSentencePrefetch(utteranceId: String) {
        nextSentencePrefetchJob?.cancel()
        val generation = sessionGeneration
        val engine = activeEngine
        val source = pageSource ?: return
        val current = _currentPage.value ?: return
        val pageSegments = segments
        val baseIndex = sentenceIndex
        val depth = if (engine.supportsQueuedPlayback) {
            engine.maxQueuedUtterances.coerceAtLeast(1)
        } else {
            engine.prefetchDepth.coerceAtLeast(1)
        }
        val landing = crossPageMerge
        val currentClauses = clauses
        val duration = sentencePauseDuration
        val currentClauseIndex = clauseIndex
        nextSentencePrefetchJob = scope.launch {
            try {
                val successors = ArrayList<Pair<TtsPageContent, TtsTextSegment>>()
                fun spokenText(segment: TtsTextSegment) =
                    spokenText(segment, engine, duration)
                suspend fun submitKnown() {
                    if (successors.isEmpty()) return
                    commandMutex.withLock {
                        if (generation != sessionGeneration || utteranceId != activeUtteranceId || engine !== activeEngine)
                            return@withLock
                        val (firstPage, firstSegment) = successors.first()
                        if (firstPage.location != current.location) prefetchedPage = firstPage
                        prefetchedSuccessor = firstSegment
                        if (_currentUtterance.value === activeSentenceSegment) publishUpcomingUtterance()
                        if (engine.supportsQueuedPlayback && duration == TtsPauseDuration.STANDARD &&
                            _pauseDuration.value == duration) {
                            val alreadyQueued = queuedSentences.size
                            val consistent = alreadyQueued <= successors.size && queuedSentences.withIndex().all { (index, queued) ->
                                val (page, segment) = successors[index]
                                queued.generation == generation && queued.engine === engine &&
                                    queued.page.location == page.location &&
                                    queued.segment.startCharacterOffset == segment.startCharacterOffset &&
                                    queued.text == spokenText(segment)
                            }
                            if (!consistent) return@withLock
                            for ((page, segment) in successors.drop(alreadyQueued)) {
                                val text = spokenText(segment)
                                val id = "$generation:${++utteranceSequence}:queued_ch${page.location.chapterIndex}_pg${page.location.pageIndex}_at${segment.startCharacterOffset}"
                                if (engine.queueNext(text, id).isFailure) break
                                queuedSentences += QueuedSentence(generation, engine, page, segment, text, id)
                            }
                        } else engine.prefetchAll(successors.map { spokenText(it.second) })
                    }
                }
                currentClauses.drop(currentClauseIndex + 1).take(depth).forEach { successors += current to it }
                submitKnown()
                var lookPage = current
                var lookSegments = pageSegments
                var index = baseIndex + 1
                var nextLanding = landing
                var visited = 0
                while (successors.size < depth && visited++ < 100) {
                    if (index >= lookSegments.size) {
                        // Commit known requests before any potentially slow page provider call.
                        submitKnown()
                        val next = nextLanding?.landingLocation ?: lookPage.next ?: break
                        val offset = nextLanding?.landingStartCharacterOffset
                        lookPage = source.getPage(next.chapterIndex, next.pageIndex) ?: break
                        lookSegments = textExtractor.splitIntoSegments(lookPage.text, lookPage.startCharacterOffset, offset)
                            .filter { textExtractor.hasSpeakableContent(it.text) }
                        index = 0
                        nextLanding = null
                        continue
                    }
                    val candidate = lookSegments[index]
                    val prepared = if (index == lookSegments.lastIndex && candidate.canContinueAcrossPage &&
                        textExtractor.isTrailing(candidate.text)) {
                        submitKnown()
                        mergeAcrossPages(candidate.text, candidate.startCharacterOffset,
                            candidate.endCharacterOffset, generation, lookPage, recordLanding = false) ?: candidate
                    } else candidate
                    nextLanding = preparedLandings[landingKey(lookPage, candidate.startCharacterOffset)]
                    val pieces = playbackParts(prepared, duration)
                    pieces.take(depth - successors.size).forEach { successors += lookPage to it }
                    index++
                    submitKnown()
                }
            } catch (cancelled: CancellationException) { throw cancelled }
              catch (error: Throwable) {
                logTtsEvent("prefetch_failed", utteranceId = utteranceId,
                    errorType = error.javaClass.simpleName, result = "deferred_to_playback")
            }
        }
    }

    private fun resetClausePlayback() {
        utteranceRetryCount = 0
        activeSegment = null
        activeSentenceSegment = null
        playbackSentence = null
        completedUtteranceId = null
        pauseBetweenUtterances?.cancel()
        pauseBetweenUtterances = null
        clauses = emptyList()
        clauseIndex = 0
        // 不在这里清空 currentSentence：逐句朗读时清空会让悬浮字幕在每句之间闪一下“正在朗读”。
        // 会话真正结束时由 stopInternal() 清除。
    }

    private fun updateAndroidUtteranceRange(utteranceId: String, start: Int, end: Int) {
        if (activeEngine.isExternal || utteranceId != activeUtteranceId) return
        if (_playbackState.value != TtsPlaybackState.PLAYING) return
        val sentence = activeSentenceSegment ?: return
        if (rangeLoggedUtteranceId != utteranceId) {
            // One sample per sentence is enough to tell whether an engine reports ranges at all.
            rangeLoggedUtteranceId = utteranceId
            logTtsEvent("utterance_first_range", utteranceId = utteranceId,
                sentenceOffset = sentence.startCharacterOffset, characterStart = start,
                characterEnd = end, textLength = sentence.text.length)
        }
        estimatedSubtitleOffset = null
        val safeStart = start.coerceIn(0, sentence.text.length)
        val safeEnd = end.coerceIn(safeStart, sentence.text.length)
        if (safeStart == safeEnd) return
        _currentSentence.value = TtsTextSegment(
            text = sentence.text.substring(safeStart, safeEnd),
            startCharacterOffset = sentence.startCharacterOffset + safeStart,
            endCharacterOffset = sentence.startCharacterOffset + safeEnd,
            canContinueAcrossPage = false
        )
        publishSubtitle()
    }
    private suspend fun persistExternalProgress(
        utteranceId: String,
        cacheKey: String,
        pcmFrameOffset: Long
    ) {
        if (utteranceId != activeUtteranceId || !activeEngine.isExternal) return
        val page = _currentPage.value ?: return
        val segment = activeSegment ?: return
        val sentence = activeSentenceSegment ?: return
        val bookId = _activeBookId.value ?: return
        dataStoreManager.saveExternalTtsResumePosition(
            ExternalTtsResumePosition(
                bookId = bookId,
                chapterIndex = page.location.chapterIndex,
                pageIndex = page.location.pageIndex,
                characterOffset = playbackSentence?.startCharacterOffset ?: sentence.startCharacterOffset,
                spokenCharacterOffset = segment.startCharacterOffset,
                pageFingerprint = page.resumeFingerprint,
                cacheKey = cacheKey,
                pcmFrameOffset = pcmFrameOffset.coerceAtLeast(0L)
            )
        )
    }

    private suspend fun persistCurrentExternalProgress() {
        if (!activeEngine.isExternal || activeUtteranceId == null) return
        val page = _currentPage.value ?: return
        val segment = activeSegment ?: return
        val sentence = activeSentenceSegment ?: return
        val bookId = _activeBookId.value ?: return
        val cacheKey = activeEngine.cacheKey(spokenText(segment, activeEngine, sentencePauseDuration)) ?: return
        dataStoreManager.saveExternalTtsResumePosition(
            ExternalTtsResumePosition(
                bookId = bookId,
                chapterIndex = page.location.chapterIndex,
                pageIndex = page.location.pageIndex,
                characterOffset = playbackSentence?.startCharacterOffset ?: sentence.startCharacterOffset,
                spokenCharacterOffset = segment.startCharacterOffset,
                pageFingerprint = page.resumeFingerprint,
                cacheKey = cacheKey,
                pcmFrameOffset = activeEngine.currentPcmFrameOffset().coerceAtLeast(0L)
            )
        )
    }

    private suspend fun recordListeningUse(
        use: BookPositionUse,
        persistTarget: Boolean = false,
        characterOffset: Int? = null
    ) {
        val bookId = _activeBookId.value ?: return
        try {
            // Paused seeks/skips do not emit onStart. Persist the selected target immediately,
            // rather than letting the previous audible sentence become the next resume point.
            if (persistTarget) {
                val page = _currentPage.value ?: return
                val sentence = segments.getOrNull(sentenceIndex) ?: return
                dataStoreManager.saveTtsListeningPosition(TtsListeningPosition(
                    bookId, page.location.chapterIndex, page.location.pageIndex,
                    characterOffset ?: sentence.startCharacterOffset, System.currentTimeMillis()))
            }
            dataStoreManager.saveBookPositionUse(bookId, use)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            DiagnosticLoggerRegistry.logger?.log("tts", "position_use_save_failed", throwable = error)
        }
    }

    private suspend fun persistListeningPosition(force: Boolean = false, completed: Boolean = false) {
        val bookId = _activeBookId.value ?: return
        val page = _currentPage.value ?: return
        val sentence = activeSentenceSegment ?: return
        val highlighted = _currentSentence.value ?: sentence
        // A merged sentence may end on another page. Store its landing offset only after all
        // audio has rendered; otherwise the saved position would skip unheard text.
        val merge = crossPageMerge.takeIf { completed }
        val location = merge?.landingLocation ?: page.location
        val offset = merge?.landingStartCharacterOffset ?: if (completed) {
            playbackSentence?.endCharacterOffset ?: sentence.endCharacterOffset
        } else if (crossPageMerge != null) {
            // Range offsets in a merged utterance can extend beyond this source page.
            // Resume from the sentence's beginning until its landing page is confirmed.
            playbackSentence?.startCharacterOffset ?: sentence.startCharacterOffset
        } else highlighted.startCharacterOffset
        val position = TtsListeningPosition(bookId, location.chapterIndex, location.pageIndex,
            offset.coerceAtLeast(0), System.currentTimeMillis())
        val now = System.nanoTime()
        if (!force && now - lastListeningWriteNanos < 1_000_000_000L) return
        try {
            dataStoreManager.saveTtsListeningPosition(position)
            lastListeningWriteNanos = now
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            DiagnosticLoggerRegistry.logger?.log("tts", "listening_position_save_failed", throwable = error)
        }
    }

        fun setSleepTimer(minutes: Int) {
        scope.launch {
            val targetMs = minutes * 60_000L
            _sleepTimerRemainingMs.value = targetMs
            _sleepTimerDurationMs.value = targetMs
            sleepTimerJob?.cancel()
            sleepTimerJob = scope.launch {
                val startTime = System.currentTimeMillis()
                while (true) {
                    delay(1000)
                    val currentRemaining = _sleepTimerRemainingMs.value ?: break
                    if (currentRemaining <= 0) break
                    val elapsed = System.currentTimeMillis() - startTime
                    val remaining = (targetMs - elapsed).coerceAtLeast(0)
                    _sleepTimerRemainingMs.value = remaining
                    if (remaining <= 0) {
                        cancelSleepTimer()
                        stop()
                        break
                    }
                }
            }
        }
    }

    fun cancelSleepTimer() {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        _sleepTimerRemainingMs.value = null
        _sleepTimerDurationMs.value = null
    }

    private suspend fun stopInternal(
        clearExternalResume: Boolean = false,
        publishIdle: Boolean = true
    ) {
        val activeBookId = _activeBookId.value
        // Normal stop is an extra flush. Abrupt process death is covered by the writes made
        // during playback, without depending on Activity/Service destruction callbacks.
        if (activeUtteranceId != null) persistListeningPosition(force = true)
        persistCurrentExternalProgress()
        cancelSleepTimer()
        sessionGeneration++
        nextSentencePrefetchJob?.cancel()
        nextSentencePrefetchJob = null
        prefetchedPage = null
        clearPageTurnReplay()
        pageLoadToken++
        onListeningActivity(null)
        activeUtteranceId = null
        utteranceRetryCount = 0
        resetClausePlayback()
        clearQueuedSentence()
        systemTtsEngine.stop()
        externalTtsEngine.stop()
        runCatching { pageSource?.close() }
        pageSource = null
        crossPageMerge = null
        cancelPageTurnAckTimeout()
        pendingPageTurn = null
        acknowledgedPageTurn = null
        followReaderPageTurns = true
        readerAttached = true
        lastListeningWriteNanos = 0L
        segments = emptyList()
        sentenceIndex = 0
        _currentPage.value = null
        _currentSentence.value = null
        _currentUtterance.value = null
        _upcomingUtterance.value = null
        prefetchedSuccessor = null
        _listeningFrame.value = TtsListeningFrame()
        audibleUtteranceId = null
        _subtitleFrame.value = TtsSubtitleFrame()
        preparedLandings.clear()
        preparedSentences.clear()
        _chapterSnapshot.value = null
        fullscreenSession = null
        engineRollback = null
        _fullscreenVisible.value = false
        fullscreenReaderLocation = null
        _activeBookId.value = null
        com.huangder.lumibooks.data.sync.WebdavOpenBooks.listeningBookId = null
        sessionEngine = null
        pendingResume = null
        if (publishIdle) {
            _playbackState.value = TtsPlaybackState.IDLE
            logTtsEvent("state_changed", state = TtsPlaybackState.IDLE)
        }
        if (clearExternalResume && activeBookId != null) {
            dataStoreManager.clearExternalTtsResumePosition(activeBookId)
        }
    }

    fun shutdown() {
        onListeningActivity(null)
        cancelSleepTimer()
        scope.cancel()
        systemTtsEngine.shutdown()
        externalTtsEngine.shutdown()
    }

    private fun updateExternalClauseProgress(utteranceId: String, pcmFrameOffset: Long) {
        if (!activeEngine.isExternal ||
            _playbackState.value != TtsPlaybackState.PLAYING || utteranceId != activeUtteranceId) return
        val sentence = activeSentenceSegment ?: return
        // A streaming response may have written only its first buffer. Use its duration once
        // known and estimate from rendered frames until then.
        val totalFrames = activeEngine.currentPcmFrameCount().takeIf { it > 0L }
            ?: (estimatedSpeechMillis(sentence.text, 1f) * EXTERNAL_TTS_PCM_SAMPLE_RATE / 1000)
                .coerceAtLeast(1L)
        val displayClauses = textExtractor.splitIntoClauses(sentence)
        val ratio = (pcmFrameOffset.toDouble() / totalFrames.toDouble()).coerceIn(0.0, 0.999999)
        val targetOffset = sentence.startCharacterOffset + (ratio * sentence.text.length).toInt()
        val index = textExtractor.segmentIndexForOffset(displayClauses, targetOffset)
        val target = displayClauses.getOrNull(index) ?: return
        val previous = _currentSentence.value?.startCharacterOffset ?: sentence.startCharacterOffset
        if (target.startCharacterOffset <= previous) return
        _currentSentence.value = target
        publishSubtitle()
    }

    private companion object {
        const val MAX_UTTERANCE_RETRIES = 1
        const val PAGE_TURN_CALLBACK_GRACE_NANOS = 1_000_000_000L
        const val PAGE_TURN_ACK_TIMEOUT_MS = 1_200L
        const val MAX_PAGE_TURN_RETRIES = 3
    }

    private fun currentProsodySettings() = TtsProsodySettings(
        speechRate = _speechRate.value,
        speechRateMode = _speechRateMode.value,
        pitch = _pitch.value,
        pitchMode = _pitchMode.value
    )

    private fun logTtsEvent(
        event: String,
        state: TtsPlaybackState? = null,
        location: TtsPageLocation? = _currentPage.value?.location,
        callbackOrigin: TtsPageChangeOrigin? = null,
        callbackSource: String? = null,
        requestId: Long? = null,
        utteranceId: String? = null,
        sentenceOffset: Int? = null,
        characterStart: Int? = null,
        characterEnd: Int? = null,
        textLength: Int? = null,
        errorType: String? = null,
        retryAttempt: Int? = null,
        result: String? = null
    ) {
        DiagnosticLoggerRegistry.logger?.log(
            category = "tts",
            event = event,
            level = DiagnosticLevel.INFO,
            attributes = buildMap {
                put("sessionId", sessionGeneration)
                state?.let { put("state", it.name) }
                location?.let {
                    put("chapter", it.chapterIndex)
                    put("page", it.pageIndex)
                }
                callbackOrigin?.let { put("callbackOrigin", it.name) }
                callbackSource?.let { put("callbackSource", it) }
                requestId?.let { put("requestId", it) }
                utteranceId?.let { put("utteranceId", it) }
                sentenceOffset?.let { put("sentenceOffset", it) }
                characterStart?.let { put("characterStart", it) }
                characterEnd?.let { put("characterEnd", it) }
                textLength?.let { put("textLength", it) }
                errorType?.let { put("errorType", it) }
                retryAttempt?.let { put("retryAttempt", it) }
                result?.let { put("result", it) }
                put("sentenceIndex", sentenceIndex)
            },
            bookId = _activeBookId.value
        )
    }

    private fun logTtsCommand(command: String, accepted: Boolean, reason: String? = null) {
        if (!BuildConfig.DIAGNOSTIC_BUILD) return
        DiagnosticLoggerRegistry.logger?.log(
            category = "tts",
            event = "controller_command",
            level = DiagnosticLevel.INFO,
            attributes = buildMap {
                put("command", command)
                put("accepted", accepted)
                put("state", _playbackState.value.name)
                put("segmentCount", segments.size)
                reason?.let { put("reason", it) }
            },
            bookId = _activeBookId.value,
            result = if (accepted) "accepted" else "rejected"
        )
    }
}



