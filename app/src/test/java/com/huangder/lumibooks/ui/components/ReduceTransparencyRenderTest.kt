package com.huangder.lumibooks.ui.components

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.huangder.lumibooks.ui.theme.EBookReaderTheme
import com.huangder.lumibooks.ui.theme.LocalMotionEnabled
import com.huangder.lumibooks.ui.theme.LocalReduceTransparency
import com.huangder.lumibooks.ui.theme.MotionPreference
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
class ReduceTransparencyRenderTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun nestedThemesInheritTheLivePreferenceWithoutReducingMotion() {
        val reduceTransparency = mutableStateOf(false)
        var inherited = false
        var motionEnabled = false
        compose.setContent {
            EBookReaderTheme(reduceTransparencyEnabled = reduceTransparency.value) {
                EBookReaderTheme(motionPreference = MotionPreference.STANDARD) {
                    inherited = LocalReduceTransparency.current
                    motionEnabled = LocalMotionEnabled.current
                }
            }
        }
        compose.runOnIdle { assertFalse(inherited); assertTrue(motionEnabled) }
        compose.runOnIdle { reduceTransparency.value = true }
        compose.runOnIdle { assertTrue(inherited); assertTrue(motionEnabled) }
        compose.runOnIdle { reduceTransparency.value = false }
        compose.runOnIdle { assertFalse(inherited); assertTrue(motionEnabled) }
    }

    @Test
    fun directBlurBandsRenderTheSameReadableScrimAsTheFallback() {
        val useFallback = mutableStateOf(false)
        val fromBottom = mutableStateOf(false)
        lateinit var view: View
        var bandHeightPx = 0
        compose.setContent {
            view = LocalView.current
            bandHeightPx = with(LocalDensity.current) { 120.dp.roundToPx() }
            CompositionLocalProvider(LocalReduceTransparency provides true) {
                val backdrop = rememberLayerBackdrop()
                Box(Modifier.fillMaxSize().background(Color.Black)) {
                    when {
                        useFallback.value && fromBottom.value -> ProgressiveBottomScrim(bandHeight = 120.dp, tint = Color.White)
                        useFallback.value -> ProgressiveTopScrim(1f, bandHeight = 120.dp, tint = Color.White)
                        fromBottom.value -> ProgressiveBottomBlur(backdrop, bandHeight = 120.dp, tint = Color.White)
                        else -> ProgressiveTopBlur(backdrop, 1f, bandHeight = 120.dp, tint = Color.White)
                    }
                }
            }
        }
        fun screenshot(): Bitmap {
            lateinit var bitmap: Bitmap
            compose.runOnIdle {
                bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
                view.draw(Canvas(bitmap))
            }
            return bitmap
        }
        for (bottom in listOf(false, true)) {
            compose.runOnIdle { fromBottom.value = bottom; useFallback.value = false }
            val reduced = screenshot()
            compose.runOnIdle { useFallback.value = true }
            val fallback = screenshot()
            assertTrue("Reduced blur should retain the readability scrim (bottom=$bottom)", reduced.sameAs(fallback))
            assertTrue("The scrim must visibly protect foreground text", android.graphics.Color.red(
                reduced.getPixel(reduced.width / 2, if (bottom) bandHeightPx - 1 else 0)
            ) > 0)
            reduced.recycle()
            fallback.recycle()
        }
    }

    @Test
    fun reducedTransparencyDoesNotCreateTheMenuRefractionShader() {
        compose.setContent {
            CompositionLocalProvider(LocalReduceTransparency provides true) {
                assertNull(rememberMenuOptics())
            }
        }
    }
}
