package com.huangder.lumibooks.tts

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.coroutines.resume

enum class TtsEngineStatus {
    UNINITIALIZED,
    INITIALIZING,
    READY,
    FAILED
}

class TtsEngine(
    @ApplicationContext context: Context
) : TtsPlaybackEngine {
    override val isExternal: Boolean = false
    companion object {
        private const val TAG = "TtsEngine"
    }

    private val appContext = context.applicationContext
    private val initializeMutex = Mutex()
    private var engine: TextToSpeech? = null
    private var utteranceListener: UtteranceProgressListener? = null
    private var pendingSpeechRate = 1f
    private var pendingPitch = 1f

    private val _engineStatus = MutableStateFlow(TtsEngineStatus.UNINITIALIZED)
    val engineStatus: StateFlow<TtsEngineStatus> = _engineStatus.asStateFlow()

    override suspend fun initialize(): Result<Unit> = initialize(Locale.getDefault())

    suspend fun initialize(locale: Locale = Locale.getDefault()): Result<Unit> = initializeMutex.withLock {
        if (_engineStatus.value == TtsEngineStatus.READY && engine != null) {
            return Result.success(Unit)
        }

        _engineStatus.value = TtsEngineStatus.INITIALIZING
        return withContext(Dispatchers.Main.immediate) {
            suspendCancellableCoroutine { continuation ->
                var createdEngine: TextToSpeech? = null
                createdEngine = TextToSpeech(appContext) { status ->
                    val activeEngine = createdEngine ?: engine
                    if (!continuation.isActive) {
                        activeEngine?.shutdown()
                        return@TextToSpeech
                    }
                    if (status != TextToSpeech.SUCCESS || activeEngine == null) {
                        Log.e(TAG, "Initialization failed: status=$status engineAvailable=${activeEngine != null}")
                        activeEngine?.shutdown()
                        engine = null
                        _engineStatus.value = TtsEngineStatus.FAILED
                        continuation.resume(Result.failure(IllegalStateException("TTS initialization failed")))
                        return@TextToSpeech
                    }

                    val languageResult = activeEngine.setLanguage(locale)
                    if (languageResult == TextToSpeech.LANG_MISSING_DATA ||
                        languageResult == TextToSpeech.LANG_NOT_SUPPORTED
                    ) {
                        Log.e(TAG, "Language unavailable: locale=$locale result=$languageResult")
                        activeEngine.shutdown()
                        engine = null
                        _engineStatus.value = TtsEngineStatus.FAILED
                        continuation.resume(Result.failure(IllegalStateException("TTS language is unavailable")))
                        return@TextToSpeech
                    }

                    activeEngine.setSpeechRate(pendingSpeechRate)
                    activeEngine.setPitch(pendingPitch)
                    utteranceListener?.let(activeEngine::setOnUtteranceProgressListener)
                    _engineStatus.value = TtsEngineStatus.READY
                    Log.i(TAG, "Initialization ready: locale=$locale languageResult=$languageResult")
                    continuation.resume(Result.success(Unit))
                }
                engine = createdEngine
                utteranceListener?.let { createdEngine?.setOnUtteranceProgressListener(it) }
                continuation.invokeOnCancellation {
                    createdEngine?.shutdown()
                    if (engine === createdEngine) engine = null
                    _engineStatus.value = TtsEngineStatus.UNINITIALIZED
                }
            }
        }
    }

    override suspend fun speak(text: String, utteranceId: String): Result<Unit> = withContext(Dispatchers.Main.immediate) {
        val activeEngine = engine
            ?: return@withContext Result.failure(IllegalStateException("TTS engine is not ready"))
        val result = activeEngine.speak(text, TextToSpeech.QUEUE_FLUSH, Bundle(), utteranceId)
        if (result == TextToSpeech.SUCCESS) {
            Result.success(Unit)
        } else {
            Result.failure(IllegalStateException("TTS speak failed"))
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
                utteranceId?.let { listener.onError(it, IllegalStateException("System TTS playback failed")) }
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                utteranceId?.let {
                    listener.onError(it, IllegalStateException("System TTS playback failed: $errorCode"))
                }
            }
        }
        utteranceListener?.let { progressListener ->
            engine?.setOnUtteranceProgressListener(progressListener)
        }
    }

    override fun shutdown() {
        engine?.shutdown()
        engine = null
        _engineStatus.value = TtsEngineStatus.UNINITIALIZED
    }
}
