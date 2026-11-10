package com.huangder.lumibooks.ui.reader

import android.app.Application
import android.graphics.Bitmap
import android.view.View
import android.view.View.MeasureSpec
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.huangder.lumibooks.ui.theme.EBookReaderTheme
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Host rendering of the actual settings sheet, without installing an APK. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "zh-rCN-w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ReaderThemeSettingsRenderTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var view: View

    private fun render(theme: String, dark: Boolean = false) {
        val fontSize = mutableStateOf(20f)
        val weight = mutableStateOf(400f)
        val brightness = mutableStateOf(-1f)
        val mask = mutableStateOf(0f)
        compose.setContent {
            view = LocalView.current
            EBookReaderTheme(appTheme = theme, darkTheme = dark) {
                Box(Modifier.fillMaxSize().testTag("reader-preview-root")) {
                    Column(Modifier.fillMaxSize()
                        .background(if (dark) Color(0xFF17191D) else Color(0xFFF7F4ED))
                        .padding(horizontal = 32.dp, vertical = 56.dp)) {
                        Text("阅读时光", color = if (dark) Color.LightGray else Color.DarkGray, fontSize = 15.sp)
                        Spacer(Modifier.height(28.dp))
                        Text("清晨的阳光透过窗棂，轻轻落在书页上。\n\n翻开一本书，让思绪随文字缓缓展开。每一段故事，都有属于自己的光。",
                            color = if (dark) Color(0xFFCAC7BF) else Color(0xFF35332F),
                            fontSize = fontSize.value.sp, lineHeight = (fontSize.value * 1.6f).sp)
                    }
                    ThemeSettingsSheet(visible = true, currentFontSize = fontSize.value,
                        currentFontType = "kaiti", currentBodyFontWeight = weight.value,
                        currentBrightness = brightness.value, currentDarkMaskOpacity = mask.value,
                        currentTheme = if (dark) "night" else "day", isAppDark = dark,
                        eInkModeEnabled = false,
                        onFontSizeChange = { fontSize.value = it }, onFontSizePreview = { fontSize.value = it },
                        onBodyFontWeightChange = { weight.value = it }, onBodyFontWeightPreview = { weight.value = it },
                        onBrightnessChange = { brightness.value = it },
                        onDarkMaskOpacityChange = { mask.value = it }, onDarkMaskOpacityPreview = { mask.value = it },
                        onThemeChange = {}, onOpenAdvanced = {}, onDismiss = {})
                }
            }
        }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(1000)
        compose.waitForIdle()
        val prefix = "$theme-${if (dark) "dark" else "light"}"
        save(prefix)
        compose.mainClock.autoAdvance = false
        val root = compose.onNodeWithTag("reader-preview-root")
        var frame = 0
        listOf("font-size", "font-weight", "brightness", "mask").forEach { control ->
            val slider = compose.onNodeWithTag("reader-$control-slider")
            val bounds = slider.fetchSemanticsNode().boundsInRoot
            root.performTouchInput { down(bounds.center); moveTo(bounds.center + Offset(90f, 0f), delayMillis = 80) }
            repeat(14) {
                compose.mainClock.advanceTimeByFrame()
                if (!dark && (control == "font-size" || control == "font-weight"))
                    save("frames/%03d".format(frame++))
            }
            save("$prefix-$control-expanded")
            root.performTouchInput { up() }
            repeat(14) {
                compose.mainClock.advanceTimeByFrame()
                if (!dark && (control == "font-size" || control == "font-weight"))
                    save("frames/%03d".format(frame++))
            }
        }
        compose.mainClock.autoAdvance = true
    }

    private fun save(name: String) {
        var dimensions = 0 to 0
        compose.runOnIdle {
            val width = view.width.coerceAtLeast(411)
            val height = view.height.coerceAtLeast(891)
            view.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY))
            view.layout(0, 0, width, height)
            dimensions = width to height
        }
        val bitmap = Bitmap.createBitmap(dimensions.first, dimensions.second, Bitmap.Config.ARGB_8888)
        compose.runOnIdle { view.draw(android.graphics.Canvas(bitmap)) }
        val output = File("build/reports/reader-theme-settings").apply { mkdirs() }
        val file = File(output, "$name.png")
        file.parentFile.mkdirs()
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    @Test fun light() = render("lumi")
    @Test fun dark() = render("lumi", dark = true)
}
