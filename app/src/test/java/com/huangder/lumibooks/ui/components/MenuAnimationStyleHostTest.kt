package com.huangder.lumibooks.ui.components

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.huangder.lumibooks.domain.model.MenuAnimationStyle
import com.huangder.lumibooks.ui.theme.LocalMenuAnimationStyle
import com.huangder.lumibooks.ui.theme.LocalMotionEnabled
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MenuAnimationStyleHostTest {
    @get:Rule val compose = createComposeRule()
    private val style = mutableStateOf(MenuAnimationStyle.NORMAL)
    private val motion = mutableStateOf(true)
    private val sourceId = Any()
    private lateinit var host: LiquidGlassMenuHostState
    private lateinit var view: View
    private var selections = 0
    private val menu = LiquidGlassMenuSpec(
        Rect.Zero, 200.dp,
        listOf(LiquidGlassMenuItem("Choose this") { selections++ }),
        sourceId = sourceId,
        forceSolid = true
    )

    private fun show() {
        compose.setContent {
            view = LocalView.current
            CompositionLocalProvider(
                LocalMenuAnimationStyle provides style.value,
                LocalMotionEnabled provides motion.value
            ) {
                LiquidGlassMenuHost(Modifier.fillMaxSize(), backdrop = null) {
                    host = LocalLiquidGlassMenuHost.current!!
                    Box(Modifier.align(Alignment.TopEnd).padding(24.dp)
                        .liquidGlassMenuAnchor(sourceId).size(44.dp).background(Color.Gray))
                }
            }
        }
        // Force the initial source draw before testing its handoff to the overlay.
        compose.runOnIdle {
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            bitmap.recycle()
            assertTrue("The fixture must draw its source before opening", host.source(menu)!!.recorded)
        }
        compose.mainClock.autoAdvance = false
    }

    private fun advance(milliseconds: Long) {
        compose.waitForIdle()
        // Layout/draw run on Android's clock; allow each new menu to measure
        // before consuming the remaining animation frames on Compose's clock.
        var remaining = milliseconds
        while (remaining > 0) {
            val step = minOf(32L, remaining)
            compose.mainClock.advanceTimeBy(step)
            compose.waitForIdle()
            remaining -= step
        }
    }

    @Test fun normalMenuKeepsTheTriggerVisibleAndSubmitsSelectionOnlyOnce() {
        show()
        compose.runOnIdle { host.show(menu) }
        advance(240)
        compose.runOnIdle {
            assertNotNull(host.displayedMenu)
            assertTrue(host.measured)
            assertFalse(host.ownsDrawing(sourceId))
        }
        compose.onNodeWithText("Choose this").performClick()
        compose.runOnIdle {
            assertEquals(1, selections)
            assertFalse(host.select(menu) { selections++ })
        }
        advance(240)
        compose.runOnIdle {
            assertNull(host.displayedMenu)
            assertFalse(host.ownsDrawing(sourceId))
            assertEquals(1, selections)
        }
    }

    @Test fun changingStyleDuringCloseRemeasuresTheSameSourceAndCanReopen() {
        show()
        compose.runOnIdle { host.show(menu) }
        advance(240)
        compose.runOnIdle { host.dismiss(); style.value = MenuAnimationStyle.LIQUID }
        advance(32)
        compose.runOnIdle { host.show(menu) }
        advance(1400)
        compose.runOnIdle {
            assertTrue(host.isActive(menu))
            assertTrue(host.measured)
            assertTrue("Liquid mode must take the recorded source; recorded=${host.source(menu)?.recorded}",
                host.ownsDrawing(sourceId))
        }
        compose.runOnIdle { host.dismiss(); style.value = MenuAnimationStyle.NORMAL }
        advance(32)
        compose.runOnIdle { host.show(menu) }
        advance(1400)
        compose.runOnIdle {
            assertTrue(host.isActive(menu))
            assertTrue(host.measured)
            assertFalse(host.ownsDrawing(sourceId))
        }
        compose.onNodeWithText("Choose this").performClick()
        advance(240)
        compose.runOnIdle { assertNull(host.displayedMenu); assertEquals(1, selections) }
    }

    @Test fun reducedMotionNeverTakesTheTriggerDrawingForEitherStyle() {
        motion.value = false
        show()
        for (value in MenuAnimationStyle.entries) {
            compose.runOnIdle { style.value = value; host.show(menu) }
            advance(200)
            compose.runOnIdle {
                assertTrue(host.isActive(menu))
                assertFalse(host.ownsDrawing(sourceId))
            }
            compose.runOnIdle { host.dismiss() }
            advance(200)
            compose.runOnIdle { assertNull(host.displayedMenu) }
        }
    }
}
