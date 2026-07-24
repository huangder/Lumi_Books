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

    @Test
    fun restoresLetterSpacingThatSingleCharacterDrawingDoesNotRender() {
        assertEquals(2f, readerExplicitLetterSpacing(letterSpacingEm = 0.05f, textSizePx = 40f), 0.001f)

        val characterWidths = 360f
        val gapCount = 9
        val availableWidth = 450f
        val configuredSpacing = readerExplicitLetterSpacing(0.05f, 40f)
        val staticLayoutWidth = characterWidths + configuredSpacing * gapCount
        val justificationExtra = (availableWidth - staticLayoutWidth) / gapCount
        val customDrawWidth = characterWidths +
            (configuredSpacing + justificationExtra) * gapCount

        assertEquals(availableWidth, customDrawWidth, 0.001f)
    }

    @Test
    fun extendsHighlightToTheActualJustifiedCharacterPosition() {
        assertEquals(
            145f,
            readerHighlightCharacterEnd(
                x = 100f,
                characterWidth = 20f,
                hasFollowingCharacter = true,
                letterSpacingPx = 5f,
                justificationSpacingPx = 20f
            ),
            0.001f
        )
        assertEquals(
            120f,
            readerHighlightCharacterEnd(
                x = 100f,
                characterWidth = 20f,
                hasFollowingCharacter = false,
                letterSpacingPx = 5f,
                justificationSpacingPx = 20f
            ),
            0.001f
        )
    }

    @Test
    fun splitsFullPageLineRemainderBetweenTopAndBottom() {
        assertEquals(
            5f,
            calculateReaderVerticalBalanceOffset(
                availableHeightPx = 100f,
                lineHeightPx = 30f,
                maxShiftPx = 20f
            ),
            0.001f
        )
        assertEquals(
            0f,
            calculateReaderVerticalBalanceOffset(90f, 30f, 20f),
            0.001f
        )
    }
}
