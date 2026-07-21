package com.huangder.lumibooks.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.tween
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Brightness6
import androidx.compose.material.icons.outlined.Animation
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.FontDownload
import androidx.compose.material.icons.outlined.FormatSize
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Landscape
import androidx.compose.material.icons.outlined.LineWeight
import androidx.compose.material.icons.outlined.NightsStay
import androidx.compose.material.icons.outlined.Opacity
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.SwipeRightAlt
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.Title
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material.icons.outlined.SystemUpdateAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.huangder.lumibooks.R
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.huangder.lumibooks.ui.theme.AppColors
import com.huangder.lumibooks.ui.theme.AppRadius
import com.huangder.lumibooks.ui.theme.AppSpace
import com.huangder.lumibooks.ui.theme.AppType
import com.huangder.lumibooks.ui.theme.FangSong
import com.huangder.lumibooks.ui.components.LiquidGlassSwitch
import com.huangder.lumibooks.ui.components.LiquidGlassMenuHost
import com.huangder.lumibooks.ui.components.LiquidGlassMenuItem
import com.huangder.lumibooks.ui.components.LiquidGlassMenuSpec
import com.huangder.lumibooks.ui.components.LocalLiquidGlassMenuHost
import com.huangder.lumibooks.ui.theme.LocalAppTheme
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop

// ─── 详情页通用框架 ──────────────────────────────────────────

@Composable
fun DetailPage(title: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    val isLiquidGlass = LocalAppTheme.current == "liquid_glass"
    val pageBackdrop = rememberLayerBackdrop()

    LiquidGlassMenuHost(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.WindowBg),
        backdrop = pageBackdrop.takeIf { isLiquidGlass }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .then(if (isLiquidGlass) Modifier.layerBackdrop(pageBackdrop) else Modifier)
                .background(AppColors.WindowBg)
                .statusBarsPadding()
        ) {
            // 顶栏
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AppSpace.sm, vertical = AppSpace.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.back), tint = AppColors.TextPrimary)
                }
                Spacer(Modifier.weight(1f))
                Text(title, fontSize = AppType.Section, fontWeight = FontWeight.Bold, fontFamily = FangSong, color = AppColors.TextPrimary)
                Spacer(Modifier.weight(1f))
                Spacer(Modifier.size(48.dp))
            }
            Column(
                modifier = Modifier
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

// ─── 阅读设置 ────────────────────────────────────────────────

@Composable
fun ReadingSettingsDetail(viewModel: SettingsViewModel) {
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
    val appThemeOptions = listOf(
        "lumi" to stringResource(R.string.app_theme_lumi),
        "material3" to stringResource(R.string.app_theme_material3),
        "liquid_glass" to stringResource(R.string.app_theme_liquid_glass)
    )
    val darkModeOptions = listOf(
        "system" to stringResource(R.string.dark_mode_system),
        "light" to stringResource(R.string.dark_mode_light),
        "dark" to stringResource(R.string.dark_mode_dark)
    )
    val themeOptions = listOf(
        "day" to stringResource(R.string.theme_day),
        "night" to stringResource(R.string.theme_night),
        "sepia" to stringResource(R.string.theme_sepia),
        "green" to stringResource(R.string.theme_green)
    )

    DetailCard {
        DropdownSettingRow(
            icon = Icons.Outlined.Palette,
            label = stringResource(R.string.label_app_theme),
            options = appThemeOptions,
            selected = uiState.appTheme,
            onSelect = viewModel::saveAppTheme
        )
    }

    AnimatedVisibility(
        visible = uiState.appTheme == "liquid_glass",
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
            }
        }
    }

    Spacer(Modifier.height(12.dp))

    DetailCard {
        DropdownSettingRow(
            icon = Icons.Outlined.Brightness6,
            label = stringResource(R.string.label_dark_mode),
            options = darkModeOptions,
            selected = uiState.darkMode,
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
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(AppSpace.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Outlined.Animation,
                contentDescription = null,
                tint = AppColors.TextSecondary,
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.width(AppSpace.md))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.label_entrance_animations),
                    fontSize = AppType.Body,
                    color = AppColors.TextPrimary
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    stringResource(R.string.entrance_animations_description),
                    fontSize = AppType.Caption,
                    color = AppColors.TextSecondary
                )
            }
            Spacer(Modifier.width(12.dp))
            LiquidGlassSwitch(
                checked = uiState.entranceAnimationsEnabled,
                onCheckedChange = viewModel::saveEntranceAnimationsEnabled
            )
        }
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
            selected = uiState.readerTheme,
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

    Column {
        // ── 总览卡片 ──
        DetailCard {
            Column(Modifier.fillMaxWidth().padding(AppSpace.md)) {
                // 标题行
                val totalBytes = info.appSizeBytes + info.cacheSizeBytes + info.booksSizeBytes + info.coversSizeBytes
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

        // ── 操作卡片 ──
        Spacer(Modifier.height(AppSpace.md))
        DetailCard {
            ActionRow(Icons.Outlined.DeleteSweep, stringResource(R.string.clear_cache)) { viewModel.clearCache() }
            SettingsDivider()
            ActionRow(Icons.Outlined.DeleteForever, stringResource(R.string.clear_all_data), Color.Red) { showClearDialog = true }
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text(stringResource(R.string.clear_all_data)) },
            text = { Text(stringResource(R.string.clear_all_confirm)) },
            confirmButton = { TextButton(onClick = { viewModel.clearAllData(); showClearDialog = false }) { Text(stringResource(R.string.clear), color = Color.Red) } },
            dismissButton = { TextButton(onClick = { showClearDialog = false }) { Text(stringResource(R.string.cancel)) } }
        )
    }
}

// ─── 备份与恢复 ──────────────────────────────────────────────

@Composable
fun BackupRestoreDetail(viewModel: SettingsViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()

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
            backupLauncher.launch("lumi_backup_$timestamp.zip")
        }
        SettingsDivider()
        // 恢复
        ActionRow(Icons.Outlined.Download, "恢复数据") {
            restoreLauncher.launch(arrayOf("application/zip", "application/octet-stream"))
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
        val needsTerms = update.hasTermsUpdate
        val needsPrivacy = update.hasPrivacyUpdate
        val title = when {
            needsTerms && needsPrivacy -> "用户协议与隐私政策已更新"
            needsTerms -> "用户协议已更新"
            else -> "隐私政策已更新"
        }
        val message = buildString {
            append("感谢您使用 Lumi！我们在持续改进产品的同时，也可能会不断完善法律条款。")
            if (needsTerms) append("\n\n• 用户协议已更新（版本 ${update.termsVersion}）")
            if (needsPrivacy) append("\n\n• 隐私政策已更新（版本 ${update.privacyVersion}）")
            append("\n\n请阅读更新内容后选择是否同意。")
        }

        AlertDialog(
            onDismissRequest = { /* 不可关闭 */ },
            title = { Text(title, fontSize = AppType.Body, fontWeight = FontWeight.Bold) },
            text = { Text(message, fontSize = AppType.BodySmall) },
            confirmButton = {
                TextButton(onClick = {
                    if (update.hasTermsUpdate) viewModel.acceptTermsUpdate(update.termsVersion)
                    if (update.hasPrivacyUpdate) viewModel.acceptPrivacyUpdate(update.privacyVersion)
                }) { Text("同意并继续", color = AppColors.Accent) }
            },
            dismissButton = {
                // 查看协议
                if (update.hasTermsUpdate) {
                    TextButton(onClick = { openDoc("用户协议", "terms.html") }) {
                        Text("查看用户协议")
                    }
                }
                if (update.hasPrivacyUpdate) {
                    TextButton(onClick = { openDoc("隐私政策", "privacy.html") }) {
                        Text("查看隐私政策")
                    }
                }
                // 退出按钮
                TextButton(onClick = {
                    // 用户不同意，退出应用
                    (context as? android.app.Activity)?.finishAffinity()
                }) { Text("不同意并退出", color = AppColors.TextSecondary) }
            }
        )
    }

    // ── App 更新 Dialog ──
    if (update.showAppUpdateDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissAppUpdateDialog() },
            title = { Text("发现新版本", fontSize = AppType.Body, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "新版本 ${update.appVersion} 已发布，是否前往下载？",
                    fontSize = AppType.BodySmall
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    // 打开 GitHub Releases 页面
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(update.releaseUrl))
                    context.startActivity(intent)
                    viewModel.dismissAppUpdateDialog()
                }) { Text("下载", color = AppColors.Accent) }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissAppUpdateDialog() }) {
                    Text("稍后", color = AppColors.TextSecondary)
                }
            }
        )
    }

    val currentVersion = remember(context) {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0.06"
        } catch (_: Exception) {
            "1.0.06"
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
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppSpace.md)
            .shadow(8.dp, RoundedCornerShape(AppRadius.lg), ambientColor = Color(0x06000000), spotColor = Color(0x06000000))
            .clip(RoundedCornerShape(AppRadius.lg))
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
        AlertDialog(
            onDismissRequest = { showRestartDialog = false },
            title = { Text(stringResource(R.string.switch_language)) },
            text = { Text(stringResource(R.string.restart_prompt)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.saveAppLanguage(pendingLanguage)
                    showRestartDialog = false
                    // 重启应用
                    val intent = android.content.Intent(context, com.huangder.lumibooks.MainActivity::class.java).apply {
                        addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    }
                    context.startActivity(intent)
                    (context as? android.app.Activity)?.finishAffinity()
                }) { Text(stringResource(R.string.restart)) }
            },
            dismissButton = {
                TextButton(onClick = { showRestartDialog = false }) { Text(stringResource(R.string.later)) }
            }
        )
    }
}
