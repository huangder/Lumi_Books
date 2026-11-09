package com.huangder.lumibooks.ui.components

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.huangder.lumibooks.ui.theme.*
import org.junit.Assert.assertEquals
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
class GlassPressedHighlightRenderTest {
    @get:Rule val compose = createComposeRule()

    @Test fun lightHighlightCoversTheIconWhileDarkKeepsItsBrightnessAndBothExpand() {
        val dark = mutableStateOf(false)
        val hdr = mutableStateOf(false)
        lateinit var view: View
        compose.setContent {
            view = LocalView.current
            CompositionLocalProvider(
                LocalAppTheme provides "liquid_glass",
                LocalIsDarkTheme provides dark.value,
                LocalLiquidGlassCapability provides LiquidGlassCapability(true, true),
                LocalLiquidGlassHdrHighlightEnabled provides hdr.value,
                LocalMotionEnabled provides true,
                LocalLiquidGlassBackdrop provides null,
                LocalLumiBackgroundBackdrop provides null
            ) {
                Box(Modifier.size(240.dp).background(Color.Gray), contentAlignment = Alignment.Center) {
                    LiquidGlassSurface(G2ContinuousCornerShape(24f), Color.White,
                        Modifier.size(96.dp).testTag("button"), onClick = {}) {
                        Box(Modifier.size(20.dp).background(Color.Black).testTag("icon"))
                    }
                }
            }
        }
        compose.mainClock.autoAdvance = false
        fun iconBounds() = compose.onNodeWithTag("icon", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        fun iconRed(): Int {
            val center = iconBounds().center
            return compose.runOnIdle {
                val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
                view.draw(Canvas(bitmap))
                android.graphics.Color.red(bitmap.getPixel(center.x.toInt(), center.y.toInt()))
                    .also { bitmap.recycle() }
            }
        }
        for (isHdr in listOf(false, true)) for (isDark in listOf(false, true)) {
            compose.runOnIdle { dark.value = isDark; hdr.value = isHdr }
            val initialWidth = iconBounds().width
            val initialRed = iconRed()
            compose.onNodeWithTag("button").performTouchInput { down(center) }
            compose.mainClock.advanceTimeBy(500)
            assertEquals("Both themes enlarge the icon", initialWidth * 1.088f, iconBounds().width, .3f)
            val pressedRed = iconRed()
            if (isDark) assertEquals("Dark highlight stays behind the icon", initialRed, pressedRed)
            else assertTrue("Light highlight must cover the icon (HDR=$isHdr)", pressedRed > initialRed + 40)
            compose.onNodeWithTag("button").performTouchInput { up() }
            compose.mainClock.advanceTimeBy(1200)
        }
    }
}
