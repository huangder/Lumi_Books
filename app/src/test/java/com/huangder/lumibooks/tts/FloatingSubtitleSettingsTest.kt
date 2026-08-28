package com.huangder.lumibooks.tts

import org.junit.Assert.assertEquals
import org.junit.Test

class FloatingSubtitleSettingsTest {
    @Test
    fun normalizedClampsEveryNumericSetting() {
        val result = FloatingSubtitleSettings(
            xFraction = -1f,
            yFraction = 2f,
            backgroundOpacity = 3f,
            cornerRadiusDp = 100f,
            widthDp = 1f,
            heightDp = 1_000f
        ).normalized()

        assertEquals(0f, result.xFraction)
        assertEquals(1f, result.yFraction)
        assertEquals(1f, result.backgroundOpacity)
        assertEquals(FloatingSubtitleSettings.MAX_CORNER_RADIUS_DP, result.cornerRadiusDp)
        assertEquals(FloatingSubtitleSettings.MIN_WIDTH_DP, result.widthDp)
        assertEquals(FloatingSubtitleSettings.MAX_HEIGHT_DP, result.heightDp)
    }

    @Test
    fun normalizedCanonicalizesValidColorAndRejectsInvalidColor() {
        assertEquals(
            "#12ABEF",
            FloatingSubtitleSettings(backgroundColorHex = "  #12abef ").normalized().backgroundColorHex
        )
        assertEquals(
            FloatingSubtitleSettings.DEFAULT_BACKGROUND_COLOR,
            FloatingSubtitleSettings(backgroundColorHex = "transparent").normalized().backgroundColorHex
        )
    }
}
