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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.asAndroidBitmap
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
        compose.setContent {
            view = LocalView.current
            EBookReaderTheme(appTheme = theme, darkTheme = dark) {
                Box(Modifier.fillMaxSize()) {
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
                        currentFontType = "kaiti", currentBodyFontWeight = 400f,
                        currentTheme = if (dark) "night" else "day", isAppDark = dark,
                        eInkModeEnabled = true,
                        onFontSizeChange = { fontSize.value = it },
                        onThemeChange = {}, onOpenAdvanced = {}, onDismiss = {})
                }
            }
        }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(1000)
        compose.waitForIdle()
        save("$theme-${if (dark) "dark" else "light"}")
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
        File(output, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    @Test fun light() = render("lumi")
    @Test fun dark() = render("lumi", dark = true)
}
