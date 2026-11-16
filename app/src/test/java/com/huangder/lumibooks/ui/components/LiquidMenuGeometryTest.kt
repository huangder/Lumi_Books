package com.huangder.lumibooks.ui.components

import androidx.compose.animation.core.TargetBasedAnimation
import androidx.compose.animation.core.VectorConverter
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.huangder.lumibooks.ui.components.LiquidMenuCloseStyle.ReturnToSource
import com.huangder.lumibooks.ui.components.LiquidMenuCloseStyle.Vanish
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class LiquidMenuGeometryTest {
    // A round 44px button opening a tall menu below it, aligned to its end.
    private val source = Rect(300f, 30f, 344f, 74f)
    private val target = Rect(124f, 30f, 344f, 410f)
    // A wide 158x36 selector capsule opening a 248x300 menu aligned to its end.
    private val capsule = Rect(150f, 30f, 308f, 66f)
    private val capsuleTarget = Rect(60f, 30f, 308f, 330f)

    private fun frame(p: Float, style: LiquidMenuCloseStyle = ReturnToSource, mix: Float = 0f) =
        LiquidMenuGeometry.frame(source, target, p, 22f, 24f, style, mix)

    private fun wide(p: Float, style: LiquidMenuCloseStyle = ReturnToSource, mix: Float = 0f) =
        LiquidMenuGeometry.frame(capsule, capsuleTarget, p, 18f, 24f, style, mix)

    private fun assertRect(expected: Rect, actual: Rect, tolerance: Float = 0.01f) {
        assertEquals(expected.left, actual.left, tolerance)
        assertEquals(expected.top, actual.top, tolerance)
        assertEquals(expected.right, actual.right, tolerance)
        assertEquals(expected.bottom, actual.bottom, tolerance)
    }

    @Test fun everyPathStartsOnTheTriggerAndEndsOnThePanel() {
        for (mix in listOf(0f, 1f)) {
            assertRect(source, frame(0f, mix = mix).bounds)
            assertRect(target, frame(1f, mix = mix).bounds)
            assertRect(capsule, wide(0f, mix = mix).bounds)
            assertRect(capsuleTarget, wide(1f, mix = mix).bounds)
            assertEquals(22f, frame(0f, mix = mix).radius, 0.001f)
            assertEquals(24f, frame(1f, mix = mix).radius, 0.001f)
            for (f in listOf(frame(0f, mix = mix), frame(1f, mix = mix), wide(0f, mix = mix), wide(1f, mix = mix))) {
                assertEquals(0f, f.neck, 0f)
                assertEquals(0f, f.highlight, 0f)
                assertEquals(0f, f.distortion, 0f)
            }
            assertEquals(0f, LiquidMenuGeometry.contentAlpha(0f, mix), 0f)
            assertEquals(1f, LiquidMenuGeometry.contentAlpha(1f, mix), 0f)
        }
        assertRect(capsuleTarget, wide(1f, Vanish, 1f).bounds)
        assertEquals(1f, LiquidMenuGeometry.squeezedSourceAlpha(0f), 0f)
        assertEquals(0f, LiquidMenuGeometry.squeezedSourceAlpha(1f), 0f)
        assertEquals(1f, LiquidMenuGeometry.squeezedSourceAlpha(0f, ReturnToSource, 1f), 0f)
        assertEquals(0f, LiquidMenuGeometry.squeezedSourceAlpha(0f, Vanish, 1f), 0f)
    }

    @Test fun theWholeWideCapsuleContractsIntoADropletBeforeInflating() {
        val narrowest = (0..50).map { wide(it / 100f) }.minBy { it.bounds.width }
        assertTrue("The capsule must contract: ${narrowest.bounds}", narrowest.bounds.width < capsule.width * 0.6f)
        assertTrue("…into a near-round droplet", narrowest.bounds.width / narrowest.bounds.height < 1.6f)
        // A round button is already a droplet: it barely changes before inflating.
        val roundNarrowest = (0..50).minOf { frame(it / 100f).bounds.width }
        assertTrue(roundNarrowest >= source.width * 0.85f)
    }

    @Test fun theDropletIsBrightenedWhenPressedAndInflatesAsARoundDroplet() {
        assertTrue((0..10).maxOf { wide(it / 100f).highlight } > 0.5f)
        assertEquals(0f, wide(0.4f).highlight, 0f)
        for (p in listOf(0.3f, 0.4f, 0.5f, 0.6f)) {
            val f = wide(p)
            val aspect = f.bounds.width / f.bounds.height
            assertTrue("A slightly upright round droplet at $p: $aspect", aspect in 0.8f..1.02f)
            assertTrue("…drawn with fully round ends at $p", f.radius >= f.bounds.minDimension / 2f * 0.9f)
        }
        // It grows to most of the panel before it squares off.
        assertTrue(wide(0.6f).bounds.width > capsuleTarget.width * 0.6f)
    }

    @Test fun eachPathIsContinuous() {
        for (style in LiquidMenuCloseStyle.entries) for (mix in listOf(0f, 0.5f, 1f)) {
            for (step in 0 until 2000) {
                val p = step / 2000f
                val a = wide(p, style, mix)
                val b = wide(p + 0.0005f, style, mix)
                assertTrue("$style/$mix at $p", abs(a.bounds.left - b.bounds.left) < 1f)
                assertTrue("$style/$mix at $p", abs(a.bounds.bottom - b.bounds.bottom) < 1f)
                assertTrue(abs(a.neck - b.neck) < 0.005f)
            }
        }
    }

    @Test fun reversingMidwayBlendsThePathsWithoutAJump() {
        for (style in LiquidMenuCloseStyle.entries) for (p in listOf(0.1f, 0.3f, 0.6f, 0.9f)) {
            for (step in 0 until 1000) {
                val mix = step / 1000f
                val a = wide(p, style, mix)
                val b = wide(p, style, mix + 0.001f)
                assertTrue("$style p=$p mix=$mix", abs(a.bounds.left - b.bounds.left) < 0.6f)
                assertTrue("$style p=$p mix=$mix", abs(a.bounds.top - b.bounds.top) < 0.6f)
                assertTrue("$style p=$p mix=$mix", abs(a.alpha - b.alpha) < 0.01f)
            }
        }
    }

    @Test fun closingRetracesTheOpeningWithoutThePressWash() {
        for (step in 0..100) {
            val p = step / 100f
            val open = wide(p)
            val close = wide(p, ReturnToSource, 1f)
            assertRect(open.bounds, close.bounds, 0.001f)
            assertEquals(open.radius, close.radius, 0.001f)
            assertEquals(open.neck, close.neck, 0f)
            assertEquals(0f, close.highlight, 0f)
            // The trigger fades back in, squeezed into the droplet, as it opened.
            assertEquals(LiquidMenuGeometry.squeezedSourceAlpha(p), LiquidMenuGeometry.squeezedSourceAlpha(p, ReturnToSource, 1f), 0f)
        }
    }

    @Test fun theReturningTriggerLandsWithASlightSquash() {
        val landing = wide(-0.05f, ReturnToSource, 1f).bounds
        assertTrue(landing.width < capsule.width && landing.height > capsule.height)
        assertEquals(capsule.center.x, landing.center.x, 0.01f)
        assertEquals(capsule.center.y, landing.center.y, 0.01f)
        assertRect(capsule, wide(0f, ReturnToSource, 1f).bounds)
    }

    @Test fun aVanishingDropletShrinksAndFadesWithoutReturning() {
        val gone = wide(0f, Vanish, 1f)
        assertEquals(0f, gone.alpha, 0f)
        assertTrue(gone.bounds.width <= capsule.height)
        assertEquals(0f, LiquidMenuGeometry.squeezedSourceAlpha(0.1f, Vanish, 1f), 0f)
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
        for (mix in listOf(0f, 1f)) for (step in 0..20) {
            val p = step / 20f
            val below = wide(p, ReturnToSource, mix)
            val above = LiquidMenuGeometry.frame(flip(capsule), flip(capsuleTarget), p, 18f, 24f, ReturnToSource, mix)
            assertEquals(flip(below.bounds).top, above.bounds.top, 0.01f)
            assertEquals(flip(below.bounds).bottom, above.bounds.bottom, 0.01f)
            assertEquals(1f, above.nearY, 0f)
            assertEquals(below.neck, above.neck, 0f)
        }
    }

    @Test fun extremePhasesAndSmallMenusStayFiniteAndBounded() {
        for (style in LiquidMenuCloseStyle.entries) for (mix in listOf(0f, 0.4f, 1f)) {
            for (size in listOf(1f, 20f, 220f)) for (step in -10..120) {
                val f = LiquidMenuGeometry.frame(
                    capsule, Rect(100f, 100f, 100f + size, 100f + size), step / 100f, 18f, 24f, style, mix
                )
                assertTrue(f.bounds.width > 0f && f.bounds.height > 0f)
                assertTrue(f.radius >= 0f && f.alpha in 0f..1f)
                f.outlinePoints().forEach { point ->
                    assertTrue(point.x.isFinite() && point.y.isFinite())
                    assertTrue(point.x >= -0.001f && point.x <= f.bounds.width + 0.001f)
                    assertTrue(point.y >= -0.001f && point.y <= f.bounds.height + 0.001f)
                }
            }
        }
    }

    @Test fun motionHasReadableTravelSoftReboundAndRetainsReversalVelocity() {
        val open = TargetBasedAnimation(LiquidMenuGeometry.Motion.openSpec(), Float.VectorConverter, 0f, 1f)
        assertTrue("Contracting into the droplet is visible", open.getValueFromNanos(50_000_000) < 0.3f)
        assertTrue("Opening must remain visible beyond a quick pop", open.getValueFromNanos(200_000_000) < 0.92f)
        val peak = (0..100).maxOf { open.getValueFromNanos(it * 10_000_000L) }
        assertTrue("A restrained overshoot makes the settling perceptible: $peak", peak in 1.015f..1.06f)
        assertEquals(1f, open.getValueFromNanos(800_000_000), 0.01f)
        val close = TargetBasedAnimation(LiquidMenuGeometry.Motion.closeSpec(), Float.VectorConverter, 1f, 0f)
        assertTrue("Closing leaves time to see the droplet retrace", close.getValueFromNanos(150_000_000) > 0.25f)
        val landing = (0..80).minOf { close.getValueFromNanos(it * 10_000_000L) }
        assertTrue("A small landing squash, not a bounce: $landing", landing in -0.07f..-0.02f)
        assertEquals(0f, close.getValueFromNanos(700_000_000), 0.01f)
        val phase = open.getValueFromNanos(120_000_000)
        val velocity = open.getVelocityVectorFromNanos(120_000_000).value
        val reverse = TargetBasedAnimation(LiquidMenuGeometry.Motion.closeSpec(), Float.VectorConverter, phase, 0f, velocity)
        assertEquals(phase, reverse.getValueFromNanos(0), 0f)
        assertEquals(velocity, reverse.getVelocityVectorFromNanos(0).value, 0.001f)
    }

    @Test fun passingTheExpandedSizeDoesNotIntroduceAVelocityKink() {
        val step = 0.0001f
        for (f in listOf(::frame, ::wide)) {
            val leftSlope = (f(1f, ReturnToSource, 0f).bounds.height - f(1f - step, ReturnToSource, 0f).bounds.height) / step
            val rightSlope = (f(1f + step, ReturnToSource, 0f).bounds.height - f(1f, ReturnToSource, 0f).bounds.height) / step
            assertTrue("Both sides of the overshoot join should ease toward zero slope", abs(rightSlope - leftSlope) < 5f)
        }
    }
}
