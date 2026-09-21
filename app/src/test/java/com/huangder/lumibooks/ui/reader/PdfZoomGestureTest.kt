package com.huangder.lumibooks.ui.reader

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Test

class PdfZoomGestureTest {
    @Test
    fun `pan is clamped and outward drag is retained`() {
        val result = consumePdfPanDelta(offset = 80f, maxOffset = 100f, delta = 40f)

        assertEquals(100f, result.offset, 0.001f)
        assertEquals(20f, result.edgeDrag, 0.001f)
    }

    @Test
    fun `reverse drag cancels pending edge drag`() {
        val atEdge = consumePdfPanDelta(offset = 100f, maxOffset = 100f, delta = 40f)
        val reversed = consumePdfPanDelta(
            offset = atEdge.offset,
            maxOffset = 100f,
            delta = -25f,
            edgeDrag = atEdge.edgeDrag
        )

        assertEquals(75f, reversed.offset, 0.001f)
        assertEquals(15f, reversed.edgeDrag, 0.001f)
    }

    @Test
    fun `edge drag maps to next or previous page`() {
        assertEquals(4, pdfPageForEdgeDrag(currentPage = 3, pageCount = 8, edgeDrag = -60f))
        assertEquals(2, pdfPageForEdgeDrag(currentPage = 3, pageCount = 8, edgeDrag = 60f))
        assertEquals(3, pdfPageForEdgeDrag(currentPage = 3, pageCount = 8, edgeDrag = 20f))
    }

    @Test
    fun `edge page target is clamped at document bounds`() {
        assertEquals(0, pdfPageForEdgeDrag(currentPage = 0, pageCount = 8, edgeDrag = 80f))
        assertEquals(7, pdfPageForEdgeDrag(currentPage = 7, pageCount = 8, edgeDrag = -80f))
    }

    @Test
    fun `zero zoom range always resets offset`() {
        val result = consumePdfPanDelta(offset = 0f, maxOffset = 0f, delta = -10f)

        assertEquals(0f, result.offset, 0.001f)
        assertEquals(-10f, result.edgeDrag, 0.001f)
    }

    @Test
    fun `annotation transform prefers a clear two finger pan over pinch jitter`() {
        val mode = resolvePdfTransformMode(
            panMotion = 12f,
            zoomMotion = 10f,
            touchSlop = 8f,
            preferPan = true
        )

        assertEquals(PdfMultiTouchMode.PAN, mode)
    }

    @Test
    fun `annotation transform still accepts a deliberate pinch`() {
        val mode = resolvePdfTransformMode(
            panMotion = 3f,
            zoomMotion = 18f,
            touchSlop = 8f,
            preferPan = true
        )

        assertEquals(PdfMultiTouchMode.ZOOM, mode)
    }

    @Test
    fun `small two finger movement remains undecided`() {
        val mode = resolvePdfTransformMode(
            panMotion = 4f,
            zoomMotion = 5f,
            touchSlop = 8f,
            preferPan = true
        )

        assertEquals(PdfMultiTouchMode.UNDECIDED, mode)
    }

    @Test
    fun `zoom gesture can decay when it ends with a pan`() {
        assertEquals(
            true,
            shouldStartPdfPanDecay(
                scale = 2f,
                mode = PdfMultiTouchMode.ZOOM,
                navigationGesture = true
            )
        )
    }

    @Test
    fun `zoom gesture at resting scale does not start custom decay`() {
        assertEquals(
            false,
            shouldStartPdfPanDecay(
                scale = 1f,
                mode = PdfMultiTouchMode.ZOOM,
                navigationGesture = true
            )
        )
    }

    @Test
    fun `pan velocity estimator preserves a recent release velocity`() {
        val estimator = PdfPanVelocityEstimator()
        estimator.reset(0L)
        estimator.addPan(16L, Offset(0f, 16f))
        estimator.addPan(32L, Offset(0f, 16f))

        assertEquals(0f, estimator.velocityAt(40L).x, 0.001f)
        assertEquals(933.333f, estimator.velocityAt(40L).y, 0.01f)
    }

    @Test
    fun `pan velocity estimator discards a paused release`() {
        val estimator = PdfPanVelocityEstimator()
        estimator.reset(0L)
        estimator.addPan(16L, Offset(20f, 0f))

        assertEquals(Offset.Zero, estimator.velocityAt(200L))
    }

    @Test
    fun `estimated velocity replaces a missing tracked axis`() {
        val velocity = resolvePdfReleaseVelocity(
            tracked = Offset.Zero,
            estimated = Offset(640f, -920f)
        )

        assertEquals(640f, velocity.x, 0.001f)
        assertEquals(-920f, velocity.y, 0.001f)
    }
}
