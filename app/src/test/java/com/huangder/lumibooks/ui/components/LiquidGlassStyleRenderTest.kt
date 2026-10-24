package com.huangder.lumibooks.ui.components

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.huangder.lumibooks.domain.model.LiquidGlassStyle
import com.huangder.lumibooks.ui.theme.*
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LiquidGlassStyleRenderTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var view: View

    private fun capture(tag: String): Bitmap {
        val bounds = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
        lateinit var result: Bitmap
        compose.runOnIdle {
            val full = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(full))
            result = Bitmap.createBitmap(full, bounds.left.toInt(), bounds.top.toInt(), bounds.width.toInt(), bounds.height.toInt())
            full.recycle()
        }
        return result
    }

    // Android 12 exercises the original native highlight without a hardware-only shader.
    @Test @Config(sdk = [31])
    fun liveStyleSwitchReachesNestedThemesButtonsCardsMenusAndTabs() = verifyLiveStyles(false)

    // Modern glass uses continuous outlines; its original fine border can also be captured locally.
    @Test fun continuousOutlinesSwitchOnModernAndroid() = verifyLiveStyles(true)

    private fun verifyLiveStyles(continuous: Boolean) {
        val style = mutableStateOf(LiquidGlassStyle.DEFAULT)
        val dark = mutableStateOf(false)
        val hasBackdrop = mutableStateOf(false)
        compose.setContent {
            view = LocalView.current
            EBookReaderTheme(appTheme = "liquid_glass", liquidGlassStyle = style.value, darkTheme = dark.value) {
                // Reader routes wrap the app theme again without an explicit style.
                EBookReaderTheme(appTheme = "liquid_glass", darkTheme = dark.value) {
                    assertEquals(style.value, LocalLiquidGlassStyle.current)
                    assertEquals(style.value == LiquidGlassStyle.DEFAULT, LocalLiquidGlassControlEdgeEnabled.current)
                    val backdrop = rememberLayerBackdrop()
                    Box(Modifier.fillMaxSize().layerBackdrop(backdrop).background(if (dark.value) Color.DarkGray else Color.LightGray))
                    CompositionLocalProvider(
                        LocalAppTheme provides "liquid_glass",
                        LocalLiquidGlassCapability provides LiquidGlassCapability(true, false),
                        LocalLiquidGlassBackdrop provides backdrop.takeIf { hasBackdrop.value },
                        LocalLumiBackgroundBackdrop provides null
                    ) {
                        val color = if (dark.value) Color(0xFF202023) else Color(0xFFF0EFF4)
                        val buttonShape = if (continuous) AppRoundedCornerShape(50) else CircleShape
                        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            LiquidGlassSurface(buttonShape, color, Modifier.size(150.dp, 48.dp).testTag("button"),
                                interactive = false, controlEdge = true, onClick = {}) {}
                            CompositionLocalProvider(LocalLumiBackgroundBackdrop provides backdrop) {
                                Box(Modifier.size(150.dp, 48.dp).testTag("input").lumiCardSurface(color, CircleShape, controlEdge = true))
                            }
                            val shape = AppRoundedCornerShape(20.dp)
                            LiquidMenuSurface(
                                frame = { error("Software fixture uses no optical backdrop") }, shape = { shape },
                                backdrop = null, baseColor = color, isDark = dark.value, transparency = 0.55f,
                                modifier = Modifier.size(150.dp, 60.dp).testTag("menu")
                            )
                            FloatingTabBar(0, {}, Modifier.width(300.dp).testTag("tabs"))
                        }
                    }
                }
            }
        }
        val reports = File("build/reports/liquid-glass-style").apply { mkdirs() }
        for (isDark in listOf(false, true)) for (backdrop in listOf(false, true)) {
            compose.runOnIdle { dark.value = isDark; hasBackdrop.value = backdrop; style.value = LiquidGlassStyle.DEFAULT }
            val current = listOf("button", "input", "menu", "tabs").associateWith { capture(it) }
            compose.runOnIdle { style.value = LiquidGlassStyle.ORIGINAL }
            current.forEach { (tag, bitmap) ->
                val original = capture(tag)
                assertFalse("Style must change $tag rim (dark=$isDark backdrop=$backdrop)", bitmap.sameAs(original))
                for ((name, image) in listOf("default" to bitmap, "original" to original)) {
                    File(reports, "$tag-$name-dark-$isDark-backdrop-$backdrop.png").outputStream().use {
                        image.compress(Bitmap.CompressFormat.PNG, 100, it)
                    }
                }
                original.recycle()
            }
            compose.runOnIdle { style.value = LiquidGlassStyle.DEFAULT }
            current.forEach { (tag, bitmap) ->
                val restored = capture(tag)
                assertTrue("Switching back must restore $tag", bitmap.sameAs(restored))
                restored.recycle(); bitmap.recycle()
            }
        }
    }

    @Test fun originalOutlineKeepsPressExpansionWithoutBackdrop() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            view = LocalView.current
            EBookReaderTheme(appTheme = "liquid_glass", liquidGlassStyle = LiquidGlassStyle.ORIGINAL) {
                CompositionLocalProvider(
                    LocalAppTheme provides "liquid_glass",
                    LocalLiquidGlassCapability provides LiquidGlassCapability(true, false),
                    LocalLiquidGlassBackdrop provides null,
                    LocalLumiBackgroundBackdrop provides null
                ) {
                    Box(Modifier.size(230.dp, 128.dp).testTag("fixture"), contentAlignment = Alignment.Center) {
                        LiquidGlassSurface(CircleShape, Color.LightGray, Modifier.size(150.dp, 48.dp).testTag("button"),
                            controlEdge = true, onClick = {}) {}
                    }
                }
            }
        }
        compose.mainClock.advanceTimeBy(32)
        val before = capture("fixture")
        compose.onNodeWithTag("button").performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(160)
        val pressed = capture("fixture")
        fun paintedWidth(bitmap: Bitmap): Int {
            val y = bitmap.height / 2
            val painted = (0 until bitmap.width).filter { android.graphics.Color.alpha(bitmap.getPixel(it, y)) > 128 }
            return painted.last() - painted.first() + 1
        }
        assertTrue("Original style must retain surface expansion without a backdrop",
            paintedWidth(pressed) > paintedWidth(before) + 4)
        compose.onNodeWithTag("button").performTouchInput { up() }
        compose.mainClock.advanceTimeBy(2000)
        before.recycle(); pressed.recycle()
    }
}
