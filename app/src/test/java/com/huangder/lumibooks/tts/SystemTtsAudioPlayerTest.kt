package com.huangder.lumibooks.tts

import android.media.AudioFormat
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SystemTtsAudioPlayerTest {
    @Test
    fun busyMainThreadDoesNotBlockAudioWritesForRenderedRanges() = runTest {
        val mainQueue = ArrayDeque<Runnable>()
        Dispatchers.setMain(object : CoroutineDispatcher() {
            override fun dispatch(context: CoroutineContext, block: Runnable) {
                mainQueue.addLast(block)
            }
        })
        val track = BufferedTrack(frameBytes = 2)
        val player = SystemTtsAudioPlayer(backgroundScope) { track }
        var ranges = 0
        try {
            assertTrue(player.begin(24_000, AudioFormat.ENCODING_PCM_16BIT, 1))
            player.scheduleRange(0) { ranges++ }
            player.append(ByteArray(8) { 1 })
            runCurrent()
            track.render(4)
            // Keep main-thread callbacks queued while more PCM becomes available.
            player.append(ByteArray(8) { 2 })
            player.append(ByteArray(8) { 3 })
            advanceTimeBy(20)
            runCurrent()
            assertTrue(mainQueue.isNotEmpty())
            assertEquals(0, ranges)
            assertEquals(List<Byte>(8) { 1 } + List<Byte>(8) { 2 } + List<Byte>(8) { 3 }, track.bytes)
            do {
                while (mainQueue.isNotEmpty()) mainQueue.removeFirst().run()
                runCurrent()
            } while (mainQueue.isNotEmpty())
            assertEquals(1, ranges)
        } finally {
            player.stop()
            while (mainQueue.isNotEmpty()) mainQueue.removeFirst().run()
            runCurrent()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun shortUtteranceStartsAndCompletesOnlyAfterItsSpeechIsRendered() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val track = BufferedTrack(frameBytes = 2)
        val player = SystemTtsAudioPlayer(backgroundScope) { track }
        var completions = 0
        var ranges = 0
        try {
            player.setCallbacks({ completions++ }, { throw AssertionError(it) })
            assertTrue(player.begin(24_000, AudioFormat.ENCODING_PCM_16BIT, 1))
            player.scheduleRange(0) { ranges++ }
            player.append(byteArrayOf(1, 2, 3, 4))
            runCurrent()
            track.render(2)
            assertEquals(0L, track.playbackHeadPosition) // Too short to start the native track.
            player.finish()
            player.finish()
            runCurrent()
            assertEquals(listOf<Byte>(1, 2, 3, 4, 0, 0, 0, 0), track.bytes)
            assertEquals(0, completions)
            assertEquals(0, ranges)
            track.render(2)
            advanceTimeBy(10)
            runCurrent()
            assertEquals(1, completions)
            assertEquals(1, ranges)
        } finally {
            player.stop()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun stereoUnsignedPcmUsesMidpointSilenceAndPreservesAllSpeechBytes() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val track = BufferedTrack(frameBytes = 2, maxWriteBytes = 1)
        val player = SystemTtsAudioPlayer(backgroundScope) { track }
        try {
            assertTrue(player.begin(16_000, AudioFormat.ENCODING_PCM_8BIT, 2))
            player.append(byteArrayOf(12))
            player.append(byteArrayOf(34))
            player.finish()
            runCurrent()
            assertEquals(listOf<Byte>(12, 34) + List(6) { 0x80.toByte() }, track.bytes)
        } finally {
            player.stop()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun fullBufferIsNotPaddedAndMustDrainBeforeCompletion() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val track = BufferedTrack(frameBytes = 2)
        val player = SystemTtsAudioPlayer(backgroundScope) { track }
        var completions = 0
        try {
            player.setCallbacks({ completions++ }, { throw AssertionError(it) })
            assertTrue(player.begin(24_000, AudioFormat.ENCODING_PCM_16BIT, 1))
            player.append(ByteArray(12) { 7 })
            player.finish()
            runCurrent()
            assertEquals(List<Byte>(12) { 7 }, track.bytes)
            track.render(5)
            advanceTimeBy(10)
            runCurrent()
            assertEquals(0, completions)
            track.render(1)
            advanceTimeBy(10)
            runCurrent()
            assertEquals(1, completions)
        } finally {
            player.stop()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun pausedShortUtteranceWaitsForResumeAndStopCancelsCompletion() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val track = BufferedTrack(frameBytes = 2)
        val player = SystemTtsAudioPlayer(backgroundScope) { track }
        var completions = 0
        try {
            player.setCallbacks({ completions++ }, { throw AssertionError(it) })
            assertTrue(player.begin(24_000, AudioFormat.ENCODING_PCM_16BIT, 1))
            player.pause()
            player.append(byteArrayOf(1, 2))
            player.finish()
            runCurrent()
            assertTrue(track.bytes.isEmpty())
            assertTrue(player.resume())
            advanceTimeBy(10)
            runCurrent()
            assertEquals(8, track.bytes.size)
            assertEquals(0, completions)
            player.stop()
            track.render(4)
            advanceTimeBy(100)
            runCurrent()
            assertEquals(0, completions)
        } finally {
            player.stop()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun consecutiveSentencesReuseTheTrackAndKeepTheirCompletionSeparate() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val track = BufferedTrack(frameBytes = 2)
        var creations = 0
        val player = SystemTtsAudioPlayer(backgroundScope) { creations++; track }
        var completions = 0
        try {
            player.setCallbacks({ completions++ }, { throw AssertionError(it) })
            repeat(2) {
                assertTrue(player.begin(24_000, AudioFormat.ENCODING_PCM_16BIT, 1))
                player.append(ByteArray(8) { 7 })
                player.finish()
                runCurrent()
                track.render(4)
                advanceTimeBy(10)
                runCurrent()
                assertEquals(it + 1, completions)
            }
            assertEquals(1, creations)
        } finally {
            player.stop()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun loweredStartThresholdAvoidsPaddingToTheFullBuffer() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val track = BufferedTrack(frameBytes = 2, startThresholdInFrames = 1)
        val player = SystemTtsAudioPlayer(backgroundScope) { track }
        var rendered = 0
        try {
            player.setCallbacks(null, { throw AssertionError(it) }, { rendered++ })
            player.begin(24_000, AudioFormat.ENCODING_PCM_16BIT, 1)
            player.append(byteArrayOf(1, 2))
            player.finish()
            runCurrent()
            assertEquals(listOf<Byte>(1, 2), track.bytes)
            assertEquals(0, rendered)
            track.render(1)
            advanceTimeBy(10)
            runCurrent()
            assertEquals(1, rendered)
        } finally {
            player.stop()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun playbackHeadStallReportsErrorAndUserPauseDoesNotTimeout() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val track = BufferedTrack(frameBytes = 2)
        val player = SystemTtsAudioPlayer(backgroundScope, monotonicMs = { testScheduler.currentTime }) { track }
        val errors = mutableListOf<Throwable>()
        try {
            player.setCallbacks(null, errors::add)
            player.begin(24_000, AudioFormat.ENCODING_PCM_16BIT, 1)
            player.append(ByteArray(8))
            player.finish()
            runCurrent()
            player.pause()
            advanceTimeBy(10_000)
            runCurrent()
            assertTrue(errors.isEmpty())
            player.resume()
            advanceTimeBy(5_010)
            runCurrent()
            assertEquals(1, errors.size)
            assertTrue(errors.single().message.orEmpty().contains("playback made no progress"))
        } finally {
            player.stop()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun blockedWriteTimesOutAndNegativeWriteFailsImmediately() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        for (writeResult in listOf(0, -6)) {
            val player = SystemTtsAudioPlayer(backgroundScope, monotonicMs = { testScheduler.currentTime }) {
                BufferedTrack(2, writeResult = writeResult)
            }
            val errors = mutableListOf<Throwable>()
            try {
                player.setCallbacks(null, errors::add)
                player.begin(24_000, AudioFormat.ENCODING_PCM_16BIT, 1)
                player.append(ByteArray(8))
                runCurrent()
                if (writeResult == 0) {
                    advanceTimeBy(5_010)
                    runCurrent()
                }
                assertEquals(1, errors.size)
            } finally {
                player.stop()
            }
        }
        Dispatchers.resetMain()
    }

    @Test
    fun replacementCancelsOldCallbacksAndTrackInitializationCanFail() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val track = BufferedTrack(2)
        val player = SystemTtsAudioPlayer(backgroundScope) { track }
        var oldCompletions = 0
        var newCompletions = 0
        try {
            player.setCallbacks({ oldCompletions++ }, { throw AssertionError(it) })
            player.begin(24_000, AudioFormat.ENCODING_PCM_16BIT, 1)
            player.append(ByteArray(8))
            player.finish()
            runCurrent()
            player.setCallbacks({ newCompletions++ }, { throw AssertionError(it) })
            player.begin(24_000, AudioFormat.ENCODING_PCM_16BIT, 1)
            player.append(ByteArray(8))
            player.finish()
            runCurrent()
            track.render(4)
            advanceTimeBy(10)
            runCurrent()
            assertEquals(0, oldCompletions)
            assertEquals(1, newCompletions)
            val failed = SystemTtsAudioPlayer(backgroundScope) { null }
            assertEquals(false, failed.begin(24_000, AudioFormat.ENCODING_PCM_16BIT, 1))
            failed.stop()
        } finally {
            player.stop()
            Dispatchers.resetMain()
        }
    }

    /** Models a streaming device that cannot render until its initial buffer is full. */
    private class BufferedTrack(
        private val frameBytes: Int,
        private val maxWriteBytes: Int = Int.MAX_VALUE,
        override val startThresholdInFrames: Int = 4,
        private val writeResult: Int? = null
    ) : SystemTtsAudioPlayer.PcmTrack {
        override val bufferSizeInFrames = 4
        override var playbackHeadPosition = 0L
        val bytes = mutableListOf<Byte>()
        private var playing = false

        fun render(frames: Int) {
            if (playing && bytes.size / frameBytes >= startThresholdInFrames) {
                playbackHeadPosition = minOf(playbackHeadPosition + frames, (bytes.size / frameBytes).toLong())
            }
        }

        override fun play() { playing = true }
        override fun pause() { playing = false }
        override fun flush() { playbackHeadPosition = 0; bytes.clear() }
        override fun release() { playing = false }
        override fun write(bytes: ByteArray, offset: Int, size: Int): Int {
            writeResult?.let { return it }
            val count = minOf(size, maxWriteBytes)
            this.bytes.addAll(bytes.slice(offset until offset + count))
            return count
        }
    }
}
