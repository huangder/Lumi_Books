package com.huangder.lumibooks.ui.components

import androidx.compose.animation.core.TargetBasedAnimation
import androidx.compose.animation.core.VectorConverter
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class LiquidMenuGeometryTest {
    private val source = Rect(300f, 30f, 344f, 74f)
    private val target = Rect(124f, 30f, 344f, 410f)
    private fun frame(p: Float) = LiquidMenuGeometry.frame(source, target, p, 22f, 24f)

    @Test fun endpointsMatchTheRealButtonAndPanel() {
        assertEquals(source, frame(0f).bounds)
        assertEquals(target, frame(1f).bounds)
        assertEquals(22f, frame(0f).radius, 0.001f)
        assertEquals(24f, frame(1f).radius, 0.001f)
        assertEquals(0f, frame(0f).neck, 0f)
        assertEquals(0f, frame(1f).neck, 0f)
        assertEquals(1f, LiquidMenuGeometry.sourceAlpha(0f), 0f)
        assertEquals(0f, LiquidMenuGeometry.sourceAlpha(1f), 0f)
        assertEquals(0f, LiquidMenuGeometry.contentAlpha(0f), 0f)
        assertEquals(1f, LiquidMenuGeometry.contentAlpha(1f), 0f)
        assertEquals(0f, frame(1f).distortion, 0f)
    }

    @Test fun neckPullsTowardTheButtonAndReleasesBeforeSettling() {
        val f = frame(0.3f)
        val topLeft = f.warp(Offset.Zero)
        val topRight = f.warp(Offset(f.bounds.width, 0f))
        assertTrue(f.neck > 0.4f)
        assertTrue(topLeft.x > f.bounds.width * 0.4f)
        assertEquals(f.bounds.width, topRight.x, 0.001f)
        assertEquals(Offset(0f, f.bounds.height), f.warp(Offset(0f, f.bounds.height)))
        assertEquals(0f, frame(0.9f).neck, 0f)
    }

    @Test fun bothDirectionsUseTheSameContinuousShape() {
        for (step in 0..1000) {
            val p = step / 1000f
            val a = frame(p)
            val b = frame(p + 0.0001f)
            assertTrue(abs(a.bounds.left - b.bounds.left) < 0.2f)
            assertTrue(abs(a.bounds.bottom - b.bounds.bottom) < 0.2f)
            assertTrue(abs(a.neck - b.neck) < 0.002f)
        }
    }

    @Test fun shaderInverseAndOutlineAgreeForEveryOrientation() {
        for (x in listOf(-1f, 1f)) for (y in listOf(-1f, 1f)) {
            val f = frame(0.35f).copy(nearX = x, nearY = y)
            for (i in 0..10) for (j in 0..10) {
                val p = Offset(f.bounds.width * i / 10f, f.bounds.height * j / 10f)
                val restored = f.warp(f.warp(p), inverse = true)
                assertEquals(p.x, restored.x, 0.001f)
                assertEquals(p.y, restored.y, 0.001f)
            }
        }
    }

    @Test fun aboveMenuMirrorsTheVerticalTravelAndNeck() {
        fun flip(rect: Rect) = Rect(rect.left, 800f - rect.bottom, rect.right, 800f - rect.top)
        for (step in 0..20) {
            val p = step / 20f
            val above = LiquidMenuGeometry.frame(flip(source), flip(target), p, 22f, 24f)
            assertEquals(flip(frame(p).bounds).top, above.bounds.top, 0.001f)
            assertEquals(1f, above.nearY, 0f)
            assertEquals(frame(p).neck, above.neck, 0f)
        }
    }

    @Test fun extremePhasesAndSmallMenusStayFiniteAndBounded() {
        for (size in listOf(1f, 20f, 220f)) for (step in -10..120) {
            val f = LiquidMenuGeometry.frame(source, Rect(100f, 100f, 100f + size, 100f + size), step / 100f, 22f, 24f)
            assertTrue(f.bounds.width > 0f && f.bounds.height > 0f)
            assertTrue(f.radius >= 0f)
            f.outlinePoints().forEach { point ->
                assertTrue(point.x.isFinite() && point.y.isFinite())
                assertTrue(point.x >= -0.001f && point.x <= f.bounds.width + 0.001f)
                assertTrue(point.y >= -0.001f && point.y <= f.bounds.height + 0.001f)
            }
        }
    }

    @Test fun verticalStretchLeadsWidthAndBothReturnToTheTarget() {
        for (p in listOf(0.2f, 0.4f, 0.6f, 0.8f)) {
            val bounds = frame(p).bounds
            val widthGrowth = (bounds.width - source.width) / (target.width - source.width)
            val heightGrowth = (bounds.height - source.height) / (target.height - source.height)
            assertTrue("The droplet stretches before opening out", heightGrowth > widthGrowth)
        }
        assertEquals(target, frame(1f).bounds)
    }

    @Test fun motionHasReadableTravelSoftReboundAndRetainsReversalVelocity() {
        val open = TargetBasedAnimation(LiquidMenuGeometry.Motion.openSpec(), Float.VectorConverter, 0f, 1f)
        assertTrue("Opening must remain visible beyond a quick pop", open.getValueFromNanos(200_000_000) < 0.9f)
        val peak = (0..100).maxOf { open.getValueFromNanos(it * 10_000_000L) }
        assertTrue("A restrained overshoot makes the settling perceptible", peak in 1.02f..1.08f)
        assertEquals(1f, open.getValueFromNanos(700_000_000), 0.01f)
        val close = TargetBasedAnimation(LiquidMenuGeometry.Motion.closeSpec(), Float.VectorConverter, 1f, 0f)
        assertTrue("Closing must leave time to see the returning droplet", close.getValueFromNanos(150_000_000) > 0.25f)
        assertEquals(0f, close.getValueFromNanos(650_000_000), 0.01f)
        val phase = open.getValueFromNanos(120_000_000)
        val velocity = open.getVelocityVectorFromNanos(120_000_000).value
        val reverse = TargetBasedAnimation(LiquidMenuGeometry.Motion.closeSpec(), Float.VectorConverter, phase, 0f, velocity)
        assertEquals(phase, reverse.getValueFromNanos(0), 0f)
        assertEquals(velocity, reverse.getVelocityVectorFromNanos(0).value, 0.001f)
    }

    @Test fun closingDeceleratesIntoTheButtonWithoutAnEarlyClampOrRebound() {
        val close = TargetBasedAnimation(LiquidMenuGeometry.Motion.closeSpec(), Float.VectorConverter, 1f, 0f)
        var previous = 1f
        for (ms in 0L..1000L step 10) {
            val phase = close.getValueFromNanos(ms * 1_000_000)
            assertTrue("Closing should approach zero without crossing it at $ms ms", phase >= 0f)
            assertTrue("Closing should never grow back after shrinking", phase <= previous)
            previous = phase
        }
        val tail = close.getValueFromNanos(300_000_000)
        assertTrue("Keep a visible droplet during the final return", frame(tail).bounds.height > source.height + 10f)
        assertTrue(LiquidMenuGeometry.sourceAlpha(tail) < 0.25f)
        val later = close.getValueFromNanos(450_000_000)
        assertTrue("The source must blend back gradually", LiquidMenuGeometry.sourceAlpha(later) > 0.9f)
    }

    @Test fun passingTheExpandedSizeDoesNotIntroduceAVelocityKink() {
        val step = 0.0001f
        val leftSlope = (frame(1f).bounds.height - frame(1f - step).bounds.height) / step
        val rightSlope = (frame(1f + step).bounds.height - frame(1f).bounds.height) / step
        assertTrue("Both sides of the overshoot join should ease toward zero slope", abs(rightSlope - leftSlope) < 5f)
    }
}
