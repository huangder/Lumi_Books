package com.huangder.lumibooks.tts

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import kotlinx.coroutines.CancellationException
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
    private val monotonicMs: () -> Long = { System.nanoTime() / 1_000_000L },
    private val createTrack: (PcmFormat) -> PcmTrack? = ::createAndroidTrack
) {
    @Volatile
    private var track: PcmTrack? = null
    private var worker: Job? = null
    private var renderMonitor: Job? = null
    private var input: Channel<ByteArray>? = null
    private var format: PcmFormat? = null
    @Volatile
    private var completed = false
    @Volatile
    private var paused = false
    @Volatile
    private var generation = 0L
    @Volatile
    private var inputFinished = false
    private var onDone: (() -> Unit)? = null
    private var onError: ((Throwable) -> Unit)? = null
    private var onRenderedStart: (() -> Unit)? = null

    fun renderedFrameOffset(): Long = track?.playbackHeadPosition ?: 0L
    private val ranges = ArrayDeque<Pair<Long, () -> Unit>>()
    private val rangeLock = Any()

    internal interface PcmTrack {
        val bufferSizeInFrames: Int
        val startThresholdInFrames: Int get() = bufferSizeInFrames
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
        val pcm = PcmFormat(sampleRate, encoding, channels)
        if (sampleRate <= 0 || channels !in 1..2 || pcm.frameBytes <= 0) return false
        val reusable = track?.takeIf { completed && format == pcm }
        stop(releaseTrack = reusable == null)
        val created = reusable ?: createTrack(pcm) ?: return false
        track = created
        format = pcm
        completed = false
        inputFinished = false
        paused = false
        synchronized(rangeLock) { ranges.clear() }
        created.play()
        val channel = Channel<ByteArray>(Channel.UNLIMITED)
        input = channel
        val token = generation
        val doneCallback = onDone
        val errorCallback = onError
        val renderedCallback = onRenderedStart
        renderMonitor = scope.launch {
            while (token == generation) {
                if (created.playbackHeadPosition > 0L) {
                    withContext(Dispatchers.Main.immediate) {
                        if (token == generation) renderedCallback?.invoke()
                    }
                    return@launch
                }
                delay(10)
            }
        }
        worker = scope.launch {
            var writtenBytes = 0L
            suspend fun write(bytes: ByteArray) {
                writeChunk(created, bytes, token) { writtenBytes += it }
            }
            try {
                for (chunk in channel) {
                    write(chunk)
                    dispatchRenderedRanges(created, token)
                }
                if (token == generation) {
                    val speechFrames = writtenBytes / pcm.frameBytes
                    // MODE_STREAM may wait for a full buffer before rendering any audio.
                    // Single words can end below that threshold, so prime the remainder with
                    // silence. Completion still follows the speech, not the padding.
                    if (writtenBytes > 0L && speechFrames < created.startThresholdInFrames) {
                        val paddingBytes = (created.startThresholdInFrames.toLong() * pcm.frameBytes - writtenBytes).toInt()
                        val silence = if (pcm.encoding == AudioFormat.ENCODING_PCM_8BIT) 0x80.toByte() else 0.toByte()
                        write(ByteArray(paddingBytes) { silence })
                    }
                    var lastHead = created.playbackHeadPosition
                    var lastProgressMs = monotonicMs()
                    while (token == generation && (paused || created.playbackHeadPosition < speechFrames)) {
                        val head = created.playbackHeadPosition
                        if (paused || head != lastHead) {
                            lastProgressMs = monotonicMs()
                            lastHead = head
                        } else if (monotonicMs() - lastProgressMs >= STALL_TIMEOUT_MS) {
                            throw IllegalStateException("AudioTrack playback made no progress for 5 seconds")
                        }
                        dispatchRenderedRanges(created, token)
                        delay(10)
                    }
                    dispatchRenderedRanges(created, token)
                    if (token == generation) withContext(Dispatchers.Main.immediate) {
                        if (token == generation) {
                            completed = true
                            doneCallback?.invoke()
                        }
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                if (token == generation) withContext(Dispatchers.Main.immediate) {
                    if (token == generation) errorCallback?.invoke(error)
                }
            }
        }
        return true
    }

    fun scheduleRange(frame: Long, callback: () -> Unit) {
        if (frame < 0L) return
        synchronized(rangeLock) { ranges.addLast(frame to callback) }
    }

    private suspend fun dispatchRenderedRanges(active: PcmTrack, token: Long) {
        if (token != generation) return
        val rendered = active.playbackHeadPosition
        while (true) {
            val callback = synchronized(rangeLock) {
                if (token != generation) return
                val first = ranges.firstOrNull()
                if (first != null && first.first <= rendered && (rendered > 0L || first.first > 0L)) {
                    ranges.removeFirst().second
                } else null
            } ?: break
            if (token != generation) return
            withContext(Dispatchers.Main.immediate) { if (token == generation) callback() }
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

    private suspend fun writeChunk(active: PcmTrack, bytes: ByteArray, token: Long, onWrite: (Int) -> Unit) {
        var offset = 0
        var lastProgressMs = monotonicMs()
        while (offset < bytes.size && token == generation) {
            while (paused && token == generation) {
                lastProgressMs = monotonicMs()
                delay(10)
            }
            if (token != generation) return
            val written = active.write(bytes, offset, bytes.size - offset)
            when {
                written > 0 -> {
                    offset += written
                    onWrite(written)
                    lastProgressMs = monotonicMs()
                }
                written == 0 -> {
                    if (monotonicMs() - lastProgressMs >= STALL_TIMEOUT_MS) {
                        throw IllegalStateException("AudioTrack.write made no progress for 5 seconds")
                    }
                    delay(2)
                }
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

    fun stop() = stop(releaseTrack = true)

    private fun stop(releaseTrack: Boolean) {
        generation++
        input?.close()
        worker?.cancel()
        worker = null
        renderMonitor?.cancel()
        renderMonitor = null
        input = null
        track?.let {
            runCatching { it.pause() }
            runCatching { it.flush() }
            if (releaseTrack) runCatching { it.release() }
        }
        if (releaseTrack) {
            track = null
            format = null
        }
        completed = false
        synchronized(rangeLock) { ranges.clear() }
        paused = false
        inputFinished = false
    }

    fun setCallbacks(onDone: (() -> Unit)?, onError: ((Throwable) -> Unit)?, onRenderedStart: (() -> Unit)? = null) {
        this.onDone = onDone
        this.onError = onError
        this.onRenderedStart = onRenderedStart
    }

    fun release() {
        stop()
        scope.coroutineContext[Job]?.cancel()
    }

    companion object {
        internal const val STALL_TIMEOUT_MS = 5_000L
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
                    maxOf(min, pcm.frameBytes * (pcm.sampleRate / 25)),
                    AudioTrack.MODE_STREAM,
                    AudioManager.AUDIO_SESSION_ID_GENERATE
                )
            }.getOrNull() ?: return null
            if (created.state != AudioTrack.STATE_INITIALIZED) {
                created.release()
                return null
            }
            if (Build.VERSION.SDK_INT >= 31) {
                created.setStartThresholdInFrames(minOf(created.bufferSizeInFrames,
                    maxOf(1, pcm.sampleRate / 50)))
            }
            return object : PcmTrack {
                override val bufferSizeInFrames get() = created.bufferSizeInFrames
                override val startThresholdInFrames get() = if (Build.VERSION.SDK_INT >= 31) {
                    created.startThresholdInFrames
                } else created.bufferSizeInFrames
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
