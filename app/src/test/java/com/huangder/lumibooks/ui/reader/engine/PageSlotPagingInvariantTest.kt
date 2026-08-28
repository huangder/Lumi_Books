package com.huangder.lumibooks.ui.reader.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class PageSlotPagingInvariantTest {
    @Test
    fun `loaded slot retains chapter total after layout cache eviction`() {
        assertEquals(
            3,
            stableChapterPageCount(
                cachedPageCount = null,
                slotPageCount = 3,
                visiblePageIndex = 1,
                isLoaded = true
            )
        )
    }

    @Test
    fun `reported total can never precede the visible page`() {
        assertEquals(
            2,
            stableChapterPageCount(
                cachedPageCount = 1,
                slotPageCount = 0,
                visiblePageIndex = 1,
                isLoaded = true
            )
        )
    }

    @Test
    fun `only the first loaded page of the first chapter is book start`() {
        assertEquals(true, isAbsoluteBookStart(0, 0, isLoaded = true))
        assertEquals(false, isAbsoluteBookStart(0, 1, isLoaded = true))
        assertEquals(false, isAbsoluteBookStart(1, 0, isLoaded = true))
        assertEquals(false, isAbsoluteBookStart(0, 0, isLoaded = false))
    }

    @Test
    fun `last page of last chapter is book end`() {
        assertEquals(true, isAbsoluteBookEnd(4, 2, -1, 3, 5, isLoaded = true))
        assertEquals(false, isAbsoluteBookEnd(4, 1, -1, 3, 5, isLoaded = true))
        assertEquals(false, isAbsoluteBookEnd(3, 2, -1, 3, 5, isLoaded = true))
    }

    @Test
    fun `spread whose right page is final page is book end`() {
        assertEquals(true, isAbsoluteBookEnd(4, 1, 2, 3, 5, isLoaded = true))
    }
}
