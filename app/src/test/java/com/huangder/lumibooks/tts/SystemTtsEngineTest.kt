package com.huangder.lumibooks.tts

import android.media.AudioFormat
import android.media.AudioTrack
import android.content.Intent
import android.content.pm.ResolveInfo
import android.content.pm.ServiceInfo
import android.os.Bundle
import android.os.Looper
import android.speech.tts.TextToSpeech
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.time.Duration
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.TestScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadows.ShadowTextToSpeech

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], shadows = [SystemTtsEngineTest.RecordingTts::class])
class SystemTtsEngineTest {
    @Test
    fun nativeMediaRoutingSurvivesQueuedHandoffAndStopsOnPauseErrorCompletionAndShutdown() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val tracks = mutableListOf<AudioTrack>()
        val routing = NativeTtsMediaRouting { NativeTtsMediaRouting.createRoutingTrack().also(tracks::add) }
        val player = SystemTtsAudioPlayer(backgroundScope) { throw AssertionError("Unexpected local speech") }
        val engine = TtsEngine(ApplicationProvider.getApplicationContext(), player, routing)
        engine.setListener(listener(mutableListOf(), mutableListOf(), mutableListOf()))
        try {
            selectMultiTts(engine)
            initializeEngine(engine)
            val callbacks = (shadowOf(ShadowTextToSpeech.getLastTextToSpeechInstance()) as RecordingTts)
                .utteranceProgressListener
            engine.speak("First.", "first")
            assertTrue(tracks.isEmpty()) // Do not start routing merely for synthesis/initialization.
            callbacks.onStart("first")
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(AudioTrack.PLAYSTATE_PLAYING, tracks.single().playState)
            engine.queueNext("Next.", "next")
            callbacks.onStart("next")
            callbacks.onDone("first")
            shadowOf(Looper.getMainLooper()).idle()
            engine.stopForPageTurn()
            engine.speak("Next.", "next")
            assertEquals(1, tracks.size)
            assertEquals(AudioTrack.PLAYSTATE_PLAYING, tracks.single().playState)
            engine.pause()
            assertEquals(AudioTrack.STATE_UNINITIALIZED, tracks.single().state)
            callbacks.onStart("next") // An obsolete callback must not restart the routing track.
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(1, tracks.size)

            for (ending in listOf("done", "error", "stop", "shutdown")) {
                engine.speak("Restart.", ending)
                callbacks.onStart(ending)
                shadowOf(Looper.getMainLooper()).idle()
                assertEquals(AudioTrack.PLAYSTATE_PLAYING, tracks.last().playState)
                when (ending) {
                    "done" -> callbacks.onDone(ending)
                    "error" -> callbacks.onError(ending, TextToSpeech.ERROR_SYNTHESIS)
                    "stop" -> engine.stop()
                    else -> engine.shutdown()
                }
                shadowOf(Looper.getMainLooper()).idle()
                assertEquals(AudioTrack.STATE_UNINITIALIZED, tracks.last().state)
            }
        } finally { engine.shutdown(); player.release(); Dispatchers.resetMain() }
    }

    @Implements(TextToSpeech::class)
    class RecordingTts : ShadowTextToSpeech() {
        data class Request(val text: String, val id: String, val file: File)
        val requests = mutableListOf<Request>()
        data class SpokenRequest(val text: String, val queueMode: Int, val id: String)
        val spokenRequests = mutableListOf<SpokenRequest>()
        var stopCount = 0
        var rejectNextSpeak = false

        @Implementation
        override fun speak(text: CharSequence, queueMode: Int, params: Bundle?, utteranceId: String): Int {
            if (rejectNextSpeak) { rejectNextSpeak = false; return TextToSpeech.ERROR }
            spokenRequests += SpokenRequest(text.toString(), queueMode, utteranceId)
            return TextToSpeech.SUCCESS
        }

        @Implementation
        override fun stop(): Int {
            stopCount++
            return TextToSpeech.SUCCESS
        }

        @Implementation
        override fun synthesizeToFile(text: CharSequence, params: Bundle, file: File, utteranceId: String): Int {
            requests += Request(text.toString(), utteranceId, file)
            return TextToSpeech.SUCCESS
        }
    }

    @Test
    fun multiTtsQueuesOneSuccessorAndReplaysEarlyCallbacksOnlyOnAdoptionAcrossPageTurns() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val player = SystemTtsAudioPlayer(backgroundScope) { throw AssertionError("Unexpected local playback") }
        val engine = TtsEngine(ApplicationProvider.getApplicationContext(), player)
        val started = mutableListOf<String>()
        val done = mutableListOf<String>()
        val errors = mutableListOf<Throwable>()
        engine.setListener(listener(started, done, errors))
        try {
            selectMultiTts(engine)
            initializeEngine(engine)
            val shadow = shadowOf(ShadowTextToSpeech.getLastTextToSpeechInstance()) as RecordingTts
            val callbacks = shadow.utteranceProgressListener
            val stops = shadow.stopCount
            assertTrue(engine.supportsQueuedPlayback)
            engine.speak("First.", "first")
            assertTrue(engine.queueNext("Second.", "second").isSuccess)
            assertTrue(engine.queueNext("Overflow.", "overflow").isFailure)
            assertEquals(listOf("first", "second"), shadow.spokenRequests.map { it.id })
            callbacks.onStart("first")
            callbacks.onStart("second") // Android can deliver this before the controller handles first's done.
            callbacks.onStart("second")
            callbacks.onDone("second") // Even a very short successor can finish before adoption.
            callbacks.onDone("second")
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(listOf("first"), started)
            assertTrue(done.isEmpty())
            callbacks.onDone("first")
            shadowOf(Looper.getMainLooper()).idle()
            engine.stopForPageTurn()
            assertTrue(engine.speak("Second.", "second").isSuccess)
            assertEquals(listOf("first", "second"), started)
            assertEquals(listOf("first", "second"), done)
            assertEquals(2, shadow.spokenRequests.size)
            assertEquals(stops, shadow.stopCount)
            assertTrue(shadow.spokenRequests.all { it.queueMode == TextToSpeech.QUEUE_ADD })
            assertTrue(shadow.requests.isEmpty())
            assertTrue(errors.isEmpty())
        } finally { engine.shutdown(); player.release(); Dispatchers.resetMain() }
    }

    @Test
    fun multiTtsPauseAndStopInvalidateBothQueuedAudioAndBufferedCallbacks() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val player = SystemTtsAudioPlayer(backgroundScope) { throw AssertionError("Unexpected local playback") }
        val engine = TtsEngine(ApplicationProvider.getApplicationContext(), player)
        val started = mutableListOf<String>()
        val done = mutableListOf<String>()
        val errors = mutableListOf<Throwable>()
        engine.setListener(listener(started, done, errors))
        try {
            selectMultiTts(engine)
            initializeEngine(engine)
            val shadow = shadowOf(ShadowTextToSpeech.getLastTextToSpeechInstance()) as RecordingTts
            val callbacks = shadow.utteranceProgressListener
            for (pause in listOf(true, false)) {
                engine.speak("Current.", "current-$pause")
                engine.queueNext("Queued.", "queued-$pause")
                callbacks.onStart("queued-$pause")
                shadowOf(Looper.getMainLooper()).idle()
                if (pause) engine.pause() else engine.stop()
                callbacks.onStart("current-$pause")
                callbacks.onDone("current-$pause")
                callbacks.onDone("queued-$pause")
                callbacks.onError("queued-$pause", TextToSpeech.ERROR_SYNTHESIS)
                shadowOf(Looper.getMainLooper()).idle()
                assertEquals(false, engine.resume())
                assertTrue(started.isEmpty())
                assertTrue(done.isEmpty())
                assertTrue(errors.isEmpty())
            }
        } finally { engine.shutdown(); player.release(); Dispatchers.resetMain() }
    }

    @Test
    fun multiTtsQueuedFailuresDoNotInterruptCurrentSpeechAndAreDeliveredOnAdoption() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val player = SystemTtsAudioPlayer(backgroundScope) { throw AssertionError("Unexpected local playback") }
        val engine = TtsEngine(ApplicationProvider.getApplicationContext(), player)
        val started = mutableListOf<String>()
        val done = mutableListOf<String>()
        val errors = mutableListOf<Throwable>()
        engine.setListener(listener(started, done, errors))
        try {
            selectMultiTts(engine)
            initializeEngine(engine)
            val shadow = shadowOf(ShadowTextToSpeech.getLastTextToSpeechInstance()) as RecordingTts
            val callbacks = shadow.utteranceProgressListener
            engine.speak("Current.", "current")
            shadow.rejectNextSpeak = true
            assertTrue(engine.queueNext("Next.", "rejected").isFailure)
            assertTrue(engine.queueNext("Next.", "next").isSuccess)
            val stops = shadow.stopCount
            callbacks.onError("next", TextToSpeech.ERROR_SYNTHESIS)
            shadowOf(Looper.getMainLooper()).idle()
            assertTrue(errors.isEmpty())
            assertEquals(stops, shadow.stopCount)
            callbacks.onDone("current")
            shadowOf(Looper.getMainLooper()).idle()
            engine.speak("Next.", "next")
            assertEquals(1, errors.size)
            assertEquals(listOf("current"), done)
            assertEquals(2, shadow.spokenRequests.size)
        } finally { engine.shutdown(); player.release(); Dispatchers.resetMain() }
    }

    @Test
    fun multiTtsUsesNativePlaybackWithoutSpeculativeSynthesisOrStopsBetweenSentences() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val player = SystemTtsAudioPlayer(backgroundScope) { throw AssertionError("MultiTTS must own playback") }
        val engine = TtsEngine(ApplicationProvider.getApplicationContext(), player)
        val started = mutableListOf<String>()
        val done = mutableListOf<String>()
        val errors = mutableListOf<Throwable>()
        engine.setListener(listener(started, done, errors))
        try {
            selectMultiTts(engine)
            initializeEngine(engine)
            val shadow = shadowOf(ShadowTextToSpeech.getLastTextToSpeechInstance()) as RecordingTts
            val callbacks = shadow.utteranceProgressListener
            val initialStops = shadow.stopCount
            assertEquals(false, engine.canResumeWithoutRestart)

            assertTrue(engine.speak("First sentence.", "first").isSuccess)
            engine.prefetch("Next sentence.")
            assertTrue(shadow.requests.isEmpty())
            assertEquals(listOf(RecordingTts.SpokenRequest("First sentence.", TextToSpeech.QUEUE_ADD, "first")), shadow.spokenRequests)
            callbacks.onStart("first")
            // Native synthesis callbacks must not create a second, local playback stream.
            callbacks.onBeginSynthesis("first", 24_000, AudioFormat.ENCODING_PCM_16BIT, 1)
            callbacks.onAudioAvailable("first", ByteArray(8))
            shadowOf(Looper.getMainLooper()).idle()
            runCurrent()
            assertEquals(listOf("first"), started)
            assertTrue(done.isEmpty())

            callbacks.onDone("first")
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(listOf("first"), done)
            engine.stopForPageTurn()
            assertTrue(engine.speak("Next sentence.", "next").isSuccess)
            callbacks.onDone("first") // Late duplicates cannot complete the next sentence.
            callbacks.onError("first", TextToSpeech.ERROR_SYNTHESIS)
            callbacks.onStart("next")
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(listOf("first", "next"), started)
            assertEquals(listOf("first"), done)
            assertEquals(initialStops, shadow.stopCount)
            assertEquals(listOf(TextToSpeech.QUEUE_ADD, TextToSpeech.QUEUE_ADD), shadow.spokenRequests.map { it.queueMode })
            assertTrue(shadow.requests.isEmpty())
            assertTrue(errors.isEmpty())
        } finally {
            engine.shutdown()
            player.release()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun multiTtsPauseStopsNativeAudioAndInvalidatesCallbacksUntilSentenceIsRestarted() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val player = SystemTtsAudioPlayer(backgroundScope) { throw AssertionError("Unexpected local playback") }
        val engine = TtsEngine(ApplicationProvider.getApplicationContext(), player)
        val started = mutableListOf<String>()
        val done = mutableListOf<String>()
        val errors = mutableListOf<Throwable>()
        engine.setListener(listener(started, done, errors))
        try {
            selectMultiTts(engine)
            initializeEngine(engine)
            val shadow = shadowOf(ShadowTextToSpeech.getLastTextToSpeechInstance()) as RecordingTts
            val callbacks = shadow.utteranceProgressListener
            engine.speak("First sentence.", "first")
            val initialStops = shadow.stopCount
            engine.pause()
            callbacks.onStop("first", true)
            callbacks.onStart("first")
            callbacks.onDone("first")
            callbacks.onError("first", TextToSpeech.ERROR_SYNTHESIS)
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(initialStops + 1, shadow.stopCount)
            assertEquals(false, engine.resume())
            assertTrue(started.isEmpty())
            assertTrue(done.isEmpty())
            assertTrue(errors.isEmpty())
            engine.speak("First sentence.", "restart")
            callbacks.onStart("restart")
            callbacks.onDone("restart")
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(listOf("restart"), started)
            assertEquals(listOf("restart"), done)
        } finally {
            engine.shutdown()
            player.release()
            Dispatchers.resetMain()
        }
    }

    private suspend fun selectMultiTts(engine: TtsEngine) {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        shadowOf(context.packageManager).addResolveInfoForIntent(
            Intent(TextToSpeech.Engine.INTENT_ACTION_TTS_SERVICE),
            ResolveInfo().apply {
                serviceInfo = ServiceInfo().apply {
                    packageName = "org.nobody.multitts"
                    name = "org.nobody.multitts.service.TTSService"
                }
            }
        )
        engine.selectEngine("org.nobody.multitts")
    }

    @Test fun missingEngineRangesUseRenderedClausesAndLatePreciseRangesTakeOver() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        ShadowTextToSpeech.addLanguageAvailability(Locale.CHINESE)
        val track = Track()
        val player = SystemTtsAudioPlayer(backgroundScope) { track }
        val engine = TtsEngine(ApplicationProvider.getApplicationContext(), player)
        val ranges = mutableListOf<Pair<Int, Int>>()
        engine.setListener(object : TtsPlaybackListener {
            override fun onStart(utteranceId: String) = Unit
            override fun onDone(utteranceId: String) = Unit
            override fun onError(utteranceId: String, throwable: Throwable) { throw AssertionError(throwable) }
            override fun onPlaybackInterrupted() = Unit
            override fun onRangeStart(utteranceId: String, start: Int, end: Int) { ranges += start to end }
        })
        try {
            initializeEngine(engine)
            val shadow = shadowOf(ShadowTextToSpeech.getLastTextToSpeechInstance()) as RecordingTts
            engine.speak("第一小句，第二小句，句子结束。", "first")
            val id = shadow.requests.single().id
            val callbacks = shadow.utteranceProgressListener
            callbacks.onBeginSynthesis(id, 24_000, AudioFormat.ENCODING_PCM_16BIT, 1)
            callbacks.onAudioAvailable(id, ByteArray(20))
            shadowOf(Looper.getMainLooper()).idle()
            runCurrent()
            callbacks.onAudioAvailable(id, ByteArray(40)) // Count bytes streamed after playback began.
            callbacks.onDone(id)
            shadowOf(Looper.getMainLooper()).idle()
            runCurrent()
            assertTrue(ranges.isEmpty())
            track.playbackHeadPosition = 1
            advanceTimeBy(10); runCurrent()
            assertEquals(listOf(0 to 5), ranges)
            engine.pause()
            advanceTimeBy(100); runCurrent()
            assertEquals(listOf(0 to 5), ranges)
            engine.resume()
            track.playbackHeadPosition = 11
            advanceTimeBy(10); runCurrent()
            assertEquals(5 to 10, ranges.last())
            callbacks.onRangeStart(id, 10, 11, 15)
            shadowOf(Looper.getMainLooper()).idle()
            track.playbackHeadPosition = 15
            advanceTimeBy(10); runCurrent()
            assertEquals(10 to 11, ranges.last())
            val count = ranges.size
            track.playbackHeadPosition = 21
            advanceTimeBy(10); runCurrent()
            assertEquals(count, ranges.size) // No estimate can override the precise range.
            engine.stop()
            track.playbackHeadPosition = 29
            advanceTimeBy(10); runCurrent()
            assertEquals(count, ranges.size)
        } finally { engine.shutdown(); player.release(); Dispatchers.resetMain() }
    }

    private class Track : SystemTtsAudioPlayer.PcmTrack {
        override val bufferSizeInFrames = 4
        override var playbackHeadPosition = 0L
        val bytes = mutableListOf<Byte>()
        override fun play() = Unit
        override fun pause() = Unit
        override fun flush() { bytes.clear(); playbackHeadPosition = 0L }
        override fun release() = Unit
        override fun write(bytes: ByteArray, offset: Int, size: Int): Int {
            this.bytes.addAll(bytes.slice(offset until offset + size))
            return size
        }
    }

    @Test
    fun nextSentenceIsSynthesizedDuringPlaybackAndPromotedWithoutAnotherRequest() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        ShadowTextToSpeech.addLanguageAvailability(Locale.CHINESE)
        val track = Track()
        val player = SystemTtsAudioPlayer(backgroundScope) { track }
        val engine = TtsEngine(ApplicationProvider.getApplicationContext(), player)
        val started = mutableListOf<String>()
        val done = mutableListOf<String>()
        val errors = mutableListOf<Throwable>()
        engine.setListener(listener(started, done, errors))
        try {
            initializeEngine(engine)
            val tts = ShadowTextToSpeech.getLastTextToSpeechInstance()
            val shadow = shadowOf(tts) as RecordingTts
            val callbacks = shadow.utteranceProgressListener

            engine.speak("First sentence.", "first")
            engine.prefetch("Next sentence.")
            assertEquals(1, shadow.requests.size)
            val firstId = shadow.requests.first().id
            callbacks.onBeginSynthesis(firstId, 24_000, AudioFormat.ENCODING_PCM_16BIT, 1)
            callbacks.onAudioAvailable(firstId, ByteArray(8) { 1 })
            callbacks.onDone(firstId)
            shadowOf(Looper.getMainLooper()).idle()
            runCurrent()
            assertEquals(listOf("first"), started)
            assertTrue(done.isEmpty())
            assertEquals(2, shadow.requests.size)

            val nextId = shadow.requests.last().id
            callbacks.onBeginSynthesis(nextId, 24_000, AudioFormat.ENCODING_PCM_16BIT, 1)
            callbacks.onAudioAvailable(nextId, ByteArray(8) { 2 })
            callbacks.onDone(nextId)
            shadowOf(Looper.getMainLooper()).idle()
            runCurrent()
            assertEquals(List<Byte>(8) { 1 }, track.bytes)

            track.playbackHeadPosition = 4
            advanceTimeBy(10)
            runCurrent()
            assertEquals(listOf("first"), done)
            engine.stopForPageTurn()
            engine.speak("Next sentence.", "next")
            runCurrent()
            assertEquals(2, shadow.requests.size)
            assertEquals(listOf("first", "next"), started)
            assertEquals(List<Byte>(8) { 2 }, track.bytes)

            // Old synthesis callbacks must not affect the promoted sentence.
            callbacks.onError(firstId, TextToSpeech.ERROR_SYNTHESIS)
            callbacks.onDone(firstId)
            shadowOf(Looper.getMainLooper()).idle()
            runCurrent()
            assertTrue(errors.isEmpty())
            track.playbackHeadPosition = 4
            advanceTimeBy(10)
            runCurrent()
            assertEquals(listOf("first", "next"), done)
        } finally {
            engine.shutdown()
            player.release()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun unfinishedPrefetchIsCancelledAndResynthesizedBeforePlayback() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        ShadowTextToSpeech.addLanguageAvailability(Locale.CHINESE)
        val track = Track()
        val player = SystemTtsAudioPlayer(backgroundScope) { track }
        val engine = TtsEngine(ApplicationProvider.getApplicationContext(), player)
        val started = mutableListOf<String>()
        val done = mutableListOf<String>()
        val errors = mutableListOf<Throwable>()
        engine.setListener(listener(started, done, errors))
        try {
            initializeEngine(engine)
            val shadow = shadowOf(ShadowTextToSpeech.getLastTextToSpeechInstance()) as RecordingTts
            val callbacks = shadow.utteranceProgressListener

            engine.speak("First sentence.", "first")
            engine.prefetch("Next sentence.")
            val firstId = shadow.requests.single().id
            callbacks.onBeginSynthesis(firstId, 24_000, AudioFormat.ENCODING_PCM_16BIT, 1)
            callbacks.onAudioAvailable(firstId, ByteArray(8) { 1 })
            callbacks.onDone(firstId)
            shadowOf(Looper.getMainLooper()).idle()
            runCurrent()

            val incompletePrefetchId = shadow.requests.last().id
            callbacks.onBeginSynthesis(
                incompletePrefetchId,
                24_000,
                AudioFormat.ENCODING_PCM_16BIT,
                1
            )
            callbacks.onAudioAvailable(incompletePrefetchId, ByteArray(8) { 2 })
            shadowOf(Looper.getMainLooper()).idle()
            runCurrent()

            track.playbackHeadPosition = 4
            advanceTimeBy(10)
            runCurrent()
            assertEquals(listOf("first"), done)

            engine.speak("Next sentence.", "next")
            runCurrent()

            assertEquals(3, shadow.requests.size)
            assertEquals(listOf("first"), started)
            assertTrue(errors.isEmpty())

            // Late callbacks from the discarded partial synthesis cannot start playback.
            callbacks.onDone(incompletePrefetchId)
            shadowOf(Looper.getMainLooper()).idle()
            runCurrent()
            assertEquals(listOf("first"), started)

            val replacementId = shadow.requests.last().id
            callbacks.onBeginSynthesis(
                replacementId,
                24_000,
                AudioFormat.ENCODING_PCM_16BIT,
                1
            )
            callbacks.onAudioAvailable(replacementId, ByteArray(8) { 3 })
            callbacks.onDone(replacementId)
            shadowOf(Looper.getMainLooper()).idle()
            runCurrent()
            assertEquals(listOf("first", "next"), started)
        } finally {
            engine.shutdown()
            player.release()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun audioTrackInitializationFailureIsReportedAsRecoverablePlaybackError() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        ShadowTextToSpeech.addLanguageAvailability(Locale.CHINESE)
        val player = SystemTtsAudioPlayer(backgroundScope) { null }
        val engine = TtsEngine(ApplicationProvider.getApplicationContext(), player)
        val errors = mutableListOf<Throwable>()
        engine.setListener(listener(mutableListOf(), mutableListOf(), errors))
        try {
            initializeEngine(engine)
            val shadow = shadowOf(ShadowTextToSpeech.getLastTextToSpeechInstance()) as RecordingTts
            engine.speak("First sentence.", "first")
            val id = shadow.requests.single().id
            shadow.utteranceProgressListener.onBeginSynthesis(id, 24_000, AudioFormat.ENCODING_PCM_16BIT, 1)
            shadow.utteranceProgressListener.onAudioAvailable(id, ByteArray(8))
            shadowOf(Looper.getMainLooper()).idle()
            runCurrent()
            assertEquals(1, errors.size)
            assertTrue(errors.single() is SystemTtsException.Playback)
        } finally {
            engine.shutdown()
            player.release()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun missingSynthesisCallbackTimesOutInsteadOfRemainingPlayingForever() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        ShadowTextToSpeech.addLanguageAvailability(Locale.CHINESE)
        val player = SystemTtsAudioPlayer(backgroundScope) { Track() }
        val engine = TtsEngine(ApplicationProvider.getApplicationContext(), player)
        val errors = mutableListOf<Throwable>()
        engine.setListener(listener(mutableListOf(), mutableListOf(), errors))
        try {
            initializeEngine(engine)
            engine.speak("First sentence.", "first")
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(11))
            assertEquals(1, errors.size)
            assertTrue(errors.single() is SystemTtsException.Playback)
        } finally {
            engine.shutdown()
            player.release()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun prefetchErrorLeavesCurrentSentencePlayingAndReinitializationCreatesANewEngine() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        ShadowTextToSpeech.addLanguageAvailability(Locale.CHINESE)
        ShadowTextToSpeech.addLanguageAvailability(Locale.getDefault())
        val track = Track()
        val player = SystemTtsAudioPlayer(backgroundScope) { track }
        val engine = TtsEngine(ApplicationProvider.getApplicationContext(), player)
        val done = mutableListOf<String>()
        val errors = mutableListOf<Throwable>()
        engine.setListener(listener(mutableListOf(), done, errors))
        try {
            initializeEngine(engine)
            val oldTts = ShadowTextToSpeech.getLastTextToSpeechInstance()
            val shadow = shadowOf(oldTts) as RecordingTts
            engine.speak("First sentence.", "first")
            engine.prefetch("Next sentence.")
            val id = shadow.requests.first().id
            shadow.utteranceProgressListener.onBeginSynthesis(id, 24_000, AudioFormat.ENCODING_PCM_16BIT, 1)
            shadow.utteranceProgressListener.onAudioAvailable(id, ByteArray(8))
            shadow.utteranceProgressListener.onDone(id)
            shadowOf(Looper.getMainLooper()).idle()
            runCurrent()
            val prefetchedId = shadow.requests.last().id
            shadow.utteranceProgressListener.onError(prefetchedId, TextToSpeech.ERROR_SYNTHESIS)
            shadowOf(Looper.getMainLooper()).idle()
            runCurrent()
            assertTrue(errors.isEmpty())
            track.playbackHeadPosition = 4
            advanceTimeBy(10)
            runCurrent()
            assertEquals(listOf("first"), done)

            initializeEngine(engine, restart = true)
            assertTrue(oldTts !== ShadowTextToSpeech.getLastTextToSpeechInstance())
            assertTrue(shadow.isShutdown)
            shadow.utteranceProgressListener.onError(id, TextToSpeech.ERROR_SYNTHESIS)
            shadowOf(Looper.getMainLooper()).idle()
            assertTrue(errors.isEmpty())
        } finally {
            engine.shutdown()
            player.release()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun fileOnlyEnginePreparesNextAudioBeforePlaybackAndStopInvalidatesItsCallbacks() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        ShadowTextToSpeech.addLanguageAvailability(Locale.CHINESE)
        val track = Track()
        val player = SystemTtsAudioPlayer(backgroundScope) { track }
        val engine = TtsEngine(ApplicationProvider.getApplicationContext(), player)
        val started = mutableListOf<String>()
        val errors = mutableListOf<Throwable>()
        engine.setListener(listener(started, mutableListOf(), errors))
        try {
            initializeEngine(engine)
            val shadow = shadowOf(ShadowTextToSpeech.getLastTextToSpeechInstance()) as RecordingTts
            engine.speak("First sentence.", "first")
            engine.prefetch("Next sentence.")
            val first = shadow.requests.first()
            first.file.writeBytes(ByteArray(8) { 1 })
            shadow.utteranceProgressListener.onBeginSynthesis(first.id, 24_000, AudioFormat.ENCODING_PCM_16BIT, 1)
            shadow.utteranceProgressListener.onDone(first.id)
            shadowOf(Looper.getMainLooper()).idle()
            runCurrent()
            val next = shadow.requests.last()
            next.file.writeBytes(ByteArray(8) { 2 })
            shadow.utteranceProgressListener.onBeginSynthesis(next.id, 24_000, AudioFormat.ENCODING_PCM_16BIT, 1)
            shadow.utteranceProgressListener.onDone(next.id)
            shadowOf(Looper.getMainLooper()).idle()
            runCurrent()
            track.playbackHeadPosition = 4
            advanceTimeBy(10)
            runCurrent()
            engine.speak("Next sentence.", "next")
            runCurrent()
            assertEquals(2, shadow.requests.size)
            assertEquals(List<Byte>(8) { 2 }, track.bytes)
            engine.stop()
            engine.speak("Replacement sentence.", "replacement")
            shadow.utteranceProgressListener.onError(next.id, TextToSpeech.ERROR_SYNTHESIS)
            shadow.utteranceProgressListener.onAudioAvailable(next.id, ByteArray(8) { 3 })
            shadowOf(Looper.getMainLooper()).idle()
            runCurrent()
            assertTrue(errors.isEmpty())
            assertEquals(listOf("first", "next"), started)
            assertEquals(false, next.file.exists())
        } finally {
            engine.shutdown()
            player.release()
            Dispatchers.resetMain()
        }
    }

    private suspend fun TestScope.initializeEngine(engine: TtsEngine, restart: Boolean = false) {
        val previousInstance = ShadowTextToSpeech.getLastTextToSpeechInstance()
        val initialization = async { if (restart) engine.reinitialize() else engine.initialize(Locale.CHINESE) }
        // Package discovery uses Dispatchers.Default; wait for construction without advancing
        // the coroutine scheduler past the real engine's initialization timeout.
        for (attempt in 0 until 1_000) {
            runCurrent()
            val tts = ShadowTextToSpeech.getLastTextToSpeechInstance()
            if (tts != null && tts !== previousInstance) {
                shadowOf(tts).onInitListener.onInit(TextToSpeech.SUCCESS)
                shadowOf(Looper.getMainLooper()).idle()
                runCurrent()
                val result = initialization.await()
                assertTrue(result.exceptionOrNull().toString(), result.isSuccess)
                return
            }
            Thread.sleep(1)
        }
        throw AssertionError("TTS instance was not constructed")
    }

    private fun listener(started: MutableList<String>, done: MutableList<String>, errors: MutableList<Throwable>) =
        object : TtsPlaybackListener {
            override fun onStart(utteranceId: String) { started += utteranceId }
            override fun onDone(utteranceId: String) { done += utteranceId }
            override fun onError(utteranceId: String, throwable: Throwable) { errors += throwable }
            override fun onPlaybackInterrupted() = Unit
        }
}
