package com.huangder.lumibooks.ui.components

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.huangder.lumibooks.ui.theme.*
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
class LiquidGlassControlEdgeRenderTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var view: View

    private fun screenshot(): Bitmap {
        lateinit var result: Bitmap
        compose.runOnIdle {
            val full = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(full))
            result = full
        }
        return result
    }

    private fun control(): Bitmap {
        val bounds = compose.onNodeWithTag("edge").fetchSemanticsNode().boundsInRoot
        val full = screenshot()
        return Bitmap.createBitmap(full, bounds.left.toInt(), bounds.top.toInt(), bounds.width.toInt(), bounds.height.toInt())
            .also { if (it !== full) full.recycle() }
    }

    private fun save(bitmap: Bitmap, name: String) {
        val output = File("build/reports/glass-control-edge").apply { mkdirs() }
        File(output, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun verifyRim(forceCanvas: Boolean) {
        compose.setContent {
            view = LocalView.current
            Box(Modifier.background(Color.White).padding(12.dp)) {
                Box(Modifier.size(160.dp, 48.dp).testTag("edge")
                    .liquidGlassControlEdge(CircleShape, Color.White, false, forceCanvas)
                    .background(Color(0xFFE8E8E8), CircleShape))
            }
        }
        val bitmap = control()
        save(bitmap, if (forceCanvas) "canvas" else "software-snapshot")
        fun luminance(x: Int, y: Int) = android.graphics.Color.red(bitmap.getPixel(x, y))
        val cx = bitmap.width / 2
        val cy = bitmap.height / 2
        val sideDark = (0..2).minOf { luminance(it, cy) }
        val topDark = (0..2).minOf { luminance(cx, it) }
        val innerLight = (2..5).maxOf { luminance(cx, it) }
        assertTrue("Sides should darken more than the top: $sideDark vs $topDark", sideDark < topDark - 10)
        assertTrue("Light sits inside the dark rim: $innerLight vs $topDark", innerLight > topDark + 15)
        assertEquals("Center must remain untouched", 232, luminance(cx, cy))
        assertEquals("Outside circular corner must stay white", 255, luminance(0, 0))
        bitmap.recycle()
    }

    @Test fun softwareSnapshotFallsBackEvenWhenRuntimeShaderIsAvailable() = verifyRim(false)
    @Test @Config(sdk = [31]) fun android12CanvasRendersTheSameOpticalLayers() = verifyRim(false)
    @Test fun canvasFallbackCanBeExercisedOnModernAndroid() = verifyRim(true)

    @Test fun optInHonorsFallbackThemeCapabilityAndLayerLimit() {
        val optedIn = mutableStateOf(false)
        val forced = mutableStateOf(false)
        val theme = mutableStateOf("liquid_glass")
        val supported = mutableStateOf(true)
        val depth = mutableStateOf(0)
        compose.setContent {
            view = LocalView.current
            CompositionLocalProvider(
                LocalAppTheme provides theme.value,
                LocalLiquidGlassCapability provides LiquidGlassCapability(supported.value, false),
                LocalLiquidGlassLayer provides depth.value
            ) {
                Box(Modifier.background(Color.White).padding(12.dp)) {
                    LiquidGlassSurface(CircleShape, Color(0xFFE8E8E8), Modifier.size(120.dp, 48.dp).testTag("edge"),
                        forceFallback = forced.value, controlEdge = optedIn.value, decorationModifier = Modifier) {}
                }
            }
        }
        fun setOptIn(enabled: Boolean): Bitmap { compose.runOnIdle { optedIn.value = enabled }; return control() }
        val old = setOptIn(false)
        val new = setOptIn(true)
        assertFalse("Real glass must receive the new rim", old.sameAs(new))
        old.recycle(); new.recycle()
        listOf<() -> Unit>(
            { forced.value = true },
            { forced.value = false; theme.value = "lumi" },
            { theme.value = "liquid_glass"; supported.value = false },
            { supported.value = true; depth.value = 3 }
        ).forEach { setFallback ->
            compose.runOnIdle(setFallback)
            val without = setOptIn(false)
            val with = setOptIn(true)
            assertTrue("Solid fallback must ignore the optical rim", without.sameAs(with))
            without.recycle(); with.recycle()
        }
    }

    @Test fun newRimPreservesClickDragCancelDisabledAndReducedMotion() {
        var clicks = 0
        val enabled = mutableStateOf(true)
        val motion = mutableStateOf(true)
        compose.setContent {
            CompositionLocalProvider(
                LocalAppTheme provides "liquid_glass",
                LocalLiquidGlassCapability provides LiquidGlassCapability(true, false),
                LocalMotionEnabled provides motion.value
            ) {
                LiquidGlassSurface(CircleShape, Color.White, Modifier.size(120.dp, 48.dp).testTag("edge"),
                    controlEdge = true, enabled = enabled.value, onClick = { clicks++ }) {}
            }
        }
        val node = compose.onNodeWithTag("edge")
        node.performTouchInput { down(center); up() }
        compose.runOnIdle { assertEquals(1, clicks) }
        node.performTouchInput { down(center); moveTo(center.copy(x = center.x + 500f), 120); up() }
        compose.runOnIdle { assertEquals(1, clicks); enabled.value = false }
        node.performTouchInput { down(center); up() }
        compose.runOnIdle { assertEquals(1, clicks); enabled.value = true; motion.value = false }
        node.performTouchInput { down(center); up() }
        compose.runOnIdle { assertEquals(2, clicks) }
    }
}
