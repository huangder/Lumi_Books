package com.ebook.reader.ui.reader

import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ebook.reader.ui.animation.AppEasing
import com.ebook.reader.ui.theme.AppColors
import com.ebook.reader.ui.theme.AppRadius
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import com.ebook.reader.ui.theme.AppSpace
import com.ebook.reader.ui.theme.AppType
import com.ebook.reader.ui.theme.DingliSong
import com.ebook.reader.ui.theme.ReaderColors

/**
 * 主题设置弹窗（字体大小 + 主题切换）
 */
@Composable
fun ThemeSettingsSheet(
    visible: Boolean,
    currentFontSize: Float,
    currentTheme: String,
    onFontSizeChange: (Float) -> Unit,
    onThemeChange: (String) -> Unit,
    onDismiss: () -> Unit
) {
    if (!visible) return

    val sheetAlpha = remember { androidx.compose.animation.core.Animatable(0f) }
    val sheetOffset = remember { androidx.compose.animation.core.Animatable(1f) }

    LaunchedEffect(visible) {
        if (visible) {
            coroutineScope {
                launch { sheetAlpha.animateTo(1f, tween(250)) }
                launch { sheetOffset.snapTo(1f); sheetOffset.animateTo(0f, tween(300, easing = AppEasing.Smooth)) }
            }
        }
    }

    var isClosing by remember { mutableStateOf(false) }
    LaunchedEffect(isClosing) {
        if (isClosing) {
            coroutineScope {
                launch { sheetAlpha.animateTo(0f, tween(200)) }
                launch { sheetOffset.animateTo(1f, tween(200, easing = AppEasing.Accelerate)) }
            }
            onDismiss()
        }
    }

    Box(Modifier.fillMaxSize()) {
        // 遮罩
        Box(
            Modifier.fillMaxSize()
                .graphicsLayer { alpha = sheetAlpha.value }
                .background(Color.Black.copy(alpha = 0.1f))
                .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { isClosing = true }
        )

        // 弹窗
        Box(
            Modifier.align(Alignment.BottomCenter)
                .fillMaxWidth()
                .graphicsLayer { translationY = sheetOffset.value * size.height; alpha = sheetAlpha.value }
                .shadow(24.dp, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .background(AppColors.CardBg)
                .navigationBarsPadding()
                .padding(AppSpace.lg)
        ) {
            Column {
                // 标题栏
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("主题与设置", fontSize = AppType.Section, fontWeight = FontWeight.Bold, fontFamily = DingliSong, color = AppColors.TextPrimary)
                    Spacer(Modifier.weight(1f))
                    Icon(Icons.Outlined.Close, "关闭", tint = AppColors.TextSecondary, modifier = Modifier.size(24.dp).clickable { isClosing = true })
                }

                Spacer(Modifier.height(AppSpace.xl))

                // 字体大小
                Text("字体大小", fontSize = AppType.Body, fontWeight = FontWeight.SemiBold, color = AppColors.TextPrimary)
                Spacer(Modifier.height(AppSpace.sm))
                FontSizeSlider(currentSize = currentFontSize, onSizeChange = onFontSizeChange)

                Spacer(Modifier.height(AppSpace.xl))

                // 主题选择
                Text("阅读主题", fontSize = AppType.Body, fontWeight = FontWeight.SemiBold, color = AppColors.TextPrimary)
                Spacer(Modifier.height(AppSpace.md))
                ThemeSelector(currentTheme = currentTheme, onThemeChange = onThemeChange)
            }
        }
    }
}

@Composable
private fun FontSizeSlider(currentSize: Float, onSizeChange: (Float) -> Unit) {
    var sliderValue by remember(currentSize) { mutableFloatStateOf(currentSize) }

    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("A", fontSize = 14.sp, color = AppColors.TextSecondary)
        Spacer(Modifier.width(AppSpace.sm))
        Slider(
            value = sliderValue,
            onValueChange = { sliderValue = it },
            onValueChangeFinished = { onSizeChange(sliderValue) },
            valueRange = 12f..28f,
            steps = 7,
            modifier = Modifier.weight(1f),
            colors = SliderDefaults.colors(
                thumbColor = AppColors.Accent,
                activeTrackColor = AppColors.Accent,
                inactiveTrackColor = AppColors.Divider
            )
        )
        Spacer(Modifier.width(AppSpace.sm))
        Text("A", fontSize = 24.sp, color = AppColors.TextSecondary)
    }
    Spacer(Modifier.height(AppSpace.xs))
    Text(
        text = "${sliderValue.toInt()}sp",
        fontSize = AppType.Caption,
        color = AppColors.TextSecondary,
        modifier = Modifier.fillMaxWidth(),
        textAlign = androidx.compose.ui.text.style.TextAlign.Center
    )
}

@Composable
private fun ThemeSelector(currentTheme: String, onThemeChange: (String) -> Unit) {
    val themes = listOf(
        ThemeOption("light", "日间", Color(0xFFFBFBFC), Color(0xFF1C1C1E)),
        ThemeOption("night", "夜间", Color(0xFF1C1C1E), Color(0xFFEBEBF5)),
        ThemeOption("sepia", "护眼", Color(0xFFF5E6D3), Color(0xFF3E2723)),
        ThemeOption("green", "绿色", Color(0xFFE8F5E9), Color(0xFF1B5E20))
    )

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        themes.forEach { theme ->
            val isSelected = currentTheme == theme.key
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { onThemeChange(theme.key) }
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .then(
                            if (isSelected) Modifier.border(3.dp, AppColors.Accent, CircleShape)
                            else Modifier
                        )
                        .clip(CircleShape)
                        .background(theme.bgColor),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Aa", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = theme.textColor)
                }
                Spacer(Modifier.height(AppSpace.xs))
                Text(theme.label, fontSize = AppType.Caption, color = if (isSelected) AppColors.Accent else AppColors.TextSecondary)
            }
        }
    }
}

private data class ThemeOption(val key: String, val label: String, val bgColor: Color, val textColor: Color)
