package com.huangder.lumibooks.tts

import android.media.AudioFormat
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
    @Implements(TextToSpeech::class)
    class RecordingTts : ShadowTextToSpeech() {
        data class Request(val text: String, val id: String, val file: File)
        val requests = mutableListOf<Request>()
        data class SpokenRequest(val text: String, val queueMode: Int, val id: String)
        val spokenRequests = mutableListOf<SpokenRequest>()
        var stopCount = 0

        @Implementation
        override fun speak(text: CharSequence, queueMode: Int, params: Bundle?, utteranceId: String): Int {
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
