package com.huangder.lumibooks.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class FloatingTabBarTest {
    @Test
    fun projectedTargetUsesNearestTabForSlowRelease() {
        assertEquals(1, projectedTabTarget(currentValue = 0.62f, velocity = 0.1f, lastIndex = 2))
    }

    @Test
    fun projectedTargetUsesVelocityForFastRelease() {
        assertEquals(2, projectedTabTarget(currentValue = 1.15f, velocity = 3.2f, lastIndex = 2))
        assertEquals(0, projectedTabTarget(currentValue = 0.85f, velocity = -3.2f, lastIndex = 2))
    }

    @Test
    fun projectedTargetStaysWithinAvailableTabs() {
        assertEquals(0, projectedTabTarget(currentValue = 0f, velocity = -20f, lastIndex = 2))
        assertEquals(2, projectedTabTarget(currentValue = 2f, velocity = 20f, lastIndex = 2))
    }

    @Test
    fun panelOffsetIsSymmetricAndBounded() {
        val left = dampedTabPanelOffset(-320f, panelWidthPx = 640f, maxOffsetPx = 8f)
        val right = dampedTabPanelOffset(320f, panelWidthPx = 640f, maxOffsetPx = 8f)

        assertEquals(-left, right, 0.0001f)
        assertEquals(8f, dampedTabPanelOffset(5000f, 640f, 8f), 0.0001f)
        assertEquals(-8f, dampedTabPanelOffset(-5000f, 640f, 8f), 0.0001f)
    }

    @Test
    fun prismScaleExpandsHorizontalAndVerticalEdgesEqually() {
        val scaleY = 1.4f
        val width = 160f
        val height = 64f
        val scaleX = equalEdgePrismScaleX(scaleY, width, height)

        val horizontalExpansionPerEdge = width * (scaleX - 1f) / 2f
        val verticalExpansionPerEdge = height * (scaleY - 1f) / 2f
        assertEquals(verticalExpansionPerEdge, horizontalExpansionPerEdge, 0.0001f)
    }
}
