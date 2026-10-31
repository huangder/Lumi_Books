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
import androidx.compose.runtime.Composer
import androidx.compose.runtime.CompositionTracer
import androidx.compose.runtime.InternalComposeTracingApi
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
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
                        .liquidGlassMenuAnchor(sourceId).testTag("menuTrigger")
                        .size(44.dp).background(Color.Gray))
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

    private fun triggerPixel(): Int {
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        return try {
            view.draw(Canvas(bitmap))
            val center = host.source(menu)!!.rootBounds.center
            bitmap.getPixel(center.x.toInt(), center.y.toInt())
        } finally {
            bitmap.recycle()
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

    @Test fun normalMenuKeepsTheTriggerAndSubmitsSelectionOnlyOnce() {
        show()
        compose.onNodeWithTag("menuTrigger").assertExists()
        compose.runOnIdle { assertEquals(Color.Gray.toArgb(), triggerPixel()) }
        compose.runOnIdle { host.show(menu) }
        advance(240)
        compose.runOnIdle {
            assertNotNull(host.displayedMenu)
            assertTrue(host.measured)
            assertFalse(host.ownsDrawing(sourceId))
            // A normal popup does not take ownership of the source drawing layer.
            assertFalse(host.hidesSource(sourceId))
            assertEquals("The normal popup keeps its source visible", Color.Gray.toArgb(), triggerPixel())
        }
        compose.onNodeWithTag("menuTrigger").assertExists()
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
        compose.onNodeWithTag("menuTrigger").assertExists()
        compose.runOnIdle { assertEquals(Color.Gray.toArgb(), triggerPixel()) }
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

    @Test fun categoryViewportRemainsSettledWhileItsContainerTravels() {
        val measurements = mutableSetOf<IntSize>()
        show()
        compose.runOnIdle {
            host.show(menu.copy(fadeOnlyPanel = true, content = { _, _ ->
                Box(Modifier.fillMaxSize().onSizeChanged { measurements += it })
            }))
        }
        for (opening in listOf(true, false)) {
            repeat(10) {
                advance(80, renderFrames = true)
                compose.runOnIdle {
                    assertEquals("Content must have exactly one settled viewport", 1, measurements.size)
                    val measured = measurements.first()
                    assertEquals(view.width, measured.width)
                    assertEquals(view.height * 0.85, measured.height.toDouble(), 1.0)
                }
            }
            if (opening) compose.runOnIdle { host.dismiss() }
        }
        compose.runOnIdle { assertNull(host.displayedMenu) }
    }

    @Test fun ordinaryThemeDoesNotRecomposeMorphSurfacesEveryFrame() = assertBoundedMorphCompositions(false)

    @Test fun glassThemeDoesNotRecomposeMorphSurfacesEveryFrame() = assertBoundedMorphCompositions(true)

    @OptIn(InternalComposeTracingApi::class)
    private fun assertBoundedMorphCompositions(glass: Boolean) {
        style.value = MenuAnimationStyle.LIQUID
        val visits = mutableMapOf<String, Int>()
        val watched = setOf("AnchoredLiquidGlassMenu", "MenuFallbackSurface", "LiquidGlassSurface", "LiquidMenuSurface")
        val prefix = "com.huangder.lumibooks.ui.components."
        val tracer = object : CompositionTracer {
            override fun isTraceInProgress(): Boolean = true
            override fun traceEventStart(key: Int, dirty1: Int, dirty2: Int, info: String) {
                val name = info.removePrefix(prefix).substringBefore(' ')
                if (info.startsWith(prefix) && name in watched) visits[name] = visits.getOrDefault(name, 0) + 1
            }
            override fun traceEventEnd() {}
        }
        Composer.setTracer(tracer)
        try {
            show(glass)
            listOf(false, true).forEach { panel ->
                compose.runOnIdle {
                    visits.clear()
                    host.show(menu.copy(forceSolid = panel, fadeOnlyPanel = panel))
                }
                advance(1200, renderFrames = true)
                compose.runOnIdle { host.dismiss() }
                advance(1200, renderFrames = true)
                compose.runOnIdle {
                    val expected = when {
                        glass && !panel -> setOf("AnchoredLiquidGlassMenu", "LiquidMenuSurface")
                        glass -> setOf("AnchoredLiquidGlassMenu", "MenuFallbackSurface")
                        else -> setOf("AnchoredLiquidGlassMenu", "MenuFallbackSurface", "LiquidGlassSurface")
                    }
                    expected.forEach { name ->
                        val count = visits.getOrDefault(name, 0)
                        assertTrue("Missing tracer observation for $name (glass=$glass panel=$panel)", count > 0)
                        assertTrue("$name recomposed during morph (glass=$glass panel=$panel): $count", count <= 12)
                    }
                    assertNull(host.displayedMenu)
                }
            }
        } finally {
            Composer.setTracer(null)
        }
    }

    @Test fun surfaceBlurBoostIsAddedOnceAndSuppressedByReducedTransparency() {
        val requested = LiquidGlassMenuMorph.blurBoostDp(0.3f) * 2f
        assertTrue(requested > 0f)
        for (reduced in listOf(false, true)) {
            val boost = menuSurfaceBlurBoostPx(reduced, requested)
            assertEquals(if (reduced) 0f else requested, boost, 0f)
            for (supported in listOf(false, true)) {
                val drawnBlur = mutableListOf<Float>()
                GlassRefractionState().render(supported, 3f + boost, 12f + boost,
                    resetEffects = {}, blur = { drawnBlur += it }, refract = {}, onFailure = { throw it })
                assertEquals(listOf((if (supported) 3f else 12f) + boost), drawnBlur)
            }
        }
        assertEquals(0f, menuSurfaceBlurBoostPx(false, -1f), 0f)
        assertEquals(0f, menuSurfaceBlurBoostPx(false, LiquidGlassMenuMorph.blurBoostDp(1f)), 0f)
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

    @Test fun reducedMotionKeepsTheTriggerVisibleForEitherStyle() {
        motion.value = false
        show()
        for (value in MenuAnimationStyle.entries) {
            compose.onNodeWithTag("menuTrigger").assertExists()
            compose.runOnIdle { style.value = value; host.show(menu) }
            advance(200)
            compose.runOnIdle {
                assertTrue(host.isActive(menu))
                assertFalse(host.ownsDrawing(sourceId))
                assertFalse(host.hidesSource(sourceId))
            }
            compose.onNodeWithTag("menuTrigger").assertExists()
            compose.runOnIdle { host.dismiss() }
            advance(32)
            compose.onNodeWithTag("menuTrigger").assertExists()
            advance(200)
            compose.runOnIdle {
                assertNull(host.displayedMenu)
                assertFalse(host.hidesSource(sourceId))
            }
            compose.onNodeWithTag("menuTrigger").assertExists()
        }
    }
}
