package com.huangder.lumibooks.ui.reader

import androidx.compose.ui.geometry.Rect
import com.huangder.lumibooks.domain.model.PageRenderMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfTileLayerTest {
    @Test
    fun longPageUsesScreenWidthAndLoadsOnlyVisibleRows() {
        val ratio = 640f / 15417f
        val viewport = Rect(0f, 0f, 1200f, 2400f)
        val page = Rect(0f, 0f, 1200f, 1200f / ratio)
        val tiles = visiblePdfTiles(0, page, viewport, ratio, RasterHorizontalCrop.FULL,
            fallbackWidth = 226, PageRenderMode.NORMAL)
        val scrolled = visiblePdfTiles(0, page.translate(0f, -8000f), viewport, ratio,
            RasterHorizontalCrop.FULL, fallbackWidth = 226, PageRenderMode.NORMAL)

        assertEquals(setOf(1280), tiles.map { it.pageWidth }.toSet())
        assertEquals(setOf(0, 768, 1536, 2304), tiles.map { it.top }.toSet())
        assertTrue(scrolled.minOf { it.top } >= 7680)
        assertTrue((tiles + scrolled).all { it.width <= 768 && it.height <= 768 })
    }

    @Test
    fun zoomedAndCroppedPageKeepsTilesInFullPageCoordinates() {
        val tiles = visiblePdfTiles(1, Rect(-300f, 0f, 2100f, 4800f),
            Rect(0f, 0f, 1200f, 2400f), 0.8f, RasterHorizontalCrop(0.2f, 0.8f),
            fallbackWidth = 2000, PageRenderMode.HIGH)

        assertEquals(setOf(5120), tiles.map { it.pageWidth }.toSet())
        assertEquals(setOf(768, 1536, 2304), tiles.map { it.left }.toSet())
    }

    @Test
    fun sharpFallbackAndPageOutsideViewportNeedNoTiles() {
        val viewport = Rect(0f, 0f, 1200f, 2400f)
        assertTrue(visiblePdfTiles(0, viewport, viewport, 0.5f, RasterHorizontalCrop.FULL,
            fallbackWidth = 1200, PageRenderMode.NORMAL).isEmpty())
        assertTrue(visiblePdfTiles(0, viewport.translate(0f, 2400f), viewport, 0.5f,
            RasterHorizontalCrop.FULL, fallbackWidth = 226, PageRenderMode.NORMAL).isEmpty())
    }
}
