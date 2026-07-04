package com.huangder.lumibooks.ui.reader

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.huangder.lumibooks.ui.theme.DingliSong
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

// 设计规范颜色
private val AccentColor = Color(0xFFE85D5D)
private val LightTextSecondary = Color(0xFF6E6E73)
private val LightBgGray = Color(0xFFF2F2F7)
private val LightCardBg = Color.White
private val LightDivider = Color(0xFFE5E5EA)

// 阅读主题颜色
private val ReaderDayBg = Color(0xFFFFFFFF)
private val ReaderDayText = Color(0xFF000000)
private val ReaderNightBg = Color(0xFF1C1C1E)
private val ReaderNightText = Color(0xFFEBEBF5)
private val ReaderSepiaBg = Color(0xFFF5E6D3)
private val ReaderSepiaText = Color(0xFF3E2723)
private val ReaderGreenBg = Color(0xFFE8F5E9)
private val ReaderGreenText = Color(0xFF1B5E20)

/**
 * 主题设置弹窗（Page6 设计规范）
 */
@Composable
fun ThemeSettingsSheet(
    visible: Boolean,
    requestClose: Boolean = false,
    currentFontSize: Float,
    currentTheme: String,
    currentBrightness: Float = -1f,
    onFontSizeChange: (Float) -> Unit,
    onThemeChange: (String) -> Unit,
    onBrightnessChange: (Float) -> Unit = {},
    onOpenAdvanced: () -> Unit,
    onDismiss: () -> Unit
) {
    if (!visible) return

    val sheetOffset = remember { Animatable(1f) }

    LaunchedEffect(visible) {
        if (visible) {
            sheetOffset.snapTo(1f)
            sheetOffset.animateTo(0f, tween(300, easing = FastOutSlowInEasing))
        }
    }

    var isClosing by remember { mutableStateOf(false) }

    // 监听 requestClose 状态，触发动画关闭
    LaunchedEffect(requestClose) {
        if (requestClose && !isClosing) {
            isClosing = true
        }
    }

    LaunchedEffect(isClosing) {
        if (isClosing) {
            sheetOffset.animateTo(1f, tween(250, easing = FastOutSlowInEasing))
            onDismiss()
        }
    }

    // 亮度值：-1f=跟随系统，0f~1f=自定义
    val brightnessPercent = if (currentBrightness < 0f) 80f else currentBrightness * 100f

    Box(Modifier.fillMaxSize()) {
        // 遮罩
        Box(
            Modifier.fillMaxSize()
                .background(Color.Black.copy(alpha = 0.1f))
                .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { isClosing = true }
        )

        // 弹窗容器
        Column(
            Modifier.align(Alignment.BottomCenter)
                .fillMaxWidth()
                .graphicsLayer { translationY = sheetOffset.value * size.height }
                .shadow(24.dp, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .background(LightCardBg, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .navigationBarsPadding()
                .padding(24.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // 标题栏
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "主题与设置",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = DingliSong,
                    color = Color.Black
                )
                Spacer(Modifier.weight(1f))
                // 关闭按钮
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(LightBgGray)
                        .clickable { isClosing = true },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Outlined.Close, "关闭", tint = LightTextSecondary, modifier = Modifier.size(18.dp))
                }
            }

            Spacer(Modifier.height(24.dp))

            // 字号区域
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("字号", fontSize = 14.sp, color = LightTextSecondary)
                Spacer(Modifier.weight(1f))
                Text("${currentFontSize.toInt()}sp", fontSize = 14.sp, color = LightTextSecondary)
            }
            Spacer(Modifier.height(4.dp))
            Slider(
                value = currentFontSize,
                onValueChange = onFontSizeChange,
                valueRange = 12f..28f,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 0.dp),
                colors = SliderDefaults.colors(
                    thumbColor = Color.Black,
                    activeTrackColor = Color.Black,
                    inactiveTrackColor = LightDivider
                )
            )

            Spacer(Modifier.height(16.dp))

            // 亮度区域
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("亮度", fontSize = 14.sp, color = LightTextSecondary)
                Spacer(Modifier.weight(1f))
                Text(
                    if (currentBrightness < 0f) "自动" else "${(currentBrightness * 100).toInt()}%",
                    fontSize = 14.sp,
                    color = LightTextSecondary
                )
            }
            Spacer(Modifier.height(4.dp))
            Slider(
                value = brightnessPercent,
                onValueChange = { pct -> onBrightnessChange(pct / 100f) },
                valueRange = 0f..100f,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 0.dp),
                colors = SliderDefaults.colors(
                    thumbColor = Color.Black,
                    activeTrackColor = Color.Black,
                    inactiveTrackColor = LightDivider
                )
            )

            Spacer(Modifier.height(16.dp))

            // 阅读背景区域
            Text("阅读背景", fontSize = 14.sp, color = LightTextSecondary)
            Spacer(Modifier.height(12.dp))

            // 第一行：日间、夜间
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ThemeButton(
                    label = "日间",
                    bgColor = ReaderDayBg,
                    textColor = ReaderDayText,
                    isSelected = currentTheme == "day",
                    hasBorder = true,
                    onClick = { onThemeChange("day") },
                    modifier = Modifier.weight(1f)
                )
                ThemeButton(
                    label = "夜间",
                    bgColor = ReaderNightBg,
                    textColor = ReaderNightText,
                    isSelected = currentTheme == "night",
                    hasBorder = false,
                    onClick = { onThemeChange("night") },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(12.dp))

            // 第二行：护眼、护眼绿
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ThemeButton(
                    label = "护眼",
                    bgColor = ReaderSepiaBg,
                    textColor = ReaderSepiaText,
                    isSelected = currentTheme == "sepia",
                    hasBorder = false,
                    onClick = { onThemeChange("sepia") },
                    modifier = Modifier.weight(1f)
                )
                ThemeButton(
                    label = "护眼绿",
                    bgColor = ReaderGreenBg,
                    textColor = ReaderGreenText,
                    isSelected = currentTheme == "green",
                    hasBorder = false,
                    onClick = { onThemeChange("green") },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(24.dp))

            // 高级设置按钮
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(LightBgGray)
                    .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { onOpenAdvanced() },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "高级设置",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.Black
                )
            }
        }
    }
}

@Composable
private fun ThemeButton(
    label: String,
    bgColor: Color,
    textColor: Color,
    isSelected: Boolean,
    hasBorder: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // 选中边框颜色：夜间用白色，其他用黑色
    val borderColor = when {
        !isSelected -> LightDivider
        bgColor == ReaderNightBg -> Color.White
        else -> Color.Black
    }

    Box(
        modifier = modifier
            .height(48.dp)
            .clip(RoundedCornerShape(12.dp))
            .then(
                if (isSelected || hasBorder) {
                    Modifier.border(1.dp, borderColor, RoundedCornerShape(12.dp))
                } else {
                    Modifier
                }
            )
            .background(bgColor)
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            fontSize = 14.sp,
            color = textColor
        )
    }
}

/**
 * 高级排版设置弹窗——比主题设置更高的底部弹出容器。
 * 布局（从上到下）：预览框 → 行距 → 字间距 → 页边距 → 字体选择
 */
@Composable
fun AdvancedSettingsSheet(
    visible: Boolean,
    requestClose: Boolean = false,
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

    val sheetOffset = remember { Animatable(1f) }

    LaunchedEffect(visible) {
        if (visible) {
            sheetOffset.snapTo(1f)
            sheetOffset.animateTo(0f, tween(300, easing = FastOutSlowInEasing))
        }
    }

    var isClosing by remember { mutableStateOf(false) }

    // 监听 requestClose 状态，触发动画关闭
    LaunchedEffect(requestClose) {
        if (requestClose && !isClosing) {
            isClosing = true
        }
    }

    LaunchedEffect(isClosing) {
        if (isClosing) {
            sheetOffset.animateTo(1f, tween(250, easing = FastOutSlowInEasing))
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
                .background(Color.Black.copy(alpha = 0.1f))
                .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { isClosing = true }
        )

        // 底部弹出（85% 屏幕高度）
        Column(
            Modifier.align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
                .graphicsLayer { translationY = sheetOffset.value * size.height }
                .shadow(24.dp, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .background(LightCardBg, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .navigationBarsPadding()
                .padding(24.dp)
        ) {
            // 标题栏
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                // 关闭按钮
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(LightBgGray)
                        .clickable { isClosing = true },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Outlined.Close, "关闭", tint = LightTextSecondary, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.weight(1f))
                Text(
                    "高级设置",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = DingliSong,
                    color = Color.Black
                )
                Spacer(Modifier.weight(1f))
                // 确认按钮
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color.Black)
                        .clickable { isClosing = true },
                    contentAlignment = Alignment.Center
                ) {
                    Text("✓", fontSize = 16.sp, color = Color.White, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(Modifier.height(16.dp))

            // 预览框
            Text("预览", fontSize = 14.sp, color = LightTextSecondary)
            Spacer(Modifier.height(8.dp))
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
                    maxLines = 4
                )
            }

            Spacer(Modifier.height(16.dp))

            // 可滚动调节区
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
            ) {
                // 行距
                SettingSlider("行距", currentLineHeight, 1.0f..2.5f, 0.1f, { String.format("%.1fx", it) }, onLineHeightChange)
                Spacer(Modifier.height(12.dp))

                // 字间距
                SettingSlider("字间距", currentLetterSpacing, 0f..10f, 0.5f, { String.format("%.1f sp", it) }, onLetterSpacingChange)
                Spacer(Modifier.height(12.dp))

                // 左右边距
                SettingSlider("左右边距", currentMarginHoriz, 20f..80f, 2f, { "${it.toInt()} dp" }, onMarginHorizChange)
                Spacer(Modifier.height(12.dp))

                // 上下边距
                SettingSlider("上下边距", currentMarginVert, 32f..120f, 2f, { "${it.toInt()} dp" }, onMarginVertChange)
                Spacer(Modifier.height(16.dp))

                // 字体选择
                Text("字体", fontSize = 14.sp, color = LightTextSecondary)
                Spacer(Modifier.height(12.dp))
                FontSelector(currentFont = currentFontType, onFontChange = onFontTypeChange)
            }
        }
    }
}

@Composable
private fun SettingSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    step: Float,
    format: (Float) -> String,
    onChange: (Float) -> Unit
) {
    var sliderValue by remember(value) { mutableFloatStateOf(value) }

    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 14.sp, color = LightTextSecondary)
        Spacer(Modifier.weight(1f))
        Text(format(sliderValue), fontSize = 14.sp, color = LightTextSecondary)
    }
    Spacer(Modifier.height(4.dp))
    Slider(
        value = sliderValue,
        onValueChange = { sliderValue = it },
        onValueChangeFinished = { onChange(sliderValue) },
        valueRange = range,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 0.dp),
        colors = SliderDefaults.colors(
            thumbColor = Color.Black,
            activeTrackColor = Color.Black,
            inactiveTrackColor = LightDivider
        )
    )
}

@Composable
private fun FontSelector(currentFont: String, onFontChange: (String) -> Unit) {
    val fonts = listOf(
        FontOption("system", "系统", androidx.compose.ui.text.font.FontFamily.Default),
        FontOption("serif", "衬线", androidx.compose.ui.text.font.FontFamily.Serif),
        FontOption("dingli_song", "鼎力宋", DingliSong),
    )

    // 2行布局，每行3个
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // 第一行
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            fonts.take(3).forEach { font ->
                FontButton(
                    label = font.label,
                    isSelected = currentFont == font.key,
                    onClick = { onFontChange(font.key) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
        // 第二行
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            FontButton(
                label = "仿宋",
                isSelected = currentFont == "fangsong",
                onClick = { onFontChange("fangsong") },
                modifier = Modifier.weight(1f)
            )
            FontButton(
                label = "楷体",
                isSelected = currentFont == "kaiti",
                onClick = { onFontChange("kaiti") },
                modifier = Modifier.weight(1f)
            )
            // 导入字体按钮（虚线边框）
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .border(1.dp, LightTextSecondary, RoundedCornerShape(12.dp))
                    .background(Color.White)
                    .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {
                        // TODO: 导入字体
                    },
                contentAlignment = Alignment.Center
            ) {
                Text("导入", fontSize = 14.sp, color = LightTextSecondary)
            }
        }
    }
}

@Composable
private fun FontButton(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(48.dp)
            .clip(RoundedCornerShape(12.dp))
            .then(
                if (isSelected) {
                    Modifier.border(1.dp, Color.Black, RoundedCornerShape(12.dp))
                } else {
                    Modifier
                }
            )
            .background(LightBgGray)
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            fontSize = 14.sp,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            color = Color.Black
        )
    }
}

private data class FontOption(val key: String, val label: String, val family: androidx.compose.ui.text.font.FontFamily)
