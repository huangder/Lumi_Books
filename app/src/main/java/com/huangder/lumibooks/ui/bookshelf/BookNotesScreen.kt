package com.huangder.lumibooks.ui.bookshelf

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.huangder.lumibooks.domain.model.Bookmark
import com.huangder.lumibooks.domain.model.Note
import com.huangder.lumibooks.ui.animation.AppEasing
import com.huangder.lumibooks.ui.theme.AppColors
import com.huangder.lumibooks.ui.theme.AppRadius
import com.huangder.lumibooks.ui.theme.AppSpace
import com.huangder.lumibooks.ui.theme.AppType
import com.huangder.lumibooks.ui.theme.KaiTi
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val tabs = listOf("高亮", "笔记", "书签")

@Composable
fun BookNotesScreen(
    onNavigateBack: () -> Unit,
    viewModel: BookNotesViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedTab by remember { mutableIntStateOf(0) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.WindowBg)
            .statusBarsPadding()
    ) {
        // ── 顶栏：返回 + 标题 ──
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AppSpace.sm, vertical = AppSpace.sm)
        ) {
            IconButton(onClick = onNavigateBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回",
                    tint = AppColors.TextPrimary
                )
            }
            Spacer(Modifier.width(AppSpace.sm))
            Text(
                text = "书签、高亮与笔记",
                fontSize = AppType.Section,
                fontWeight = FontWeight.Bold,
                fontFamily = KaiTi,
                color = AppColors.TextPrimary
            )
        }

        // ── Tab 切换栏 ──
        TabSwitcher(
            selectedTab = selectedTab,
            onTabSelected = { selectedTab = it }
        )

        Spacer(Modifier.height(AppSpace.md))

        // ── 内容列表 ──
        val items = when (selectedTab) {
            0 -> uiState.highlights
            1 -> uiState.noteItems
            2 -> uiState.bookmarks
            else -> emptyList()
        }

        if (items.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = when (selectedTab) {
                        0 -> "暂无高亮"
                        1 -> "暂无笔记"
                        2 -> "暂无书签"
                        else -> ""
                    },
                    fontSize = AppType.Body,
                    color = AppColors.TextSecondary
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = AppSpace.lg, vertical = AppSpace.md),
                verticalArrangement = Arrangement.spacedBy(AppSpace.sm)
            ) {
                when (selectedTab) {
                    0 -> items(items as List<Note>) { note ->
                        HighlightNoteItem(
                            note = note,
                            onDelete = { viewModel.deleteNote(note) }
                        )
                    }
                    1 -> items(items as List<Note>) { note ->
                        HighlightNoteItem(
                            note = note,
                            onDelete = { viewModel.deleteNote(note) }
                        )
                    }
                    2 -> items(items as List<Bookmark>) { bookmark ->
                        BookmarkItem(
                            bookmark = bookmark,
                            onDelete = { viewModel.deleteBookmark(bookmark) }
                        )
                    }
                }
            }
        }
    }
}

// ─── Tab 切换栏 ──────────────────────────────────────────────────

@Composable
private fun TabSwitcher(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppSpace.lg),
        horizontalArrangement = Arrangement.spacedBy(AppSpace.lg)
    ) {
        tabs.forEachIndexed { index, label ->
            val isSelected = index == selectedTab
            val textColor by animateColorAsState(
                targetValue = if (isSelected) AppColors.TextPrimary else AppColors.TextSecondary,
                animationSpec = tween(200, easing = AppEasing.Standard),
                label = "tabColor"
            )
            Text(
                text = label,
                fontSize = AppType.Body,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                color = textColor,
                modifier = Modifier.clickable { onTabSelected(index) }
            )
        }
    }
}

// ─── 高亮/笔记列表项 ────────────────────────────────────────────

@Composable
private fun HighlightNoteItem(
    note: Note,
    onDelete: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AppRadius.sm))
            .background(AppColors.CardBg)
            .padding(16.dp)
    ) {
        // 高亮色条 + 文字
        Row(verticalAlignment = Alignment.Top) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(20.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(parseColor(note.color))
            )
            Spacer(Modifier.width(AppSpace.sm))
            Text(
                text = note.selectedText,
                fontSize = AppType.BodySmall,
                color = AppColors.TextPrimary,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }

        // 笔记内容（如果有）
        if (note.note.isNotBlank()) {
            Spacer(Modifier.height(AppSpace.sm))
            Text(
                text = note.note,
                fontSize = AppType.Caption,
                color = AppColors.TextSecondary,
                maxLines = 5,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(Modifier.height(AppSpace.sm))

        // 底部：章节目录 + 删除
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "第 ${note.chapterIndex + 1} 章",
                fontSize = AppType.Caption,
                color = AppColors.TextSecondary
            )
            Icon(
                imageVector = Icons.Outlined.Delete,
                contentDescription = "删除",
                tint = AppColors.TextSecondary,
                modifier = Modifier
                    .size(18.dp)
                    .clickable(onClick = onDelete)
            )
        }
    }
}

// ─── 书签列表项 ──────────────────────────────────────────────────

@Composable
private fun BookmarkItem(
    bookmark: Bookmark,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AppRadius.sm))
            .background(AppColors.CardBg)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Outlined.Bookmark,
            contentDescription = null,
            tint = AppColors.Accent,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(AppSpace.md))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = bookmark.title.ifBlank { "第 ${bookmark.chapterIndex + 1} 章" },
                fontSize = AppType.BodySmall,
                fontWeight = FontWeight.SemiBold,
                color = AppColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = formatDate(bookmark.createdAt),
                fontSize = AppType.Caption,
                color = AppColors.TextSecondary
            )
        }
        Icon(
            imageVector = Icons.Outlined.Delete,
            contentDescription = "删除",
            tint = AppColors.TextSecondary,
            modifier = Modifier
                .size(18.dp)
                .clickable(onClick = onDelete)
        )
    }
}

// ─── 工具函数 ────────────────────────────────────────────────────

private fun parseColor(hex: String): Color {
    return try {
        Color(android.graphics.Color.parseColor(hex))
    } catch (_: Exception) {
        Color(0xFFE85D5D) // AppColors.Accent 的默认值
    }
}

private fun formatDate(timestamp: Long): String {
    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
