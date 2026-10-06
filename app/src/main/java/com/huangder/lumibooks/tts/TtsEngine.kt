package com.huangder.lumibooks.tts

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.File
import java.io.IOException
import java.util.Locale
import com.huangder.lumibooks.util.diagnostics.DiagnosticLoggerRegistry
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

enum class TtsEngineStatus {
    UNINITIALIZED,
    INITIALIZING,
    READY,
    FAILED
}

sealed class SystemTtsException(message: String, cause: Throwable? = null) :
    IllegalStateException(message, cause) {
    class Initialization(cause: Throwable? = null) :
        SystemTtsException("System TTS initialization failed", cause)

    class LanguageUnavailable(val requestedLocale: Locale) :
        SystemTtsException("System TTS language is unavailable: ${requestedLocale.toLanguageTag()}")

    class EngineUnavailable(val packageName: String) :
        SystemTtsException("Android TTS engine is unavailable: $packageName")

    class Playback(val errorCode: Int? = null, cause: Throwable? = null) :
        SystemTtsException(
            if (errorCode == null) "System TTS playback failed"
            else "System TTS playback failed: $errorCode",
            cause
        )

    class ProsodyRejected(
        val setting: TtsProsodySetting,
        val requestedValue: Float,
        val errorCode: Int
    ) : SystemTtsException("System TTS rejected $setting=$requestedValue: $errorCode")
}

enum class TtsProsodySetting { RATE, PITCH }

class TtsEngine internal constructor(
    context: Context,
    private val localAudioPlayer: SystemTtsAudioPlayer
) : AndroidTtsPlaybackEngine {
    constructor(@ApplicationContext context: Context) : this(context, SystemTtsAudioPlayer())
    override val isExternal: Boolean = false

    companion object {
        private const val TAG = "TtsEngine"
        private const val INITIALIZATION_TIMEOUT_MS = 6_000L
        private const val RETRY_DELAY_MS = 180L
        private const val SYNTHESIS_STALL_TIMEOUT_MS = 10_000L
        private const val MAX_PREFETCH_BYTES = 8 * 1024 * 1024
    }

    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val initializeMutex = Mutex()
    private var engine: TextToSpeech? = null
    private var enginePackageName: String? = null
    private var requestedEnginePackageName: String? = null
    private var initializedRequestPackageName: String? = null
    private var selectedLocale: Locale? = null
    private var utteranceListener: UtteranceProgressListener? = null
    private class Synthesis(val id: String, val text: String, val file: File) {
        var playbackId: String? = null
        var sampleRate = 0
        var encoding = 0
        var channels = 0
        var finished = false
        var playbackStarted = false
        var playbackDone = false
        var bufferedBytes = 0
        var lastProgressMs = SystemClock.elapsedRealtime()
        val requestedAtMs = lastProgressMs
        var playbackRequestedAtMs = lastProgressMs
        var error: Throwable? = null
        val chunks = ArrayList<ByteArray>()
        val ranges = ArrayList<Triple<Int, Int, Int>>()
    }

    private var activeSynthesis: Synthesis? = null
    private var prefetchedSynthesis: Synthesis? = null
    private var pendingPrefetchText: String? = null
    private var synthesisSequence = 0L
    private val synthesisTasks = HashMap<String, Synthesis>()
    private var synthesisWatchdog: Runnable? = null
    private var activePaused = false
    private var pendingSpeechRate: Float? = null
    private var pendingPitch: Float? = null
    private val speechAudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()

    private val _engineStatus = MutableStateFlow(TtsEngineStatus.UNINITIALIZED)
    val engineStatus: StateFlow<TtsEngineStatus> = _engineStatus.asStateFlow()

    @Suppress("DEPRECATION")
    fun getInstalledEngines(): List<InstalledTtsEngine> = runCatching {
        val packageManager = appContext.packageManager
        packageManager.queryIntentServices(Intent(TextToSpeech.Engine.INTENT_ACTION_TTS_SERVICE), 0)
            .mapNotNull { info ->
                val service = info.serviceInfo ?: return@mapNotNull null
                InstalledTtsEngine(
                    packageName = service.packageName,
                    label = runCatching { service.loadLabel(packageManager).toString() }
                        .getOrDefault(service.packageName)
                )
            }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase(Locale.getDefault()) }
    }.getOrElse { error ->
        Log.w(TAG, "Unable to enumerate installed TTS engines", error)
        emptyList()
    }

    override suspend fun initialize(): Result<Unit> = initialize(Locale.getDefault())

    override suspend fun reinitialize(): Result<Unit> {
        initializeMutex.withLock {
            withContext(Dispatchers.Main.immediate) { shutdownEngine() }
            _engineStatus.value = TtsEngineStatus.UNINITIALIZED
        }
        return initialize()
    }

    override suspend fun selectEngine(packageName: String?) = initializeMutex.withLock {
        val normalized = packageName?.trim()?.takeIf(String::isNotEmpty)
        if (requestedEnginePackageName == normalized) return@withLock
        requestedEnginePackageName = normalized
        withContext(Dispatchers.Main.immediate) { shutdownEngine() }
        _engineStatus.value = TtsEngineStatus.UNINITIALIZED
    }

    suspend fun initialize(locale: Locale = Locale.getDefault()): Result<Unit> = initializeMutex.withLock {
        if (_engineStatus.value == TtsEngineStatus.READY &&
            engine != null &&
            initializedRequestPackageName == requestedEnginePackageName
        ) {
            return@withLock Result.success(Unit)
        }

        shutdownEngine()
        _engineStatus.value = TtsEngineStatus.INITIALIZING
        val packages = installedEnginePackages()
        val requestedPackage = requestedEnginePackageName
        if (requestedPackage != null && requestedPackage !in packages) {
            _engineStatus.value = TtsEngineStatus.FAILED
            return@withLock Result.failure(SystemTtsException.EngineUnavailable(requestedPackage))
        }
        // The null entry asks Android for the user's default engine. Explicit packages are a
        // vendor-neutral fallback when that engine is temporarily unavailable or misconfigured.
        val candidates = requestedPackage?.let { listOf<String?>(it) }
            ?: (listOf<String?>(null) + packages)
        var lastFailure: Throwable? = null

        candidates.forEachIndexed { index, packageName ->
            if (index > 0) delay(RETRY_DELAY_MS)
            val created = try {
                createEngine(packageName)
            } catch (error: Throwable) {
                if (error is kotlinx.coroutines.CancellationException) throw error
                lastFailure = error
                Log.w(TAG, "Engine initialization attempt failed: package=${packageName ?: "<default>"}", error)
                null
            } ?: return@forEachIndexed

            val chosenLocale = if (requestedPackage == null) {
                withContext(Dispatchers.Main.immediate) {
                    selectSupportedLocale(created, locale)
                }
            } else {
                null
            }
            if (requestedPackage == null && chosenLocale == null) {
                lastFailure = SystemTtsException.LanguageUnavailable(locale)
                Log.w(
                    TAG,
                    "No supported locale: package=${packageName ?: "<default>"} requested=${locale.toLanguageTag()}"
                )
                withContext(Dispatchers.Main.immediate) { created.shutdown() }
                return@forEachIndexed
            }

            val prosodyFailure = withContext(Dispatchers.Main.immediate) {
                created.setAudioAttributes(speechAudioAttributes)
                val rateFailure = pendingSpeechRate?.let { rate ->
                    created.setSpeechRate(rate)
                        .takeIf { it != TextToSpeech.SUCCESS }
                        ?.let { SystemTtsException.ProsodyRejected(TtsProsodySetting.RATE, rate, it) }
                }
                val pitchFailure = pendingPitch?.let { pitch ->
                    created.setPitch(pitch)
                        .takeIf { it != TextToSpeech.SUCCESS }
                        ?.let { SystemTtsException.ProsodyRejected(TtsProsodySetting.PITCH, pitch, it) }
                }
                utteranceListener?.let(created::setOnUtteranceProgressListener)
                rateFailure ?: pitchFailure
            }
            if (prosodyFailure != null) {
                withContext(Dispatchers.Main.immediate) { created.shutdown() }
                _engineStatus.value = TtsEngineStatus.FAILED
                return@withLock Result.failure(prosodyFailure)
            }
            engine = created
            enginePackageName = packageName ?: runCatching { created.defaultEngine }
                .getOrNull()
                ?.takeIf { it.isNotBlank() }
            initializedRequestPackageName = requestedPackage
            selectedLocale = chosenLocale
            _engineStatus.value = TtsEngineStatus.READY
            Log.i(
                TAG,
                "Initialization ready: package=${enginePackageName ?: "<default>"} " +
                    "requestedLocale=${locale.toLanguageTag()} " +
                    "selectedLocale=${chosenLocale?.toLanguageTag() ?: "<engine-managed>"}"
            )
            return@withLock Result.success(Unit)
        }

        shutdownEngine()
        _engineStatus.value = TtsEngineStatus.FAILED
        val failure = when {
            requestedPackage != null -> SystemTtsException.EngineUnavailable(requestedPackage)
            else -> when (val error = lastFailure) {
            is SystemTtsException.LanguageUnavailable -> error
            null -> SystemTtsException.Initialization()
            else -> SystemTtsException.Initialization(error)
            }
        }
        Result.failure(failure)
    }

    private suspend fun createEngine(packageName: String?): TextToSpeech =
        withContext(Dispatchers.Main.immediate) {
            try {
                withTimeout(INITIALIZATION_TIMEOUT_MS) {
                    suspendCancellableCoroutine { continuation ->
                        var createdEngine: TextToSpeech? = null
                        val listener = TextToSpeech.OnInitListener { status ->
                            // Some implementations can invoke OnInitListener before their constructor
                            // has returned. Posting guarantees createdEngine is assigned first.
                            mainHandler.post {
                                val activeEngine = createdEngine
                                if (!continuation.isActive) {
                                    activeEngine?.shutdown()
                                    return@post
                                }
                                if (status == TextToSpeech.SUCCESS && activeEngine != null) {
                                    continuation.resume(activeEngine)
                                } else {
                                    activeEngine?.shutdown()
                                    continuation.resumeWithException(
                                        SystemTtsException.Initialization(
                                            IllegalStateException(
                                                "status=$status engineAvailable=${activeEngine != null}"
                                            )
                                        )
                                    )
                                }
                            }
                        }

                        try {
                            createdEngine = if (packageName == null) {
                                TextToSpeech(appContext, listener)
                            } else {
                                TextToSpeech(appContext, listener, packageName)
                            }
                        } catch (error: Throwable) {
                            if (continuation.isActive) continuation.resumeWithException(error)
                        }
                        continuation.invokeOnCancellation {
                            mainHandler.post { createdEngine?.shutdown() }
                        }
                    }
                }
            } catch (error: TimeoutCancellationException) {
                throw SystemTtsException.Initialization(error)
            }
        }

    private fun selectSupportedLocale(activeEngine: TextToSpeech, requested: Locale): Locale? {
        val engineLocales = buildList {
            runCatching { activeEngine.defaultVoice?.locale }.getOrNull()?.let(::add)
            runCatching { activeEngine.voice?.locale }.getOrNull()?.let(::add)
        }
        val availableLocales = runCatching { activeEngine.availableLanguages.orEmpty() }
            .getOrElse { error ->
                Log.w(TAG, "Unable to enumerate TTS languages", error)
                emptySet()
            }
        val candidates = TtsLocaleResolver.candidates(requested, engineLocales, availableLocales)
        for (candidate in candidates) {
            val result = runCatching { activeEngine.setLanguage(candidate) }
                .getOrElse { error ->
                    Log.w(TAG, "setLanguage threw for ${candidate.toLanguageTag()}", error)
                    TextToSpeech.ERROR
                }
            Log.d(TAG, "Language candidate: ${candidate.toLanguageTag()} result=$result")
            if (result >= TextToSpeech.LANG_AVAILABLE) return candidate
        }
        return null
    }

    @Suppress("DEPRECATION")
    private suspend fun installedEnginePackages(): List<String> = withContext(Dispatchers.Default) {
        runCatching {
            appContext.packageManager
                .queryIntentServices(Intent(TextToSpeech.Engine.INTENT_ACTION_TTS_SERVICE), 0)
                .mapNotNull { it.serviceInfo?.packageName }
                .distinct()
        }.getOrElse { error ->
            Log.w(TAG, "Unable to enumerate installed TTS engines", error)
            emptyList()
        }
    }

    override val canResumeWithoutRestart: Boolean = true

    override suspend fun speak(text: String, utteranceId: String): Result<Unit> =
        withContext(Dispatchers.Main.immediate) {
            if (engine == null) return@withContext Result.failure(SystemTtsException.Initialization())
            val prepared = prefetchedSynthesis?.takeIf { it.text == text && it.error == null }
            if (prepared == null && activeSynthesis?.playbackDone != true) {
                stopLocalSynthesis()
            } else {
                discardSynthesis(activeSynthesis)
            }
            if (prepared == null) discardSynthesis(prefetchedSynthesis)
            prefetchedSynthesis = null
            pendingPrefetchText = null
            activePaused = false
            val task = prepared ?: runCatching { requestSynthesis(text) }.getOrElse { error ->
                return@withContext Result.failure(SystemTtsException.Playback(cause = error))
            }
            task.playbackId = utteranceId
            task.playbackRequestedAtMs = SystemClock.elapsedRealtime()
            activeSynthesis = task
            logSynthesis("synthesis_selected", task, mapOf("prefetchHit" to (prepared != null)))
            scheduleSynthesisWatchdog()
            runCatching {
                if (task.chunks.isNotEmpty() && task.sampleRate > 0 && task.encoding != 0 &&
                    task.channels in 1..2) beginLocalPlayback(task)
                if (task.finished) finishSynthesisPlayback(task)
            }.fold(
                onSuccess = { Result.success(Unit) },
                onFailure = { error ->
                    stopLocalSynthesis()
                    Result.failure(SystemTtsException.Playback(cause = error))
                }
            )
        }

    override suspend fun prefetch(text: String) = withContext(Dispatchers.Main.immediate) {
        if (text.isBlank() || text == prefetchedSynthesis?.text) return@withContext
        pendingPrefetchText = text
        // synthesizeToFile is already queued by Android. Submit the next sentence once the
        // current synthesis is complete, while its buffered audio is still playing.
        if (activeSynthesis?.finished == true) startPendingPrefetch()
    }

    private fun requestSynthesis(text: String): Synthesis {
        val tts = engine ?: throw SystemTtsException.Initialization()
        if (TtsLocaleResolver.shouldManageLocale(requestedEnginePackageName)) {
            val locale = TtsLocaleResolver.localeForText(text, Locale.getDefault())
            if (locale != null && selectedLocale?.language != locale.language) {
                selectSupportedLocale(tts, locale)?.let { selectedLocale = it }
            }
        }
        val task = Synthesis("lumi-synthesis-${++synthesisSequence}", text,
            File.createTempFile("lumi-tts-", ".wav", appContext.cacheDir))
        synthesisTasks[task.id] = task
        val result = runCatching { tts.synthesizeToFile(text, Bundle(), task.file, task.id) }
        if (result.getOrNull() != TextToSpeech.SUCCESS) {
            discardSynthesis(task)
            throw SystemTtsException.Playback(result.getOrNull(), result.exceptionOrNull())
        }
        logSynthesis("synthesis_requested", task)
        return task
    }

    private fun startPendingPrefetch() {
        val text = pendingPrefetchText ?: return
        pendingPrefetchText = null
        discardSynthesis(prefetchedSynthesis)
        prefetchedSynthesis = runCatching { requestSynthesis(text) }.getOrElse { error ->
            // A speculative failure must never interrupt the sentence the user is hearing.
            DiagnosticLoggerRegistry.logger?.log("tts", "prefetch_failed", throwable = error)
            null
        }
    }

    override suspend fun pause() {
        withContext(Dispatchers.Main.immediate) {
            if (activeSynthesis == null) return@withContext
            activePaused = true
            localAudioPlayer.pause()
        }
    }

    override suspend fun resume(): Boolean = withContext(Dispatchers.Main.immediate) {
        val task = activeSynthesis ?: return@withContext false
        if (task.playbackDone) return@withContext false
        activePaused = false
        task.lastProgressMs = SystemClock.elapsedRealtime()
        if (task.playbackStarted) localAudioPlayer.resume() else true
    }

    override suspend fun stop() = withContext(Dispatchers.Main.immediate) {
        stopLocalSynthesis()
        Unit
    }

    override suspend fun stopForPageTurn() = withContext(Dispatchers.Main.immediate) {
        discardSynthesis(activeSynthesis)
        activeSynthesis = null
        activePaused = false
        Unit
    }

    override suspend fun setSpeechRate(rate: Float): Unit = applyProsody(rate, pendingPitch)

    override suspend fun setPitch(pitch: Float): Unit = applyProsody(pendingSpeechRate, pitch)

    override suspend fun applyProsody(rate: Float?, pitch: Float?): Unit = initializeMutex.withLock {
        val normalizedRate = rate?.coerceIn(0.5f, 5f)
        val normalizedPitch = pitch?.coerceIn(0.5f, 2f)
        val previousRate = pendingSpeechRate
        val previousPitch = pendingPitch
        if (normalizedRate != previousRate || normalizedPitch != previousPitch) {
            withContext(Dispatchers.Main.immediate) {
                discardSynthesis(prefetchedSynthesis)
                prefetchedSynthesis = null
                pendingPrefetchText = null
            }
        }
        val mustRestoreEngineDefaults = engine != null &&
            ((pendingSpeechRate != null && normalizedRate == null) ||
                (pendingPitch != null && normalizedPitch == null))
        if (mustRestoreEngineDefaults) {
            pendingSpeechRate = normalizedRate
            pendingPitch = normalizedPitch
            withContext(Dispatchers.Main.immediate) { shutdownEngine() }
            _engineStatus.value = TtsEngineStatus.UNINITIALIZED
            return@withLock
        }
        data class ApplyFailure(
            val error: SystemTtsException.ProsodyRejected,
            val rateChangedBeforeFailure: Boolean
        )
        val failure = withContext(Dispatchers.Main.immediate) {
            val activeEngine = engine ?: return@withContext null
            var rateChanged = false
            normalizedRate?.takeIf { it != previousRate }?.let { value ->
                val result = activeEngine.setSpeechRate(value)
                if (result != TextToSpeech.SUCCESS) {
                    return@withContext ApplyFailure(
                        SystemTtsException.ProsodyRejected(TtsProsodySetting.RATE, value, result),
                        rateChangedBeforeFailure = false
                    )
                }
                rateChanged = true
            }
            normalizedPitch?.takeIf { it != previousPitch }?.let { value ->
                val result = activeEngine.setPitch(value)
                if (result != TextToSpeech.SUCCESS) {
                    return@withContext ApplyFailure(
                        SystemTtsException.ProsodyRejected(TtsProsodySetting.PITCH, value, result),
                        rateChangedBeforeFailure = rateChanged
                    )
                }
            }
            null
        }
        if (failure != null) {
            if (failure.rateChangedBeforeFailure) {
                val restored = previousRate?.let { previous ->
                    withContext(Dispatchers.Main.immediate) {
                        engine?.setSpeechRate(previous) == TextToSpeech.SUCCESS
                    }
                } ?: false
                if (!restored) {
                    withContext(Dispatchers.Main.immediate) { shutdownEngine() }
                    _engineStatus.value = TtsEngineStatus.UNINITIALIZED
                }
            }
            throw failure.error
        }
        pendingSpeechRate = normalizedRate
        pendingPitch = normalizedPitch
        Unit
    }

    override fun setListener(listener: TtsPlaybackListener) {
        playbackListener = listener
        utteranceListener = object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                // synthesizeToFile must never own the output stream. Local playback emits onStart.
            }

            override fun onDone(utteranceId: String?) {
                postSynthesisCallback(utteranceId) { task ->
                    if (task.finished) return@postSynthesisCallback
                    task.finished = true
                    logSynthesis("synthesis_completed", task,
                        mapOf("bufferedBytes" to task.bufferedBytes))
                    if (task === activeSynthesis) {
                        finishSynthesisPlayback(task)
                        startPendingPrefetch()
                    } else {
                        prepareBufferedAudio(task)
                    }
                }
            }

            override fun onBeginSynthesis(
                utteranceId: String?,
                sampleRateInHz: Int,
                audioFormat: Int,
                channelCount: Int
            ) {
                postSynthesisCallback(utteranceId) { task ->
                    if (task.finished) return@postSynthesisCallback
                    task.sampleRate = sampleRateInHz
                    task.encoding = audioFormat
                    task.channels = channelCount
                    logSynthesis("synthesis_format", task, mapOf("sampleRate" to sampleRateInHz,
                        "encoding" to audioFormat, "channels" to channelCount))
                    if (task === activeSynthesis && task.chunks.isNotEmpty() && !task.playbackStarted) {
                        beginLocalPlayback(task)
                    }
                }
            }

            override fun onAudioAvailable(utteranceId: String?, audio: ByteArray) {
                val ownedAudio = audio.copyOf()
                postSynthesisCallback(utteranceId) { task ->
                    if (task.finished || ownedAudio.isEmpty()) return@postSynthesisCallback
                    if (task === activeSynthesis && task.playbackStarted) {
                        localAudioPlayer.append(ownedAudio)
                    } else {
                        task.bufferedBytes += ownedAudio.size
                        if (task.bufferedBytes > MAX_PREFETCH_BYTES) {
                            throw IOException("TTS buffered audio exceeded its memory limit")
                        }
                        task.chunks += ownedAudio
                        if (task === activeSynthesis && task.sampleRate > 0 && task.encoding != 0 &&
                            task.channels in 1..2) beginLocalPlayback(task)
                    }
                }
            }

            override fun onRangeStart(utteranceId: String?, start: Int, end: Int, frame: Int) {
                postSynthesisCallback(utteranceId) { task ->
                    if (task === activeSynthesis && task.playbackStarted) {
                        scheduleRange(task, start, end, frame)
                    } else task.ranges += Triple(start, end, frame)
                }
            }

            @Deprecated("Deprecated by Android")
            override fun onError(utteranceId: String?) {
                postSynthesisCallback(utteranceId) { task ->
                    failLocalSynthesis(task, SystemTtsException.Playback())
                }
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                postSynthesisCallback(utteranceId) { task ->
                    failLocalSynthesis(task, SystemTtsException.Playback(errorCode))
                }
            }
        }
        utteranceListener?.let { progressListener ->
            engine?.setOnUtteranceProgressListener(progressListener)
        }
    }

    /** TextToSpeech callbacks are not required to run on the main thread. Keep synthesis state
     * and AudioTrack ownership serialized with pause/stop/speak commands. */
    private fun postSynthesisCallback(utteranceId: String?, callback: (Synthesis) -> Unit) {
        mainHandler.post {
            val task = synthesisTasks[utteranceId] ?: return@post
            if (task.error != null || task.playbackDone) return@post
            task.lastProgressMs = SystemClock.elapsedRealtime()
            runCatching { callback(task) }.onFailure { error ->
                failLocalSynthesis(task, error)
            }
        }
    }

    private fun beginLocalPlayback(task: Synthesis) {
        if (task !== activeSynthesis || task.playbackStarted) return
        localAudioPlayer.setCallbacks(
            onDone = {
                if (task === activeSynthesis && task.finished && !task.playbackDone) {
                    task.playbackDone = true
                    val frames = localAudioPlayer.renderedFrameOffset()
                    logSynthesis("audio_rendered_done", task, mapOf("renderedFrames" to frames,
                        "audioDurationMs" to (frames * 1_000L / task.sampleRate.coerceAtLeast(1))))
                    task.playbackId?.let { playbackListener?.onDone(it) }
                }
            },
            onError = { error -> if (task === activeSynthesis) failLocalSynthesis(task, error) },
            onRenderedStart = {
                if (task === activeSynthesis) logSynthesis("audio_first_frame_rendered", task,
                    mapOf("startLatencyMs" to (SystemClock.elapsedRealtime() - task.playbackRequestedAtMs)))
            }
        )
        if (!localAudioPlayer.begin(task.sampleRate, task.encoding, task.channels)) {
            throw IOException("Unable to initialize Lumi AudioTrack")
        }
        task.playbackStarted = true
        if (activePaused) localAudioPlayer.pause()
        task.playbackId?.let { playbackListener?.onStart(it) }
        task.chunks.forEach(localAudioPlayer::append)
        task.chunks.clear()
        task.bufferedBytes = 0
        task.ranges.forEach { (start, end, frame) ->
            scheduleRange(task, start, end, frame)
        }
        task.ranges.clear()
    }

    private fun scheduleRange(task: Synthesis, start: Int, end: Int, frame: Int) {
        localAudioPlayer.scheduleRange(frame.toLong()) {
            if (task === activeSynthesis && !task.playbackDone) {
                task.playbackId?.let { playbackListener?.onRangeStart(it, start, end) }
            }
        }
    }

    private var playbackListener: TtsPlaybackListener? = null

    private fun finishSynthesisPlayback(task: Synthesis) {
        if (task !== activeSynthesis) return
        if (!task.playbackStarted) {
            prepareBufferedAudio(task)
            beginLocalPlayback(task)
        }
        localAudioPlayer.finish()
    }

    private fun prepareBufferedAudio(task: Synthesis) {
        if (task.chunks.isNotEmpty() && task.sampleRate > 0 && task.encoding != 0 && task.channels in 1..2) return
        if (task.file.length() > MAX_PREFETCH_BYTES) throw IOException("TTS output exceeded its memory limit")
        val decoded = SystemTtsAudioDecoder.decode(task.file.readBytes(),
            task.sampleRate, task.encoding, task.channels)
        task.sampleRate = decoded.sampleRate
        task.encoding = decoded.encoding
        task.channels = decoded.channels
        task.chunks.clear()
        task.chunks += decoded.pcm
        task.bufferedBytes = decoded.pcm.size
    }

    private fun failLocalSynthesis(task: Synthesis, error: Throwable) {
        if (synthesisTasks[task.id] !== task) return
        task.error = error
        logSynthesis("synthesis_failed", task, mapOf("errorType" to error.javaClass.simpleName))
        if (task !== activeSynthesis) {
            discardSynthesis(task)
            if (prefetchedSynthesis === task) prefetchedSynthesis = null
            return
        }
        val id = task.playbackId
        stopLocalSynthesis()
        id?.let { playbackListener?.onError(it,
            error as? SystemTtsException ?: SystemTtsException.Playback(cause = error)) }
    }

    private fun stopLocalSynthesis(stopEngine: Boolean = true) {
        synthesisWatchdog?.let(mainHandler::removeCallbacks)
        synthesisWatchdog = null
        synthesisTasks.values.toList().forEach(::discardSynthesis)
        activeSynthesis = null
        prefetchedSynthesis = null
        pendingPrefetchText = null
        localAudioPlayer.stop()
        activePaused = false
        if (stopEngine) engine?.stop()
    }

    private fun discardSynthesis(task: Synthesis?) {
        if (task == null) return
        synthesisTasks.remove(task.id)
        task.file.delete()
        task.chunks.clear()
        task.ranges.clear()
    }

    private fun scheduleSynthesisWatchdog() {
        synthesisWatchdog?.let(mainHandler::removeCallbacks)
        val check = object : Runnable {
            override fun run() {
                val task = activeSynthesis ?: return
                if (activePaused) task.lastProgressMs = SystemClock.elapsedRealtime()
                if (!task.finished && !activePaused &&
                    SystemClock.elapsedRealtime() - task.lastProgressMs >= SYNTHESIS_STALL_TIMEOUT_MS) {
                    failLocalSynthesis(task, IOException("TTS synthesis made no progress for 10 seconds"))
                    return
                }
                mainHandler.postDelayed(this, 1_000L)
            }
        }
        synthesisWatchdog = check
        mainHandler.postDelayed(check, 1_000L)
    }

    private fun logSynthesis(event: String, task: Synthesis, extra: Map<String, Any?> = emptyMap()) {
        DiagnosticLoggerRegistry.logger?.log("tts", event, attributes = mapOf(
            "enginePackage" to enginePackageName,
            "synthesisId" to task.id,
            "utteranceId" to task.playbackId,
            "textLength" to task.text.length,
            "elapsedMs" to (SystemClock.elapsedRealtime() - task.requestedAtMs)
        ) + extra)
    }

    private fun shutdownEngine() {
        stopLocalSynthesis()
        engine?.shutdown()
        engine = null
        enginePackageName = null
        initializedRequestPackageName = null
        selectedLocale = null
    }

    override fun shutdown() {
        shutdownEngine()
        _engineStatus.value = TtsEngineStatus.UNINITIALIZED
    }
}

internal object TtsLocaleResolver {
    fun shouldManageLocale(requestedEnginePackageName: String?): Boolean =
        requestedEnginePackageName == null

    fun candidates(
        requested: Locale,
        engineDefaults: Collection<Locale>,
        available: Collection<Locale>
    ): List<Locale> {
        val ordered = LinkedHashMap<String, Locale>()
        fun add(locale: Locale?) {
            if (locale == null || locale.language.isBlank()) return
            ordered.putIfAbsent(locale.toLanguageTag().lowercase(Locale.ROOT), locale)
        }

        add(requested)
        engineDefaults.filter { it.language == requested.language }.forEach(::add)
        if (requested.language == Locale.CHINESE.language) {
            add(Locale.SIMPLIFIED_CHINESE)
            add(Locale.CHINESE)
            add(Locale.TRADITIONAL_CHINESE)
        } else {
            add(Locale.forLanguageTag(requested.language))
        }
        available
            .filter { it.language == requested.language }
            .sortedWith(compareBy<Locale>({ it.country != requested.country }, { it.toLanguageTag() }))
            .forEach(::add)
        return ordered.values.toList()
    }

    fun localeForText(text: String, fallback: Locale): Locale? {
        var han = 0
        var kana = 0
        var hangul = 0
        var latin = 0
        text.forEach { char ->
            when (Character.UnicodeScript.of(char.code)) {
                Character.UnicodeScript.HAN -> han++
                Character.UnicodeScript.HIRAGANA,
                Character.UnicodeScript.KATAKANA -> kana++
                Character.UnicodeScript.HANGUL -> hangul++
                Character.UnicodeScript.LATIN -> if (char.isLetter()) latin++
                else -> Unit
            }
        }
        val strongCount = han + kana + hangul + latin
        if (strongCount < 4) return null
        return when {
            kana > 0 && han + kana >= hangul && han + kana >= latin -> Locale.JAPANESE
            hangul >= han && hangul >= latin -> Locale.KOREAN
            han >= latin -> if (fallback.language == Locale.CHINESE.language) {
                fallback
            } else {
                Locale.SIMPLIFIED_CHINESE
            }
            latin > han && latin > hangul -> if (
                fallback.language != Locale.CHINESE.language &&
                fallback.language != Locale.JAPANESE.language &&
                fallback.language != Locale.KOREAN.language
            ) {
                fallback
            } else {
                Locale.ENGLISH
            }
            else -> null
        }
    }
}
