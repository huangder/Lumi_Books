package com.huangder.lumibooks.ui.reader

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import androidx.core.graphics.ColorUtils
import com.huangder.lumibooks.ui.theme.fangSongFamily
import com.huangder.lumibooks.ui.theme.KaiTi
import com.huangder.lumibooks.ui.theme.AppColors
import com.huangder.lumibooks.ui.theme.LocalAppTheme
import com.huangder.lumibooks.ui.theme.LocalIsDarkTheme
import com.huangder.lumibooks.ui.theme.LocalLiquidGlassTransparency
import com.huangder.lumibooks.ui.theme.resolveAppFontFamily
import com.huangder.lumibooks.R
import com.huangder.lumibooks.data.local.DataStoreManager
import com.huangder.lumibooks.domain.model.ReaderBackgroundPreset
import com.huangder.lumibooks.util.DownloadedFonts
import com.huangder.lumibooks.util.epub.EpubRenderMode
import com.huangder.lumibooks.domain.model.ReaderBackgroundType
import com.huangder.lumibooks.domain.model.ReaderCornerContent
import com.huangder.lumibooks.domain.model.ReaderEdgeTapMode
import com.huangder.lumibooks.domain.model.ReaderWritingMode
import com.huangder.lumibooks.domain.model.ReaderPageCorner
import com.huangder.lumibooks.domain.model.CustomFontPreset
import com.huangder.lumibooks.domain.model.ReaderThemeSuite
import com.huangder.lumibooks.domain.model.ReaderThemeSuites
import com.huangder.lumibooks.domain.model.normalizeReaderThemeSuiteName
import com.huangder.lumibooks.domain.model.readerThemeSuiteNameCodePointCount
import com.huangder.lumibooks.ui.components.ConfigurableBottomSheetBackHandler
import com.huangder.lumibooks.ui.components.LiquidGlassSurface
import com.huangder.lumibooks.ui.components.LiquidGlassButton
import com.huangder.lumibooks.ui.components.LiquidGlassDialog
import com.huangder.lumibooks.ui.components.LiquidGlassAlertDialog
import com.huangder.lumibooks.ui.components.LiquidGlassIconButton
import com.huangder.lumibooks.ui.components.LiquidGlassTextButton
import com.huangder.lumibooks.ui.components.LiquidGlassSwitch
import com.huangder.lumibooks.ui.components.LiquidGlassColumnSheetContainer
import com.huangder.lumibooks.ui.components.LiquidGlassMenuHost
import com.huangder.lumibooks.ui.components.LiquidGlassMenuItem
import com.huangder.lumibooks.ui.components.LiquidGlassMenuSpec
import com.huangder.lumibooks.ui.components.LocalLiquidGlassMenuHost
import com.huangder.lumibooks.ui.components.ProvideLiquidGlassBackdrop
import com.huangder.lumibooks.ui.components.animateBottomSheetIn
import com.huangder.lumibooks.ui.components.animateBottomSheetOut
import com.huangder.lumibooks.ui.components.liquidGlassSheetSurface
import com.huangder.lumibooks.ui.components.materialBottomSheetMotion
import androidx.compose.ui.res.stringResource
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import java.io.File

// 设计规范颜色
private val AccentColor: Color @Composable get() = AppColors.Accent
private val LightTextSecondary: Color @Composable get() = AppColors.TextSecondary
private val LightBgGray: Color @Composable get() = AppColors.BgGray
private val LightCardBg: Color @Composable get() = AppColors.CardBg
private val LightDivider: Color @Composable get() = AppColors.Divider

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
    readerThemeSuites: List<ReaderThemeSuite> = ReaderThemeSuites.defaults(),
    activeReaderThemeSuiteId: String = ReaderThemeSuites.DAY_ID,
    customFonts: List<CustomFontPreset> = emptyList(),
    currentPreserveEpubBackground: Boolean = true,
    currentBrightness: Float = -1f,
    currentOptimizeLayout: Boolean = true,
    currentUseEpubCss: Boolean = false,
    supportsBookLayout: Boolean = false,
    currentRenderMode: EpubRenderMode = EpubRenderMode.READER_LAYOUT,
    currentWritingMode: ReaderWritingMode = ReaderWritingMode.HORIZONTAL,
    supportsWritingMode: Boolean = true,
    currentChineseMode: String = "original",
    currentPageTransition: String = "slide",
    currentDisplayMode: String = "auto",
    eInkModeEnabled: Boolean = false,
    onFontSizeChange: (Float) -> Unit,
    onThemeChange: (String) -> Unit,
    onBackgroundSelect: (String) -> Unit = onThemeChange,
    onAddBackgroundColor: (Int) -> Unit = {},
    onAddBackgroundImage: (Uri) -> Unit = {},
    onDeleteBackground: (String) -> Unit = {},
    onThemeSuiteSelect: (String) -> Unit = {},
    onThemeSuiteCreate: (String) -> Unit = {},
    onThemeSuiteDelete: (String) -> Unit = {},
    onThemeSuitesReorder: (List<String>) -> Unit = {},
    onPreserveEpubBackgroundChange: (Boolean) -> Unit = {},
    onBrightnessChange: (Float) -> Unit = {},
    onOptimizeLayoutChange: (Boolean) -> Unit = {},
    onUseEpubCssChange: (Boolean) -> Unit = {},
    onRenderModeChange: (EpubRenderMode) -> Unit = {},
    onWritingModeChange: (ReaderWritingMode) -> Unit = {},
    onChineseModeChange: (String) -> Unit = {},
    onPageTransitionChange: (String) -> Unit = {},
    onDisplayModeChange: (String) -> Unit = {},
    onOpenAdvanced: () -> Unit,
    onDismiss: () -> Unit
) {
    if (!visible) return

    val sheetOffset = remember { Animatable(1f) }

    LaunchedEffect(visible) {
        if (visible) {
            sheetOffset.snapTo(1f)
            if (eInkModeEnabled) sheetOffset.snapTo(0f) else sheetOffset.animateBottomSheetIn()
        }
    }

    var isClosing by remember { mutableStateOf(false) }
    val predictiveBackProgress = ConfigurableBottomSheetBackHandler { isClosing = true }

    // 监听 requestClose 状态，触发动画关闭
    LaunchedEffect(requestClose) {
        if (requestClose && !isClosing) {
            isClosing = true
        }
    }

    LaunchedEffect(isClosing) {
        if (isClosing) {
            if (eInkModeEnabled) sheetOffset.snapTo(1f) else sheetOffset.animateBottomSheetOut()
            onDismiss()
        }
    }

    // 亮度值：-1f=跟随系统，0f~1f=自定义
    val brightnessPercent = if (currentBrightness < 0f) 80f else currentBrightness * 100f
    val isLiquidGlass = LocalAppTheme.current == "liquid_glass" && !eInkModeEnabled
    val isDark = LocalIsDarkTheme.current
    val sheetScrimAlpha = if (isLiquidGlass) 0.20f else 0.08f
    val sheetContentBackdrop = rememberLayerBackdrop()
    val sheetShape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)

    Box(Modifier.fillMaxSize()) {
        // 遮罩
        Box(
            Modifier.fillMaxSize()
                .background(
                    AppColors.Scrim.copy(
                        alpha = sheetScrimAlpha * (1f - sheetOffset.value.coerceIn(0f, 1f))
                    )
                )
                .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { isClosing = true }
        )

        // 弹窗容器：玻璃底层与控件内容分层，内部控件折射容器而非书页。
        Box(
            Modifier.align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(if (isLiquidGlass) 0.64f else 0.60f)
                .materialBottomSheetMotion(sheetOffset.value, predictiveBackProgress)
        ) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .then(
                        if (isLiquidGlass) Modifier.layerBackdrop(sheetContentBackdrop)
                        else Modifier
                    )
                    .liquidGlassSheetSurface(
                        fallbackColor = LightCardBg,
                        shape = sheetShape
                    )
            )

            ProvideLiquidGlassBackdrop(sheetContentBackdrop.takeIf { isLiquidGlass }) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    // 内容层单独裁切，滚动内容不会越过弹层圆角或底部边界。
                    .clip(sheetShape)
                    .padding(start = 14.dp, end = 14.dp, bottom = 12.dp)
                    .navigationBarsPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(top = 24.dp, bottom = 24.dp)
            ) {
            // 标题栏
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(R.string.theme_settings_title),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = resolveAppFontFamily(KaiTi),
                    color = AppColors.TextPrimary
                )
                Spacer(Modifier.weight(1f))
                LiquidGlassTextButton(
                    text = stringResource(R.string.advanced_settings),
                    onClick = onOpenAdvanced,
                    modifier = Modifier.widthIn(min = 104.dp),
                    tintedColor = if (isLiquidGlass) null else AppColors.BgGray,
                    contentColor = AppColors.TextPrimary
                )
                Spacer(Modifier.width(8.dp))
                // 关闭按钮
                LiquidGlassIconButton(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = stringResource(R.string.close),
                    onClick = { isClosing = true },
                    size = 44.dp,
                    iconSize = 20.dp,
                    contentColor = AppColors.TextPrimary,
                    normalContainerColor = LightBgGray
                )
            }

            Spacer(Modifier.height(24.dp))

            // 字号区域
            var showFontSizeDialog by remember { mutableStateOf(false) }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.label_font_size), fontSize = 14.sp, color = LightTextSecondary)
                Spacer(Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() }
                        ) { showFontSizeDialog = true }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        "${currentFontSize.toInt()} sp",
                        fontSize = 14.sp,
                        color = LightTextSecondary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            com.huangder.lumibooks.ui.components.PillSlider(
                value = currentFontSize,
                onValueChange = onFontSizeChange,
                valueRange = 12f..28f,
                step = 1f,
                onDragValueChange = onFontSizeChange,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
            if (showFontSizeDialog) {
                SliderValueInputDialog(
                    label = stringResource(R.string.label_font_size),
                    value = currentFontSize,
                    range = 12f..28f,
                    step = 1f,
                    format = { "${it.toInt()} sp" },
                    onConfirm = { onFontSizeChange(it) },
                    onDismiss = { showFontSizeDialog = false }
                )
            }

            Spacer(Modifier.height(16.dp))

            // 亮度区域
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 24.dp),
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
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                com.huangder.lumibooks.ui.components.PillSlider(
                    value = brightnessPercent,
                    onValueChange = { pct -> onBrightnessChange(pct / 100f) },
                    valueRange = 0f..100f,
                    step = 1f,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(12.dp))
                val isAutoBrightness = currentBrightness < 0f
                val autoBrightnessDescription = stringResource(R.string.brightness_auto)
                if (isLiquidGlass) {
                    val controlColor = if (isDark) Color.White else Color.Black
                    LiquidGlassSurface(
                        shape = CircleShape,
                        fallbackColor = controlColor,
                        modifier = Modifier
                            .size(32.dp)
                            .semantics { contentDescription = autoBrightnessDescription },
                        contentScrimColor = controlColor.copy(
                            alpha = if (isAutoBrightness) 0.58f else 0.24f
                        ),
                        onClick = {
                            onBrightnessChange(if (isAutoBrightness) brightnessPercent / 100f else -1f)
                        }
                    ) {
                        Text(
                            text = "A",
                            color = if (isAutoBrightness) {
                                if (isDark) Color.Black else Color.White
                            } else {
                                AppColors.TextPrimary
                            },
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .then(
                                if (isAutoBrightness) {
                                    Modifier.background(AppColors.Accent)
                                } else {
                                    Modifier.border(1.5.dp, AppColors.TextPrimary, CircleShape)
                                }
                            )
                            .semantics { contentDescription = autoBrightnessDescription }
                            .clickable {
                                onBrightnessChange(if (isAutoBrightness) brightnessPercent / 100f else -1f)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "A",
                            color = if (isAutoBrightness) AppColors.OnAccent else AppColors.TextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            if (supportsBookLayout) {
                Text(
                    text = stringResource(R.string.epub_render_mode),
                    modifier = Modifier.padding(horizontal = 24.dp),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = AppColors.TextPrimary
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ModeButton(
                        label = stringResource(R.string.epub_book_layout),
                        isSelected = currentRenderMode == EpubRenderMode.BOOK_LAYOUT,
                        onClick = { onRenderModeChange(EpubRenderMode.BOOK_LAYOUT) },
                        modifier = Modifier.weight(1f)
                    )
                    ModeButton(
                        label = stringResource(R.string.epub_reader_layout),
                        isSelected = currentRenderMode == EpubRenderMode.READER_LAYOUT,
                        onClick = { onRenderModeChange(EpubRenderMode.READER_LAYOUT) },
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(
                        if (currentRenderMode == EpubRenderMode.BOOK_LAYOUT) R.string.epub_book_layout_hint
                        else R.string.epub_reader_layout_hint
                    ),
                    modifier = Modifier.padding(horizontal = 24.dp),
                    fontSize = 12.sp,
                    color = LightTextSecondary
                )
                Spacer(Modifier.height(16.dp))
            }

            if (supportsWritingMode) {
                Text(
                    text = stringResource(R.string.reader_writing_mode),
                    modifier = Modifier.padding(horizontal = 24.dp),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = AppColors.TextPrimary
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ModeButton(
                        label = stringResource(R.string.reader_writing_horizontal),
                        isSelected = currentWritingMode == ReaderWritingMode.HORIZONTAL,
                        onClick = { onWritingModeChange(ReaderWritingMode.HORIZONTAL) },
                        modifier = Modifier.weight(1f)
                    )
                    ModeButton(
                        label = stringResource(R.string.reader_writing_vertical),
                        isSelected = currentWritingMode == ReaderWritingMode.VERTICAL_RL,
                        onClick = { onWritingModeChange(ReaderWritingMode.VERTICAL_RL) },
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(Modifier.height(16.dp))
            }

            // 主题套装区域
            Text(
                stringResource(R.string.reader_theme_suites),
                fontSize = 14.sp,
                color = LightTextSecondary,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
            Spacer(Modifier.height(12.dp))

            if (eInkModeEnabled) {
                Text(
                    stringResource(R.string.e_ink_reader_fixed_theme_hint),
                    fontSize = 13.sp,
                    color = LightTextSecondary,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )
            } else {
                ReaderThemeSuiteSelector(
                    suites = readerThemeSuites,
                    activeSuiteId = activeReaderThemeSuiteId,
                    customBackgrounds = customBackgrounds,
                    customFonts = customFonts,
                    onSelect = onThemeSuiteSelect,
                    onCreate = onThemeSuiteCreate,
                    onDelete = onThemeSuiteDelete,
                    onReorder = onThemeSuitesReorder
                )
            }


            Spacer(Modifier.height(16.dp))

            // 简繁转换
            Text(
                stringResource(R.string.chinese_convert_label),
                fontSize = 14.sp,
                color = LightTextSecondary,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 24.dp),
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

            // 翻页效果 + 显示效果（并排图标模块）
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (eInkModeEnabled) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.page_turn_module_label),
                            fontSize = 14.sp,
                            color = LightTextSecondary
                        )
                        Spacer(Modifier.height(8.dp))
                        ModeButton(
                            label = stringResource(R.string.transition_none),
                            isSelected = true,
                            onClick = {},
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                } else {
                    ReaderModeModule(
                        title = stringResource(R.string.page_turn_module_label),
                        modifier = Modifier.weight(1f),
                        items = buildList {
                            add(
                                ReaderModeOption(
                                    key = "slide",
                                    label = stringResource(R.string.transition_slide),
                                    icon = ReaderIconPageSlide
                                )
                            )
                            if ((!supportsBookLayout || currentRenderMode != EpubRenderMode.BOOK_LAYOUT) &&
                                currentWritingMode != ReaderWritingMode.VERTICAL_RL
                            ) {
                                add(
                                    ReaderModeOption(
                                        key = "continuous",
                                        label = stringResource(R.string.transition_scroll),
                                        icon = ReaderIconPageScroll
                                    )
                                )
                            }
                            add(
                                ReaderModeOption(
                                    key = "fade",
                                    label = stringResource(R.string.transition_fade),
                                    icon = ReaderIconPageFade
                                )
                            )
                            add(
                                ReaderModeOption(
                                    key = "curl",
                                    label = stringResource(R.string.transition_curl),
                                    icon = ReaderIconPageCurl
                                )
                            )
                        },
                        selectedKey = currentPageTransition,
                        onSelect = onPageTransitionChange,
                        glass = isLiquidGlass
                    )
                }
                ReaderModeModule(
                    title = stringResource(R.string.display_module_label),
                    modifier = Modifier.weight(1f),
                    enabled = !eInkModeEnabled,
                    glass = isLiquidGlass,
                    items = listOf(
                        ReaderModeOption(
                            key = "day",
                            label = stringResource(R.string.display_mode_day),
                            icon = ReaderIconDisplayDay
                        ),
                        ReaderModeOption(
                            key = "night",
                            label = stringResource(R.string.display_mode_night),
                            icon = ReaderIconDisplayNight
                        ),
                        ReaderModeOption(
                            key = "auto",
                            label = stringResource(R.string.display_mode_auto),
                            icon = ReaderIconDisplayAuto
                        )
                    ),
                    selectedKey = if (eInkModeEnabled) "auto" else currentDisplayMode,
                    onSelect = onDisplayModeChange
                )
            }

            Spacer(Modifier.height(16.dp))

            if (!supportsBookLayout) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.optimize_layout), fontSize = 14.sp, color = AppColors.TextPrimary)
                        Spacer(Modifier.height(2.dp))
                        Text(stringResource(R.string.optimize_layout_hint), fontSize = 12.sp, color = LightTextSecondary)
                    }
                    LiquidGlassSwitch(
                        checked = currentOptimizeLayout,
                        onCheckedChange = onOptimizeLayoutChange
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
    }
    }
}

@Composable
private fun ReaderThemeSuiteSelector(
    suites: List<ReaderThemeSuite>,
    activeSuiteId: String,
    customBackgrounds: List<ReaderBackgroundPreset>,
    customFonts: List<CustomFontPreset>,
    onSelect: (String) -> Unit,
    onCreate: (String) -> Unit,
    onDelete: (String) -> Unit,
    onReorder: (List<String>) -> Unit
) {
    var displayedSuites by remember(suites) { mutableStateOf(suites) }
    var armedId by remember { mutableStateOf<String?>(null) }
    var draggingId by remember { mutableStateOf<String?>(null) }
    var draggedInitialOffset by remember { mutableFloatStateOf(0f) }
    var totalDragDistance by remember { mutableFloatStateOf(0f) }
    var autoScrollSpeed by remember { mutableFloatStateOf(0f) }
    var didDrag by remember { mutableStateOf(false) }
    var showNameDialog by remember { mutableStateOf(false) }
    var pendingDeleteId by remember { mutableStateOf<String?>(null) }
    val haptics = LocalHapticFeedback.current
    val edgeScrollZonePx = with(LocalDensity.current) { 52.dp.toPx() }
    val maxAutoScrollPx = with(LocalDensity.current) { 14.dp.toPx() }
    val listState = rememberLazyListState()
    val draggedItemInfo = listState.layoutInfo.visibleItemsInfo.firstOrNull {
        it.key == draggingId
    }
    val draggedTranslationX = if (draggingId != null && draggedItemInfo != null) {
        draggedInitialOffset + totalDragDistance - draggedItemInfo.offset
    } else {
        0f
    }

    fun reorderDraggedSuiteToPointer() {
        val draggedId = draggingId ?: return
        val currentInfo = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == draggedId }
            ?: return
        val currentIndex = displayedSuites.indexOfFirst { it.id == draggedId }
        if (currentIndex < 0) return
        // LazyRow can expose the previous key/index mapping for a frame after a move.
        if (currentInfo.index != currentIndex) return
        val pointerCenter = draggedInitialOffset + currentInfo.size / 2f + totalDragDistance
        val visibleItems = listState.layoutInfo.visibleItemsInfo

        // Move by one insertion slot at a time. This prevents a missing/stale
        // candidate from turning a neighboring move into a jump to the tail.
        val nextIndex = currentIndex + 1
        val nextInfo = displayedSuites.getOrNull(nextIndex)?.let { nextSuite ->
            visibleItems.firstOrNull { it.index == nextIndex && it.key == nextSuite.id }
        }
        val previousIndex = currentIndex - 1
        val previousInfo = displayedSuites.getOrNull(previousIndex)?.let { previousSuite ->
            visibleItems.firstOrNull { it.index == previousIndex && it.key == previousSuite.id }
        }
        val targetIndex = when {
            nextInfo != null && pointerCenter > nextInfo.offset + nextInfo.size / 2f -> nextIndex
            previousInfo != null && pointerCenter < previousInfo.offset + previousInfo.size / 2f -> previousIndex
            else -> return
        }

        val mutable = displayedSuites.toMutableList()
        val moved = mutable.removeAt(currentIndex)
        mutable.add(targetIndex, moved)
        // Keep the viewport at the same numeric slot. Without this override,
        // LazyRow follows the old first-visible key when that key moves right,
        // shifting every following card under the stationary pointer.
        listState.requestScrollToItem(
            index = listState.firstVisibleItemIndex,
            scrollOffset = listState.firstVisibleItemScrollOffset
        )
        displayedSuites = mutable
        didDrag = true
    }

    LaunchedEffect(draggingId) {
        while (draggingId != null) {
            if (autoScrollSpeed != 0f) listState.scrollBy(autoScrollSpeed)
            // Re-evaluate every frame so a move rejected during LazyRow's stale
            // layout frame is applied as soon as the new key/index map is ready.
            reorderDraggedSuiteToPointer()
            withFrameNanos { }
        }
    }

    val dayName = stringResource(R.string.theme_day)
    val nightName = stringResource(R.string.theme_night)
    val sepiaName = stringResource(R.string.theme_sepia)
    val greenName = stringResource(R.string.theme_green)
    val usedNames = buildSet {
        add(dayName.lowercase())
        add(nightName.lowercase())
        add(sepiaName.lowercase())
        add(greenName.lowercase())
        suites.mapNotNullTo(this) { it.customName?.trim()?.lowercase() }
    }

    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(Unit) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { offset ->
                        val touchedInfo = listState.layoutInfo.visibleItemsInfo.firstOrNull {
                            offset.x >= it.offset && offset.x < it.offset + it.size
                        } ?: return@detectDragGesturesAfterLongPress
                        val touchedSuite = displayedSuites.getOrNull(touchedInfo.index)
                            ?.takeIf { it.id == touchedInfo.key }
                            ?: return@detectDragGesturesAfterLongPress

                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        armedId = touchedSuite.id.takeUnless { touchedSuite.isBuiltIn }
                        draggingId = touchedSuite.id
                        draggedInitialOffset = touchedInfo.offset.toFloat()
                        totalDragDistance = 0f
                        autoScrollSpeed = 0f
                        didDrag = false
                    },
                    onDrag = { change, amount ->
                        val draggedId = draggingId
                        if (draggedId != null) {
                            change.consume()
                            totalDragDistance += amount.x
                            reorderDraggedSuiteToPointer()

                            val layoutInfo = listState.layoutInfo
                            val draggedSize = layoutInfo.visibleItemsInfo
                                .firstOrNull { it.key == draggedId }
                                ?.size
                                ?: 0
                            val pointerCenter = draggedInitialOffset +
                                draggedSize / 2f +
                                totalDragDistance
                            val startEdge = layoutInfo.viewportStartOffset + edgeScrollZonePx
                            val endEdge = layoutInfo.viewportEndOffset - edgeScrollZonePx
                            autoScrollSpeed = when {
                                pointerCenter < startEdge -> -(
                                    3f + (startEdge - pointerCenter) * 0.18f
                                ).coerceAtMost(maxAutoScrollPx)
                                pointerCenter > endEdge -> (
                                    3f + (pointerCenter - endEdge) * 0.18f
                                ).coerceAtMost(maxAutoScrollPx)
                                else -> 0f
                            }
                        }
                    },
                    onDragEnd = {
                        if (draggingId != null && didDrag) {
                            onReorder(displayedSuites.map(ReaderThemeSuite::id))
                        }
                        draggingId = null
                        totalDragDistance = 0f
                        autoScrollSpeed = 0f
                        if (didDrag) armedId = null
                    },
                    onDragCancel = {
                        displayedSuites = suites
                        draggingId = null
                        totalDragDistance = 0f
                        autoScrollSpeed = 0f
                        armedId = null
                    }
                )
            },
        state = listState,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(displayedSuites, key = ReaderThemeSuite::id) { suite ->
            val isDragging = draggingId == suite.id
            ThemeSuiteCard(
                suite = suite,
                displayName = when (suite.id) {
                    ReaderThemeSuites.DAY_ID -> dayName
                    ReaderThemeSuites.NIGHT_ID -> nightName
                    ReaderThemeSuites.SEPIA_ID -> sepiaName
                    ReaderThemeSuites.GREEN_ID -> greenName
                    else -> suite.customName.orEmpty()
                },
                isSelected = activeSuiteId == suite.id,
                isArmed = armedId == suite.id,
                isDragging = isDragging,
                customBackgrounds = customBackgrounds,
                customFonts = customFonts,
                modifier = Modifier
                    .then(
                        if (!isDragging) {
                            Modifier.animateItem(
                                fadeInSpec = null,
                                fadeOutSpec = null,
                                placementSpec = tween(180, easing = FastOutSlowInEasing)
                            )
                        } else {
                            Modifier
                        }
                    )
                    .zIndex(if (isDragging) 2f else 0f)
                    .graphicsLayer {
                        translationX = if (isDragging) draggedTranslationX else 0f
                        scaleX = if (isDragging) 1.035f else 1f
                        scaleY = if (isDragging) 1.035f else 1f
                        shadowElevation = if (isDragging) 12.dp.toPx() else 0f
                    },
                onClick = {
                    if (armedId == suite.id) {
                        armedId = null
                    } else {
                        armedId = null
                        onSelect(suite.id)
                    }
                },
                onDeleteClick = { pendingDeleteId = suite.id }
            )
        }
        item(key = "add-theme-suite") {
            AddThemeSuiteCard(
                onClick = {
                    armedId = null
                    showNameDialog = true
                }
            )
        }
    }

    if (showNameDialog) {
        NewThemeSuiteDialog(
            usedNames = usedNames,
            onConfirm = {
                onCreate(it)
                showNameDialog = false
            },
            onDismiss = { showNameDialog = false }
        )
    }

    pendingDeleteId?.let { suiteId ->
        val suiteName = displayedSuites.firstOrNull { it.id == suiteId }?.customName.orEmpty()
        LiquidGlassAlertDialog(
            onDismissRequest = { pendingDeleteId = null },
            title = {
                Text(
                    stringResource(R.string.delete_theme_suite_title),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppColors.TextPrimary
                )
            },
            text = {
                Text(
                    stringResource(R.string.delete_theme_suite_message, suiteName),
                    color = LightTextSecondary,
                    fontSize = 14.sp
                )
            },
            dismissButton = {
                LiquidGlassTextButton(
                    text = stringResource(R.string.cancel),
                    onClick = { pendingDeleteId = null }
                )
            },
            confirmButton = {
                LiquidGlassTextButton(
                    text = stringResource(R.string.delete),
                    onClick = {
                        onDelete(suiteId)
                        armedId = null
                        pendingDeleteId = null
                    },
                    tintedColor = Color(0xFFFF3B30)
                )
            }
        )
    }
}

@Composable
private fun ThemeSuiteCard(
    suite: ReaderThemeSuite,
    displayName: String,
    isSelected: Boolean,
    isArmed: Boolean,
    isDragging: Boolean,
    customBackgrounds: List<ReaderBackgroundPreset>,
    customFonts: List<CustomFontPreset>,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val shape = RoundedCornerShape(14.dp)
    val backgroundPreset = customBackgrounds.firstOrNull {
        it.selectionKey == suite.settings.backgroundSelection
    }
    val fallbackBackground = suiteBackgroundColor(suite, backgroundPreset)
    val textColor = suiteTextColor(suite, backgroundPreset, fallbackBackground)
    val fontFamily = rememberSuiteFontFamily(suite, customFonts)

    Box(
        modifier = modifier
            .size(width = 104.dp, height = 132.dp)
            .semantics { selected = isSelected },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(width = 104.dp, height = 132.dp)
                .then(if (isSelected) Modifier.border(2.dp, AccentColor, RoundedCornerShape(18.dp)) else Modifier)
                .padding(4.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(shape)
                    .background(fallbackBackground)
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                        onClick = onClick
                    )
            ) {
                if (backgroundPreset?.type == ReaderBackgroundType.IMAGE) {
                    AsyncImage(
                        model = File(backgroundPreset.value),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 13.dp, vertical = 12.dp)
                ) {
                    Text(
                        text = "Aa",
                        color = textColor,
                        fontFamily = fontFamily,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = stringResource(R.string.theme_suite_preview_text),
                        color = textColor,
                        fontFamily = fontFamily,
                        fontSize = 25.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = displayName,
                        color = textColor,
                        fontFamily = fontFamily,
                        fontSize = if (displayName.codePointCount(0, displayName.length) > 10) 11.sp else 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (isArmed && !isDragging) {
                    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.28f)))
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.58f))
                            .clickable(onClick = onDeleteClick),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Outlined.Delete,
                            contentDescription = stringResource(R.string.delete_theme_suite),
                            tint = Color.White,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AddThemeSuiteCard(onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    val strokeColor = LightTextSecondary
    Box(
        modifier = Modifier
            .size(width = 104.dp, height = 132.dp)
            .padding(4.dp)
            .drawBehind {
                drawRoundRect(
                    color = strokeColor,
                    cornerRadius = CornerRadius(14.dp.toPx()),
                    style = Stroke(
                        width = 1.5.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(
                            floatArrayOf(7.dp.toPx(), 6.dp.toPx())
                        )
                    )
                )
            }
            .clip(shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 12.dp)
    ) {
        Column(Modifier.fillMaxSize()) {
            Text("Aa", color = strokeColor, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Text(
                stringResource(R.string.theme_suite_preview_text),
                color = strokeColor,
                fontSize = 25.sp,
                fontWeight = FontWeight.SemiBold
            )
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Outlined.Add,
                    contentDescription = stringResource(R.string.add_theme_suite),
                    tint = strokeColor,
                    modifier = Modifier.size(30.dp)
                )
            }
            Text(
                stringResource(R.string.background_add),
                color = strokeColor,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun NewThemeSuiteDialog(
    usedNames: Set<String>,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    var name by remember { mutableStateOf("") }
    val normalized = normalizeReaderThemeSuiteName(name)
    val count = readerThemeSuiteNameCodePointCount(normalized)
    val error = when {
        normalized.isEmpty() -> R.string.theme_suite_name_required
        count > 20 -> R.string.theme_suite_name_too_long
        normalized.lowercase() in usedNames -> R.string.theme_suite_name_duplicate
        else -> null
    }
    val confirm = { if (error == null) onConfirm(normalized) }

    LiquidGlassDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        transparencyOverride = (LocalLiquidGlassTransparency.current - 0.10f).coerceIn(0f, 0.90f),
        backgroundBlurRadius = 12.dp
    ) {
        Column(Modifier.padding(horizontal = 28.dp, vertical = 22.dp)) {
            Text(
                stringResource(R.string.new_theme_suite_title),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = AppColors.TextPrimary
            )
            Spacer(Modifier.height(18.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(LightBgGray)
                    .border(
                        1.dp,
                        if (name.isNotEmpty() && error != null) Color(0xFFFF3B30) else LightDivider,
                        RoundedCornerShape(14.dp)
                    )
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                BasicTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                    singleLine = true,
                    textStyle = TextStyle(color = AppColors.TextPrimary, fontSize = 16.sp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { confirm() }),
                    decorationBox = { inner ->
                        if (name.isEmpty()) {
                            Text(
                                stringResource(R.string.theme_suite_name_hint),
                                color = LightTextSecondary,
                                fontSize = 16.sp
                            )
                        }
                        inner()
                    }
                )
            }
            if (name.isNotEmpty() && error != null) {
                Spacer(Modifier.height(6.dp))
                Text(stringResource(error), color = Color(0xFFFF3B30), fontSize = 12.sp)
            }
            Spacer(Modifier.height(18.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End)) {
                LiquidGlassTextButton(text = stringResource(R.string.cancel), onClick = onDismiss)
                LiquidGlassTextButton(
                    text = stringResource(R.string.confirm),
                    onClick = { confirm() },
                    enabled = error == null,
                    tintedColor = AccentColor
                )
            }
        }
    }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
}

@Composable
private fun rememberSuiteFontFamily(
    suite: ReaderThemeSuite,
    customFonts: List<CustomFontPreset>
): FontFamily {
    val fontType = suite.settings.fontType
    val customPath = customFonts.firstOrNull { fontType == "custom:${it.id}" }?.path
    val fangSongFamilyValue = fangSongFamily()
    return remember(fontType, customPath, fangSongFamilyValue) {
        when {
            fontType == "serif" -> FontFamily.Serif
            fontType == "fangsong" -> fangSongFamilyValue
            fontType == "kaiti" -> KaiTi
            customPath != null -> runCatching {
                FontFamily(android.graphics.Typeface.createFromFile(File(customPath)))
            }.getOrDefault(FontFamily.Default)
            else -> FontFamily.Default
        }
    }
}

private fun suiteBackgroundColor(
    suite: ReaderThemeSuite,
    preset: ReaderBackgroundPreset?
): Color = when {
    preset?.type == ReaderBackgroundType.COLOR -> runCatching {
        Color(android.graphics.Color.parseColor(preset.value))
    }.getOrDefault(ReaderDayBg)
    preset?.dominantColor != null -> Color(preset.dominantColor)
    suite.settings.backgroundSelection == ReaderThemeSuites.NIGHT_ID -> ReaderNightBg
    suite.settings.backgroundSelection == ReaderThemeSuites.SEPIA_ID -> ReaderSepiaBg
    suite.settings.backgroundSelection == ReaderThemeSuites.GREEN_ID -> ReaderGreenBg
    else -> ReaderDayBg
}

private fun suiteTextColor(
    suite: ReaderThemeSuite,
    preset: ReaderBackgroundPreset?,
    backgroundColor: Color
): Color {
    suite.settings.textColor?.let { return Color(it) }
    if (preset != null) {
        return if (ColorUtils.calculateLuminance(backgroundColor.toArgb()) < 0.42) {
            Color(0xFFE8E8EA)
        } else {
            Color(0xFF333333)
        }
    }
    return when (suite.settings.backgroundSelection) {
        ReaderThemeSuites.NIGHT_ID -> Color(0xFFCCCCCC)
        ReaderThemeSuites.SEPIA_ID -> Color(0xFF4A3728)
        ReaderThemeSuites.GREEN_ID -> Color(0xFF2E7D32)
        else -> Color(0xFF333333)
    }
}

@Composable
private fun ReaderBackgroundSelector(
    currentSelection: String,
    customBackgrounds: List<ReaderBackgroundPreset>,
    onSelect: (String) -> Unit,
    onAddColor: (Int) -> Unit,
    onAddImage: (Uri) -> Unit,
    onDelete: (String) -> Unit,
    horizontalPadding: Dp = 24.dp
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
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = horizontalPadding),
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
                        val fallbackColor = LightBgGray
                        val color = remember(preset.value, fallbackColor) {
                            runCatching {
                                Color(android.graphics.Color.parseColor(preset.value))
                            }.getOrDefault(fallbackColor)
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
            color = if (isSelected) AppColors.TextPrimary else LightTextSecondary,
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
    val dialogTransparency = (LocalLiquidGlassTransparency.current - 0.10f)
        .coerceIn(0f, 0.90f)
    var hue by remember { mutableFloatStateOf(35f) }
    var saturation by remember { mutableFloatStateOf(12f) }
    var lightness by remember { mutableFloatStateOf(96f) }
    val previewColorInt = android.graphics.Color.HSVToColor(
        floatArrayOf(hue, saturation / 100f, lightness / 100f)
    )

    LiquidGlassDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        transparencyOverride = dialogTransparency
    ) {
            Column(Modifier.padding(horizontal = 28.dp, vertical = 20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.custom_background_title),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                            color = AppColors.TextPrimary
                    )
                    Spacer(Modifier.weight(1f))
                    LiquidGlassTextButton(
                        text = stringResource(R.string.cancel),
                        onClick = onDismiss,
                        contentColor = LightTextSecondary
                    )
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

                LiquidGlassTextButton(
                    text = stringResource(R.string.background_add_color),
                    onClick = { onAddColor(previewColorInt); onDismiss() },
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                    tintedColor = AccentColor
                )
                Spacer(Modifier.height(10.dp))
                LiquidGlassButton(
                    onClick = onPickPhoto,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .border(1.dp, LightDivider, RoundedCornerShape(22.dp))
                ) {
                        Icon(
                            imageVector = Icons.Outlined.Image,
                            contentDescription = null,
                            tint = AppColors.TextPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.background_choose_photo),
                            color = AppColors.TextPrimary,
                            fontSize = 14.sp
                        )
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
        valueRange = range,
        step = 1f,
        onDragValueChange = onValueChange
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
                if (isSelected) Modifier.border(1.5.dp, AppColors.TextPrimary, RoundedCornerShape(12.dp))
                else Modifier
            )
            .background(if (isSelected) LightBgGray else AppColors.CardBg)
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            fontSize = 14.sp,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            color = AppColors.TextPrimary
        )
    }
}

/** 阅读模式图标选项（翻页效果 / 显示效果共用） */
private data class ReaderModeOption(
    val key: String,
    val label: String,
    val icon: ImageVector
)

/**
 * 并排图标选择模块：标题在上，图标置于圆角浅灰容器内，
 * 选中项为白底圆角块（截图「翻页 / 显示」样式）。
 */
@Composable
private fun ReaderModeModule(
    title: String,
    items: List<ReaderModeOption>,
    selectedKey: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    glass: Boolean = false
) {
    Column(modifier) {
        Text(
            title,
            fontSize = 14.sp,
            color = LightTextSecondary
        )
        Spacer(Modifier.height(8.dp))
        val shape = RoundedCornerShape(12.dp)
        val containerModifier = if (glass) {
            val isDark = LocalIsDarkTheme.current
            val transparency = LocalLiquidGlassTransparency.current
            val surfaceAlpha = if (isDark) {
                0.43f - transparency * 0.12f
            } else {
                0.59f - transparency * 0.18f
            }
            Modifier
                .clip(shape)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            LightCardBg.copy(alpha = (surfaceAlpha + 0.06f).coerceAtMost(0.62f)),
                            LightCardBg.copy(alpha = surfaceAlpha)
                        )
                    )
                )
                .border(
                    width = 0.7.dp,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = if (isDark) 0.24f else 0.72f),
                            Color.White.copy(alpha = if (isDark) 0.08f else 0.20f)
                        )
                    ),
                    shape = shape
                )
                .padding(4.dp)
        } else {
            Modifier
                .clip(shape)
                .background(AppColors.BgGray)
                .padding(4.dp)
        }
        val itemSpacing = 4.dp
        val containerPadding = 4.dp
        val density = LocalDensity.current
        var containerWidthPx by remember { mutableIntStateOf(0) }
        val itemCount = items.size
        val paddingPx = with(density) { containerPadding.toPx() }
        val spacingPx = with(density) { itemSpacing.toPx() }
        val contentWidthPx = (containerWidthPx - paddingPx * 2f).coerceAtLeast(0f)
        val cellWidthPx = if (itemCount > 0) {
            ((contentWidthPx - spacingPx * (itemCount - 1)) / itemCount).coerceAtLeast(0f)
        } else {
            0f
        }
        val selectedIndex = items.indexOfFirst { it.key == selectedKey }.coerceAtLeast(0)
        val indicatorTargetX = if (itemCount > 0) {
            selectedIndex * (cellWidthPx + spacingPx)
        } else {
            0f
        }
        val indicatorX by animateDpAsState(
            targetValue = with(density) { indicatorTargetX.toDp() },
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMediumLow
            ),
            label = "readerModeIndicatorX"
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .onSizeChanged { containerWidthPx = it.width }
                .then(containerModifier)
        ) {
            if (itemCount > 0 && containerWidthPx > 0) {
                // 选中底色滑块：随点击连贯滑动到新位置
                Box(
                    modifier = Modifier
                        .offset(x = indicatorX)
                        .width(with(density) { cellWidthPx.toDp() })
                        .height(40.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(AppColors.CardBg)
                )
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(itemSpacing)
            ) {
                items.forEach { item ->
                    ReaderModeIconButton(
                        icon = item.icon,
                        contentDescription = item.label,
                        isSelected = item.key == selectedKey,
                        enabled = enabled,
                        onClick = { onSelect(item.key) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun ReaderModeIconButton(
    icon: ImageVector,
    contentDescription: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Box(
        modifier = modifier
            .height(40.dp)
            .clickable(
                enabled = enabled,
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) { onClick() }
            .semantics {
                this.contentDescription = contentDescription
                this.selected = isSelected
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = when {
                !enabled -> LightTextSecondary.copy(alpha = 0.35f)
                isSelected -> AppColors.TextPrimary
                else -> LightTextSecondary
            },
            modifier = Modifier.size(22.dp)
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
    customFonts: List<com.huangder.lumibooks.domain.model.CustomFontPreset> = emptyList(),
    currentBackgroundSelection: String,
    customBackgrounds: List<ReaderBackgroundPreset>,
    currentPreserveEpubBackground: Boolean = true,
    showPreserveEpubBackground: Boolean = false,
    currentMarginLeft: Float,
    currentMarginRight: Float,
    currentMarginTop: Float,
    currentMarginBottom: Float,
    currentBgColor: Color,
    currentBackgroundImagePath: String?,
    currentTextColor: Color,
    currentTextColorOverride: Int?,
    currentFontSizeSp: Float,
    preservePublisherLayout: Boolean = false,
    currentWritingMode: ReaderWritingMode = ReaderWritingMode.HORIZONTAL,
    eInkModeEnabled: Boolean = false,
    fontDownloadKey: String? = null,
    fontDownloadFailed: Boolean = false,
    onLineHeightChange: (Float) -> Unit,
    onLetterSpacingChange: (Float) -> Unit,
    onFontTypeChange: (String) -> Unit,
    onImportFont: (android.net.Uri) -> Unit = {},
    onDeleteCustomFont: (String) -> Unit = {},
    onBackgroundSelect: (String) -> Unit,
    onAddBackgroundColor: (Int) -> Unit,
    onAddBackgroundImage: (Uri) -> Unit,
    onDeleteBackground: (String) -> Unit,
    onPreserveEpubBackgroundChange: (Boolean) -> Unit = {},
    onMarginLeftChange: (Float) -> Unit,
    onMarginRightChange: (Float) -> Unit,
    onMarginTopChange: (Float) -> Unit,
    onMarginBottomChange: (Float) -> Unit,
    currentParagraphSpacing: Float = 0f,
    currentFirstLineIndent: Float = 0f,
    onParagraphSpacingChange: (Float) -> Unit = {},
    onFirstLineIndentChange: (Float) -> Unit = {},
    readerTopLeftContent: ReaderCornerContent,
    readerTopRightContent: ReaderCornerContent,
    readerBottomLeftContent: ReaderCornerContent,
    readerBottomRightContent: ReaderCornerContent,
    volumeKeyPageTurnEnabled: Boolean = false,
    bionicReadingEnabled: Boolean = false,
    screenSleepTimeoutSeconds: Int = DataStoreManager.DEFAULT_SCREEN_SLEEP_TIMEOUT_SECONDS,
    readerEdgeTapMode: ReaderEdgeTapMode = ReaderEdgeTapMode.LEFT_PREVIOUS_RIGHT_NEXT,
    onReaderCornerContentChange: (ReaderPageCorner, ReaderCornerContent) -> Unit,
    onVolumeKeyPageTurnEnabledChange: (Boolean) -> Unit = {},
    onBionicReadingEnabledChange: (Boolean) -> Unit = {},
    onScreenSleepTimeoutChange: (Int) -> Unit = {},
    onReaderEdgeTapModeChange: (ReaderEdgeTapMode) -> Unit = {},
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
            if (eInkModeEnabled) sheetOffset.snapTo(0f) else sheetOffset.animateBottomSheetIn()
        }
    }

    var isClosing by remember { mutableStateOf(false) }
    val predictiveBackProgress = ConfigurableBottomSheetBackHandler { isClosing = true }

    // 监听 requestClose 状态，触发动画关闭
    LaunchedEffect(requestClose) {
        if (requestClose && !isClosing) {
            isClosing = true
        }
    }

    LaunchedEffect(isClosing) {
        if (isClosing) {
            if (eInkModeEnabled) sheetOffset.snapTo(1f) else sheetOffset.animateBottomSheetOut()
            onDismiss()
        }
    }

    // 预览文本用的字体（自定义字体从文件路径加载，与阅读页保持一致）
    val customPreviewFontFamily = remember(customFontPath) {
        if (customFontPath != null) {
            runCatching {
                val file = java.io.File(customFontPath)
                if (file.exists()) FontFamily(android.graphics.Typeface.createFromFile(file))
                else FontFamily.Default
            }.getOrDefault(FontFamily.Default)
        } else FontFamily.Default
    }
    val previewFont = when {
        currentFontType == "serif" -> androidx.compose.ui.text.font.FontFamily.Serif
        currentFontType == "fangsong" -> fangSongFamily()
        currentFontType == "kaiti" -> KaiTi
        currentFontType.startsWith("custom") -> customPreviewFontFamily
        else -> androidx.compose.ui.text.font.FontFamily.Default
    }
    val resolvedPreviewText = previewText.ifBlank { stringResource(R.string.preview_text) }
    val previewParagraphs = remember(resolvedPreviewText) {
        buildPreviewParagraphs(resolvedPreviewText)
    }
    val previewLeftPadding = currentMarginLeft.coerceIn(0f, 80f).dp
    val previewRightPadding = currentMarginRight.coerceIn(0f, 80f).dp
    val previewTopPadding = (currentMarginTop / 3f).coerceIn(0f, 40f).dp
    val previewBottomPadding = (currentMarginBottom / 3f).coerceIn(0f, 40f).dp
    val previewLineHeight = if (preservePublisherLayout) 1.5f else currentLineHeight
    val previewLetterSpacing = if (preservePublisherLayout) 0f else currentLetterSpacing
    val previewParagraphSpacing = if (preservePublisherLayout) 0f else currentParagraphSpacing
    val previewFirstLineIndent = if (preservePublisherLayout) 0f else currentFirstLineIndent
    val previewContext = LocalContext.current
    val verticalPreviewTypeface = remember(previewContext, currentFontType, customFontPath) {
        when {
            currentFontType == "serif" -> android.graphics.Typeface.SERIF
            currentFontType == "fangsong" -> DownloadedFonts.typeface(previewContext, "fangsong")
                ?: android.graphics.Typeface.DEFAULT
            currentFontType == "kaiti" -> androidx.core.content.res.ResourcesCompat.getFont(
                previewContext,
                R.font.lxgw_wenkai
            ) ?: android.graphics.Typeface.DEFAULT
            currentFontType.startsWith("custom") && customFontPath != null -> runCatching {
                android.graphics.Typeface.createFromFile(java.io.File(customFontPath))
            }.getOrDefault(android.graphics.Typeface.DEFAULT)
            else -> android.graphics.Typeface.DEFAULT
        }
    }

    LiquidGlassMenuHost(modifier = Modifier.fillMaxSize()) {
        // 遮罩
        Box(
            Modifier.fillMaxSize()
                .background(
                    AppColors.Scrim.copy(
                        alpha = 0.20f * (1f - sheetOffset.value.coerceIn(0f, 1f))
                    )
                )
                .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { isClosing = true }
        )

        // 底部弹出（90% 屏幕高度）
        LiquidGlassColumnSheetContainer(
            modifier = Modifier.align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(0.90f)
                .pointerInput(Unit) {
                    detectTapGestures(onTap = {})
                }
                .materialBottomSheetMotion(sheetOffset.value, predictiveBackProgress),
            contentModifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding(),
            fallbackColor = LightCardBg,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        ) {
            // 顶部预览区域：背景直接铺到容器顶部，操作按钮悬浮在预览之上。
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp)
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
                if (currentWritingMode.isVertical && !preservePublisherLayout) {
                    VerticalAdvancedPreview(
                        text = previewParagraphs.joinToString("\n"),
                        fontSizeSp = currentFontSizeSp,
                        textColor = currentTextColor,
                        typeface = verticalPreviewTypeface,
                        lineHeight = previewLineHeight,
                        letterSpacingDp = previewLetterSpacing,
                        paragraphSpacingDp = previewParagraphSpacing,
                        firstLineIndentCharacters = previewFirstLineIndent,
                        marginLeft = previewLeftPadding,
                        marginTop = 62.dp + previewTopPadding,
                        marginRight = previewRightPadding,
                        marginBottom = previewBottomPadding,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = 62.dp)
                            .padding(
                                start = previewLeftPadding,
                                top = previewTopPadding,
                                end = previewRightPadding,
                                bottom = previewBottomPadding
                            ),
                        verticalArrangement = Arrangement.spacedBy(
                            previewParagraphSpacing.coerceIn(0f, 30f).dp
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
                                    lineHeight = (currentFontSizeSp * previewLineHeight).sp,
                                    letterSpacing = previewLetterSpacing.sp,
                                    textIndent = androidx.compose.ui.text.style.TextIndent(
                                        firstLine = (currentFontSizeSp * previewFirstLineIndent).sp
                                    )
                                )
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LiquidGlassIconButton(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = stringResource(R.string.close),
                        onClick = { isClosing = true },
                        size = 44.dp,
                        iconSize = 20.dp,
                        contentColor = AppColors.TextPrimary,
                        normalContainerColor = LightBgGray.copy(alpha = 0.92f)
                    )
                    Spacer(Modifier.weight(1f))
                    LiquidGlassIconButton(
                        imageVector = Icons.Outlined.Check,
                        contentDescription = "确认",
                        onClick = { isClosing = true },
                        size = 44.dp,
                        iconSize = 20.dp,
                        contentColor = AppColors.OnAccent,
                        normalContainerColor = AppColors.Accent,
                        liquidContainerColor = AppColors.Accent,
                        liquidScrimColor = AppColors.Accent.copy(alpha = 0.72f)
                    )
                }
            }

            // 可滚动调节区
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(settingsScrollState)
                    .padding(start = 24.dp, top = 16.dp, end = 24.dp, bottom = 24.dp)
            ) {
                if (!eInkModeEnabled) {
                    AdvancedSettingsGroup(eInkModeEnabled) {
                        Text(
                            stringResource(R.string.reading_background),
                            fontSize = 14.sp,
                            color = LightTextSecondary
                        )
                        Spacer(Modifier.height(12.dp))
                        ReaderBackgroundSelector(
                            currentSelection = currentBackgroundSelection,
                            customBackgrounds = customBackgrounds,
                            onSelect = onBackgroundSelect,
                            onAddColor = onAddBackgroundColor,
                            onAddImage = onAddBackgroundImage,
                            onDelete = onDeleteBackground,
                            horizontalPadding = 0.dp
                        )
                        if (showPreserveEpubBackground) {
                            Spacer(Modifier.height(16.dp))
                            Row(
                                Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        stringResource(R.string.preserve_epub_background),
                                        fontSize = 14.sp,
                                        color = AppColors.TextPrimary
                                    )
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        stringResource(R.string.preserve_epub_background_hint),
                                        fontSize = 12.sp,
                                        color = LightTextSecondary
                                    )
                                }
                                LiquidGlassSwitch(
                                    checked = currentPreserveEpubBackground,
                                    onCheckedChange = onPreserveEpubBackgroundChange
                                )
                            }
                        }
                        Spacer(Modifier.height(18.dp))
                        TextColorSetting(
                            currentOverride = currentTextColorOverride,
                            effectiveTextColor = currentTextColor,
                            onColorChange = onTextColorChange
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                }

                if (!eInkModeEnabled) {
                    AdvancedSettingsGroup(eInkModeEnabled) {
                        AdvancedToggleRow(
                            title = stringResource(R.string.bionic_reading),
                            hint = stringResource(R.string.bionic_reading_hint),
                            checked = bionicReadingEnabled,
                            onCheckedChange = onBionicReadingEnabledChange
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                }

                if (!preservePublisherLayout) {
                AdvancedSettingsGroup(eInkModeEnabled) {
                    SettingSlider(stringResource(R.string.label_line_height), currentLineHeight, 1.0f..2.5f, 0.1f, { String.format("%.1fx", it) }, onLineHeightChange)
                    Spacer(Modifier.height(12.dp))
                    SettingSlider(stringResource(R.string.label_letter_spacing), currentLetterSpacing, 0f..10f, 0.5f, { String.format("%.1f sp", it) }, onLetterSpacingChange)
                    Spacer(Modifier.height(12.dp))
                    SettingSlider(
                        stringResource(R.string.label_paragraph_spacing),
                        currentParagraphSpacing,
                        0f..30f,
                        0.5f,
                        {
                            if (it % 1f == 0f) "${it.toInt()} dp"
                            else String.format("%.1f dp", it)
                        },
                        onParagraphSpacingChange
                    )
                    Spacer(Modifier.height(12.dp))
                    SettingSlider(stringResource(R.string.label_first_line_indent), currentFirstLineIndent, 0f..4f, 0.5f, { "${it} 字符" }, onFirstLineIndentChange)
                }
                Spacer(Modifier.height(12.dp))

                }
                AdvancedSettingsGroup(eInkModeEnabled) {
                    SettingSlider(stringResource(R.string.label_margin_top), currentMarginTop, 0f..120f, 2f, { "${it.toInt()} dp" }, onMarginTopChange)
                    Spacer(Modifier.height(12.dp))
                    SettingSlider(stringResource(R.string.label_margin_bottom), currentMarginBottom, 0f..120f, 2f, { "${it.toInt()} dp" }, onMarginBottomChange)
                    Spacer(Modifier.height(12.dp))
                    SettingSlider(stringResource(R.string.label_margin_left), currentMarginLeft, 0f..80f, 2f, { "${it.toInt()} dp" }, onMarginLeftChange)
                    Spacer(Modifier.height(12.dp))
                    SettingSlider(stringResource(R.string.label_margin_right), currentMarginRight, 0f..80f, 2f, { "${it.toInt()} dp" }, onMarginRightChange)
                }
                Spacer(Modifier.height(12.dp))

                AdvancedSettingsGroup(eInkModeEnabled) {
                    if (!eInkModeEnabled) {
                        ReaderCornerLayoutSettings(
                            topLeft = readerTopLeftContent,
                            topRight = readerTopRightContent,
                            bottomLeft = readerBottomLeftContent,
                            bottomRight = readerBottomRightContent,
                            forceSolidMenus = preservePublisherLayout,
                            onContentChange = onReaderCornerContentChange
                        )
                        Spacer(Modifier.height(16.dp))
                    }
                    ReaderEdgeTapModeSetting(
                        selected = readerEdgeTapMode,
                        forceSolidMenu = preservePublisherLayout,
                        onSelected = onReaderEdgeTapModeChange
                    )
                    Spacer(Modifier.height(16.dp))
                    ScreenSleepTimeoutSetting(
                        selectedSeconds = screenSleepTimeoutSeconds,
                        forceSolidMenu = preservePublisherLayout,
                        onSelected = onScreenSleepTimeoutChange
                    )
                    Spacer(Modifier.height(16.dp))
                    AdvancedToggleRow(
                        title = stringResource(R.string.volume_key_page_turn),
                        hint = stringResource(R.string.volume_key_page_turn_hint),
                        checked = volumeKeyPageTurnEnabled,
                        onCheckedChange = onVolumeKeyPageTurnEnabledChange
                    )
                }
                Spacer(Modifier.height(12.dp))

                AdvancedSettingsGroup(eInkModeEnabled) {
                    Text(stringResource(R.string.font_label), fontSize = 14.sp, color = LightTextSecondary)
                    Spacer(Modifier.height(12.dp))
                    FontSelector(
                        currentFont = currentFontType,
                        customFontPath = customFontPath,
                        customFonts = customFonts,
                        onFontChange = onFontTypeChange,
                        onImportFont = onImportFont,
                        onDeleteCustomFont = onDeleteCustomFont,
                        usePublisherFontLabel = preservePublisherLayout,
                        downloadingKey = fontDownloadKey,
                        fontDownloadFailed = fontDownloadFailed
                    )
                }
                Spacer(Modifier.height(16.dp))

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

@Composable
private fun AdvancedSettingsGroup(
    eInkModeEnabled: Boolean,
    content: @Composable ColumnScope.() -> Unit
) {
    val isLiquidGlass = LocalAppTheme.current == "liquid_glass" && !eInkModeEnabled
    val isDark = LocalIsDarkTheme.current
    val transparency = LocalLiquidGlassTransparency.current
    val shape = RoundedCornerShape(16.dp)
    val surfaceAlpha = if (isDark) {
        0.43f - transparency * 0.12f
    } else {
        0.59f - transparency * 0.18f
    }
    val groupModifier = if (isLiquidGlass) {
        Modifier
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        LightCardBg.copy(alpha = (surfaceAlpha + 0.06f).coerceAtMost(0.62f)),
                        LightCardBg.copy(alpha = surfaceAlpha)
                    )
                )
            )
            .border(
                width = 0.7.dp,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = if (isDark) 0.24f else 0.72f),
                        Color.White.copy(alpha = if (isDark) 0.08f else 0.20f)
                    )
                ),
                shape = shape
            )
            .padding(16.dp)
    } else {
        Modifier
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(groupModifier),
        content = content
    )
}

@Composable
private fun AdvancedToggleRow(
    title: String,
    hint: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp, color = AppColors.TextPrimary)
            Spacer(Modifier.height(2.dp))
            Text(hint, fontSize = 12.sp, color = LightTextSecondary)
        }
        LiquidGlassSwitch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
private fun ReaderCornerLayoutSettings(
    topLeft: ReaderCornerContent,
    topRight: ReaderCornerContent,
    bottomLeft: ReaderCornerContent,
    bottomRight: ReaderCornerContent,
    forceSolidMenus: Boolean,
    onContentChange: (ReaderPageCorner, ReaderCornerContent) -> Unit
) {
    Text(
        text = stringResource(R.string.reader_page_layout),
        fontSize = 14.sp,
        color = AppColors.TextPrimary,
        fontWeight = FontWeight.SemiBold
    )
    Spacer(Modifier.height(2.dp))
    Text(
        text = stringResource(R.string.reader_page_layout_hint),
        fontSize = 12.sp,
        color = LightTextSecondary
    )
    Spacer(Modifier.height(8.dp))

    ReaderCornerSelectionRow(
        label = stringResource(R.string.reader_corner_top_left),
        selected = topLeft,
        forceSolidMenu = forceSolidMenus,
        onSelected = { onContentChange(ReaderPageCorner.TOP_LEFT, it) }
    )
    ReaderCornerSelectionRow(
        label = stringResource(R.string.reader_corner_top_right),
        selected = topRight,
        forceSolidMenu = forceSolidMenus,
        onSelected = { onContentChange(ReaderPageCorner.TOP_RIGHT, it) }
    )
    ReaderCornerSelectionRow(
        label = stringResource(R.string.reader_corner_bottom_left),
        selected = bottomLeft,
        forceSolidMenu = forceSolidMenus,
        onSelected = { onContentChange(ReaderPageCorner.BOTTOM_LEFT, it) }
    )
    ReaderCornerSelectionRow(
        label = stringResource(R.string.reader_corner_bottom_right),
        selected = bottomRight,
        forceSolidMenu = forceSolidMenus,
        onSelected = { onContentChange(ReaderPageCorner.BOTTOM_RIGHT, it) }
    )
}

@Composable
private fun ReaderCornerSelectionRow(
    label: String,
    selected: ReaderCornerContent,
    forceSolidMenu: Boolean,
    onSelected: (ReaderCornerContent) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var menuAnchorBounds by remember { mutableStateOf(Rect.Zero) }
    val options = ReaderCornerContent.entries
    val labeledOptions = options.map { option -> option to readerCornerContentLabel(option) }
    val useLiquidGlassMenu = LocalAppTheme.current == "liquid_glass" && !forceSolidMenu
    val liquidMenuHost = LocalLiquidGlassMenuHost.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 13.sp, color = AppColors.TextPrimary, modifier = Modifier.weight(1f))
        Box {
            Box(
                modifier = Modifier
                    .width(158.dp)
                    .height(36.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(LightBgGray)
                    .onGloballyPositioned { menuAnchorBounds = it.boundsInRoot() }
                    .clickable {
                        if (useLiquidGlassMenu && liquidMenuHost != null && menuAnchorBounds != Rect.Zero) {
                            liquidMenuHost.show(
                                LiquidGlassMenuSpec(
                                    anchorBounds = menuAnchorBounds,
                                    width = 158.dp,
                                    items = labeledOptions.map { (option, optionLabel) ->
                                        LiquidGlassMenuItem(
                                            label = optionLabel,
                                            selected = option == selected,
                                            onClick = { onSelected(option) }
                                        )
                                    }
                                )
                            )
                        } else {
                            expanded = true
                        }
                    }
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = readerCornerContentLabel(selected),
                    fontSize = 12.sp,
                    color = AppColors.TextPrimary,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier.fillMaxWidth()
                )
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = LightTextSecondary,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .size(16.dp)
                )
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                shape = RoundedCornerShape(18.dp),
                containerColor = LightCardBg,
                tonalElevation = 0.dp,
                shadowElevation = 0.dp
            ) {
                labeledOptions.forEach { (option, optionLabel) ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = optionLabel,
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        },
                        onClick = {
                            expanded = false
                            onSelected(option)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ReaderEdgeTapModeSetting(
    selected: ReaderEdgeTapMode,
    forceSolidMenu: Boolean,
    onSelected: (ReaderEdgeTapMode) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var menuAnchorBounds by remember { mutableStateOf(Rect.Zero) }
    val labeledOptions = ReaderEdgeTapMode.entries.map { mode ->
        mode to readerEdgeTapModeLabel(mode)
    }
    val useLiquidGlassMenu = LocalAppTheme.current == "liquid_glass" && !forceSolidMenu
    val liquidMenuHost = LocalLiquidGlassMenuHost.current

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp)
        ) {
            Text(
                text = stringResource(R.string.reader_edge_tap_page_turn),
                fontSize = 14.sp,
                color = AppColors.TextPrimary
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = stringResource(R.string.reader_edge_tap_page_turn_hint),
                fontSize = 12.sp,
                color = LightTextSecondary
            )
        }

        Box {
            Box(
                modifier = Modifier
                    .width(158.dp)
                    .height(38.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(LightBgGray)
                    .onGloballyPositioned { menuAnchorBounds = it.boundsInRoot() }
                    .clickable {
                        if (useLiquidGlassMenu && liquidMenuHost != null && menuAnchorBounds != Rect.Zero) {
                            liquidMenuHost.show(
                                LiquidGlassMenuSpec(
                                    anchorBounds = menuAnchorBounds,
                                    width = 158.dp,
                                    items = labeledOptions.map { (mode, label) ->
                                        LiquidGlassMenuItem(
                                            label = label,
                                            selected = mode == selected,
                                            onClick = { onSelected(mode) }
                                        )
                                    }
                                )
                            )
                        } else {
                            expanded = true
                        }
                    }
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = readerEdgeTapModeLabel(selected),
                    fontSize = 12.sp,
                    color = AppColors.TextPrimary,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(end = 16.dp)
                )
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = LightTextSecondary,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .size(16.dp)
                )
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                shape = RoundedCornerShape(18.dp),
                containerColor = LightCardBg,
                tonalElevation = 0.dp,
                shadowElevation = 0.dp
            ) {
                labeledOptions.forEach { (mode, label) ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = label,
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        },
                        onClick = {
                            expanded = false
                            onSelected(mode)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ScreenSleepTimeoutSetting(
    selectedSeconds: Int,
    forceSolidMenu: Boolean,
    onSelected: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var menuAnchorBounds by remember { mutableStateOf(Rect.Zero) }
    val options = DataStoreManager.SCREEN_SLEEP_TIMEOUT_SECONDS_OPTIONS
    val labeledOptions = options.map { seconds -> seconds to screenSleepTimeoutLabel(seconds) }
    val useLiquidGlassMenu = LocalAppTheme.current == "liquid_glass" && !forceSolidMenu
    val liquidMenuHost = LocalLiquidGlassMenuHost.current

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp)
        ) {
            Text(
                text = stringResource(R.string.screen_sleep_timeout),
                fontSize = 14.sp,
                color = AppColors.TextPrimary
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = stringResource(R.string.screen_sleep_timeout_hint),
                fontSize = 11.sp,
                color = LightTextSecondary.copy(alpha = 0.72f)
            )
        }

        Box {
            Box(
                modifier = Modifier
                    .width(128.dp)
                    .height(38.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(LightBgGray)
                    .onGloballyPositioned { menuAnchorBounds = it.boundsInRoot() }
                    .clickable {
                        if (useLiquidGlassMenu && liquidMenuHost != null && menuAnchorBounds != Rect.Zero) {
                            liquidMenuHost.show(
                                LiquidGlassMenuSpec(
                                    anchorBounds = menuAnchorBounds,
                                    width = 128.dp,
                                    items = labeledOptions.map { (seconds, label) ->
                                        LiquidGlassMenuItem(
                                            label = label,
                                            selected = seconds == selectedSeconds,
                                            onClick = { onSelected(seconds) }
                                        )
                                    }
                                )
                            )
                        } else {
                            expanded = true
                        }
                    }
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = screenSleepTimeoutLabel(selectedSeconds),
                    fontSize = 12.sp,
                    color = AppColors.TextPrimary,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(end = 16.dp)
                )
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = LightTextSecondary,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .size(16.dp)
                )
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                shape = RoundedCornerShape(18.dp),
                containerColor = LightCardBg,
                tonalElevation = 0.dp,
                shadowElevation = 0.dp
            ) {
                labeledOptions.forEach { (seconds, label) ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = label,
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        },
                        onClick = {
                            expanded = false
                            onSelected(seconds)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun screenSleepTimeoutLabel(seconds: Int): String = when {
    seconds == DataStoreManager.SCREEN_SLEEP_TIMEOUT_FOLLOW_SYSTEM ->
        stringResource(R.string.screen_sleep_timeout_follow_system)
    seconds < 60 -> stringResource(R.string.time_seconds, seconds)
    else -> stringResource(R.string.time_minutes, seconds / 60)
}

@Composable
private fun readerEdgeTapModeLabel(mode: ReaderEdgeTapMode): String = stringResource(
    when (mode) {
        ReaderEdgeTapMode.LEFT_PREVIOUS_RIGHT_NEXT -> R.string.reader_edge_tap_left_previous_right_next
        ReaderEdgeTapMode.LEFT_NEXT_RIGHT_PREVIOUS -> R.string.reader_edge_tap_left_next_right_previous
        ReaderEdgeTapMode.BOTH_PREVIOUS -> R.string.reader_edge_tap_both_previous
        ReaderEdgeTapMode.BOTH_NEXT -> R.string.reader_edge_tap_both_next
    }
)

@Composable
private fun readerCornerContentLabel(content: ReaderCornerContent): String = stringResource(
    when (content) {
        ReaderCornerContent.NONE -> R.string.reader_corner_content_none
        ReaderCornerContent.CHAPTER_INFO -> R.string.reader_corner_content_chapter
        ReaderCornerContent.BOOK_PROGRESS -> R.string.reader_corner_content_book_progress
        ReaderCornerContent.PAGE_NUMBER -> R.string.reader_corner_content_page_number
        ReaderCornerContent.BATTERY -> R.string.reader_corner_content_battery
        ReaderCornerContent.TIME -> R.string.reader_corner_content_time
    }
)

private fun buildPreviewParagraphs(text: String): List<String> {
    val lines = text.lineSequence().map(String::trim).filter(String::isNotEmpty).toList()
    if (lines.size >= 2) return lines.take(3)
    val compact = lines.firstOrNull().orEmpty()
    if (compact.isBlank()) return listOf(text)
    val chunkSize = (compact.length / 3).coerceIn(24, 48)
    return compact.chunked(chunkSize).take(3)
}

@Composable
private fun VerticalAdvancedPreview(
    text: String,
    fontSizeSp: Float,
    textColor: Color,
    typeface: android.graphics.Typeface,
    lineHeight: Float,
    letterSpacingDp: Float,
    paragraphSpacingDp: Float,
    firstLineIndentCharacters: Float,
    marginLeft: androidx.compose.ui.unit.Dp,
    marginTop: androidx.compose.ui.unit.Dp,
    marginRight: androidx.compose.ui.unit.Dp,
    marginBottom: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val previewView = remember(context) {
        com.huangder.lumibooks.ui.reader.engine.PageContentView(context).apply {
            setReaderBackground(android.graphics.Color.TRANSPARENT, null)
        }
    }
    var previewSize by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }

    val fontSizePx = fontSizeSp * density.density
    val letterSpacingPx = with(density) { letterSpacingDp.dp.toPx() }
    val paragraphSpacingPx = with(density) { paragraphSpacingDp.dp.toPx() }
    val marginLeftPx = with(density) { marginLeft.toPx() }
    val marginTopPx = with(density) { marginTop.toPx() }
    val marginRightPx = with(density) { marginRight.toPx() }
    val marginBottomPx = with(density) { marginBottom.toPx() }
    val lineSpacingExtraPx = with(density) { 2.5.dp.toPx() }
    val textColorArgb = textColor.toArgb()

    LaunchedEffect(
        previewView,
        previewSize,
        text,
        fontSizePx,
        textColorArgb,
        typeface,
        lineHeight,
        letterSpacingPx,
        paragraphSpacingPx,
        firstLineIndentCharacters,
        marginLeftPx,
        marginTopPx,
        marginRightPx,
        marginBottomPx
    ) {
        if (previewSize.width <= 0 || previewSize.height <= 0) return@LaunchedEffect
        val formatted = com.huangder.lumibooks.ui.reader.engine.ReaderParagraphFormatter.applyFirstLineIndent(
            text = text,
            indentCharacters = firstLineIndentCharacters,
            textSizePx = fontSizePx,
            paragraphSpacingPx = paragraphSpacingPx,
            skipFirstNonEmptyParagraph = false
        )
        val contentWidth = (previewSize.width - marginLeftPx - marginRightPx).toInt().coerceAtLeast(1)
        val contentHeight = (previewSize.height - marginTopPx - marginBottomPx).toInt().coerceAtLeast(1)
        val paint = android.text.TextPaint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            textSize = fontSizePx
            color = textColorArgb
            this.typeface = typeface
            this.density = density.density
            isSubpixelText = true
        }
        val page = com.huangder.lumibooks.ui.reader.engine.VerticalTextLayouter.layout(
            text = formatted,
            paint = paint,
            width = contentWidth,
            height = contentHeight,
            lineSpacingExtra = lineSpacingExtraPx,
            lineSpacingMultiplier = lineHeight,
            letterSpacing = letterSpacingPx
        ).firstOrNull()

        previewView.configure(
            fontSizePx = fontSizePx,
            textColor = textColorArgb,
            lineHeightMult = lineHeight,
            lineSpacingExtraPx = lineSpacingExtraPx,
            letterSpacingPx = letterSpacingPx,
            typeface = typeface,
            marginLeftPx = marginLeftPx,
            marginTopPx = marginTopPx,
            marginRightPx = marginRightPx,
            marginBottomPx = marginBottomPx,
            writingMode = ReaderWritingMode.VERTICAL_RL
        )
        if (page == null) {
            previewView.setPageContent("", 0, 0)
        } else {
            previewView.setPageContent(
                fullText = formatted,
                startChar = page.startOffset,
                endChar = page.endOffset,
                verticalGeometry = page.geometry
            )
        }
    }

    AndroidView(
        factory = { previewView },
        modifier = modifier.onSizeChanged { previewSize = it }
    )
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
        Text(stringResource(R.string.label_text_color), fontSize = 14.sp, color = AppColors.TextPrimary)
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
            color = currentOverride?.takeIf { isCustomColor }?.let(::Color) ?: LightBgGray,
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
    val dialogTransparency = (LocalLiquidGlassTransparency.current - 0.10f)
        .coerceIn(0f, 0.90f)
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

    LiquidGlassDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        transparencyOverride = dialogTransparency
    ) {
            Column(Modifier.padding(horizontal = 28.dp, vertical = 20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.text_color_custom),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = AppColors.TextPrimary
                    )
                    Spacer(Modifier.weight(1f))
                    LiquidGlassTextButton(
                        text = stringResource(R.string.cancel),
                        onClick = onDismiss,
                        contentColor = LightTextSecondary
                    )
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
                LiquidGlassTextButton(
                    text = stringResource(R.string.apply_text_color),
                    onClick = { onApply(previewColor) },
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                    tintedColor = AccentColor
                )
            }
    }
}

/**
 * 点击数值弹出的精细输入对话框，适配液态玻璃主题。
 *
 * @param label      滑块名称（如"字号"）
 * @param value      当前值
 * @param range      合法范围
 * @param step       步长，用于输入校验（不强制但会提示）
 * @param format     格式化函数，用于显示单位（如 "18 sp"）
 * @param onConfirm  确认后的回调，返回 coerce 到范围内的值
 * @param onDismiss  关闭对话框
 */
@Composable
private fun SliderValueInputDialog(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    step: Float,
    format: (Float) -> String,
    onConfirm: (Float) -> Unit,
    onDismiss: () -> Unit
) {
    val dialogTransparency = (LocalLiquidGlassTransparency.current - 0.10f).coerceIn(0f, 0.90f)
    val focusRequester = remember { FocusRequester() }

    val initialText = remember(value) {
        // 去掉小数尾零：1.0 → "1"，1.5 → "1.5"
        if (value == value.toLong().toFloat()) value.toLong().toString()
        else value.toString()
    }
    var textFieldValue by remember {
        mutableStateOf(TextFieldValue(initialText, selection = TextRange(0, initialText.length)))
    }

    val parsedFloat = textFieldValue.text.toFloatOrNull()
    val isValid = parsedFloat != null && parsedFloat >= range.start && parsedFloat <= range.endInclusive

    val confirm = {
        val v = textFieldValue.text.toFloatOrNull()?.coerceIn(range.start, range.endInclusive)
        if (v != null) { onConfirm(v); onDismiss() }
    }

    LiquidGlassDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        transparencyOverride = dialogTransparency
    ) {
        Column(Modifier.padding(horizontal = 28.dp, vertical = 20.dp)) {
            // 标题行
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = label,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppColors.TextPrimary
                )
                Spacer(Modifier.weight(1f))
                // 范围提示
                Text(
                    text = "${format(range.start)} ~ ${format(range.endInclusive)}",
                    fontSize = 12.sp,
                    color = LightTextSecondary
                )
            }

            Spacer(Modifier.height(20.dp))

            // 输入框
            val borderColor = when {
                textFieldValue.text.isEmpty() -> LightTextSecondary.copy(alpha = 0.3f)
                isValid -> AppColors.TextPrimary.copy(alpha = 0.4f)
                else -> Color(0xFFFF3B30)
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(LightBgGray)
                    .border(1.5.dp, borderColor, RoundedCornerShape(16.dp))
                    .padding(vertical = 16.dp, horizontal = 20.dp),
                contentAlignment = Alignment.Center
            ) {
                BasicTextField(
                    value = textFieldValue,
                    onValueChange = { textFieldValue = it },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(onDone = { confirm() }),
                    textStyle = TextStyle(
                        fontSize = 32.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isValid || textFieldValue.text.isEmpty()) AppColors.TextPrimary
                                else Color(0xFFFF3B30),
                        textAlign = TextAlign.Center
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                    decorationBox = { inner ->
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            if (textFieldValue.text.isEmpty()) {
                                Text(
                                    format(value),
                                    fontSize = 32.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = LightTextSecondary,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            inner()
                        }
                    }
                )
            }

            Spacer(Modifier.height(20.dp))

            // 底部按钮
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LiquidGlassTextButton(
                    text = stringResource(R.string.cancel),
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f).height(44.dp),
                    contentColor = LightTextSecondary
                )
                LiquidGlassTextButton(
                    text = stringResource(R.string.confirm),
                    onClick = confirm,
                    enabled = isValid,
                    modifier = Modifier.weight(1f).height(44.dp),
                    tintedColor = if (isValid) AccentColor else LightTextSecondary
                )
            }
        }
    }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }
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
    var showInputDialog by remember { mutableStateOf(false) }

    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 14.sp, color = AppColors.TextPrimary)
        Spacer(Modifier.weight(1f))
        // 点击数值弹出精细输入对话框
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) { showInputDialog = true }
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Text(
                text = format(sliderValue),
                fontSize = 14.sp,
                color = AppColors.TextPrimary,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
    Spacer(Modifier.height(4.dp))
    com.huangder.lumibooks.ui.components.PillSlider(
        value = sliderValue,
        onValueChange = { sliderValue = it; onChange(it) },
        valueRange = range,
        step = step,
        onDragValueChange = { sliderValue = it }
    )

    if (showInputDialog) {
        SliderValueInputDialog(
            label = label,
            value = sliderValue,
            range = range,
            step = step,
            format = format,
            onConfirm = { newVal ->
                sliderValue = newVal
                onChange(newVal)
            },
            onDismiss = { showInputDialog = false }
        )
    }
}

// FontSelector 用的条目类型（sealed interface 不能是 local，放到文件级）
private sealed interface FontSelectorItem {
    data class Fixed(val key: String, val staticLabel: String, val family: FontFamily) : FontSelectorItem
    data class Custom(val preset: com.huangder.lumibooks.domain.model.CustomFontPreset, val index: Int) : FontSelectorItem
    data object AddButton : FontSelectorItem
}

@Composable
private fun FontSelector(
    currentFont: String,
    customFontPath: String? = null,
    customFonts: List<com.huangder.lumibooks.domain.model.CustomFontPreset> = emptyList(),
    onFontChange: (String) -> Unit,
    onImportFont: (android.net.Uri) -> Unit = {},
    onDeleteCustomFont: (String) -> Unit = {},
    usePublisherFontLabel: Boolean = false,
    downloadingKey: String? = null,
    fontDownloadFailed: Boolean = false
) {
    val launcher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) onImportFont(uri)
    }
    var deleteArmedId by remember { mutableStateOf<String?>(null) }

    val sysLabel = stringResource(if (usePublisherFontLabel) R.string.font_publisher else R.string.font_system)
    val fangLabel = stringResource(R.string.font_fangsong)
    val kaiLabel  = stringResource(R.string.font_kaiti)
    val addLabel  = stringResource(R.string.font_import)
    val downloadingLabel = stringResource(R.string.font_downloading)
    val failedLabel = stringResource(R.string.font_download_failed)
    val fangSongFamilyValue = fangSongFamily()

    val items = remember(customFonts, sysLabel, fangLabel, kaiLabel, fangSongFamilyValue) {
        buildList<FontSelectorItem> {
            add(FontSelectorItem.Fixed("system",   sysLabel,  FontFamily.Default))
            add(FontSelectorItem.Fixed("serif",    "Serif",   FontFamily.Serif))
            add(FontSelectorItem.Fixed("fangsong", fangLabel, fangSongFamilyValue))
            add(FontSelectorItem.Fixed("kaiti",    kaiLabel,  KaiTi))
            customFonts.forEachIndexed { i, p -> add(FontSelectorItem.Custom(p, i)) }
            add(FontSelectorItem.AddButton)
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items.chunked(3).forEach { rowItems ->
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                rowItems.forEach { item ->
                    when (item) {
                        is FontSelectorItem.Fixed -> {
                            val isFangSong = item.key == "fangsong"
                            val label = when {
                                isFangSong && downloadingKey == "fangsong" -> downloadingLabel
                                isFangSong && fontDownloadFailed -> failedLabel
                                else -> item.staticLabel
                            }
                            FontButton(
                                label = label,
                                isSelected = currentFont == item.key,
                                onClick = { onFontChange(item.key) },
                                fontFamily = item.family,
                                enabled = !(isFangSong && downloadingKey == "fangsong"),
                                modifier = Modifier.weight(1f)
                            )
                        }
                        is FontSelectorItem.Custom -> {
                            val preset = item.preset
                            val fontFamily = remember(preset.path) {
                                runCatching {
                                    val f = java.io.File(preset.path)
                                    if (f.exists()) FontFamily(android.graphics.Typeface.createFromFile(f))
                                    else FontFamily.Default
                                }.getOrDefault(FontFamily.Default)
                            }
                            val isSelected = currentFont == preset.fontTypeKey
                            val isDeleteArmed = deleteArmedId == preset.id
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .then(
                                        if (isSelected) Modifier.border(2.dp, AccentColor, RoundedCornerShape(12.dp))
                                        else Modifier.border(1.dp, LightTextSecondary, RoundedCornerShape(12.dp))
                                    )
                                    .background(AppColors.CardBg)
                                    .pointerInput(preset.id, isDeleteArmed) {
                                        detectTapGestures(
                                            onTap = {
                                                if (isDeleteArmed) {
                                                    onDeleteCustomFont(preset.id)
                                                    deleteArmedId = null
                                                } else {
                                                    deleteArmedId = null
                                                    onFontChange(preset.fontTypeKey)
                                                }
                                            },
                                            onLongPress = { deleteArmedId = preset.id }
                                        )
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(preset.displayName(item.index), fontSize = 14.sp, fontFamily = fontFamily,
                                    color = if (isSelected) AccentColor else LightTextSecondary)
                                if (isDeleteArmed) {
                                    Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = 0.48f)),
                                        contentAlignment = Alignment.Center) {
                                        Icon(Icons.Outlined.Delete, null, tint = Color.White, modifier = Modifier.size(20.dp))
                                    }
                                }
                            }
                        }
                        FontSelectorItem.AddButton -> Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .border(1.dp, LightTextSecondary, RoundedCornerShape(12.dp))
                                .background(AppColors.CardBg)
                                .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {
                                    deleteArmedId = null
                                    launcher.launch(arrayOf("font/ttf", "font/otf", "application/octet-stream"))
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Outlined.Add, addLabel, tint = LightTextSecondary, modifier = Modifier.size(20.dp))
                        }
                    }
                }
                // 如果这行不足 3 个，补 Spacer 占位
                repeat(3 - rowItems.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun FontButton(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    fontFamily: FontFamily = FontFamily.Default,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Box(
        modifier = modifier
            .height(48.dp)
            .clip(RoundedCornerShape(12.dp))
            .then(
                if (isSelected) {
                    Modifier.border(1.dp, AppColors.TextPrimary, RoundedCornerShape(12.dp))
                } else {
                    Modifier
                }
            )
            .background(LightBgGray)
            .clickable(
                enabled = enabled,
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            fontSize = 14.sp,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            fontFamily = fontFamily,
            color = AppColors.TextPrimary
        )
    }
}

private data class FontOption(val key: String, val label: String, val family: androidx.compose.ui.text.font.FontFamily)
