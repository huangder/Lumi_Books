package com.huangder.lumibooks.tts

import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import com.huangder.lumibooks.data.local.DataStoreManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
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
 */
@Singleton
class ExternalTtsEngine @Inject constructor(
    @ApplicationContext context: Context,
    private val dataStoreManager: DataStoreManager,
    private val tokenStore: ExternalTtsTokenStore,
    private val audioCache: ExternalTtsAudioCache
) : TtsPlaybackEngine {
    override val isExternal = true

    private val httpClient = ExternalTtsHttpClient(tokenStore)
    private val audioThread = HandlerThread("LumiExternalTtsAudio").apply { start() }
    private val audioHandler = Handler(audioThread.looper)
    private val engineDispatcher = audioHandler.asCoroutineDispatcher("ExternalTtsEngine")
    private val audioPlayer = ExternalTtsAudioPlayer(context, audioHandler)
    private val scope = CoroutineScope(SupervisorJob() + engineDispatcher)
    private val prefetchLock = Any()

    @Volatile
    private var listener: TtsPlaybackListener? = null
    private var sessionJob: Job? = null
    private var progressJob: Job? = null
    private var currentSessionId = 0L
    private var utterancePitch = 1f

    private var prefetchJob: Job? = null
    private var prefetchedKey: String? = null

    @Volatile
    private var activeUtteranceId: String? = null

    @Volatile
    private var activeCacheKey: String? = null

    @Volatile
    private var lastReportedFrame = -1L

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
    ): Result<Unit> = withContext(engineDispatcher) {
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
                scope.launch { listener?.onPlaybackInterrupted() }
            }
            activeUtteranceId = utteranceId
            activeCacheKey = resolvedCacheKey
            lastReportedFrame = -1L
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

    override suspend fun cacheKey(text: String): String? = withContext(engineDispatcher) {
        if (text.isBlank()) return@withContext null
        audioCache.createKey(effectiveSettings(), text)
    }

    override fun currentPcmFrameOffset(): Long = audioPlayer.renderedFrameOffset()

    override suspend fun prefetch(text: String) = withContext(engineDispatcher) {
        if (text.isBlank()) return@withContext
        val settings = effectiveSettings()
        val key = audioCache.createKey(settings, text)
        if (audioCache.contains(key)) return@withContext
        synchronized(prefetchLock) {
            if (prefetchedKey == key && prefetchJob?.isActive == true) return@synchronized
            prefetchJob?.cancel()
            prefetchedKey = key
            prefetchJob = scope.launch {
                try {
                    audioCache.store(
                        key = key,
                        source = httpClient.synthesizeAsPcm(settings, text),
                        maxBytes = cacheLimitBytes()
                    )
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Throwable) {
                    // Prefetch is best effort; foreground synthesis will surface real failures.
                } finally {
                    synchronized(prefetchLock) {
                        if (prefetchedKey == key) {
                            prefetchedKey = null
                            prefetchJob = null
                        }
                    }
                }
            }
        }
    }

    override suspend fun pause() = withContext(engineDispatcher) {
        pauseRequested = true
        audioPlayer.pause()
        reportActiveProgress()
    }

    override suspend fun resume(): Boolean = withContext(engineDispatcher) {
        if (sessionJob?.isActive != true) return@withContext false
        pauseRequested = false
        audioPlayer.resume()
    }

    override suspend fun stop() = withContext(engineDispatcher) {
        reportActiveProgress()
        cancelCurrentSession()
        clearPrefetch()
    }

    override suspend fun setSpeechRate(rate: Float) = withContext(engineDispatcher) {
        audioPlayer.setPlaybackRate(rate)
    }

    override suspend fun setPitch(pitch: Float) = withContext(engineDispatcher) {
        utterancePitch = pitch.coerceIn(0.5f, 2f)
    }

    override fun setListener(listener: TtsPlaybackListener) {
        this.listener = listener
    }

    override fun shutdown() {
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
            val cachedRead = if (useCachedAudio) {
                audioCache.open(cacheKey, startFrame)
            } else {
                null
            }
            readHandle = cachedRead ?: run {
                val stored = audioCache.store(
                    key = cacheKey,
                    source = httpClient.synthesizeAsPcm(settings, text),
                    maxBytes = cacheLimitBytes()
                )
                if (!stored) throw ExternalTtsException.InvalidAudio
                audioCache.open(cacheKey, 0L) ?: throw ExternalTtsException.InvalidAudio
            }
            val playbackStartFrame = if (cachedRead != null) startFrame else 0L
            val pcmFlow = checkNotNull(readHandle).chunks
            if (!audioPlayer.prepare(playbackStartFrame)) {
                throw ExternalTtsException.Network(
                    IllegalStateException("Audio focus is unavailable")
                )
            }
            if (pauseRequested) audioPlayer.pause()
            var chunkCount = 0
            pcmFlow.collect { chunk ->
                if (currentSessionId != sessionId) throw CancellationException("TTS session superseded")
                if (chunk.isEmpty()) return@collect
                if (!audioPlayer.write(chunk)) throw ExternalTtsException.InvalidAudio
                chunkCount++
                if (chunkCount == 1) {
                    withContext(Dispatchers.Main) {
                        if (currentSessionId == sessionId) listener?.onStart(utteranceId)
                    }
                }
            }
            if (currentSessionId != sessionId) throw CancellationException("TTS session superseded")
            if (chunkCount == 0 || !audioPlayer.drain()) throw ExternalTtsException.InvalidAudio
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
        val storedSettings = dataStoreManager.externalTtsSettings.first()
        return storedSettings.copy(
            styleInstructions = buildEffectiveInstructions(storedSettings.styleInstructions)
        ).normalized()
    }

    private suspend fun cacheLimitBytes(): Long =
        dataStoreManager.externalTtsCacheLimitMb.first().toLong() * BYTES_PER_MEBIBYTE

    private fun buildEffectiveInstructions(styleInstructions: String): String {
        val instructions = buildList {
            if (styleInstructions.isNotBlank()) add(styleInstructions)
            if (utterancePitch != 1f) add(ExternalTtsConfig.pitchInstruction(utterancePitch))
        }
        return instructions.joinToString("\n\n")
    }

    private fun cancelCurrentSession() {
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
        synchronized(prefetchLock) {
            prefetchJob?.cancel()
            prefetchJob = null
            prefetchedKey = null
        }
    }

    private companion object {
        const val PROGRESS_INTERVAL_MS = 500L
        const val BYTES_PER_MEBIBYTE = 1_048_576L
    }
}
