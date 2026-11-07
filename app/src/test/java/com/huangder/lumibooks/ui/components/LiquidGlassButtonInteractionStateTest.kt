package com.huangder.lumibooks.ui.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.shape.CircleShape
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiquidGlassButtonInteractionStateTest {
    @Test
    fun pressExpansionIsConsistentAcrossButtonSizes() {
        val transform = liquidGlassButtonLayerTransform(
            width = 160f,
            height = 48f,
            pressProgress = 1f,
            dragOffset = Offset.Zero,
            expansionPx = 4f,
            motionEnabled = true
        )

        assertEquals(1.10f, transform.scaleX, 0.0001f)
        assertEquals(1.10f, transform.scaleY, 0.0001f)
        assertEquals(0f, transform.translationX, 0.0001f)
        assertEquals(0f, transform.translationY, 0.0001f)
    }

    @Test
    fun horizontalDragStretchesAndCompressesAcrossItsDirection() {
        val transform = liquidGlassButtonLayerTransform(
            width = 160f,
            height = 48f,
            pressProgress = 1f,
            dragOffset = Offset(80f, 0f),
            expansionPx = 4f,
            motionEnabled = true
        )
        val baseScale = 1.10f

        assertTrue(transform.translationX > 0f)
        assertEquals(0f, transform.translationY, 0.0001f)
        assertTrue(transform.scaleX > baseScale)
        assertTrue(transform.scaleY < baseScale)
    }

    @Test
    fun diagonalTranslationIsSymmetricAndRadiallyBounded() {
        fun transform(offset: Offset) = liquidGlassButtonLayerTransform(
            48f, 48f, 1f, offset, 4f, true, maxTranslationPx = 6f)
        val positive = transform(Offset(10_000f, 10_000f))
        val negative = transform(Offset(-10_000f, -10_000f))
        assertEquals(positive.translationX, -negative.translationX, 0.0001f)
        assertEquals(positive.translationY, -negative.translationY, 0.0001f)
        assertTrue(Offset(positive.translationX, positive.translationY).getDistance() <= 4.801f)
        assertTrue(positive.scaleX <= 1.16f && positive.scaleY <= 1.16f)
    }

    @Test
    fun pressScaleDoesNotDependOnDensityOrCapsuleAspectRatio() {
        for ((width, height) in listOf(32f to 32f, 160f to 48f, 960f to 144f)) {
            val transform = liquidGlassButtonLayerTransform(width, height, 1f, Offset.Zero, 4f, true)
            assertEquals(1.1f, transform.scaleX, 0.0001f)
            assertEquals(1.1f, transform.scaleY, 0.0001f)
        }
    }

    @Test
    fun velocityLimitPreservesDirection() {
        val velocity = boundedButtonVelocity(Offset(30_000f, -40_000f))
        assertEquals(2400f, velocity.getDistance(), .01f)
        assertEquals(-.75f, velocity.x / velocity.y, .001f)
        assertEquals(Offset(20f, 10f), boundedButtonVelocity(Offset(20f, 10f)))
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
    fun dragStretchSaturatesSmoothlyAtLongDistances() {
        val atLimit = liquidGlassButtonLayerTransform(
            width = 160f,
            height = 48f,
            pressProgress = 1f,
            dragOffset = Offset(1_000f, 0f),
            expansionPx = 4f,
            motionEnabled = true
        )
        val farAway = liquidGlassButtonLayerTransform(
            width = 160f,
            height = 48f,
            pressProgress = 1f,
            dragOffset = Offset(10_000f, 0f),
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
