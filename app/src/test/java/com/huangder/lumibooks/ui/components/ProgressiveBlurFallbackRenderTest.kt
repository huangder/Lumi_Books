package com.huangder.lumibooks.ui.components

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.huangder.lumibooks.ui.theme.LiquidGlassCapability
import com.huangder.lumibooks.ui.theme.LocalEInkMode
import com.huangder.lumibooks.ui.theme.LocalAppTheme
import com.huangder.lumibooks.ui.theme.LocalLiquidGlassCapability
import com.huangder.lumibooks.ui.theme.LocalReduceTransparency
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Native CPU pixels verify the actual fallback brushes, without installing a test APK. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32, 35], application = Application::class, qualifiers = "w360dp-h740dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ProgressiveBlurFallbackRenderTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var view: View

    private fun strip(rtl: Boolean = false, eInk: Boolean = false, supported: Boolean = false, glass: Boolean = false, content: @Composable () -> Unit) {
        compose.setContent {
            view = LocalView.current
            CompositionLocalProvider(
                LocalLiquidGlassCapability provides LiquidGlassCapability(supported, false),
                LocalEInkMode provides eInk,
                LocalReduceTransparency provides false,
                LocalAppTheme provides if (glass) "liquid_glass" else "lumi",
                LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr,
            ) {
                Box(Modifier.size(96.dp).background(Color.Red).testTag("strip")) { content() }
            }
        }
        compose.waitForIdle()
    }

    private fun pixels(check: (Bitmap, Rect) -> Unit) {
        val bounds = compose.onNodeWithTag("strip").fetchSemanticsNode().boundsInRoot
        compose.runOnIdle {
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            try { check(bitmap, bounds) } finally { bitmap.recycle() }
        }
    }

    @Test fun topBlurOnUnsupportedSurfaceReachesOpaqueColorAndClearEnd() {
        strip {
            ProgressiveTopBlur(rememberLayerBackdrop(), 1f, bandHeight = 96.dp,
                tint = Color.White.copy(alpha = 0.08f))
        }
        pixels { bitmap, b ->
            val top = bitmap.getPixel(b.center.x.toInt(), b.top.toInt())
            val bottom = bitmap.getPixel(b.center.x.toInt(), b.bottom.toInt() - 1)
            assertTrue("opaque top pixel: $top", AndroidColor.green(top) >= 250)
            assertTrue("clear bottom pixel: $bottom", AndroidColor.green(bottom) <= 3)
        }
    }

    @Test @Config(sdk = [32]) fun api32FallsBackEvenWhenUniformBlurIsSupported() {
        strip(supported = true) {
            ProgressiveTopBlur(rememberLayerBackdrop(), 1f, bandHeight = 96.dp, tint = Color.White)
        }
        pixels { bitmap, b ->
            assertTrue(AndroidColor.green(bitmap.getPixel(b.center.x.toInt(), b.top.toInt())) >= 250)
            assertTrue(AndroidColor.green(bitmap.getPixel(b.center.x.toInt(), b.bottom.toInt() - 1)) <= 3)
        }
    }

    @Test fun bottomGradientReversesTheOpaqueAndClearEnds() {
        strip { ProgressiveBottomScrim(bandHeight = 96.dp, tint = Color.White.copy(alpha = 0.08f)) }
        pixels { bitmap, b ->
            assertTrue(AndroidColor.green(bitmap.getPixel(b.center.x.toInt(), b.top.toInt())) <= 3)
            assertTrue(AndroidColor.green(bitmap.getPixel(b.center.x.toInt(), b.bottom.toInt() - 1)) >= 250)
        }
    }

    @Test fun bookshelfStatusKeepsTheToolbarOpaqueAndExportsItsFallback() {
        strip(glass = true) {
            StatusGradientOverlay(backdrop = rememberLayerBackdrop(),
                exportedBackdrop = rememberLayerBackdrop(), height = 96.dp, solidFraction = 0.68f)
        }
        pixels { bitmap, b ->
            val x = b.center.x.toInt()
            assertTrue(AndroidColor.green(bitmap.getPixel(x, b.top.toInt() + 60)) >= 245)
            assertTrue(AndroidColor.green(bitmap.getPixel(x, b.bottom.toInt() - 1)) <= 3)
        }
    }

    @Test fun rtlStartGradientKeepsItsOpaqueEdgeOnTheRight() {
        strip(rtl = true) { ProgressiveStartBlur(null, Color.White, bandWidth = 96.dp) }
        pixels { bitmap, b ->
            assertTrue(AndroidColor.green(bitmap.getPixel(b.left.toInt(), b.center.y.toInt())) <= 3)
            assertTrue(AndroidColor.green(bitmap.getPixel(b.right.toInt() - 1, b.center.y.toInt())) >= 250)
        }
    }

    @Test fun zeroStrengthLeavesTheOriginalPixelsUntouched() {
        strip { ProgressiveTopScrim(0f, bandHeight = 96.dp, tint = Color.White) }
        pixels { bitmap, b -> assertEquals(AndroidColor.RED, bitmap.getPixel(b.center.x.toInt(), b.top.toInt())) }
    }

    @Test fun eInkStillOmitsTheBottomEffect() {
        strip(eInk = true) { ProgressiveBottomBlur(rememberLayerBackdrop(), bandHeight = 96.dp, tint = Color.White) }
        pixels { bitmap, b -> assertEquals(AndroidColor.RED, bitmap.getPixel(b.center.x.toInt(), b.bottom.toInt() - 1)) }
    }
}

