package com.huangder.lumibooks.ui.components

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.huangder.lumibooks.ui.theme.AppRoundedCornerShape
import com.huangder.lumibooks.ui.theme.LiquidGlassCapability
import com.huangder.lumibooks.ui.theme.LocalAppTheme
import com.huangder.lumibooks.ui.theme.LocalIsDarkTheme
import com.huangder.lumibooks.ui.theme.LocalLiquidGlassCapability
import com.huangder.lumibooks.ui.theme.LocalLiquidGlassTransparency
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "zh-rCN-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LiquidGlassStaticSurfaceRenderTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var view: View

    /** Hardware elevation artifacts are checked on-device with the debug fixture.
     * Locally compare the complete default render to an explicit undecorated surface. */
    // API 31 keeps the original circular highlight compatible with software screenshots.
    @Test @Config(sdk = [31]) fun defaultGlassMatchesNoShadowAcrossShapesThemesAndBackdrops() {
        val style = mutableStateOf(true)
        val dark = mutableStateOf(false)
        val hasBackdrop = mutableStateOf(false)
        val circle = mutableStateOf(false)
        val useDefaultDecoration = mutableStateOf(false)
        compose.setContent {
            view = LocalView.current
            val backdrop = rememberLayerBackdrop()
            Box(Modifier.fillMaxSize().layerBackdrop(backdrop).background(
                if (dark.value) Color(0xFF202023) else Color(0xFFF0EFF4)
            ))
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CompositionLocalProvider(
                    LocalLiquidGlassControlEdgeEnabled provides style.value,
                    LocalAppTheme provides "liquid_glass",
                    LocalIsDarkTheme provides dark.value,
                    LocalLiquidGlassCapability provides LiquidGlassCapability(true, false),
                    LocalLiquidGlassTransparency provides 0.55f,
                    LocalLiquidGlassBackdrop provides backdrop.takeIf { hasBackdrop.value }
                ) {
                    val shape = if (circle.value) CircleShape else AppRoundedCornerShape(28.dp)
                    val decoration = if (useDefaultDecoration.value) null else Modifier
                    val size = Modifier.size(if (circle.value) 72.dp else 150.dp, 72.dp)
                    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
                        for (prominent in listOf(false, true)) {
                            LiquidGlassButton(
                                onClick = {}, shape = shape, modifier = size,
                                decorationModifier = decoration,
                                prominentShadow = prominent,
                                contentColor = if (dark.value) Color.White else Color.Black
                            ) { Text("Lumi") }
                        }
                        LiquidGlassSurface(
                            shape = shape,
                            fallbackColor = if (dark.value) Color(0xFF202023) else Color.White,
                            modifier = size, decorationModifier = decoration,
                            controlEdge = true, onClick = {}
                        ) { Text("+") }
                    }
                }
            }
        }

        fun screenshot(): Bitmap {
            lateinit var result: Bitmap
            compose.runOnIdle {
                result = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
                view.draw(Canvas(result))
            }
            return result
        }
        val reports = File("build/reports/liquid-glass-static").apply { mkdirs() }
        for (useNewEdge in listOf(false, true)) for (isCircle in listOf(false, true)) for (isDark in listOf(false, true)) {
            for (backdrop in listOf(false, true)) {
                compose.runOnIdle {
                    style.value = useNewEdge
                    circle.value = isCircle
                    dark.value = isDark
                    hasBackdrop.value = backdrop
                    useDefaultDecoration.value = false
                }
                val baseline = screenshot()
                compose.runOnIdle { useDefaultDecoration.value = true }
                val rendered = screenshot()
                val scenario = "new-$useNewEdge-circle-$isCircle-dark-$isDark-backdrop-$backdrop"
                assertTrue("Default changed the glass, outline or background ($scenario)",
                    baseline.sameAs(rendered))
                listOf(2 to 2, rendered.width - 3 to rendered.height - 3).forEach { (x, y) ->
                    assertEquals("Shadow changed distant background ($scenario)",
                        baseline.getPixel(x, y), rendered.getPixel(x, y))
                }
                File(reports, "$scenario.png").outputStream().use {
                    rendered.compress(Bitmap.CompressFormat.PNG, 100, it)
                }
                baseline.recycle()
                rendered.recycle()
            }
        }
    }
}
