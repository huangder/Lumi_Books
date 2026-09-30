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
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.huangder.lumibooks.domain.model.MenuAnimationStyle
import com.huangder.lumibooks.ui.theme.LiquidGlassCapability
import com.huangder.lumibooks.ui.theme.LocalAppTheme
import com.huangder.lumibooks.ui.theme.LocalLiquidGlassCapability
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

    private fun show(glass: Boolean = false) {
        compose.setContent {
            view = LocalView.current
            CompositionLocalProvider(
                LocalAppTheme provides if (glass) "liquid_glass" else LocalAppTheme.current,
                LocalLiquidGlassCapability provides if (glass) LiquidGlassCapability(true, false) else LocalLiquidGlassCapability.current,
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

    private fun advance(milliseconds: Long, renderFrames: Boolean = false) {
        compose.waitForIdle()
        // Layout/draw run on Android's clock; allow each new menu to measure
        // before consuming the remaining animation frames on Compose's clock.
        var remaining = milliseconds
        while (remaining > 0) {
            val step = minOf(if (renderFrames) 16L else 32L, remaining)
            compose.mainClock.advanceTimeBy(step)
            compose.waitForIdle()
            if (renderFrames) compose.runOnIdle {
                val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
                view.draw(Canvas(bitmap))
                bitmap.recycle()
            }
            remaining -= step
        }
    }

    @Test fun selectingLiquidGlassMenuRendersTheEntireReturnWithTheNewRim() {
        style.value = MenuAnimationStyle.LIQUID
        show(glass = true)
        val glassMenu = menu.copy(
            forceSolid = false,
            items = (1..7).map { index -> LiquidGlassMenuItem("Option $index") { selections++ } }
        )
        repeat(2) { iteration ->
            compose.runOnIdle { host.show(glassMenu) }
            advance(1000, renderFrames = true)
            compose.onNodeWithText("Option 2").performClick()
            advance(1000, renderFrames = true)
            compose.runOnIdle {
                assertNull(host.displayedMenu)
                assertFalse(host.ownsDrawing(sourceId))
                assertEquals(iteration + 1, selections)
            }
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

    @Test fun categoryPanelMorphsFromItsAnchorRegardlessOfGlobalMenuStyle() {
        val panel = menu.copy(
            fadeOnlyPanel = true,
            items = listOf(LiquidGlassMenuItem("Panel action") { selections++ })
        )
        show()

        listOf(MenuAnimationStyle.NORMAL, MenuAnimationStyle.LIQUID).forEach { animationStyle ->
            style.value = animationStyle
            compose.runOnIdle { host.show(panel) }
            advance(160)
            compose.runOnIdle {
                assertTrue(host.isActive(panel))
                assertTrue("The panel should begin from its trigger in $animationStyle mode", host.ownsDrawing(sourceId))
            }
            advance(1200)
            compose.onNodeWithText("Panel action").performClick()
            advance(1200)
        }

        compose.runOnIdle {
            assertEquals(2, selections)
            assertNull(host.displayedMenu)
        }
    }

    @Test fun categoryPanelUsesFadeWhenMotionIsReduced() {
        motion.value = false
        val panel = menu.copy(
            fadeOnlyPanel = true,
            items = listOf(LiquidGlassMenuItem("Panel action") { selections++ })
        )
        show()

        for (value in MenuAnimationStyle.entries) {
            compose.runOnIdle { style.value = value; host.show(panel) }
            advance(160)
            compose.runOnIdle {
                assertTrue(host.isActive(panel))
                assertFalse(host.ownsDrawing(sourceId))
            }
            compose.onNodeWithText("Panel action").performClick()
            advance(160)
        }

        compose.runOnIdle {
            assertEquals(2, selections)
            assertNull(host.displayedMenu)
        }
    }

    @Test fun retainedMenuReflectsToggleChangesAcrossRepeatedOpens() {
        show(glass = true)
        val enabled = mutableStateOf(false)
        val toggleMenu = menu.copy(
            forceSolid = false,
            items = listOf(LiquidGlassMenuItem("Bionic reading", selectedState = { enabled.value }) {
                enabled.value = !enabled.value
            })
        )

        repeat(3) { opening ->
            compose.runOnIdle { host.show(toggleMenu) }
            advance(300)
            val item = compose.onNodeWithText("Bionic reading")
            if (opening == 1) item.assertIsSelected() else item.assertIsNotSelected()
            item.performClick()
            advance(300)
        }
        compose.runOnIdle { assertTrue(enabled.value) }
    }

    @Test fun submenuExpandsInPlaceAndBackCollapsesBeforeDismissal() {
        show(glass = true)
        val sortMenu = menu.copy(
            forceSolid = false,
            items = listOf(
                LiquidGlassMenuItem(
                    label = "Sort by", subtitle = "Current: Date added",
                    submenuItems = listOf(
                        LiquidGlassMenuItem("Date added", selected = true) { selections++ },
                        LiquidGlassMenuItem("Title A-Z") { selections++ }
                    )
                ),
                LiquidGlassMenuItem("Import") { selections++ }
            )
        )

        compose.runOnIdle { host.show(sortMenu) }
        advance(300)
        compose.onNodeWithText("Title A-Z").assertDoesNotExist()
        compose.onNodeWithText("Sort by").performClick()
        advance(300)
        compose.onNodeWithText("Date added").assertIsSelected()
        compose.runOnIdle { host.back() }
        advance(300)
        compose.runOnIdle { assertTrue(host.isActive(sortMenu)) }
        compose.onNodeWithText("Title A-Z").assertDoesNotExist()

        compose.onNodeWithText("Sort by").performClick()
        advance(300)
        compose.onNodeWithText("Title A-Z").performClick()
        advance(300)
        compose.runOnIdle {
            assertNull(host.activeMenu)
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
