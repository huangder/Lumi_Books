package com.huangder.lumibooks.ui.animation

import org.junit.Assert.*
import org.junit.Test

class CoverFlowReaderMotionTest {
    @Test fun `slow loading retains sharp cover before text is ready`() {
        assertEquals(1f, CoverFlowReaderMotion.coverAlpha(0.34f), 0f)
        assertEquals(0f, CoverFlowReaderMotion.readerAlpha(0.34f), 0f)
        assertEquals(1f, CoverFlowReaderMotion.sideExit(0.34f), 0f)
    }

    @Test fun `cover and text overlap while both enlarge`() {
        assertTrue(CoverFlowReaderMotion.coverAlpha(0.6f) > 0f)
        assertTrue(CoverFlowReaderMotion.readerAlpha(0.6f) > 0f)
        assertTrue(CoverFlowReaderMotion.coverScale(0.6f) > CoverFlowReaderMotion.coverScale(0.34f))
        assertTrue(CoverFlowReaderMotion.readerScale(0.6f) > CoverFlowReaderMotion.readerScale(0.34f))
        assertEquals(0f, CoverFlowReaderMotion.coverAlpha(1f), 0f)
        assertEquals(1f, CoverFlowReaderMotion.readerAlpha(1f), 0.0001f)
        assertEquals(1f, CoverFlowReaderMotion.readerScale(1f), 0.0001f)
    }

    @Test fun `window corners move from the cover to the display corners`() {
        val fit = BookReaderMotion.SCREEN_CORNER_FIT
        assertEquals(12f, CoverFlowReaderMotion.windowCornerRadius(12f, 40f, 0f), 0f)
        assertEquals(40f * fit, CoverFlowReaderMotion.windowCornerRadius(12f, 40f, 1f), 0.0001f)
        assertEquals(0f, CoverFlowReaderMotion.windowCornerRadius(12f, 0f, 1f), 0.0001f)
    }
}
