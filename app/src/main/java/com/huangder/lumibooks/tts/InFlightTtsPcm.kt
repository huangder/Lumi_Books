package com.huangder.lumibooks.tts

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/** Single-consumer handoff: preserves every PCM byte, with at most 512 KiB queued.
 * An unclaimed prefetch applies backpressure instead of retaining an entire sentence.
 */
internal class InFlightTtsPcm(scope: CoroutineScope, source: Flow<PcmChunk>) {
    private val channel = Channel<PcmChunk>(BUFFER_CHUNKS)
    private val consumed = AtomicBoolean(false)
    private val producer = scope.launch {
        try {
            source.collect { chunk ->
                if (chunk.isEmpty() || chunk.size % 2 != 0) throw ExternalTtsException.InvalidAudio
                var offset = 0
                while (offset < chunk.size) {
                    val end = minOf(offset + CHUNK_BYTES, chunk.size)
                    channel.send(chunk.copyOfRange(offset, end))
                    offset = end
                }
            }
            channel.close()
        } catch (error: Throwable) {
            channel.close(error)
            if (error is CancellationException) throw error
        }
    }

    val chunks: Flow<PcmChunk> = flow {
        check(consumed.compareAndSet(false, true)) { "In-flight PCM has already been claimed" }
        try {
            for (chunk in channel) emit(chunk)
        } finally {
            cancel()
        }
    }

    suspend fun awaitCompletion() = producer.join()

    fun cancel() {
        producer.cancel()
        channel.cancel()
    }

    internal companion object {
        const val CHUNK_BYTES = 16_384
        const val BUFFER_CHUNKS = 32
    }
}
