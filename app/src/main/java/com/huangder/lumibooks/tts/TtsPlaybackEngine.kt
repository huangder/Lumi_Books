package com.huangder.lumibooks.tts

/**
 * Unified playback boundary for system and remotely synthesized speech.
 * Implementations must report the lifecycle of the utterance they receive.
 */
interface TtsPlaybackEngine {
    val isExternal: Boolean

    suspend fun initialize(): Result<Unit>

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

    /** Pauses buffered audio when supported; system TTS implementations may stop instead. */
    suspend fun pause()

    /** Returns true only when buffered audio resumed without re-synthesizing the active text. */
    suspend fun resume(): Boolean

    suspend fun stop()

    suspend fun setSpeechRate(rate: Float)

    suspend fun setPitch(pitch: Float)

    fun setListener(listener: TtsPlaybackListener)

    fun shutdown()
}

interface TtsPlaybackListener {
    fun onStart(utteranceId: String)

    fun onDone(utteranceId: String)

    fun onError(utteranceId: String, throwable: Throwable)

    /**
     * A transient system interruption paused speech. The controller retains the current segment
     * and may restart it when the user resumes.
     */
    fun onPlaybackInterrupted()

    /** Reports rendered PCM progress for exact external-audio resume positions. */
    fun onProgress(utteranceId: String, cacheKey: String, pcmFrameOffset: Long) = Unit
}
