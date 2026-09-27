package com.huangder.lumibooks.ui.components

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import org.junit.Assert.*
import org.junit.Test

class LiquidGlassControlEdgeTest {
    @Test fun outlineNormalSeparatesSideDarkeningFromTopBottomReflection() {
        assertTrue(controlEdgeDarkAlpha(1f) > 3f * controlEdgeDarkAlpha(0f))
        assertEquals(controlEdgeDarkAlpha(-1f), controlEdgeDarkAlpha(1f), 0f)
        assertTrue(controlEdgeLightAlpha(0f, -1f) > controlEdgeLightAlpha(1f, 0f))
        assertTrue(controlEdgeLightAlpha(0f, 1f) > controlEdgeLightAlpha(-1f, 0f))
        // The join from the upper straight segment to its circular end is continuous.
        assertEquals(controlEdgeDarkAlpha(0f), controlEdgeDarkAlpha(0.001f), 0.0001f)
        assertEquals(controlEdgeLightAlpha(0f, -1f), controlEdgeLightAlpha(0.001f, -0.9999995f), 0.0002f)
    }

    @Test fun tintedRimRetainsWarmHueAndDarkSurfacesReduceContrast() {
        val gold = controlEdgePalette(Color(0xFFE6AD00), false)
        assertTrue(gold.dark.red > gold.dark.green && gold.dark.green > gold.dark.blue)
        assertTrue(gold.light.red > gold.light.green && gold.light.green > gold.light.blue)
        val light = controlEdgePalette(Color.White, false)
        val dark = controlEdgePalette(Color.White, true)
        val blackButton = controlEdgePalette(Color.Black, false)
        assertTrue(dark.darkStrength < light.darkStrength)
        assertTrue(dark.lightStrength < light.lightStrength)
        assertEquals(dark, blackButton)
    }

    @Test fun shaderSelectionRequiresAndroid13AndSupportedActualOutline() {
        val density = Density(3f)
        val capsule = CircleShape.createOutline(Size(480f, 144f), LayoutDirection.Ltr, density)
        val circle = CircleShape.createOutline(Size(144f, 144f), LayoutDirection.Rtl, density)
        assertFalse(controlEdgeUsesShader(31, capsule))
        assertFalse(controlEdgeUsesShader(32, capsule))
        assertTrue(controlEdgeUsesShader(33, capsule))
        assertTrue(controlEdgeUsesShader(35, circle))
        val unequal = RoundedCornerShape(topStart = 4.dp, topEnd = 12.dp).createOutline(
            Size(120f, 60f), LayoutDirection.Ltr, density
        )
        assertFalse(controlEdgeUsesShader(35, unequal))
        assertFalse(controlEdgeUsesShader(35, RectangleShape.createOutline(Size(20f, 20f), LayoutDirection.Ltr, density)))
        assertTrue(capsule is Outline.Rounded)
    }
}
