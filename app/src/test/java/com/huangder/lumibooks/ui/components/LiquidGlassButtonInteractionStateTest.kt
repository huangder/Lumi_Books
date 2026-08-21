package com.huangder.lumibooks.ui.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.shape.CircleShape
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiquidGlassButtonInteractionStateTest {
    @Test
    fun pressExpansionUsesFixedVisualDistance() {
        val transform = liquidGlassButtonLayerTransform(
            width = 160f,
            height = 48f,
            pressProgress = 1f,
            dragOffset = Offset.Zero,
            expansionPx = 4f,
            motionEnabled = true
        )

        assertEquals(1f + 4f / 48f, transform.scaleX, 0.0001f)
        assertEquals(1f + 4f / 48f, transform.scaleY, 0.0001f)
        assertEquals(0f, transform.translationX, 0.0001f)
        assertEquals(0f, transform.translationY, 0.0001f)
    }

    @Test
    fun horizontalDragStretchesOnlyHorizontalAxis() {
        val transform = liquidGlassButtonLayerTransform(
            width = 160f,
            height = 48f,
            pressProgress = 1f,
            dragOffset = Offset(80f, 0f),
            expansionPx = 4f,
            motionEnabled = true
        )
        val baseScale = 1f + 4f / 48f

        assertTrue(transform.translationX > 0f)
        assertEquals(0f, transform.translationY, 0.0001f)
        assertTrue(transform.scaleX > baseScale)
        assertEquals(baseScale, transform.scaleY, 0.0001f)
    }

    @Test
    fun dampedTranslationIsSymmetricAndBounded() {
        val positive = dampedLiquidGlassButtonOffset(10_000f, 48f)
        val negative = dampedLiquidGlassButtonOffset(-10_000f, 48f)

        assertEquals(positive, -negative, 0.0001f)
        assertTrue(positive > 0f)
        assertTrue(positive <= 48f)
    }

    @Test
    fun reducedMotionDisablesMovementAndScale() {
        val transform = liquidGlassButtonLayerTransform(
            width = 48f,
            height = 48f,
            pressProgress = 1f,
            dragOffset = Offset(120f, -80f),
            expansionPx = 4f,
            motionEnabled = false
        )

        assertEquals(0f, transform.translationX, 0f)
        assertEquals(0f, transform.translationY, 0f)
        assertEquals(1f, transform.scaleX, 0f)
        assertEquals(1f, transform.scaleY, 0f)
    }

    @Test
    fun dragStretchStopsGrowingPastLimit() {
        val atLimit = liquidGlassButtonLayerTransform(
            width = 160f,
            height = 48f,
            pressProgress = 1f,
            dragOffset = Offset(36f, 0f),
            expansionPx = 4f,
            motionEnabled = true
        )
        val farAway = liquidGlassButtonLayerTransform(
            width = 160f,
            height = 48f,
            pressProgress = 1f,
            dragOffset = Offset(1_000f, 0f),
            expansionPx = 4f,
            motionEnabled = true
        )

        assertEquals(atLimit.translationX, farAway.translationX, 0.0001f)
        assertEquals(atLimit.scaleX, farAway.scaleX, 0.0001f)
        assertEquals(atLimit.scaleY, farAway.scaleY, 0.0001f)
    }

    @Test
    fun contentFollowsWithSmallerStretchAndTranslation() {
        val surface = liquidGlassButtonLayerTransform(
            width = 160f,
            height = 48f,
            pressProgress = 1f,
            dragOffset = Offset(30f, 12f),
            expansionPx = 4f,
            motionEnabled = true
        )
        val content = liquidGlassButtonContentTransform(surface)

        assertTrue(content.translationX > 0f)
        assertTrue(content.translationX < surface.translationX)
        assertTrue(content.translationY > 0f)
        assertTrue(content.translationY < surface.translationY)
        assertTrue(content.scaleX > 1f)
        assertTrue(content.scaleX < surface.scaleX)
        assertTrue(content.scaleY > 1f)
        assertTrue(content.scaleY < surface.scaleY)
    }

    @Test
    fun lensSupportRejectsCustomContinuousShape() {
        assertTrue(supportsLiquidGlassLens(CircleShape))
        assertFalse(supportsLiquidGlassLens(G2ContinuousCornerShape(24f)))
    }
}
