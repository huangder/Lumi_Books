package com.huangder.lumibooks.tts

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.Locale
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SystemTtsEngineInstrumentedTest {
    @Test
    fun traditionalChineseFallsBackToAnInstalledChineseVoiceAndSpeaks() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val engine = TtsEngine(context)
        val expectedUtteranceId = "oem-tts-smoke-test"
        val completed = CompletableDeferred<Unit>()
        engine.setListener(object : TtsPlaybackListener {
            override fun onStart(utteranceId: String) = Unit

            override fun onDone(utteranceId: String) {
                if (utteranceId == expectedUtteranceId) {
                    completed.complete(Unit)
                }
            }

            override fun onError(utteranceId: String, throwable: Throwable) {
                if (utteranceId == expectedUtteranceId) {
                    completed.completeExceptionally(throwable)
                }
            }

            override fun onPlaybackInterrupted() {
                completed.completeExceptionally(IllegalStateException("TTS playback was interrupted"))
            }
        })

        try {
            val initialization = engine.initialize(Locale.TRADITIONAL_CHINESE)
            assertTrue(
                "TTS initialization failed: ${initialization.exceptionOrNull()}",
                initialization.isSuccess
            )
            val playback = engine.speak("這是一段文字轉語音測試。", expectedUtteranceId)
            assertTrue(
                "TTS playback was rejected: ${playback.exceptionOrNull()}",
                playback.isSuccess
            )
            withTimeout(20_000L) { completed.await() }
        } finally {
            withContext(Dispatchers.Main.immediate) { engine.shutdown() }
        }
    }
}
