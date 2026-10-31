package com.huangder.lumibooks.ui.components

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.huangder.lumibooks.ui.theme.*
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
class GlassMaterialStabilityTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var view: View

    private fun capture(): Bitmap = compose.runOnIdle {
        Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888).also {
            view.draw(Canvas(it))
        }
    }

    @Test fun takingClickHandlingOutsideACapsuleDoesNotChangeItsMaterial() {
        val clickable = mutableStateOf(true)
        compose.setContent {
            view = LocalView.current
            EBookReaderTheme {
                CompositionLocalProvider(LocalAppTheme provides "liquid_glass",
                    LocalLiquidGlassCapability provides LiquidGlassCapability(true, false),
                    LocalLiquidGlassBackdrop provides null, LocalLumiBackgroundBackdrop provides null) {
                    Box(Modifier.size(220.dp, 100.dp)) {
                        LiquidGlassSurface(AppShapes.rounded(24.dp), Color.White,
                            modifier = Modifier.size(200.dp, 48.dp),
                            contentScrimColor = Color.White.copy(alpha = 0.18f),
                            interactive = false, pressFeedbackEnabled = false,
                            onClick = if (clickable.value) ({}) else null) {}
                    }
                }
            }
        }
        val before = capture()
        compose.runOnIdle { clickable.value = false }
        val after = capture()
        assertTrue("Custom gesture and passive capsules must keep their original material", before.sameAs(after))
        before.recycle(); after.recycle()
    }

    @Test fun overlayClassificationDoesNotForceAnExtraOpaqueVeil() {
        val overlay = mutableStateOf(false)
        compose.setContent {
            view = LocalView.current
            EBookReaderTheme {
                CompositionLocalProvider(LocalAppTheme provides "liquid_glass",
                    LocalLiquidGlassCapability provides LiquidGlassCapability(true, false),
                    LocalLiquidGlassBackdrop provides null, LocalLumiBackgroundBackdrop provides null) {
                    LiquidGlassSurface(AppShapes.rounded(24.dp), Color.White,
                        modifier = Modifier.size(200.dp, 80.dp),
                        contentScrimColor = Color.White.copy(alpha = 0.18f),
                        role = if (overlay.value) AppSurfaceRole.Overlay else AppSurfaceRole.Control) {}
                }
            }
        }
        val before = capture()
        compose.runOnIdle { overlay.value = true }
        val after = capture()
        assertTrue("Explicit tint must not acquire the old 60% overlay floor", before.sameAs(after))
        before.recycle(); after.recycle()
    }
}
