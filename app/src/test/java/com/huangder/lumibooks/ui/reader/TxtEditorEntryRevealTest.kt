package com.huangder.lumibooks.ui.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TxtEditorEntryRevealTest {
    @Test
    fun skipsLeadingWhitespaceAndIncludesClosingQuote() {
        val text = "\n  Alpha\u3002\u201dBeta\u3002"

        val range = findTxtEntrySentenceRange(text, 0)

        assertEquals("Alpha\u3002\u201d", range?.let(text::substring))
    }

    @Test
    fun startsAtReadingPageOffset() {
        val text = "Previous sentence. First visible sentence! Next."
        val offset = text.indexOf("First")

        val range = findTxtEntrySentenceRange(text, offset)

        assertEquals("First visible sentence!", range?.let(text::substring))
    }

    @Test
    fun doesNotTreatDecimalPointAsSentenceEnd() {
        val text = "Value 3.14 is valid. Next."

        val range = findTxtEntrySentenceRange(text, 0)

        assertEquals("Value 3.14 is valid.", range?.let(text::substring))
    }

    @Test
    fun usesLineEndWhenSentenceHasNoPunctuation() {
        val text = "Heading without punctuation\nBody."

        val range = findTxtEntrySentenceRange(text, 0)

        assertEquals("Heading without punctuation", range?.let(text::substring))
    }

    @Test
    fun returnsNullWhenOffsetHasNoRemainingText() {
        assertNull(findTxtEntrySentenceRange("Text   ", 4))
    }

    @Test
    fun mapsReaderTitleAndParagraphSpacingBackToTxtSource() {
        val source = "第1章 标题\n　　第一句话。\n第二句话。"
        val reader = "第1章 标题\n\n第一句话。\n\n第二句话。"

        val sourceOffset = mapReaderTxtOffsetToSource(
            sourceText = source,
            readerText = reader,
            readerOffset = reader.indexOf("第二句话")
        )

        assertEquals(source.indexOf("第二句话"), sourceOffset)
        assertEquals("第二句话。", findTxtEntrySentenceRange(source, sourceOffset)?.let(source::substring))
    }

    @Test
    fun mapsFirstVisibleGlyphForEitherWritingDirection() {
        val source = "标题\n　　横排或竖排的当前页第一句。下一句。"
        val reader = "标题\n\n横排或竖排的当前页第一句。下一句。"
        val readerPageAnchor = reader.indexOf("横排或竖排")

        val sourceOffset = mapReaderTxtOffsetToSource(source, reader, readerPageAnchor)

        assertEquals(source.indexOf("横排或竖排"), sourceOffset)
        assertEquals(
            "横排或竖排的当前页第一句。",
            findTxtEntrySentenceRange(source, sourceOffset)?.let(source::substring)
        )
    }
}
