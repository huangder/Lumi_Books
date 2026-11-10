package com.huangder.lumibooks.ui.components

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.huangder.lumibooks.ui.theme.LiquidGlassCapability
import com.huangder.lumibooks.ui.theme.LocalAppTheme
import com.huangder.lumibooks.ui.theme.LocalLiquidGlassCapability
import com.huangder.lumibooks.ui.theme.LocalMotionEnabled
import com.huangder.lumibooks.ui.animation.LocalPageScene
import com.huangder.lumibooks.ui.animation.PageScene
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Robolectric
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class LiquidGlassButtonSurfaceTest {
    @get:Rule val compose = createComposeRule()
    private val motion = mutableStateOf(true)
    private val enabled = mutableStateOf(true)
    private val feedback = mutableStateOf(true)
    private var clicks = 0
    private var pageScene: PageScene? = null

    private fun show() {
        compose.setContent {
            CompositionLocalProvider(LocalAppTheme provides "liquid_glass",
                LocalPageScene provides pageScene,
                LocalLiquidGlassCapability provides LiquidGlassCapability(true, false),
                LocalMotionEnabled provides motion.value) {
                // Custom paths must get motion even when the lens cannot accept their shape.
                LiquidGlassSurface(G2ContinuousCornerShape(20f), Color.Black,
                    modifier = Modifier.size(48.dp).testTag("button"), enabled = enabled.value,
                    pressFeedbackEnabled = feedback.value, onClick = { clicks++ }) {
                    Box(Modifier.size(16.dp).testTag("content"))
                }
            }
        }
        compose.mainClock.autoAdvance = false
    }

    private fun width() = compose.onNodeWithTag("content", useUnmergedTree = true)
        .fetchSemanticsNode().boundsInRoot.width

    @Test fun customShapeExpandsContentWithoutTheOldOuterShrink() {
        show()
        val initial = width()
        compose.onNodeWithTag("button").performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(400)
        compose.waitForIdle()
        assertEquals(initial * 1.088f, width(), initial * .01f)
        compose.onNodeWithTag("button").performTouchInput { up() }
        compose.mainClock.advanceTimeBy(1000)
        compose.waitForIdle()
        assertEquals(initial, width(), .1f)
        compose.runOnIdle { assertEquals(1, clicks) }
    }

    @Test fun optOutDisabledAndReducedMotionDoNotDeform() {
        feedback.value = false
        show()
        val initial = width()
        repeat(3) { phase ->
            compose.runOnIdle {
                feedback.value = phase != 0
                enabled.value = phase != 1
                motion.value = phase != 2
            }
            compose.onNodeWithTag("button").performTouchInput { down(center) }
            compose.mainClock.advanceTimeBy(400)
            compose.waitForIdle()
            assertEquals(initial, width(), .1f)
            compose.onNodeWithTag("button").performTouchInput { up() }
            compose.mainClock.advanceTimeBy(500)
        }
        compose.runOnIdle { assertEquals(2, clicks) }
    }

    @Test fun pageNavigationClearsThePressedAppearanceBeforeTheToolbarIsCaptured() {
        val scene = PageScene(Robolectric.buildActivity(ComponentActivity::class.java).create().get(), false)
        pageScene = scene
        show()
        val initial = width()
        compose.onNodeWithTag("button").performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(400)
        compose.waitForIdle()
        assertTrue(width() > initial)
        compose.onNodeWithTag("button").performTouchInput { up() }
        compose.runOnIdle {
            assertEquals(1, clicks)
            scene.coveredBy = PageScene(scene.activity, true).apply { moving = true }
        }
        compose.mainClock.advanceTimeBy(48)
        compose.waitForIdle()
        assertEquals("Navigation snapshots use the unpressed control", initial, width(), .1f)
        compose.runOnIdle { scene.coveredBy = null }
        compose.mainClock.advanceTimeBy(48)
        compose.waitForIdle()
        assertEquals("Returning must not restore the old press", initial, width(), .1f)
    }
}
