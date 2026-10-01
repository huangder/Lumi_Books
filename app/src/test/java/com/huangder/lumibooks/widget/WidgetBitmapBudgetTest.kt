package com.huangder.lumibooks.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetBitmapBudgetTest {
    @Test
    fun leavesNormalWidgetBitmapUnchanged() {
        assertEquals(
            BitmapDimensions(960, 480),
            WidgetBitmapBudget.constrain(960, 480, maxPixels = 1080L * 2400L)
        )
    }

    @Test
    fun clampsExtremeDimensionsBelowScreenPixelBudget() {
        val maxPixels = WidgetBitmapBudget.maxBitmapPixels(1080, 2400)
        val dimensions = WidgetBitmapBudget.constrain(20_000, 10_000, maxPixels)
        assertTrue(dimensions.pixelCount <= maxPixels)
        assertEquals(2.0, dimensions.width.toDouble() / dimensions.height, 0.01)
    }

    @Test
    fun handlesInvalidDimensionsWithoutExceedingTinyBudget() {
        val dimensions = WidgetBitmapBudget.constrain(0, -10, maxPixels = 1)
        assertEquals(BitmapDimensions(1, 1), dimensions)
    }
}
