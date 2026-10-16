package com.huangder.lumibooks.ui.reader

import org.junit.Assert.*
import org.junit.Test

class ReaderBookPagesTest {
    @Test fun unknownPrefixNeverProducesAnInventedPageNumber() {
        val pages = ReaderBookPages(chapterCounts = listOf(3, null, 4, null))
        assertEquals(3, pages.pageNumber(0, 2))
        assertNull(pages.pageNumber(2, 0))
        assertNull(pages.totalPages)
        assertNull(readerBookPageLabel(pages, 0, 2))
        assertEquals(6, pages.withChapter(1, 2).pageNumber(2, 0))
    }

    @Test fun knownEmptyChaptersAndCrossChapterSpreadUseOneBasedBookNumbers() {
        val pages = ReaderBookPages(chapterCounts = listOf(3, 0, 2))
        assertEquals(5, pages.totalPages)
        assertEquals("3–4 / 5", readerBookPageLabel(pages, 0, 2, 2, 0))
        assertEquals("5 / 5", readerBookPageLabel(pages, 2, 1))
        assertNull(pages.pageNumber(1, 0))
        assertNull(pages.pageNumber(2, 2))
    }

    @Test fun characterAnchorMapsAgainAfterPaginationChanges() {
        val pages = ReaderBookPages(chapterCounts = listOf(null, null))
            .withChapter(0, 2, listOf(0, 100)).withChapter(1, 3, listOf(0, 50, 100))
        assertEquals(1, pages.pageAtCharacter(1, 99))
        assertEquals(4, pages.pageNumber(1, pages.pageAtCharacter(1, 99)!!))
        val reflow = ReaderBookPages(generation = 2, chapterCounts = listOf(null, null))
        assertNull(reflow.pageAtCharacter(1, 99))
        assertEquals(0, reflow.withChapter(1, 1, listOf(0)).pageAtCharacter(1, 99))
    }

    @Test fun targetBeforeAfterAndBothVisiblePagesAreHandled() {
        assertEquals(ReaderReturnSide.LEFT, readerReturnSide(0, 3f, 1, 0f))
        assertEquals(ReaderReturnSide.RIGHT, readerReturnSide(2, 0f, 1, 0f))
        assertNull(readerReturnSide(1, 0f, 1, 0f))
        assertNull(readerReturnSide(2, 0f, 1, 3f, 2, 0f))
        val visible = listOf(ReaderVisibleCharacters(1, 100, 200), ReaderVisibleCharacters(2, 0, 50))
        assertNull(readerReturnSideAtCharacter(2, 49, 1, 100, visible))
        assertEquals(ReaderReturnSide.RIGHT, readerReturnSideAtCharacter(2, 50, 1, 100, visible))
        assertEquals(ReaderReturnSide.LEFT, readerReturnSideAtCharacter(1, 99, 1, 100, visible))
        assertEquals(ReaderReturnSide.RIGHT, readerReturnSideAtCharacter(1, 450, 1, 100, emptyList()))
    }

    @Test fun physicalPagesAndSpreadsUseBookNumbers() {
        assertEquals("1 / 30", readerPhysicalPageLabel(0, 30))
        assertEquals("2–3 / 30", readerPhysicalPageLabel(1, 30, 2))
        assertEquals("30 / 30", readerPhysicalPageLabel(29, 30, 29))
    }
}
