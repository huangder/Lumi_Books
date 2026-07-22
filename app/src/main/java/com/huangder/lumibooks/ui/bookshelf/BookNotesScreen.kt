package com.huangder.lumibooks.ui.bookshelf

import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.huangder.lumibooks.R
import com.huangder.lumibooks.domain.model.Bookmark
import com.huangder.lumibooks.domain.model.Note
import com.huangder.lumibooks.ui.theme.AppColors
import androidx.compose.ui.res.stringResource
import com.huangder.lumibooks.ui.theme.AppRadius
import com.huangder.lumibooks.ui.theme.AppSpace
import com.huangder.lumibooks.ui.theme.AppType
import com.huangder.lumibooks.ui.theme.KaiTi
import com.huangder.lumibooks.ui.components.LiquidGlassIconButton
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun BookNotesScreen(
    onNavigateBack: () -> Unit,
    viewModel: BookNotesViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedTab by remember { mutableIntStateOf(0) }

    val tabs = listOf(
        stringResource(R.string.tab_highlights),
        stringResource(R.string.tab_notes),
        stringResource(R.string.tab_bookmarks)
    )

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
                .padding(horizontal = AppSpace.lg, vertical = AppSpace.sm)
        ) {
            LiquidGlassIconButton(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.back),
                onClick = onNavigateBack,
                size = 36.dp,
                iconSize = 18.dp,
                contentColor = AppColors.TextPrimary,
                normalContainerColor = AppColors.BgGray
            )
            Spacer(Modifier.width(AppSpace.md))
            Text(
                text = stringResource(R.string.notes_title),
                fontSize = AppType.Section,
                fontWeight = FontWeight.Bold,
                fontFamily = KaiTi,
                color = AppColors.TextPrimary
            )
        }

        Spacer(Modifier.height(AppSpace.md))

        // ── 分段选择器 Tab ──
        SegmentedTabBar(
            selectedTab = selectedTab,
            tabs = tabs,
            onTabSelected = { selectedTab = it },
            modifier = Modifier.padding(horizontal = AppSpace.lg)
        )

        Spacer(Modifier.height(AppSpace.md))

        // ── 内容列表 ──
        when (selectedTab) {
            0 -> NoteList(
                notes = uiState.highlights,
                onDelete = { viewModel.deleteNote(it) }
            )
            1 -> NoteList(
                notes = uiState.noteItems,
                onDelete = { viewModel.deleteNote(it) }
            )
            2 -> BookmarkList(
                bookmarks = uiState.bookmarks,
                onDelete = { viewModel.deleteBookmark(it) }
            )
        }
    }
}

// ─── 分段选择器（设计规范：#F2F2F7 背景, 圆角 20dp, 选中白色带阴影）───

@Composable
private fun SegmentedTabBar(
    selectedTab: Int,
    tabs: List<String>,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(40.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(AppColors.BgGray)
            .padding(2.dp)
    ) {
        // 选中指示器（graphicsLayer translationX 实现平滑滑动）
        val indicatorOffset by animateFloatAsState(
            targetValue = selectedTab.toFloat(),
            animationSpec = tween(200),
            label = "tabIndicator"
        )
        val tabFraction = 1f / tabs.size

        Box(
            modifier = Modifier
                .fillMaxWidth(tabFraction)
                .fillMaxSize()
                .graphicsLayer {
                    translationX = indicatorOffset * size.width
                }
                .clip(RoundedCornerShape(18.dp))
                .shadow(2.dp, RoundedCornerShape(18.dp))
                .background(Color.White)
        )

        // Tab 文字
        Row(modifier = Modifier.fillMaxSize()) {
            tabs.forEachIndexed { index, label ->
                val isSelected = index == selectedTab
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .clickable { onTabSelected(index) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        fontSize = 14.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (isSelected) AppColors.TextPrimary else AppColors.TextSecondary
                    )
                }
            }
        }
    }
}

// ─── 内容列表 ────────────────────────────────────────────────────

@Composable
private fun NoteList(
    notes: List<Note>,
    onDelete: (Note) -> Unit
) {
    if (notes.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(R.string.no_content),
                fontSize = AppType.Body,
                color = AppColors.TextSecondary
            )
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = AppSpace.lg, vertical = AppSpace.sm),
            verticalArrangement = Arrangement.spacedBy(AppSpace.sm)
        ) {
            items(notes) { note ->
                HighlightNoteItem(
                    note = note,
                    onDelete = { onDelete(note) }
                )
            }
        }
    }
}

@Composable
private fun BookmarkList(
    bookmarks: List<Bookmark>,
    onDelete: (Bookmark) -> Unit
) {
    if (bookmarks.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(R.string.no_bookmarks),
                fontSize = AppType.Body,
                color = AppColors.TextSecondary
            )
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = AppSpace.lg, vertical = AppSpace.sm),
            verticalArrangement = Arrangement.spacedBy(AppSpace.sm)
        ) {
            items(bookmarks) { bookmark ->
                BookmarkItem(
                    bookmark = bookmark,
                    onDelete = { onDelete(bookmark) }
                )
            }
        }
    }
}

// ─── 高亮/笔记列表项（设计规范样式）────────────────────────────

@Composable
private fun HighlightNoteItem(
    note: Note,
    onDelete: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFFFFFBF0))
            .padding(16.dp)
    ) {
        // 高亮色条 + 文字
        Row(verticalAlignment = Alignment.Top) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(20.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(parseColor(note.color))
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = note.selectedText,
                fontSize = 14.sp,
                color = AppColors.TextPrimary,
                maxLines = 2,
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

        // 底部：章节 + 日期 + 删除
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.chapter_number, note.chapterIndex + 1),
                fontSize = 12.sp,
                color = AppColors.Accent
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = formatDate(note.createdAt),
                    fontSize = 12.sp,
                    color = AppColors.Accent
                )
                Spacer(Modifier.width(AppSpace.sm))
                Icon(
                    imageVector = Icons.Outlined.Delete,
                    contentDescription = stringResource(R.string.delete),
                    tint = AppColors.TextSecondary,
                    modifier = Modifier
                        .size(16.dp)
                        .clickable(onClick = onDelete)
                )
            }
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
                text = bookmark.title.ifBlank { stringResource(R.string.chapter_number, bookmark.chapterIndex + 1) },
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
            contentDescription = stringResource(R.string.delete),
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
        Color(0xFFE85D5D)
    }
}

private fun formatDate(timestamp: Long): String {
    val sdf = SimpleDateFormat("MM/dd", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
