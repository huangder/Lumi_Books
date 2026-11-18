package com.huangder.lumibooks.ui.reader

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.huangder.lumibooks.ui.theme.AppShapes
import com.huangder.lumibooks.ui.theme.*
import com.huangder.lumibooks.ui.components.LiquidGlassSurface
import com.huangder.lumibooks.ui.components.LocalLiquidGlassBackdrop
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
@Config(sdk = [35], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SelectionMenuShadowRenderTest {
    @get:Rule val compose = createComposeRule()

    @Test @Config(sdk = [31])
    fun realGlassInteriorRemainsIdenticalWithSampledAndSolidSurfaces() {
        val enabled = mutableStateOf(false)
        val dark = mutableStateOf(false)
        val solid = mutableStateOf(false)
        lateinit var view: View
        compose.setContent {
            view = LocalView.current
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                val backdrop = rememberLayerBackdrop()
                Box(Modifier.size(240.dp, 160.dp)) {
                    Box(Modifier.matchParentSize().layerBackdrop(backdrop).background(
                        if (dark.value) Color(0xFF707078) else Color(0xFFF4E8D8)))
                    Box(Modifier.matchParentSize(), contentAlignment = Alignment.Center) {
                        CompositionLocalProvider(
                            LocalAppTheme provides "liquid_glass",
                            LocalIsDarkTheme provides dark.value,
                            LocalLiquidGlassCapability provides LiquidGlassCapability(true, false),
                            LocalLiquidGlassTransparency provides 0.55f,
                            LocalLiquidGlassBackdrop provides backdrop
                        ) {
                            val shape = AppShapes.rounded(26.dp)
                            val tint = if (dark.value) Color.Black else Color.White
                            LiquidGlassSurface(shape, tint,
                                modifier = Modifier.size(160.dp, 52.dp).testTag("real-capsule")
                                    .then(if (enabled.value) Modifier.selectionMenuOuterShadow(shape, dark.value) else Modifier),
                                contentScrimColor = tint.copy(alpha = 0.6f), forceFallback = solid.value
                            ) {}
                        }
                    }
                }
            }
        }
        fun screenshot(): Bitmap = compose.runOnIdle {
            Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888).also { view.draw(Canvas(it)) }
        }
        val reports = File("build/reports/selection-shadow-glass").apply { mkdirs() }
        for (isDark in listOf(false, true)) for (isSolid in listOf(false, true)) {
            compose.runOnIdle { dark.value = isDark; solid.value = isSolid; enabled.value = false }
            val baseline = screenshot()
            compose.runOnIdle { enabled.value = true }
            val actual = screenshot()
            val rect = compose.onNodeWithTag("real-capsule").fetchSemanticsNode().boundsInRoot
            for (y in rect.top.toInt() + 1 until rect.bottom.toInt() - 1) {
                for (x in rect.left.toInt() + 26 until rect.right.toInt() - 26) {
                    assertEquals("Real glass tinted at ($x,$y), dark=$isDark solid=$isSolid",
                        baseline.getPixel(x,y), actual.getPixel(x,y))
                }
            }
            for ((label, bitmap) in listOf("before" to baseline, "after" to actual)) {
                File(reports, "dark-$isDark-solid-$isSolid-$label.png").outputStream().use {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                }
            }
            baseline.recycle()
            actual.recycle()
        }
    }

    @Test fun translucentCapsuleInteriorMatchesNoShadowWhileAllFourSidesCastShadow() {
        val enabled = mutableStateOf(false)
        val dark = mutableStateOf(false)
        val continuous = mutableStateOf(false)
        lateinit var view: View
        compose.setContent {
            view = LocalView.current
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                val shape = AppShapes.rounded(26.dp, continuous.value)
                Box(Modifier.size(240.dp, 160.dp).background(
                    if (dark.value) Color(0xFF707078) else Color(0xFFF4E8D8)
                ), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(160.dp, 52.dp).testTag("capsule")
                        .then(if (enabled.value) Modifier.selectionMenuOuterShadow(shape, dark.value) else Modifier)
                        .clip(shape).background(
                            (if (dark.value) Color.Black else Color.White).copy(alpha = 0.25f)
                        ))
                }
            }
        }
        fun screenshot(): Bitmap = compose.runOnIdle {
            Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888).also {
                view.draw(Canvas(it))
            }
        }
        for (isDark in listOf(false, true)) for (isContinuous in listOf(false, true)) {
            compose.runOnIdle { dark.value = isDark; continuous.value = isContinuous; enabled.value = false }
            val baseline = screenshot()
            compose.runOnIdle { enabled.value = true }
            val actual = screenshot()
            val rect = compose.onNodeWithTag("capsule").fetchSemanticsNode().boundsInRoot
            val left = rect.left.toInt()
            val top = rect.top.toInt()
            val right = rect.right.toInt()
            val bottom = rect.bottom.toInt()
            // Cover the full middle strip and both end caps, including pixels near
            // the inner edge, without depending on shape-edge antialias rounding.
            for (y in top + 2 until bottom - 2) for (x in left + 26 until right - 26) {
                assertEquals("Shadow tinted the capsule at ($x, $y), dark=$isDark continuous=$isContinuous",
                    baseline.getPixel(x, y), actual.getPixel(x, y))
            }
            for (x in left + 2 until right - 2) {
                val y = (top + bottom) / 2
                assertEquals("Shadow tinted an end cap at $x", baseline.getPixel(x, y), actual.getPixel(x, y))
            }
            val exterior = listOf(
                (left + right) / 2 to top - 3, (left + right) / 2 to bottom + 3,
                left - 3 to (top + bottom) / 2, right + 3 to (top + bottom) / 2
            )
            exterior.forEach { (x, y) ->
                assertTrue("Missing exterior shadow at ($x, $y)",
                    android.graphics.Color.red(actual.getPixel(x, y)) < android.graphics.Color.red(baseline.getPixel(x, y)))
            }
            baseline.recycle()
            actual.recycle()
        }
    }
}
