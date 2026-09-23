package com.huangder.lumibooks.ui.theme

import android.app.Application
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class LumiBackgroundAnimationTest {
    @get:Rule val compose = createComposeRule()
    private val scene = mutableStateOf(LumiBackgroundScene.HOME)
    private val motion = mutableStateOf(true)
    private val viewport = mutableStateOf(1)
    private lateinit var y: State<Float>
    private val geometry = LumiMainPlacement(80f, 320f, 40f, 480f)

    private fun advance(milliseconds: Long) {
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(milliseconds)
        compose.waitForIdle()
    }

    private fun show() {
        compose.setContent { y = rememberLumiMainPosition(scene.value, geometry, motion.value, viewport.value) }
        compose.mainClock.autoAdvance = false
    }

    @Test fun navigationReversesFromTheCurrentPositionAndSettlesAt450ms() {
        show()
        assertEquals(40f, y.value, 0.01f)
        compose.runOnIdle { scene.value = LumiBackgroundScene.BOOKSHELF }
        advance(200)
        val halfway = y.value
        assertTrue("y=$halfway", halfway > 40f && halfway < 480f)
        compose.runOnIdle { scene.value = LumiBackgroundScene.HOME }
        advance(16)
        assertTrue("Reversal must keep its current position", y.value > 40f && y.value < 480f)
        advance(500)
        assertEquals(40f, y.value, 0.01f)
        compose.runOnIdle { scene.value = LumiBackgroundScene.BOOKSHELF }
        advance(500)
        assertEquals(480f, y.value, 0.01f)
    }

    @Test fun directShelfStartReducedMotionAndRotationSnapToTheCorrectPosition() {
        scene.value = LumiBackgroundScene.BOOKSHELF
        show()
        assertEquals(480f, y.value, 0.01f)
        compose.runOnIdle { motion.value = false; scene.value = LumiBackgroundScene.HOME }
        advance(32)
        assertEquals(40f, y.value, 0.01f)
        compose.runOnIdle { motion.value = true; scene.value = LumiBackgroundScene.BOOKSHELF }
        advance(160)
        assertTrue(y.value < 480f)
        compose.runOnIdle { viewport.value = 2 }
        advance(32)
        assertEquals(480f, y.value, 0.01f)
    }

    @Test fun leavingASecondaryPageFadesIntoTheCorrectMainPosition() {
        scene.value = LumiBackgroundScene.SECONDARY
        show()
        compose.runOnIdle { scene.value = LumiBackgroundScene.BOOKSHELF }
        advance(32)
        assertEquals(480f, y.value, 0.01f)
    }
}
