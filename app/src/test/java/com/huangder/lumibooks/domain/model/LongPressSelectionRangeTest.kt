package com.huangder.lumibooks.domain.model

import org.junit.Assert.*
import org.junit.Test

class LongPressSelectionRangeTest {
    private fun selected(text: String, offset: Int, scope: LongPressSelectionRange, line: IntRange? = null) =
        text.selectionRangeAt(offset, scope, line)?.let { text.substring(it.first, it.last + 1) }

    @Test fun paragraphAndVisualLineHaveDifferentBoundaries() {
        val text = "上一段。\n  第一行，第二行。\n下一段。"
        assertEquals("第一行，第二行。", selected(text, text.indexOf('二'), LongPressSelectionRange.PARAGRAPH))
        val start = text.indexOf("第二行")
        assertEquals("第二行。", selected(text, start + 1, LongPressSelectionRange.LINE, start until start + 4))
        assertEquals("上一段。", selected(text, text.indexOf('\n'), LongPressSelectionRange.PARAGRAPH))
    }

    @Test fun sentenceIncludesPunctuationAndClosingQuoteWithoutPreviousSentence() {
        val text = "第一句。 “第二句！” 第三句？"
        assertEquals("“第二句！”", selected(text, text.indexOf('二'), LongPressSelectionRange.SENTENCE))
        assertEquals("第三句？", selected(text, text.indexOf('三'), LongPressSelectionRange.SENTENCE))
    }

    @Test fun englishSentenceDoesNotStopAtDecimalPoint() {
        val text = "Before. The value is 3.14 today. After!"
        assertEquals("The value is 3.14 today.", selected(text, text.indexOf("14"), LongPressSelectionRange.SENTENCE))
        assertEquals("value", selected(text, text.indexOf("value") + 2, LongPressSelectionRange.WORD))
    }

    @Test fun characterDoesNotSplitSurrogatePairOrCombiningMark() {
        val text = "A😀e\u0301Z"
        assertEquals("😀", selected(text, 2, LongPressSelectionRange.CHARACTER))
        assertEquals("e\u0301", selected(text, 4, LongPressSelectionRange.CHARACTER))
    }

    @Test fun emptyAndWhitespaceAndInvalidPreferencesAreSafe() {
        assertNull("".selectionRangeAt(0, LongPressSelectionRange.WORD))
        assertEquals(" ", selected(" ", 99, LongPressSelectionRange.SENTENCE))
        assertEquals(LongPressSelectionRange.WORD, LongPressSelectionRange.fromKey(null))
        assertEquals(LongPressSelectionRange.WORD, LongPressSelectionRange.fromKey("unknown"))
    }
}
