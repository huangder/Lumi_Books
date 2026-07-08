package com.huangder.lumibooks.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.FontDownload
import androidx.compose.material.icons.outlined.FormatSize
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Landscape
import androidx.compose.material.icons.outlined.LineWeight
import androidx.compose.material.icons.outlined.NightsStay
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.Title
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.huangder.lumibooks.ui.theme.AppColors
import com.huangder.lumibooks.ui.theme.AppRadius
import com.huangder.lumibooks.ui.theme.AppSpace
import com.huangder.lumibooks.ui.theme.AppType
import com.huangder.lumibooks.ui.theme.FangSong

// ─── 详情页通用框架 ──────────────────────────────────────────

@Composable
fun DetailPage(title: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.WindowBg)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
        ) {
            // 顶栏
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AppSpace.sm, vertical = AppSpace.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, "返回", tint = AppColors.TextPrimary)
                }
                Spacer(Modifier.weight(1f))
                Text(title, fontSize = AppType.Section, fontWeight = FontWeight.Bold, fontFamily = FangSong, color = AppColors.TextPrimary)
                Spacer(Modifier.weight(1f))
                Spacer(Modifier.size(48.dp))
            }
            Spacer(Modifier.height(AppSpace.sm))
            content()
            Spacer(Modifier.height(120.dp))
        }
    }
}

// ─── 阅读设置 ────────────────────────────────────────────────

@Composable
fun ReadingSettingsDetail(viewModel: SettingsViewModel) {
    val uiState by viewModel.uiState.collectAsState()

    DetailCard {
        SettingsSliderItem(Icons.Outlined.FormatSize, "字号", uiState.fontSize, 12f..28f, "${uiState.fontSize.toInt()} sp") { viewModel.saveFontSize(it) }
        SettingsDivider()
        SettingsSliderItem(Icons.Outlined.LineWeight, "行距", uiState.lineHeight, 1.0f..2.5f, String.format("%.1f", uiState.lineHeight)) { viewModel.saveLineHeight(it) }
        SettingsDivider()
        SettingsSliderItem(Icons.Outlined.Title, "字间距", uiState.letterSpacing, 0f..0.1f, String.format("%.2f em", uiState.letterSpacing)) { viewModel.saveLetterSpacing(it) }
        SettingsDivider()
        FontTypeRow(uiState.fontType) { viewModel.saveFontType(it) }
        SettingsDivider()
        SettingsSliderItem(Icons.Outlined.Landscape, "左右边距", uiState.marginHoriz, 20f..60f, "${uiState.marginHoriz.toInt()} dp") { viewModel.saveMarginHoriz(it) }
        SettingsDivider()
        SettingsSliderItem(Icons.Outlined.Landscape, "上下边距", uiState.marginVert, 40f..100f, "${uiState.marginVert.toInt()} dp") { viewModel.saveMarginVert(it) }
    }
}

// ─── 显示与外观 ──────────────────────────────────────────────

@Composable
fun DisplayDetail(viewModel: SettingsViewModel) {
    val uiState by viewModel.uiState.collectAsState()

    DetailCard {
        val darkModeOptions = listOf("system" to "跟随系统", "light" to "浅色", "dark" to "深色")
        OptionRow(Icons.Outlined.Brightness6, "深色模式", darkModeOptions, uiState.darkMode) { viewModel.saveDarkMode(it) }
        SettingsDivider()
        val themeOptions = listOf("day" to "日间", "night" to "夜间", "sepia" to "护眼", "green" to "绿色")
        OptionRow(Icons.Outlined.Palette, "默认阅读主题", themeOptions, uiState.readerTheme) { viewModel.saveReaderTheme(it) }
    }
}

// ─── 阅读目标 ────────────────────────────────────────────────

@Composable
fun ReadingGoalDetail(viewModel: SettingsViewModel) {
    val uiState by viewModel.uiState.collectAsState()

    DetailCard {
        SettingsSliderItem(Icons.Outlined.Timer, "每日目标", uiState.dailyGoal.toFloat(), 10f..120f, "${uiState.dailyGoal} 分钟", steps = 21) { viewModel.saveDailyGoal(it.toInt()) }
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
                    Text("总占用空间", fontSize = AppType.Body, color = AppColors.TextPrimary, modifier = Modifier.weight(1f))
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
                        "应用本体" to (info.appSizeBytes to SegmentColors[0]),
                        "缓存文件" to (info.cacheSizeBytes to SegmentColors[1]),
                        "电子书文件" to (info.booksSizeBytes to SegmentColors[2]),
                        "封面图片" to (info.coversSizeBytes to SegmentColors[3])
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
                    Text("计算中...", fontSize = AppType.BodySmall, color = AppColors.TextSecondary)
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
                            "电子书文件 (${info.bookDetails.size}本)",
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
                    info.bookDetails.forEachIndexed { index, item ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = AppSpace.md, vertical = AppSpace.sm),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    item.title,
                                    fontSize = AppType.BodySmall,
                                    color = AppColors.TextPrimary,
                                    maxLines = 1
                                )
                            }
                            // 格式标签
                            val fmtColor = FormatColors[item.format] ?: Color.Gray
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(fmtColor.copy(alpha = 0.12f))
                                    .padding(horizontal = 6.dp, vertical = 1.dp)
                            ) {
                                Text(
                                    item.format,
                                    fontSize = 10.sp,
                                    color = fmtColor,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            Spacer(Modifier.width(AppSpace.sm))
                            Text(
                                viewModel.formatFileSize(item.sizeBytes),
                                fontSize = AppType.Caption,
                                color = AppColors.TextSecondary,
                                modifier = Modifier.width(52.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.End
                            )
                        }
                        if (index < info.bookDetails.lastIndex) {
                            SettingsDivider()
                        }
                    }
                }
            }
        }

        // ── 操作卡片 ──
        Spacer(Modifier.height(AppSpace.md))
        DetailCard {
            ActionRow(Icons.Outlined.DeleteSweep, "清除缓存") { viewModel.clearCache() }
            SettingsDivider()
            ActionRow(Icons.Outlined.DeleteForever, "清除所有数据", Color.Red) { showClearDialog = true }
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("清除所有数据") },
            text = { Text("此操作将清除所有阅读记录、书籍数据和设置。头像将被保留。此操作不可撤销。") },
            confirmButton = { TextButton(onClick = { viewModel.clearAllData(); showClearDialog = false }) { Text("确认清除", color = Color.Red) } },
            dismissButton = { TextButton(onClick = { showClearDialog = false }) { Text("取消") } }
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
fun AboutDetail() {
    val context = androidx.compose.ui.platform.LocalContext.current

    fun openDoc(title: String, file: String) {
        context.startActivity(
            Intent(context, WebViewActivity::class.java)
                .putExtra("title", title)
                .putExtra("file", file)
        )
    }

    DetailCard {
        // 版本
        Row(Modifier.fillMaxWidth().padding(AppSpace.md), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Info, null, tint = AppColors.TextSecondary, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(AppSpace.md))
            Text("版本", fontSize = AppType.Body, color = AppColors.TextPrimary, modifier = Modifier.weight(1f))
            Text("1.0.01.104_Beta", fontSize = AppType.BodySmall, color = AppColors.TextSecondary)
        }
        SettingsDivider()
        // 隐私声明
        Row(Modifier.fillMaxWidth().padding(AppSpace.md), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.NightsStay, null, tint = AppColors.TextSecondary, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(AppSpace.md))
            Column(modifier = Modifier.weight(1f)) {
                Text("隐私声明", fontSize = AppType.Body, color = AppColors.TextPrimary)
                Text("本应用完全离线运行，无网络权限，无第三方SDK", fontSize = AppType.Caption, color = AppColors.TextSecondary)
            }
        }
    }

    Spacer(Modifier.height(AppSpace.lg))

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
    valueText: String, steps: Int = 0, onChange: (Float) -> Unit
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
            valueRange = range
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
private fun OptionRow(icon: ImageVector, label: String, options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = AppSpace.md, vertical = AppSpace.md), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = AppColors.TextSecondary, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(AppSpace.md))
        Text(label, fontSize = AppType.Body, color = AppColors.TextPrimary, modifier = Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(AppSpace.xs)) {
            options.forEach { (key, disp) ->
                val sel = key == selected
                Box(
                    Modifier.clip(RoundedCornerShape(AppRadius.sm)).background(if (sel) AppColors.Accent else AppColors.BgGray)
                        .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { onSelect(key) }
                        .padding(horizontal = AppSpace.sm, vertical = AppSpace.xs)
                ) { Text(disp, fontSize = AppType.Caption, color = if (sel) Color.White else AppColors.TextSecondary, fontWeight = if (sel) FontWeight.Medium else FontWeight.Normal) }
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
    }
}
