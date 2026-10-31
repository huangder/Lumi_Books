package com.huangder.lumibooks.ui.reader

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.huangder.lumibooks.ui.components.*
import com.huangder.lumibooks.ui.theme.*
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop

/** Uses the real reader controls over a deterministic page; never changes books or preferences. */
class ReaderGlassMenuPreviewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val dark = intent.getBooleanExtra("dark", false)
            val blurOnly = intent.getBooleanExtra("blurOnly", false)
            val transparency = intent.getFloatExtra("transparency", 0.55f)
            EBookReaderTheme(appTheme = "liquid_glass", darkTheme = dark,
                liquidGlassTransparency = transparency) {
                val backdrop = rememberLayerBackdrop()
                var showing by remember { mutableStateOf(true) }
                var dialog by remember { mutableStateOf(false) }
                var bookmarked by remember { mutableStateOf(false) }
                val surface = if (dark) Color(0xFF252529) else Color(0xFFF8F8FA)
                val ink = if (dark) Color.White else Color(0xFF19191C)
                CompositionLocalProvider(
                    LocalLiquidGlassBackdrop provides backdrop,
                    LocalLiquidGlassContrastEnabled provides intent.getBooleanExtra("contrast", false),
                    LocalReduceTransparency provides blurOnly
                ) {
                    LiquidGlassMenuHost(Modifier.fillMaxSize(), backdrop = backdrop) {
                        LiquidGlassDialogHost(Modifier.fillMaxSize(), backdrop = backdrop) {
                            BoxWithConstraints(Modifier.fillMaxSize()) {
                                val layout = resolveReaderMenuLayout(DpSize(maxWidth, maxHeight),
                                    DpSize(88.dp, 56.dp), 24.dp)
                                Box(Modifier.fillMaxSize().layerBackdrop(backdrop)
                                    .pointerInput(Unit) { detectTapGestures { showing = !showing } }) {
                                    Canvas(Modifier.fillMaxSize()) {
                                        val cell = 12.dp.toPx()
                                        for (row in 0..(size.height / cell).toInt()) {
                                            for (col in 0..(size.width / cell).toInt()) {
                                                drawRect(if ((row + col) % 2 == 0) Color(0xFF6BA1BB) else Color(0xFFE5C59B),
                                                    Offset(col * cell, row * cell), Size(cell, cell))
                                            }
                                        }
                                    }
                                    Column(Modifier.fillMaxSize().statusBarsPadding().padding(20.dp),
                                        verticalArrangement = Arrangement.spacedBy(20.dp)) {
                                        repeat(24) { Text("第 ${it + 1} 段 · Lumi / abc 0123456789",
                                            color = Color.Black.copy(alpha = 0.68f), fontSize = 17.sp) }
                                    }
                                }
                                if (showing) {
                                    Box(Modifier.align(Alignment.TopStart).statusBarsPadding()) {
                                        ReaderTopBar("阅读菜单 · 玻璃验证", { finish() },
                                            sideRailItemSize = layout.sideRailItemSize,
                                            horizontalActions = layout.horizontalTopActions,
                                            bgColor = surface, contentColor = ink,
                                            glassContentScrimColor = surface.copy(alpha = 0.18f),
                                            onTtsClick = { dialog = true },
                                            isBookmarked = bookmarked, onBookmarkToggle = { bookmarked = !bookmarked },
                                            supportsHighlightRules = true, onHighlightRulesClick = { dialog = true })
                                    }
                                    FloatingReaderMenu(true, "第 3 章 · 玻璃与折射",
                                        List(10) { "第 ${it + 1} 章" }, 10, 35f, "35 / 100", 4, 10,
                                        capsuleBgColor = surface, capsuleContentColor = ink,
                                        catalogProgressColor = AppColors.Accent,
                                        glassContentScrimColor = surface.copy(alpha = 0.18f),
                                        forceSolidCapsules = false,
                                        canGoToPreviousChapter = true, canGoToNextChapter = true,
                                        onCatalogClick = { dialog = true },
                                        onPreviousChapterClick = {}, onNextChapterClick = {},
                                        onBookmarkClick = { dialog = true }, onSearchClick = { dialog = true },
                                        onThemeClick = { dialog = true }, sideRailItemSize = layout.sideRailItemSize,
                                        modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding())
                                }
                                if (dialog) LiquidGlassAlertDialog(
                                    onDismissRequest = { dialog = false }, titleText = "玻璃弹窗",
                                    text = { Text("可折射时保留玻璃效果", color = AppColors.TextPrimary) },
                                    actions = listOf(AppDialogAction("关闭", { dialog = false })))
                            }
                        }
                    }
                }
            }
        }
    }
}
