package com.huangder.lumibooks.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Test

class LiquidGlassCapabilityTest {
    @Test
    fun unsupportedLiquidGlassFallsBackWithoutChangingStoredTheme() {
        val capability = LiquidGlassCapability(supported = false, hdrSupported = false)

        assertEquals("lumi", effectiveAppTheme("liquid_glass", capability))
    }

    @Test
    fun supportedLiquidGlassRemainsActiveWithoutHdr() {
        val capability = LiquidGlassCapability(supported = true, hdrSupported = false)

        assertEquals("liquid_glass", effectiveAppTheme("liquid_glass", capability))
    }

    @Test
    fun otherThemesAreNeverChangedByCapability() {
        val capability = LiquidGlassCapability(supported = false, hdrSupported = false)

        assertEquals("lumi", effectiveAppTheme("lumi", capability))
        assertEquals("material3", effectiveAppTheme("material3", capability))
    }
}
