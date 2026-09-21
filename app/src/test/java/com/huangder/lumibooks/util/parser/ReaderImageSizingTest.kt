package com.huangder.lumibooks.util.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
    fun singleImagePageUpscalesToFillTheContentColumn() {
        // 漫画页常见 860px 宽：不放大时在手机上只有半屏宽（"好小一个照片"）。
        assertEquals(860, ReaderImageSizing.bounds(860, 1146, 990)!!.width)
        val filled = ReaderImageSizing.bounds(860, 1146, 990, allowUpscale = true)!!
        assertEquals(990, filled.width)
        assertEquals(1319, filled.height)
        // 放大时不需要降采样解码。
        assertEquals(1, ReaderImageSizing.decodeSampleSize(860, 1146, 990, allowUpscale = true))
    }

    @Test
    fun singleImagePageIsCappedByPageHeight() {
        // 漫画页 860x1146 在 990x1200 的内容盒里：宽高都要放得下，取较小的缩放比。
        val fitted = ReaderImageSizing.fitBounds(860, 1146, 990, 1200)!!
        assertEquals(1200, fitted.height)
        // 取整会有 1px 误差，只要贴着高度上限即可。
        assertTrue(fitted.width in 899..901)
        // 高度不受限（上下滚动）时按宽度铺满。
        val widthOnly = ReaderImageSizing.fitBounds(860, 1146, 990, 0)!!
        assertEquals(990, widthOnly.width)
        assertEquals(1319, widthOnly.height)
        // 横长图受宽度限制。
        val wide = ReaderImageSizing.fitBounds(2000, 500, 990, 1200)!!
        assertEquals(990, wide.width)
        assertTrue(wide.height in 246..248)
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
