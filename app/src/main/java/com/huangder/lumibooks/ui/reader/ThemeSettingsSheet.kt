package com.huangder.lumibooks.ui.reader

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import androidx.core.graphics.ColorUtils
import com.huangder.lumibooks.ui.theme.FangSong
import com.huangder.lumibooks.ui.theme.KaiTi
import com.huangder.lumibooks.R
import com.huangder.lumibooks.domain.model.ReaderBackgroundPreset
import com.huangder.lumibooks.domain.model.ReaderBackgroundType
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.io.File

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
    currentBackgroundSelection: String = currentTheme,
    customBackgrounds: List<ReaderBackgroundPreset> = emptyList(),
    currentBrightness: Float = -1f,
    currentOptimizeLayout: Boolean = true,
    currentChineseMode: String = "original",
    currentPageTransition: String = "slide",
    onFontSizeChange: (Float) -> Unit,
    onThemeChange: (String) -> Unit,
    onBackgroundSelect: (String) -> Unit = onThemeChange,
    onAddBackgroundColor: (Int) -> Unit = {},
    onAddBackgroundImage: (Uri) -> Unit = {},
    onDeleteBackground: (String) -> Unit = {},
    onBrightnessChange: (Float) -> Unit = {},
    onOptimizeLayoutChange: (Boolean) -> Unit = {},
    onChineseModeChange: (String) -> Unit = {},
    onPageTransitionChange: (String) -> Unit = {},
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
                .padding(start = 24.dp, top = 24.dp, bottom = 24.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // 标题栏
            Row(
                Modifier.fillMaxWidth().padding(end = 24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(R.string.theme_settings_title),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = KaiTi,
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
                    Icon(Icons.Outlined.Close, stringResource(R.string.close), tint = LightTextSecondary, modifier = Modifier.size(18.dp))
                }
            }

            Spacer(Modifier.height(24.dp))

            // 字号区域
            Row(
                Modifier.fillMaxWidth().padding(end = 24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.label_font_size), fontSize = 14.sp, color = LightTextSecondary)
                Spacer(Modifier.weight(1f))
                Text("${currentFontSize.toInt()}sp", fontSize = 14.sp, color = LightTextSecondary)
            }
            Spacer(Modifier.height(4.dp))
            com.huangder.lumibooks.ui.components.PillSlider(
                value = currentFontSize,
                onValueChange = onFontSizeChange,
                valueRange = 12f..28f,
                modifier = Modifier.padding(end = 24.dp)
            )

            Spacer(Modifier.height(16.dp))

            // 亮度区域
            Row(
                Modifier.fillMaxWidth().padding(end = 24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.brightness), fontSize = 14.sp, color = LightTextSecondary)
                Spacer(Modifier.weight(1f))
                Text(
                    if (currentBrightness < 0f) stringResource(R.string.brightness_auto) else "${(currentBrightness * 100).toInt()}%",
                    fontSize = 14.sp,
                    color = LightTextSecondary
                )
            }
            Spacer(Modifier.height(4.dp))
            com.huangder.lumibooks.ui.components.PillSlider(
                value = brightnessPercent,
                onValueChange = { pct -> onBrightnessChange(pct / 100f) },
                valueRange = 0f..100f,
                modifier = Modifier.padding(end = 24.dp)
            )

            Spacer(Modifier.height(16.dp))

            // 阅读背景区域
            Text(stringResource(R.string.reading_background), fontSize = 14.sp, color = LightTextSecondary)
            Spacer(Modifier.height(12.dp))

            ReaderBackgroundSelector(
                currentSelection = currentBackgroundSelection,
                customBackgrounds = customBackgrounds,
                onSelect = onBackgroundSelect,
                onAddColor = onAddBackgroundColor,
                onAddImage = onAddBackgroundImage,
                onDelete = onDeleteBackground
            )

            Spacer(Modifier.height(16.dp))

            // 简繁转换
            Text(stringResource(R.string.chinese_convert_label), fontSize = 14.sp, color = LightTextSecondary)
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.fillMaxWidth().padding(end = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ModeButton(
                    label = stringResource(R.string.chinese_original),
                    isSelected = currentChineseMode == "original",
                    onClick = { onChineseModeChange("original") },
                    modifier = Modifier.weight(1f)
                )
                ModeButton(
                    label = stringResource(R.string.chinese_simplified),
                    isSelected = currentChineseMode == "simplified",
                    onClick = { onChineseModeChange("simplified") },
                    modifier = Modifier.weight(1f)
                )
                ModeButton(
                    label = stringResource(R.string.chinese_traditional),
                    isSelected = currentChineseMode == "traditional",
                    onClick = { onChineseModeChange("traditional") },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(16.dp))

            // 翻页效果
            Text(stringResource(R.string.page_transition_label), fontSize = 14.sp, color = LightTextSecondary)
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.fillMaxWidth().padding(end = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ModeButton(
                    label = stringResource(R.string.transition_slide),
                    isSelected = currentPageTransition == "slide",
                    onClick = { onPageTransitionChange("slide") },
                    modifier = Modifier.weight(1f)
                )
                ModeButton(
                    label = stringResource(R.string.transition_fade),
                    isSelected = currentPageTransition == "fade",
                    onClick = { onPageTransitionChange("fade") },
                    modifier = Modifier.weight(1f)
                )
                ModeButton(
                    label = stringResource(R.string.transition_curl),
                    isSelected = currentPageTransition == "curl",
                    onClick = { onPageTransitionChange("curl") },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(16.dp))

            // 优化书籍排版开关
            Row(
                Modifier.fillMaxWidth().padding(end = 24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.optimize_layout), fontSize = 14.sp, color = Color.Black)
                    Spacer(Modifier.height(2.dp))
                    Text(stringResource(R.string.optimize_layout_hint), fontSize = 12.sp, color = LightTextSecondary)
                }
                androidx.compose.material3.Switch(
                    checked = currentOptimizeLayout,
                    onCheckedChange = onOptimizeLayoutChange,
                    colors = androidx.compose.material3.SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Color(0xFF34C759),
                        uncheckedThumbColor = Color.White,
                        uncheckedTrackColor = Color(0xFFE5E5EA),
                        uncheckedBorderColor = Color(0xFFE5E5EA)
                    )
                )
            }

            Spacer(Modifier.height(16.dp))

            // 高级设置按钮
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(end = 24.dp)
                    .height(48.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(LightBgGray)
                    .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { onOpenAdvanced() },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    stringResource(R.string.advanced_settings),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.Black
                )
            }
        }
    }
}

@Composable
private fun ReaderBackgroundSelector(
    currentSelection: String,
    customBackgrounds: List<ReaderBackgroundPreset>,
    onSelect: (String) -> Unit,
    onAddColor: (Int) -> Unit,
    onAddImage: (Uri) -> Unit,
    onDelete: (String) -> Unit
) {
    var showCustomizer by remember { mutableStateOf(false) }
    var deleteArmedId by remember { mutableStateOf<String?>(null) }
    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) onAddImage(uri)
    }

    LaunchedEffect(currentSelection, customBackgrounds) {
        val armedId = deleteArmedId
        if (armedId != null &&
            (currentSelection != "custom:$armedId" || customBackgrounds.none { it.id == armedId })
        ) {
            deleteArmedId = null
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        BackgroundPresetItem(
            label = stringResource(R.string.theme_day),
            isSelected = currentSelection == "day",
            onClick = { deleteArmedId = null; onSelect("day") }
        ) {
            Box(Modifier.fillMaxSize().background(ReaderDayBg))
        }
        BackgroundPresetItem(
            label = stringResource(R.string.theme_night),
            isSelected = currentSelection == "night",
            onClick = { deleteArmedId = null; onSelect("night") }
        ) {
            Box(Modifier.fillMaxSize().background(ReaderNightBg))
        }
        BackgroundPresetItem(
            label = stringResource(R.string.theme_sepia),
            isSelected = currentSelection == "sepia",
            onClick = { deleteArmedId = null; onSelect("sepia") }
        ) {
            Box(Modifier.fillMaxSize().background(ReaderSepiaBg))
        }
        BackgroundPresetItem(
            label = stringResource(R.string.theme_green),
            isSelected = currentSelection == "green",
            onClick = { deleteArmedId = null; onSelect("green") }
        ) {
            Box(Modifier.fillMaxSize().background(ReaderGreenBg))
        }

        customBackgrounds.forEach { preset ->
            val isSelected = currentSelection == preset.selectionKey
            val isDeleteArmed = deleteArmedId == preset.id
            BackgroundPresetItem(
                label = stringResource(R.string.background_custom),
                isSelected = isSelected,
                onClick = {
                    if (isDeleteArmed) {
                        onDelete(preset.id)
                        deleteArmedId = null
                    } else {
                        deleteArmedId = null
                        onSelect(preset.selectionKey)
                    }
                },
                onLongPress = {
                    if (isSelected) deleteArmedId = preset.id
                }
            ) {
                when (preset.type) {
                    ReaderBackgroundType.COLOR -> {
                        val color = remember(preset.value) {
                            runCatching {
                                Color(android.graphics.Color.parseColor(preset.value))
                            }.getOrDefault(LightBgGray)
                        }
                        Box(Modifier.fillMaxSize().background(color))
                    }
                    ReaderBackgroundType.IMAGE -> {
                        AsyncImage(
                            model = File(preset.value),
                            contentDescription = stringResource(R.string.background_custom),
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                }
                if (isDeleteArmed) {
                    Box(
                        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.48f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Delete,
                            contentDescription = stringResource(R.string.delete_custom_background),
                            tint = Color.White,
                            modifier = Modifier.size(23.dp)
                        )
                    }
                }
            }
        }

        BackgroundPresetItem(
            label = stringResource(R.string.background_add),
            isSelected = false,
            onClick = { deleteArmedId = null; showCustomizer = true }
        ) {
            Box(
                modifier = Modifier.fillMaxSize().background(LightBgGray),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Add,
                    contentDescription = stringResource(R.string.background_add),
                    tint = LightTextSecondary,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }

    if (showCustomizer) {
        CustomBackgroundDialog(
            onAddColor = onAddColor,
            onPickPhoto = {
                showCustomizer = false
                photoPicker.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            },
            onDismiss = { showCustomizer = false }
        )
    }
}

@Composable
private fun BackgroundPresetItem(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongPress: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val latestOnClick by rememberUpdatedState(onClick)
    val latestOnLongPress by rememberUpdatedState(onLongPress)

    Column(
        modifier = Modifier.width(58.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(58.dp)
                .then(
                    if (isSelected) Modifier.border(2.dp, AccentColor, CircleShape)
                    else Modifier
                )
                .padding(4.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .border(1.dp, LightDivider, CircleShape)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = { latestOnClick() },
                            onLongPress = { latestOnLongPress?.invoke() }
                        )
                    }
            ) {
                content()
            }
        }
        Spacer(Modifier.height(5.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            color = if (isSelected) Color.Black else LightTextSecondary,
            maxLines = 1,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun CustomBackgroundDialog(
    onAddColor: (Int) -> Unit,
    onPickPhoto: () -> Unit,
    onDismiss: () -> Unit
) {
    var hue by remember { mutableFloatStateOf(35f) }
    var saturation by remember { mutableFloatStateOf(12f) }
    var lightness by remember { mutableFloatStateOf(96f) }
    val previewColorInt = android.graphics.Color.HSVToColor(
        floatArrayOf(hue, saturation / 100f, lightness / 100f)
    )

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            color = Color.White
        ) {
            Column(Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.custom_background_title),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.cancel), color = LightTextSecondary)
                    }
                }

                Box(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(Color(previewColorInt))
                        .border(1.dp, LightDivider, CircleShape)
                )
                Spacer(Modifier.height(16.dp))

                BackgroundColorSlider(stringResource(R.string.background_hue), hue, 0f..360f) {
                    hue = it
                }
                Spacer(Modifier.height(10.dp))
                BackgroundColorSlider(
                    stringResource(R.string.background_saturation),
                    saturation,
                    0f..100f
                ) { saturation = it }
                Spacer(Modifier.height(10.dp))
                BackgroundColorSlider(
                    stringResource(R.string.background_lightness),
                    lightness,
                    15f..100f
                ) { lightness = it }
                Spacer(Modifier.height(18.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(AccentColor)
                        .clickable { onAddColor(previewColorInt); onDismiss() },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.background_add_color),
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Spacer(Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .border(1.dp, LightDivider, RoundedCornerShape(22.dp))
                        .clickable { onPickPhoto() },
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Outlined.Image,
                            contentDescription = null,
                            tint = Color.Black,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.background_choose_photo),
                            color = Color.Black,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BackgroundColorSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 13.sp, color = LightTextSecondary)
        Spacer(Modifier.weight(1f))
        Text(value.toInt().toString(), fontSize = 12.sp, color = LightTextSecondary)
    }
    Spacer(Modifier.height(3.dp))
    com.huangder.lumibooks.ui.components.PillSlider(
        value = value,
        onValueChange = onValueChange,
        valueRange = range
    )
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

/** 通用模式选择按钮（简繁转换、翻页效果等） */
@Composable
private fun ModeButton(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .then(
                if (isSelected) Modifier.border(1.5.dp, Color.Black, RoundedCornerShape(12.dp))
                else Modifier
            )
            .background(if (isSelected) LightBgGray else Color.White)
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
    customFontPath: String? = null,
    currentMarginHoriz: Float,
    currentMarginVert: Float,
    currentBgColor: Color,
    currentBackgroundImagePath: String?,
    currentTextColor: Color,
    currentTextColorOverride: Int?,
    currentFontSizeSp: Float,
    onLineHeightChange: (Float) -> Unit,
    onLetterSpacingChange: (Float) -> Unit,
    onFontTypeChange: (String) -> Unit,
    onImportFont: (android.net.Uri) -> Unit = {},
    onMarginHorizChange: (Float) -> Unit,
    onMarginVertChange: (Float) -> Unit,
    currentParagraphSpacing: Float = 0f,
    currentFirstLineIndent: Float = 0f,
    onParagraphSpacingChange: (Float) -> Unit = {},
    onFirstLineIndentChange: (Float) -> Unit = {},
    onTextColorChange: (Int?) -> Unit,
    onResetSettings: () -> Unit,
    onDismiss: () -> Unit
) {
    if (!visible) return

    val sheetOffset = remember { Animatable(1f) }
    val settingsScrollState = rememberScrollState()

    LaunchedEffect(visible) {
        if (visible) {
            settingsScrollState.scrollTo(0)
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
        "fangsong" -> FangSong
        "kaiti" -> KaiTi
        else -> androidx.compose.ui.text.font.FontFamily.Default
    }
    val resolvedPreviewText = previewText.ifBlank { stringResource(R.string.preview_text) }
    val previewParagraphs = remember(resolvedPreviewText) {
        buildPreviewParagraphs(resolvedPreviewText)
    }
    val previewHorizontalPadding = (currentMarginHoriz / 2f).coerceIn(10f, 40f).dp
    val previewVerticalPadding = (currentMarginVert / 3f).coerceIn(10f, 40f).dp

    Box(Modifier.fillMaxSize()) {
        // 遮罩
        Box(
            Modifier.fillMaxSize()
                .background(Color.Black.copy(alpha = 0.1f))
                .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { isClosing = true }
        )

        // 底部弹出（90% 屏幕高度）
        Column(
            Modifier.align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(0.90f)
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
                    Icon(Icons.Outlined.Close, stringResource(R.string.close), tint = LightTextSecondary, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.weight(1f))
                Text(
                    stringResource(R.string.advanced_settings),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = KaiTi,
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
            Text(stringResource(R.string.preview), fontSize = 14.sp, color = LightTextSecondary)
            Spacer(Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(currentBgColor)
            ) {
                currentBackgroundImagePath?.let { path ->
                    AsyncImage(
                        model = File(path),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(
                            horizontal = previewHorizontalPadding,
                            vertical = previewVerticalPadding
                        ),
                    verticalArrangement = Arrangement.spacedBy(
                        (currentParagraphSpacing * 0.65f).coerceAtLeast(0f).dp
                    )
                ) {
                    previewParagraphs.forEach { paragraph ->
                        Text(
                            text = paragraph,
                            modifier = Modifier.fillMaxWidth(),
                            style = androidx.compose.ui.text.TextStyle(
                                fontSize = currentFontSizeSp.sp,
                                color = currentTextColor,
                                fontFamily = previewFont,
                                lineHeight = (currentFontSizeSp * currentLineHeight).sp,
                                letterSpacing = currentLetterSpacing.sp,
                                textIndent = androidx.compose.ui.text.style.TextIndent(
                                    firstLine = (currentFontSizeSp * currentFirstLineIndent).sp
                                )
                            )
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // 可滚动调节区
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(settingsScrollState)
            ) {
                TextColorSetting(
                    currentOverride = currentTextColorOverride,
                    effectiveTextColor = currentTextColor,
                    onColorChange = onTextColorChange
                )
                Spacer(Modifier.height(16.dp))

                // 行距
                SettingSlider(stringResource(R.string.label_line_height), currentLineHeight, 1.0f..2.5f, 0.1f, { String.format("%.1fx", it) }, onLineHeightChange)
                Spacer(Modifier.height(12.dp))

                // 字间距
                SettingSlider(stringResource(R.string.label_letter_spacing), currentLetterSpacing, 0f..10f, 0.5f, { String.format("%.1f sp", it) }, onLetterSpacingChange)
                Spacer(Modifier.height(12.dp))

                // 左右边距
                SettingSlider(stringResource(R.string.label_margin_horiz), currentMarginHoriz, 20f..80f, 2f, { "${it.toInt()} dp" }, onMarginHorizChange)
                Spacer(Modifier.height(12.dp))

                // 上下边距
                SettingSlider(stringResource(R.string.label_margin_vert), currentMarginVert, 32f..120f, 2f, { "${it.toInt()} dp" }, onMarginVertChange)
                Spacer(Modifier.height(12.dp))

                // 段间距
                SettingSlider(stringResource(R.string.label_paragraph_spacing), currentParagraphSpacing, 0f..30f, 1f, { "${it.toInt()} dp" }, onParagraphSpacingChange)
                Spacer(Modifier.height(12.dp))

                // 首行缩进
                SettingSlider(stringResource(R.string.label_first_line_indent), currentFirstLineIndent, 0f..4f, 0.5f, { "${it} 字符" }, onFirstLineIndentChange)
                Spacer(Modifier.height(16.dp))

                // 字体选择
                Text(stringResource(R.string.font_label), fontSize = 14.sp, color = LightTextSecondary)
                Spacer(Modifier.height(12.dp))
                FontSelector(currentFont = currentFontType, customFontPath = customFontPath, onFontChange = onFontTypeChange, onImportFont = onImportFont)
                Spacer(Modifier.height(24.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(LightBgGray)
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() }
                        ) { onResetSettings() },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.reset_reader_settings),
                        color = AccentColor,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

private fun buildPreviewParagraphs(text: String): List<String> {
    val lines = text.lineSequence().map(String::trim).filter(String::isNotEmpty).toList()
    if (lines.size >= 2) return lines.take(3)
    val compact = lines.firstOrNull().orEmpty()
    if (compact.isBlank()) return listOf(text)
    val chunkSize = (compact.length / 3).coerceIn(24, 48)
    return compact.chunked(chunkSize).take(3)
}

@Composable
private fun TextColorSetting(
    currentOverride: Int?,
    effectiveTextColor: Color,
    onColorChange: (Int?) -> Unit
) {
    val presetColors = remember {
        listOf(
            0xFF202124.toInt(),
            0xFF55565A.toInt(),
            0xFF4A3728.toInt(),
            0xFFF4F4F5.toInt()
        )
    }
    var showCustomColorDialog by remember { mutableStateOf(false) }
    val isCustomColor = currentOverride != null && currentOverride !in presetColors

    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.label_text_color), fontSize = 14.sp, color = Color.Black)
        Spacer(Modifier.weight(1f))
        Text(
            text = if (currentOverride == null) {
                stringResource(R.string.text_color_auto)
            } else {
                String.format("#%06X", 0xFFFFFF and currentOverride)
            },
            fontSize = 13.sp,
            color = LightTextSecondary
        )
    }
    Spacer(Modifier.height(10.dp))
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextColorSwatch(
            color = Color.White,
            isSelected = currentOverride == null,
            contentDescription = stringResource(R.string.text_color_auto),
            onClick = { onColorChange(null) }
        ) {
            Text("A", color = Color.Black, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }
        presetColors.forEach { color ->
            TextColorSwatch(
                color = Color(color),
                isSelected = currentOverride == color,
                contentDescription = stringResource(R.string.label_text_color),
                onClick = { onColorChange(color) }
            )
        }
        TextColorSwatch(
            color = if (isCustomColor) Color(currentOverride!!) else LightBgGray,
            isSelected = isCustomColor,
            contentDescription = stringResource(R.string.text_color_custom),
            onClick = { showCustomColorDialog = true }
        ) {
            if (!isCustomColor) {
                Icon(
                    imageVector = Icons.Outlined.Add,
                    contentDescription = null,
                    tint = LightTextSecondary,
                    modifier = Modifier.size(19.dp)
                )
            }
        }
    }

    if (showCustomColorDialog) {
        TextColorDialog(
            initialColor = currentOverride ?: effectiveTextColor.toArgb(),
            onApply = {
                onColorChange(it)
                showCustomColorDialog = false
            },
            onDismiss = { showCustomColorDialog = false }
        )
    }
}

@Composable
private fun TextColorSwatch(
    color: Color,
    isSelected: Boolean,
    contentDescription: String,
    onClick: () -> Unit,
    content: @Composable () -> Unit = {}
) {
    Box(
        modifier = Modifier
            .size(42.dp)
            .then(
                if (isSelected) Modifier.border(2.dp, AccentColor, CircleShape)
                else Modifier
            )
            .padding(4.dp)
            .clip(CircleShape)
            .background(color)
            .border(1.dp, LightDivider, CircleShape)
            .semantics { this.contentDescription = contentDescription }
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

@Composable
private fun TextColorDialog(
    initialColor: Int,
    onApply: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val initialHsv = remember(initialColor) {
        FloatArray(3).also { android.graphics.Color.colorToHSV(initialColor, it) }
    }
    var hue by remember(initialColor) { mutableFloatStateOf(initialHsv[0]) }
    var saturation by remember(initialColor) { mutableFloatStateOf(initialHsv[1] * 100f) }
    var lightness by remember(initialColor) { mutableFloatStateOf(initialHsv[2] * 100f) }
    val previewColor = android.graphics.Color.HSVToColor(
        floatArrayOf(hue, saturation / 100f, lightness / 100f)
    )
    val previewLabelColor = if (ColorUtils.calculateLuminance(previewColor) < 0.45) {
        Color.White
    } else {
        Color.Black
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            color = Color.White
        ) {
            Column(Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.text_color_custom),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.cancel), color = LightTextSecondary)
                    }
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(Color(previewColor))
                        .border(1.dp, LightDivider, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Aa", color = previewLabelColor, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(16.dp))
                BackgroundColorSlider(stringResource(R.string.background_hue), hue, 0f..360f) {
                    hue = it
                }
                Spacer(Modifier.height(10.dp))
                BackgroundColorSlider(
                    stringResource(R.string.background_saturation),
                    saturation,
                    0f..100f
                ) { saturation = it }
                Spacer(Modifier.height(10.dp))
                BackgroundColorSlider(
                    stringResource(R.string.background_lightness),
                    lightness,
                    5f..100f
                ) { lightness = it }
                Spacer(Modifier.height(18.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(AccentColor)
                        .clickable { onApply(previewColor) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.apply_text_color),
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
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
        Text(label, fontSize = 14.sp, color = Color.Black)
        Spacer(Modifier.weight(1f))
        Text(format(sliderValue), fontSize = 14.sp, color = Color.Black)
    }
    Spacer(Modifier.height(4.dp))
    com.huangder.lumibooks.ui.components.PillSlider(
        value = sliderValue,
        onValueChange = { sliderValue = it; onChange(it) },
        valueRange = range
    )
}

@Composable
private fun FontSelector(currentFont: String, customFontPath: String? = null, onFontChange: (String) -> Unit, onImportFont: (android.net.Uri) -> Unit = {}) {
    val launcher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            onImportFont(uri)
            onFontChange("custom")
        }
    }

    // 自定义字体的 FontFamily（从文件路径加载）
    val customFontFamily = remember(customFontPath) {
        if (customFontPath != null) {
            try {
                val file = java.io.File(customFontPath)
                if (file.exists()) FontFamily(android.graphics.Typeface.createFromFile(file))
                else FontFamily.Default
            } catch (_: Exception) { FontFamily.Default }
        } else FontFamily.Default
    }

    // 第一行：系统、衬线、仿宋
    // 第二行：楷体、导入
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // 第一行
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            FontButton(
                label = stringResource(R.string.font_system),
                isSelected = currentFont == "system",
                onClick = { onFontChange("system") },
                fontFamily = FontFamily.Default,
                modifier = Modifier.weight(1f)
            )
            FontButton(
                label = "Serif",
                isSelected = currentFont == "serif",
                onClick = { onFontChange("serif") },
                fontFamily = FontFamily.Serif,
                modifier = Modifier.weight(1f)
            )
            FontButton(
                label = stringResource(R.string.font_fangsong),
                isSelected = currentFont == "fangsong",
                onClick = { onFontChange("fangsong") },
                fontFamily = FangSong,
                modifier = Modifier.weight(1f)
            )
        }
        // 第二行
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            FontButton(
                label = stringResource(R.string.font_kaiti),
                isSelected = currentFont == "kaiti",
                onClick = { onFontChange("kaiti") },
                fontFamily = KaiTi,
                modifier = Modifier.weight(1f)
            )
            // 导入字体按钮
            val hasCustomFont = customFontPath != null
            val label = if (hasCustomFont) stringResource(R.string.font_custom) else stringResource(R.string.font_import)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .then(
                        if (hasCustomFont && currentFont == "custom")
                            Modifier.border(2.dp, AccentColor, RoundedCornerShape(12.dp))
                        else
                            Modifier.border(1.dp, LightTextSecondary, RoundedCornerShape(12.dp))
                    )
                    .background(Color.White)
                    .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {
                        if (hasCustomFont) {
                            onFontChange("custom")
                        } else {
                            launcher.launch(arrayOf("font/ttf", "font/otf", "application/octet-stream"))
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label,
                    fontSize = 14.sp,
                    fontFamily = if (hasCustomFont) customFontFamily else FontFamily.Default,
                    color = if (hasCustomFont && currentFont == "custom") AccentColor else LightTextSecondary
                )
            }
            Spacer(Modifier.weight(1f)) // 占位，保持第二行左对齐
        }
    }
}

@Composable
private fun FontButton(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    fontFamily: FontFamily = FontFamily.Default,
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
            fontFamily = fontFamily,
            color = Color.Black
        )
    }
}

private data class FontOption(val key: String, val label: String, val family: androidx.compose.ui.text.font.FontFamily)
