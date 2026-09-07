package com.huangder.lumibooks.ui.reader

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
}
