package com.huangder.lumibooks.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppAccentColorTest {
    @Test
    fun normalizeAcceptsOptionalHashAndCanonicalizesCase() {
        assertEquals("#1A2B3C", normalizeAppAccentHex("1a2b3c"))
        assertEquals("#1A2B3C", normalizeAppAccentHex("  #1a2B3c  "))
    }

    @Test
    fun normalizeFallsBackForInvalidValues() {
        listOf(null, "", "#123", "#12345678", "#GG0000").forEach { value ->
            assertEquals(DEFAULT_APP_ACCENT_HEX, normalizeAppAccentHex(value))
        }
    }

    @Test
    fun contentColorUsesBlackOnlyForBrightAccents() {
        assertEquals(0xFFFFFFFF.toInt(), appAccentContentArgb(parseAppAccentArgb("#E85D5D")))
        assertEquals(0xFF000000.toInt(), appAccentContentArgb(parseAppAccentArgb("#FFFF00")))
    }

    @Test
    fun darkAccentRaisesLowContrastColorsToTarget() {
        val darkAccent = deriveDarkAppAccentArgb(parseAppAccentArgb("#102040"))
        assertTrue(contrastRatio(darkAccent, 0xFF1C1C1E.toInt()) >= 6.99)
    }

    @Test
    fun darkAccentLeavesReadableColorsUnchanged() {
        val bright = parseAppAccentArgb("#FFFF00")
        assertEquals(bright, deriveDarkAppAccentArgb(bright))
    }

    @Test
    fun blendUsesRequestedForegroundFraction() {
        assertEquals(0xFF808080.toInt(), blendAppAccentArgb(0xFF000000.toInt(), 0xFFFFFFFF.toInt(), 0.5f))
    }
}
