package com.huangder.lumibooks.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class PillSliderTest {
    @Test
    fun valueSnapsToConfiguredStepAndRange() {
        assertEquals(1.5f, snapSliderValue(1.46f, 1f..2f, 0.1f), 0.0001f)
        assertEquals(2f, snapSliderValue(2.4f, 1f..2f, 0.1f), 0.0001f)
        assertEquals(1f, snapSliderValue(0.4f, 1f..2f, 0.1f), 0.0001f)
    }

    @Test
    fun tapFractionMapsInBothLayoutDirections() {
        assertEquals(25f, sliderValueFromFraction(0.25f, 0f..100f, isLtr = true), 0.0001f)
        assertEquals(75f, sliderValueFromFraction(0.25f, 0f..100f, isLtr = false), 0.0001f)
    }

    @Test
    fun visualFractionMirrorsInRtl() {
        assertEquals(0.2f, sliderFraction(20f, 0f..100f, isLtr = true), 0.0001f)
        assertEquals(0.8f, sliderFraction(20f, 0f..100f, isLtr = false), 0.0001f)
    }

    @Test
    fun thumbOverhangIsSymmetricAtTrackEnds() {
        val width = 300f
        val thumbWidth = 40f
        val left = sliderThumbOffsetPx(0f, width, thumbWidth)
        val right = sliderThumbOffsetPx(1f, width, thumbWidth)

        assertEquals(-10f, left, 0.0001f)
        assertEquals(10f, right + thumbWidth - width, 0.0001f)
    }
}
