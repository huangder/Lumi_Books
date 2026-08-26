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

    class Playback(val errorCode: Int? = null) :
        SystemTtsException(
            if (errorCode == null) "System TTS playback failed"
            else "System TTS playback failed: $errorCode"
        )
}

class TtsEngine(
    @ApplicationContext context: Context
) : TtsPlaybackEngine {
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
    private var selectedLocale: Locale? = null
    private var utteranceListener: UtteranceProgressListener? = null
    private var pendingSpeechRate = 1f
    private var pendingPitch = 1f
    private val speechAudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()

    private val _engineStatus = MutableStateFlow(TtsEngineStatus.UNINITIALIZED)
    val engineStatus: StateFlow<TtsEngineStatus> = _engineStatus.asStateFlow()

    @Suppress("DEPRECATION")
    fun getInstalledEngines(): List<Pair<String, String>> = runCatching {
        val packageManager = appContext.packageManager
        packageManager.queryIntentServices(Intent(TextToSpeech.Engine.INTENT_ACTION_TTS_SERVICE), 0)
            .mapNotNull { info ->
                val service = info.serviceInfo ?: return@mapNotNull null
                service.packageName to runCatching { service.loadLabel(packageManager).toString() }
                    .getOrDefault(service.packageName)
            }
            .distinctBy { it.first }
    }.getOrElse { error ->
        Log.w(TAG, "Unable to enumerate installed TTS engines", error)
        emptyList()
    }

    override suspend fun initialize(): Result<Unit> = initialize(Locale.getDefault())

    suspend fun initialize(locale: Locale = Locale.getDefault()): Result<Unit> = initializeMutex.withLock {
        if (_engineStatus.value == TtsEngineStatus.READY && engine != null) {
            return@withLock Result.success(Unit)
        }

        shutdownEngine()
        _engineStatus.value = TtsEngineStatus.INITIALIZING
        val packages = installedEnginePackages()
        // The null entry asks Android for the user's default engine. Explicit packages are a
        // vendor-neutral fallback when that engine is temporarily unavailable or misconfigured.
        val candidates = listOf<String?>(null) + packages
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

            val chosenLocale = withContext(Dispatchers.Main.immediate) {
                selectSupportedLocale(created, locale)
            }
            if (chosenLocale == null) {
                lastFailure = SystemTtsException.LanguageUnavailable(locale)
                Log.w(
                    TAG,
                    "No supported locale: package=${packageName ?: "<default>"} requested=${locale.toLanguageTag()}"
                )
                withContext(Dispatchers.Main.immediate) { created.shutdown() }
                return@forEachIndexed
            }

            withContext(Dispatchers.Main.immediate) {
                created.setAudioAttributes(speechAudioAttributes)
                created.setSpeechRate(pendingSpeechRate)
                created.setPitch(pendingPitch)
                utteranceListener?.let(created::setOnUtteranceProgressListener)
            }
            engine = created
            enginePackageName = packageName ?: runCatching { created.defaultEngine }
                .getOrNull()
                ?.takeIf { it.isNotBlank() }
            selectedLocale = chosenLocale
            _engineStatus.value = TtsEngineStatus.READY
            Log.i(
                TAG,
                "Initialization ready: package=${enginePackageName ?: "<default>"} " +
                    "requested=${locale.toLanguageTag()} selected=${chosenLocale.toLanguageTag()}"
            )
            return@withLock Result.success(Unit)
        }

        shutdownEngine()
        _engineStatus.value = TtsEngineStatus.FAILED
        val failure = when (val error = lastFailure) {
            is SystemTtsException.LanguageUnavailable -> error
            null -> SystemTtsException.Initialization()
            else -> SystemTtsException.Initialization(error)
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

    override suspend fun speak(text: String, utteranceId: String): Result<Unit> =
        withContext(Dispatchers.Main.immediate) {
            val activeEngine = engine
                ?: return@withContext Result.failure(SystemTtsException.Initialization())

            val textLocale = TtsLocaleResolver.localeForText(text, Locale.getDefault())
            val currentLocale = selectedLocale
            if (textLocale != null && currentLocale?.language != textLocale.language) {
                selectSupportedLocale(activeEngine, textLocale)?.let { selectedLocale = it }
                    ?: Log.w(
                        TAG,
                        "Keeping locale=${currentLocale?.toLanguageTag()} because text locale " +
                            "${textLocale.toLanguageTag()} is unavailable"
                    )
            }

            val result = activeEngine.speak(text, TextToSpeech.QUEUE_FLUSH, Bundle(), utteranceId)
            if (result == TextToSpeech.SUCCESS) {
                Result.success(Unit)
            } else {
                Log.e(
                    TAG,
                    "speak() rejected: result=$result package=${enginePackageName ?: "<default>"} " +
                        "locale=${selectedLocale?.toLanguageTag()} textLength=${text.length}"
                )
                Result.failure(SystemTtsException.Playback(result))
            }
        }

    override suspend fun pause() {
        stop()
    }

    override suspend fun resume(): Boolean = false

    override suspend fun stop() = withContext(Dispatchers.Main.immediate) {
        engine?.stop()
        Unit
    }

    override suspend fun setSpeechRate(rate: Float) = withContext(Dispatchers.Main.immediate) {
        pendingSpeechRate = rate.coerceIn(0.5f, 2f)
        engine?.setSpeechRate(pendingSpeechRate)
        Unit
    }

    override suspend fun setPitch(pitch: Float) = withContext(Dispatchers.Main.immediate) {
        pendingPitch = pitch.coerceIn(0.5f, 2f)
        engine?.setPitch(pendingPitch)
        Unit
    }

    override fun setListener(listener: TtsPlaybackListener) {
        utteranceListener = object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                utteranceId?.let(listener::onStart)
            }

            override fun onDone(utteranceId: String?) {
                utteranceId?.let(listener::onDone)
            }

            @Deprecated("Deprecated by Android")
            override fun onError(utteranceId: String?) {
                utteranceId?.let { listener.onError(it, SystemTtsException.Playback()) }
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                utteranceId?.let { listener.onError(it, SystemTtsException.Playback(errorCode)) }
            }
        }
        utteranceListener?.let { progressListener ->
            engine?.setOnUtteranceProgressListener(progressListener)
        }
    }

    private fun shutdownEngine() {
        engine?.shutdown()
        engine = null
        enginePackageName = null
        selectedLocale = null
    }

    override fun shutdown() {
        shutdownEngine()
        _engineStatus.value = TtsEngineStatus.UNINITIALIZED
    }
}

internal object TtsLocaleResolver {
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
