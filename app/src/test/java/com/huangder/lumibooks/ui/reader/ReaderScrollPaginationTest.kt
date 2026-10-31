package com.huangder.lumibooks.ui.reader

import com.huangder.lumibooks.domain.model.ReaderCornerContent
import org.junit.Assert.*
import org.junit.Test

class ReaderScrollPaginationTest {
    private val pages = ReaderBookPages(chapterCounts = listOf(4),
        chapterStarts = mapOf(0 to listOf(0, 30, 200, 250)))

    @Test fun scrollUsesHorizontalPageBoundariesInsteadOfEvenlyDividingTheChapter() {
        assertEquals(1, readerScrollPageIndex(pages, 0, 199, 0.9f))
        assertEquals(2, readerScrollPageIndex(pages, 0, 200, 0.1f))
        assertEquals(3, readerScrollPageIndex(pages, 0, 1000, 1f))
        assertNull(readerScrollPageIndex(ReaderBookPages(), 0, 100, 0.5f))
    }

    @Test fun percentageRemainsAnIndependentPersistableCornerChoice() {
        assertEquals(ReaderCornerContent.CHAPTER_PROGRESS, ReaderCornerContent.fromKey("chapter_progress"))
        assertEquals(ReaderCornerContent.PAGE_NUMBER, ReaderCornerContent.fromKey("page_number"))
        assertEquals("3-25.0%", readerScrollPageLabel(2, 0.25f))
    }
}
