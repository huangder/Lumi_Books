package com.huangder.lumibooks.tts

/**
 * Unified playback boundary for system and remotely synthesized speech.
 * Implementations must report the lifecycle of the utterance they receive.
 */
interface TtsPlaybackEngine {
    val isExternal: Boolean

    /** True when pause/resume keeps the current utterance and its playback position. */
    val canResumeWithoutRestart: Boolean
        get() = false

    suspend fun initialize(): Result<Unit>

    /** Discards a failed engine instance before restoring its selected voice and settings. */
    suspend fun reinitialize(): Result<Unit> {
        stop()
        return initialize()
    }

    suspend fun speak(text: String, utteranceId: String): Result<Unit>

    /** Best-effort memory-only prefetch. Implementations may ignore it. */
    suspend fun prefetch(text: String) = Unit

    /** Restores a persisted utterance from [startFrame] when this engine supports PCM caching. */
    suspend fun speak(
        text: String,
        utteranceId: String,
        cacheKey: String?,
        startFrame: Long
    ): Result<Unit> = speak(text, utteranceId)

    /** Stable cache key for the effective current synthesis settings, or null when unsupported. */
    suspend fun cacheKey(text: String): String? = null

    /** Current rendered PCM frame offset within the active cached utterance. */
    fun currentPcmFrameOffset(): Long = 0L

    /** Total cached PCM frames for the active utterance, when known. */
    fun currentPcmFrameCount(): Long = 0L

    /** Pauses buffered audio when supported; system TTS implementations may stop instead. */
    suspend fun pause()

    /** Returns true only when buffered audio resumed without re-synthesizing the active text. */
    suspend fun resume(): Boolean

    suspend fun stop()

    /** Ends the spoken page while retaining the next prepared sentence on a normal page turn. */
    suspend fun stopForPageTurn() = stop()

    suspend fun setSpeechRate(rate: Float)

    suspend fun setPitch(pitch: Float)

    /** Null values leave that property under the selected engine's control. */
    suspend fun applyProsody(rate: Float?, pitch: Float?) {
        rate?.let { setSpeechRate(it) }
        pitch?.let { setPitch(it) }
    }

    fun setListener(listener: TtsPlaybackListener)

    fun shutdown()
}

interface AndroidTtsPlaybackEngine : TtsPlaybackEngine {
    /** Selects Android's default engine when null, or one exact installed engine package. */
    suspend fun selectEngine(packageName: String?)
}

interface TtsPlaybackListener {
    /** Actual rendered PCM activity, including pause/resume and buffer starvation. */
    fun onPlaybackActivity(utteranceId: String, active: Boolean) = Unit

    fun onStart(utteranceId: String)

    fun onDone(utteranceId: String)

    fun onError(utteranceId: String, throwable: Throwable)

    /**
     * A transient system interruption paused speech. The controller retains the current segment
     * and may restart it when the user resumes.
     */
    fun onPlaybackInterrupted()

    /** Android engines may report a finer spoken range within the complete utterance. */
    fun onRangeStart(utteranceId: String, start: Int, end: Int) = Unit

    /** Reports rendered PCM progress for exact external-audio resume positions. */
    fun onProgress(utteranceId: String, cacheKey: String, pcmFrameOffset: Long) = Unit
}
