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
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TtsController(
    private val systemTtsEngine: TtsPlaybackEngine,
    private val externalTtsEngine: TtsPlaybackEngine,
    private val textExtractor: TtsTextExtractor,
    private val dataStoreManager: TtsSettingsStore
) {
    private val configuredEngine: TtsPlaybackEngine
        get() = if (externalTtsEnabled) externalTtsEngine else systemTtsEngine
    private var sessionEngine: TtsPlaybackEngine? = null
    private val activeEngine: TtsPlaybackEngine
        get() = sessionEngine ?: configuredEngine
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _playbackState = MutableStateFlow(TtsPlaybackState.IDLE)
    val playbackState: StateFlow<TtsPlaybackState> = _playbackState.asStateFlow()

    private val _speechRate = MutableStateFlow(1f)
    val speechRate: StateFlow<Float> = _speechRate.asStateFlow()

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
    private var activeUtteranceId: String? = null
    private var sessionGeneration = 0L
    private var utteranceSequence = 0L
    private var pageLoadToken = 0L
    private var externalTtsEnabled = false
    private var pageContentError: Throwable? = null
    private var pendingResume: ExternalTtsResumePosition? = null
    private var crossPageMerge: CrossPageMergeState? = null
    private var activeSegment: TtsTextSegment? = null
    private var pageTurnSequence = 0L
    private var pendingPageTurn: PendingPageTurn? = null
    private var acknowledgedPageTurn: AcknowledgedPageTurn? = null

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
        val sourceLocation: TtsPageLocation?
    )

    private data class AcknowledgedPageTurn(
        val sourceLocation: TtsPageLocation?,
        val targetLocation: TtsPageLocation,
        val acknowledgedAtNanos: Long
    )

    init {
        val listener = object : TtsPlaybackListener {
            override fun onStart(utteranceId: String) = Unit

            override fun onDone(utteranceId: String) {
                scope.launch { handleUtteranceDone(utteranceId) }
            }

            override fun onError(utteranceId: String, throwable: Throwable) {
                scope.launch { handleUtteranceError(utteranceId, throwable) }
            }

            override fun onProgress(utteranceId: String, cacheKey: String, pcmFrameOffset: Long) {
                scope.launch { persistExternalProgress(utteranceId, cacheKey, pcmFrameOffset) }
            }

            override fun onPlaybackInterrupted() {
                scope.launch {
                    if (_playbackState.value == TtsPlaybackState.PLAYING) {
                        _playbackState.value = TtsPlaybackState.PAUSED
                    }
                }
            }
        }
        systemTtsEngine.setListener(listener)
        externalTtsEngine.setListener(listener)

        scope.launch {
            dataStoreManager.ttsSpeechRate.collectLatest { rate ->
                _speechRate.value = rate.coerceIn(0.5f, 2f)
                systemTtsEngine.setSpeechRate(_speechRate.value)
                externalTtsEngine.setSpeechRate(_speechRate.value)
            }
        }
        scope.launch {
            dataStoreManager.ttsPitch.collectLatest { pitch ->
                systemTtsEngine.setPitch(pitch.coerceIn(0.5f, 2f))
                externalTtsEngine.setPitch(pitch.coerceIn(0.5f, 2f))
            }
        }
        scope.launch {
            dataStoreManager.externalTtsSettings.collectLatest { settings ->
                externalTtsEnabled = settings.enabled &&
                    settings.consentVersion >= ExternalTtsConfig.CONSENT_VERSION
            }
        }
    }

    suspend fun start(
        bookId: String,
        source: TtsPageSource,
        startChapter: Int,
        startPage: Int
    ): Result<Unit> = withContext(Dispatchers.Main.immediate) {
        stopInternal()
        val generation = ++sessionGeneration
        val settings = dataStoreManager.externalTtsSettings.first()
        externalTtsEnabled = settings.enabled &&
            settings.consentVersion >= ExternalTtsConfig.CONSENT_VERSION
        sessionEngine = configuredEngine
        pageSource = source
        _activeBookId.value = bookId
        _playbackState.value = TtsPlaybackState.INITIALIZING

        val engine = activeEngine
        engine.setSpeechRate(_speechRate.value)
        val initializeResult = engine.initialize()
        if (generation != sessionGeneration) return@withContext Result.success(Unit)
        if (initializeResult.isFailure) {
            stopInternal()
            return@withContext initializeResult
        }

        val storedResume = if (externalTtsEnabled) {
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
        val location = resume?.let { TtsPageLocation(it.chapterIndex, it.pageIndex) }
            ?: TtsPageLocation(startChapter, startPage)
        val moved = moveToPage(
            location = location,
            startAtEnd = false,
            publishLocation = true,
            startCharacterOffset = resume?.characterOffset
        )
        if (generation != sessionGeneration) return@withContext Result.success(Unit)
        if (!moved) {
            val error = pageContentError ?: IllegalStateException("No readable text found")
            stopInternal()
            return@withContext Result.failure(error)
        }
        Result.success(Unit)
    }

    fun pause() {
        scope.launch {
            if (_playbackState.value != TtsPlaybackState.PLAYING) return@launch
            val engine = activeEngine
            if (!engine.isExternal) activeUtteranceId = null
            engine.pause()
            _playbackState.value = TtsPlaybackState.PAUSED
        }
    }

    fun resume() {
        scope.launch {
            if (_playbackState.value != TtsPlaybackState.PAUSED || segments.isEmpty()) return@launch
            _playbackState.value = TtsPlaybackState.PLAYING
            if (!activeEngine.resume()) speakCurrentSegment()
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
            activeSegment = null
            _currentSentence.value = null
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

    fun onPageVisible(bookId: String, chapterIndex: Int, pageIndex: Int) {
        scope.launch {
            if (_activeBookId.value != bookId) return@launch
            val state = _playbackState.value
            if (state != TtsPlaybackState.PLAYING && state != TtsPlaybackState.PAUSED) return@launch
            val location = TtsPageLocation(chapterIndex, pageIndex)
            val pending = pendingPageTurn
            if (pending != null) {
                if (pending.request.sessionId != sessionGeneration) return@launch
                if (pending.request.location == location) acknowledgePendingPageTurn(pending)
                return@launch
            }
            val acknowledged = acknowledgedPageTurn
            if (acknowledged != null) {
                val withinAnimationWindow = System.nanoTime() - acknowledged.acknowledgedAtNanos <=
                    PAGE_TURN_CALLBACK_GRACE_NANOS
                if (withinAnimationWindow &&
                    (location == acknowledged.sourceLocation ||
                        location == acknowledged.targetLocation)
                ) return@launch
                acknowledgedPageTurn = null
            }
            if (_currentPage.value?.location == location) return@launch
            if (crossPageMerge?.landingLocation == location) return@launch
            if (!moveToPage(location, startAtEnd = false, publishLocation = false)) {
                _errors.tryEmit(pageContentError ?: IllegalStateException("No readable text found"))
                stopInternal()
            }
        }
    }

    fun isPageTurnRequestActive(request: TtsPageTurnRequest): Boolean =
        request.bookId == _activeBookId.value &&
            request.sessionId == sessionGeneration &&
            request.location == _currentPage.value?.location &&
            _playbackState.value != TtsPlaybackState.IDLE

    fun acknowledgePageTurnRequest(request: TtsPageTurnRequest) {
        scope.launch {
            if (!isPageTurnRequestActive(request)) return@launch
            val pending = pendingPageTurn ?: return@launch
            if (pending.request.sessionId == request.sessionId &&
                pending.request.requestId == request.requestId &&
                pending.request.location == request.location
            ) {
                acknowledgePendingPageTurn(pending)
            }
        }
    }

    private fun acknowledgePendingPageTurn(pending: PendingPageTurn) {
        acknowledgedPageTurn = AcknowledgedPageTurn(
            sourceLocation = pending.sourceLocation,
            targetLocation = pending.request.location,
            acknowledgedAtNanos = System.nanoTime()
        )
        pendingPageTurn = null
    }

    suspend fun setSpeechRate(rate: Float) = withContext(Dispatchers.Main.immediate) {
        val safeRate = rate.coerceIn(0.5f, 2f)
        _speechRate.value = safeRate
        dataStoreManager.saveTtsSpeechRate(safeRate)
        activeEngine.setSpeechRate(safeRate)
        if (
            !activeEngine.isExternal &&
            _playbackState.value == TtsPlaybackState.PLAYING &&
            segments.isNotEmpty()
        ) {
            activeUtteranceId = null
            activeSegment = null
            _currentSentence.value = null
            activeEngine.stop()
            speakCurrentSegment()
        }
    }

    suspend fun setPitch(pitch: Float) = withContext(Dispatchers.Main.immediate) {
        val safePitch = pitch.coerceIn(0.5f, 2f)
        dataStoreManager.saveTtsPitch(safePitch)
        systemTtsEngine.setPitch(safePitch)
        externalTtsEngine.setPitch(safePitch)
    }

    private suspend fun handleUtteranceDone(utteranceId: String?) {
        if (utteranceId == null || utteranceId != activeUtteranceId) return
        if (_playbackState.value != TtsPlaybackState.PLAYING) return
        activeUtteranceId = null
        activeSegment = null
        pendingResume = null
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
        startCharacterOffset: Int? = null
    ): Boolean {
        val source = pageSource ?: return false
        val token = ++pageLoadToken
        pageContentError = null
        activeUtteranceId = null
        activeSegment = null
        _currentSentence.value = null
        activeEngine.stop()
        crossPageMerge = null

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
        sentenceIndex = if (startAtEnd) segments.lastIndex else 0
        if (publishLocation) {
            val request = TtsPageTurnRequest(
                bookId = _activeBookId.value ?: return false,
                sessionId = sessionGeneration,
                requestId = ++pageTurnSequence,
                location = page.location
            )
            pendingPageTurn = PendingPageTurn(request, sourceLocation)
            _pageTurnRequests.emit(request)
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
        var segment = segments.getOrNull(sentenceIndex) ?: return

        if (
            crossPageMerge == null &&
            sentenceIndex == segments.lastIndex &&
            segment.canContinueAcrossPage &&
            textExtractor.isTrailing(segment.text)
        ) {
            val merged = mergeAcrossPages(
                segment.text,
                segment.startCharacterOffset,
                segment.endCharacterOffset,
                generation
            )
            if (merged != null) segment = merged
        }
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
        }
        activeUtteranceId = utteranceId
        activeSegment = segment
        _currentSentence.value = segment
        val resume = pendingResume?.takeIf {
            it.chapterIndex == page.location.chapterIndex &&
                it.pageIndex == page.location.pageIndex &&
                it.characterOffset == segment.startCharacterOffset
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
                    characterOffset = segment.startCharacterOffset,
                    pageFingerprint = page.resumeFingerprint,
                    cacheKey = cacheKey,
                    pcmFrameOffset = resume?.pcmFrameOffset ?: 0L
                )
            )
            segments.getOrNull(sentenceIndex + 1)?.let { activeEngine.prefetch(it.text) }
        }
    }
    private suspend fun persistExternalProgress(
        utteranceId: String,
        cacheKey: String,
        pcmFrameOffset: Long
    ) {
        if (utteranceId != activeUtteranceId || !activeEngine.isExternal) return
        val page = _currentPage.value ?: return
        val segment = activeSegment ?: segments.getOrNull(sentenceIndex) ?: return
        val bookId = _activeBookId.value ?: return
        dataStoreManager.saveExternalTtsResumePosition(
            ExternalTtsResumePosition(
                bookId = bookId,
                chapterIndex = page.location.chapterIndex,
                pageIndex = page.location.pageIndex,
                characterOffset = segment.startCharacterOffset,
                pageFingerprint = page.resumeFingerprint,
                cacheKey = cacheKey,
                pcmFrameOffset = pcmFrameOffset.coerceAtLeast(0L)
            )
        )
    }

    private suspend fun persistCurrentExternalProgress() {
        if (!activeEngine.isExternal || activeUtteranceId == null) return
        val page = _currentPage.value ?: return
        val segment = activeSegment ?: segments.getOrNull(sentenceIndex) ?: return
        val bookId = _activeBookId.value ?: return
        val cacheKey = activeEngine.cacheKey(segment.text) ?: return
        dataStoreManager.saveExternalTtsResumePosition(
            ExternalTtsResumePosition(
                bookId = bookId,
                chapterIndex = page.location.chapterIndex,
                pageIndex = page.location.pageIndex,
                characterOffset = segment.startCharacterOffset,
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
        activeSegment = null
        _currentSentence.value = null
        systemTtsEngine.stop()
        externalTtsEngine.stop()
        runCatching { pageSource?.close() }
        pageSource = null
        pendingPageTurn = null
        acknowledgedPageTurn = null
        segments = emptyList()
        sentenceIndex = 0
        _currentPage.value = null
        _activeBookId.value = null
        sessionEngine = null
        pendingResume = null
        _playbackState.value = TtsPlaybackState.IDLE
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

    private companion object {
        const val PAGE_TURN_CALLBACK_GRACE_NANOS = 1_000_000_000L
    }
}



