package com.huangder.lumibooks.ui.reader.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderSelectionMagnifierGeometryTest {

    @Test
    fun windowCenterSitsAboveTheAnchoredLine() {
        val density = 3f
        val lineHeightPx = 90f
        val windowHeightPx = 144
        val anchorY = 600f

        val windowCenterY = readerMagnifierWindowCenterY(
            anchorY = anchorY,
            windowHeightPx = windowHeightPx,
            lineHeightPx = lineHeightPx,
            density = density
        )

        val windowBottom = windowCenterY + windowHeightPx / 2f
        val lineTop = anchorY - lineHeightPx / 2f
        assertTrue("magnifier must float above the text line", windowBottom < lineTop)
        assertEquals(
            anchorY - (windowHeightPx / 2f + lineHeightPx / 2f + 8f * density),
            windowCenterY,
            0.001f
        )
    }

    @Test
    fun negativeLineHeightIsClamped() {
        val center = readerMagnifierWindowCenterY(
            anchorY = 100f,
            windowHeightPx = 100,
            lineHeightPx = -20f,
            density = 2f
        )
        assertEquals(100f - (50f + 16f), center, 0.001f)
    }
}
