package com.huangder.lumibooks.ui.reader

import org.junit.Assert.*
import org.junit.Test

class RasterLoadingPolicyTest {
    @Test fun `resume window keeps current and nearest pages first`() {
        assertEquals(listOf(7, 8, 6, 9, 5), rasterResumeWindow(7, 30, false))
        assertEquals(listOf(0, 1, 2), rasterResumeWindow(0, 30, false))
        assertEquals(listOf(29, 28, 27), rasterResumeWindow(29, 30, false))
        assertTrue(rasterResumeWindow(0, 0, true).isEmpty())
    }

    @Test fun `spreads always include the companion and never duplicate a page`() {
        assertEquals(setOf(3, 4, 5, 6, 7, 8), rasterResumeWindow(5, 30, true).toSet())
        assertEquals(listOf(0, 1, 2), rasterResumeWindow(0, 30, true))
    }

    @Test fun `reverse and jump replace the bounded prefetch window`() {
        val viewport = RasterViewport(setOf(10, 11), 10)
        assertEquals(listOf(12, 13, 9), rasterPrefetchWindow(viewport, 1, 100))
        assertEquals(listOf(9, 8, 12), rasterPrefetchWindow(viewport, -1, 100))
        assertEquals(listOf(91, 92, 89), rasterPrefetchWindow(viewport.copy(visiblePages = setOf(90), anchor = 90), 1, 100))
    }

    @Test fun `preview sampling is cheap even for long or enormous pages`() {
        assertEquals(16, rasterPreviewSample(5000, 7500, 240))
        val sample = rasterPreviewSample(1200, 100000, 240)
        assertTrue((1200L / sample) * (100000L / sample) <= 512000)
    }
}
