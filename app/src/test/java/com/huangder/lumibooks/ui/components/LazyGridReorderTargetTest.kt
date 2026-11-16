package com.huangder.lumibooks.ui.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Test

class LazyGridReorderTargetTest {
    // Two columns of 100×150 cells, 20 px apart.
    private val slots = listOf(
        Rect(0f, 0f, 100f, 150f),
        Rect(120f, 0f, 220f, 150f),
        Rect(0f, 170f, 100f, 320f),
        Rect(120f, 170f, 220f, 320f)
    )

    @Test
    fun pointInsideACellTargetsThatCell() {
        assertEquals(0, reorderTargetIndex(Offset(50f, 75f), slots, slack = 10f))
        assertEquals(3, reorderTargetIndex(Offset(200f, 300f), slots, slack = 10f))
    }

    @Test
    fun spacingBetweenCellsGoesToTheNearestCentre() {
        // In the horizontal gap, nearer the right column.
        assertEquals(1, reorderTargetIndex(Offset(115f, 75f), slots, slack = 10f))
        // In the vertical gap, nearer the lower row.
        assertEquals(2, reorderTargetIndex(Offset(50f, 165f), slots, slack = 10f))
    }

    @Test
    fun farOutsideEveryCellHasNoTarget() {
        assertEquals(-1, reorderTargetIndex(Offset(500f, 75f), slots, slack = 10f))
        assertEquals(-1, reorderTargetIndex(Offset(50f, 75f), emptyList(), slack = 10f))
    }
}
