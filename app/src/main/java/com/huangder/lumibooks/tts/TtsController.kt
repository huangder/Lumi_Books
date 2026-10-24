package com.huangder.lumibooks.tts

import com.huangder.lumibooks.BuildConfig
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import com.huangder.lumibooks.util.diagnostics.DiagnosticLevel
import com.huangder.lumibooks.util.diagnostics.DiagnosticLoggerRegistry

class TtsController(
    private val systemTtsEngine: TtsPlaybackEngine,
    private val externalTtsEngine: TtsPlaybackEngine,
    private val textExtractor: TtsTextExtractor,
    private val dataStoreManager: TtsSettingsStore
) {
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
    private var activeUtteranceId: String? = null
    /** Number of recovery attempts for the currently spoken sentence. */
    private var utteranceRetryCount = 0
    private var nextSentencePrefetchJob: Job? = null
    private var prefetchedPage: TtsPageContent? = null
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
    private val _followUserPageTurns = MutableStateFlow(false)
    val followUserPageTurns: StateFlow<Boolean> = _followUserPageTurns.asStateFlow()
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
        val resumeWhenAcknowledged: Boolean
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
        override fun onStart(utteranceId: String) {
            scope.launch {
                commandMutex.withLock {
                    if (engine !== activeEngine || utteranceId != activeUtteranceId) return@withLock
                    logTtsEvent(
                        event = "utterance_started",
                        utteranceId = utteranceId,
                        callbackSource = callbackSource
                    )
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
        _usesAndroidTts.value = !activeEngine.isExternal
        pageSource = source
        _activeBookId.value = bookId
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
            if (!engine.canResumeWithoutRestart) activeUtteranceId = null
            engine.pause()
            persistListeningPosition(force = true)
            _playbackState.value = TtsPlaybackState.PAUSED
            logTtsEvent("state_changed", state = TtsPlaybackState.PAUSED)
            }
        }
    }

    fun resume() {
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
            if (!activeEngine.resume()) {
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

    fun skip(forward: Boolean = true) {
        scope.launch {
            commandMutex.withLock {
            val state = _playbackState.value
            val command = if (forward) "NEXT" else "PREVIOUS"
            if (state != TtsPlaybackState.PLAYING && state != TtsPlaybackState.PAUSED) {
                logTtsCommand(command, accepted = false, reason = "state_not_active")
                return@withLock
            }
            logTtsCommand(command, accepted = true)
            activeUtteranceId = null
            resetClausePlayback()
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
            }
        }
    }

    /** Detach visual following while the reader is hidden or awaiting a resume choice. */
    fun setReaderAttached(bookId: String, attached: Boolean) {
        scope.launch {
            commandMutex.withLock {
                if (_activeBookId.value != bookId) return@withLock
                readerAttached = attached
                if (attached) {
                    followReaderPageTurns = true
                    return@withLock
                }
                val pending = pendingPageTurn
                cancelPageTurnAckTimeout()
                pendingPageTurn = null
                clearPageTurnReplay()
                acknowledgedPageTurn = null
                pageTurnSequence++
                if (pending?.resumeWhenAcknowledged == true &&
                    _playbackState.value == TtsPlaybackState.PLAYING && activeUtteranceId == null
                ) speakCurrentSegment()
            }
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
            if (_activeBookId.value != bookId) return@withLock
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
                // The reader moved elsewhere while playback was waiting for it. Never block playback.
                cancelPageTurnAckTimeout()
                pendingPageTurn = null
                val canContinue = pending.resumeWhenAcknowledged &&
                    _playbackState.value == TtsPlaybackState.PLAYING &&
                    activeUtteranceId == null
                followReaderPageTurns = false
                if (canContinue) speakCurrentSegment()
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
            // Only the reader confirming a page turn requested by playback may move playback.
            // Manual page turns and layout callbacks leave the listening position untouched.
            if (origin != TtsPageChangeOrigin.TTS_FOLLOW) {
                if (origin == TtsPageChangeOrigin.USER) {
                    if (_followUserPageTurns.value && location != _currentPage.value?.location) {
                        followReaderPageTurns = true
                        if (!moveToPage(location, startAtEnd = false, publishLocation = false)) {
                            _errors.tryEmit(pageContentError ?: IllegalStateException("No readable text found"))
                            stopInternal()
                        }
                        return@withLock
                    }
                    // Passing the spoken page while browsing must not re-enable following.
                    followReaderPageTurns = followReaderPageTurns && _currentPage.value?.location == location
                }
                return@withLock
            }
            if (!readerAttached || !followReaderPageTurns) return@withLock
            if (_currentPage.value?.location == location) return@withLock
            if (crossPageMerge?.landingLocation == location) return@withLock
            if (!moveToPage(location, startAtEnd = false, publishLocation = false)) {
                _errors.tryEmit(pageContentError ?: IllegalStateException("No readable text found"))
                stopInternal()
            }
            }
        }
    }

    /**
     * Jumps playback to [characterOffset] inside [chapterIndex] without moving the reading page.
     * Used by the double-tap gesture on a sentence.
     */
    fun seekTo(chapterIndex: Int, characterOffset: Int) {
        scope.launch {
            commandMutex.withLock {
                val state = _playbackState.value
                if (state != TtsPlaybackState.PLAYING && state != TtsPlaybackState.PAUSED) {
                    return@withLock
                }
                if (pageSource == null) return@withLock
                val target = locatePageWithFallback(chapterIndex, characterOffset)
                    ?: return@withLock
                followReaderPageTurns = true
                if (state == TtsPlaybackState.PAUSED) {
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
                        seekCharacterOffset = characterOffset
                    )
                ) {
                    pageContentError?.let(_errors::tryEmit)
                    stopInternal()
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
            readerAttached && followReaderPageTurns &&
            request.location == _currentPage.value?.location &&
            _playbackState.value != TtsPlaybackState.IDLE

    fun setFollowUserPageTurns(enabled: Boolean) {
        _followUserPageTurns.value = enabled
        if (enabled) followReaderPageTurns = true
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

    /**
     * 阅读页可能已经不在（应用退到后台被回收、阅读器已关闭）或来不及翻页，
     * 此时必须放弃等待确认继续朗读，否则会读完一页后长时间停住。
     */
    private fun schedulePageTurnAckTimeout(request: TtsPageTurnRequest) {
        cancelPageTurnAckTimeout()
        pendingPageTurnTimeoutJob = scope.launch {
            delay(PAGE_TURN_ACK_TIMEOUT_MS)
            commandMutex.withLock {
                val pending = pendingPageTurn ?: return@withLock
                if (pending.request.requestId != request.requestId) return@withLock
                pendingPageTurn = null
                logTtsEvent(
                    event = "page_turn_ack_timeout",
                    location = pending.request.location,
                    requestId = request.requestId
                )
                if (pending.resumeWhenAcknowledged &&
                    _playbackState.value == TtsPlaybackState.PLAYING &&
                    activeUtteranceId == null
                ) {
                    speakCurrentSegment()
                }
            }
        }
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
        if (activeEngine.isExternal || segments.isEmpty()) return
        val state = _playbackState.value
        if (state != TtsPlaybackState.PLAYING && state != TtsPlaybackState.PAUSED) return
        activeUtteranceId = null
        activeSegment = null
        activeSentenceSegment = null
        _currentSentence.value = null
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
        if (_playbackState.value != TtsPlaybackState.PLAYING) return
        persistListeningPosition(force = true, completed = true)
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
        if (utteranceId == null || utteranceId != activeUtteranceId) return
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
            activeUtteranceId = null
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
        activeUtteranceId = null
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
        preservePreparedAudio: Boolean = false
    ): Boolean {
        val source = pageSource ?: return false
        val token = ++pageLoadToken
        pageContentError = null
        activeUtteranceId = null
        utteranceRetryCount = 0
        resetClausePlayback()
        if (preservePreparedAudio) activeEngine.stopForPageTurn() else activeEngine.stop()
        crossPageMerge = null
        // Any new playback decision supersedes a turn we were still waiting for.
        cancelPageTurnAckTimeout()
        pendingPageTurn = null

        var target = location
        var selectedPage: TtsPageContent? = null
        var selectedSegments: List<TtsTextSegment> = emptyList()
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
            val pageSegments = textExtractor.splitIntoSegments(
                text = page.text,
                baseCharacterOffset = page.startCharacterOffset,
                startCharacterOffset = startCharacterOffset.takeIf { page.location == location }
            ).filter { segment -> textExtractor.hasSpeakableContent(segment.text) }
            if (pageSegments.isNotEmpty()) {
                selectedPage = page
                selectedSegments = pageSegments
            } else {
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
        if (publishLocation && followReaderPageTurns && readerAttached) {
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
            val waitForAcknowledgement = sourceLocation != null && sourceLocation != page.location
            pendingPageTurn = if (waitForAcknowledgement) {
                PendingPageTurn(request, sourceLocation, resumeWhenAcknowledged = true)
            } else {
                null
            }
            _pageTurnRequests.emit(request)
            if (waitForAcknowledgement) {
                schedulePageTurnAckTimeout(request)
                return true
            }
        }
        if (_playbackState.value == TtsPlaybackState.PLAYING) speakCurrentSegment()
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
        val source = pageSource ?: return null
        val maxLength = TtsTextExtractor.MAX_SENTENCE_LENGTH
        var mergedText = trailingText
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
            if (endedAtSentenceBoundary) break
        }

        val endedAtSentenceBoundary = mergedText.trimEnd().lastOrNull()?.let { last ->
            last in TtsTextExtractor.TERMINATORS || last in TtsTextExtractor.CLOSING_PUNCTUATION
        } == true
        if (mergedText == trailingText || !endedAtSentenceBoundary) return null
        if (generation != sessionGeneration) return null
        if (recordLanding) {
            crossPageMerge = CrossPageMergeState(
                landingLocation = landingLocation,
                landingStartCharacterOffset = landingStartOffset
            )
        }
        return TtsTextSegment(
            text = mergedText,
            startCharacterOffset = trailingStartOffset,
            endCharacterOffset = trailingEndOffset
        )
    }
    private suspend fun speakCurrentSegment() {
        val generation = sessionGeneration
        val page = _currentPage.value ?: return
        val sentence = prepareCurrentSentence(page, generation) ?: return
        val clause = clauses.getOrNull(clauseIndex) ?: return
        val segment = sentence
        if (generation != sessionGeneration ||
            _currentPage.value?.location != page.location ||
            _playbackState.value != TtsPlaybackState.PLAYING
        ) return
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
            append("_c")
            append(clauseIndex)
        }
        activeUtteranceId = utteranceId
        activeSegment = segment
        activeSentenceSegment = sentence
        _currentUtterance.value = segment
        _currentSentence.value = if (activeEngine.isExternal) clause else sentence
        logTtsEvent(
            event = "utterance_requested",
            location = page.location,
            utteranceId = utteranceId
        )
        val resume = pendingResume?.takeIf {
            it.chapterIndex == page.location.chapterIndex &&
                it.pageIndex == page.location.pageIndex &&
                it.characterOffset == sentence.startCharacterOffset &&
                it.clauseIndex == clauseIndex
        }
        pendingResume = null
        val spokenText = if (activeEngine.isExternal) {
            segment.text
        } else {
            textExtractor.playbackText(segment.text)
        }
        val result = activeEngine.speak(
            text = spokenText,
            utteranceId = utteranceId,
            cacheKey = resume?.cacheKey,
            startFrame = resume?.pcmFrameOffset ?: 0L
        )
        if (generation != sessionGeneration || activeUtteranceId != utteranceId) return
        if (result.isFailure && activeUtteranceId == utteranceId) {
            recoverOrPauseAfterPlaybackError(
                utteranceId,
                result.exceptionOrNull() ?: SystemTtsException.Playback()
            )
            return
        }
        if (activeEngine.isExternal) {
            val cacheKey = activeEngine.cacheKey(segment.text)
            dataStoreManager.saveExternalTtsResumePosition(
                ExternalTtsResumePosition(
                    bookId = _activeBookId.value ?: return,
                    chapterIndex = page.location.chapterIndex,
                    pageIndex = page.location.pageIndex,
                    characterOffset = sentence.startCharacterOffset,
                    clauseIndex = clauseIndex,
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

        val preparedClauses = textExtractor.splitIntoClauses(sentence)
        if (preparedClauses.isEmpty()) return null
        playbackSentence = sentence
        clauses = preparedClauses
        clauseIndex = pendingResume
            ?.takeIf {
                it.chapterIndex == page.location.chapterIndex &&
                    it.pageIndex == page.location.pageIndex &&
                    it.characterOffset == sentence.startCharacterOffset &&
                    it.clauseIndex in preparedClauses.indices
            }
            ?.clauseIndex
            ?: 0
        return sentence
    }

    private fun scheduleNextSentencePrefetch(utteranceId: String) {
        nextSentencePrefetchJob?.cancel()
        val generation = sessionGeneration
        val engine = activeEngine
        val source = pageSource ?: return
        val current = _currentPage.value ?: return
        val nextSegment = segments.getOrNull(sentenceIndex + 1)
        val landing = crossPageMerge
        nextSentencePrefetchJob = scope.launch {
            try {
                var page = current
                var segment = nextSegment
                var location = landing?.landingLocation ?: current.next
                var offset = landing?.landingStartCharacterOffset
                var attempts = 0
                while (segment == null && location != null && attempts++ < 50) {
                    page = source.getPage(location.chapterIndex, location.pageIndex) ?: return@launch
                    segment = textExtractor.splitIntoSegments(page.text, page.startCharacterOffset, offset)
                        .firstOrNull { textExtractor.hasSpeakableContent(it.text) }
                    location = page.next
                    offset = null
                }
                val candidate = segment ?: return@launch
                val prepared = if (candidate.canContinueAcrossPage && textExtractor.isTrailing(candidate.text)) {
                    mergeAcrossPages(candidate.text, candidate.startCharacterOffset,
                        candidate.endCharacterOffset, generation, page, recordLanding = false) ?: candidate
                } else candidate
                val text = if (engine.isExternal) prepared.text else textExtractor.playbackText(prepared.text)
                commandMutex.withLock {
                    if (generation == sessionGeneration && utteranceId == activeUtteranceId && engine === activeEngine) {
                        if (page.location != current.location) prefetchedPage = page
                        engine.prefetch(text)
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
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
        clauses = emptyList()
        clauseIndex = 0
        // 不在这里清空 currentSentence：逐句朗读时清空会让悬浮字幕在每句之间闪一下“正在朗读”。
        // 会话真正结束时由 stopInternal() 清除。
    }

    private fun updateAndroidUtteranceRange(utteranceId: String, start: Int, end: Int) {
        if (activeEngine.isExternal || utteranceId != activeUtteranceId) return
        if (_playbackState.value != TtsPlaybackState.PLAYING) return
        val sentence = activeSentenceSegment ?: return
        val safeStart = start.coerceIn(0, sentence.text.length)
        val safeEnd = end.coerceIn(safeStart, sentence.text.length)
        if (safeStart == safeEnd) return
        _currentSentence.value = TtsTextSegment(
            text = sentence.text.substring(safeStart, safeEnd),
            startCharacterOffset = sentence.startCharacterOffset + safeStart,
            endCharacterOffset = sentence.startCharacterOffset + safeEnd,
            canContinueAcrossPage = false
        )
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
                characterOffset = sentence.startCharacterOffset,
                clauseIndex = clauseIndex,
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
        val cacheKey = activeEngine.cacheKey(segment.text) ?: return
        dataStoreManager.saveExternalTtsResumePosition(
            ExternalTtsResumePosition(
                bookId = bookId,
                chapterIndex = page.location.chapterIndex,
                pageIndex = page.location.pageIndex,
                characterOffset = sentence.startCharacterOffset,
                clauseIndex = clauseIndex,
                pageFingerprint = page.resumeFingerprint,
                cacheKey = cacheKey,
                pcmFrameOffset = activeEngine.currentPcmFrameOffset().coerceAtLeast(0L)
            )
        )
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
            sentence.endCharacterOffset
        } else if (crossPageMerge != null) {
            // Range offsets in a merged utterance can extend beyond this source page.
            // Resume from the sentence's beginning until its landing page is confirmed.
            sentence.startCharacterOffset
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
        activeUtteranceId = null
        utteranceRetryCount = 0
        resetClausePlayback()
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
        _activeBookId.value = null
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
        cancelSleepTimer()
        scope.cancel()
        systemTtsEngine.shutdown()
        externalTtsEngine.shutdown()
    }

    private fun updateExternalClauseProgress(utteranceId: String, pcmFrameOffset: Long) {
        if (!activeEngine.isExternal || utteranceId != activeUtteranceId) return
        val sentence = activeSentenceSegment ?: return
        val totalFrames = activeEngine.currentPcmFrameCount()
        if (totalFrames <= 0L || clauses.size <= 1) return
        val ratio = (pcmFrameOffset.toDouble() / totalFrames.toDouble()).coerceIn(0.0, 0.999999)
        val targetOffset = (ratio * sentence.text.length).toInt()
        val targetClause = clauses.indexOfLast { it.startCharacterOffset - sentence.startCharacterOffset <= targetOffset }
            .coerceIn(0, clauses.lastIndex)
        if (targetClause == clauseIndex) return
        clauseIndex = targetClause
        _currentSentence.value = clauses[targetClause]
    }

    private companion object {
        const val MAX_UTTERANCE_RETRIES = 1
        const val PAGE_TURN_CALLBACK_GRACE_NANOS = 1_000_000_000L
        const val PAGE_TURN_ACK_TIMEOUT_MS = 1_200L
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



