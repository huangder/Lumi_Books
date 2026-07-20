package com.huangder.lumibooks.tts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TtsTextExtractorTest {
    private val extractor = TtsTextExtractor()

    @Test
    fun splitIntoSentences_keepsChinesePunctuationAndClosingQuote() {
        val result = extractor.splitIntoSentences("他说：“你好！”然后离开。下一句？")

        assertEquals(listOf("他说：“你好！”", "然后离开。", "下一句？"), result)
    }

    @Test
    fun splitIntoSentences_removesImagePlaceholderAndBlankText() {
        assertTrue(extractor.splitIntoSentences(" \uFFFC\n ").isEmpty())
    }

    @Test
    fun splitIntoSentences_hardSplitsLongUnpunctuatedText() {
        val result = extractor.splitIntoSentences("字".repeat(450))

        assertEquals(listOf(200, 200, 50), result.map(String::length))
    }
}
