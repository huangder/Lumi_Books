package com.huangder.lumibooks.ui.reader

import org.junit.Assert.*
import org.junit.Test

class RasterOpeningReadinessTest {
    @Test fun `neighbor and late repeated completions cannot uncover the reader`() {
        val opening = RasterOpeningReadiness(setOf(7))
        assertFalse(opening.pagePrepared(6))
        assertFalse(opening.pagePrepared(8))
        assertTrue(opening.pagePrepared(7))
        assertFalse(opening.pagePrepared(7))
    }

    @Test fun `both pages in the opening spread must have an image or error surface`() {
        val opening = RasterOpeningReadiness(setOf(3, 4))
        assertFalse(opening.pagePrepared(4))
        assertFalse(opening.pagePrepared(4))
        assertTrue(opening.pagePrepared(3))
    }

    @Test fun `a new opening has independent readiness`() {
        val first = RasterOpeningReadiness(setOf(3))
        val second = RasterOpeningReadiness(setOf(9))
        assertTrue(first.pagePrepared(3))
        assertFalse(second.pagePrepared(3))
        assertTrue(second.pagePrepared(9))
    }
}
