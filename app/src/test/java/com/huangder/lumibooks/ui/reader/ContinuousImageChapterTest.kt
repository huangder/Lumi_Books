package com.huangder.lumibooks.ui.reader

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContinuousImageChapterTest {
    @Test
    fun `single object replacement character is a full image chapter`() {
        assertTrue(isContinuousSingleImageChapter("\uFFFC", imageSpanCount = 1))
        assertTrue(isContinuousSingleImageChapter(" \n\uFFFC\n ", imageSpanCount = 1))
    }

    @Test
    fun `text or multiple images stay in normal chapter rendering`() {
        assertFalse(isContinuousSingleImageChapter("caption\uFFFC", imageSpanCount = 1))
        assertFalse(isContinuousSingleImageChapter("\uFFFC", imageSpanCount = 2))
        assertFalse(isContinuousSingleImageChapter(" ", imageSpanCount = 0))
    }
}
