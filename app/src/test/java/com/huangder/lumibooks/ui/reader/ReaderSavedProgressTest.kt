package com.huangder.lumibooks.ui.reader

import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderSavedProgressTest {
    @Test
    fun `paged progress includes the current page`() {
        assertEquals(0.125f, paged(chapter = 0, chapters = 2, page = 0, pages = 4), 0.0001f)
        assertEquals(0.375f, paged(chapter = 0, chapters = 2, page = 2, pages = 4), 0.0001f)
    }

    @Test
    fun `last page of last chapter is complete`() {
        assertEquals(1f, paged(chapter = 9, chapters = 10, page = 3, pages = 4), 0.0001f)
        assertEquals(1f, paged(chapter = 0, chapters = 1, page = 0, pages = 1), 0.0001f)
    }

    @Test
    fun `continuous progress keeps its scaled chapter fraction`() {
        assertEquals(
            0.875f,
            calculateSavedReadingProgress(
                currentChapterIndex = 3,
                chapterCount = 4,
                currentPageIndex = 5_000,
                totalPages = 10_000,
                isContinuousScroll = true
            ),
            0.0001f
        )
    }

    @Test
    fun `invalid counts are safe`() {
        assertEquals(0f, paged(chapter = 0, chapters = 0, page = 0, pages = 1), 0f)
        assertEquals(0f, paged(chapter = 0, chapters = 1, page = 0, pages = 0), 0f)
    }

    private fun paged(chapter: Int, chapters: Int, page: Int, pages: Int): Float =
        calculateSavedReadingProgress(
            currentChapterIndex = chapter,
            chapterCount = chapters,
            currentPageIndex = page,
            totalPages = pages,
            isContinuousScroll = false
        )
}
