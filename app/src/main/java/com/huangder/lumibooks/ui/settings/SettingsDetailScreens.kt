package com.huangder.lumibooks.ui.settings

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
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.DeleteSweep
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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

@Composable
fun StorageDetail(viewModel: SettingsViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    var showClearDialog by remember { mutableStateOf(false) }

    DetailCard {
        Row(
            modifier = Modifier.fillMaxWidth().padding(AppSpace.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Outlined.Speed, null, tint = AppColors.TextSecondary, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(AppSpace.md))
            Text("占用空间", fontSize = AppType.Body, color = AppColors.TextPrimary, modifier = Modifier.weight(1f))
            Text(uiState.cacheSize, fontSize = AppType.BodySmall, color = AppColors.TextSecondary)
        }
        SettingsDivider()
        ActionRow(Icons.Outlined.DeleteSweep, "清除缓存") { viewModel.clearCache() }
        SettingsDivider()
        ActionRow(Icons.Outlined.DeleteForever, "清除所有数据", Color.Red) { showClearDialog = true }
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

// ─── 关于应用 ────────────────────────────────────────────────

@Composable
fun AboutDetail() {
    DetailCard {
        Row(Modifier.fillMaxWidth().padding(AppSpace.md), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Info, null, tint = AppColors.TextSecondary, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(AppSpace.md))
            Text("版本", fontSize = AppType.Body, color = AppColors.TextPrimary, modifier = Modifier.weight(1f))
            Text("1.0.3", fontSize = AppType.BodySmall, color = AppColors.TextSecondary)
        }
        SettingsDivider()
        Row(Modifier.fillMaxWidth().padding(AppSpace.md), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.NightsStay, null, tint = AppColors.TextSecondary, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(AppSpace.md))
            Column(modifier = Modifier.weight(1f)) {
                Text("隐私声明", fontSize = AppType.Body, color = AppColors.TextPrimary)
                Text("本应用完全离线运行，无网络权限，无第三方SDK", fontSize = AppType.Caption, color = AppColors.TextSecondary)
            }
        }
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
        Modifier.fillMaxWidth().clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { onClick() }.padding(AppSpace.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = if (labelColor == Color.Red) Color.Red else AppColors.TextSecondary, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(AppSpace.md))
        Text(label, fontSize = AppType.Body, color = labelColor, modifier = Modifier.weight(1f))
    }
}
