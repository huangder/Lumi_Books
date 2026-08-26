package com.huangder.lumibooks.ui.reader.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VerticalCurlGeometryTest {
    @Test
    fun horizontalProgressKeepsFoldVerticalAndFinite() {
        val frame = VerticalCurlFrame()

        assertTrue(VerticalCurlGeometry.evaluate(1080f, 2340f, 540f, frame))

        assertEquals(0.25f, frame.progress, 0.0001f)
        assertEquals(810f, frame.creaseX, 0.01f)
        assertEquals(540f, frame.foldedEdgeX, 0.01f)
        assertEquals(270f, frame.foldWidth, 0.01f)
        assertTrue(frame.foldedEdgeX < frame.creaseX)
        assertTrue(frame.curveInset >= 0f)
        assertTrue(
            floatArrayOf(
                frame.progress,
                frame.creaseX,
                frame.foldedEdgeX,
                frame.curveInset,
                frame.foldWidth
            ).all { it.isFinite() }
        )
    }

    @Test
    fun flatAndTerminalFramesAreDeterministic() {
        val frame = VerticalCurlFrame()
        assertTrue(VerticalCurlGeometry.evaluate(1080f, 2340f, 1080f, frame))
        assertEquals(0f, frame.progress, 0f)
        assertEquals(0f, frame.foldWidth, 0f)
        assertFalse(frame.terminal)

        assertTrue(VerticalCurlGeometry.evaluate(1080f, 2340f, -1080f, frame))
        assertEquals(1f, frame.progress, 0f)
        assertEquals(0f, frame.creaseX, 0f)
        assertTrue(frame.terminal)
    }

    @Test
    fun invalidViewportUsesRendererFallback() {
        val frame = VerticalCurlFrame()
        assertFalse(VerticalCurlGeometry.evaluate(0f, 2340f, 0f, frame))
        assertFalse(VerticalCurlGeometry.evaluate(1080f, Float.NaN, 0f, frame))
    }

    @Test
    fun tapDurationMatchesAcceptedTiming() {
        assertEquals(620, CurlPageAnim.TAP_DURATION_MS)
    }
}
