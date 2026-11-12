package com.huangder.lumibooks.ui.components

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.huangder.lumibooks.ui.theme.AppColors
import com.huangder.lumibooks.ui.theme.EBookReaderTheme
import com.huangder.lumibooks.ui.theme.LiquidGlassCapability
import com.huangder.lumibooks.ui.theme.LocalAppTheme
import com.huangder.lumibooks.ui.theme.LocalLiquidGlassCapability
import com.huangder.lumibooks.ui.theme.MotionPreference
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Renders the banner in each theme to build/reports/banner/ for visual review. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "zh-rCN-w411dp-h500dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NotificationBannerRenderTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var view: View

    private fun render(name: String, theme: String, dark: Boolean, m3: Boolean = false, eink: Boolean = false, message: BannerMessage) {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            view = LocalView.current
            EBookReaderTheme(darkTheme = dark, dynamicColor = m3, appTheme = if (theme == "liquid_glass") "lumi" else theme,
                eInkMode = eink, motionPreference = MotionPreference.REDUCED) {
                CompositionLocalProvider(
                    LocalAppTheme provides theme,
                    LocalLiquidGlassCapability provides LiquidGlassCapability(supported = theme == "liquid_glass", hdrSupported = false)
                ) {
                    val state = remember { NotificationBannerState() }
                    val backdrop = rememberLayerBackdrop()
                    LaunchedEffect(Unit) { state.show(message) }
                    Box(Modifier.fillMaxSize().background(AppColors.WindowBg)) {
                        FakeBookshelf(Modifier.fillMaxSize().layerBackdrop(backdrop))
                        NotificationBannerHost(
                            state = state,
                            liquidGlassBackdrop = backdrop,
                            modifier = Modifier.align(Alignment.TopCenter).padding(top = 28.dp, start = 12.dp, end = 12.dp)
                        )
                    }
                }
            }
        }
        compose.mainClock.advanceTimeBy(600)
        compose.runOnIdle {
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            val file = File("build/reports/banner/$name.png")
            file.parentFile!!.mkdirs()
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }

    private val sync = BannerMessage("正在同步…")
    private val archive = BannerMessage("图书已归档", body = "可在“设置 › 隐私 › 已归档的图书”中找回", onClick = {})

    @Test fun lumiLight() = render("lumi-light", "lumi", dark = false, message = sync)
    @Test fun lumiDarkArchive() = render("lumi-dark-archive", "lumi", dark = true, message = archive)
    @Test fun glassLight() = render("glass-light", "liquid_glass", dark = false, message = sync)
    @Test fun glassLightArchive() = render("glass-light-archive", "liquid_glass", dark = false, message = archive)
    @Test fun glassDark() = render("glass-dark", "liquid_glass", dark = true, message = archive)
    @Test fun material3() = render("material3", "lumi", dark = false, m3 = true, message = archive)
    @Test fun eink() = render("eink", "lumi", dark = false, eink = true, message = archive)
}

@androidx.compose.runtime.Composable
private fun FakeBookshelf(modifier: Modifier) {
    val covers = listOf(
        listOf(Color(0xFF2E7D32), Color(0xFFA5D6A7)), listOf(Color(0xFFC62828), Color(0xFFFFCC80)),
        listOf(Color(0xFF1565C0), Color(0xFF80DEEA)), listOf(Color(0xFF6A1B9A), Color(0xFFF48FB1))
    )
    Column(modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Spacer(Modifier.height(24.dp))
        Text("书库", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = AppColors.TextPrimary)
        covers.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                row.forEach { colors ->
                    Box(
                        Modifier.weight(1f).aspectRatio(0.72f).clip(RoundedCornerShape(10.dp))
                            .background(Brush.linearGradient(colors))
                    )
                }
            }
        }
    }
}
