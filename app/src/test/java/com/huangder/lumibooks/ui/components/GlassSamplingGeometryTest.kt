package com.huangder.lumibooks.ui.components

import androidx.compose.ui.unit.IntSize
import com.huangder.lumibooks.domain.model.LiquidGlassSamplingQuality
import com.kyant.backdrop.backdropSampleSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GlassSamplingGeometryTest {
    @Test fun presetsHaveExpectedLinearResolutionAndSafeFallback() {
        val viewport = IntSize(1080, 1920)
        assertEquals(IntSize(1080, 1920), LiquidGlassSamplingQuality.ORIGINAL.sampleSize(viewport))
        assertEquals(IntSize(720, 1280), LiquidGlassSamplingQuality.CLEAR.sampleSize(viewport))
        assertEquals(IntSize(540, 960), LiquidGlassSamplingQuality.BALANCED.sampleSize(viewport))
        assertEquals(IntSize(360, 640), LiquidGlassSamplingQuality.PERFORMANCE.sampleSize(viewport))
        assertEquals(LiquidGlassSamplingQuality.BALANCED, LiquidGlassSamplingQuality.fromStoredValue(null))
        assertEquals(LiquidGlassSamplingQuality.BALANCED, LiquidGlassSamplingQuality.fromStoredValue("future"))
        assertEquals(0.5f, LiquidGlassSamplingQuality.BALANCED.scale, 0f)
        assertEquals(LiquidGlassSamplingQuality.CLEAR, LiquidGlassSamplingQuality.fromStoredValue("balanced"))
        assertEquals(LiquidGlassSamplingQuality.BALANCED, LiquidGlassSamplingQuality.fromStoredValue("smooth"))
    }

    @Test fun effectBuffersCoverOddViewportSizesWithoutChangingScreenCoordinates() {
        for (viewport in listOf(IntSize(1081, 2401), IntSize(2401, 1081), IntSize(1, 1))) {
            for (quality in LiquidGlassSamplingQuality.entries) {
                val sampled = backdropSampleSize(viewport, quality.scale)
                assertEquals(quality.sampleSize(viewport), sampled)
                assertTrue(sampled.width / quality.scale >= viewport.width)
                assertTrue(sampled.height / quality.scale >= viewport.height)
            }
        }
        assertEquals(IntSize(1, 1), LiquidGlassSamplingQuality.BALANCED.sampleSize(IntSize.Zero))
    }

    @Test fun windowCaptureRetainsItsExistingCapBeforeApplyingQuality() {
        val viewport = IntSize(1080, 2400)
        assertEquals(IntSize(648, 1440), windowGlassSampleSize(viewport, LiquidGlassSamplingQuality.ORIGINAL))
        assertEquals(IntSize(432, 960), windowGlassSampleSize(viewport, LiquidGlassSamplingQuality.CLEAR))
        assertEquals(IntSize(324, 720), windowGlassSampleSize(viewport, LiquidGlassSamplingQuality.BALANCED))
        assertEquals(IntSize(216, 480), windowGlassSampleSize(viewport, LiquidGlassSamplingQuality.PERFORMANCE))
        assertEquals(IntSize(960, 432), windowGlassSampleSize(IntSize(2400, 1080), LiquidGlassSamplingQuality.CLEAR))
    }
}
