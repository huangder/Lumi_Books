package com.huangder.lumibooks.ui.settings

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.spring
import androidx.compose.animation.animateContentSize
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Brightness6
import androidx.compose.material.icons.outlined.Animation
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.FontDownload
import androidx.compose.material.icons.outlined.FormatSize
import androidx.compose.material.icons.outlined.FormatBold
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material.icons.outlined.Subtitles
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.outlined.GroupAdd
import androidx.compose.material.icons.outlined.HdrOn
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Landscape
import androidx.compose.material.icons.outlined.LineWeight
import androidx.compose.material.icons.outlined.NightsStay
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material.icons.outlined.Opacity
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Source
import androidx.compose.material.icons.outlined.SwipeRightAlt
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.Title
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material.icons.outlined.SystemUpdateAlt
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import com.huangder.lumibooks.R
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.huangder.lumibooks.ui.theme.AppColors
import com.huangder.lumibooks.ui.theme.AppRadius
import com.huangder.lumibooks.ui.theme.AppSpace
import com.huangder.lumibooks.ui.theme.AppType
import com.huangder.lumibooks.tts.ExternalTtsConfig
import com.huangder.lumibooks.ui.theme.fangSongFamily
import com.huangder.lumibooks.ui.components.LiquidGlassSwitch
import com.huangder.lumibooks.ui.components.LiquidGlassAlertDialog
import com.huangder.lumibooks.ui.components.LiquidGlassDialogHost
import com.huangder.lumibooks.ui.components.LiquidGlassDialog
import com.huangder.lumibooks.ui.components.LiquidGlassSurface
import com.huangder.lumibooks.ui.components.LiquidGlassButton
import com.huangder.lumibooks.ui.components.G2ContinuousCornerShape
import com.huangder.lumibooks.ui.components.LiquidGlassIconButton
import com.huangder.lumibooks.ui.components.LiquidGlassTextButton
import com.huangder.lumibooks.ui.components.AppUpdateDialog
import com.huangder.lumibooks.ui.components.PolicyUpdateDialog
import com.huangder.lumibooks.ui.components.ProvideLiquidGlassBackdrop
import com.huangder.lumibooks.ui.components.LiquidGlassMenuHost
import com.huangder.lumibooks.ui.components.LiquidGlassMenuItem
import com.huangder.lumibooks.ui.components.LiquidGlassMenuSpec
import com.huangder.lumibooks.ui.components.LocalLiquidGlassMenuHost
import com.huangder.lumibooks.ui.theme.LocalAppTheme
import com.huangder.lumibooks.domain.model.HighlightPalette
import com.huangder.lumibooks.ui.theme.LocalLiquidGlassCapability
import com.huangder.lumibooks.ui.theme.resolveAppFontFamily
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop

// ─── 详情页通用框架 ──────────────────────────────────────────

@Composable
fun DetailPage(title: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    val isLiquidGlass = LocalAppTheme.current == "liquid_glass"
    val pageBackdrop = rememberLayerBackdrop()
    val controlsBackdrop = rememberLayerBackdrop()

    LiquidGlassMenuHost(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.WindowBg),
        backdrop = pageBackdrop.takeIf { isLiquidGlass }
    ) {
        LiquidGlassDialogHost(
            modifier = Modifier.fillMaxSize(),
            backdrop = pageBackdrop.takeIf { isLiquidGlass }
        ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(if (isLiquidGlass) Modifier.layerBackdrop(pageBackdrop) else Modifier)
        ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .then(if (isLiquidGlass) Modifier.layerBackdrop(controlsBackdrop) else Modifier)
                .background(AppColors.WindowBg)
        )
        ProvideLiquidGlassBackdrop(controlsBackdrop.takeIf { isLiquidGlass }) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(AppColors.WindowBg)
                .statusBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 顶栏
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AppSpace.sm, vertical = AppSpace.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LiquidGlassIconButton(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = stringResource(R.string.back),
                    onClick = onBack,
                    settingsBackButton = true
                )
                Spacer(Modifier.weight(1f))
                Text(title, fontSize = AppType.Section, fontWeight = FontWeight.Bold, fontFamily = resolveAppFontFamily(fangSongFamily()), color = AppColors.TextPrimary)
                Spacer(Modifier.weight(1f))
                Spacer(Modifier.size(48.dp))
            }
            Column(
                modifier = Modifier
                    .widthIn(max = 840.dp)
                    .fillMaxWidth()
                    .weight(1f)
                    .imePadding()
                    .verticalScroll(rememberScrollState())
            ) {
                Spacer(Modifier.height(AppSpace.sm))
                content()
                Spacer(Modifier.height(120.dp))
            }
        }
        }
        }
        }
    }
}

// ─── 阅读设置 ────────────────────────────────────────────────

@Composable
fun ReadingSettingsDetail(viewModel: SettingsViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    DetailCard {
        // 正文字重
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(AppSpace.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Outlined.FormatBold,
                contentDescription = null,
                tint = AppColors.TextSecondary,
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.width(AppSpace.md))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.body_font_weight),
                    fontSize = AppType.Body,
                    color = AppColors.TextPrimary
                )
                Text(
                    stringResource(R.string.body_font_weight_desc),
                    fontSize = AppType.Caption,
                    color = AppColors.TextSecondary
                )
            }
            LiquidGlassSwitch(
                checked = uiState.bodyFontWeight >= 600,
                onCheckedChange = {
                    viewModel.saveBodyFontWeight(if (it) 700 else 400)
                }
            )
        }
    }

    Spacer(Modifier.height(12.dp))

    DetailCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    context.startActivity(Intent(context, DetailActivity::class.java).putExtra("category", "highlight_color"))
                }
                .padding(AppSpace.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Outlined.Palette,
                contentDescription = null,
                tint = AppColors.TextSecondary,
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.width(AppSpace.md))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.highlight_color_palette),
                    fontSize = AppType.Body,
                    color = AppColors.TextPrimary
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    stringResource(R.string.highlight_color_palette_desc),
                    fontSize = AppType.Caption,
                    color = AppColors.TextSecondary
                )
            }
            Icon(Icons.Outlined.ChevronRight, null, tint = AppColors.TextSecondary, modifier = Modifier.size(20.dp))
        }
    }

    Spacer(Modifier.height(12.dp))

    // 听书悬浮窗开关
    DetailCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(AppSpace.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Outlined.Subtitles,
                contentDescription = null,
                tint = AppColors.TextSecondary,
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.width(AppSpace.md))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.tts_floating_toggle),
                    fontSize = AppType.Body,
                    color = AppColors.TextPrimary
                )
                Text(
                    stringResource(R.string.tts_floating_toggle_desc),
                    fontSize = AppType.Caption,
                    color = AppColors.TextSecondary
                )
            }
            LiquidGlassSwitch(
                checked = uiState.ttsFloatingWindow,
                onCheckedChange = { viewModel.saveTtsFloatingWindow(it) }
            )
        }
    }

    Spacer(Modifier.height(12.dp))

    // 语音引擎选择
    val installedEngines = remember { viewModel.getInstalledTtsEngines() }
    val selectedEngineLabel = installedEngines.find { it.first == uiState.preferredTtsEngine }?.second
        ?: stringResource(R.string.tts_engine_system_default)
    var showEngineDialog by remember { mutableStateOf(false) }
    DetailCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showEngineDialog = true }
                .padding(AppSpace.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Outlined.RecordVoiceOver,
                contentDescription = null,
                tint = AppColors.TextSecondary,
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.width(AppSpace.md))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.tts_engine_selection),
                    fontSize = AppType.Body,
                    color = AppColors.TextPrimary
                )
                Text(
                    selectedEngineLabel,
                    fontSize = AppType.Caption,
                    color = AppColors.TextSecondary
                )
            }
            Icon(Icons.Outlined.ChevronRight, null, tint = AppColors.TextSecondary, modifier = Modifier.size(20.dp))
        }
    }
    if (showEngineDialog) {
        AlertDialog(
            onDismissRequest = { showEngineDialog = false },
            title = { Text(stringResource(R.string.tts_engine_select_dialog_title)) },
            text = {
                Column {
                    // 系统默认选项
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                viewModel.savePreferredTtsEngine(null)
                                showEngineDialog = false
                            }
                            .padding(vertical = 12.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = uiState.preferredTtsEngine == null,
                            onClick = {
                                viewModel.savePreferredTtsEngine(null)
                                showEngineDialog = false
                            }
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            stringResource(R.string.tts_engine_system_default),
                            fontSize = AppType.Body,
                            color = AppColors.TextPrimary
                        )
                    }
                    // 已安装引擎列表
                    installedEngines.forEach { (pkg, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.savePreferredTtsEngine(pkg)
                                    showEngineDialog = false
                                }
                                .padding(vertical = 12.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = uiState.preferredTtsEngine == pkg,
                                onClick = {
                                    viewModel.savePreferredTtsEngine(pkg)
                                    showEngineDialog = false
                                }
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                label,
                                fontSize = AppType.Body,
                                color = AppColors.TextPrimary
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showEngineDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    Spacer(Modifier.height(12.dp))

    // 排版作用范围
    DetailCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(AppSpace.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Outlined.TextFields,
                contentDescription = null,
                tint = AppColors.TextSecondary,
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.width(AppSpace.md))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.apply_to_body_only),
                    fontSize = AppType.Body,
                    color = AppColors.TextPrimary
                )
                Text(
                    stringResource(R.string.apply_to_body_only_desc),
                    fontSize = AppType.Caption,
                    color = AppColors.TextSecondary
                )
            }
            Spacer(Modifier.width(AppSpace.sm))
            LiquidGlassSwitch(
                checked = uiState.applyToBodyOnly,
                onCheckedChange = { viewModel.saveApplyToBodyOnly(it) }
            )
        }
    }
}

@Composable
private fun ReadingSettingsBasicDetail(viewModel: SettingsViewModel) {
    val uiState by viewModel.uiState.collectAsState()

    DetailCard {
        SettingsSliderItem(Icons.Outlined.FormatSize, stringResource(R.string.label_font_size), uiState.fontSize, 12f..28f, "${uiState.fontSize.toInt()} sp", step = 1f) { viewModel.saveFontSize(it) }
        SettingsDivider()
        SettingsSliderItem(Icons.Outlined.LineWeight, stringResource(R.string.label_line_height), uiState.lineHeight, 1.0f..2.5f, String.format("%.1f", uiState.lineHeight)) { viewModel.saveLineHeight(it) }
        SettingsDivider()
        SettingsSliderItem(Icons.Outlined.Title, stringResource(R.string.label_letter_spacing), uiState.letterSpacing, 0f..0.1f, String.format("%.2f em", uiState.letterSpacing), step = 0.01f) { viewModel.saveLetterSpacing(it) }
        SettingsDivider()
        FontTypeRow(uiState.fontType) { viewModel.saveFontType(it) }
        SettingsDivider()
        SettingsSliderItem(Icons.Outlined.Landscape, stringResource(R.string.label_margin_horiz), uiState.marginHoriz, 0f..80f, "${uiState.marginHoriz.toInt()} dp", step = 1f) { viewModel.saveMarginHoriz(it) }
        SettingsDivider()
        SettingsSliderItem(Icons.Outlined.Landscape, stringResource(R.string.label_margin_vert), uiState.marginVert, 0f..120f, "${uiState.marginVert.toInt()} dp", step = 1f) { viewModel.saveMarginVert(it) }
    }
}

// ─── 显示与外观 ──────────────────────────────────────────────

@Composable
fun DisplayDetail(viewModel: SettingsViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val liquidGlassCapability = LocalLiquidGlassCapability.current
    val liquidGlassSupported = liquidGlassCapability.supported
    val appThemeOptions = listOf(
        "lumi" to stringResource(R.string.app_theme_lumi),
        "material3" to stringResource(R.string.app_theme_material3)
    ) + if (uiState.eInkModeEnabled || !liquidGlassSupported) {
        emptyList()
    } else {
        listOf("liquid_glass" to stringResource(R.string.app_theme_liquid_glass))
    }
    val globalFontOptions = listOf(
        "default" to stringResource(R.string.global_font_default),
        "system" to stringResource(R.string.global_font_system)
    )
    val darkModeOptions = if (uiState.eInkModeEnabled) {
        listOf("light" to stringResource(R.string.dark_mode_light))
    } else {
        listOf(
            "system" to stringResource(R.string.dark_mode_system),
            "light" to stringResource(R.string.dark_mode_light),
            "dark" to stringResource(R.string.dark_mode_dark)
        )
    }
    val themeOptions = if (uiState.eInkModeEnabled) {
        listOf("day" to stringResource(R.string.theme_day))
    } else {
        listOf(
            "day" to stringResource(R.string.theme_day),
            "night" to stringResource(R.string.theme_night),
            "sepia" to stringResource(R.string.theme_sepia),
            "green" to stringResource(R.string.theme_green)
        )
    }

    DetailCard {
        DropdownSettingRow(
            icon = Icons.Outlined.Palette,
            label = stringResource(R.string.label_app_theme),
            options = appThemeOptions,
            selected = if (
                (uiState.eInkModeEnabled || !liquidGlassSupported) && uiState.appTheme == "liquid_glass"
            ) "lumi" else uiState.appTheme,
            onSelect = viewModel::saveAppTheme
        )
        SettingsDivider()
        DropdownSettingRow(
            icon = Icons.Outlined.FontDownload,
            label = stringResource(R.string.label_global_font),
            options = globalFontOptions,
            selected = uiState.globalFontMode,
            onSelect = viewModel::saveGlobalFontMode
        )
    }

    Spacer(Modifier.height(12.dp))

    DetailCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(AppSpace.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Outlined.Speed,
                contentDescription = null,
                tint = AppColors.TextSecondary,
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.width(AppSpace.md))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.label_e_ink_mode),
                    fontSize = AppType.Body,
                    color = AppColors.TextPrimary
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    stringResource(R.string.e_ink_mode_description),
                    fontSize = AppType.Caption,
                    color = AppColors.TextSecondary
                )
            }
            Spacer(Modifier.width(12.dp))
            LiquidGlassSwitch(
                checked = uiState.eInkModeEnabled,
                onCheckedChange = viewModel::saveEInkModeEnabled
            )
        }
    }

    Spacer(Modifier.height(12.dp))

    DetailCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(AppSpace.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Outlined.Landscape,
                contentDescription = null,
                tint = AppColors.TextSecondary,
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.width(AppSpace.md))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.label_two_page_spread),
                    fontSize = AppType.Body,
                    color = AppColors.TextPrimary
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    stringResource(R.string.two_page_spread_description),
                    fontSize = AppType.Caption,
                    color = AppColors.TextSecondary
                )
            }
            Spacer(Modifier.width(12.dp))
            LiquidGlassSwitch(
                checked = uiState.twoPageSpreadEnabled,
                onCheckedChange = viewModel::saveTwoPageSpreadEnabled
            )
        }
    }


    AnimatedVisibility(
        visible = uiState.appTheme == "liquid_glass" && !uiState.eInkModeEnabled && liquidGlassSupported,
        enter = expandVertically(animationSpec = tween(260)) +
            slideInVertically(animationSpec = tween(260)) { it / 3 } +
            fadeIn(animationSpec = tween(180)),
        exit = shrinkVertically(animationSpec = tween(180)) +
            slideOutVertically(animationSpec = tween(180)) { it / 4 } +
            fadeOut(animationSpec = tween(130))
    ) {
        Column {
            Spacer(Modifier.height(12.dp))
            DetailCard {
                Column(Modifier.padding(vertical = 5.dp)) {
                    SettingsSliderItem(
                        icon = Icons.Outlined.Opacity,
                        label = stringResource(R.string.liquid_glass_transparency),
                        value = uiState.liquidGlassTransparency,
                        range = 0f..1f,
                        valueText = "${(uiState.liquidGlassTransparency * 100).toInt()}%",
                        step = 0.05f,
                        onDragChange = viewModel::previewLiquidGlassTransparency,
                        onChange = viewModel::saveLiquidGlassTransparency
                    )
                    if (liquidGlassCapability.hdrSupported) {
                        SettingsDivider()
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = AppSpace.md, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Outlined.HdrOn,
                                contentDescription = null,
                                tint = AppColors.TextSecondary,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(Modifier.width(AppSpace.md))
                            Text(
                                stringResource(R.string.liquid_glass_hdr_highlight),
                                fontSize = AppType.Body,
                                color = AppColors.TextPrimary,
                                modifier = Modifier.weight(1f)
                            )
                            LiquidGlassSwitch(
                                checked = uiState.liquidGlassHdrHighlightEnabled,
                                onCheckedChange = viewModel::saveLiquidGlassHdrHighlightEnabled
                            )
                        }
                    }
                }
            }
        }
    }

    Spacer(Modifier.height(12.dp))

    DetailCard {
        DropdownSettingRow(
            icon = Icons.Outlined.Brightness6,
            label = stringResource(R.string.label_dark_mode),
            options = darkModeOptions,
            selected = if (uiState.eInkModeEnabled) "light" else uiState.darkMode,
            onSelect = viewModel::saveDarkMode
        )
    }

    Spacer(Modifier.height(12.dp))

    DetailCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(AppSpace.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Outlined.SwipeRightAlt,
                contentDescription = null,
                tint = AppColors.TextSecondary,
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.width(AppSpace.md))
            Text(
                stringResource(R.string.label_predictive_back),
                fontSize = AppType.Body,
                color = AppColors.TextPrimary,
                modifier = Modifier.weight(1f)
            )
            LiquidGlassSwitch(
                checked = uiState.predictiveBackEnabled,
                onCheckedChange = viewModel::savePredictiveBackEnabled
            )
        }
    }

    Spacer(Modifier.height(12.dp))

    DetailCard {
        DropdownSettingRow(
            icon = Icons.Outlined.Animation,
            label = stringResource(R.string.motion_preference_label),
            options = listOf(
                "standard" to stringResource(R.string.motion_preference_standard),
                "reduced" to stringResource(R.string.motion_preference_reduced)
            ),
            selected = uiState.motionPreference,
            onSelect = viewModel::saveMotionPreference
        )
    }

    Spacer(Modifier.height(12.dp))

    DetailCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { viewModel.saveSplashEnabled(!uiState.splashEnabled) }
                .padding(AppSpace.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Outlined.PhoneAndroid,
                contentDescription = null,
                tint = AppColors.TextSecondary,
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.width(AppSpace.md))
            Text(
                stringResource(R.string.label_splash_screen),
                fontSize = AppType.Body,
                color = AppColors.TextPrimary,
                modifier = Modifier.weight(1f)
            )
            LiquidGlassSwitch(
                checked = uiState.splashEnabled,
                onCheckedChange = null
            )
        }
    }

    Spacer(Modifier.height(12.dp))

    DetailCard {
        DropdownSettingRow(
            icon = Icons.Outlined.Palette,
            label = stringResource(R.string.label_reader_theme),
            options = themeOptions,
            selected = if (uiState.eInkModeEnabled) "day" else uiState.readerTheme,
            onSelect = viewModel::saveReaderTheme
        )
    }
}

// ─── 阅读目标 ────────────────────────────────────────────────

@Composable
fun ReadingGoalDetail(viewModel: SettingsViewModel) {
    val uiState by viewModel.uiState.collectAsState()

    DetailCard {
        SettingsSliderItem(Icons.Outlined.Timer, stringResource(R.string.label_daily_goal), uiState.dailyGoal.toFloat(), 10f..120f, stringResource(R.string.goal_minutes, uiState.dailyGoal), steps = 21) { viewModel.saveDailyGoal(it.toInt()) }
    }
}

// ─── 高亮颜色色卡 ────────────────────────────────────────────

private val presetColorOptions = listOf(
    "#D6C58D", "#CFA09A", "#A7B59D", "#9DAFC1", "#B2A198", "#AFB0AC",
    "#FFD700", "#FF6B6B", "#4ECDC4", "#45B7D1", "#96CEB4", "#FFEAA7",
    "#DDA0DD", "#98D8C8", "#F7DC6F", "#BB8FCE", "#85C1E9", "#F8C471",
    "#82E0AA", "#F1948A", "#AED6F1", "#D7BDE2", "#A3E4D7", "#FAD7A0",
    "#E59866", "#ABEBC6", "#D5F5E3", "#FADBD8", "#D6EAF8", "#E8DAEF"
)

private val defaultHighlightColors = listOf(
    "#D6C58D", "#CFA09A", "#A7B59D", "#9DAFC1", "#B2A198", "#AFB0AC"
)

@Composable
fun HighlightColorDetail(viewModel: SettingsViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val palettes = remember { mutableStateListOf<HighlightPalette>() }
    LaunchedEffect(uiState.customHighlightPalettes) {
        if (palettes.toList() != uiState.customHighlightPalettes) {
            palettes.clear()
            palettes.addAll(uiState.customHighlightPalettes)
        }
    }
    var showCreateDialog by remember { mutableStateOf(false) }
    var paletteName by remember { mutableStateOf("") }
    var colorTarget by remember { mutableStateOf<Pair<Int, Int>?>(null) }

    fun savePalettes() = viewModel.saveCustomHighlightPalettes(palettes.toList())

    fun updateSlot(paletteIndex: Int, slotIndex: Int, hex: String) {
        val palette = palettes.getOrNull(paletteIndex) ?: return
        val updated = palette.normalizedColors.toMutableList().apply { this[slotIndex] = hex }
        palettes[paletteIndex] = palette.copy(colors = updated)
        savePalettes()
    }

    fun deletePalette(index: Int) {
        val removed = palettes.removeAt(index)
        if (uiState.activeHighlightPaletteId == removed.id) viewModel.saveActiveHighlightPalette(null)
        savePalettes()
    }

    val density = androidx.compose.ui.platform.LocalDensity.current
    val g2CardShape = remember(density) {
        G2ContinuousCornerShape(with(density) { 26.dp.toPx() })
    }

    Column(Modifier.fillMaxWidth().padding(vertical = AppSpace.sm)) {
        DetailCard {
            Column(Modifier.padding(AppSpace.md)) {
                Text(
                    stringResource(R.string.highlight_color_palette_tip),
                    fontSize = AppType.BodySmall,
                    color = AppColors.TextSecondary
                )
            }
        }
        Spacer(Modifier.height(AppSpace.sm))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AppSpace.md)
                .animateContentSize(animationSpec = tween(360))
                .background(AppColors.CardBg, g2CardShape)
        ) {
            Column(Modifier.padding(AppSpace.md)) {
                Text(
                    stringResource(R.string.highlight_all_palettes),
                    fontSize = AppType.Section,
                    fontWeight = FontWeight.Bold,
                    color = AppColors.TextPrimary
                )
                Spacer(Modifier.height(AppSpace.md))

                HighlightPaletteRow(
                    palette = HighlightPalette(
                        id = "default",
                        name = stringResource(R.string.highlight_default_palette),
                        colors = defaultHighlightColors
                    ),
                    selected = uiState.activeHighlightPaletteId == null,
                    isDefault = true,
                    shape = g2CardShape,
                    onSelect = { viewModel.saveActiveHighlightPalette(null) },
                    onSlotClick = {}
                )

                palettes.forEachIndexed { index, palette ->
                    Spacer(Modifier.height(AppSpace.sm))
                    HighlightPaletteRow(
                        palette = palette,
                        selected = uiState.activeHighlightPaletteId == palette.id,
                        isDefault = false,
                        shape = g2CardShape,
                        onSelect = { viewModel.saveActiveHighlightPalette(palette.id) },
                        onSlotClick = { slot -> colorTarget = index to slot },
                        onDelete = { deletePalette(index) }
                    )
                }

                Spacer(Modifier.height(AppSpace.lg))
                LiquidGlassButton(
                    onClick = {
                        paletteName = ""
                        showCreateDialog = true
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = g2CardShape,
                    tintedColor = AppColors.Accent,
                    prominentShadow = true,
                    contentColor = AppColors.OnAccent
                ) {
                    Icon(Icons.Outlined.Add, null, modifier = Modifier.size(18.dp), tint = Color.White)
                    Spacer(Modifier.width(AppSpace.sm))
                    Text(
                        stringResource(R.string.highlight_create_palette),
                        color = Color.White,
                        fontSize = AppType.BodySmall
                    )
                }
            }
        }
    }

    if (showCreateDialog) {
        LiquidGlassDialog(
            onDismissRequest = { showCreateDialog = false },
            backgroundScrimColor = Color.Black.copy(alpha = 0.20f),
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
        ) {
            com.huangder.lumibooks.ui.components.EditInputDialog(
                title = stringResource(R.string.highlight_create_palette),
                fields = listOf(
                    Triple(
                        stringResource(R.string.highlight_palette_name),
                        stringResource(R.string.highlight_palette_name_hint),
                        paletteName
                    )
                ),
                onBack = { showCreateDialog = false },
                onConfirm = { values ->
                    val name = values.firstOrNull()?.trim().orEmpty()
                    if (name.isNotBlank()) {
                        val palette = HighlightPalette(name = name)
                        palettes.add(palette)
                        savePalettes()
                        viewModel.saveActiveHighlightPalette(palette.id)
                        showCreateDialog = false
                    }
                }
            )
        }
    }

    colorTarget?.let { (paletteIndex, slotIndex) ->
        HighlightPaletteColorDialog(
            onDismiss = { colorTarget = null },
            onColorSelected = { hex ->
                updateSlot(paletteIndex, slotIndex, hex)
                colorTarget = null
            }
        )
    }
}

@Composable
private fun HighlightPaletteRow(
    palette: HighlightPalette,
    selected: Boolean,
    isDefault: Boolean,
    shape: androidx.compose.ui.graphics.Shape,
    onSelect: () -> Unit,
    onSlotClick: (Int) -> Unit,
    onDelete: () -> Unit = {}
) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    val revealWidth = with(density) { 72.dp.toPx() }
    var dragOffset by remember(palette.id) { mutableFloatStateOf(0f) }
    var isDragging by remember(palette.id) { mutableStateOf(false) }
    fun resistedOffset(rawOffset: Float): Float {
        fun rubberBand(distance: Float): Float {
            val resisted = revealWidth * (1f - 1f / (1f + distance * 0.70f / revealWidth))
            return resisted.coerceAtMost(with(density) { 14.dp.toPx() })
        }
        return when {
            rawOffset < -revealWidth -> -revealWidth - rubberBand(-revealWidth - rawOffset)
            rawOffset > 0f -> rubberBand(rawOffset)
            else -> rawOffset
        }
    }
    val visualTarget = if (isDragging) resistedOffset(dragOffset) else dragOffset
    val animatedOffset by androidx.compose.animation.core.animateFloatAsState(
        targetValue = visualTarget,
        animationSpec = if (isDragging) androidx.compose.animation.core.snap()
        else spring(dampingRatio = 0.62f, stiffness = 360f),
        label = "paletteReveal"
    )
    val currentAnimatedOffset = androidx.compose.runtime.rememberUpdatedState(animatedOffset)
    val revealProgress = (-animatedOffset / revealWidth).coerceIn(0f, 1f)
    val deleteButtonTravel = with(density) { 35.dp.toPx() }

    Box(Modifier.fillMaxWidth().height(70.dp)) {
        if (!isDefault) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 12.dp)
                    .size(46.dp)
                    .graphicsLayer {
                        alpha = revealProgress
                        translationX = deleteButtonTravel * (1f - revealProgress)
                    }
                    .clip(CircleShape)
                    .background(AppColors.Accent.copy(alpha = 0.88f)),
                contentAlignment = Alignment.Center
            ) {
                IconButton(onClick = onDelete, modifier = Modifier.fillMaxSize()) {
                    Icon(
                        Icons.Outlined.DeleteForever,
                        stringResource(R.string.highlight_palette_delete),
                        tint = AppColors.OnAccent,
                        modifier = Modifier.size(19.dp)
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { translationX = if (isDefault) 0f else animatedOffset }
                .clip(shape)
                .background(AppColors.BgGray)
                .then(if (selected) Modifier.border(1.5.dp, AppColors.Accent, shape) else Modifier)
                .pointerInput(revealWidth, isDefault) {
                    if (!isDefault) {
                        detectHorizontalDragGestures(
                            onDragStart = {
                                dragOffset = currentAnimatedOffset.value
                                isDragging = true
                            },
                            onHorizontalDrag = { change, dragAmount ->
                                change.consume()
                                dragOffset += dragAmount
                            },
                            onDragEnd = {
                                dragOffset = if (resistedOffset(dragOffset) < -revealWidth * 0.35f) -revealWidth else 0f
                                isDragging = false
                            },
                            onDragCancel = {
                                dragOffset = 0f
                                isDragging = false
                            }
                        )
                    }
                }
                .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { onSelect() }
                .padding(horizontal = AppSpace.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                palette.normalizedColors.forEachIndexed { index, hex ->
                    HighlightPaletteSlot(hex, !isDefault) { onSlotClick(index) }
                }
            }
            Spacer(Modifier.width(AppSpace.sm))
            Text(
                palette.name,
                fontSize = AppType.Body,
                color = AppColors.TextSecondary,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun HighlightPaletteSlot(hex: String?, enabled: Boolean, onClick: () -> Unit) {
    val color = hex?.let { runCatching { Color(android.graphics.Color.parseColor(it)) }.getOrNull() }
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(CircleShape)
            .then(
                if (color != null) Modifier.background(color).border(1.dp, AppColors.Divider, CircleShape)
                else Modifier.border(1.5.dp, AppColors.TextSecondary.copy(alpha = 0.42f), CircleShape)
            )
            .clickable(enabled = enabled, indication = null, interactionSource = remember { MutableInteractionSource() }, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (color == null) {
            Icon(Icons.Outlined.Add, stringResource(R.string.highlight_palette_set_color), tint = AppColors.TextSecondary.copy(alpha = 0.66f), modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun HighlightPaletteColorDialog(onDismiss: () -> Unit, onColorSelected: (String) -> Unit) {
    var customHex by remember { mutableStateOf("") }
    var customError by remember { mutableStateOf(false) }
    val density = androidx.compose.ui.platform.LocalDensity.current
    val shape = remember(density) { G2ContinuousCornerShape(with(density) { 30.dp.toPx() }) }
    LiquidGlassDialog(
        onDismissRequest = onDismiss,
        backgroundScrimColor = Color.Black.copy(alpha = 0.20f),
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        LiquidGlassSurface(
            shape = shape,
            fallbackColor = AppColors.CardBg,
            contentScrimColor = AppColors.CardBg.copy(alpha = 0.82f),
            modifier = Modifier.fillMaxWidth().widthIn(max = 360.dp)
        ) {
            Column(Modifier.padding(AppSpace.lg)) {
                Text(stringResource(R.string.highlight_palette_set_color), fontSize = AppType.Section, fontWeight = FontWeight.Bold, color = AppColors.TextPrimary)
                Spacer(Modifier.height(AppSpace.md))
                Text(stringResource(R.string.highlight_pick_preset), fontSize = AppType.BodySmall, color = AppColors.TextSecondary)
                Spacer(Modifier.height(AppSpace.sm))
                presetColorOptions.chunked(6).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        row.forEach { hex ->
                            val color = runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrDefault(Color.Gray)
                            Box(
                                Modifier.size(36.dp).clip(CircleShape).background(color)
                                    .border(1.dp, AppColors.Divider, CircleShape)
                                    .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { onColorSelected(hex) }
                            )
                        }
                        repeat(6 - row.size) { Spacer(Modifier.size(36.dp)) }
                    }
                    Spacer(Modifier.height(8.dp))
                }
                Spacer(Modifier.height(AppSpace.sm))
                OutlinedTextField(
                    value = customHex,
                    onValueChange = { customHex = it; customError = false },
                    label = { Text(stringResource(R.string.highlight_color_hex), fontSize = AppType.Caption) },
                    isError = customError,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = TextStyle(fontSize = AppType.BodySmall)
                )
                Spacer(Modifier.height(AppSpace.sm))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
                    LiquidGlassTextButton(
                        text = stringResource(R.string.confirm),
                        onClick = {
                            val value = customHex.trim()
                            if (value.matches(Regex("^#?[0-9A-Fa-f]{6}$"))) {
                                onColorSelected(if (value.startsWith("#")) value else "#$value")
                            } else {
                                customError = true
                            }
                        },
                        tintedColor = AppColors.Accent,
                        contentColor = AppColors.OnAccent
                    )
                }
            }
        }
    }
}

// ─── 存储管理 ────────────────────────────────────────────────

/** 分段色条颜色 */
private val SegmentColors = listOf(
    Color(0xFF4A90D9),  // 应用本体 - 蓝
    Color(0xFF9B9B9B),  // 缓存文件 - 灰
    Color(0xFFE85D5D),  // 电子书文件 - 主题红
    Color(0xFFF5A623),  // 封面图片 - 橙黄
)

/** 格式标签颜色 */
private val FormatColors = mapOf(
    "EPUB" to Color(0xFF4CAF50),
    "PDF" to Color(0xFFE85D5D),
    "TXT" to Color(0xFF9B9B9B)
)

@Composable
fun StorageDetail(viewModel: SettingsViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val info = uiState.storageInfo
    var showClearDialog by remember { mutableStateOf(false) }
    var showClearExternalTtsCacheDialog by remember { mutableStateOf(false) }
    val externalTtsCacheColor = AppColors.Accent

    Column {
        // ── 总览卡片 ──
        DetailCard {
            Column(Modifier.fillMaxWidth().padding(AppSpace.md)) {
                // 标题行
                val totalBytes = info.appSizeBytes + info.cacheSizeBytes + info.externalTtsCacheSizeBytes + info.booksSizeBytes + info.coversSizeBytes
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Speed, null, tint = AppColors.TextSecondary, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(AppSpace.sm))
                    Text(stringResource(R.string.label_total_size), fontSize = AppType.Body, color = AppColors.TextPrimary, modifier = Modifier.weight(1f))
                    Text(viewModel.formatFileSize(totalBytes), fontSize = AppType.Section, fontWeight = FontWeight.Bold, color = AppColors.Accent)
                }

                Spacer(Modifier.height(AppSpace.sm))

                // 分段色条
                if (totalBytes > 0) {
                    val segments = listOf(
                        info.appSizeBytes to SegmentColors[0],
                        info.cacheSizeBytes to SegmentColors[1],
                        info.externalTtsCacheSizeBytes to externalTtsCacheColor,
                        info.booksSizeBytes to SegmentColors[2],
                        info.coversSizeBytes to SegmentColors[3]
                    ).filter { it.first > 0 }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                    ) {
                        segments.forEach { (bytes, color) ->
                            val weight = (bytes.toFloat() / totalBytes).coerceAtLeast(0.02f)
                            Box(
                                modifier = Modifier
                                    .weight(weight)
                                    .fillMaxSize()
                                    .background(color)
                            )
                        }
                    }

                    Spacer(Modifier.height(AppSpace.md))

                    // 分类明细
                    val categories = listOf(
                        stringResource(R.string.storage_app) to (info.appSizeBytes to SegmentColors[0]),
                        stringResource(R.string.storage_cache) to (info.cacheSizeBytes to SegmentColors[1]),
                        stringResource(R.string.external_tts_audio_cache) to (info.externalTtsCacheSizeBytes to externalTtsCacheColor),
                        stringResource(R.string.storage_books) to (info.booksSizeBytes to SegmentColors[2]),
                        stringResource(R.string.storage_covers) to (info.coversSizeBytes to SegmentColors[3])
                    )
                    categories.forEach { (label, pair) ->
                        val (bytes, color) = pair
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(color)
                            )
                            Spacer(Modifier.width(AppSpace.sm))
                            Text(label, fontSize = AppType.BodySmall, color = AppColors.TextPrimary, modifier = Modifier.weight(1f))
                            Text(
                                viewModel.formatFileSize(bytes),
                                fontSize = AppType.BodySmall,
                                color = AppColors.TextSecondary
                            )
                        }
                    }
                } else {
                    Text(stringResource(R.string.calculating), fontSize = AppType.BodySmall, color = AppColors.TextSecondary)
                }
            }
        }

        // ── 书籍明细卡片 ──
        if (info.bookDetails.isNotEmpty()) {
            Spacer(Modifier.height(AppSpace.md))
            DetailCard {
                Column(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.fillMaxWidth().padding(AppSpace.md),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            stringResource(R.string.storage_books_count, info.bookDetails.size),
                            fontSize = AppType.Body,
                            fontWeight = FontWeight.SemiBold,
                            color = AppColors.TextPrimary,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            viewModel.formatFileSize(info.booksSizeBytes),
                            fontSize = AppType.BodySmall,
                            color = AppColors.TextSecondary
                        )
                    }
                    SettingsDivider()
                    info.bookDetails.forEach { item ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = AppSpace.md, vertical = AppSpace.md),
                            verticalAlignment = Alignment.Top
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    item.title,
                                    fontSize = AppType.BodySmall,
                                    color = AppColors.TextPrimary,
                                    maxLines = 1
                                )
                                Spacer(Modifier.height(4.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    val fmtColor = FormatColors[item.format] ?: Color.Gray
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(3.dp))
                                            .background(fmtColor.copy(alpha = 0.12f))
                                            .padding(horizontal = 5.dp, vertical = 1.dp)
                                    ) {
                                        Text(
                                            item.format,
                                            fontSize = 10.sp,
                                            color = fmtColor,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.width(AppSpace.sm))
                            Text(
                                viewModel.formatFileSize(item.sizeBytes),
                                fontSize = AppType.BodySmall,
                                color = AppColors.TextSecondary,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }

        // ── 外部 TTS 音频缓存 ──
        Spacer(Modifier.height(AppSpace.md))
        DetailCard {
            Row(
                modifier = Modifier.fillMaxWidth().padding(AppSpace.md),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Outlined.VolumeUp, null, tint = externalTtsCacheColor, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(AppSpace.md))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.external_tts_audio_cache),
                        fontSize = AppType.Body,
                        color = AppColors.TextPrimary
                    )
                    Text(
                        stringResource(
                            R.string.external_tts_cache_usage,
                            viewModel.formatFileSize(info.externalTtsCacheSizeBytes)
                        ),
                        fontSize = AppType.BodySmall,
                        color = AppColors.TextSecondary
                    )
                }
            }
            SettingsDivider()
            SettingsSliderItem(
                icon = Icons.Outlined.Timer,
                label = stringResource(R.string.external_tts_cache_limit),
                value = info.externalTtsCacheLimitMb.toFloat(),
                range = ExternalTtsConfig.MIN_AUDIO_CACHE_LIMIT_MB.toFloat()..ExternalTtsConfig.MAX_AUDIO_CACHE_LIMIT_MB.toFloat(),
                valueText = stringResource(R.string.external_tts_cache_limit_value, info.externalTtsCacheLimitMb),
                step = 32f,
                onChange = { viewModel.saveExternalTtsCacheLimitMb(it.toInt()) }
            )
            Text(
                stringResource(R.string.external_tts_cache_limit_desc),
                modifier = Modifier.fillMaxWidth().padding(horizontal = AppSpace.md, vertical = AppSpace.sm),
                fontSize = AppType.BodySmall,
                color = AppColors.TextSecondary
            )
            SettingsDivider()
            ActionRow(
                Icons.Outlined.DeleteSweep,
                stringResource(R.string.clear_external_tts_cache),
                Color.Red
            ) {
                showClearExternalTtsCacheDialog = true
            }
        }

        Spacer(Modifier.height(AppSpace.md))

        DetailCard {
            ActionRow(Icons.Outlined.DeleteSweep, stringResource(R.string.clear_cache)) { viewModel.clearCache() }
            SettingsDivider()
            ActionRow(Icons.Outlined.DeleteForever, stringResource(R.string.clear_all_data), Color.Red) { showClearDialog = true }
        }
    }

    if (showClearExternalTtsCacheDialog) {
        LiquidGlassAlertDialog(
            onDismissRequest = { showClearExternalTtsCacheDialog = false },
            title = { Text(stringResource(R.string.clear_external_tts_cache)) },
            text = { Text(stringResource(R.string.clear_external_tts_cache_confirm)) },
            confirmButton = {
                LiquidGlassTextButton(
                    text = stringResource(R.string.clear),
                    onClick = {
                        viewModel.clearExternalTtsAudioCache()
                        showClearExternalTtsCacheDialog = false
                    },
                    tintedColor = Color.Red
                )
            },
            dismissButton = {
                LiquidGlassTextButton(
                    text = stringResource(R.string.cancel),
                    onClick = { showClearExternalTtsCacheDialog = false }
                )
            }
        )
    }

    if (showClearDialog) {
        LiquidGlassAlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text(stringResource(R.string.clear_all_data)) },
            text = { Text(stringResource(R.string.clear_all_confirm)) },
            confirmButton = {
                LiquidGlassTextButton(
                    text = stringResource(R.string.clear),
                    onClick = { viewModel.clearAllData(); showClearDialog = false },
                    tintedColor = Color.Red
                )
            },
            dismissButton = {
                LiquidGlassTextButton(
                    text = stringResource(R.string.cancel),
                    onClick = { showClearDialog = false }
                )
            }
        )
    }
}

// ─── 备份与恢复 ──────────────────────────────────────────────

@Composable
fun BackupRestoreDetail(viewModel: SettingsViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val genericErrorText = stringResource(R.string.error)
    val backupPickerFailureText = stringResource(R.string.backup_failed, genericErrorText)
    val restorePickerFailureText = stringResource(R.string.restore_failed, genericErrorText)

    // 备份：创建文件
    val backupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { uri: Uri? ->
        uri?.let {
            scope.launch {
                try { viewModel.backup(it) } catch (_: Exception) {}
            }
        }
    }

    // 恢复：选择文件
    val restoreLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            scope.launch {
                try { viewModel.restore(it) } catch (_: Exception) {}
            }
        }
    }

    DetailCard {
        // 备份
        ActionRow(Icons.Outlined.Upload, "备份数据") {
            val timestamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.getDefault()).format(java.util.Date())
            runCatching {
                backupLauncher.launch("lumi_backup_$timestamp.zip")
            }.onFailure {
                Toast.makeText(context, backupPickerFailureText, Toast.LENGTH_LONG).show()
            }
        }
        SettingsDivider()
        // 恢复
        ActionRow(Icons.Outlined.Download, "恢复数据") {
            runCatching {
                restoreLauncher.launch(arrayOf("application/zip", "application/octet-stream"))
            }.onFailure {
                Toast.makeText(context, restorePickerFailureText, Toast.LENGTH_LONG).show()
            }
        }
    }

    Spacer(Modifier.height(AppSpace.md))

    // 备份说明
    DetailCard {
        Column(Modifier.fillMaxWidth().padding(AppSpace.md)) {
            Text("备份内容", fontSize = AppType.BodySmall, fontWeight = FontWeight.SemiBold, color = AppColors.TextPrimary)
            Spacer(Modifier.height(AppSpace.xs))
            listOf(
                "阅读记录与书签",
                "阅读进度与统计数据",
                "应用设置（字号、主题、深色模式等）",
                "用户头像",
                "已导入的电子书文件"
            ).forEach { item ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                    Text("·", fontSize = AppType.Caption, color = AppColors.Accent, fontWeight = FontWeight.Bold, modifier = Modifier.width(16.dp))
                    Text(item, fontSize = AppType.Caption, color = AppColors.TextSecondary)
                }
            }
            Spacer(Modifier.height(AppSpace.sm))
            Text(
                "建议定期备份数据，卸载应用或清除数据将导致所有记录丢失。",
                fontSize = AppType.Caption,
                color = AppColors.Accent,
                fontWeight = FontWeight.Medium
            )
        }
    }

    // 状态提示
    if (uiState.backupStatus.isNotEmpty()) {
        Spacer(Modifier.height(AppSpace.md))
        DetailCard {
            Row(Modifier.fillMaxWidth().padding(AppSpace.md), verticalAlignment = Alignment.CenterVertically) {
                if (uiState.isProcessing) {
                    androidx.compose.material3.CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = AppColors.Accent)
                    Spacer(Modifier.width(AppSpace.md))
                }
                Text(uiState.backupStatus, fontSize = AppType.BodySmall, color = if (uiState.backupStatus.contains("失败")) Color.Red else AppColors.TextSecondary)
            }
        }
    }

    Spacer(Modifier.height(AppSpace.lg))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = AppSpace.lg),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(R.string.backup_webdav_prompt),
            fontSize = AppType.Caption,
            color = AppColors.TextSecondary
        )
        Spacer(Modifier.width(AppSpace.xs))
        Text(
            text = stringResource(R.string.backup_webdav_link),
            fontSize = AppType.Caption,
            fontWeight = FontWeight.SemiBold,
            color = AppColors.Accent,
            modifier = Modifier.clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                context.startActivity(
                    Intent(context, DetailActivity::class.java)
                        .putExtra("category", "webdav")
                )
            }
        )
    }
}

// ─── 关于应用 ────────────────────────────────────────────────

@Composable
fun AboutDetail(viewModel: SettingsViewModel) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val update = uiState.updateCheck

    fun openDoc(title: String, file: String) {
        context.startActivity(
            Intent(context, WebViewActivity::class.java)
                .putExtra("title", title)
                .putExtra("file", file)
        )
    }

    // ── 条款/政策更新 Dialog ──
    if (update.showPolicyUpdateDialog) {
        PolicyUpdateDialog(
            hasTermsUpdate = update.hasTermsUpdate,
            termsVersion = update.termsVersion,
            hasPrivacyUpdate = update.hasPrivacyUpdate,
            privacyVersion = update.privacyVersion,
            onAccept = {
                if (update.hasTermsUpdate) viewModel.acceptTermsUpdate(update.termsVersion)
                if (update.hasPrivacyUpdate) viewModel.acceptPrivacyUpdate(update.privacyVersion)
            },
            onDecline = {
                (context as? android.app.Activity)?.finishAffinity()
            },
            onViewTerms = { openDoc("用户协议", "terms.html") },
            onViewPrivacy = { openDoc("隐私政策", "privacy.html") }
        )
    }

    // ── App 更新 Dialog ──
    if (update.showAppUpdateDialog) {
        AppUpdateDialog(
            appVersion = update.appVersion,
            updateTitle = update.updateTitle,
            updateMessage = update.updateMessage,
            changelog = update.changelog,
            force = update.isForceUpdate,
            onDownload = {
                val opened = runCatching {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(update.releaseUrl))
                    context.startActivity(intent)
                }.isSuccess
                if (!opened) {
                    Toast.makeText(context, R.string.network_error, Toast.LENGTH_LONG).show()
                } else if (!update.isForceUpdate) {
                    viewModel.dismissAppUpdateDialog()
                }
            },
            onLater = { if (!update.isForceUpdate) viewModel.dismissAppUpdateDialog() },
            onIgnoreVersion = if (update.isForceUpdate) null else viewModel::ignoreCurrentAppUpdate
        )
    }

    val currentVersion = remember(context) {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.1.00"
        } catch (_: Exception) {
            "1.1.00"
        }
    }

    // ── 版本主视觉与就地更新检查 ──
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppSpace.md)
            .aspectRatio(1.46f)
            .shadow(8.dp, RoundedCornerShape(AppRadius.lg), ambientColor = Color(0x06000000), spotColor = Color(0x06000000))
            .clip(RoundedCornerShape(AppRadius.lg))
    ) {
        Image(
            painter = painterResource(R.drawable.about_header),
            contentDescription = null,
            modifier = Modifier.matchParentSize(),
            contentScale = ContentScale.Crop
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = AppSpace.lg, bottom = AppSpace.lg)
        ) {
            Text(
                text = currentVersion,
                color = Color.White,
                fontSize = 40.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(AppSpace.sm))
            Box(
                modifier = Modifier
                    .width(120.dp)
                    .height(40.dp)
                    .clip(RoundedCornerShape(AppRadius.capsule))
                    .background(Color.White.copy(alpha = 0.36f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        enabled = !update.isChecking
                    ) { viewModel.checkUpdate(isAutoCheck = false) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (update.isChecking) "正在查更新" else stringResource(R.string.check_update),
                    color = Color.White,
                    fontSize = AppType.BodySmall,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }

    Spacer(Modifier.height(AppSpace.md))

    DetailCard {
        ActionRow(Icons.Outlined.SystemUpdateAlt, stringResource(R.string.title_changelog)) {
            context.startActivity(Intent(context, DetailActivity::class.java).putExtra("category", "changelog"))
        }
    }

    Spacer(Modifier.height(AppSpace.md))

    DetailCard {
        ActionRow(Icons.Outlined.GroupAdd, stringResource(R.string.join_community)) {
            val opened = runCatching {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse("https://qm.qq.com/q/pq77woweNG"))
                )
            }.isSuccess
            if (!opened) {
                Toast.makeText(context, R.string.network_error, Toast.LENGTH_LONG).show()
            }
        }
    }

    Spacer(Modifier.height(AppSpace.md))

    DetailCard {
        ActionRow(Icons.Outlined.Source, stringResource(R.string.github_repository)) {
            val opened = runCatching {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/huangder/Lumi_Books"))
                )
            }.isSuccess
            if (!opened) {
                Toast.makeText(context, R.string.network_error, Toast.LENGTH_LONG).show()
            }
        }
    }

    Spacer(Modifier.height(AppSpace.lg))

    // ── 法律条款 Card ──
    DetailCard {
        // 隐私条款
        ActionRow(Icons.Outlined.NightsStay, "隐私条款") { openDoc("隐私条款", "privacy.html") }
        SettingsDivider()
        // 用户协议
        ActionRow(Icons.Outlined.Info, "用户协议") { openDoc("用户协议", "terms.html") }
        SettingsDivider()
        // 开放源代码许可
        ActionRow(Icons.Outlined.Code, "开放源代码许可") { openDoc("开放源代码许可", "licenses.html") }
    }
}

// ─── 更新日志 ────────────────────────────────────────────────

data class ChangelogEntry(val version: String, val items: List<String>)

@Composable
fun ChangelogDetail() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val entries = remember {
        try {
            val text = context.assets.open("changelog.md").bufferedReader().readText()
            parseChangelog(text)
        } catch (_: Exception) {
            emptyList()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppSpace.md, vertical = AppSpace.sm)
    ) {
        entries.forEachIndexed { index, entry ->
            val isLatest = index == 0
            DetailCard {
                Column(Modifier.padding(AppSpace.md)) {
                    // 版本标题行
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            entry.version,
                            fontSize = AppType.Body,
                            fontWeight = FontWeight.Bold,
                            color = AppColors.TextPrimary
                        )
                        if (isLatest) {
                            Spacer(Modifier.width(AppSpace.sm))
                            Box(
                                Modifier
                                    .clip(RoundedCornerShape(AppRadius.sm))
                                    .background(AppColors.Accent)
                                    .padding(horizontal = AppSpace.sm, vertical = 2.dp)
                            ) {
                                Text("最新", fontSize = AppType.Caption, color = Color.White)
                            }
                        }
                    }
                    // 变更条目
                    if (entry.items.isNotEmpty()) {
                        Spacer(Modifier.height(AppSpace.sm))
                        entry.items.forEach { item ->
                            Text(
                                item,
                                fontSize = AppType.BodySmall,
                                color = AppColors.TextSecondary,
                                modifier = Modifier.padding(vertical = 2.dp)
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(AppSpace.sm))
        }
    }
}

private fun parseChangelog(text: String): List<ChangelogEntry> {
    val result = mutableListOf<ChangelogEntry>()
    val lines = text.lines()
    var currentVersion: String? = null
    val currentItems = mutableListOf<String>()

    for (line in lines) {
        when {
            line.startsWith("## ") -> {
                // 保存上一个版本
                if (currentVersion != null) {
                    result.add(ChangelogEntry(currentVersion, currentItems.toList()))
                    currentItems.clear()
                }
                currentVersion = line.removePrefix("## ").trim()
            }
            line.startsWith("· ") -> {
                currentItems.add(line.trim())
            }
            line.startsWith("### ") -> {
                // 跳过标题行
            }
            line.isNotBlank() && currentVersion != null && !currentItems.contains(line.trim()) -> {
                // 非 · 开头的普通行（如"第一个开发测试版"）
                currentItems.add(line.trim())
            }
        }
    }
    // 保存最后一个版本
    if (currentVersion != null) {
        result.add(ChangelogEntry(currentVersion, currentItems.toList()))
    }
    return result
}

// ─── 通用组件 ────────────────────────────────────────────────

@Composable
private fun DetailCard(content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(AppRadius.lg)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppSpace.md)
            .shadow(8.dp, shape, ambientColor = Color(0x06000000), spotColor = Color(0x06000000))
            .clip(shape)
            .background(AppColors.CardBg)
    ) { content() }
}

@Composable
private fun SettingsDivider() {
    Box(Modifier.fillMaxWidth().padding(horizontal = AppSpace.md).height(0.5.dp).background(AppColors.Divider))
}

@Composable
private fun SettingsSliderItem(
    icon: ImageVector, label: String, value: Float, range: ClosedFloatingPointRange<Float>,
    valueText: String,
    steps: Int = 0,
    step: Float = 0.1f,
    onDragChange: ((Float) -> Unit)? = null,
    onChange: (Float) -> Unit
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = AppSpace.md, vertical = AppSpace.sm)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = AppColors.TextSecondary, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(AppSpace.md))
            Text(label, fontSize = AppType.Body, color = AppColors.TextPrimary, modifier = Modifier.weight(1f))
            Text(valueText, fontSize = AppType.BodySmall, color = AppColors.Accent, fontWeight = FontWeight.Medium)
        }
        com.huangder.lumibooks.ui.components.PillSlider(
            value = value,
            onValueChange = onChange,
            valueRange = range,
            step = if (steps > 0) {
                (range.endInclusive - range.start) / (steps + 1)
            } else {
                step
            },
            onDragValueChange = onDragChange
        )
    }
}

@Composable
private fun FontTypeRow(selected: String, onSelect: (String) -> Unit) {
    val options = listOf("system" to "系统默认", "serif" to "宋体", "monospace" to "等宽")
    Row(Modifier.fillMaxWidth().padding(horizontal = AppSpace.md, vertical = AppSpace.md), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Outlined.FontDownload, null, tint = AppColors.TextSecondary, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(AppSpace.md))
        Text("字体", fontSize = AppType.Body, color = AppColors.TextPrimary, modifier = Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(AppSpace.xs)) {
            options.forEach { (key, label) ->
                val sel = key == selected
                Box(
                    Modifier.clip(RoundedCornerShape(AppRadius.sm)).background(if (sel) AppColors.Accent else AppColors.BgGray)
                        .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { onSelect(key) }
                        .padding(horizontal = AppSpace.sm, vertical = AppSpace.xs)
                ) { Text(label, fontSize = AppType.Caption, color = if (sel) Color.White else AppColors.TextSecondary, fontWeight = if (sel) FontWeight.Medium else FontWeight.Normal) }
            }
        }
    }
}

@Composable
private fun DropdownSettingRow(
    icon: ImageVector,
    label: String,
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var menuAnchorBounds by remember { mutableStateOf(Rect.Zero) }
    val selectedLabel = options.firstOrNull { it.first == selected }?.second.orEmpty()
    val isLiquidGlass = LocalAppTheme.current == "liquid_glass"
    val liquidMenuHost = LocalLiquidGlassMenuHost.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(AppSpace.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = AppColors.TextSecondary, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(AppSpace.md))
        Text(
            label,
            fontSize = AppType.Body,
            color = AppColors.TextPrimary,
            modifier = Modifier.weight(1f)
        )
        Box {
            Row(
                modifier = Modifier
                    .width(138.dp)
                    .height(42.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(AppColors.WindowBg)
                    .border(1.dp, AppColors.Divider, RoundedCornerShape(14.dp))
                    .onGloballyPositioned { menuAnchorBounds = it.boundsInRoot() }
                    .clickable {
                        if (isLiquidGlass && liquidMenuHost != null && menuAnchorBounds != Rect.Zero) {
                            liquidMenuHost.show(
                                LiquidGlassMenuSpec(
                                    anchorBounds = menuAnchorBounds,
                                    width = 138.dp,
                                    items = options.map { (key, display) ->
                                        LiquidGlassMenuItem(
                                            label = display,
                                            selected = key == selected,
                                            onClick = { onSelect(key) }
                                        )
                                    }
                                )
                            )
                        } else {
                            expanded = true
                        }
                    }
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    selectedLabel,
                    fontSize = AppType.BodySmall,
                    color = AppColors.TextPrimary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    Icons.Outlined.ExpandMore,
                    contentDescription = null,
                    tint = AppColors.TextSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.width(138.dp),
                shape = RoundedCornerShape(16.dp),
                containerColor = AppColors.WindowBg,
                border = BorderStroke(1.dp, AppColors.Divider),
                shadowElevation = 0.dp,
                tonalElevation = 0.dp
            ) {
                options.forEach { (key, display) ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                display,
                                modifier = Modifier.fillMaxWidth(),
                                color = if (key == selected) AppColors.Accent else AppColors.TextPrimary,
                                fontSize = AppType.BodySmall,
                                fontWeight = if (key == selected) FontWeight.SemiBold else FontWeight.Normal,
                                textAlign = TextAlign.Center
                            )
                        },
                        onClick = {
                            onSelect(key)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ActionRow(icon: ImageVector, label: String, labelColor: Color = AppColors.TextPrimary, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onClick() }.padding(AppSpace.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = if (labelColor == Color.Red) Color.Red else AppColors.TextSecondary, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(AppSpace.md))
        Text(label, fontSize = AppType.Body, color = labelColor, modifier = Modifier.weight(1f))
        Icon(Icons.Outlined.ChevronRight, null, tint = AppColors.TextSecondary, modifier = Modifier.size(20.dp))
    }
}

// ─── 语言设置 ────────────────────────────────────────────────

@Composable
fun LanguageDetailScreen(viewModel: SettingsViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    var showRestartDialog by remember { mutableStateOf(false) }
    var pendingLanguage by remember { mutableStateOf("") }
    val context = LocalContext.current

    val languageOptions = com.huangder.lumibooks.util.LocaleHelper.SUPPORTED_LANGUAGES.map { key ->
        key to (com.huangder.lumibooks.util.LocaleHelper.LANGUAGE_DISPLAY_NAMES[key] ?: key)
    }

    DetailCard {
        languageOptions.forEachIndexed { index, (key, displayName) ->
            val isSelected = key == uiState.appLanguage
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {
                        if (!isSelected) {
                            pendingLanguage = key
                            showRestartDialog = true
                        }
                    }
                    .padding(horizontal = AppSpace.md, vertical = AppSpace.md),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    displayName,
                    fontSize = AppType.Body,
                    color = if (isSelected) AppColors.Accent else AppColors.TextPrimary,
                    fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                    modifier = Modifier.weight(1f)
                )
                if (isSelected) {
                    Icon(
                        Icons.Outlined.Check,
                        contentDescription = null,
                        tint = AppColors.Accent,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            if (index < languageOptions.size - 1) {
                SettingsDivider()
            }
        }
    }

    // ── 重启确认对话框 ──
    if (showRestartDialog) {
        LiquidGlassAlertDialog(
            onDismissRequest = { showRestartDialog = false },
            title = { Text(stringResource(R.string.switch_language)) },
            text = { Text(stringResource(R.string.restart_prompt)) },
            confirmButton = {
                LiquidGlassTextButton(
                    text = stringResource(R.string.restart),
                    tintedColor = AppColors.Accent,
                    onClick = {
                    viewModel.saveAppLanguage(pendingLanguage)
                    showRestartDialog = false
                    // 重启应用
                    val intent = android.content.Intent(context, com.huangder.lumibooks.MainActivity::class.java).apply {
                        addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    }
                    context.startActivity(intent)
                    (context as? android.app.Activity)?.finishAffinity()
                })
            },
            dismissButton = {
                LiquidGlassTextButton(
                    text = stringResource(R.string.later),
                    onClick = { showRestartDialog = false }
                )
            }
        )
    }
}
