package com.huangder.lumibooks.tts

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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

class TtsEngine(
    @ApplicationContext context: Context
) : AndroidTtsPlaybackEngine {
    override val isExternal: Boolean = false

    companion object {
        private const val TAG = "TtsEngine"
        private const val INITIALIZATION_TIMEOUT_MS = 6_000L
        private const val RETRY_DELAY_MS = 180L
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
    private val localAudioPlayer = SystemTtsAudioPlayer()
    private var activeUtteranceId: String? = null
    private var activeSynthesisFile: File? = null
    private var activeSynthesisGeneration = 0L
    private var activeAudioReceived = false
    private var activeSynthesisFinished = false
    private var activeLocalStarted = false
    private var activePaused = false
    private val pendingAudioChunks = ArrayList<ByteArray>()
    private val pendingRanges = ArrayList<Triple<Int, Int, Int>>()
    private var synthesisSampleRate = 0
    private var synthesisAudioFormat = 0
    private var synthesisChannels = 0
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
            val activeEngine = engine
                ?: return@withContext Result.failure(SystemTtsException.Initialization())

            stopLocalSynthesis()

            val textLocale = if (TtsLocaleResolver.shouldManageLocale(requestedEnginePackageName)) {
                TtsLocaleResolver.localeForText(text, Locale.getDefault())
            } else {
                null
            }
            val currentLocale = selectedLocale
            if (textLocale != null && currentLocale?.language != textLocale.language) {
                selectSupportedLocale(activeEngine, textLocale)?.let { selectedLocale = it }
                    ?: Log.w(
                        TAG,
                        "Keeping locale=${currentLocale?.toLanguageTag()} because text locale " +
                            "${textLocale.toLanguageTag()} is unavailable"
                    )
            }

            val outputFile = runCatching {
                File.createTempFile("lumi-tts-", ".wav", appContext.cacheDir)
            }.getOrElse { error ->
                return@withContext Result.failure(SystemTtsException.Playback(cause = error))
            }
            val generation = ++activeSynthesisGeneration
            activeUtteranceId = utteranceId
            activeSynthesisFile = outputFile
            activeAudioReceived = false
            activeSynthesisFinished = false
            activeLocalStarted = false
            activePaused = false
            pendingAudioChunks.clear()
            pendingRanges.clear()
            val result = activeEngine.synthesizeToFile(
                text,
                Bundle(),
                outputFile,
                utteranceId
            )
            if (result == TextToSpeech.SUCCESS) {
                Result.success(Unit)
            } else {
                Log.e(
                    TAG,
                    "speak() rejected: result=$result package=${enginePackageName ?: "<default>"} " +
                        "locale=${selectedLocale?.toLanguageTag()} textLength=${text.length}"
                )
                if (generation == activeSynthesisGeneration) stopLocalSynthesis()
                Result.failure(SystemTtsException.Playback(result))
            }
        }

    override suspend fun pause() {
        withContext(Dispatchers.Main.immediate) {
            if (activeUtteranceId == null) return@withContext
            activePaused = true
            localAudioPlayer.pause()
        }
    }

    override suspend fun resume(): Boolean = withContext(Dispatchers.Main.immediate) {
        if (activeUtteranceId == null) return@withContext false
        activePaused = false
        if (activeLocalStarted) localAudioPlayer.resume() else true
    }

    override suspend fun stop() = withContext(Dispatchers.Main.immediate) {
        stopLocalSynthesis()
        Unit
    }

    override suspend fun setSpeechRate(rate: Float): Unit = applyProsody(rate, pendingPitch)

    override suspend fun setPitch(pitch: Float): Unit = applyProsody(pendingSpeechRate, pitch)

    override suspend fun applyProsody(rate: Float?, pitch: Float?): Unit = initializeMutex.withLock {
        val normalizedRate = rate?.coerceIn(0.5f, 5f)
        val normalizedPitch = pitch?.coerceIn(0.5f, 2f)
        val previousRate = pendingSpeechRate
        val previousPitch = pendingPitch
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
                postSynthesisCallback {
                    if (utteranceId == null || utteranceId != activeUtteranceId) return@postSynthesisCallback
                    activeSynthesisFinished = true
                    if (activeAudioReceived && !activeLocalStarted && synthesisSampleRate > 0 &&
                        synthesisAudioFormat != 0 && synthesisChannels in 1..2 && pendingAudioChunks.isNotEmpty()
                    ) {
                        if (beginLocalPlayback(
                                utteranceId,
                                synthesisSampleRate,
                                synthesisAudioFormat,
                                synthesisChannels
                            )
                        ) {
                            localAudioPlayer.finish()
                        } else {
                            failLocalSynthesis(utteranceId, IOException("Unable to initialize Lumi AudioTrack"))
                        }
                    } else if (!activeAudioReceived || !activeLocalStarted) {
                        runCatching { playSynthesizedFile(utteranceId) }
                            .onFailure { failLocalSynthesis(utteranceId, it) }
                    } else {
                        localAudioPlayer.finish()
                    }
                }
            }

            override fun onBeginSynthesis(
                utteranceId: String?,
                sampleRateInHz: Int,
                audioFormat: Int,
                channelCount: Int
            ) {
                postSynthesisCallback {
                    if (utteranceId == null || utteranceId != activeUtteranceId) return@postSynthesisCallback
                    synthesisSampleRate = sampleRateInHz
                    synthesisAudioFormat = audioFormat
                    synthesisChannels = channelCount
                    if (pendingAudioChunks.isNotEmpty()) {
                        if (!beginLocalPlayback(utteranceId, sampleRateInHz, audioFormat, channelCount)) {
                            failLocalSynthesis(utteranceId, IOException("Unable to initialize Lumi AudioTrack"))
                            return@postSynthesisCallback
                        }
                    }
                }
            }

            override fun onAudioAvailable(utteranceId: String?, audio: ByteArray) {
                postSynthesisCallback {
                    if (utteranceId == null || utteranceId != activeUtteranceId || audio.isEmpty()) return@postSynthesisCallback
                    activeAudioReceived = true
                    if (!activeLocalStarted) {
                        if (synthesisSampleRate <= 0 || synthesisAudioFormat == 0 || synthesisChannels <= 0) {
                            pendingAudioChunks += audio.copyOf()
                            return@postSynthesisCallback
                        }
                        if (!beginLocalPlayback(
                                utteranceId,
                                synthesisSampleRate,
                                synthesisAudioFormat,
                                synthesisChannels
                            )
                        ) {
                            failLocalSynthesis(utteranceId, IOException("Unable to initialize Lumi AudioTrack"))
                            return@postSynthesisCallback
                        }
                    }
                    if (pendingAudioChunks.isNotEmpty()) {
                        pendingAudioChunks.forEach(localAudioPlayer::append)
                        pendingAudioChunks.clear()
                    }
                    localAudioPlayer.append(audio)
                }
            }

            override fun onRangeStart(utteranceId: String?, start: Int, end: Int, frame: Int) {
                postSynthesisCallback {
                    if (utteranceId == null || utteranceId != activeUtteranceId) return@postSynthesisCallback
                    if (!activeLocalStarted) {
                        pendingRanges += Triple(start, end, frame)
                    } else {
                        localAudioPlayer.scheduleRange(frame.toLong()) {
                            playbackListener?.onRangeStart(utteranceId, start, end)
                        }
                    }
                }
            }

            @Deprecated("Deprecated by Android")
            override fun onError(utteranceId: String?) {
                postSynthesisCallback {
                    utteranceId?.let { failLocalSynthesis(it, SystemTtsException.Playback()) }
                }
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                postSynthesisCallback {
                    utteranceId?.let { failLocalSynthesis(it, SystemTtsException.Playback(errorCode)) }
                }
            }
        }
        localAudioPlayer.setCallbacks(
            onDone = {
                val id = activeUtteranceId ?: return@setCallbacks
                if (activeSynthesisFinished) {
                    listener.onDone(id)
                    stopLocalSynthesis(stopEngine = false)
                }
            },
            onError = { error ->
                val id = activeUtteranceId ?: return@setCallbacks
                failLocalSynthesis(id, SystemTtsException.Playback(cause = error))
            }
        )
        utteranceListener?.let { progressListener ->
            engine?.setOnUtteranceProgressListener(progressListener)
        }
    }

    /** TextToSpeech callbacks are not required to run on the main thread. Keep synthesis state
     * and AudioTrack ownership serialized with pause/stop/speak commands. */
    private fun postSynthesisCallback(callback: () -> Unit) {
        mainHandler.post {
            runCatching(callback).onFailure { error ->
                val id = activeUtteranceId ?: return@onFailure
                failLocalSynthesis(id, SystemTtsException.Playback(cause = error))
            }
        }
    }

    private fun beginLocalPlayback(
        utteranceId: String,
        sampleRate: Int,
        encoding: Int,
        channels: Int
    ): Boolean {
        if (utteranceId != activeUtteranceId) return false
        val started = localAudioPlayer.begin(sampleRate, encoding, channels)
        if (!started) return false
        activeLocalStarted = true
        if (activePaused) localAudioPlayer.pause()
        notifyLocalStart(utteranceId)
        pendingAudioChunks.forEach(localAudioPlayer::append)
        pendingAudioChunks.clear()
        pendingRanges.forEach { (start, end, frame) ->
            localAudioPlayer.scheduleRange(frame.toLong()) {
                playbackListener?.onRangeStart(utteranceId, start, end)
            }
        }
        pendingRanges.clear()
        return true
    }

    private fun notifyLocalStart(utteranceId: String) {
        val callback = playbackListener ?: return
        if (!activeLocalStarted || utteranceId != activeUtteranceId) return
        callback.onStart(utteranceId)
    }

    private var playbackListener: TtsPlaybackListener? = null

    private fun playSynthesizedFile(utteranceId: String) {
        if (utteranceId != activeUtteranceId) return
        val file = activeSynthesisFile ?: throw IOException("TTS synthesis did not produce a file")
        val decoded = SystemTtsAudioDecoder.decode(
            bytes = file.readBytes(),
            fallbackSampleRate = synthesisSampleRate,
            fallbackEncoding = synthesisAudioFormat,
            fallbackChannels = synthesisChannels
        )
        if (decoded.pcm.isEmpty() || !beginLocalPlayback(
                utteranceId,
                decoded.sampleRate,
                decoded.encoding,
                decoded.channels
            )
        ) {
            throw IOException("Unable to initialize Lumi AudioTrack")
        }
        localAudioPlayer.append(decoded.pcm)
        localAudioPlayer.finish()
        activeAudioReceived = true
    }

    private fun failLocalSynthesis(utteranceId: String, error: Throwable) {
        if (utteranceId != activeUtteranceId) return
        val callback = playbackListener
        stopLocalSynthesis()
        callback?.onError(utteranceId, error)
    }

    private fun stopLocalSynthesis(stopEngine: Boolean = true) {
        activeSynthesisGeneration++
        activeUtteranceId = null
        localAudioPlayer.stop()
        activeSynthesisFile?.delete()
        activeSynthesisFile = null
        activeAudioReceived = false
        activeSynthesisFinished = false
        activeLocalStarted = false
        activePaused = false
        pendingAudioChunks.clear()
        pendingRanges.clear()
        synthesisSampleRate = 0
        synthesisAudioFormat = 0
        synthesisChannels = 0
        if (stopEngine) engine?.stop()
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
