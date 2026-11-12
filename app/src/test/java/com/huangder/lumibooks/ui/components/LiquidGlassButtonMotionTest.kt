package com.huangder.lumibooks.ui.components

import android.app.Application
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class LiquidGlassButtonMotionTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var state: LiquidGlassButtonInteractionState

    private fun setup() {
        compose.setContent {
            val scope = rememberCoroutineScope()
            state = remember(scope) { LiquidGlassButtonInteractionState(scope) }
        }
        compose.mainClock.autoAdvance = false
    }

    private fun advance(ms: Long) {
        compose.mainClock.advanceTimeBy(ms)
        compose.waitForIdle()
    }

    @Test fun repressDuringReturnKeepsCurrentDisplacementAndHighlightUsesNewFinger() {
        setup()
        compose.runOnIdle { state.press(Offset(20f, 20f), 0) }
        advance(250)
        compose.runOnIdle { state.dragTo(Offset(45f, 30f), 100) }
        advance(16)
        compose.runOnIdle { state.release(cancelled = true) }
        advance(48)
        compose.runOnIdle {
            val before = state.offset
            assertTrue(before.getDistance() > 1f)
            state.press(Offset(4f, 5f), 200)
            assertEquals(before.x, state.offset.x, .001f)
            assertEquals(before.y, state.offset.y, .001f)
            assertEquals(Offset(4f, 5f), state.position)
        }
        advance(16)
        compose.runOnIdle { state.release(cancelled = true) }
        advance(1200)
        compose.runOnIdle {
            assertEquals(0f, state.offset.getDistance(), .001f)
            assertEquals(0f, state.pressProgress, .001f)
        }
    }

    @Test fun releaseRingsLikeJellyThenSettlesWithClampedPressProgress() {
        setup()
        compose.runOnIdle { state.press(Offset(10f, 10f), 0) }
        advance(400)
        compose.runOnIdle { state.release() }
        var minScale = Float.MAX_VALUE
        var minWobble = 0f
        var maxWobble = 0f
        repeat(60) {
            advance(16)
            compose.runOnIdle {
                assertTrue(state.pressProgress in 0f..1f)
                minScale = minOf(minScale, state.scaleProgress)
                minWobble = minOf(minWobble, state.wobble)
                maxWobble = maxOf(maxWobble, state.wobble)
            }
        }
        assertTrue("Release dips below rest before settling", minScale < -0.01f)
        assertTrue("Wobble swings both ways", minWobble < -0.005f && maxWobble > 0.002f)
        advance(2000)
        compose.runOnIdle {
            assertEquals(0f, state.wobble, .001f)
            assertEquals(0f, state.scaleProgress, .001f)
        }
    }

    @Test fun releaseContinuesFlingVelocityAndResetStopsAllMotion() {
        setup()
        compose.runOnIdle { state.press(Offset.Zero, 0) }
        advance(16)
        for (i in 1..5) {
            compose.runOnIdle { state.dragTo(Offset(i * 8f, 0f), i * 16L) }
            advance(16)
        }
        var before = 0f
        compose.runOnIdle { before = state.offset.x; state.release() }
        advance(32)
        compose.runOnIdle {
            assertTrue("A moving release carries forward velocity before returning", state.offset.x > before)
            state.reset()
        }
        advance(32)
        compose.runOnIdle {
            assertEquals(0f, state.offset.getDistance(), .001f)
            assertEquals(0f, state.pressProgress, .001f)
        }
        advance(500)
        compose.runOnIdle { assertEquals(0f, state.offset.getDistance(), .001f) }
    }
}
