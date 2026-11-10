package com.huangder.lumibooks.ui.home

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Density
import com.huangder.lumibooks.ui.theme.AppColors
import com.huangder.lumibooks.ui.theme.LiquidGlassCapability
import com.huangder.lumibooks.ui.theme.LocalAppTheme
import com.huangder.lumibooks.ui.theme.LocalEInkMode
import com.huangder.lumibooks.ui.theme.LocalIsDarkTheme
import com.huangder.lumibooks.ui.theme.LocalLiquidGlassCapability
import com.huangder.lumibooks.ui.theme.LocalReduceTransparency
import com.kyant.backdrop.Backdrop
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
class ContinueReadingCardFrameTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `default cover samples background with sharp content and accessible opaque fallbacks`() {
        data class Scenario(
            val theme: String = "lumi",
            val dark: Boolean = false,
            val supported: Boolean = true,
            val eInk: Boolean = false,
            val reduceTransparency: Boolean = false,
            val hasBackdrop: Boolean = true
        )
        val scenarios = listOf(
            Scenario(), Scenario(dark = true), Scenario(theme = "liquid_glass"),
            Scenario(supported = false), Scenario(eInk = true),
            Scenario(reduceTransparency = true), Scenario(hasBackdrop = false)
        )
        val scenario = mutableStateOf(scenarios.first())
        var backdropDraws = 0
        val backdrop = object : Backdrop {
            override val isCoordinatesDependent = false
            override fun DrawScope.drawBackdrop(
                density: Density, coordinates: LayoutCoordinates?,
                layerBlock: (GraphicsLayerScope.() -> Unit)?
            ) {
                backdropDraws++
                drawRect(Color.Blue)
            }
        }
        lateinit var view: View
        var fallbackColor = 0
        compose.setContent {
            view = LocalView.current
            val s = scenario.value
            CompositionLocalProvider(
                LocalAppTheme provides s.theme,
                LocalIsDarkTheme provides s.dark,
                LocalLiquidGlassCapability provides LiquidGlassCapability(s.supported, false),
                LocalEInkMode provides s.eInk,
                LocalReduceTransparency provides s.reduceTransparency
            ) {
                fallbackColor = AppColors.CardBg.toArgb()
                Box(Modifier.size(320.dp, 180.dp).background(Color.Red), contentAlignment = Alignment.Center) {
                    ContinueReadingCardFrame(
                        onClick = {}, frostedBackdrop = backdrop.takeIf { s.hasBackdrop },
                        modifier = Modifier.padding(24.dp).height(120.dp).testTag("card")
                    ) {
                        Box(Modifier.align(Alignment.Center).size(24.dp).background(Color.Green).testTag("label"))
                    }
                }
            }
        }
        for (s in scenarios) {
            compose.runOnIdle { scenario.value = s }
            val card = compose.onNodeWithTag("card").fetchSemanticsNode().boundsInRoot
            val label = compose.onNodeWithTag("label", true).fetchSemanticsNode().boundsInRoot
            val frosted = s.supported && !s.eInk && !s.reduceTransparency && s.hasBackdrop
            compose.runOnIdle {
                backdropDraws = 0
                val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
                try {
                    view.draw(Canvas(bitmap))
                    assertEquals("Foreground stays sharp: $s", Color.Green.toArgb(),
                        bitmap.getPixel(label.center.x.toInt(), label.center.y.toInt()))
                    if (frosted) {
                        assertTrue("Default cover must sample its background: $s", backdropDraws > 0)
                    } else {
                        assertEquals("Fallback must not sample a blur layer: $s", 0, backdropDraws)
                        assertEquals("Fallback must be opaque: $s", fallbackColor,
                            bitmap.getPixel((card.left + card.width * .25f).toInt(), card.center.y.toInt()))
                    }
                } finally { bitmap.recycle() }
            }
        }
    }

    @Test fun `press shrinks cover surface and rounded mask together without exposing white`() {
        val theme = mutableStateOf("lumi")
        lateinit var view: View
        var clicks = 0
        compose.setContent {
            view = LocalView.current
            CompositionLocalProvider(LocalAppTheme provides theme.value) {
                Box(Modifier.size(360.dp, 180.dp).background(Color(0xFF304050)).testTag("scene"),
                    contentAlignment = Alignment.Center) {
                    ContinueReadingCardFrame(onClick = { clicks++ },
                        modifier = Modifier.padding(24.dp).height(120.dp).testTag("card")) {
                        Box(Modifier.fillMaxSize().background(Color(0xFF15202B)).testTag("cover"))
                    }
                }
            }
        }
        compose.mainClock.autoAdvance = false
        for (value in listOf("lumi", "liquid_glass")) {
            compose.runOnIdle { theme.value = value }
            val normalWidth = compose.onNodeWithTag("cover", true).fetchSemanticsNode().boundsInRoot.width
            compose.onNodeWithTag("card").performTouchInput { down(center) }
            compose.mainClock.advanceTimeBy(250)
            val pressedWidth = compose.onNodeWithTag("cover", true).fetchSemanticsNode().boundsInRoot.width
            assertEquals(normalWidth * .96f, pressedWidth, .5f)
            val bounds = compose.onNodeWithTag("scene").fetchSemanticsNode().boundsInRoot
            compose.runOnIdle {
                val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
                view.draw(Canvas(bitmap))
                var whitePixels = 0
                for (y in bounds.top.toInt() until bounds.bottom.toInt()) {
                    for (x in bounds.left.toInt() until bounds.right.toInt()) {
                        val p = bitmap.getPixel(x, y)
                        if (android.graphics.Color.red(p) > 220 && android.graphics.Color.green(p) > 220 &&
                            android.graphics.Color.blue(p) > 220) whitePixels++
                    }
                }
                bitmap.recycle()
                assertEquals("No exposed white base while pressed in $value", 0, whitePixels)
            }
            compose.onNodeWithTag("card").performTouchInput { cancel() }
            compose.mainClock.advanceTimeBy(250)
            assertEquals(0, clicks)
        }
        compose.onNodeWithTag("card").performTouchInput { down(center); up() }
        compose.mainClock.advanceTimeBy(250)
        assertEquals(1, clicks)
    }
}
