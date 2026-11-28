package com.huangder.lumibooks.ui.reader

import com.huangder.lumibooks.ui.reader.engine.PageLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderInitialLoadTest {
    @Test
    fun `txt releases transition after first valid page even with pending anchor`() {
        assertTrue(
            shouldReleasePagedReaderOnFirstPage(
                isTxtBook = true,
                isContinuous = false,
                chapterTotalPages = 12,
                hasPendingReaderPosition = true,
                reachedPendingPosition = false
            )
        )
    }

    @Test
    fun `empty current slot never releases initial loading`() {
        assertFalse(
            shouldReleasePagedReaderOnFirstPage(
                isTxtBook = true,
                isContinuous = false,
                chapterTotalPages = 0,
                hasPendingReaderPosition = false,
                reachedPendingPosition = false
            )
        )
    }

    @Test
    fun `non txt keeps waiting for an exact pending restore`() {
        assertFalse(
            shouldReleasePagedReaderOnFirstPage(
                isTxtBook = false,
                isContinuous = false,
                chapterTotalPages = 12,
                hasPendingReaderPosition = true,
                reachedPendingPosition = false
            )
        )
    }

    private val pages = listOf(
        PageLayout(0, 0, 0, 1, 2, 10),
        PageLayout(0, 1, 1, 2, 10, 20),
        PageLayout(0, 2, 3, 4, 24, 30)
    )

    private fun scrollPosition(offset: Int?) = ReaderPositionLocator(
        chapterIndex = 0,
        chapterFraction = 0.5f,
        flow = ReaderPositionFlow.CONTINUOUS,
        characterOffset = offset
    )

    @Test
    fun `reopening a scroll locator past the current chapter resolves to the last page`() {
        assertEquals(2, restoredPagedPageIndex(scrollPosition(40), pages))
    }

    @Test
    fun `first page clamps leading whitespace and positions in trimmed gaps have a valid page`() {
        assertEquals(0, restoredPagedPageIndex(scrollPosition(0), pages))
        assertEquals(1, restoredPagedPageIndex(scrollPosition(22), pages))
    }

    @Test
    fun `exact page start wins and missing character anchor uses the saved fraction`() {
        assertEquals(2, restoredPagedPageIndex(scrollPosition(24), pages))
        assertEquals(1, restoredPagedPageIndex(scrollPosition(null), pages))
        assertNull(restoredPagedPageIndex(scrollPosition(24), emptyList()))
    }
}
