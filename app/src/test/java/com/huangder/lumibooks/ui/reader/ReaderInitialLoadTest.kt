package com.huangder.lumibooks.ui.reader

import org.junit.Assert.assertFalse
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
}
