package com.huangder.lumibooks.tts

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Plays PCM delivered by TextToSpeech without letting the selected engine own the output stream. */
internal class SystemTtsAudioPlayer(
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val createTrack: (PcmFormat) -> PcmTrack? = ::createAndroidTrack
) {
    @Volatile
    private var track: PcmTrack? = null
    private var worker: Job? = null
    private var input: Channel<ByteArray>? = null
    private var frameBytes = 2
    private var writtenBytes = 0L
    private var writtenFrames = 0L
    @Volatile
    private var paused = false
    @Volatile
    private var generation = 0L
    @Volatile
    private var inputFinished = false
    private var onDone: (() -> Unit)? = null
    private var onError: ((Throwable) -> Unit)? = null
    private val ranges = ArrayDeque<Pair<Long, () -> Unit>>()
    private val rangeLock = Any()

    internal interface PcmTrack {
        val bufferSizeInFrames: Int
        val playbackHeadPosition: Long
        fun play()
        fun pause()
        fun flush()
        fun release()
        fun write(bytes: ByteArray, offset: Int, size: Int): Int
    }

    data class PcmFormat(val sampleRate: Int, val encoding: Int, val channels: Int) {
        val channelMask: Int
            get() = if (channels == 1) AudioFormat.CHANNEL_OUT_MONO else AudioFormat.CHANNEL_OUT_STEREO
        val frameBytes: Int
            get() = channels * when (encoding) {
                AudioFormat.ENCODING_PCM_8BIT -> 1
                AudioFormat.ENCODING_PCM_24BIT_PACKED -> 3
                AudioFormat.ENCODING_PCM_FLOAT -> 4
                AudioFormat.ENCODING_PCM_16BIT -> 2
                AudioFormat.ENCODING_PCM_32BIT -> 4
                else -> 0
            }
    }

    fun begin(sampleRate: Int, encoding: Int, channels: Int): Boolean {
        stop()
        val pcm = PcmFormat(sampleRate, encoding, channels)
        if (sampleRate <= 0 || channels !in 1..2 || pcm.frameBytes <= 0) return false
        val created = createTrack(pcm) ?: return false
        track = created
        frameBytes = pcm.frameBytes
        writtenBytes = 0L
        writtenFrames = 0L
        inputFinished = false
        paused = false
        synchronized(rangeLock) { ranges.clear() }
        created.play()
        val channel = Channel<ByteArray>(Channel.UNLIMITED)
        input = channel
        val token = generation
        worker = scope.launch {
            try {
                for (chunk in channel) {
                    writeChunk(created, chunk, token)
                    dispatchRenderedRanges(created, token)
                }
                if (token == generation) {
                    val speechFrames = writtenFrames
                    // MODE_STREAM may wait for a full buffer before rendering any audio.
                    // Single words can end below that threshold, so prime the remainder with
                    // silence. Completion still follows the speech, not the padding.
                    if (writtenBytes > 0L && writtenFrames < created.bufferSizeInFrames) {
                        val paddingBytes = (created.bufferSizeInFrames.toLong() * pcm.frameBytes - writtenBytes).toInt()
                        val silence = if (pcm.encoding == AudioFormat.ENCODING_PCM_8BIT) 0x80.toByte() else 0.toByte()
                        writeChunk(created, ByteArray(paddingBytes) { silence }, token)
                    }
                    while (token == generation && (paused || created.playbackHeadPosition < speechFrames)) {
                        dispatchRenderedRanges(created, token)
                        delay(10)
                    }
                    dispatchRenderedRanges(created, token)
                    if (token == generation) withContext(Dispatchers.Main.immediate) {
                        if (token == generation) onDone?.invoke()
                    }
                }
            } catch (error: Throwable) {
                if (token == generation) withContext(Dispatchers.Main.immediate) { onError?.invoke(error) }
            }
        }
        return true
    }

    fun scheduleRange(frame: Long, callback: () -> Unit) {
        if (frame < 0L) return
        synchronized(rangeLock) { ranges.addLast(frame to callback) }
    }

    private suspend fun dispatchRenderedRanges(active: PcmTrack, token: Long) {
        val rendered = active.playbackHeadPosition
        while (true) {
            val callback = synchronized(rangeLock) {
                val first = ranges.firstOrNull()
                if (first != null && first.first <= rendered && (rendered > 0L || first.first > 0L)) {
                    ranges.removeFirst().second
                } else null
            } ?: break
            if (token != generation) return
            withContext(Dispatchers.Main.immediate) { callback() }
        }
    }

    fun append(bytes: ByteArray) {
        if (bytes.isEmpty() || inputFinished) return
        input?.trySend(bytes.copyOf())
    }

    fun finish() {
        if (inputFinished) return
        inputFinished = true
        input?.close()
    }

    private suspend fun writeChunk(active: PcmTrack, bytes: ByteArray, token: Long) {
        var offset = 0
        while (offset < bytes.size && token == generation) {
            while (paused && token == generation) delay(10)
            if (token != generation) return
            val written = active.write(bytes, offset, bytes.size - offset)
            when {
                written > 0 -> {
                    offset += written
                    writtenBytes += written
                    writtenFrames = writtenBytes / frameBytes
                }
                written == 0 -> delay(2)
                else -> throw IllegalStateException("AudioTrack.write returned $written")
            }
        }
    }

    fun pause() {
        paused = true
        runCatching { track?.pause() }
    }

    fun resume(): Boolean {
        val active = track ?: return false
        return runCatching {
            active.play()
            paused = false
            true
        }.getOrDefault(false)
    }

    fun stop() {
        generation++
        input?.close()
        worker?.cancel()
        worker = null
        input = null
        track?.let { runCatching { it.pause() }; runCatching { it.flush() }; it.release() }
        track = null
        synchronized(rangeLock) { ranges.clear() }
        paused = false
        inputFinished = false
    }

    fun setCallbacks(onDone: (() -> Unit)?, onError: ((Throwable) -> Unit)?) {
        this.onDone = onDone
        this.onError = onError
    }

    fun release() {
        stop()
        scope.coroutineContext[Job]?.cancel()
    }

    companion object {
        private fun createAndroidTrack(pcm: PcmFormat): PcmTrack? {
            val min = AudioTrack.getMinBufferSize(pcm.sampleRate, pcm.channelMask, pcm.encoding)
            if (min <= 0) return null
            val created = runCatching {
                AudioTrack(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build(),
                    AudioFormat.Builder()
                        .setSampleRate(pcm.sampleRate)
                        .setChannelMask(pcm.channelMask)
                        .setEncoding(pcm.encoding)
                        .build(),
                    maxOf(min * 2, pcm.frameBytes * 2_400),
                    AudioTrack.MODE_STREAM,
                    AudioManager.AUDIO_SESSION_ID_GENERATE
                )
            }.getOrNull() ?: return null
            if (created.state != AudioTrack.STATE_INITIALIZED) {
                created.release()
                return null
            }
            return object : PcmTrack {
                override val bufferSizeInFrames get() = created.bufferSizeInFrames
                override val playbackHeadPosition get() = created.playbackHeadPosition.toLong() and 0xffffffffL
                override fun play() = created.play()
                override fun pause() = created.pause()
                override fun flush() = created.flush()
                override fun release() = created.release()
                override fun write(bytes: ByteArray, offset: Int, size: Int) =
                    created.write(bytes, offset, size, AudioTrack.WRITE_NON_BLOCKING)
            }
        }
    }
}
