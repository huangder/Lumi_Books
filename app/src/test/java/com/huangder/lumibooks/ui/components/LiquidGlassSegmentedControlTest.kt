package com.huangder.lumibooks.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class LiquidGlassSegmentedControlTest {

    @Test
    fun equalGeometry_accountsForSpacing() {
        val geometry = equalLiquidGlassSegmentGeometry(
            totalWidthPx = 308f,
            count = 3,
            spacingPx = 4f
        )

        assertEquals(listOf(0f, 104f, 208f), geometry.startsPx)
        assertEquals(listOf(100f, 100f, 100f), geometry.widthsPx)
        assertEquals(308f, geometry.totalWidthPx, 0.001f)
    }

    @Test
    fun variableGeometry_preservesCallerWidths() {
        val geometry = variableLiquidGlassSegmentGeometry(
            widthsPx = listOf(72f, 112f, 84f),
            spacingPx = 4f
        )

        assertEquals(listOf(0f, 76f, 192f), geometry.startsPx)
        assertEquals(listOf(72f, 112f, 84f), geometry.widthsPx)
        assertEquals(276f, geometry.totalWidthPx, 0.001f)
    }

    @Test
    fun centerMapping_mirrorsInRtl() {
        val geometry = equalLiquidGlassSegmentGeometry(204f, 2, 4f)

        assertEquals(0f, liquidGlassSegmentValueForCenter(50f, geometry, isLtr = true), 0.001f)
        assertEquals(1f, liquidGlassSegmentValueForCenter(154f, geometry, isLtr = true), 0.001f)
        assertEquals(0f, liquidGlassSegmentValueForCenter(154f, geometry, isLtr = false), 0.001f)
        assertEquals(1f, liquidGlassSegmentValueForCenter(50f, geometry, isLtr = false), 0.001f)
    }

    @Test
    fun centerMapping_clampsAtBothBoundaries() {
        val geometry = equalLiquidGlassSegmentGeometry(300f, 3)

        assertEquals(0f, liquidGlassSegmentValueForCenter(-100f, geometry, isLtr = true), 0.001f)
        assertEquals(2f, liquidGlassSegmentValueForCenter(500f, geometry, isLtr = true), 0.001f)
        assertEquals(2f, liquidGlassSegmentValueForCenter(-100f, geometry, isLtr = false), 0.001f)
        assertEquals(0f, liquidGlassSegmentValueForCenter(500f, geometry, isLtr = false), 0.001f)
    }

    @Test
    fun snapTarget_skipsDisabledSegments() {
        val enabled = listOf(true, false, true, true)

        assertEquals(0, nearestEnabledLiquidGlassSegment(0.7f, enabled))
        assertEquals(2, nearestEnabledLiquidGlassSegment(1.6f, enabled))
        assertEquals(3, nearestEnabledLiquidGlassSegment(2.8f, enabled))
        assertEquals(-1, nearestEnabledLiquidGlassSegment(1f, List(4) { false }))
    }

    @Test
    fun overshoot_isCappedBySixPixelsForWideSegments() {
        val geometry = equalLiquidGlassSegmentGeometry(300f, 3)

        assertEquals(
            2.06f,
            cappedLiquidGlassSegmentValue(2.4f, 0f, 2, geometry, maximumOvershootPx = 6f),
            0.001f
        )
        assertEquals(
            -0.06f,
            cappedLiquidGlassSegmentValue(-0.4f, 2f, 0, geometry, maximumOvershootPx = 6f),
            0.001f
        )
    }

    @Test
    fun overshoot_isCappedAtEightPercentForNarrowSegments() {
        val geometry = equalLiquidGlassSegmentGeometry(150f, 3)

        assertEquals(
            1.08f,
            cappedLiquidGlassSegmentValue(1.5f, 0f, 1, geometry, maximumOvershootPx = 6f),
            0.001f
        )
    }

    @Test
    fun dynamicOptionCounts_rebuildAnchorsWithoutStaleEntries() {
        val threeItems = equalLiquidGlassSegmentGeometry(300f, 3)
        val twoItems = equalLiquidGlassSegmentGeometry(300f, 2)

        assertEquals(3, threeItems.size)
        assertEquals(listOf(50f, 150f, 250f), threeItems.centersPx)
        assertEquals(2, twoItems.size)
        assertEquals(listOf(75f, 225f), twoItems.centersPx)
    }
}
