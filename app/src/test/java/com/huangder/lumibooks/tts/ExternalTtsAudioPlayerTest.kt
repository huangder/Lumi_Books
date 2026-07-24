package com.huangder.lumibooks.tts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ExternalTtsAudioPlayerTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun normalizePlaybackRate_clampsSupportedAudioTrackRange() {
        assertEquals(0.5f, normalizeExternalTtsPlaybackRate(0.2f), 0f)
        assertEquals(1f, normalizeExternalTtsPlaybackRate(1f), 0f)
        assertEquals(2f, normalizeExternalTtsPlaybackRate(3f), 0f)
    }

    @Test
    fun pitchInstruction_formatsClampedRate() {
        val high = ExternalTtsConfig.pitchInstruction(3f)
        val low = ExternalTtsConfig.pitchInstruction(0.1f)

        assertTrue(high.contains("2.00"))
        assertTrue(low.contains("0.50"))
    }

    @Test
    fun createKey_isStableForIdenticalSynthesisInputs() {
        val cache = ExternalTtsAudioCache(temporaryFolder.newFolder("cache"))
        val settings = ExternalTtsConfig.defaults(ExternalTtsProtocol.MIMO_CHAT)
        val text = "hello world"

        val first = cache.createKey(settings, text)
        val second = cache.createKey(settings, text)
        val withStyle = cache.createKey(
            settings.copy(styleInstructions = "whisper slowly"),
            text
        )

        assertEquals("same synthesis inputs produce the same key", first, second)
        assertNotEquals("style instructions affect synthesis identity", first, withStyle)
    }

    @Test
    fun createKey_changesWithSynthesisInputs() {
        val cache = ExternalTtsAudioCache(temporaryFolder.newFolder("cache"))
        val base = ExternalTtsConfig.defaults(ExternalTtsProtocol.MIMO_CHAT)
        val text = "test"

        val key1 = cache.createKey(base, text)
        val key2 = cache.createKey(base.copy(voice = "other_voice"), text)
        val key3 = cache.createKey(base.copy(model = "other_model"), text)
        val key4 = cache.createKey(
            base.copy(protocol = ExternalTtsProtocol.OPENAI_SPEECH),
            text
        )

        assertNotEquals("voice changes key", key1, key2)
        assertNotEquals("model changes key", key1, key3)
        assertNotEquals("protocol changes key", key1, key4)
    }
}
