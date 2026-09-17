package com.huangder.lumibooks.util.parser

import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderImageSizingTest {
    @Test
    fun lowResolutionImageIsNotUpscaled() {
        assertEquals(ReaderImageBounds(300, 200), ReaderImageSizing.bounds(300, 200, 1000))
    }

    @Test
    fun highResolutionImageIsCappedToContentWidth() {
        assertEquals(ReaderImageBounds(1000, 625), ReaderImageSizing.bounds(2000, 1250, 1000))
    }

    @Test
    fun aspectRatioIsRoundedWithoutStretching() {
        assertEquals(ReaderImageBounds(333, 222), ReaderImageSizing.bounds(500, 333, 333))
    }

    @Test
    fun decodeSampleKeepsDecodedWidthAtOrAboveDisplayedWidth() {
        assertEquals(2, ReaderImageSizing.decodeSampleSize(2000, 1250, 1000))
        assertEquals(1, ReaderImageSizing.decodeSampleSize(300, 200, 1000))
    }

    @Test
    fun invalidDimensionsReturnNoBounds() {
        assertEquals(null, ReaderImageSizing.bounds(0, 100, 1000))
        assertEquals(1, ReaderImageSizing.decodeSampleSize(0, 100, 1000))
    }

    @Test
    fun nonMarkerImageIsDrawnAtItsSlotSizeInsteadOfBeingSquared() {
        val rect = ReaderImageSizing.drawRect(
            slotLeft = 20f,
            slotTop = 40f,
            slotWidth = 600f,
            slotHeight = 240f,
            isInlineMarker = false,
            markerSizePx = 30f
        )
        assertEquals(20f, rect.left, 0.001f)
        assertEquals(40f, rect.top, 0.001f)
        assertEquals(600f, rect.width, 0.001f)
        assertEquals(240f, rect.height, 0.001f)
    }

    @Test
    fun portraitImageKeepsItsOwnAspectRatioWhenDrawn() {
        val bounds = requireNotNull(ReaderImageSizing.bounds(800, 1200, 600))
        val rect = ReaderImageSizing.drawRect(
            slotLeft = 0f,
            slotTop = 0f,
            slotWidth = bounds.width.toFloat(),
            slotHeight = bounds.height.toFloat(),
            isInlineMarker = false,
            markerSizePx = 30f
        )
        // 槽位是原图等比缩放后的结果（800x1200 → 600x900），绘制矩形必须与它一致。
        assertEquals(800f / 1200f, rect.width / rect.height, 0.01f)
        assertEquals(600f, rect.width, 0.001f)
        assertEquals(900f, rect.height, 0.001f)
    }

    @Test
    fun inlineMarkerStaysSquareAndCentredInsideItsSlot() {
        val rect = ReaderImageSizing.drawRect(
            slotLeft = 100f,
            slotTop = 50f,
            slotWidth = 72f,
            slotHeight = 72f,
            isInlineMarker = true,
            markerSizePx = 20f
        )
        assertEquals(20f, rect.width, 0.001f)
        assertEquals(20f, rect.height, 0.001f)
        assertEquals(126f, rect.left, 0.001f)
        assertEquals(76f, rect.top, 0.001f)
    }
}
