package com.huangder.lumibooks.ui.theme

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.huangder.lumibooks.ui.components.LiquidGlassSurface
import com.huangder.lumibooks.ui.components.liquidGlassControlEdge
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Local native-render screenshots: no device or instrumented APK is involved. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w420dp-h920dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AppShapesRenderTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var view: View

    @Test fun themeSwitchUpdatesClipsBordersAndMaterialDefaults() {
        val theme = mutableStateOf("lumi")
        val dark = mutableStateOf(false)
        compose.setContent {
            EBookReaderTheme(appTheme = theme.value, darkTheme = dark.value) {
                // A software snapshot cannot provide the hardware backdrop. Keep
                // the glass theme selected to verify its fallback mask and real rim.
                CompositionLocalProvider(LocalAppTheme provides theme.value) {
                    view = LocalView.current
                    Gallery(theme.value, dark.value)
                }
            }
        }
        val output = File("build/reports/continuous-shapes").apply { mkdirs() }
        val coverBitmaps = mutableMapOf<String, Bitmap>()
        val circleBitmaps = mutableMapOf<String, Bitmap>()
        for (isDark in listOf(false, true)) for (name in listOf("lumi", "liquid_glass", "material3")) {
            compose.runOnIdle { theme.value = name; dark.value = isDark }
            compose.waitForIdle()
            lateinit var screenshot: Bitmap
            compose.runOnIdle {
                screenshot = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
                view.draw(Canvas(screenshot))
            }
            val label = "$name-${if (isDark) "dark" else "light"}"
            File(output, "$label.png").outputStream().use { screenshot.compress(Bitmap.CompressFormat.PNG, 100, it) }
            fun crop(tag: String): Bitmap {
                val bounds = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
                return Bitmap.createBitmap(screenshot, bounds.left.toInt(), bounds.top.toInt(), bounds.width.toInt(), bounds.height.toInt())
            }
            coverBitmaps[label] = crop("cover-mask")
            circleBitmaps[label] = crop("circle-mask")
            screenshot.recycle()
        }
        for (mode in listOf("light", "dark")) {
            assertTrue("Default and glass covers share the curve",
                coverBitmaps.getValue("lumi-$mode").sameAs(coverBitmaps.getValue("liquid_glass-$mode")))
            assertFalse("Material 3 still uses circular corners",
                coverBitmaps.getValue("lumi-$mode").sameAs(coverBitmaps.getValue("material3-$mode")))
            assertTrue("Explicit circle is unchanged",
                circleBitmaps.getValue("lumi-$mode").sameAs(circleBitmaps.getValue("material3-$mode")))
        }
        coverBitmaps.values.forEach(Bitmap::recycle)
        circleBitmaps.values.forEach(Bitmap::recycle)
    }

    @Composable private fun Gallery(theme: String, dark: Boolean) {
        val surface = if (dark) Color(0xFF24262B) else Color.White
        val ink = if (dark) Color.White else Color(0xFF202126)
        val accent = Color(0xFF6587C9)
        val card = AppRoundedCornerShape(18.dp)
        val cover = AppRoundedCornerShape(14.dp)
        val panel = AppRoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        Column(Modifier.fillMaxSize().background(if (dark) Color(0xFF101115) else Color(0xFFF2F3F7)).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Text("$theme / ${if (dark) "dark" else "light"}", color = ink, style = MaterialTheme.typography.titleLarge)
            Text("Continuous corner regression gallery", color = ink)
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Box(Modifier.size(96.dp, 136.dp).testTag("cover-mask").clip(cover).background(accent))
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Box(Modifier.size(56.dp).testTag("circle-mask").clip(CircleShape).background(accent))
                    Surface(shape = AppRoundedCornerShape(50), color = accent) {
                        Text("Capsule", Modifier.padding(horizontal = 26.dp, vertical = 12.dp), color = Color.White)
                    }
                }
            }
            Box(Modifier.fillMaxWidth().height(72.dp).shadow(5.dp, card).clip(card)
                .background(surface).border(0.5.dp, accent, card).padding(18.dp)) {
                Text("Card: shared clip / border / shadow", color = ink)
            }
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = surface)) {
                Text("Material component / theme shape", Modifier.padding(18.dp), color = ink)
            }
            OutlinedTextField("Input field", {}, Modifier.fillMaxWidth(), shape = AppRoundedCornerShape(12.dp))
            LiquidGlassSurface(shape = card, fallbackColor = surface, modifier = Modifier.fillMaxWidth().height(68.dp),
                contentScrimColor = surface.copy(alpha = 0.2f)) {
                Text("Glass fallback surface", color = ink)
            }
            Box(Modifier.fillMaxWidth().height(56.dp).clip(card).background(surface)
                .liquidGlassControlEdge(card, surface, dark, forceCanvas = true).padding(16.dp)) {
                Text("Continuous optical rim", color = ink)
            }
            Box(Modifier.fillMaxWidth().height(110.dp).clip(panel).background(surface).border(0.5.dp, accent, panel).padding(20.dp)) {
                Text("Sheet: rounded top / square bottom", color = ink)
            }
        }
    }
}
