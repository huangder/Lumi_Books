package com.huangder.lumibooks.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StorageVisualizationTest {
    @Test
    fun emptyValuesProduceEmptySegments() {
        assertEquals(listOf(0f, 0f, 0f), storageSegmentFractions(listOf(0L, 0L, 0L)))
    }

    @Test
    fun positiveSegmentsAreNormalizedAndRemainVisible() {
        val fractions = storageSegmentFractions(listOf(10_000L, 1L, 0L, 500L))

        assertEquals(1f, fractions.sum(), 0.0001f)
        assertTrue(fractions[0] > fractions[3])
        assertTrue(fractions[1] >= 0.017f)
        assertEquals(0f, fractions[2], 0f)
    }

    @Test
    fun negativeValuesAreTreatedAsZero() {
        val fractions = storageSegmentFractions(listOf(-1L, 5L))

        assertEquals(0f, fractions[0], 0f)
        assertEquals(1f, fractions[1], 0f)
    }

    @Test
    fun entranceLayerFallsAsAThinSeedBeforeGrowingToTargetHeight() {
        assertEquals(0f, storageLayerVisibleHeight(100f, 10f, 0f, 0f), 0f)
        assertEquals(5f, storageLayerVisibleHeight(100f, 10f, 0.5f, 0f), 0f)
        assertEquals(55f, storageLayerVisibleHeight(100f, 10f, 1f, 0.5f), 0f)
        assertEquals(100f, storageLayerVisibleHeight(100f, 10f, 1f, 1f), 0f)
    }

    @Test
    fun zeroByteCategoriesKeepHairlineVisualLayers() {
        val visual = storageDisplayFractions(listOf(0.8f, 0f, 0.2f, 0f))

        assertEquals(1f, visual.sum(), 0.0001f)
        assertEquals(0.008f, visual[1], 0f)
        assertEquals(0.008f, visual[3], 0f)
        assertTrue(visual[0] > visual[2])
    }
}
