package com.huangder.lumibooks.ui.animation

import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MainTabMotionTest {
    private val frame = 8_333_333L

    @Test
    fun incomingItemsTrackBothDirectionsInTopToBottomOrder() {
        val forward = MainTabMotion().apply { begin(0, 0f) }
        val backward = MainTabMotion().apply { begin(1, 1f) }
        for (step in 1..16) {
            val progress = step / 100f
            forward.advance(step * frame, progress)
            backward.advance(step * frame, 1f - progress)
        }
        val top = forward.itemOffset(1, 0f)
        val bottom = forward.itemOffset(1, 1f)
        assertTrue("Top items must stay close to the finger", top in .02f.. .12f)
        assertTrue("Bottom items follow after the top", bottom > top + .04f)
        assertTrue(MAIN_TAB_MAX_STAGGER_MS <= 500L)
        for (height in listOf(0f, .25f, .5f, 1f)) {
            assertEquals(-forward.itemOffset(1, height), backward.itemOffset(0, height), .00001f)
        }
        assertEquals(0f, forward.itemOffset(0, .5f), 0f)
    }

    @Test
    fun slowDragCatchesUpOnceAndSubsequentMovementHasNoDelay() {
        val forward = MainTabMotion().apply { begin(0, 0f) }
        val backward = MainTabMotion().apply { begin(1, 1f) }
        for (step in 1..160) {
            // Continue slowly, pause, then reverse without lifting the finger.
            val progress = when {
                step < 70 -> step / 200f
                step < 110 -> .35f
                else -> .35f - (step - 110) / 200f
            }
            forward.advance(step * frame, progress)
            backward.advance(step * frame, 1f - progress)
            if (step * frame > (MAIN_TAB_CATCH_UP_MS + 20L) * 1_000_000L) {
                for (height in listOf(0f, .25f, .5f, 1f)) {
                    assertEquals("Continuing the same drag must stay attached", 0f, forward.itemOffset(1, height), 0f)
                    assertEquals(0f, backward.itemOffset(0, height), 0f)
                }
            }
        }
    }

    @Test
    fun releaseAfterCatchingUpKeepsASoftRebound() {
        val motion = MainTabMotion().apply { begin(0, 0f) }
        for (step in 1..60) motion.advance(step * frame, step / 80f)
        assertEquals(0f, motion.itemOffset(1, 1f), 0f)
        motion.release()
        assertEquals("Lifting the finger must not jump the content", 0f, motion.itemOffset(1, 1f), 0f)
        var sawOvershoot = false
        for (step in 61..130) {
            val target = (step / 80f).coerceAtMost(1f)
            if (step == 80) motion.settle(1f)
            motion.advance(step * frame, target)
            if (target == 1f && motion.itemOffset(1, 1f) < -.001f) sawOvershoot = true
        }
        assertTrue(sawOvershoot)
        assertTrue(abs(motion.itemOffset(1, 1f)) < .004f)
    }

    @Test
    fun slowDragThenFastSnapStaysAttachedAndFinishesWithABoundedRebound() {
        for (direction in listOf(1f, -1f)) {
            val origin = if (direction > 0) 0 else 1
            val destination = 1 - origin
            val motion = MainTabMotion().apply { begin(origin, origin.toFloat()) }
            for (step in 1..60) motion.advance(step * frame, origin + direction * step / 200f)
            motion.release()
            for (step in 61..65) {
                val progress = .3f + (step - 60) * .14f
                motion.advance(step * frame, origin + direction * progress)
                assertEquals("A fast snap must not reintroduce lag", 0f, motion.itemOffset(destination, 1f), .000001f)
            }
            motion.settle(destination.toFloat())
            var previous = motion.itemOffset(destination, 1f)
            assertEquals("Settling must start at the last visible position", 0f, previous, .000001f)
            var peak = 0f
            for (step in 66..125) {
                motion.advance(step * frame, destination.toFloat())
                val offset = motion.itemOffset(destination, 1f)
                peak = maxOf(peak, abs(offset))
                assertTrue("No large jump or hard limit at the edge", abs(offset - previous) < .008f)
                previous = offset
            }
            assertTrue("Keep a subtle rebound without hitting the old hard clamp", peak in .001f.. .015f)
            assertTrue(abs(previous) < .0004f)
        }
    }

    @Test
    fun lastPagerFrameCannotPullAttachedItemsBackwards() {
        val motion = MainTabMotion().apply { begin(0, 0f) }
        for (step in 1..60) motion.advance(step * frame, step / 200f)
        motion.advance(61 * frame, .95f)
        motion.release()
        // The pager can finish its final frame before the item frame clock runs.
        motion.settle(1f)
        assertEquals(0f, motion.itemOffset(1, 1f), 0f)
        motion.advance(62 * frame, 1f)
        assertTrue("The first settling frame should only move slightly beyond the edge", motion.itemOffset(1, 1f) in -.008f..0f)
    }

    @Test
    fun fastReleaseDeceleratesOvershootsAndReturnsWithinTheSettleWindow() {
        val motion = MainTabMotion().apply { begin(0, 0f) }
        for (step in 1..12) motion.advance(step * frame, step / 12f)
        motion.release()
        var sawOvershoot = false
        var previousBottom = motion.itemOffset(1, 1f)
        var previousStep = Float.MAX_VALUE
        var sawDeceleration = false
        for (step in 13..70) {
            motion.advance(step * frame, 1f)
            val top = motion.itemOffset(1, 0f)
            val bottom = motion.itemOffset(1, 1f)
            if (top < -.001f || bottom < -.001f) sawOvershoot = true
            assertTrue("Overshoot stays subtle", top >= -.02501f && bottom >= -.02501f)
            val distance = abs(bottom - previousBottom)
            if (bottom > .01f && distance < previousStep) sawDeceleration = true
            previousStep = distance
            previousBottom = bottom
        }
        assertTrue("The release should visibly ease out", sawDeceleration)
        assertTrue("The finish should cross the resting position", sawOvershoot)
        assertTrue(abs(motion.itemOffset(1, 1f)) < .004f)
        motion.finish(1f)
        assertFalse(motion.running)
        assertTrue(motion.isAtRest(1f))
        assertEquals(0f, motion.itemOffset(1, 1f), 0f)
    }

    @Test
    fun cancelledDragAndLongFrameLeaveNoResidualOffset() {
        val motion = MainTabMotion().apply { begin(0, 0f) }
        for (step in 1..20) motion.advance(step * frame, step / 80f)
        motion.advance(1_000_000_000L, .25f)
        for (step in 1..100) motion.advance(1_000_000_000L + step * frame, 0f)
        assertTrue(motion.isAtRest(0f))
        motion.finish(0f)
        motion.begin(0, 0f)
        assertEquals(0f, motion.itemOffset(1, .5f), 0f)
    }
}
