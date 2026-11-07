package com.huangder.lumibooks.ui.components

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.roundToInt

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class LiquidGlassTabMotionStateTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var state: LiquidGlassTabMotionState
    private val visible = mutableStateOf(true)

    private fun setup(motion: Boolean = true) {
        compose.setContent {
            val scope = rememberCoroutineScope()
            state = remember { LiquidGlassTabMotionState(scope, 0f, 0f..2f, motion) }
            if (visible.value) Box(Modifier.size(240.dp, 64.dp).testTag("prism")
                .liquidGlassTabDrag(state,
                    onDrag = { size, offset -> state.updateValue(state.targetValue + offset.x / size.width * 2f) },
                    onDragEnd = { state.animateToValue(state.targetValue.roundToInt().toFloat()) },
                    onDragCancel = { state.animateToValue(state.targetValue.roundToInt().toFloat()) }))
        }
        compose.mainClock.autoAdvance = false
    }

    private fun advance(ms: Long) {
        var remaining = ms
        while (remaining > 0) {
            val step = minOf(16L, remaining)
            compose.mainClock.advanceTimeBy(step)
            compose.waitForIdle()
            remaining -= step
        }
    }

    private fun assertResting() = compose.runOnIdle {
        assertEquals("Press feedback must release", 0f, state.pressProgress, .001f)
        assertEquals("Horizontal scale must recover", 1f, state.scaleX, .001f)
        assertEquals("Vertical scale must recover", 1f, state.scaleY, .001f)
        assertEquals("Velocity-based squashing must end", 0f, state.velocity, 0f)
    }

    @Test fun previousReleaseCannotFlattenANewHeldGesture() {
        setup()
        compose.runOnIdle { state.press(); state.updateValue(1.7f); state.release() }
        advance(16)
        compose.runOnIdle { state.press() }
        advance(1200)
        compose.runOnIdle {
            assertEquals("An old release must not reset the next press", 1f, state.pressProgress, .001f)
            assertTrue(state.scaleX > 1.3f && state.scaleY > 1.3f)
            state.release()
        }
        advance(1600)
        assertResting()
    }

    @Test fun releaseClearsResidualDragVelocity() {
        setup()
        compose.runOnIdle { state.press() }
        repeat(8) { i ->
            compose.runOnIdle { state.updateValue((i % 3) * .8f) }
            advance(32)
        }
        compose.runOnIdle { state.release() }
        advance(2000)
        assertResting()
    }

    @Test fun rapidDragsAndProgrammaticSelectionsAlwaysRecover() {
        setup()
        repeat(16) { i ->
            compose.runOnIdle { state.press(); state.updateValue(if (i % 2 == 0) 1.8f else .2f) }
            advance(16)
            compose.runOnIdle { state.animateToValue(if (i % 2 == 0) 2f else 0f); state.release() }
            advance(if (i % 3 == 0) 16 else 48)
        }
        advance(2000)
        assertResting()
        compose.runOnIdle { assertEquals(0f, state.value, .001f) }
    }

    @Test fun removingPointerInputDuringDragReleasesTheStillRememberedState() {
        setup()
        compose.onNodeWithTag("prism").performTouchInput { down(center); moveBy(Offset(30f, 0f)) }
        advance(80)
        compose.runOnIdle { visible.value = false }
        advance(2000)
        assertResting()
    }

    @Test fun cancelledGestureAndReducedMotionCannotLeaveDeformation() {
        setup(motion = false)
        compose.onNodeWithTag("prism").performTouchInput { down(center); moveBy(Offset(40f, 0f)) }
        advance(32)
        compose.runOnIdle { assertEquals(1f, state.scaleX, .001f); assertEquals(1f, state.scaleY, .001f); assertEquals(0f, state.velocity, 0f) }
        compose.onNodeWithTag("prism").performTouchInput { cancel() }
        advance(2000)
        assertResting()
    }
    @Test fun programmaticSelectionReleasesWithoutAPointerUpEvent() {
        setup()
        compose.runOnIdle { state.animateToValue(2f) }
        advance(2000)
        assertResting()
        compose.runOnIdle { assertEquals(2f, state.value, .001f) }
    }

    @Test fun stoppedDragRelaxesVelocityWhileTheFingerRemainsDown() {
        setup()
        compose.runOnIdle { state.press(); state.updateValue(1.7f) }
        advance(1800)
        compose.runOnIdle {
            assertEquals(1f, state.pressProgress, .001f)
            assertEquals(0f, state.velocity, 0f)
            state.release()
        }
        advance(1600)
        assertResting()
    }

    @Test fun repressWithoutAnotherMoveCannotFreezeThePreviousVelocity() {
        setup()
        compose.runOnIdle { state.press(); state.updateValue(1.8f) }
        advance(80)
        compose.runOnIdle { state.press() }
        advance(1800)
        compose.runOnIdle {
            assertEquals(1f, state.pressProgress, .001f)
            assertEquals(0f, state.velocity, 0f)
            state.release()
        }
        advance(1600)
        assertResting()
    }

    @Test fun continuousDragSamplesKeepThePrismMovingBeforeRelease() {
        setup()
        compose.runOnIdle { state.press() }
        repeat(20) { i ->
            compose.runOnIdle { state.updateValue((i + 1) * .08f) }
            advance(16)
        }
        compose.runOnIdle {
            assertEquals(1.6f, state.targetValue, .001f)
            assertTrue("Continuous input must keep following rather than restarting from rest", state.value > .9f)
            state.release()
        }
        advance(1600)
        assertResting()
    }

}
