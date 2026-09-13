package com.huangder.lumibooks.tts

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
    private var acknowledgedPageTurn: AcknowledgedPageTurn? = null
    /**
     * When false the reader keeps its own position and playback advances on its own. Cleared by a
     * user page change, restored by a sentence seek or when the user returns to the spoken page.
     */
    private var followReaderPageTurns = true

    // Sleep timer
    private val _sleepTimerRemainingMs = MutableStateFlow<Long?>(null)
    val sleepTimerRemainingMs: StateFlow<Long?> = _sleepTimerRemainingMs.asStateFlow()
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
        startCharacterOffset: Int? = null
    ): Result<Unit> = withContext(Dispatchers.Main.immediate) {
        commandMutex.withLock {
        stopInternal()
        val generation = ++sessionGeneration
        val selection = dataStoreManager.ttsProviderSelection.first()
        val selectedEngine = selectPlaybackEngine(selection)
        if (selectedEngine.isFailure) {
            source.close()
            return@withContext Result.failure(checkNotNull(selectedEngine.exceptionOrNull()))
        }
        sessionEngine = selectedEngine.getOrThrow()
        _usesAndroidTts.value = !activeEngine.isExternal
        pageSource = source
        _activeBookId.value = bookId
        _playbackState.value = TtsPlaybackState.INITIALIZING
        logTtsEvent("session_start", state = TtsPlaybackState.INITIALIZING)

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

        val storedResume = if (engine.isExternal) {
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
            savedPage?.resumeFingerprint == fingerprint
        }
        if (storedResume != null && resume == null) {
            dataStoreManager.clearExternalTtsResumePosition(bookId)
        }
        if (generation != sessionGeneration) return@withContext Result.success(Unit)
        pendingResume = resume
        _playbackState.value = TtsPlaybackState.PLAYING
        logTtsEvent("state_changed", state = TtsPlaybackState.PLAYING)
        val requestedOffset = resume?.characterOffset ?: startCharacterOffset
        val locatedPage = requestedOffset
            ?.takeIf { resume == null }
            ?.let { offset -> locatePageWithFallback(startChapter, offset) }
        val location = resume?.let { TtsPageLocation(it.chapterIndex, it.pageIndex) }
            ?: locatedPage?.location
            ?: TtsPageLocation(startChapter, startPage)
        followReaderPageTurns = true
        val moved = moveToPage(
            location = location,
            startAtEnd = false,
            publishLocation = true,
            startCharacterOffset = resume?.characterOffset,
            seekCharacterOffset = requestedOffset?.takeIf { resume == null && locatedPage != null }
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
            if (_playbackState.value != TtsPlaybackState.PLAYING) return@withLock
            val engine = activeEngine
            if (!engine.isExternal) activeUtteranceId = null
            engine.pause()
            _playbackState.value = TtsPlaybackState.PAUSED
            logTtsEvent("state_changed", state = TtsPlaybackState.PAUSED)
            }
        }
    }

    fun resume() {
        scope.launch {
            commandMutex.withLock {
            if (_playbackState.value != TtsPlaybackState.PAUSED || segments.isEmpty()) return@withLock
            _playbackState.value = TtsPlaybackState.PLAYING
            logTtsEvent("state_changed", state = TtsPlaybackState.PLAYING)
            if (!activeEngine.resume()) speakCurrentSegment()
            }
        }
    }

    fun stop() {
        scope.launch { commandMutex.withLock { stopInternal() } }
    }

    fun skip(forward: Boolean = true) {
        scope.launch {
            commandMutex.withLock {
            val state = _playbackState.value
            if (state != TtsPlaybackState.PLAYING && state != TtsPlaybackState.PAUSED) return@withLock
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
                    followReaderPageTurns = _currentPage.value?.location == location
                }
                return@withLock
            }
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
            request.location == _currentPage.value?.location &&
            _playbackState.value != TtsPlaybackState.IDLE

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
        activeUtteranceId = null
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
                startCharacterOffset = merge.landingStartCharacterOffset
            )
            return
        }

        moveToAdjacentPage(forward = true)
    }

    private suspend fun handleUtteranceError(utteranceId: String?, throwable: Throwable) {
        if (utteranceId == null || utteranceId != activeUtteranceId) return
        _errors.tryEmit(throwable)
        stopInternal()
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
        if (!moveToPage(target, startAtEnd = !forward, publishLocation = true)) {
            pageContentError?.let(_errors::tryEmit)
            stopInternal()
        }
    }

    private suspend fun moveToPage(
        location: TtsPageLocation,
        startAtEnd: Boolean,
        publishLocation: Boolean,
        startCharacterOffset: Int? = null,
        seekCharacterOffset: Int? = null
    ): Boolean {
        val source = pageSource ?: return false
        val token = ++pageLoadToken
        pageContentError = null
        activeUtteranceId = null
        resetClausePlayback()
        activeEngine.stop()
        crossPageMerge = null
        // Any new playback decision supersedes a turn we were still waiting for.
        pendingPageTurn = null

        var target = location
        var selectedPage: TtsPageContent? = null
        var selectedSegments: List<TtsTextSegment> = emptyList()
        var attempts = 0
        while (attempts < 50 && selectedPage == null) {
            val page = try {
                source.getPage(target.chapterIndex, target.pageIndex)
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
            )
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
        val sourceLocation = _currentPage.value?.location
        _currentPage.value = page
        segments = selectedSegments
        sentenceIndex = when {
            startAtEnd -> segments.lastIndex
            seekCharacterOffset != null ->
                textExtractor.segmentIndexForOffset(segments, seekCharacterOffset).coerceAtLeast(0)
            else -> 0
        }
        if (publishLocation && followReaderPageTurns) {
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
            if (waitForAcknowledgement) return true
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
        generation: Long
    ): TtsTextSegment? {
        val source = pageSource ?: return null
        val maxLength = TtsTextExtractor.MAX_SENTENCE_LENGTH
        var mergedText = trailingText
        var lastPage = _currentPage.value ?: return null
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
        crossPageMerge = CrossPageMergeState(
            landingLocation = landingLocation,
            landingStartCharacterOffset = landingStartOffset
        )
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
        val result = activeEngine.speak(
            text = segment.text,
            utteranceId = utteranceId,
            cacheKey = resume?.cacheKey,
            startFrame = resume?.pcmFrameOffset ?: 0L
        )
        if (generation != sessionGeneration || activeUtteranceId != utteranceId) return
        if (result.isFailure && activeUtteranceId == utteranceId) {
            activeUtteranceId = null
            activeSegment = null
            _currentSentence.value = null
            _errors.tryEmit(result.exceptionOrNull())
            stopInternal()
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
            nextPrefetchText()?.let { activeEngine.prefetch(it) }
        }
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

    private fun nextPrefetchText(): String? {
        if (activeEngine.isExternal) {
            return segments.getOrNull(sentenceIndex + 1)?.text
        }
        clauses.getOrNull(clauseIndex + 1)?.let { return it.text }
        return segments.getOrNull(sentenceIndex + 1)
            ?.let { textExtractor.splitIntoClauses(it) }
            ?.firstOrNull()
            ?.text
    }

    private fun resetClausePlayback() {
        activeSegment = null
        activeSentenceSegment = null
        playbackSentence = null
        clauses = emptyList()
        clauseIndex = 0
        _currentSentence.value = null
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

        fun setSleepTimer(minutes: Int) {
        scope.launch {
            val targetMs = minutes * 60_000L
            _sleepTimerRemainingMs.value = targetMs
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
    }

    private suspend fun stopInternal(clearExternalResume: Boolean = false) {
        val activeBookId = _activeBookId.value
        persistCurrentExternalProgress()
        cancelSleepTimer()
        sessionGeneration++
        clearPageTurnReplay()
        pageLoadToken++
        activeUtteranceId = null
        resetClausePlayback()
        systemTtsEngine.stop()
        externalTtsEngine.stop()
        runCatching { pageSource?.close() }
        pageSource = null
        crossPageMerge = null
        pendingPageTurn = null
        acknowledgedPageTurn = null
        followReaderPageTurns = true
        segments = emptyList()
        sentenceIndex = 0
        _currentPage.value = null
        _activeBookId.value = null
        sessionEngine = null
        pendingResume = null
        _playbackState.value = TtsPlaybackState.IDLE
        logTtsEvent("state_changed", state = TtsPlaybackState.IDLE)
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
        const val PAGE_TURN_CALLBACK_GRACE_NANOS = 1_000_000_000L
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
        sentenceOffset: Int? = null
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
                put("sentenceIndex", sentenceIndex)
            },
            bookId = _activeBookId.value
        )
    }
}



