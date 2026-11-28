package com.huangder.lumibooks.tts

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class InFlightTtsPcmTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun completedPrefetchRetainsEveryBufferedByteUntilClaimed() = runTest {
        val expected = ByteArray(InFlightTtsPcm.CHUNK_BYTES * 2 + 2) { it.toByte() }
        val stream = InFlightTtsPcm(this, flow { emit(expected) })
        stream.awaitCompletion()
        val actual = mutableListOf<Byte>()
        stream.chunks.collect {
            assertTrue(it.size <= InFlightTtsPcm.CHUNK_BYTES)
            actual.addAll(it.toList())
        }
        assertArrayEquals(expected, actual.toByteArray())
    }

    @Test fun playsReceivedPrefixBeforeSlowTailAndCachesWholeSingleRequest() = runTest {
        val cache = ExternalTtsAudioCache(temporary.newFolder())
        val key = "a".repeat(64)
        val tail = CompletableDeferred<Unit>()
        var requests = 0
        val stream = InFlightTtsPcm(this, cache.writeThrough(key, flow {
            requests++
            emit(byteArrayOf(1, 2))
            tail.await()
            emit(byteArrayOf(3, 4))
        }, 1024))
        runCurrent()
        assertFalse(cache.contains(key))
        val received = mutableListOf<Byte>()
        val playback = launch { stream.chunks.collect { received.addAll(it.toList()) } }
        runCurrent()
        assertEquals(listOf<Byte>(1, 2), received)
        assertFalse(playback.isCompleted)
        tail.complete(Unit)
        playback.join()
        assertEquals(listOf<Byte>(1, 2, 3, 4), received)
        assertEquals(1, requests)
        assertTrue(cache.contains(key))
    }

    @Test fun disconnectedTailPropagatesWithoutPartialCacheAndRetryCanComplete() = runTest {
        val directory = temporary.newFolder()
        val cache = ExternalTtsAudioCache(directory)
        val key = "b".repeat(64)
        val disconnect = CompletableDeferred<Unit>()
        val stream = InFlightTtsPcm(this, cache.writeThrough(key, flow {
            emit(byteArrayOf(1, 2))
            disconnect.await()
            throw IOException("disconnected")
        }, 1024))
        val bytes = mutableListOf<Byte>()
        var failure: Throwable? = null
        val playback = launch {
            try { stream.chunks.collect { bytes.addAll(it.toList()) } }
            catch (error: IOException) { failure = error }
        }
        runCurrent()
        assertEquals(listOf<Byte>(1, 2), bytes)
        disconnect.complete(Unit)
        playback.join()
        assertTrue(failure is IOException)
        assertFalse(cache.contains(key))
        assertTrue(directory.listFiles().orEmpty().isEmpty())
        assertTrue(cache.store(key, flow { emit(byteArrayOf(5, 6)) }, 1024))
        assertTrue(cache.contains(key))
    }

    @Test fun unclaimedBufferIsBoundedAndCancellationRemovesPartialFile() = runTest {
        val directory = temporary.newFolder()
        val cache = ExternalTtsAudioCache(directory)
        var chunksProduced = 0
        var cleaned = false
        val stream = InFlightTtsPcm(this, cache.writeThrough("c".repeat(64), flow {
            try {
                repeat(100) { chunksProduced++; emit(ByteArray(InFlightTtsPcm.CHUNK_BYTES)) }
            } finally { cleaned = true }
        }, 10_000_000))
        runCurrent()
        assertEquals(InFlightTtsPcm.BUFFER_CHUNKS + 1, chunksProduced)
        stream.cancel()
        stream.awaitCompletion()
        assertTrue(cleaned)
        assertTrue(directory.listFiles().orEmpty().isEmpty())
    }

    @Test fun cancellingPlaybackCancelsStalledSourceAndCleansCache() = runTest {
        val directory = temporary.newFolder()
        val cache = ExternalTtsAudioCache(directory)
        val tail = CompletableDeferred<Unit>()
        var cleaned = false
        val stream = InFlightTtsPcm(this, cache.writeThrough("d".repeat(64), flow {
            try { emit(byteArrayOf(1, 2)); tail.await() }
            finally { cleaned = true }
        }, 1024))
        val playback = launch { stream.chunks.collect() }
        runCurrent()
        playback.cancelAndJoin()
        stream.awaitCompletion()
        assertTrue(cleaned)
        assertTrue(directory.listFiles().orEmpty().isEmpty())
    }
}
