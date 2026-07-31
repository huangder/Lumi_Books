package com.huangder.lumibooks.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuoteExcerptFormatterTest {
    @Test
    fun `keeps the longest complete leading sentence that fits`() {
        val result = QuoteExcerptFormatter.formatToFit(
            "短句。后面是一句很长的话。"
        ) { candidate -> candidate.length <= 3 }

        assertEquals("短句。", result)
    }

    @Test
    fun `keeps an english sentence including its closing quote`() {
        val result = QuoteExcerptFormatter.formatToFit(
            "Read this. Another sentence follows."
        ) { candidate -> candidate.length <= 10 }

        assertEquals("Read this.", result)
    }

    @Test
    fun `truncates an oversized first sentence with three dots`() {
        val result = QuoteExcerptFormatter.formatToFit(
            "这是一个很长的句子。"
        ) { candidate -> candidate.codePointCount(0, candidate.length) <= 8 }

        assertTrue(result.endsWith("..."))
        assertEquals(8, result.codePointCount(0, result.length))
    }

    @Test
    fun `returns unpunctuated text unchanged when it fits`() {
        assertEquals(
            "没有句号也能完整显示",
            QuoteExcerptFormatter.formatToFit("没有句号也能完整显示") { true }
        )
    }

    @Test
    fun `normalizes surrounding and repeated whitespace`() {
        assertEquals(
            "第一句。 第二句。",
            QuoteExcerptFormatter.formatToFit("  第一句。\n\t 第二句。  ") { true }
        )
    }

    @Test
    fun `does not split a supplementary unicode character`() {
        val result = QuoteExcerptFormatter.formatToFit("😀😀😀😀😀😀") { candidate ->
            candidate.codePointCount(0, candidate.length) <= 5
        }

        assertEquals("😀😀...", result)
        assertFalse(result.contains('\uFFFD'))
    }

    @Test
    fun `empty content stays empty`() {
        assertEquals("", QuoteExcerptFormatter.formatToFit(" \n ") { true })
    }
}
