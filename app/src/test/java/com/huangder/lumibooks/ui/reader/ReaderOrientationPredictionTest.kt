package com.huangder.lumibooks.ui.reader

import org.junit.Assert.*
import org.junit.Test

class ReaderOrientationPredictionTest {
    @Test fun predictsContentAreaThenUsesMeasuredOrientation() {
        val predictor = ReaderViewportPredictor()
        val portrait = ReaderViewport(1000, 1800)
        assertEquals(ReaderViewport(2000, 800), predictor.observeBounds(portrait, ReaderViewport(1000, 2000), 160, insetY = 100))
        val landscape = ReaderViewport(1920, 910)
        assertEquals(portrait, predictor.observeBounds(landscape, ReaderViewport(2000, 1000), 160))
        assertEquals(landscape, predictor.observeBounds(portrait, ReaderViewport(1000, 2000), 160))
    }

    @Test fun edgeToEdgeDoesNotSubtractUnconsumedInsetsAndResizeDiscardsOldMatch() {
        val predictor = ReaderViewportPredictor()
        assertEquals(ReaderViewport(2000, 1000), predictor.observeBounds(ReaderViewport(1000, 2000), ReaderViewport(1000, 2000), 160, insetY = 100))
        predictor.observeBounds(ReaderViewport(1940, 950), ReaderViewport(2000, 1000), 160)
        assertEquals(ReaderViewport(1600, 800), predictor.observeBounds(ReaderViewport(800, 1600), ReaderViewport(800, 1600), 160))
        assertNull(predictor.observeBounds(ReaderViewport(700, 900), ReaderViewport(700, 900), 160, multiWindow = true))
    }
}
