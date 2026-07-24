package com.huangder.lumibooks.ui.reader

import com.huangder.lumibooks.domain.model.ReaderCornerContent
import com.huangder.lumibooks.domain.model.ReaderPageCorner
import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderFooterProgressTest {
    @Test
    fun calculatesWholeBookProgressFromChapterAndPage() {
        assertEquals(0f, calculateBookProgressPercent(0, 0, 0, 1), 0.001f)
        assertEquals(2f, calculateBookProgressPercent(0, 10, 0, 5), 0.001f)
        assertEquals(45f, calculateBookProgressPercent(4, 10, 1, 4), 0.001f)
        assertEquals(100f, calculateBookProgressPercent(9, 10, 3, 4), 0.001f)
    }

    @Test
    fun formatsReadingProgressWithTwoDecimalPlaces() {
        assertEquals("0.00%", formatReadingProgressPercent(0f))
        assertEquals("12.35%", formatReadingProgressPercent(12.345f))
        assertEquals("100.00%", formatReadingProgressPercent(100f))
    }

    @Test
    fun movingCornerContentClearsItsPreviousCorner() {
        val updated = ReaderUiState().withReaderCornerContent(
            ReaderPageCorner.BOTTOM_RIGHT,
            ReaderCornerContent.CHAPTER_INFO
        )

        assertEquals(ReaderCornerContent.NONE, updated.readerTopLeftContent)
        assertEquals(ReaderCornerContent.CHAPTER_INFO, updated.readerBottomRightContent)
    }
}
