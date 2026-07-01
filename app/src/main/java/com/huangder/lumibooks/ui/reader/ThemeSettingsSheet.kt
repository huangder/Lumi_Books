package com.huangder.lumibooks.ui.reader

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.huangder.lumibooks.ui.animation.AppEasing
import com.huangder.lumibooks.ui.animation.cardPressEffect
import com.huangder.lumibooks.ui.theme.AppColors
import com.huangder.lumibooks.ui.theme.AppRadius
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import com.huangder.lumibooks.ui.theme.AppSpace
import com.huangder.lumibooks.ui.theme.AppType
import com.huangder.lumibooks.ui.theme.DingliSong
import com.huangder.lumibooks.ui.theme.ReaderColors

/**
 * 主题设置弹窗（字体大小 + 主题切换 + 高级设置入口）
 */
@Composable
fun ThemeSettingsSheet(
    visible: Boolean,
    currentFontSize: Float,
    currentTheme: String,
    onFontSizeChange: (Float) -> Unit,
    onThemeChange: (String) -> Unit,
    onOpenAdvanced: () -> Unit,
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
                .background(AppColors.CardBg, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .padding(bottom = 16.dp)
                .padding(AppSpace.lg)
        ) {
            Column {
                // 标题栏：❌ 标题 ✅
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier.size(36.dp).clip(CircleShape).background(AppColors.BgGray).clickable { isClosing = true },
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Outlined.Close, "关闭", tint = AppColors.TextSecondary, modifier = Modifier.size(18.dp)) }
                    Text("主题与设置", fontSize = AppType.Section, fontWeight = FontWeight.Bold, fontFamily = DingliSong, color = AppColors.TextPrimary, modifier = Modifier.weight(1f).padding(horizontal = 12.dp))
                    Box(
                        modifier = Modifier.size(36.dp).clip(CircleShape).background(AppColors.Accent.copy(alpha = 0.15f)).clickable { isClosing = true },
                        contentAlignment = Alignment.Center
                    ) { Text("✓", fontSize = 16.sp, color = AppColors.Accent) }
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

                Spacer(Modifier.height(AppSpace.lg))

                // 高级设置入口
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(AppColors.BgGray)
                        .cardPressEffect()
                        .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {
                            onOpenAdvanced()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text("高级设置", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = AppColors.TextPrimary)
                }
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

/**
 * 高级排版设置弹窗——比主题设置更高的底部弹出容器。
 * 布局（从上到下）：预览框 → 行距 → 字间距 → 页边距 → 字体选择
 */
@Composable
fun AdvancedSettingsSheet(
    visible: Boolean,
    previewText: String,
    currentLineHeight: Float,
    currentLetterSpacing: Float,
    currentFontType: String,
    currentMarginHoriz: Float,
    currentMarginVert: Float,
    currentBgColor: Color,
    currentTextColor: Color,
    currentFontSizeSp: Float,
    onLineHeightChange: (Float) -> Unit,
    onLetterSpacingChange: (Float) -> Unit,
    onFontTypeChange: (String) -> Unit,
    onMarginHorizChange: (Float) -> Unit,
    onMarginVertChange: (Float) -> Unit,
    onDismiss: () -> Unit
) {
    if (!visible) return

    val sheetAlpha = remember { Animatable(0f) }
    val sheetOffset = remember { Animatable(1f) }

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

    // 预览文本用的字体
    val previewFont = when (currentFontType) {
        "serif" -> androidx.compose.ui.text.font.FontFamily.Serif
        "sans_serif" -> androidx.compose.ui.text.font.FontFamily.SansSerif
        "monospace" -> androidx.compose.ui.text.font.FontFamily.Monospace
        "dingli_song" -> DingliSong
        else -> androidx.compose.ui.text.font.FontFamily.Default
    }

    Box(Modifier.fillMaxSize()) {
        // 遮罩
        Box(
            Modifier.fillMaxSize()
                .graphicsLayer { alpha = sheetAlpha.value }
                .background(Color.Black.copy(alpha = 0.1f))
                .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { isClosing = true }
        )

        // 底部弹出（更高容器）
        Box(
            Modifier.align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(0.82f)
                .graphicsLayer { translationY = sheetOffset.value * size.height; alpha = sheetAlpha.value }
                .shadow(24.dp, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .background(AppColors.CardBg, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .padding(bottom = 16.dp)
                .padding(AppSpace.lg)
        ) {
            Column {
                // 标题栏：❌ 标题 ✅
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier.size(36.dp).clip(CircleShape).background(AppColors.BgGray).clickable { isClosing = true },
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Outlined.Close, "取消", tint = AppColors.TextSecondary, modifier = Modifier.size(18.dp)) }
                    Text("高级设置", fontSize = AppType.Section, fontWeight = FontWeight.Bold, fontFamily = DingliSong, color = AppColors.TextPrimary, modifier = Modifier.weight(1f).padding(horizontal = 12.dp))
                    Box(
                        modifier = Modifier.size(36.dp).clip(CircleShape).background(AppColors.Accent.copy(alpha = 0.15f)).clickable { isClosing = true },
                        contentAlignment = Alignment.Center
                    ) { Text("✓", fontSize = 16.sp, color = AppColors.Accent) }
                }

                Spacer(Modifier.height(AppSpace.md))

                // ── 预览框 ──
                Text("预览", fontSize = AppType.Body, fontWeight = FontWeight.SemiBold, color = AppColors.TextPrimary)
                Spacer(Modifier.height(AppSpace.sm))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(currentBgColor)
                        .padding(horizontal = (currentMarginHoriz / 3).dp, vertical = (currentMarginVert / 5).dp)
                ) {
                    Text(
                        text = previewText.ifBlank { "落霞与孤鹜齐飞，秋水共长天一色。渔舟唱晚，响穷彭蠡之滨；雁阵惊寒，声断衡阳之浦。" },
                        fontSize = currentFontSizeSp.sp,
                        color = currentTextColor.copy(alpha = 0.7f),
                        fontFamily = previewFont,
                        lineHeight = (currentFontSizeSp * currentLineHeight).sp,
                        letterSpacing = currentLetterSpacing.sp,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(Modifier.height(AppSpace.md))

                // 可滚动调节区
                LazyColumn(modifier = Modifier.weight(1f)) {
                    // ── 行距 ──
                    item {
                        Text("行距", fontSize = AppType.Body, fontWeight = FontWeight.SemiBold, color = AppColors.TextPrimary)
                        Spacer(Modifier.height(AppSpace.xs))
                        TypoSlider(
                            value = currentLineHeight,
                            range = 1.0f..2.5f,
                            step = 0.1f,
                            format = { String.format("%.1fx", it) },
                            onChange = onLineHeightChange
                        )
                        Spacer(Modifier.height(AppSpace.lg))
                    }

                    // ── 字间距 ──
                    item {
                        Text("字间距", fontSize = AppType.Body, fontWeight = FontWeight.SemiBold, color = AppColors.TextPrimary)
                        Spacer(Modifier.height(AppSpace.xs))
                        TypoSlider(
                            value = currentLetterSpacing,
                            range = 0f..10f,
                            step = 0.5f,
                            format = { String.format("%.1f sp", it) },
                            onChange = onLetterSpacingChange
                        )
                        Spacer(Modifier.height(AppSpace.lg))
                    }

                    // ── 左右边距 ──
                    item {
                        Text("左右边距", fontSize = AppType.Body, fontWeight = FontWeight.SemiBold, color = AppColors.TextPrimary)
                        Spacer(Modifier.height(AppSpace.xs))
                        TypoSlider(
                            value = currentMarginHoriz,
                            range = 20f..80f,
                            step = 2f,
                            format = { "${it.toInt()} dp" },
                            onChange = onMarginHorizChange
                        )
                        Spacer(Modifier.height(AppSpace.lg))
                    }

                    // ── 上下边距 ──
                    item {
                        Text("上下边距", fontSize = AppType.Body, fontWeight = FontWeight.SemiBold, color = AppColors.TextPrimary)
                        Spacer(Modifier.height(AppSpace.xs))
                        TypoSlider(
                            value = currentMarginVert,
                            range = 32f..120f,
                            step = 2f,
                            format = { "${it.toInt()} dp" },
                            onChange = onMarginVertChange
                        )
                        Spacer(Modifier.height(AppSpace.lg))
                    }

                    // ── 字体选择 ──
                    item {
                        Text("字体", fontSize = AppType.Body, fontWeight = FontWeight.SemiBold, color = AppColors.TextPrimary)
                        Spacer(Modifier.height(AppSpace.md))
                        FontSelector(currentFont = currentFontType, onFontChange = onFontTypeChange)
                    }
                }
            }
        }
    }
}

@Composable
private fun TypoSlider(
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    step: Float,
    format: (Float) -> String,
    onChange: (Float) -> Unit
) {
    var sliderValue by remember(value) { mutableFloatStateOf(value) }

    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Slider(
            value = sliderValue,
            onValueChange = { sliderValue = it },
            onValueChangeFinished = { onChange(sliderValue) },
            valueRange = range,
            steps = ((range.endInclusive - range.start) / step).toInt() - 1,
            modifier = Modifier.weight(1f),
            colors = SliderDefaults.colors(
                thumbColor = AppColors.Accent,
                activeTrackColor = AppColors.Accent,
                inactiveTrackColor = AppColors.Divider
            )
        )
        Spacer(Modifier.width(AppSpace.sm))
        Text(
            text = format(sliderValue),
            fontSize = AppType.Caption,
            color = AppColors.TextSecondary,
            modifier = Modifier.width(56.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.End
        )
    }
}

@Composable
private fun FontSelector(currentFont: String, onFontChange: (String) -> Unit) {
    val fonts = listOf(
        FontOption("system", "系统", androidx.compose.ui.text.font.FontFamily.Default),
        FontOption("serif", "衬线", androidx.compose.ui.text.font.FontFamily.Serif),
        FontOption("dingli_song", "鼎力宋", DingliSong),
    )

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        fonts.forEach { font ->
            val isSelected = currentFont == font.key
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { onFontChange(font.key) }
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .then(
                            if (isSelected) Modifier.border(2.dp, AppColors.Accent, CircleShape)
                            else Modifier
                        )
                        .clip(CircleShape)
                        .background(if (isSelected) AppColors.Accent.copy(alpha = 0.1f) else AppColors.BgGray),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "A文",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isSelected) AppColors.Accent else AppColors.TextPrimary,
                        fontFamily = font.family
                    )
                }
                Spacer(Modifier.height(AppSpace.xs))
                Text(font.label, fontSize = AppType.Caption, color = if (isSelected) AppColors.Accent else AppColors.TextSecondary)
            }
        }

        // "+" 按钮 —— 自定义字体导入
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {
                // TODO: 后续支持用户导入字体
            }
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(AppColors.BgGray),
                contentAlignment = Alignment.Center
            ) {
                Text("+", fontSize = 22.sp, color = AppColors.TextSecondary)
            }
            Spacer(Modifier.height(AppSpace.xs))
            Text("导入", fontSize = AppType.Caption, color = AppColors.TextSecondary)
        }
    }
}

private data class FontOption(val key: String, val label: String, val family: androidx.compose.ui.text.font.FontFamily)
