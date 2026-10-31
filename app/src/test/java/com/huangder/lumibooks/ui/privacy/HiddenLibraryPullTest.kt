package com.huangder.lumibooks.ui.privacy

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HiddenLibraryPullTest {
    private val reveal = 24f
    private val threshold = 100f
    private val gap = 8f

    @Test
    fun lockStaysHiddenForSmallRubberBandPulls() {
        assertEquals(0f, hiddenPullProgress(0f, reveal, threshold), 0.0001f)
        assertEquals(0f, hiddenPullProgress(reveal, reveal, threshold), 0.0001f)
        assertEquals(0f, hiddenPullProgress(-40f, reveal, threshold), 0.0001f)
    }

    @Test
    fun progressFillsBetweenRevealAndThreshold() {
        assertEquals(0.5f, hiddenPullProgress(62f, reveal, threshold), 0.0001f)
        assertEquals(1f, hiddenPullProgress(threshold, reveal, threshold), 0.0001f)
        assertEquals(1f, hiddenPullProgress(400f, reveal, threshold), 0.0001f)
    }

    @Test
    fun armingHasHysteresisSoItDoesNotFlickerAtTheThreshold() {
        assertFalse(hiddenPullArmed(threshold - 1f, wasArmed = false, threshold, gap))
        assertTrue(hiddenPullArmed(threshold, wasArmed = false, threshold, gap))
        assertTrue(hiddenPullArmed(threshold - gap + 1f, wasArmed = true, threshold, gap))
        assertFalse(hiddenPullArmed(threshold - gap, wasArmed = true, threshold, gap))
    }

    @Test
    fun releaseOpensOnlyWhenArmedAndResetsTheState() {
        val state = HiddenLibraryPullState().apply {
            thresholdPx = threshold
            disarmGapPx = gap
            revealPx = reveal
        }
        assertFalse(state.release(threshold - 1f))
        assertTrue(state.release(threshold + 30f))
        assertFalse(state.armed)
        assertTrue(state.settling)
        // A plain fling at rest has nothing to spring back from.
        assertFalse(state.release(0f))
        assertFalse(state.settling)
    }

    @Test
    fun reverseDragCancelsReleaseEvenWhenOffsetIsStillArmed() {
        val state = HiddenLibraryPullState().apply {
            thresholdPx = threshold
            disarmGapPx = gap
            revealPx = reveal
            armed = true
        }
        state.onDrag(100f, 100f)
        state.onDrag(92f, -8f)

        assertFalse(state.release(92f))
    }

    @Test
    fun lockSitsInTheMiddleOfTheOpeningGapButClearsTheStatusBar() {
        assertEquals(212f, hiddenPullLockCenterY(gapTopPx = 150f, restingGapPx = 24f, offsetPx = 100f, minCenterPx = 0f), 0.0001f)
        assertEquals(80f, hiddenPullLockCenterY(gapTopPx = 0f, restingGapPx = 0f, offsetPx = 40f, minCenterPx = 80f), 0.0001f)
    }

    @Test
    fun entranceStaggerKeepsEveryPartInsideTheTimeline() {
        assertEquals(0f, gateStagger(0f, 5), 0.0001f)
        assertEquals(1f, gateStagger(1f, 5), 0.0001f)
        assertEquals(1f, gateStagger(0.6f, 0), 0.0001f)
        assertTrue(gateStagger(0.5f, 0) > gateStagger(0.5f, 3))
    }

    @Test
    fun rejectionShakeStartsAndEndsAtRest() {
        assertEquals(0f, gateShakeOffset(0f, 10f), 0.0001f)
        assertEquals(0f, gateShakeOffset(1f, 10f), 0.0001f)
        assertTrue(kotlin.math.abs(gateShakeOffset(1f / 12f, 10f)) > 5f)
    }

    @Test
    fun keysShrinkOnShortScreensButStayTouchable() {
        assertEquals(78f, gateKeySize(412.dp, 915.dp, landscape = false).value, 0.01f)
        assertTrue(gateKeySize(360.dp, 640.dp, landscape = false).value in 52f..78f)
        assertEquals(52f, gateKeySize(320.dp, 420.dp, landscape = false).value, 0.01f)
    }
}
