package com.huangder.lumibooks.ui.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Brightness6
import androidx.compose.material.icons.outlined.ChevronRight
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
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.huangder.lumibooks.ui.theme.AppColors
import com.huangder.lumibooks.ui.theme.AppRadius
import com.huangder.lumibooks.ui.theme.AppSpace
import com.huangder.lumibooks.ui.theme.AppType
import com.huangder.lumibooks.ui.theme.KaiTi
import com.huangder.lumibooks.ui.theme.SansSerif
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showClearDataDialog by remember { mutableStateOf(false) }

    // 头像选择
    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        uri?.let {
            scope.launch {
                val avatarDir = File(context.filesDir, "avatars")
                if (!avatarDir.exists()) avatarDir.mkdirs()
                val avatarFile = File(avatarDir, "avatar.jpg")
                context.contentResolver.openInputStream(it)?.use { input ->
                    avatarFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                viewModel.saveAvatar(avatarFile.absolutePath)
            }
        }
    }

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
            SettingsTopBar(onNavigateBack = onNavigateBack)

            // 1. 个人信息
            PersonalInfoSection(
                avatarUri = uiState.avatarUri,
                nickname = uiState.nickname,
                onAvatarClick = {
                    photoPicker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                onNicknameChange = { viewModel.saveNickname(it) }
            )

            Spacer(Modifier.height(AppSpace.lg))

            // 2. 阅读设置
            ReadingSettingsSection(
                fontSize = uiState.fontSize,
                lineHeight = uiState.lineHeight,
                letterSpacing = uiState.letterSpacing,
                fontType = uiState.fontType,
                marginHoriz = uiState.marginHoriz,
                marginVert = uiState.marginVert,
                onFontSizeChange = { viewModel.saveFontSize(it) },
                onLineHeightChange = { viewModel.saveLineHeight(it) },
                onLetterSpacingChange = { viewModel.saveLetterSpacing(it) },
                onFontTypeChange = { viewModel.saveFontType(it) },
                onMarginHorizChange = { viewModel.saveMarginHoriz(it) },
                onMarginVertChange = { viewModel.saveMarginVert(it) }
            )

            Spacer(Modifier.height(AppSpace.lg))

            // 3. 显示与外观
            DisplaySection(
                darkMode = uiState.darkMode,
                readerTheme = uiState.readerTheme,
                onDarkModeChange = { viewModel.saveDarkMode(it) },
                onReaderThemeChange = { viewModel.saveReaderTheme(it) }
            )

            Spacer(Modifier.height(AppSpace.lg))

            // 4. 阅读目标
            ReadingGoalSection(
                dailyGoal = uiState.dailyGoal,
                onDailyGoalChange = { viewModel.saveDailyGoal(it) }
            )

            Spacer(Modifier.height(AppSpace.lg))

            // 5. 存储管理
            StorageSection(
                cacheSize = uiState.cacheSize,
                onClearCache = { viewModel.clearCache() },
                onClearAllData = { showClearDataDialog = true }
            )

            Spacer(Modifier.height(AppSpace.lg))

            // 6. 关于应用
            AboutSection()

            Spacer(Modifier.height(120.dp))
        }
    }

    // 清除全部数据确认弹窗
    if (showClearDataDialog) {
        AlertDialog(
            onDismissRequest = { showClearDataDialog = false },
            title = { Text("清除所有数据") },
            text = { Text("此操作将清除所有阅读记录、书籍数据和设置。头像将被保留。此操作不可撤销。") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearAllData()
                    showClearDataDialog = false
                }) {
                    Text("确认清除", color = Color.Red)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDataDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}

// ─── 顶栏 ──────────────────────────────────────────────────────

@Composable
private fun SettingsTopBar(onNavigateBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppSpace.sm, vertical = AppSpace.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onNavigateBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = "返回",
                tint = AppColors.TextPrimary
            )
        }
        Spacer(Modifier.weight(1f))
        Text(
            text = "设置",
            fontSize = AppType.Section,
            fontWeight = FontWeight.Bold,
            fontFamily = KaiTi,
            color = AppColors.TextPrimary
        )
        Spacer(Modifier.weight(1f))
        // 占位，保持标题居中
        Spacer(Modifier.size(48.dp))
    }
}

// ─── 个人信息 ──────────────────────────────────────────────────

@Composable
private fun PersonalInfoSection(
    avatarUri: String?,
    nickname: String,
    onAvatarClick: () -> Unit,
    onNicknameChange: (String) -> Unit
) {
    var showNicknameDialog by remember { mutableStateOf(false) }

    SettingsCard {
        // 头像行
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) { onAvatarClick() }
                .padding(AppSpace.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "头像",
                fontSize = AppType.Body,
                color = AppColors.TextPrimary,
                modifier = Modifier.weight(1f)
            )
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(AppColors.BgGray)
                    .border(1.dp, AppColors.Divider, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (avatarUri != null) {
                    AsyncImage(
                        model = File(avatarUri),
                        contentDescription = "头像",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        imageVector = Icons.Outlined.AccountCircle,
                        contentDescription = "默认头像",
                        tint = AppColors.TextSecondary,
                        modifier = Modifier.size(40.dp)
                    )
                }
            }
            Spacer(Modifier.width(AppSpace.sm))
            Icon(
                imageVector = Icons.Outlined.ChevronRight,
                contentDescription = null,
                tint = AppColors.TextSecondary,
                modifier = Modifier.size(20.dp)
            )
        }

        SettingsDivider()

        // 昵称行
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) { showNicknameDialog = true }
                .padding(AppSpace.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "昵称",
                fontSize = AppType.Body,
                color = AppColors.TextPrimary,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = nickname,
                fontSize = AppType.Body,
                color = AppColors.TextSecondary
            )
            Spacer(Modifier.width(AppSpace.sm))
            Icon(
                imageVector = Icons.Outlined.ChevronRight,
                contentDescription = null,
                tint = AppColors.TextSecondary,
                modifier = Modifier.size(20.dp)
            )
        }
    }

    // 昵称编辑弹窗
    if (showNicknameDialog) {
        var editedName by remember { mutableStateOf(nickname) }
        AlertDialog(
            onDismissRequest = { showNicknameDialog = false },
            title = { Text("修改昵称") },
            text = {
                androidx.compose.material3.OutlinedTextField(
                    value = editedName,
                    onValueChange = { editedName = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (editedName.isNotBlank()) {
                        onNicknameChange(editedName.trim())
                    }
                    showNicknameDialog = false
                }) {
                    Text("确认")
                }
            },
            dismissButton = {
                TextButton(onClick = { showNicknameDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}

// ─── 阅读设置 ──────────────────────────────────────────────────

@Composable
private fun ReadingSettingsSection(
    fontSize: Float,
    lineHeight: Float,
    letterSpacing: Float,
    fontType: String,
    marginHoriz: Float,
    marginVert: Float,
    onFontSizeChange: (Float) -> Unit,
    onLineHeightChange: (Float) -> Unit,
    onLetterSpacingChange: (Float) -> Unit,
    onFontTypeChange: (String) -> Unit,
    onMarginHorizChange: (Float) -> Unit,
    onMarginVertChange: (Float) -> Unit
) {
    SectionTitle("阅读设置")
    Spacer(Modifier.height(AppSpace.sm))
    SettingsCard {
        SettingsSlider(
            icon = Icons.Outlined.FormatSize,
            label = "字号",
            value = fontSize,
            valueRange = 12f..28f,
            valueText = "${fontSize.toInt()} sp",
            onValueChange = onFontSizeChange
        )

        SettingsDivider()

        SettingsSlider(
            icon = Icons.Outlined.LineWeight,
            label = "行距",
            value = lineHeight,
            valueRange = 1.0f..2.5f,
            valueText = String.format("%.1f", lineHeight),
            onValueChange = onLineHeightChange
        )

        SettingsDivider()

        SettingsSlider(
            icon = Icons.Outlined.Title,
            label = "字间距",
            value = letterSpacing,
            valueRange = 0f..0.1f,
            valueText = String.format("%.2f em", letterSpacing),
            onValueChange = onLetterSpacingChange
        )

        SettingsDivider()

        // 字体选择
        val fontOptions = listOf("system" to "系统默认", "serif" to "宋体", "monospace" to "等宽")
        SettingsOptionRow(
            icon = Icons.Outlined.FontDownload,
            label = "字体",
            options = fontOptions,
            selected = fontType,
            onSelect = onFontTypeChange
        )

        SettingsDivider()

        SettingsSlider(
            icon = Icons.Outlined.Landscape,
            label = "左右边距",
            value = marginHoriz,
            valueRange = 20f..60f,
            valueText = "${marginHoriz.toInt()} dp",
            onValueChange = onMarginHorizChange
        )

        SettingsDivider()

        SettingsSlider(
            icon = Icons.Outlined.Landscape,
            label = "上下边距",
            value = marginVert,
            valueRange = 40f..100f,
            valueText = "${marginVert.toInt()} dp",
            onValueChange = onMarginVertChange
        )
    }
}

// ─── 显示与外观 ────────────────────────────────────────────────

@Composable
private fun DisplaySection(
    darkMode: String,
    readerTheme: String,
    onDarkModeChange: (String) -> Unit,
    onReaderThemeChange: (String) -> Unit
) {
    SectionTitle("显示与外观")
    Spacer(Modifier.height(AppSpace.sm))
    SettingsCard {
        // 深色模式
        val darkModeOptions = listOf(
            "system" to "跟随系统",
            "light" to "浅色",
            "dark" to "深色"
        )
        SettingsOptionRow(
            icon = Icons.Outlined.Brightness6,
            label = "深色模式",
            options = darkModeOptions,
            selected = darkMode,
            onSelect = onDarkModeChange
        )

        SettingsDivider()

        // 阅读主题
        val themeOptions = listOf(
            "day" to "日间",
            "night" to "夜间",
            "sepia" to "护眼",
            "green" to "绿色"
        )
        SettingsOptionRow(
            icon = Icons.Outlined.Palette,
            label = "默认阅读主题",
            options = themeOptions,
            selected = readerTheme,
            onSelect = onReaderThemeChange
        )
    }
}

// ─── 阅读目标 ──────────────────────────────────────────────────

@Composable
private fun ReadingGoalSection(
    dailyGoal: Int,
    onDailyGoalChange: (Int) -> Unit
) {
    SectionTitle("阅读目标")
    Spacer(Modifier.height(AppSpace.sm))
    SettingsCard {
        SettingsSlider(
            icon = Icons.Outlined.Timer,
            label = "每日目标",
            value = dailyGoal.toFloat(),
            valueRange = 10f..120f,
            valueText = "$dailyGoal 分钟",
            onValueChange = { onDailyGoalChange(it.toInt()) },
            steps = 21 // 10, 15, 20, ... 120
        )
    }
}

// ─── 存储管理 ──────────────────────────────────────────────────

@Composable
private fun StorageSection(
    cacheSize: String,
    onClearCache: () -> Unit,
    onClearAllData: () -> Unit
) {
    SectionTitle("存储管理")
    Spacer(Modifier.height(AppSpace.sm))
    SettingsCard {
        // 缓存大小
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(AppSpace.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Outlined.Speed,
                contentDescription = null,
                tint = AppColors.TextSecondary,
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.width(AppSpace.md))
            Text(
                text = "占用空间",
                fontSize = AppType.Body,
                color = AppColors.TextPrimary,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = cacheSize,
                fontSize = AppType.BodySmall,
                color = AppColors.TextSecondary
            )
        }

        SettingsDivider()

        // 清除缓存
        SettingsActionRow(
            icon = Icons.Outlined.DeleteSweep,
            label = "清除缓存",
            onClick = onClearCache
        )

        SettingsDivider()

        // 清除全部数据
        SettingsActionRow(
            icon = Icons.Outlined.DeleteForever,
            label = "清除所有数据",
            labelColor = Color.Red,
            onClick = onClearAllData
        )
    }
}

// ─── 关于应用 ──────────────────────────────────────────────────

@Composable
private fun AboutSection() {
    SectionTitle("关于应用")
    Spacer(Modifier.height(AppSpace.sm))
    SettingsCard {
        // 版本信息
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(AppSpace.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Outlined.Info,
                contentDescription = null,
                tint = AppColors.TextSecondary,
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.width(AppSpace.md))
            Text(
                text = "版本",
                fontSize = AppType.Body,
                color = AppColors.TextPrimary,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "1.0.3",
                fontSize = AppType.BodySmall,
                color = AppColors.TextSecondary
            )
        }

        SettingsDivider()

        // 隐私声明
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(AppSpace.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Outlined.NightsStay,
                contentDescription = null,
                tint = AppColors.TextSecondary,
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.width(AppSpace.md))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "隐私声明",
                    fontSize = AppType.Body,
                    color = AppColors.TextPrimary
                )
                Text(
                    text = "本应用完全离线运行，无网络权限，无第三方SDK",
                    fontSize = AppType.Caption,
                    color = AppColors.TextSecondary
                )
            }
        }
    }
}

// ─── 通用组件 ──────────────────────────────────────────────────

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        fontSize = AppType.BodySmall,
        fontWeight = FontWeight.Medium,
        color = AppColors.TextSecondary,
        modifier = Modifier.padding(horizontal = AppSpace.lg, vertical = AppSpace.xs)
    )
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppSpace.md)
            .shadow(8.dp, RoundedCornerShape(AppRadius.lg), ambientColor = Color(0x06000000), spotColor = Color(0x06000000))
            .clip(RoundedCornerShape(AppRadius.lg))
            .background(AppColors.CardBg)
    ) {
        content()
    }
}

@Composable
private fun SettingsDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppSpace.md)
            .height(0.5.dp)
            .background(AppColors.Divider)
    )
}

@Composable
private fun SettingsSlider(
    icon: ImageVector,
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    valueText: String,
    onValueChange: (Float) -> Unit,
    steps: Int = 0
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppSpace.md, vertical = AppSpace.sm)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = AppColors.TextSecondary,
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.width(AppSpace.md))
            Text(
                text = label,
                fontSize = AppType.Body,
                color = AppColors.TextPrimary,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = valueText,
                fontSize = AppType.BodySmall,
                color = AppColors.Accent,
                fontWeight = FontWeight.Medium
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            modifier = Modifier.fillMaxWidth(),
            colors = SliderDefaults.colors(
                thumbColor = AppColors.Accent,
                activeTrackColor = AppColors.Accent,
                inactiveTrackColor = AppColors.BgGray
            )
        )
    }
}

@Composable
private fun SettingsOptionRow(
    icon: ImageVector,
    label: String,
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppSpace.md, vertical = AppSpace.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = AppColors.TextSecondary,
            modifier = Modifier.size(22.dp)
        )
        Spacer(Modifier.width(AppSpace.md))
        Text(
            text = label,
            fontSize = AppType.Body,
            color = AppColors.TextPrimary,
            modifier = Modifier.weight(1f)
        )
        // 选项 Chip
        Row(horizontalArrangement = Arrangement.spacedBy(AppSpace.xs)) {
            options.forEach { (key, display) ->
                val isSelected = key == selected
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(AppRadius.sm))
                        .background(if (isSelected) AppColors.Accent else AppColors.BgGray)
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() }
                        ) { onSelect(key) }
                        .padding(horizontal = AppSpace.sm, vertical = AppSpace.xs)
                ) {
                    Text(
                        text = display,
                        fontSize = AppType.Caption,
                        color = if (isSelected) Color.White else AppColors.TextSecondary,
                        fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsActionRow(
    icon: ImageVector,
    label: String,
    labelColor: Color = AppColors.TextPrimary,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) { onClick() }
            .padding(AppSpace.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (labelColor == Color.Red) Color.Red else AppColors.TextSecondary,
            modifier = Modifier.size(22.dp)
        )
        Spacer(Modifier.width(AppSpace.md))
        Text(
            text = label,
            fontSize = AppType.Body,
            color = labelColor,
            modifier = Modifier.weight(1f)
        )
    }
}
