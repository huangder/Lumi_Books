package com.huangder.lumibooks.ui.theme

import org.junit.Assert.*
import org.junit.Test

class LumiBackgroundGeometryTest {
    @Test fun mainArtworkAlwaysTouchesPhysicalRightAndClearsShelfHeader() {
        // Portrait/landscape phones and tablets in physical pixels. RTL uses these same coordinates.
        for ((width, height) in listOf(1080f to 2400f, 2400f to 1080f, 1600f to 2560f, 2560f to 1600f)) {
            val header = 680f
            val placement = lumiMainPlacement(width, height, 72f, 48f, 3f, header)
            assertTrue(placement.width > 0f)
            assertEquals(width, placement.x + placement.width, 0.01f)
            assertEquals(120f, placement.topY, 0.01f)
            assertTrue(placement.bottomY >= header + 48f)
            assertTrue(placement.bottomY >= 72f + (height - 120f) / 2f)
            assertEquals(height - 96f, placement.bottomY + placement.width, 0.01f)
            assertTrue(placement.width <= width * 0.85f)
            assertTrue(placement.width <= 560f * 3f)
        }
    }

    @Test fun secondarySubjectIsCenteredAt68PercentWithoutStretching() {
        for ((width, height) in listOf(1080f to 2400f, 2400f to 1080f, 2560f to 1600f)) {
            val p = lumiSecondaryPlacement(width, height, 72f, 48f, 3f)
            val scale = p.width / 1024f
            assertEquals(scale, p.height / 1536f, 0.0001f)
            assertEquals(width / 2f, p.x + (61f + 937f / 2f) * scale, 0.01f)
            assertEquals(72f + (height - 120f) * 0.68f, p.y + (647f + 870f / 2f) * scale, 0.01f)
            assertTrue(937f * scale <= width * 0.85f + 0.01f)
            assertTrue(870f * scale <= (height - 120f) * 0.55f + 0.01f)
        }
    }

    @Test fun tinyWindowsDoNotProduceNegativeSizes() {
        val p = lumiMainPlacement(300f, 150f, 50f, 40f, 3f, 100f)
        assertTrue(p.width >= 0f)
        assertEquals(300f, p.x + p.width, 0.01f)
    }

    @Test fun unsupportedGlassFallsBackWithoutChangingStoredTheme() {
        assertEquals("liquid_glass", effectiveAppTheme("lumi_chan", LiquidGlassCapability(true, false)))
        assertEquals("lumi", effectiveAppTheme("lumi_chan", LiquidGlassCapability(false, false)))
        assertEquals("material3", effectiveAppTheme("material3", LiquidGlassCapability(false, false)))
    }
}
