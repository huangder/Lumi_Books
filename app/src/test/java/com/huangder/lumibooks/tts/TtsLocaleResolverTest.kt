package com.huangder.lumibooks.tts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Locale

class TtsLocaleResolverTest {
    @Test
    fun `traditional Chinese falls back to engine simplified Chinese locale`() {
        val candidates = TtsLocaleResolver.candidates(
            requested = Locale.TRADITIONAL_CHINESE,
            engineDefaults = listOf(Locale.SIMPLIFIED_CHINESE),
            available = setOf(Locale.SIMPLIFIED_CHINESE)
        )

        assertEquals(Locale.TRADITIONAL_CHINESE, candidates[0])
        assertEquals(Locale.SIMPLIFIED_CHINESE, candidates[1])
    }

    @Test
    fun `duplicate locales are removed by language tag`() {
        val candidates = TtsLocaleResolver.candidates(
            requested = Locale.SIMPLIFIED_CHINESE,
            engineDefaults = listOf(Locale.SIMPLIFIED_CHINESE),
            available = setOf(Locale.SIMPLIFIED_CHINESE, Locale.CHINESE)
        )

        assertEquals(candidates.map(Locale::toLanguageTag).distinct(), candidates.map(Locale::toLanguageTag))
    }

    @Test
    fun `unrelated engine default is not used as a language fallback`() {
        val candidates = TtsLocaleResolver.candidates(
            requested = Locale.TRADITIONAL_CHINESE,
            engineDefaults = listOf(Locale.US),
            available = setOf(Locale.US)
        )

        assertEquals(
            listOf("zh-TW", "zh-CN", "zh"),
            candidates.map(Locale::toLanguageTag)
        )
    }

    @Test
    fun `Chinese text uses Chinese locale even when app locale is English`() {
        assertEquals(
            Locale.SIMPLIFIED_CHINESE,
            TtsLocaleResolver.localeForText("这是用于测试的中文句子。", Locale.US)
        )
    }

    @Test
    fun `Japanese text with kana uses Japanese locale`() {
        assertEquals(
            Locale.JAPANESE,
            TtsLocaleResolver.localeForText("これは日本語の文章です。", Locale.SIMPLIFIED_CHINESE)
        )
    }

    @Test
    fun `punctuation only text does not force a locale switch`() {
        assertNull(TtsLocaleResolver.localeForText("……！？", Locale.US))
    }
}
