package com.huangder.lumibooks.ui.reader.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SimulationCurlShadowStyleTest {
    @Test
    fun lightenedAlphaThresholdsMatchDesign() {
        assertEquals(0x38, SimulationCurlShadowStyle.FOLD_SHADOW_MAX_ALPHA)
        assertEquals(0x30, SimulationCurlShadowStyle.BACK_PAGE_SHADOW_MAX_ALPHA)
        assertEquals(0x20, SimulationCurlShadowStyle.CREASE_SHADOW_MAX_ALPHA)
        assertEquals(0x38 shl 24, SimulationCurlShadowStyle.fold(0xFF))
        assertEquals(0x30 shl 24, SimulationCurlShadowStyle.backPage(0xFF))
        assertEquals(0x18 shl 24, SimulationCurlShadowStyle.crease(0x18))
        assertEquals(0x20 shl 24, SimulationCurlShadowStyle.crease(0xFF))
    }

    @Test
    fun shadowWidthScalesAsTwentyFiveDp() {
        assertEquals(25f, SimulationCurlShadowStyle.widthPx(1f), 0.001f)
        assertEquals(50f, SimulationCurlShadowStyle.widthPx(2f), 0.001f)
        assertTrue(SimulationCurlShadowStyle.widthPx(Float.NaN).isFinite())
    }
}
