package com.huangder.lumibooks.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.FormatSize
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import coil.compose.AsyncImage
import com.huangder.lumibooks.ui.theme.AppColors
import com.huangder.lumibooks.ui.theme.AppRadius
import com.huangder.lumibooks.ui.theme.AppSpace
import com.huangder.lumibooks.ui.theme.AppType
import com.huangder.lumibooks.ui.theme.FangSong
import kotlinx.coroutines.launch
import java.io.File

// ─── 设置主页（分类列表）────────────────────────────────────────

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = androidx.hilt.navigation.compose.hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // 昵称编辑对话框状态
    var showNicknameDialog by remember { mutableStateOf(false) }

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
                    avatarFile.outputStream().use { output -> input.copyTo(output) }
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
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AppSpace.sm, vertical = AppSpace.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onNavigateBack) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, "返回", tint = AppColors.TextPrimary)
                }
                Spacer(Modifier.weight(1f))
                Text("设置", fontSize = AppType.Section, fontWeight = FontWeight.Bold, fontFamily = FangSong, color = AppColors.TextPrimary)
                Spacer(Modifier.weight(1f))
                Spacer(Modifier.size(48.dp))
            }

            Spacer(Modifier.height(AppSpace.sm))

            // 头像快捷入口
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AppSpace.md)
                    .shadow(8.dp, RoundedCornerShape(AppRadius.lg), ambientColor = Color(0x06000000), spotColor = Color(0x06000000))
                    .clip(RoundedCornerShape(AppRadius.lg))
                    .background(AppColors.CardBg)
                    .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {
                        photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }
                    .padding(AppSpace.md),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(AppColors.BgGray)
                        .border(1.dp, AppColors.Divider, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    if (uiState.avatarUri != null) {
                        AsyncImage(
                            model = File(uiState.avatarUri),
                            contentDescription = "头像",
                            modifier = Modifier.fillMaxSize().clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Icon(Icons.Outlined.AccountCircle, "默认头像", tint = AppColors.TextSecondary, modifier = Modifier.size(48.dp))
                    }
                }
                Spacer(Modifier.width(AppSpace.md))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        uiState.nickname,
                        fontSize = AppType.Body,
                        fontWeight = FontWeight.SemiBold,
                        color = AppColors.TextPrimary,
                        modifier = Modifier.clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() },
                            onClick = { showNicknameDialog = true }
                        )
                    )
                    Text("点击更换头像", fontSize = AppType.Caption, color = AppColors.TextSecondary)
                }
                Icon(Icons.Outlined.ChevronRight, null, tint = AppColors.TextSecondary, modifier = Modifier.size(20.dp))
            }

            Spacer(Modifier.height(AppSpace.lg))

            // 分类列表
            CategoryItem(Icons.Outlined.FormatSize, "阅读设置") {
                context.startActivity(Intent(context, DetailActivity::class.java).putExtra("category", "reading"))
            }
            CategoryItem(Icons.Outlined.Brightness6, "显示与外观") {
                context.startActivity(Intent(context, DetailActivity::class.java).putExtra("category", "display"))
            }
            CategoryItem(Icons.Outlined.Timer, "阅读目标") {
                context.startActivity(Intent(context, DetailActivity::class.java).putExtra("category", "goal"))
            }
            CategoryItem(Icons.Outlined.DeleteSweep, "存储管理") {
                context.startActivity(Intent(context, DetailActivity::class.java).putExtra("category", "storage"))
            }
            CategoryItem(Icons.Outlined.Info, "关于应用") {
                context.startActivity(Intent(context, DetailActivity::class.java).putExtra("category", "about"))
            }

            Spacer(Modifier.height(120.dp))
        }
    }

    // ── 昵称编辑对话框（卡片风格） ──
    if (showNicknameDialog) {
        androidx.compose.material3.BasicAlertDialog(
            onDismissRequest = { showNicknameDialog = false },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.4f))
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                        onClick = { showNicknameDialog = false }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(AppSpace.lg)
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() },
                            onClick = {}
                        )
                ) {
                    com.huangder.lumibooks.ui.components.EditInputDialog(
                        title = "修改昵称",
                        fields = listOf(
                            Triple("昵称", "显示原始昵称", uiState.nickname)
                        ),
                        onBack = { showNicknameDialog = false },
                        onConfirm = { values ->
                            viewModel.saveNickname(values[0])
                            showNicknameDialog = false
                        }
                    )
                }
            }
        }
    }
}

// ─── 分类条目 ──────────────────────────────────────────────────

@Composable
private fun CategoryItem(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppSpace.md, vertical = 1.dp)
            .shadow(6.dp, RoundedCornerShape(AppRadius.md), ambientColor = Color(0x04000000), spotColor = Color(0x04000000))
            .clip(RoundedCornerShape(AppRadius.md))
            .background(AppColors.CardBg)
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }, onClick = onClick)
            .padding(horizontal = AppSpace.md, vertical = AppSpace.md + 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = AppColors.TextSecondary, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(AppSpace.md))
        Text(label, fontSize = AppType.Body, color = AppColors.TextPrimary, modifier = Modifier.weight(1f))
        Icon(Icons.Outlined.ChevronRight, null, tint = AppColors.TextSecondary, modifier = Modifier.size(20.dp))
    }
}

