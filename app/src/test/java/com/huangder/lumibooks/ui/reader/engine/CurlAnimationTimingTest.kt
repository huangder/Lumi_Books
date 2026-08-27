package com.huangder.lumibooks.ui.reader.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class CurlAnimationTimingTest {
    @Test
    fun settleDurationUsesConfiguredBaseAndRemainingFraction() {
        assertEquals(128, curlSettleDurationMs(300, 0f, 100f))
        assertEquals(214, curlSettleDurationMs(300, 50f, 100f))
        assertEquals(300, curlSettleDurationMs(300, 100f, 100f))

        assertEquals(340, curlSettleDurationMs(800, 0f, 100f))
        assertEquals(570, curlSettleDurationMs(800, 50f, 100f))
        assertEquals(800, curlSettleDurationMs(800, 100f, 100f))

        assertEquals(510, curlSettleDurationMs(1200, 0f, 100f))
        assertEquals(855, curlSettleDurationMs(1200, 50f, 100f))
        assertEquals(1200, curlSettleDurationMs(1200, 100f, 100f))
    }

    @Test
    fun normalizationMakesDifferentEndpointDistancesUseSameDuration() {
        assertEquals(
            curlSettleDurationMs(800, 500f, 1000f),
            curlSettleDurationMs(800, 750f, 1500f)
        )
    }

    @Test
    fun fractionsAreClampedAndZeroPathUsesMinimum() {
        assertEquals(340, curlSettleDurationMs(800, -20f, 100f))
        assertEquals(800, curlSettleDurationMs(800, 200f, 100f))
        assertEquals(340, curlSettleDurationMs(800, 20f, 0f))
    }

    @Test
    fun expeditedDurationUsesThirtyPercentWithinBounds() {
        assertEquals(120, curlExpeditedDurationMs(300))
        assertEquals(240, curlExpeditedDurationMs(800))
        assertEquals(240, curlExpeditedDurationMs(1200))
    }
}
