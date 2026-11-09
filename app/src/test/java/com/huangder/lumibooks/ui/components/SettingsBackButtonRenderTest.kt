package com.huangder.lumibooks.ui.components

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.huangder.lumibooks.ui.icons.AppIcons
import com.huangder.lumibooks.ui.theme.EBookReaderTheme
import com.huangder.lumibooks.ui.theme.LiquidGlassCapability
import com.huangder.lumibooks.ui.theme.LocalLiquidGlassCapability
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SettingsBackButtonRenderTest {
    @get:Rule val compose = createComposeRule()

    @Test fun settingsBackButtonShowsChangesInItsSampledBackground() {
        val background = mutableStateOf(Color.Red)
        val dark = mutableStateOf(false)
        lateinit var view: View
        compose.setContent {
            view = LocalView.current
            EBookReaderTheme(appTheme = "liquid_glass", darkTheme = dark.value) {
                CompositionLocalProvider(LocalLiquidGlassCapability provides LiquidGlassCapability(true, false)) {
                    val backdrop = rememberLayerBackdrop()
                    Box(Modifier.fillMaxSize()) {
                        Box(Modifier.fillMaxSize().layerBackdrop(backdrop).background(background.value))
                        ProvideLiquidGlassBackdrop(backdrop) {
                            Box(Modifier.padding(24.dp)) {
                                LiquidGlassIconButton(AppIcons.ArrowLeft, "Back", {},
                                    settingsBackButton = true, modifier = Modifier.testTag("back"))
                            }
                        }
                    }
                }
            }
        }
        fun sample(): Int {
            val bounds = compose.onNodeWithTag("back").fetchSemanticsNode().boundsInRoot
            return compose.runOnIdle {
                val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
                view.draw(Canvas(bitmap))
                // Inside the upper half of the glass, away from its rim and arrow.
                bitmap.getPixel(bounds.center.x.toInt(), (bounds.top + bounds.height * .28f).toInt())
                    .also { bitmap.recycle() }
            }
        }
        for (isDark in listOf(false, true)) {
            compose.runOnIdle { dark.value = isDark; background.value = Color.Red }
            val red = sample()
            compose.runOnIdle { background.value = Color.Blue }
            val blue = sample()
            assertTrue("Back button must transmit the background in dark=$isDark",
                android.graphics.Color.red(red) - android.graphics.Color.red(blue) > 50 &&
                    android.graphics.Color.blue(blue) - android.graphics.Color.blue(red) > 50)
        }
    }
}
