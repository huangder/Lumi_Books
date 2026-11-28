package com.huangder.lumibooks.tts

import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import com.huangder.lumibooks.data.local.DataStoreManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.android.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Streams externally synthesized 24 kHz mono PCM16LE through [ExternalTtsAudioPlayer].
 * Complete utterances are retained in a bounded application-private cache for exact resume.
 *
 * Audio infrastructure (HandlerThread, ExternalTtsAudioPlayer, CoroutineScope) is created
 * lazily on first actual use rather than at construction time. This prevents the presence
 * of a HandlerThread and AudioManager-bound player from interfering with system TextToSpeech
 * on devices with aggressive audio policy (e.g. MIUI).
 */
@Singleton
class ExternalTtsEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dataStoreManager: DataStoreManager,
    private val tokenStore: ExternalTtsTokenStore,
    private val audioCache: ExternalTtsAudioCache
) : TtsPlaybackEngine {
    override val isExternal = true
    override val canResumeWithoutRestart: Boolean = true

    private val httpClient = ExternalTtsHttpClient(tokenStore)

    @Volatile
    private var audioInit = false

    private lateinit var audioThread: HandlerThread
    private lateinit var audioHandler: Handler
    private lateinit var engineDispatcher: CoroutineDispatcher
    private lateinit var audioPlayer: ExternalTtsAudioPlayer
    private lateinit var scope: CoroutineScope

    private val initLock = Any()

    private fun ensureAudioInitialized() {
        if (audioInit) return
        synchronized(initLock) {
            if (audioInit) return
            audioThread = HandlerThread("LumiExternalTtsAudio").apply { start() }
            audioHandler = Handler(audioThread.looper)
            engineDispatcher = audioHandler.asCoroutineDispatcher("ExternalTtsEngine")
            audioPlayer = ExternalTtsAudioPlayer(context, audioHandler)
            scope = CoroutineScope(SupervisorJob() + engineDispatcher)
            scope.launch {
                dataStoreManager.ttsAllowConcurrentPlayback.collect { allow ->
                    audioPlayer.setAllowConcurrentPlayback(allow)
                }
            }
            audioInit = true
        }
    }

    @Volatile
    private var listener: TtsPlaybackListener? = null
    private var sessionJob: Job? = null
    private var progressJob: Job? = null
    private var activityJob: Job? = null
    private var renderActivity: RenderedAudioActivity? = null
    private var currentSessionId = 0L

    // Prefetch state is only touched on engineDispatcher (a single audio HandlerThread).
    private val prefetchQueue = ArrayDeque<PrefetchItem>()
    private var prefetchWorker: Job? = null
    private var inFlightPrefetch: InFlightPrefetch? = null

    @Volatile
    private var activeUtteranceId: String? = null

    @Volatile
    private var activeCacheKey: String? = null

    @Volatile
    private var lastReportedFrame = -1L

    @Volatile
    private var completedPcmFrameCount = 0L

    @Volatile
    private var pauseRequested = false

    override suspend fun initialize(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val settings = dataStoreManager.externalTtsSettings.first()
            when {
                !settings.enabled -> throw ExternalTtsException.InvalidConfiguration(
                    "External TTS is not enabled"
                )
                settings.consentVersion < ExternalTtsConfig.CONSENT_VERSION -> {
                    throw ExternalTtsException.InvalidConfiguration("External TTS consent is not accepted")
                }
                !settings.hasRequiredFields -> throw ExternalTtsException.InvalidConfiguration(
                    "External TTS requires base URL, model, and voice"
                )
            }
            ExternalTtsEndpointValidator.validate(settings.baseUrl, settings.allowHttp).getOrThrow()
            if (tokenStore.read() == null) throw ExternalTtsException.MissingApiKey
            audioCache.trimToLimit(cacheLimitBytes())
            Result.success(Unit)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            Result.failure(error)
        }
    }

    override suspend fun speak(text: String, utteranceId: String): Result<Unit> =
        speak(text, utteranceId, cacheKey = null, startFrame = 0L)

    override suspend fun speak(
        text: String,
        utteranceId: String,
        cacheKey: String?,
        startFrame: Long
    ): Result<Unit> = withContext(Dispatchers.IO) {
        ensureAudioInitialized()
        withContext(engineDispatcher) {
            try {
                cancelCurrentSession()
                val sessionId = ++currentSessionId
                val settings = effectiveSettings()
                val resolvedCacheKey = audioCache.createKey(settings, text)
                val cachedFrames = audioCache.frameCount(resolvedCacheKey)
                val resumeFrame = if (cacheKey == resolvedCacheKey && cachedFrames > 0L) {
                    startFrame.coerceIn(0L, cachedFrames - 1L)
                } else {
                    0L
                }

                audioPlayer.setOnFocusLost {
                    reportActiveProgressAsync()
                    renderActivity?.pause()
                    activeUtteranceId?.let { listener?.onPlaybackActivity(it, false) }
                    scope.launch { listener?.onPlaybackInterrupted() }
                }
                if (cachedFrames == 0L) claimPrefetch(resolvedCacheKey)
                activeUtteranceId = utteranceId
                activeCacheKey = resolvedCacheKey
                lastReportedFrame = -1L
                completedPcmFrameCount = 0L
                pauseRequested = false

                sessionJob = scope.launch {
                    playSession(
                        sessionId = sessionId,
                        text = text,
                        utteranceId = utteranceId,
                        settings = settings,
                        cacheKey = resolvedCacheKey,
                        startFrame = resumeFrame,
                        useCachedAudio = cachedFrames > 0L
                    )
                }
                progressJob = scope.launch {
                    while (isActive && currentSessionId == sessionId) {
                        delay(PROGRESS_INTERVAL_MS)
                        reportActiveProgress(sessionId)
                    }
                }
                Result.success(Unit)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                Result.failure(error)
            }
        }
    }

    override suspend fun cacheKey(text: String): String? = withContext(Dispatchers.IO) {
        if (text.isBlank()) return@withContext null
        ensureAudioInitialized()
        withContext(engineDispatcher) {
            audioCache.createKey(effectiveSettings(), text)
        }
    }

    override fun currentPcmFrameOffset(): Long {
        if (!audioInit) return 0L
        return audioPlayer.renderedFrameOffset()
    }

    override fun currentPcmFrameCount(): Long {
        if (!audioInit) return 0L
        val key = activeCacheKey ?: return 0L
        return maxOf(audioCache.frameCount(key), completedPcmFrameCount)
    }

    override val prefetchDepth: Int = PREFETCH_DEPTH

    override suspend fun prefetch(text: String) = prefetchAll(listOf(text))

    /**
     * Synthesizes the following sentences one at a time, nearest first. A request that is
     * already running for a still-wanted sentence keeps going instead of being restarted.
     */
    override suspend fun prefetchAll(texts: List<String>) {
        if (texts.none { it.isNotBlank() }) return
        ensureAudioInitialized()
        withContext(engineDispatcher) {
            val settings = effectiveSettings()
            val wanted = texts.asSequence()
                .filter { it.isNotBlank() }
                .map { PrefetchItem(audioCache.createKey(settings, it), it, settings) }
                .distinctBy { it.key }
                .filterNot { audioCache.contains(it.key) }
                .toList()
            val inFlight = inFlightPrefetch
            if (inFlight != null && !inFlight.claimed && wanted.none { it.key == inFlight.key }) {
                // The running request is for a sentence that is no longer upcoming.
                prefetchWorker?.cancel()
                prefetchWorker = null
                inFlightPrefetch = null
            }
            prefetchQueue.clear()
            prefetchQueue.addAll(wanted.filter { it.key != inFlightPrefetch?.key })
            if (prefetchWorker?.isActive != true && prefetchQueue.isNotEmpty()) {
                val worker = scope.launch(start = CoroutineStart.LAZY) { runPrefetchQueue() }
                prefetchWorker = worker
                worker.invokeOnCompletion {
                    audioHandler.post { if (prefetchWorker === worker) prefetchWorker = null }
                }
                worker.start()
            }
        }
    }

    private suspend fun runPrefetchQueue() {
        while (true) {
            val item = prefetchQueue.removeFirstOrNull() ?: return
            if (audioCache.contains(item.key)) continue
            val stream = InFlightTtsPcm(
                CoroutineScope(currentCoroutineContext()),
                audioCache.writeThrough(item.key,
                    httpClient.synthesizeAsPcm(item.settings, item.text), cacheLimitBytes())
            )
            val inFlight = InFlightPrefetch(item.key, stream)
            inFlightPrefetch = inFlight
            try {
                stream.awaitCompletion()
            } finally {
                if (inFlightPrefetch === inFlight) inFlightPrefetch = null
                if (!currentCoroutineContext().isActive) stream.cancel()
            }
        }
    }

    /** Keeps a running request alive for playback; drops a queued one playback will fetch. */
    private fun claimPrefetch(cacheKey: String) {
        prefetchQueue.removeAll { it.key == cacheKey }
        inFlightPrefetch?.takeIf { it.key == cacheKey }?.claimed = true
    }

    /** Claims already-received PCM and the rest of the same request without waiting for EOF. */
    private fun awaitPrefetchedAudio(cacheKey: String): Flow<PcmChunk>? {
        val inFlight = inFlightPrefetch?.takeIf { it.key == cacheKey } ?: return null
        inFlight.claimed = true
        inFlightPrefetch = null // A later session must not claim this single-consumer stream again.
        return inFlight.stream.chunks
    }

    override suspend fun pause() {
        if (!audioInit) return
        withContext(engineDispatcher) {
            pauseRequested = true
            renderActivity?.pause()
            audioPlayer.pause()
            activeUtteranceId?.let { listener?.onPlaybackActivity(it, false) }
            reportActiveProgress()
        }
    }

    override suspend fun resume(): Boolean {
        if (!audioInit) return false
        return withContext(engineDispatcher) {
            if (sessionJob?.isActive != true) return@withContext false
            pauseRequested = false
            audioPlayer.resume()
        }
    }

    override suspend fun stop() {
        if (!audioInit) return
        withContext(engineDispatcher) {
            reportActiveProgress()
            cancelCurrentSession()
            clearPrefetch()
        }
    }

    override suspend fun setSpeechRate(rate: Float) {
        ensureAudioInitialized()
        withContext(engineDispatcher) {
            audioPlayer.setPlaybackRate(rate)
        }
    }

    override suspend fun setPitch(pitch: Float) {
        // OpenAI Speech and MiMo Chat expose no reliable pitch parameter.
    }

    override suspend fun applyProsody(rate: Float?, pitch: Float?) {
        setSpeechRate(rate ?: 1f)
    }

    override fun setListener(listener: TtsPlaybackListener) {
        this.listener = listener
    }

    override fun shutdown() {
        if (!audioInit) return
        val finalProgress = kotlinx.coroutines.runBlocking {
            withContext(engineDispatcher) {
                val snapshot = activeProgressSnapshot()
                cancelCurrentSession()
                clearPrefetch()
                snapshot
            }
        }
        finalProgress?.let { progress ->
            listener?.onProgress(progress.utteranceId, progress.cacheKey, progress.frameOffset)
        }
        scope.cancel()
        audioThread.quitSafely()
    }

    suspend fun testConnection(): Result<Unit> = withContext(Dispatchers.IO) {
        httpClient.testConnection(dataStoreManager.externalTtsSettings.first())
    }

    private suspend fun playSession(
        sessionId: Long,
        text: String,
        utteranceId: String,
        settings: ExternalTtsSettings,
        cacheKey: String,
        startFrame: Long,
        useCachedAudio: Boolean
    ) {
        var completed = false
        var failure: Throwable? = null
        var readHandle: ExternalTtsAudioCache.ReadHandle? = null
        try {
            val cachedRead = audioCache.open(cacheKey, if (useCachedAudio) startFrame else 0L)
            readHandle = cachedRead
            val playbackStartFrame = if (cachedRead != null && useCachedAudio) startFrame else 0L
            val pcmFlow = cachedRead?.chunks ?: awaitPrefetchedAudio(cacheKey)
                ?: audioCache.writeThrough(cacheKey, httpClient.synthesizeAsPcm(settings, text), cacheLimitBytes())
            audioPlayer.setAllowConcurrentPlayback(dataStoreManager.ttsAllowConcurrentPlayback.first())
            if (!audioPlayer.prepare(playbackStartFrame)) {
                throw ExternalTtsException.Network(
                    IllegalStateException("Audio focus is unavailable")
                )
            }
            if (pauseRequested) audioPlayer.pause()
            val activity = RenderedAudioActivity(android.os.SystemClock::elapsedRealtime, audioPlayer.renderedFrameOffset())
            renderActivity = activity
            var startReported = false
            activityJob = scope.launch {
                while (currentSessionId == sessionId) {
                    activity.update(audioPlayer.renderedFrameOffset(), pauseRequested)?.let { active ->
                        withContext(Dispatchers.Main) {
                            if (currentSessionId == sessionId) {
                                if (active && !pauseRequested && !startReported) {
                                    startReported = true
                                    listener?.onStart(utteranceId)
                                }
                                listener?.onPlaybackActivity(utteranceId, active)
                            }
                        }
                    }
                    delay(10)
                }
            }
            var chunkCount = 0
            pcmFlow.collect { chunk ->
                if (currentSessionId != sessionId) throw CancellationException("TTS session superseded")
                if (chunk.isEmpty()) return@collect
                if (!audioPlayer.write(chunk)) throw ExternalTtsException.InvalidAudio
                chunkCount++
            }
            if (currentSessionId != sessionId) throw CancellationException("TTS session superseded")
            if (chunkCount == 0) throw ExternalTtsException.InvalidAudio
            completedPcmFrameCount = audioPlayer.writtenFrameCount()
            if (!audioPlayer.drain()) throw ExternalTtsException.InvalidAudio
            withContext(Dispatchers.Main) {
                if (currentSessionId == sessionId && !pauseRequested && !startReported &&
                    audioPlayer.renderedFrameOffset() > playbackStartFrame) {
                    startReported = true
                    listener?.onStart(utteranceId)
                }
            }
            reportActiveProgress(sessionId, force = true)
            completed = currentSessionId == sessionId && currentCoroutineContext().isActive
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            if (currentSessionId == sessionId && currentCoroutineContext().isActive) {
                failure = error
            }
        } finally {
            readHandle?.close()
            finishSession(sessionId)
        }

        when {
            completed -> withContext(Dispatchers.Main) {
                if (currentSessionId == sessionId) listener?.onDone(utteranceId)
            }
            failure != null && currentCoroutineContext().isActive -> withContext(Dispatchers.Main) {
                if (currentSessionId == sessionId) listener?.onError(utteranceId, failure)
            }
        }
    }

    private fun finishSession(sessionId: Long) {
        if (currentSessionId != sessionId) return
        activityJob?.cancel()
        activityJob = null
        renderActivity = null
        activeUtteranceId?.let { listener?.onPlaybackActivity(it, false) }
        progressJob?.cancel()
        progressJob = null
        sessionJob = null
        audioPlayer.stop()
        activeUtteranceId = null
        activeCacheKey = null
    }

    private data class ProgressSnapshot(
        val utteranceId: String,
        val cacheKey: String,
        val frameOffset: Long
    )

    private fun activeProgressSnapshot(): ProgressSnapshot? {
        val utteranceId = activeUtteranceId ?: return null
        val cacheKey = activeCacheKey ?: return null
        return ProgressSnapshot(
            utteranceId = utteranceId,
            cacheKey = cacheKey,
            frameOffset = audioPlayer.renderedFrameOffset().coerceAtLeast(0L)
        )
    }

    private suspend fun reportActiveProgress(sessionId: Long? = null, force: Boolean = false) {
        if (sessionId != null && sessionId != currentSessionId) return
        val utteranceId = activeUtteranceId ?: return
        val cacheKey = activeCacheKey ?: return
        val frame = audioPlayer.renderedFrameOffset().coerceAtLeast(0L)
        if (!force && frame == lastReportedFrame) return
        lastReportedFrame = frame
        withContext(Dispatchers.Main) {
            if (sessionId == null || sessionId == currentSessionId) {
                listener?.onProgress(utteranceId, cacheKey, frame)
            }
        }
    }

    private fun reportActiveProgressAsync() {
        if (activeUtteranceId == null || activeCacheKey == null) return
        scope.launch { reportActiveProgress(force = true) }
    }

    private suspend fun effectiveSettings(): ExternalTtsSettings {
        return dataStoreManager.externalTtsSettings.first().normalized()
    }

    private suspend fun cacheLimitBytes(): Long =
        dataStoreManager.externalTtsCacheLimitMb.first().toLong() * BYTES_PER_MEBIBYTE

    private fun cancelCurrentSession() {
        activityJob?.cancel()
        activityJob = null
        renderActivity = null
        activeUtteranceId?.let { listener?.onPlaybackActivity(it, false) }
        progressJob?.cancel()
        progressJob = null
        sessionJob?.cancel()
        sessionJob = null
        currentSessionId++
        pauseRequested = false
        audioPlayer.stop()
        activeUtteranceId = null
        activeCacheKey = null
        lastReportedFrame = -1L
    }

    private fun clearPrefetch() {
        prefetchQueue.clear()
        prefetchWorker?.cancel()
        prefetchWorker = null
        inFlightPrefetch?.stream?.cancel()
        inFlightPrefetch = null
    }

    private class PrefetchItem(
        val key: String,
        val text: String,
        val settings: ExternalTtsSettings
    )

    private class InFlightPrefetch(
        val key: String,
        val stream: InFlightTtsPcm
    ) {
        /** Set when playback waits for this audio, so a newer lookahead must not cancel it. */
        var claimed = false
    }

    private companion object {
        const val PROGRESS_INTERVAL_MS = 500L

        /** Several short sentences can finish faster than one MiMo request returns. */
        const val PREFETCH_DEPTH = 3
        const val BYTES_PER_MEBIBYTE = 1_048_576L
    }
}
