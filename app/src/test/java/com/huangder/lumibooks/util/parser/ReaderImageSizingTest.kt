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
}
