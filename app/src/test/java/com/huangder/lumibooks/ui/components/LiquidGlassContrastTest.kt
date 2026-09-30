package com.huangder.lumibooks.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LiquidGlassContrastTest {
    @Test
    fun lightLabelsRemainReadableOverWhitePageContent() {
        val surface = liquidGlassContrastScrim(true, Color(0xFF202020))
            .compositeOver(Color.White)
        assertTrue(contrast(Color(0xFFEBEBF5), surface) >= 4.5f)
    }

    @Test
    fun darkLabelsRemainReadableOverBlackPageContent() {
        val surface = liquidGlassContrastScrim(true, Color(0xFFF5E6D3))
            .compositeOver(Color.Black)
        assertTrue(contrast(Color(0xFF1C1C1E), surface) >= 4.5f)
    }

    @Test
    fun disabledLeavesOriginalGlassUntouched() {
        assertEquals(Color.Transparent, liquidGlassContrastScrim(false, Color.White))
        assertEquals(Color.Transparent, liquidGlassContrastScrim(false, Color.Black))
        assertEquals(1f, liquidGlassContrastTransparency(false, 1f), 0f)
    }

    @Test
    fun enabledKeepsFrostingWithoutMakingAlreadyFrostedGlassClearer() {
        assertTrue(liquidGlassContrastTransparency(true, 1f) < 0.5f)
        assertEquals(0.1f, liquidGlassContrastTransparency(true, 0.1f), 0f)
    }

    private fun contrast(first: Color, second: Color): Float {
        val a = first.luminance()
        val b = second.luminance()
        return (maxOf(a, b) + 0.05f) / (minOf(a, b) + 0.05f)
    }
}
