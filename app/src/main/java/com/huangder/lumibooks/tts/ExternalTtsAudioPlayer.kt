package com.huangder.lumibooks.tts

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Handler
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive

internal const val EXTERNAL_TTS_MIN_PLAYBACK_RATE = 0.5f
internal const val EXTERNAL_TTS_MAX_PLAYBACK_RATE = 2f

internal fun normalizeExternalTtsPlaybackRate(rate: Float): Float = rate.coerceIn(
    EXTERNAL_TTS_MIN_PLAYBACK_RATE,
    EXTERNAL_TTS_MAX_PLAYBACK_RATE
)

/**
 * Streams 24 kHz mono PCM16LE through [AudioTrack] while owning audio focus.
 *
 * Focus loss (permanent or transient) pauses playback and notifies the playback engine via
 * [onFocusLost]. Playback remains paused after focus returns until the controller resumes it.
 */
class ExternalTtsAudioPlayer(
    @ApplicationContext private val context: Context,
    private val audioHandler: Handler
) {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val focusLock = Any()

    @Volatile
    private var track: AudioTrack? = null

    @Volatile
    private var focusRequest: AudioFocusRequest? = null

    @Volatile
    private var focusChangeListener: AudioManager.OnAudioFocusChangeListener? = null
    @Volatile
    private var focusGeneration = 0L

    @Volatile
    private var hasFocus = false

    @Volatile
    private var paused = false

    @Volatile
    private var playbackRate = 1f

    @Volatile
    private var lastSafeRate = 1f

    @Volatile
    private var writtenFrames = 0L

    @Volatile
    private var initialFrameOffset = 0L

    private var onFocusLost: (() -> Unit)? = null

    fun prepare(initialFrameOffset: Long = 0L): Boolean {
        abandonFocus()
        releaseTrack()

        val newTrack = createTrack() ?: return false
        if (newTrack.state != AudioTrack.STATE_INITIALIZED) {
            newTrack.release()
            return false
        }

        track = newTrack
        if (!requestFocus()) {
            releaseTrack()
            abandonFocus()
            return false
        }
        this.initialFrameOffset = initialFrameOffset.coerceAtLeast(0L)
        writtenFrames = 0L
        paused = false
        applyRateToTrack(playbackRate)
        newTrack.play()
        return true
    }
    /** Writes a single, whole PCM16LE buffer. Cancellation-aware. */
    suspend fun write(chunk: ByteArray): Boolean {
        if (chunk.isEmpty() || chunk.size % PCM_FRAME_BYTES != 0) return false
        val activeTrack = track ?: return false
        return writeInternal(activeTrack, chunk, 0, chunk.size)
    }

    suspend fun drain(): Boolean {
        val activeTrack = track ?: return false
        val targetFrames = writtenFrames
        while (track === activeTrack) {
            if (!paused) {
                try {
                    if (activeTrack.playbackHeadPosition.toLong() >= targetFrames) return true
                } catch (_: IllegalStateException) {
                    return false
                }
            }
            delay(DRAIN_POLL_INTERVAL_MS)
        }
        return false
    }

    /**
     * Applies the playback speed locally via [android.media.PlaybackParams], keeping pitch at
     * 1.0 to avoid chipmunk effect. On failure the previous safe value is silently restored.
     */
    fun setPlaybackRate(rate: Float) {
        val clamped = normalizeExternalTtsPlaybackRate(rate)
        playbackRate = clamped
        applyRateToTrack(clamped)
    }

    private fun applyRateToTrack(rate: Float) {
        val activeTrack = track ?: return
        try {
            val params = android.media.PlaybackParams()
                .setSpeed(rate)
                .setPitch(1f)
            activeTrack.playbackParams = params
            lastSafeRate = rate
        } catch (_: IllegalArgumentException) {
            playbackRate = lastSafeRate
        } catch (_: IllegalStateException) {
            playbackRate = lastSafeRate
        }
    }

    fun pause() {
        paused = true
        track?.pause()
    }

    /** Returns true only when the track has focus and play succeeded. */
    fun resume(): Boolean {
        val activeTrack = track ?: return false
        if (!hasFocus) {
            abandonFocus()
            if (!requestFocus()) return false
        }
        return try {
            activeTrack.play()
            paused = false
            true
        } catch (_: IllegalStateException) {
            false
        }
    }

    fun stop() {
        paused = true
        initialFrameOffset = 0L
        writtenFrames = 0L
        releaseTrack()
        abandonFocus()
    }

    fun release() = stop()

    /** Invoked on any audio-focus loss (permanent or transient). */
    fun setOnFocusLost(callback: (() -> Unit)?) {
        onFocusLost = callback
    }

    /** Absolute rendered frame offset within the active cached utterance. */
    fun renderedFrameOffset(): Long {
        val activeTrack = track ?: return initialFrameOffset
        return initialFrameOffset + activeTrack.playbackHeadPosition.toLong()
    }

    private fun createTrack(): AudioTrack? {
        val minimumBufferSize = AudioTrack.getMinBufferSize(
            SAMPLE_RATE,
            CHANNEL_CONFIG,
            AUDIO_FORMAT
        )
        if (minimumBufferSize <= 0) return null
        return AudioTrack(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build(),
            AudioFormat.Builder()
                .setSampleRate(SAMPLE_RATE)
                .setChannelMask(CHANNEL_CONFIG)
                .setEncoding(AUDIO_FORMAT)
                .build(),
            maxOf(minimumBufferSize * 2, MIN_BUFFER_SIZE),
            AudioTrack.MODE_STREAM,
            AudioManager.AUDIO_SESSION_ID_GENERATE
        )
    }

    private fun requestFocus(): Boolean {
        val generation = ++focusGeneration
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()
        val listener = AudioManager.OnAudioFocusChangeListener { change ->
            if (generation != focusGeneration) return@OnAudioFocusChangeListener
            when (change) {
                AudioManager.AUDIOFOCUS_LOSS -> {
                    hasFocus = false
                    paused = true
                    track?.pause()
                    onFocusLost?.invoke()
                }
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                    hasFocus = false
                    paused = true
                    track?.pause()
                    onFocusLost?.invoke()
                }
                AudioManager.AUDIOFOCUS_GAIN -> {
                    hasFocus = true
                }
            }
        }
        focusChangeListener = listener

        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(attributes)
            .setOnAudioFocusChangeListener(listener, audioHandler)
            .setWillPauseWhenDucked(true)
            .build()
        focusRequest = request
        val result = audioManager.requestAudioFocus(request)
        hasFocus = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        return hasFocus
    }

    private fun abandonFocus() {
        synchronized(focusLock) {
            focusGeneration++
            hasFocus = false
            focusRequest?.let(audioManager::abandonAudioFocusRequest)
            focusRequest = null
            focusChangeListener = null
        }
    }

    private suspend fun writeInternal(
        activeTrack: AudioTrack,
        data: ByteArray,
        offset: Int,
        size: Int
    ): Boolean {
        var written = 0
        while (written < size) {
            if (track !== activeTrack) return false
            if (paused) {
                delay(DRAIN_POLL_INTERVAL_MS)
                continue
            }
            val count = try {
                activeTrack.write(
                    data,
                    offset + written,
                    size - written,
                    AudioTrack.WRITE_NON_BLOCKING
                )
            } catch (_: IllegalStateException) {
                return false
            }
            when {
                count > 0 -> written += count
                count == 0 -> delay(WRITE_RETRY_INTERVAL_MS)
                else -> return false
            }
            kotlinx.coroutines.currentCoroutineContext().ensureActive()
        }
        writtenFrames += size / PCM_FRAME_BYTES
        return true
    }

    private fun releaseTrack() {
        track?.let { activeTrack ->
            try {
                activeTrack.pause()
                activeTrack.flush()
            } catch (_: IllegalStateException) {
                // The track was already released.
            }
            activeTrack.release()
        }
        track = null
    }

    private companion object {
        const val SAMPLE_RATE = 24_000
        const val CHANNEL_CONFIG = AudioFormat.CHANNEL_OUT_MONO
        const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        const val MIN_BUFFER_SIZE = 4_800
        const val PCM_FRAME_BYTES = 2
        const val WRITE_RETRY_INTERVAL_MS = 2L
        const val DRAIN_POLL_INTERVAL_MS = 10L
    }
}
