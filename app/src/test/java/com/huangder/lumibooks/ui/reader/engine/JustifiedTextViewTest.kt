package com.huangder.lumibooks.ui.reader.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JustifiedTextViewTest {
    @Test
    fun trimsTrailingWhitespaceBeforeJustifyingWrappedLine() {
        val text = "正文， \n"

        assertEquals(3, readerLineContentEnd(text, 0, text.length))
        assertEquals('，', text[readerLineContentEnd(text, 0, text.length) - 1])
    }

    @Test
    fun justifiesOnlyAutomaticallyWrappedLines() {
        assertTrue(
            shouldJustifyReaderLine(
                lineIndex = 0,
                lineCount = 2,
                endsWithParagraphBreak = false,
                pageEndsMidParagraph = false
            )
        )
        assertTrue(
            shouldJustifyReaderLine(
                lineIndex = 0,
                lineCount = 1,
                endsWithParagraphBreak = false,
                pageEndsMidParagraph = true
            )
        )
        assertFalse(
            shouldJustifyReaderLine(
                lineIndex = 0,
                lineCount = 1,
                endsWithParagraphBreak = false,
                pageEndsMidParagraph = false
            )
        )
        assertFalse(
            shouldJustifyReaderLine(
                lineIndex = 0,
                lineCount = 2,
                endsWithParagraphBreak = true,
                pageEndsMidParagraph = false
            )
        )
    }

    @Test
    fun pageIndentOnlyStartsAtRealParagraphBoundary() {
        val text = "第一段仍在继续\n第二段"

        assertFalse(pageStartsMidParagraph(text, 0))
        assertTrue(pageStartsMidParagraph(text, 3))
        assertFalse(pageStartsMidParagraph(text, text.indexOf("第二段")))
    }
}
