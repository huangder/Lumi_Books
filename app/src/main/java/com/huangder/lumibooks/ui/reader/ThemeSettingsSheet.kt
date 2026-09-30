package com.huangder.lumibooks.ui.reader

import com.huangder.lumibooks.ui.components.liquidGlassMenuAnchor
import com.huangder.lumibooks.ui.icons.AppIcons
import com.huangder.lumibooks.ui.icons.IconPair

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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.blur
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
import android.provider.OpenableColumns
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import androidx.core.graphics.ColorUtils
import com.huangder.lumibooks.ui.theme.fangSongFamily
import com.huangder.lumibooks.ui.theme.KaiTi
import com.huangder.lumibooks.ui.theme.AppColors
import com.huangder.lumibooks.ui.theme.LocalAppTheme
import com.huangder.lumibooks.ui.theme.LocalEInkMode
import com.huangder.lumibooks.ui.theme.LocalIsDarkTheme
import com.huangder.lumibooks.ui.theme.LocalIsDarkTheme
import com.huangder.lumibooks.ui.theme.LocalLiquidGlassTransparency
import com.huangder.lumibooks.ui.theme.resolveAppFontFamily
import com.huangder.lumibooks.R
import com.huangder.lumibooks.data.local.DataStoreManager
import com.huangder.lumibooks.domain.model.ReaderBackgroundPreset
import com.huangder.lumibooks.ui.reader.engine.resolveReaderTypeface
import com.huangder.lumibooks.util.epub.EpubRenderMode
import com.huangder.lumibooks.domain.model.ReaderBackgroundType
import com.huangder.lumibooks.domain.model.ReaderCornerContent
import com.huangder.lumibooks.domain.model.ReaderCornerMargins
import com.huangder.lumibooks.domain.model.ReaderEdgeTapMode
import com.huangder.lumibooks.domain.model.ReaderTextAlignment
import com.huangder.lumibooks.domain.model.ReaderWritingMode
import com.huangder.lumibooks.domain.model.ReaderPageCorner
import com.huangder.lumibooks.domain.model.CustomFontPreset
import com.huangder.lumibooks.domain.model.ReaderThemeSuite
import com.huangder.lumibooks.domain.model.ReaderThemeSuites
import com.huangder.lumibooks.domain.model.ReaderThemeSettings
import com.huangder.lumibooks.domain.model.ReaderLayoutTarget
import com.huangder.lumibooks.domain.model.resolveImageSource
import com.huangder.lumibooks.domain.model.normalizeReaderThemeSuiteName
import com.huangder.lumibooks.domain.model.readerThemeSuiteNameCodePointCount
import com.huangder.lumibooks.ui.components.ConfigurableBottomSheetBackHandler
import com.huangder.lumibooks.ui.components.LiquidGlassSurface
import com.huangder.lumibooks.ui.components.LocalLiquidGlassBackdrop
import com.huangder.lumibooks.ui.components.LiquidGlassButton
import com.huangder.lumibooks.ui.components.LiquidGlassDialog
import com.huangder.lumibooks.ui.components.LiquidGlassAlertDialog
import com.huangder.lumibooks.ui.components.LiquidGlassIconButton
import com.huangder.lumibooks.ui.components.LiquidGlassTextButton
import com.huangder.lumibooks.ui.components.LiquidGlassSwitch
import com.huangder.lumibooks.ui.components.LiquidGlassSegmentedControl
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
    currentFontType: String = "system",
    currentCustomFontPath: String? = null,
    currentBodyFontWeight: Int = 400,
    onBodyFontWeightChange: (Int) -> Unit = {},
    currentTheme: String,
    currentBackgroundSelection: String = currentTheme,
    customBackgrounds: List<ReaderBackgroundPreset> = emptyList(),
    readerThemeSuites: List<ReaderThemeSuite> = ReaderThemeSuites.defaults(),
    activeReaderThemeSuiteId: String = ReaderThemeSuites.DAY_ID,
    isAppDark: Boolean = LocalIsDarkTheme.current,
    readerThemeSuiteBookScoped: Boolean = false,
    readerButtonContrastEnabled: Boolean = false,
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
    editingDark: Boolean = isAppDark,
    eInkModeEnabled: Boolean = false,
    onFontSizeChange: (Float) -> Unit,
    onThemeChange: (String) -> Unit,
    onModeThemeChange: (Boolean, String) -> Unit = { _, value -> onThemeChange(value) },
    onModeChange: (Boolean) -> Unit = {},
    onExportThemeBundle: (String) -> Unit = {},
    onImportThemeBundle: () -> Unit = {},
    onBackgroundSelect: (String) -> Unit = onThemeChange,
    onAddBackgroundColor: (Int, String) -> Unit = { _, _ -> },
    onAddBackgroundImage: (Uri, String) -> Unit = { _, _ -> },
    onDeleteBackground: (String) -> Unit = {},
    onThemeSuiteSelect: (String) -> Unit = {},
    onThemeSuiteCreate: (String) -> Unit = {},
    onThemeSuiteDelete: (String) -> Unit = {},
    onThemeSuitesReorder: (List<String>) -> Unit = {},
    onThemeSuiteBookScopedChange: (Boolean) -> Unit = {},
    onReaderButtonContrastChange: (Boolean) -> Unit = {},
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

    val fontContext = LocalContext.current
    val variableWeightRange by androidx.compose.runtime.produceState<ClosedFloatingPointRange<Float>?>(
        null, currentFontType, currentCustomFontPath
    ) {
        value = null
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            com.huangder.lumibooks.ui.reader.engine.readerVariableWeightRange(
                fontContext, currentFontType, currentCustomFontPath)
        }
    }

    val sheetOffset = remember { Animatable(1f) }

    LaunchedEffect(visible) {
        if (visible) {
            sheetOffset.snapTo(1f)
            if (eInkModeEnabled) sheetOffset.snapTo(0f) else sheetOffset.animateBottomSheetIn()
        }
    }

    var isClosing by remember { mutableStateOf(false) }
    var showExportConfirm by remember { mutableStateOf(false) }
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
    val isDark = isAppDark
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
                    imageVector = AppIcons.X,
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

            variableWeightRange?.let { range ->
                var previewWeight by remember(currentFontType, currentCustomFontPath,
                    currentBodyFontWeight) { mutableIntStateOf(currentBodyFontWeight) }
                Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.body_font_weight), fontSize = 14.sp, color = LightTextSecondary)
                    Spacer(Modifier.weight(1f))
                    Text(previewWeight.toFloat().coerceIn(range).toInt().toString(),
                        fontSize = 14.sp, color = LightTextSecondary)
                }
                Spacer(Modifier.height(4.dp))
                com.huangder.lumibooks.ui.components.PillSlider(
                    value = currentBodyFontWeight.toFloat().coerceIn(range),
                    onValueChange = {
                        previewWeight = it.toInt()
                        onBodyFontWeightChange(previewWeight)
                    },
                    onDragValueChange = { previewWeight = it.toInt() },
                    valueRange = range,
                    step = 1f,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )
                Spacer(Modifier.height(16.dp))
            }

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
                        controlEdge = true,
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
                val suiteSelectorLayout =
                    if (supportsBookLayout && currentRenderMode == EpubRenderMode.BOOK_LAYOUT) {
                        ReaderLayoutTarget.BOOK_LAYOUT
                    } else {
                        ReaderLayoutTarget.READER_LAYOUT
                    }
                ReaderThemeSuiteSelector(
                    // 「原排版」只属于书籍原排版，阅读器排版不展示。
                    suites = readerThemeSuites.filter {
                        ReaderThemeSuites.supportsLayout(it, suiteSelectorLayout)
                    },
                    activeSuiteId = activeReaderThemeSuiteId,
                    customBackgrounds = customBackgrounds,
                    customFonts = customFonts,
                    layout = suiteSelectorLayout,
                    editingDark = editingDark,
                    onSelect = onThemeSuiteSelect,
                    onCreate = onThemeSuiteCreate,
                    onDelete = onThemeSuiteDelete,
                    onReorder = onThemeSuitesReorder
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.reader_theme_suite_apply_to_book),
                        fontSize = 14.sp,
                        color = AppColors.TextPrimary
                    )
                    Spacer(Modifier.weight(1f))
                    LiquidGlassSwitch(
                        checked = readerThemeSuiteBookScoped,
                        onCheckedChange = onThemeSuiteBookScopedChange
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.reader_increase_button_contrast),
                        fontSize = 14.sp,
                        color = AppColors.TextPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    LiquidGlassSwitch(
                        checked = readerButtonContrastEnabled,
                        onCheckedChange = onReaderButtonContrastChange
                    )
                }
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

            // 翻页效果 + 显示效果（上下两行全宽模块）
            Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
                if (eInkModeEnabled) {
                    Column(Modifier.fillMaxWidth()) {
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
                        modifier = Modifier.fillMaxWidth(),
                        items = buildList {
                            add(
                                ReaderModeOption(
                                    key = "slide",
                                    label = stringResource(R.string.transition_slide),
                                    shortLabel = stringResource(R.string.page_animation_slide_short),
                                    icon = ReaderIconPageSlide
                                )
                            )
                            if (currentWritingMode != ReaderWritingMode.VERTICAL_RL) {
                                add(
                                    ReaderModeOption(
                                        key = "continuous",
                                        label = stringResource(R.string.transition_scroll),
                                        shortLabel = stringResource(R.string.page_turn_continuous_short),
                                        icon = ReaderIconPageScroll
                                    )
                                )
                                add(
                                    ReaderModeOption(
                                        key = "scroll",
                                        label = stringResource(R.string.transition_vertical_paging),
                                        shortLabel = stringResource(R.string.page_animation_scroll_short),
                                        icon = ReaderIconPageVerticalPaging
                                    )
                                )
                            }
                            add(
                                ReaderModeOption(
                                    key = "fade",
                                    label = stringResource(R.string.transition_fade),
                                    shortLabel = stringResource(R.string.page_animation_fade_short),
                                    icon = ReaderIconPageFade
                                )
                            )
                            add(
                                ReaderModeOption(
                                    key = "curl",
                                    label = stringResource(R.string.transition_curl),
                                    shortLabel = stringResource(R.string.page_animation_curl_short),
                                    icon = ReaderIconPageCurl
                                )
                            )
                        },
                        selectedKey = currentPageTransition,
                        onSelect = onPageTransitionChange,
                        glass = isLiquidGlass
                    )
                }
                Spacer(Modifier.height(10.dp))
                ReaderModeModule(
                    title = stringResource(R.string.display_module_label),
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !eInkModeEnabled,
                    glass = isLiquidGlass,
                    items = listOf(
                        ReaderModeOption(
                            key = "day",
                            label = stringResource(R.string.display_mode_day),
                            shortLabel = stringResource(R.string.display_mode_day_short),
                            icon = ReaderIconDisplayDay
                        ),
                        ReaderModeOption(
                            key = "night",
                            label = stringResource(R.string.display_mode_night),
                            shortLabel = stringResource(R.string.display_mode_night_short),
                            icon = ReaderIconDisplayNight
                        ),
                        ReaderModeOption(
                            key = "auto",
                            label = stringResource(R.string.display_mode_auto),
                            shortLabel = stringResource(R.string.display_mode_auto_short),
                            icon = ReaderIconDisplayAuto
                        )
                    ),
                    selectedKey = if (eInkModeEnabled) "auto" else currentDisplayMode,
                    onSelect = onDisplayModeChange
                )
            }

            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                LiquidGlassButton(
                    onClick = onImportThemeBundle,
                    modifier = Modifier.weight(1f),
                    tintedColor = LightBgGray,
                    contentColor = AppColors.TextPrimary
                ) {
                    Icon(AppIcons.UploadSimple, contentDescription = null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        stringResource(R.string.theme_bundle_import),
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.width(8.dp))
                LiquidGlassButton(
                    onClick = { showExportConfirm = true },
                    modifier = Modifier.weight(1f),
                    tintedColor = LightBgGray,
                    contentColor = AppColors.TextPrimary
                ) {
                    Icon(AppIcons.DownloadSimple, contentDescription = null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        stringResource(R.string.theme_bundle_export),
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }
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
    if (showExportConfirm) {
        val suite = readerThemeSuites.firstOrNull { it.id == activeReaderThemeSuiteId }
        val suiteName = suite?.customName ?: when (suite?.id) {
            ReaderThemeSuites.PUBLISHER_ID -> stringResource(R.string.reader_theme_publisher)
            ReaderThemeSuites.NIGHT_ID -> stringResource(R.string.theme_suite_night_name)
            ReaderThemeSuites.SEPIA_ID -> stringResource(R.string.theme_suite_sepia_name)
            ReaderThemeSuites.GREEN_ID -> stringResource(R.string.theme_suite_green_name)
            else -> stringResource(R.string.theme_suite_day_name)
        }
        LiquidGlassAlertDialog(
            onDismissRequest = { showExportConfirm = false },
            title = {
                Text(
                    stringResource(R.string.theme_bundle_export_confirm_title),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppColors.TextPrimary
                )
            },
            text = {
                Text(
                    stringResource(R.string.theme_bundle_export_confirm_message, suiteName),
                    color = LightTextSecondary
                )
            },
            confirmButton = {
                LiquidGlassTextButton(
                    text = stringResource(R.string.theme_bundle_export),
                    onClick = {
                        showExportConfirm = false
                        suite?.let { onExportThemeBundle(it.id) }
                    },
                    tintedColor = AppColors.Accent
                )
            },
            dismissButton = {
                LiquidGlassTextButton(
                    text = stringResource(R.string.cancel),
                    onClick = { showExportConfirm = false }
                )
            }
        )
    }
}

@Composable
private fun ReaderThemeSuiteSelector(
    suites: List<ReaderThemeSuite>,
    activeSuiteId: String,
    customBackgrounds: List<ReaderBackgroundPreset>,
    customFonts: List<CustomFontPreset>,
    layout: ReaderLayoutTarget,
    editingDark: Boolean,
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
    val publisherName = stringResource(R.string.reader_theme_publisher)
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
                    ReaderThemeSuites.PUBLISHER_ID -> publisherName
                    else -> suite.customName.orEmpty()
                },
                isSelected = activeSuiteId == suite.id,
                isArmed = armedId == suite.id,
                isDragging = isDragging,
                customBackgrounds = customBackgrounds,
                customFonts = customFonts,
                layout = layout,
                editingDark = editingDark,
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
    layout: ReaderLayoutTarget,
    editingDark: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val shape = RoundedCornerShape(14.dp)
    val settings = suite.settingsFor(layout, editingDark)
    val backgroundPreset = customBackgrounds.firstOrNull {
        it.selectionKey == settings.backgroundSelection
    }
    val baseBackgroundPreset = customBackgrounds.firstOrNull {
        it.selectionKey == settings.backgroundColorSelection && it.type == ReaderBackgroundType.COLOR
    }
    val fallbackBackground = suiteBackgroundColor(settings, backgroundPreset, baseBackgroundPreset, editingDark)
    val textColor = suiteTextColor(settings, backgroundPreset, fallbackBackground, editingDark)
    val fontFamily = rememberSuiteFontFamily(settings, customFonts)
    val backgroundImageSource = backgroundPreset
        ?.resolveImageSource(settings.backgroundImageBlurDp)

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
                        model = backgroundImageSource?.path?.let(::File),
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxSize()
                            .blur((backgroundImageSource?.runtimeBlurDp ?: 0f).dp),
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
                            AppIcons.Trash,
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
                    AppIcons.Plus,
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
    settings: ReaderThemeSettings,
    customFonts: List<CustomFontPreset>
): FontFamily {
    val fontType = settings.fontType
    val customPath = customFonts.firstOrNull { fontType == "custom:${it.id}" }?.path
    val fangSongFamilyValue = fangSongFamily()
    val serifFamily = rememberReaderSerifFontFamily(settings.bodyFontWeight)
    return remember(fontType, customPath, fangSongFamilyValue, serifFamily) {
        when {
            fontType == "serif" -> serifFamily
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
    settings: ReaderThemeSettings,
    preset: ReaderBackgroundPreset?,
    basePreset: ReaderBackgroundPreset?,
    dark: Boolean
): Color = when {
    preset?.type == ReaderBackgroundType.COLOR -> runCatching {
        android.graphics.Color.parseColor(preset.value).let {
            Color(if (dark) darkenReaderSolidColor(it) else it)
        }
    }.getOrDefault(ReaderDayBg)
    preset?.type == ReaderBackgroundType.IMAGE && basePreset != null -> runCatching {
        Color(android.graphics.Color.parseColor(basePreset.value))
    }.getOrDefault(ReaderDayBg)
    else -> Color(readerPresetBackgroundColor(settings.backgroundColorSelection, dark && preset == null))
}

private fun suiteTextColor(
    settings: ReaderThemeSettings,
    preset: ReaderBackgroundPreset?,
    backgroundColor: Color,
    dark: Boolean
): Color {
    settings.textColor?.let { return Color(it) }
    if (preset != null) {
        val themeColor = if (dark && preset.type == ReaderBackgroundType.COLOR) {
            backgroundColor.toArgb()
        } else {
            preset.dominantColor ?: backgroundColor.toArgb()
        }
        return if (ColorUtils.calculateLuminance(themeColor) < 0.42) {
            Color(0xFFE8E8EA)
        } else {
            Color(0xFF333333)
        }
    }
    return Color(readerPresetTextColor(settings.backgroundSelection, dark))
}

@Composable
private fun ReaderBackgroundSelector(
    currentSelection: String,
    customBackgrounds: List<ReaderBackgroundPreset>,
    editingDark: Boolean,
    onSelect: (String) -> Unit,
    onAddColor: (Int, String) -> Unit,
    onAddImage: (Uri, String) -> Unit,
    onDelete: (String) -> Unit,
    horizontalPadding: Dp = 24.dp
) {
    var showCustomizer by remember { mutableStateOf(false) }
    var pendingImageName by remember { mutableStateOf("") }
    var deleteArmedId by remember { mutableStateOf<String?>(null) }
    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) onAddImage(uri, pendingImageName)
        pendingImageName = ""
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
            Box(Modifier.fillMaxSize().background(Color(readerPresetBackgroundColor("day", editingDark))))
        }
        BackgroundPresetItem(
            label = stringResource(R.string.theme_night),
            isSelected = currentSelection == "night",
            onClick = { deleteArmedId = null; onSelect("night") }
        ) {
            Box(Modifier.fillMaxSize().background(Color(readerPresetBackgroundColor("night", editingDark))))
        }
        BackgroundPresetItem(
            label = stringResource(R.string.theme_sepia),
            isSelected = currentSelection == "sepia",
            onClick = { deleteArmedId = null; onSelect("sepia") }
        ) {
            Box(Modifier.fillMaxSize().background(Color(readerPresetBackgroundColor("sepia", editingDark))))
        }
        BackgroundPresetItem(
            label = stringResource(R.string.theme_green),
            isSelected = currentSelection == "green",
            onClick = { deleteArmedId = null; onSelect("green") }
        ) {
            Box(Modifier.fillMaxSize().background(Color(readerPresetBackgroundColor("green", editingDark))))
        }

        customBackgrounds.forEachIndexed { index, preset ->
            val isSelected = currentSelection == preset.selectionKey
            val isDeleteArmed = deleteArmedId == preset.id
            BackgroundPresetItem(
                label = preset.displayName(
                    stringResource(R.string.custom_background_numbered_name, index + 1)
                ),
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
                        val color = remember(preset.value, fallbackColor, editingDark) {
                            runCatching {
                                android.graphics.Color.parseColor(preset.value).let {
                                    Color(if (editingDark) darkenReaderSolidColor(it) else it)
                                }
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
                            imageVector = AppIcons.Trash,
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
                    imageVector = AppIcons.Plus,
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
                pendingImageName = it
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
    onAddColor: (Int, String) -> Unit,
    onPickPhoto: (String) -> Unit,
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
    val defaultName = stringResource(R.string.background_name_default)
    var name by remember { mutableStateOf(defaultName) }
    val backdrop = LocalLiquidGlassBackdrop.current

    LiquidGlassDialog(
        onDismissRequest = onDismiss,
        backdrop = backdrop,
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

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(LightBgGray)
                        .border(1.dp, LightDivider, RoundedCornerShape(14.dp))
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    BasicTextField(
                        value = name,
                        onValueChange = { name = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        textStyle = TextStyle(color = AppColors.TextPrimary, fontSize = 16.sp),
                        decorationBox = { inner ->
                            if (name.isEmpty()) {
                                Text(
                                    stringResource(R.string.background_name_hint),
                                    color = LightTextSecondary,
                                    fontSize = 16.sp
                                )
                            }
                            inner()
                        }
                    )
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
                    15f..100f
                ) { lightness = it }
                Spacer(Modifier.height(18.dp))

                LiquidGlassTextButton(
                    text = stringResource(R.string.background_add_color),
                    onClick = {
                        onAddColor(previewColorInt, name.trim())
                        onDismiss()
                    },
                    enabled = name.trim().isNotEmpty(),
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                    tintedColor = AccentColor
                )
                Spacer(Modifier.height(10.dp))
                LiquidGlassButton(
                    onClick = { onPickPhoto(name.trim()) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .border(1.dp, LightDivider, RoundedCornerShape(22.dp))
                ) {
                        Icon(
                            imageVector = AppIcons.Image,
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

/** 页边距滑块当前调节的对象：正文，还是四角信息区（页眉/页脚）。 */
private enum class ReaderMarginTarget { BODY, CORNER }

private enum class ReaderMarginControl {
    BODY_TOP,
    BODY_BOTTOM,
    BODY_LEFT,
    BODY_RIGHT,
    CORNER_TOP,
    CORNER_BOTTOM,
    CORNER_LEFT,
    CORNER_RIGHT
}

private data class ReaderMarginPreview(
    val control: ReaderMarginControl,
    val bounds: Rect,
    val value: Float,
    val range: ClosedFloatingPointRange<Float>,
    val step: Float
)

/**
 * 页边距区域左上角的选择 tag：一个胶囊里并排两段，切换下面四个滑块调的是正文还是页眉/页脚。
 *
 * 只占自身内容宽度、左对齐，不铺满整个容器；两段宽度按各自文字实测宽度 + 左右内边距，
 * 因此短标签（「正文」）也有足够的留白，系统字体放大时容器跟着变宽而不是挤压文字。
 */
@Composable
private fun ReaderMarginTargetTag(
    selected: ReaderMarginTarget,
    onSelect: (ReaderMarginTarget) -> Unit,
    animate: Boolean = true
) {
    val options = listOf(
        ReaderMarginTarget.BODY to stringResource(R.string.label_margin_target_body),
        ReaderMarginTarget.CORNER to stringResource(R.string.label_margin_target_corner)
    )
    ReaderSettingsSegmentedTag(
        labels = options.map { it.second },
        selectedIndex = options.indexOfFirst { it.first == selected }.coerceAtLeast(0),
        onSelect = { onSelect(options[it].first) },
        animate = animate
    )
}

/** Shared segmented tag used for reading target and light/dark theme editing mode. */
@Composable
fun ReaderSettingsSegmentedTag(
    labels: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    animate: Boolean = true
) {
    if (labels.isEmpty()) return
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium)
    val segmentWidths = labels.map { label ->
        with(density) {
            textMeasurer.measure(text = label, style = labelStyle, maxLines = 1)
                .size.width.toDp()
        } + SegmentHorizontalPadding * 2
    }

    val safeSelectedIndex = selectedIndex.coerceIn(0, labels.lastIndex)
    val selectedSegmentColor = if (LocalIsDarkTheme.current && !LocalEInkMode.current) {
        Color(0xFF1C1C1E)
    } else {
        LightCardBg
    }
    if (LocalAppTheme.current == "liquid_glass" && !LocalEInkMode.current) {
        LiquidGlassSegmentedControl(
            itemCount = labels.size,
            selectedIndex = safeSelectedIndex,
            onSelected = onSelect,
            modifier = modifier,
            segmentWidths = segmentWidths,
            trackHeight = 36.dp,
            trackPadding = SegmentTrackPadding
        ) { index, isSelected ->
            Text(
                text = labels[index],
                fontSize = 13.sp,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (isSelected) AppColors.TextPrimary.copy(alpha = 0.86f) else LightTextSecondary,
                maxLines = 1
            )
        }
        return
    }

    val indicatorTargetOffset = segmentWidths.take(safeSelectedIndex).fold(0.dp) { sum, width ->
        sum + width
    }
    val indicatorSpec = if (animate) {
        spring<Dp>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)
    } else {
        tween<Dp>(0)
    }
    val indicatorOffset by animateDpAsState(
        targetValue = indicatorTargetOffset,
        animationSpec = indicatorSpec,
        label = "marginTargetIndicatorOffset"
    )
    val indicatorWidth by animateDpAsState(
        targetValue = segmentWidths[safeSelectedIndex],
        animationSpec = indicatorSpec,
        label = "marginTargetIndicatorWidth"
    )

    Box(
        modifier = modifier
            .clip(MarginTargetTagShape)
            .background(LightBgGray)
            .padding(SegmentTrackPadding)
    ) {
        Box(
            modifier = Modifier
                .offset(x = indicatorOffset)
                .width(indicatorWidth)
                .height(MarginTargetSegmentHeight)
                .clip(MarginTargetSegmentShape)
                .background(selectedSegmentColor)
        )
        Row {
            labels.forEachIndexed { index, label ->
                val isSelected = index == safeSelectedIndex
                Box(
                    modifier = Modifier
                        .width(segmentWidths[index])
                        .height(MarginTargetSegmentHeight)
                        .clip(MarginTargetSegmentShape)
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() }
                        ) { onSelect(index) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        fontSize = 13.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (isSelected) AppColors.TextPrimary else LightTextSecondary,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

private val MarginTargetTagShape = RoundedCornerShape(14.dp)
private val MarginTargetSegmentShape = RoundedCornerShape(11.dp)
private val MarginTargetSegmentHeight = 30.dp
private val SegmentTrackPadding = 3.dp
private val SegmentHorizontalPadding = 16.dp

/** 阅读模式图标选项（翻页效果 / 显示效果共用） */
private data class ReaderModeOption(
    val key: String,
    val label: String,
    val shortLabel: String,
    val icon: IconPair
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
        if (LocalAppTheme.current == "liquid_glass" && !LocalEInkMode.current) {
            val selectedIndex = items.indexOfFirst { it.key == selectedKey }.coerceAtLeast(0)
            LiquidGlassSegmentedControl(
                itemCount = items.size,
                selectedIndex = selectedIndex,
                onSelected = { onSelect(items[it].key) },
                modifier = Modifier.fillMaxWidth(),
                enabled = enabled,
                spacing = 4.dp,
                trackHeight = 48.dp,
                trackPadding = 4.dp
            ) { index, isSelected ->
                val item = items[index]
                val contentColor = when {
                    !enabled -> LightTextSecondary.copy(alpha = 0.35f)
                    isSelected -> AppColors.TextPrimary.copy(alpha = 0.86f)
                    else -> LightTextSecondary
                }
                Row(
                    modifier = Modifier.semantics {
                        contentDescription = item.label
                    },
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = item.icon.resolve(isSelected),
                        contentDescription = null,
                        tint = contentColor,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(3.dp))
                    Text(
                        text = item.shortLabel,
                        color = contentColor,
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
            return@Column
        }

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
                        label = item.shortLabel,
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
    icon: IconPair,
    label: String,
    contentDescription: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Row(
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
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon.resolve(isSelected),
            contentDescription = null,
            tint = when {
                !enabled -> LightTextSecondary.copy(alpha = 0.35f)
                isSelected -> AppColors.TextPrimary
                else -> LightTextSecondary
            },
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(3.dp))
        Text(
            text = label,
            color = when {
                !enabled -> LightTextSecondary.copy(alpha = 0.35f)
                isSelected -> AppColors.TextPrimary
                else -> LightTextSecondary
            },
            fontSize = 11.sp,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
            softWrap = false
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
    previewImage: android.graphics.drawable.Drawable? = null,
    currentLineHeight: Float,
    currentLetterSpacing: Float,
    currentTextAlignment: ReaderTextAlignment = ReaderTextAlignment.NATURAL,
    currentFontType: String,
    customFontPath: String? = null,
    customFonts: List<com.huangder.lumibooks.domain.model.CustomFontPreset> = emptyList(),
    currentBackgroundSelection: String,
    customBackgrounds: List<ReaderBackgroundPreset>,
    currentPreserveEpubBackground: Boolean = true,
    showPreserveEpubBackground: Boolean = false,
    currentPageImageCrop: Boolean = false,
    showPageImageCrop: Boolean = false,
    /** 「原排版」套装激活：阅读器不参与配色，隐藏背景与文字颜色相关设置。 */
    publisherSuiteActive: Boolean = false,
    currentMarginLeft: Float,
    currentMarginRight: Float,
    currentMarginTop: Float,
    currentMarginBottom: Float,
    /** 四角信息区（页眉/页脚）边距；空字段表示跟随正文边距 / 沿用旧版默认位置。 */
    currentCornerMargins: ReaderCornerMargins = ReaderCornerMargins(),
    currentBgColor: Color,
    currentBackgroundImagePath: String?,
    currentTextColor: Color,
    currentTextColorOverride: Int?,
    currentFontSizeSp: Float,
    preservePublisherLayout: Boolean = false,
    editingDark: Boolean = false,
    currentWritingMode: ReaderWritingMode = ReaderWritingMode.HORIZONTAL,
    eInkModeEnabled: Boolean = false,
    fontDownloadKey: String? = null,
    fontDownloadFailed: Boolean = false,
    onLineHeightChange: (Float) -> Unit,
    onLetterSpacingChange: (Float) -> Unit,
    onTextAlignmentChange: (ReaderTextAlignment) -> Unit = {},
    onFontTypeChange: (String) -> Unit,
    onImportFont: (android.net.Uri, String) -> Unit = { _, _ -> },
    onDeleteCustomFont: (String) -> Unit = {},
    onBackgroundSelect: (String) -> Unit,
    onAddBackgroundColor: (Int, String) -> Unit,
    onAddBackgroundImage: (Uri, String) -> Unit,
    onDeleteBackground: (String) -> Unit,
    onThemeEditModeChange: (Boolean) -> Unit = {},
    onPreserveEpubBackgroundChange: (Boolean) -> Unit = {},
    onPageImageCropChange: (Boolean) -> Unit = {},
    imageAdjustmentsEnabled: Boolean = false,
    currentImageBrightness: Float = 0f,
    currentImageContrast: Float = 1f,
    currentImageSharpen: Float = 0f,
    onImageBrightnessChange: (Float) -> Unit = {},
    onImageContrastChange: (Float) -> Unit = {},
    onImageSharpenChange: (Float) -> Unit = {},
    onMarginLeftChange: (Float) -> Unit,
    onMarginRightChange: (Float) -> Unit,
    onMarginTopChange: (Float) -> Unit,
    onMarginBottomChange: (Float) -> Unit,
    onMarginLeftPreview: (Float) -> Unit = onMarginLeftChange,
    onMarginRightPreview: (Float) -> Unit = onMarginRightChange,
    onMarginTopPreview: (Float) -> Unit = onMarginTopChange,
    onMarginBottomPreview: (Float) -> Unit = onMarginBottomChange,
    onCornerMarginsChange: (ReaderCornerMargins) -> Unit = {},
    onCornerMarginsPreview: (ReaderCornerMargins) -> Unit = onCornerMarginsChange,
    currentParagraphSpacing: Float = 0f,
    currentFirstLineIndent: Float = 0f,
    onParagraphSpacingChange: (Float) -> Unit = {},
    onFirstLineIndentChange: (Float) -> Unit = {},
    readerTopLeftContent: ReaderCornerContent,
    readerTopRightContent: ReaderCornerContent,
    readerBottomLeftContent: ReaderCornerContent,
    readerBottomRightContent: ReaderCornerContent,
    volumeKeyPageTurnEnabled: Boolean = false,
    bookmarkRemarkPromptEnabled: Boolean = true,
    bionicReadingEnabled: Boolean = false,
    comicModeEnabled: Boolean = false,
    screenSleepTimeoutSeconds: Int = DataStoreManager.DEFAULT_SCREEN_SLEEP_TIMEOUT_SECONDS,
    readerEdgeTapMode: ReaderEdgeTapMode = ReaderEdgeTapMode.LEFT_PREVIOUS_RIGHT_NEXT,
    onReaderCornerContentChange: (ReaderPageCorner, ReaderCornerContent) -> Unit,
    onVolumeKeyPageTurnEnabledChange: (Boolean) -> Unit = {},
    onBookmarkRemarkPromptEnabledChange: (Boolean) -> Unit = {},
    onBionicReadingEnabledChange: (Boolean) -> Unit = {},
    onComicModeChange: (Boolean) -> Unit = {},
    onScreenSleepTimeoutChange: (Int) -> Unit = {},
    onReaderEdgeTapModeChange: (ReaderEdgeTapMode) -> Unit = {},
    onTextColorChange: (Int?) -> Unit,
    onResetSettings: () -> Unit,
    onDismiss: () -> Unit
) {
    if (!visible) return

    val sheetOffset = remember { Animatable(1f) }
    val settingsScrollState = rememberScrollState()
    var marginPreview by remember { mutableStateOf<ReaderMarginPreview?>(null) }
    val expandedGroups = remember { mutableStateMapOf<String, Boolean>() }
    fun isGroupExpanded(key: String, defaultExpanded: Boolean = false): Boolean =
        expandedGroups[key] ?: defaultExpanded

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
        currentFontType == "serif" -> rememberReaderSerifFontFamily()
        currentFontType == "fangsong" -> fangSongFamily()
        currentFontType == "kaiti" -> KaiTi
        currentFontType.startsWith("custom") -> customPreviewFontFamily
        else -> androidx.compose.ui.text.font.FontFamily.Default
    }
    val resolvedPreviewText = previewText.ifBlank { stringResource(R.string.preview_text) }
    val previewParagraphs = remember(resolvedPreviewText) {
        buildPreviewParagraphs(resolvedPreviewText)
    }
    val previewLeftPadding = currentMarginLeft.coerceIn(ReaderThemeSettings.HORIZONTAL_MARGIN_RANGE).dp
    val previewRightPadding = currentMarginRight.coerceIn(ReaderThemeSettings.HORIZONTAL_MARGIN_RANGE).dp
    val previewTopPadding =
        (currentMarginTop.coerceIn(ReaderThemeSettings.VERTICAL_MARGIN_RANGE) / 3f).dp
    val previewBottomPadding =
        (currentMarginBottom.coerceIn(ReaderThemeSettings.VERTICAL_MARGIN_RANGE) / 3f).dp
    val previewLineHeight = if (preservePublisherLayout) 1.5f else currentLineHeight
    val previewLetterSpacing = if (preservePublisherLayout) 0f else currentLetterSpacing
    val previewParagraphSpacing = if (preservePublisherLayout) 0f else currentParagraphSpacing
    val previewFirstLineIndent = if (preservePublisherLayout) 0f else currentFirstLineIndent
    val previewContext = LocalContext.current
    val verticalPreviewTypeface = remember(previewContext, currentFontType, customFontPath) {
        resolveReaderTypeface(
            context = previewContext,
            fontType = currentFontType,
            customFontPath = customFontPath,
            weight = 400
        ).typeface
    }

    LiquidGlassMenuHost(modifier = Modifier.fillMaxSize()) {
        // 遮罩
        Box(
            Modifier.fillMaxSize()
                .background(
                    AppColors.Scrim.copy(
                        alpha = if (marginPreview == null) {
                            0.20f * (1f - sheetOffset.value.coerceIn(0f, 1f))
                        } else {
                            0f
                        }
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
                .navigationBarsPadding()
                .graphicsLayer { alpha = if (marginPreview == null) 1f else 0f },
            fallbackColor = LightCardBg,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            surfaceVisible = marginPreview == null
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
                if (imageAdjustmentsEnabled && previewImage != null) {
                    val imageScope = androidx.compose.runtime.rememberCoroutineScope()
                    val image = remember(previewImage) { AdjustedReaderDrawable(previewImage) }
                    AndroidView(
                        factory = { context -> android.widget.ImageView(context).apply {
                            scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
                        } },
                        update = { view ->
                            if (view.drawable !== image) view.setImageDrawable(image)
                            image.update(com.huangder.lumibooks.domain.model.ReaderImageAdjustments(
                                currentImageBrightness, currentImageContrast, currentImageSharpen
                            ).forDisplay(eInkModeEnabled), imageScope) { view.invalidate() }
                        },
                        modifier = Modifier.fillMaxSize().padding(top = 62.dp, bottom = 8.dp)
                    )
                } else if (currentWritingMode.isVertical && !preservePublisherLayout) {
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
                                    textAlign = currentTextAlignment.toComposeTextAlign(),
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
                        imageVector = AppIcons.X,
                        contentDescription = stringResource(R.string.close),
                        onClick = { isClosing = true },
                        size = 44.dp,
                        iconSize = 20.dp,
                        contentColor = AppColors.TextPrimary,
                        normalContainerColor = LightBgGray.copy(alpha = 0.92f)
                    )
                    Spacer(Modifier.weight(1f))
                    LiquidGlassIconButton(
                        imageVector = AppIcons.Check,
                        contentDescription = stringResource(R.string.confirm),
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
                Text(
                    text = stringResource(
                        if (preservePublisherLayout) R.string.reader_layout_editing_book
                        else R.string.reader_layout_editing_reader
                    ),
                    fontSize = 12.sp,
                    color = LightTextSecondary
                )
                Spacer(Modifier.height(10.dp))
                if (!eInkModeEnabled) {
                    AdvancedSettingsSection(
                        title = stringResource(R.string.reader_settings_appearance),
                        summary = stringResource(R.string.reader_settings_appearance_summary),
                        expanded = isGroupExpanded("appearance", true),
                        onExpandedChange = { expandedGroups["appearance"] = it },
                        eInkModeEnabled = eInkModeEnabled
                    ) {
                        if (!preservePublisherLayout && !publisherSuiteActive) {
                            ReaderSettingsSegmentedTag(
                                labels = listOf(
                                    stringResource(R.string.reader_theme_mode_light),
                                    stringResource(R.string.reader_theme_mode_dark)
                                ),
                                selectedIndex = if (editingDark) 1 else 0,
                                onSelect = { onThemeEditModeChange(it == 1) }
                            )
                            Spacer(Modifier.height(18.dp))
                        }
                        if (publisherSuiteActive) {
                            // 「原排版」用书籍自带配色：底色与文字颜色都不可改。
                            Text(
                                stringResource(R.string.reader_theme_publisher_locked_background),
                                fontSize = 13.sp,
                                color = LightTextSecondary
                            )
                        } else {
                            Text(
                                stringResource(R.string.reading_background),
                                fontSize = 14.sp,
                                color = LightTextSecondary
                            )
                            Spacer(Modifier.height(12.dp))
                            ReaderBackgroundSelector(
                                currentSelection = currentBackgroundSelection,
                                customBackgrounds = customBackgrounds,
                                editingDark = editingDark,
                                onSelect = onBackgroundSelect,
                                onAddColor = onAddBackgroundColor,
                                onAddImage = onAddBackgroundImage,
                                onDelete = onDeleteBackground,
                                horizontalPadding = 0.dp
                            )
                        }
                        if (showPreserveEpubBackground && !publisherSuiteActive) {
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
                        if (showPageImageCrop) {
                            Spacer(Modifier.height(16.dp))
                            Row(
                                Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        stringResource(R.string.reader_image_page_crop),
                                        fontSize = 14.sp,
                                        color = AppColors.TextPrimary
                                    )
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        stringResource(R.string.reader_image_page_crop_hint),
                                        fontSize = 12.sp,
                                        color = LightTextSecondary
                                    )
                                }
                                LiquidGlassSwitch(
                                    checked = currentPageImageCrop,
                                    onCheckedChange = onPageImageCropChange
                                )
                            }
                        }
                        if (!publisherSuiteActive) {
                            Spacer(Modifier.height(18.dp))
                            TextColorSetting(
                                currentOverride = currentTextColorOverride,
                                effectiveTextColor = currentTextColor,
                                onColorChange = onTextColorChange
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }

                if (!eInkModeEnabled || imageAdjustmentsEnabled) {
                    AdvancedSettingsSection(
                        title = stringResource(R.string.reader_settings_page_image),
                        summary = stringResource(R.string.reader_settings_page_image_summary),
                        expanded = isGroupExpanded("page_image", true),
                        onExpandedChange = { expandedGroups["page_image"] = it },
                        eInkModeEnabled = eInkModeEnabled
                    ) {
                        if (imageAdjustmentsEnabled) {
                            val imageAdjustmentsExpanded = isGroupExpanded("image_adjustments")
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable(
                                        indication = null,
                                        interactionSource = remember { MutableInteractionSource() }
                                    ) { expandedGroups["image_adjustments"] = !imageAdjustmentsExpanded },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(stringResource(R.string.reader_image_adjustment), fontSize = 14.sp, color = AppColors.TextPrimary)
                                    Text(stringResource(R.string.reader_image_adjustment_hint), fontSize = 12.sp, color = LightTextSecondary)
                                }
                                Icon(
                                    if (imageAdjustmentsExpanded) AppIcons.CaretUp else AppIcons.CaretDown,
                                    null,
                                    tint = LightTextSecondary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            AnimatedVisibility(
                                visible = imageAdjustmentsExpanded,
                                enter = if (eInkModeEnabled) androidx.compose.animation.EnterTransition.None else expandVertically(tween(220)) + fadeIn(tween(160)),
                                exit = if (eInkModeEnabled) androidx.compose.animation.ExitTransition.None else shrinkVertically(tween(180)) + fadeOut(tween(100))
                            ) {
                                ImageAdjustmentControls(
                                    settings = com.huangder.lumibooks.domain.model.ReaderImageAdjustments(
                                        currentImageBrightness, currentImageContrast, currentImageSharpen),
                                    eInk = eInkModeEnabled,
                                    onBrightness = onImageBrightnessChange,
                                    onContrast = onImageContrastChange,
                                    onSharpen = onImageSharpenChange,
                                    onReset = {
                                        onImageBrightnessChange(0f)
                                        onImageContrastChange(1f)
                                        onImageSharpenChange(0f)
                                    }
                                )
                            }
                            Spacer(Modifier.height(16.dp))
                        }
                        AdvancedToggleRow(
                            title = stringResource(R.string.comic_mode),
                            hint = stringResource(R.string.comic_mode_hint),
                            checked = comicModeEnabled,
                            onCheckedChange = onComicModeChange
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                }

                AdvancedSettingsSection(
                    title = stringResource(R.string.reader_settings_text_layout),
                    summary = stringResource(R.string.reader_settings_text_layout_summary),
                    expanded = isGroupExpanded("text_layout"),
                    onExpandedChange = { expandedGroups["text_layout"] = it },
                    eInkModeEnabled = eInkModeEnabled
                ) {
                    TextAlignmentSetting(
                        selected = currentTextAlignment,
                        forceSolidMenu = eInkModeEnabled || preservePublisherLayout,
                        onSelected = onTextAlignmentChange
                    )
                    if (!eInkModeEnabled) {
                        Spacer(Modifier.height(16.dp))
                        AdvancedToggleRow(
                            title = stringResource(R.string.bionic_reading),
                            hint = stringResource(R.string.bionic_reading_hint),
                            checked = bionicReadingEnabled,
                            onCheckedChange = onBionicReadingEnabledChange
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))

                if (!preservePublisherLayout) {
                    AdvancedSettingsSection(
                        title = stringResource(R.string.reader_settings_text_spacing),
                        summary = stringResource(R.string.reader_settings_text_spacing_summary),
                        expanded = isGroupExpanded("text_spacing"),
                        onExpandedChange = { expandedGroups["text_spacing"] = it },
                        eInkModeEnabled = eInkModeEnabled
                    ) {
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
                        val characterUnit = stringResource(R.string.reader_unit_character)
                        SettingSlider(
                            stringResource(R.string.label_first_line_indent),
                            currentFirstLineIndent,
                            0f..4f,
                            0.5f,
                            { "$it $characterUnit" },
                            onFirstLineIndentChange
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                }
                AdvancedSettingsSection(
                    title = stringResource(R.string.reader_settings_page_layout),
                    summary = stringResource(R.string.reader_settings_page_layout_summary),
                    expanded = isGroupExpanded("page_layout"),
                    onExpandedChange = { expandedGroups["page_layout"] = it },
                    eInkModeEnabled = eInkModeEnabled
                ) {
                    // 左上角只占内容宽度的选择 tag：决定下面四个滑块调正文还是页眉/页脚。
                    var marginTarget by remember { mutableStateOf(ReaderMarginTarget.BODY) }
                    ReaderMarginTargetTag(
                        selected = marginTarget,
                        onSelect = { marginTarget = it },
                        animate = !eInkModeEnabled
                    )
                    Spacer(Modifier.height(12.dp))
                    if (marginTarget == ReaderMarginTarget.BODY) {
                        MarginSettingSlider(
                            control = ReaderMarginControl.BODY_TOP,
                            stringResource(R.string.label_margin_top),
                            currentMarginTop,
                            ReaderThemeSettings.VERTICAL_MARGIN_RANGE,
                            2f,
                            { "${it.toInt()} dp" },
                            onMarginTopChange,
                            onMarginTopPreview,
                            marginPreview,
                            { marginPreview = it }
                        )
                        Spacer(Modifier.height(12.dp))
                        MarginSettingSlider(
                            control = ReaderMarginControl.BODY_BOTTOM,
                            stringResource(R.string.label_margin_bottom),
                            currentMarginBottom,
                            ReaderThemeSettings.VERTICAL_MARGIN_RANGE,
                            2f,
                            { "${it.toInt()} dp" },
                            onMarginBottomChange,
                            onMarginBottomPreview,
                            marginPreview,
                            { marginPreview = it }
                        )
                        Spacer(Modifier.height(12.dp))
                        MarginSettingSlider(
                            control = ReaderMarginControl.BODY_LEFT,
                            stringResource(R.string.label_margin_left),
                            currentMarginLeft,
                            ReaderThemeSettings.HORIZONTAL_MARGIN_RANGE,
                            2f,
                            { "${it.toInt()} dp" },
                            onMarginLeftChange,
                            onMarginLeftPreview,
                            marginPreview,
                            { marginPreview = it }
                        )
                        Spacer(Modifier.height(12.dp))
                        MarginSettingSlider(
                            control = ReaderMarginControl.BODY_RIGHT,
                            stringResource(R.string.label_margin_right),
                            currentMarginRight,
                            ReaderThemeSettings.HORIZONTAL_MARGIN_RANGE,
                            2f,
                            { "${it.toInt()} dp" },
                            onMarginRightChange,
                            onMarginRightPreview,
                            marginPreview,
                            { marginPreview = it }
                        )
                    } else {
                        // 页眉/页脚：未单独设置过的边沿用正文边距 / 旧版默认位置。
                        val cornerMarginRange = ReaderCornerMargins.VERTICAL_RANGE
                        val cornerHorizontalRange = ReaderCornerMargins.HORIZONTAL_RANGE
                        MarginSettingSlider(
                            control = ReaderMarginControl.CORNER_TOP,
                            stringResource(R.string.label_margin_top),
                            currentCornerMargins.resolvedTopDp(),
                            cornerMarginRange,
                            2f,
                            { "${it.toInt()} dp" },
                            { value -> onCornerMarginsChange(currentCornerMargins.copy(topDp = value)) },
                            { value -> onCornerMarginsPreview(currentCornerMargins.copy(topDp = value)) },
                            marginPreview,
                            { marginPreview = it }
                        )
                        Spacer(Modifier.height(12.dp))
                        MarginSettingSlider(
                            control = ReaderMarginControl.CORNER_BOTTOM,
                            stringResource(R.string.label_margin_bottom),
                            currentCornerMargins.resolvedBottomDp(),
                            cornerMarginRange,
                            2f,
                            { "${it.toInt()} dp" },
                            { value -> onCornerMarginsChange(currentCornerMargins.copy(bottomDp = value)) },
                            { value -> onCornerMarginsPreview(currentCornerMargins.copy(bottomDp = value)) },
                            marginPreview,
                            { marginPreview = it }
                        )
                        Spacer(Modifier.height(12.dp))
                        MarginSettingSlider(
                            control = ReaderMarginControl.CORNER_LEFT,
                            stringResource(R.string.label_margin_left),
                            currentCornerMargins.resolvedLeftDp(currentMarginLeft),
                            cornerHorizontalRange,
                            2f,
                            { "${it.toInt()} dp" },
                            { value -> onCornerMarginsChange(currentCornerMargins.copy(leftDp = value)) },
                            { value -> onCornerMarginsPreview(currentCornerMargins.copy(leftDp = value)) },
                            marginPreview,
                            { marginPreview = it }
                        )
                        Spacer(Modifier.height(12.dp))
                        MarginSettingSlider(
                            control = ReaderMarginControl.CORNER_RIGHT,
                            stringResource(R.string.label_margin_right),
                            currentCornerMargins.resolvedRightDp(currentMarginRight),
                            cornerHorizontalRange,
                            2f,
                            { "${it.toInt()} dp" },
                            { value -> onCornerMarginsChange(currentCornerMargins.copy(rightDp = value)) },
                            { value -> onCornerMarginsPreview(currentCornerMargins.copy(rightDp = value)) },
                            marginPreview,
                            { marginPreview = it }
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))

                AdvancedSettingsSection(
                    title = stringResource(R.string.reader_settings_reader_controls),
                    summary = stringResource(R.string.reader_settings_reader_controls_summary),
                    expanded = isGroupExpanded("controls"),
                    onExpandedChange = { expandedGroups["controls"] = it },
                    eInkModeEnabled = eInkModeEnabled
                ) {
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
                    Spacer(Modifier.height(16.dp))
                    AdvancedToggleRow(
                        title = stringResource(R.string.bookmark_remark_prompt_setting),
                        hint = stringResource(R.string.bookmark_remark_prompt_setting_hint),
                        checked = bookmarkRemarkPromptEnabled,
                        onCheckedChange = onBookmarkRemarkPromptEnabledChange
                    )
                }
                Spacer(Modifier.height(12.dp))

                AdvancedSettingsSection(
                    title = stringResource(R.string.reader_settings_font),
                    summary = stringResource(R.string.reader_settings_font_summary),
                    expanded = isGroupExpanded("font"),
                    onExpandedChange = { expandedGroups["font"] = it },
                    eInkModeEnabled = eInkModeEnabled
                ) {
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

        marginPreview?.let { preview ->
            val density = LocalDensity.current
            com.huangder.lumibooks.ui.components.PillSlider(
                value = preview.value,
                onValueChange = {},
                valueRange = preview.range,
                step = preview.step,
                modifier = Modifier
                    .width(with(density) { preview.bounds.width.toDp() })
                    .offset {
                        androidx.compose.ui.unit.IntOffset(
                            preview.bounds.left.toInt(),
                            preview.bounds.top.toInt()
                        )
                    }
            )
        }
    }
}

@Composable
private fun MarginSettingSlider(
    control: ReaderMarginControl,
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    step: Float,
    format: (Float) -> String,
    onChange: (Float) -> Unit,
    onPreview: (Float) -> Unit,
    activePreview: ReaderMarginPreview?,
    onActivePreviewChange: (ReaderMarginPreview?) -> Unit
) {
    var bounds by remember { mutableStateOf<Rect?>(null) }
    SettingSlider(
        label = label,
        value = value,
        range = range,
        step = step,
        format = format,
        onChange = { changed ->
            onActivePreviewChange(activePreview?.takeIf { it.control == control }?.copy(value = changed))
            onChange(changed)
        },
        onPreview = { changed ->
            onActivePreviewChange(activePreview?.takeIf { it.control == control }?.copy(value = changed))
            onPreview(changed)
        },
        sliderModifier = Modifier.onGloballyPositioned { bounds = it.boundsInRoot() },
        onInteractionChange = { active ->
            if (active) {
                bounds?.let {
                    onActivePreviewChange(ReaderMarginPreview(control, it, value, range, step))
                }
            } else if (activePreview?.control == control) {
                onActivePreviewChange(null)
            }
        }
    )
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
private fun AdvancedSettingsSection(
    title: String,
    summary: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    eInkModeEnabled: Boolean,
    content: @Composable ColumnScope.() -> Unit
) {
    val isLiquidGlass = LocalAppTheme.current == "liquid_glass" && !eInkModeEnabled
    val isDark = LocalIsDarkTheme.current
    val transparency = LocalLiquidGlassTransparency.current
    val shape = RoundedCornerShape(16.dp)
    val surfaceAlpha = if (isDark) 0.43f - transparency * 0.12f else 0.59f - transparency * 0.18f
    val container = if (isLiquidGlass) {
        Modifier
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    listOf(
                        LightCardBg.copy(alpha = (surfaceAlpha + 0.06f).coerceAtMost(0.62f)),
                        LightCardBg.copy(alpha = surfaceAlpha)
                    )
                )
            )
            .border(
                0.7.dp,
                Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = if (isDark) 0.24f else 0.72f),
                        Color.White.copy(alpha = if (isDark) 0.08f else 0.20f)
                    )
                ),
                shape
            )
    } else {
        Modifier.clip(shape).background(LightBgGray.copy(alpha = 0.42f))
    }
    Column(Modifier.fillMaxWidth().then(container)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) { onExpandedChange(!expanded) }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = AppColors.TextPrimary)
                Spacer(Modifier.height(2.dp))
                Text(summary, fontSize = 12.sp, color = LightTextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Icon(
                imageVector = if (expanded) AppIcons.CaretUp else AppIcons.CaretDown,
                contentDescription = null,
                tint = LightTextSecondary,
                modifier = Modifier.size(18.dp)
            )
        }
        AnimatedVisibility(
            visible = expanded,
            enter = if (eInkModeEnabled) androidx.compose.animation.EnterTransition.None else expandVertically(tween(220)) + fadeIn(tween(160)),
            exit = if (eInkModeEnabled) androidx.compose.animation.ExitTransition.None else shrinkVertically(tween(180)) + fadeOut(tween(100))
        ) {
            Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp), content = content)
        }
    }
}

@Composable
private fun ImageAdjustmentEntry(enabled: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.reader_image_adjustment), fontSize = 14.sp, color = if (enabled) AppColors.TextPrimary else LightTextSecondary)
            Spacer(Modifier.height(2.dp))
            Text(stringResource(if (enabled) R.string.reader_image_adjustment_hint else R.string.reader_image_adjustment_unavailable), fontSize = 12.sp, color = LightTextSecondary)
        }
        Icon(AppIcons.CaretRight, contentDescription = null, tint = LightTextSecondary, modifier = Modifier.size(18.dp))
    }
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
    val menuSurfaceColor = LightCardBg
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
                    .liquidGlassMenuAnchor(cornerRadius = 14.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(LightBgGray)
                    .onGloballyPositioned { menuAnchorBounds = it.boundsInRoot() }
                    .clickable {
                        if (liquidMenuHost != null && menuAnchorBounds != Rect.Zero) {
                            liquidMenuHost.toggle(
                                LiquidGlassMenuSpec(
                                    anchorBounds = menuAnchorBounds,
                                    width = 158.dp,
                                    anchorCornerRadius = 14.dp,
                                    forceSolid = forceSolidMenu,
                                    surfaceColor = menuSurfaceColor,
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
                    imageVector = AppIcons.CaretDown,
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
private fun TextAlignmentSetting(
    selected: ReaderTextAlignment,
    forceSolidMenu: Boolean,
    onSelected: (ReaderTextAlignment) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var menuAnchorBounds by remember { mutableStateOf(Rect.Zero) }
    val labeledOptions = ReaderTextAlignment.entries.map { alignment ->
        alignment to readerTextAlignmentLabel(alignment)
    }
    val menuSurfaceColor = LightCardBg
    val liquidMenuHost = LocalLiquidGlassMenuHost.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(R.string.label_text_alignment),
            fontSize = 13.sp,
            color = AppColors.TextPrimary,
            modifier = Modifier.weight(1f)
        )
        Box {
            Box(
                modifier = Modifier
                    .width(158.dp)
                    .height(36.dp)
                    .liquidGlassMenuAnchor(cornerRadius = 14.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(LightBgGray)
                    .onGloballyPositioned { menuAnchorBounds = it.boundsInRoot() }
                    .clickable {
                        if (liquidMenuHost != null && menuAnchorBounds != Rect.Zero) {
                            liquidMenuHost.toggle(
                                LiquidGlassMenuSpec(
                                    anchorBounds = menuAnchorBounds,
                                    width = 158.dp,
                                    anchorCornerRadius = 14.dp,
                                    forceSolid = forceSolidMenu,
                                    surfaceColor = menuSurfaceColor,
                                    items = labeledOptions.map { (alignment, label) ->
                                        LiquidGlassMenuItem(
                                            label = label,
                                            selected = alignment == selected,
                                            onClick = { onSelected(alignment) }
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
                    text = readerTextAlignmentLabel(selected),
                    fontSize = 12.sp,
                    color = AppColors.TextPrimary,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(end = 16.dp)
                )
                Icon(
                    imageVector = AppIcons.CaretDown,
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
                labeledOptions.forEach { (alignment, label) ->
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
                            onSelected(alignment)
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
    val menuSurfaceColor = LightCardBg
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
                    .liquidGlassMenuAnchor(cornerRadius = 14.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(LightBgGray)
                    .onGloballyPositioned { menuAnchorBounds = it.boundsInRoot() }
                    .clickable {
                        if (liquidMenuHost != null && menuAnchorBounds != Rect.Zero) {
                            liquidMenuHost.toggle(
                                LiquidGlassMenuSpec(
                                    anchorBounds = menuAnchorBounds,
                                    width = 158.dp,
                                    anchorCornerRadius = 14.dp,
                                    forceSolid = forceSolidMenu,
                                    surfaceColor = menuSurfaceColor,
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
                    imageVector = AppIcons.CaretDown,
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
    val menuSurfaceColor = LightCardBg
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
                    .liquidGlassMenuAnchor(cornerRadius = 14.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(LightBgGray)
                    .onGloballyPositioned { menuAnchorBounds = it.boundsInRoot() }
                    .clickable {
                        if (liquidMenuHost != null && menuAnchorBounds != Rect.Zero) {
                            liquidMenuHost.toggle(
                                LiquidGlassMenuSpec(
                                    anchorBounds = menuAnchorBounds,
                                    width = 128.dp,
                                    anchorCornerRadius = 14.dp,
                                    forceSolid = forceSolidMenu,
                                    surfaceColor = menuSurfaceColor,
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
                    imageVector = AppIcons.CaretDown,
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
private fun readerTextAlignmentLabel(alignment: ReaderTextAlignment): String = stringResource(
    when (alignment) {
        ReaderTextAlignment.NATURAL -> R.string.text_alignment_natural
        ReaderTextAlignment.LEFT -> R.string.text_alignment_left
        ReaderTextAlignment.CENTER -> R.string.text_alignment_center
        ReaderTextAlignment.RIGHT -> R.string.text_alignment_right
        ReaderTextAlignment.JUSTIFY -> R.string.text_alignment_justify
    }
)

private fun ReaderTextAlignment.toComposeTextAlign(): TextAlign = when (this) {
    ReaderTextAlignment.NATURAL -> TextAlign.Justify
    ReaderTextAlignment.LEFT -> TextAlign.Left
    ReaderTextAlignment.CENTER -> TextAlign.Center
    ReaderTextAlignment.RIGHT -> TextAlign.Right
    ReaderTextAlignment.JUSTIFY -> TextAlign.Justify
}

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
                    imageVector = AppIcons.Plus,
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
internal fun SettingSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    step: Float,
    format: (Float) -> String,
    onChange: (Float) -> Unit,
    onPreview: ((Float) -> Unit)? = null,
    sliderActiveColor: Color = AppColors.ControlActive,
    sliderInactiveColor: Color = AppColors.BgGray,
    forceNonGlass: Boolean = false,
    sliderModifier: Modifier = Modifier,
    onInteractionChange: ((Boolean) -> Unit)? = null
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
        activeColor = sliderActiveColor,
        inactiveColor = sliderInactiveColor,
        forceNonGlass = forceNonGlass,
        onDragValueChange = { sliderValue = it; onPreview?.invoke(it) },
        onInteractionChange = onInteractionChange,
        modifier = sliderModifier
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
    onImportFont: (android.net.Uri, String) -> Unit = { _, _ -> },
    onDeleteCustomFont: (String) -> Unit = {},
    usePublisherFontLabel: Boolean = false,
    downloadingKey: String? = null,
    fontDownloadFailed: Boolean = false
) {
    val context = LocalContext.current
    var pendingImportUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var pendingImportName by remember { mutableStateOf("") }
    val liquidGlassBackdrop = LocalLiquidGlassBackdrop.current
    val launcher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            pendingImportUri = uri
            pendingImportName = importedFontName(context, uri)
        }
    }
    var deleteArmedId by remember { mutableStateOf<String?>(null) }

    val sysLabel = stringResource(if (usePublisherFontLabel) R.string.font_publisher else R.string.font_system)
    val serifLabel = stringResource(R.string.font_serif)
    val fangLabel = stringResource(R.string.font_fangsong)
    val kaiLabel  = stringResource(R.string.font_kaiti)
    val addLabel  = stringResource(R.string.font_import)
    val downloadingLabel = stringResource(R.string.font_downloading)
    val failedLabel = stringResource(R.string.font_download_failed)
    val fangSongFamilyValue = fangSongFamily()
    val serifFamily = rememberReaderSerifFontFamily()

    val items = remember(customFonts, sysLabel, serifLabel, fangLabel, kaiLabel, fangSongFamilyValue, serifFamily) {
        buildList<FontSelectorItem> {
            add(FontSelectorItem.Fixed("system",   sysLabel,  FontFamily.Default))
            add(FontSelectorItem.Fixed("serif",    serifLabel, serifFamily))
            add(FontSelectorItem.Fixed("fangsong", fangLabel, fangSongFamilyValue))
            add(FontSelectorItem.Fixed("kaiti",    kaiLabel,  KaiTi))
            customFonts.forEachIndexed { i, p -> add(FontSelectorItem.Custom(p, i)) }
            add(FontSelectorItem.AddButton)
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items.chunked(2).forEach { rowItems ->
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
                                val label = preset.displayName(
                                    stringResource(R.string.custom_font_numbered_name, item.index + 1)
                                )
                                Text(
                                    label,
                                    fontSize = if (label.codePointCount(0, label.length) > 4) 12.sp else 14.sp,
                                    fontFamily = fontFamily,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis,
                                    color = if (isSelected) AccentColor else LightTextSecondary
                                )
                                if (isDeleteArmed) {
                                    Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = 0.48f)),
                                        contentAlignment = Alignment.Center) {
                                        Icon(AppIcons.Trash, null, tint = Color.White, modifier = Modifier.size(20.dp))
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
                            Icon(AppIcons.Plus, addLabel, tint = LightTextSecondary, modifier = Modifier.size(20.dp))
                        }
                    }
                }
                // 如果这行不足 2 个，补 Spacer 占位
                repeat(2 - rowItems.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }

    pendingImportUri?.let { uri ->
        FontImportNameDialog(
            initialName = pendingImportName,
            backdrop = liquidGlassBackdrop,
            onConfirm = { name ->
                onImportFont(uri, name)
                pendingImportUri = null
            },
            onDismiss = { pendingImportUri = null }
        )
    }
}

private fun importedFontName(context: android.content.Context, uri: android.net.Uri): String {
    val rawName = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
    }.getOrNull()
    val withoutExtension = rawName?.substringBeforeLast('.', rawName)?.trim().orEmpty()
    val source = withoutExtension.ifBlank { context.getString(R.string.font_import_default_name) }
    val count = source.codePointCount(0, source.length)
    return source.substring(0, source.offsetByCodePoints(0, count.coerceAtMost(12)))
}

@Composable
private fun FontImportNameDialog(
    initialName: String,
    backdrop: com.kyant.backdrop.Backdrop?,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    var name by remember(initialName) { mutableStateOf(initialName) }
    val normalized = name.trim()
    val count = normalized.codePointCount(0, normalized.length)
    val confirm = {
        if (normalized.isNotEmpty()) {
            val end = normalized.offsetByCodePoints(0, count.coerceAtMost(12))
            onConfirm(normalized.substring(0, end))
        }
    }

    LiquidGlassDialog(
        onDismissRequest = onDismiss,
        backdrop = backdrop,
        shape = RoundedCornerShape(24.dp),
        transparencyOverride = (LocalLiquidGlassTransparency.current - 0.10f).coerceIn(0f, 0.90f),
        backgroundBlurRadius = 12.dp
    ) {
        Column(Modifier.padding(horizontal = 28.dp, vertical = 22.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.font_import_name_title),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppColors.TextPrimary
                )
                Spacer(Modifier.weight(1f))
                Text(
                    stringResource(R.string.font_import_name_count, count.coerceAtMost(12)),
                    fontSize = 12.sp,
                    color = LightTextSecondary
                )
            }
            Spacer(Modifier.height(18.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(LightBgGray)
                    .border(1.dp, LightDivider, RoundedCornerShape(14.dp))
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                BasicTextField(
                    value = name,
                    onValueChange = { value ->
                        val trimmed = value.trimStart()
                        val end = trimmed.offsetByCodePoints(
                            0,
                            trimmed.codePointCount(0, trimmed.length).coerceAtMost(12)
                        )
                        name = trimmed.substring(0, end)
                    },
                    modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                    singleLine = true,
                    textStyle = TextStyle(color = AppColors.TextPrimary, fontSize = 16.sp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { confirm() }),
                    decorationBox = { inner ->
                        if (name.isEmpty()) {
                            Text(
                                stringResource(R.string.font_import_name_hint),
                                color = LightTextSecondary,
                                fontSize = 16.sp
                            )
                        }
                        inner()
                    }
                )
            }
            Spacer(Modifier.height(18.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End)) {
                LiquidGlassTextButton(text = stringResource(R.string.cancel), onClick = onDismiss)
                LiquidGlassTextButton(
                    text = stringResource(R.string.confirm),
                    onClick = confirm,
                    enabled = normalized.isNotEmpty(),
                    tintedColor = AccentColor
                )
            }
        }
    }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
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
