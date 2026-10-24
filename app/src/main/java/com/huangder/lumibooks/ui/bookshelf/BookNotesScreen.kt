package com.huangder.lumibooks.ui.bookshelf

import com.huangder.lumibooks.ui.excerpt.ExcerptLauncher
import com.huangder.lumibooks.ui.excerpt.ExcerptRequest
import com.huangder.lumibooks.ui.icons.directionalIcon
import com.huangder.lumibooks.ui.icons.AppIcons

import com.huangder.lumibooks.ui.components.lumiCardSurface
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import com.huangder.lumibooks.ui.animation.VisualMotion.spring
import com.huangder.lumibooks.ui.animation.VisualMotion.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import com.huangder.lumibooks.ui.theme.AppRoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Text
import com.huangder.lumibooks.ui.reader.resolveReaderHighlightColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.huangder.lumibooks.R
import com.huangder.lumibooks.domain.model.Bookmark
import com.huangder.lumibooks.domain.model.Note
import com.huangder.lumibooks.ui.components.AnnotationTagSheet
import androidx.compose.foundation.border
import com.huangder.lumibooks.ui.theme.AppColors
import androidx.compose.ui.res.stringResource
import com.huangder.lumibooks.ui.theme.AppRadius
import com.huangder.lumibooks.ui.theme.AppSpace
import com.huangder.lumibooks.ui.theme.AppType
import com.huangder.lumibooks.ui.theme.KaiTi
import com.huangder.lumibooks.ui.components.AnnotationListFilters
import com.huangder.lumibooks.ui.components.AnnotationTagChips
import com.huangder.lumibooks.ui.components.LiquidGlassMenuHost
import com.huangder.lumibooks.ui.components.LiquidGlassIconButton
import com.huangder.lumibooks.ui.components.LiquidGlassSegmentedControl
import com.huangder.lumibooks.ui.components.LiquidGlassSurface
import com.huangder.lumibooks.ui.components.ProvideLiquidGlassBackdrop
import com.huangder.lumibooks.ui.components.SwipeRevealItem
import com.huangder.lumibooks.ui.reader.NoteInputSheet
import com.huangder.lumibooks.ui.theme.LocalAppTheme
import com.huangder.lumibooks.ui.theme.LocalEInkMode
import com.huangder.lumibooks.ui.theme.LocalIsDarkTheme
import com.huangder.lumibooks.ui.theme.LocalMotionEnabled
import com.huangder.lumibooks.ui.theme.resolveAppFontFamily
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.Backdrop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun BookNotesScreen(
    onNavigateBack: () -> Unit,
    initialTab: Int = BookNotesActivity.TAB_HIGHLIGHTS,
    targetNoteId: Long? = null,
    viewModel: BookNotesViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val isLiquidGlass = LocalAppTheme.current == "liquid_glass"
    val excerptDark = LocalIsDarkTheme.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val glassBackdrop = rememberLayerBackdrop()
    val segmentedControlBackdrop = rememberLayerBackdrop()
    var selectedTab by rememberSaveable(initialTab) {
        mutableIntStateOf(initialTab.coerceIn(0, 3))
    }
    var pendingExportText by remember { mutableStateOf<String?>(null) }
    var editingBookmarkRemark by remember { mutableStateOf<Bookmark?>(null) }
    var bookmarkRemarkText by remember { mutableStateOf("") }
    var bookmarkRemarkTags by remember { mutableStateOf<List<String>>(emptyList()) }
    var editingNoteTags by remember { mutableStateOf<Note?>(null) }
    var editingBookmarkTags by remember { mutableStateOf<Bookmark?>(null) }
    var selectedTag by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedColor by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedLine by rememberSaveable { mutableStateOf<Int?>(null) }
    var managingTags by remember { mutableStateOf(false) }
    val renameTag: (String, String) -> Unit = { old, new ->
        if (selectedTag == old) selectedTag = new.trim()
        viewModel.renameTag(old, new)
    }
    val deleteTag: (String) -> Unit = { tag ->
        if (selectedTag == tag) selectedTag = null
        viewModel.deleteTag(tag)
    }

    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        val exportText = pendingExportText
        pendingExportText = null
        if (uri != null && exportText != null) {
            scope.launch {
                val result = withContext(Dispatchers.IO) {
                    runCatching {
                        val output = context.contentResolver.openOutputStream(uri)
                            ?: throw IOException("Unable to open export destination")
                        output.bufferedWriter(Charsets.UTF_8).use { writer ->
                            writer.write(exportText)
                        }
                    }
                }
                Toast.makeText(
                    context,
                    if (result.isSuccess) R.string.export_bookmarks_success
                    else R.string.export_bookmarks_failed,
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    val shareNote: (Note) -> Unit = { note ->
        uiState.book?.let { book ->
            scope.launch {
                runCatching {
                    ExcerptLauncher.open(context, viewModel.excerptTheme(ExcerptRequest.fromNote(book, note,
                        context.getString(R.string.chapter_number, note.chapterIndex + 1)), excerptDark))
                }.onFailure { Toast.makeText(context, R.string.excerpt_failed, Toast.LENGTH_SHORT).show() }
            }
        }
    }
    val shareBookmark: (Bookmark) -> Unit = { bookmark ->
        uiState.book?.let { book ->
            scope.launch {
                runCatching {
                    ExcerptLauncher.open(context, viewModel.excerptTheme(ExcerptRequest.fromBookmark(book, bookmark,
                        context.getString(R.string.chapter_number, bookmark.chapterIndex + 1)), excerptDark))
                }.onFailure { Toast.makeText(context, R.string.excerpt_failed, Toast.LENGTH_SHORT).show() }
            }
        }
    }
    val tabs = listOf(
        stringResource(R.string.tab_highlights),
        stringResource(R.string.tab_underline),
        stringResource(R.string.tab_notes),
        stringResource(R.string.tab_bookmarks)
    )

    LiquidGlassMenuHost(
        // Controls sample only the background; the host captures the completed page
        // separately for menus. Sampling glassBackdrop here would capture themselves.
        backdrop = segmentedControlBackdrop.takeIf { isLiquidGlass },
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.PageBg)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (isLiquidGlass) Modifier.layerBackdrop(segmentedControlBackdrop)
                    else Modifier
                )
                .background(AppColors.PageBg)
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .then(
                    if (isLiquidGlass) Modifier.layerBackdrop(glassBackdrop) else Modifier
                )
                .background(AppColors.PageBg)
        ) {
            // ── 顶栏：返回 + 标题 ──
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = if (isLiquidGlass) AppSpace.sm else AppSpace.lg,
                        vertical = AppSpace.sm
                    )
            ) {
                LiquidGlassIconButton(
                    imageVector = directionalIcon(AppIcons.ArrowLeft, AppIcons.ArrowRight),
                    contentDescription = stringResource(R.string.back),
                    onClick = onNavigateBack,
                    size = if (isLiquidGlass) 48.dp else 36.dp,
                    iconSize = if (isLiquidGlass) 20.dp else 18.dp,
                    contentColor = AppColors.TextPrimary,
                    normalContainerColor = AppColors.BgGray,
                    settingsBackButton = true
                )
                Spacer(Modifier.width(AppSpace.md))
                Text(
                    text = stringResource(R.string.notes_title),
                    fontSize = AppType.Section,
                    fontWeight = FontWeight.Bold,
                    fontFamily = resolveAppFontFamily(KaiTi),
                    color = AppColors.TextPrimary,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                LiquidGlassIconButton(
                    imageVector = AppIcons.Tag,
                    contentDescription = stringResource(R.string.annotation_tag_manage),
                    onClick = { managingTags = true }, size = 44.dp, iconSize = 20.dp,
                    normalContainerColor = AppColors.BgGray
                )
            }

            Spacer(Modifier.height(AppSpace.md))

            // ── 分段选择器 Tab ──
            SegmentedTabBar(
                selectedTab = selectedTab,
                tabs = tabs,
                onTabSelected = { selectedTab = it; selectedColor = null; selectedLine = null },
                modifier = Modifier.padding(horizontal = AppSpace.lg),
                backdrop = segmentedControlBackdrop.takeIf { isLiquidGlass }
            )

            Spacer(Modifier.height(AppSpace.md))

            // ── 内容列表 ──
            AnnotationListFilters(
                tags = (uiState.notes.flatMap { it.tags } + uiState.bookmarks.flatMap { it.tags }).distinct().sorted(),
                selectedTag = selectedTag,
                onTagSelected = { selectedTag = it },
                colors = if (selectedTab < 2) uiState.notes.filter { !it.isNoteEntry && (selectedTab == 1) == (it.type == "underline") }.map { it.color }.distinct() else emptyList(),
                selectedColor = selectedColor,
                onColorSelected = { selectedColor = it },
                selectedLine = selectedLine,
                onLineSelected = { selectedLine = it },
                showLines = selectedTab == 1,
                modifier = Modifier.padding(horizontal = AppSpace.lg)
            )
            val filter = ReadingMarkFilter(selectedTag, selectedColor, selectedLine)
            when (selectedTab) {
                0 -> NoteList(
                    onShare = shareNote,
                    notes = uiState.highlights.filter(filter::matches),
                    targetNoteId = targetNoteId,
                    onEditTags = { editingNoteTags = it },
                    onDelete = { viewModel.deleteNote(it) }
                )
                1 -> NoteList(
                    onShare = shareNote,
                    notes = uiState.underlines.filter(filter::matches),
                    targetNoteId = targetNoteId,
                    onEditTags = { editingNoteTags = it },
                    onDelete = { viewModel.deleteNote(it) }
                )
                2 -> NoteList(
                    onShare = shareNote,
                    notes = uiState.noteItems.filter(filter::matches),
                    targetNoteId = targetNoteId,
                    onEditTags = { editingNoteTags = it },
                    onDelete = { viewModel.deleteNote(it) }
                )
                3 -> BookmarkList(
                    onShare = shareBookmark,
                    bookmarks = uiState.bookmarks.filter(filter::matches),
                    onEditTags = { editingBookmarkTags = it },
                    onEditRemark = { bookmark ->
                        editingBookmarkRemark = bookmark
                        bookmarkRemarkText = bookmark.remark
                        bookmarkRemarkTags = bookmark.tags
                    },
                    onDelete = { viewModel.deleteBookmark(it) }
                )
            }
        }

        ProvideLiquidGlassBackdrop(
            backdrop = glassBackdrop.takeIf { isLiquidGlass }
        ) {
            ExportBookmarksButton(
                isLiquidGlass = isLiquidGlass,
                isExporting = uiState.isExporting,
                enabled = uiState.book != null &&
                    (uiState.bookmarks.isNotEmpty() || uiState.notes.isNotEmpty()),
                onClick = {
                    viewModel.prepareExport { result ->
                        result.onSuccess { document ->
                            pendingExportText = document.text
                            runCatching {
                                createDocumentLauncher.launch(document.fileName)
                            }.onFailure {
                                pendingExportText = null
                                Toast.makeText(
                                    context,
                                    R.string.export_bookmarks_failed,
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }.onFailure {
                            Toast.makeText(
                                context,
                                R.string.export_bookmarks_failed,
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(end = AppSpace.lg, bottom = AppSpace.lg)
            )
        }
        NoteInputSheet(
            visible = editingBookmarkRemark != null,
            glassBackdrop = glassBackdrop.takeIf { isLiquidGlass },
            initialText = bookmarkRemarkText,
            onTextChange = { bookmarkRemarkText = it },
            tags = bookmarkRemarkTags,
            availableTags = uiState.availableTags,
            onTagsChange = { bookmarkRemarkTags = it },
            onCreateTag = viewModel::createTag,
            onConfirm = {
                editingBookmarkRemark?.let {
                    viewModel.updateBookmarkRemarkAndTags(it, bookmarkRemarkText, bookmarkRemarkTags)
                }
            },
            onDismiss = { editingBookmarkRemark = null; bookmarkRemarkText = "" },
            title = stringResource(R.string.bookmark_remark_title),
            placeholder = stringResource(R.string.bookmark_remark_placeholder),
            footer = stringResource(R.string.bookmark_remark_setting_hint)
        )
        editingNoteTags?.let { note ->
            AnnotationTagSheet(
                backdrop = glassBackdrop.takeIf { isLiquidGlass },
                selected = uiState.notes.firstOrNull { it.id == note.id }?.tags ?: note.tags,
                available = uiState.availableTags,
                onSave = { viewModel.updateNoteTags(note, it) },
                onCreate = viewModel::createTag,
                onDismiss = { editingNoteTags = null },
                onRename = renameTag,
                onDelete = deleteTag
            )
        }
        editingBookmarkTags?.let { bookmark ->
            AnnotationTagSheet(
                backdrop = glassBackdrop.takeIf { isLiquidGlass },
                selected = uiState.bookmarks.firstOrNull { it.id == bookmark.id }?.tags ?: bookmark.tags,
                available = uiState.availableTags,
                onSave = { viewModel.updateBookmarkTags(bookmark, it) },
                onCreate = viewModel::createTag,
                onDismiss = { editingBookmarkTags = null },
                onRename = renameTag,
                onDelete = deleteTag
            )
        }
        if (managingTags) AnnotationTagSheet(
            backdrop = glassBackdrop.takeIf { isLiquidGlass },
            selected = emptyList(),
            available = uiState.availableTags,
            onSave = { names -> names.forEach { viewModel.createTag(it) } },
            onCreate = viewModel::createTag,
            onDismiss = { managingTags = false },
            onRename = renameTag,
            onDelete = deleteTag,
            managementOnly = true
        )
    }
}

// ─── 分段选择器（设计规范：#F2F2F7 背景, 圆角 20dp, 选中白色带阴影）───

@Composable
internal fun SegmentedTabBar(
    selectedTab: Int,
    tabs: List<String>,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    backdrop: Backdrop? = null,
    labelFontSize: TextUnit = 14.sp
) {
    val tabHeight = (40f * LocalDensity.current.fontScale.coerceIn(1f, 1.6f)).dp
    if (LocalAppTheme.current == "liquid_glass" && !LocalEInkMode.current) {
        LiquidGlassSegmentedControl(
            itemCount = tabs.size,
            selectedIndex = selectedTab,
            onSelected = onTabSelected,
            modifier = modifier.fillMaxWidth(),
            trackHeight = tabHeight,
            trackPadding = 2.dp,
            backdrop = backdrop
        ) { index, isSelected ->
            Text(
                text = tabs[index],
                fontSize = labelFontSize,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (isSelected) AppColors.TextPrimary.copy(alpha = 0.86f) else AppColors.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        return
    }

    val isDark = LocalIsDarkTheme.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(tabHeight)
            .clip(AppRoundedCornerShape(20.dp))
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
                .clip(AppRoundedCornerShape(18.dp))
                .shadow(2.dp, AppRoundedCornerShape(18.dp))
                .background(if (isDark) AppColors.CardBg else Color.White)
        )

        // Tab 文字
        Row(modifier = Modifier.fillMaxSize()) {
            tabs.forEachIndexed { index, label ->
                val isSelected = index == selectedTab
                val interactionSource = remember { MutableInteractionSource() }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .clickable(
                            indication = null,
                            interactionSource = interactionSource,
                            onClick = { onTabSelected(index) }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        fontSize = labelFontSize,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (isSelected) AppColors.TextPrimary else AppColors.TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
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
    onShare: (Note) -> Unit,
    targetNoteId: Long? = null,
    onEditTags: (Note) -> Unit,
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
        val listState = rememberLazyListState()
        var targetHandled by rememberSaveable(targetNoteId) { mutableStateOf(false) }
        LaunchedEffect(notes, targetNoteId, targetHandled) {
            if (!targetHandled && targetNoteId != null) {
                val targetIndex = notes.indexOfFirst { it.id == targetNoteId }
                if (targetIndex >= 0) {
                    listState.scrollToItem(targetIndex)
                    targetHandled = true
                }
            }
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = AppSpace.lg,
                top = AppSpace.sm,
                end = AppSpace.lg,
                bottom = 96.dp
            ),
            verticalArrangement = Arrangement.spacedBy(AppSpace.sm)
        ) {
            items(
                items = notes,
                key = { note -> note.id }
            ) { note ->
                HighlightNoteItem(
                    note = note,
                    onShare = { onShare(note) },
                    onEditTags = { onEditTags(note) },
                    onDelete = { onDelete(note) }
                )
            }
        }
    }
}

@Composable
private fun BookmarkList(
    bookmarks: List<Bookmark>,
    onShare: (Bookmark) -> Unit,
    onEditRemark: (Bookmark) -> Unit,
    onEditTags: (Bookmark) -> Unit,
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
            contentPadding = PaddingValues(
                start = AppSpace.lg,
                top = AppSpace.sm,
                end = AppSpace.lg,
                bottom = 96.dp
            ),
            verticalArrangement = Arrangement.spacedBy(AppSpace.sm)
        ) {
            items(bookmarks) { bookmark ->
                BookmarkItem(
                    bookmark = bookmark,
                    onShare = { onShare(bookmark) },
                    onEditTags = { onEditTags(bookmark) },
                    onEditRemark = { onEditRemark(bookmark) },
                    onDelete = { onDelete(bookmark) }
                )
            }
        }
    }
}

// ─── 高亮/笔记列表项（设计规范样式）────────────────────────────

@Composable
internal fun HighlightNoteItem(
    note: Note,
    onShare: () -> Unit,
    onEditTags: () -> Unit,
    onDelete: () -> Unit,
    header: (@Composable () -> Unit)? = null,
    dateText: String = formatDate(note.createdAt),
    /** Cross-book feed: tapping a clipped card shows the whole passage instead of navigating. */
    expandable: Boolean = false,
    cornerRadius: Dp = 12.dp
) {
    val shape = AppRoundedCornerShape(cornerRadius)
    var expanded by rememberSaveable(note.id) { mutableStateOf(false) }
    var truncated by remember(note.id) { mutableStateOf(false) }
    val motionEnabled = LocalMotionEnabled.current
    SwipeRevealItem(
        onShare = onShare,
        onEditTags = onEditTags,
        onDelete = onDelete,
        onClick = if (expandable && (truncated || expanded)) ({ expanded = !expanded }) else null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (expandable && motionEnabled) {
                        Modifier.animateContentSize(
                            animationSpec = spring(stiffness = 400f, visibilityThreshold = IntSize(1, 1))
                        )
                    } else Modifier
                )
                .clip(shape)
                .lumiCardSurface(shape = shape)
                .border(1.dp, AppColors.TextSecondary.copy(alpha = 0.24f), shape)
                .padding(16.dp)
        ) {
            header?.let {
                it()
                Spacer(Modifier.height(AppSpace.sm))
            }
            // 高亮色条 + 文字
            Row(verticalAlignment = Alignment.Top) {
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .height(20.dp)
                        .clip(AppRoundedCornerShape(2.dp))
                        .background(parseColor(note.color))
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = if (expanded) note.selectedText else note.selectedText.replace('\n', ' '),
                    fontSize = 14.sp,
                    color = AppColors.TextPrimary,
                    maxLines = if (expanded) Int.MAX_VALUE else 2,
                    overflow = TextOverflow.Ellipsis,
                    onTextLayout = { if (expandable && !expanded && it.hasVisualOverflow) truncated = true },
                    modifier = Modifier.weight(1f)
                )
            }

            if (note.isStale) {
                Text(stringResource(R.string.markdown_original_changed), fontSize = 12.sp, color = AppColors.Accent)
            }
            // 笔记内容（如果有）
            if (note.note.isNotBlank()) {
                Spacer(Modifier.height(AppSpace.sm))
                Text(
                    text = note.note,
                    fontSize = AppType.Caption,
                    color = AppColors.TextSecondary,
                    maxLines = if (expanded) Int.MAX_VALUE else 5,
                    overflow = TextOverflow.Ellipsis,
                    onTextLayout = { if (expandable && !expanded && it.hasVisualOverflow) truncated = true }
                )
            }
            AnnotationTagChips(note.tags, modifier = Modifier.padding(top = 6.dp))

            Spacer(Modifier.height(AppSpace.sm))

            // 底部：章节 + 日期
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
                Text(
                    text = dateText,
                    fontSize = 12.sp,
                    color = AppColors.Accent
                )
            }
        }
    }
}

// ─── 书签列表项 ──────────────────────────────────────────────────

@Composable
internal fun BookmarkItem(
    bookmark: Bookmark,
    onShare: () -> Unit,
    onEditRemark: () -> Unit,
    onEditTags: () -> Unit,
    onDelete: () -> Unit,
    header: (@Composable () -> Unit)? = null,
    dateText: String = formatDate(bookmark.createdAt),
    cornerRadius: Dp = 12.dp
) {
    val shape = AppRoundedCornerShape(cornerRadius)
    SwipeRevealItem(
        onShare = onShare,
        onEdit = onEditRemark,
        onEditTags = onEditTags,
        onDelete = onDelete
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .lumiCardSurface(shape = shape)
                .border(1.dp, AppColors.TextSecondary.copy(alpha = 0.24f), shape)
                .padding(16.dp)
        ) {
            header?.let {
                it()
                Spacer(Modifier.height(AppSpace.sm))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = AppIcons.Bookmark.regular,
                    contentDescription = null,
                    tint = AppColors.Accent,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(AppSpace.md))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = (if (bookmark.isStale) stringResource(R.string.markdown_original_changed) + " · " else "") +
                            bookmark.title.ifBlank { stringResource(R.string.chapter_number, bookmark.chapterIndex + 1) },
                        fontSize = AppType.BodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = AppColors.TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = dateText,
                        fontSize = AppType.Caption,
                        color = AppColors.TextSecondary
                    )
                    if (bookmark.remark.isNotBlank()) {
                        Text(bookmark.remark, fontSize = AppType.Caption,
                            color = AppColors.TextSecondary)
                    }
                    AnnotationTagChips(bookmark.tags, modifier = Modifier.padding(top = 6.dp))
                }
            }
        }
    }
}

// ─── 工具函数 ────────────────────────────────────────────────────

@Composable
private fun ExportBookmarksButton(
    isLiquidGlass: Boolean,
    isExporting: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val buttonEnabled = enabled && !isExporting
    val shape = CircleShape
    val content: @Composable () -> Unit = {
        if (isExporting) {
            CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                color = Color.White,
                strokeWidth = 2.4.dp
            )
        } else {
            Icon(
                imageVector = AppIcons.DownloadSimple,
                contentDescription = stringResource(R.string.export_bookmarks),
                tint = Color.White,
                modifier = Modifier.size(27.dp)
            )
        }
    }

    if (isLiquidGlass) {
        LiquidGlassSurface(
            controlEdge = true,
            shape = shape,
            fallbackColor = Color.Black,
            contentScrimColor = Color.Black.copy(alpha = 0.85f),
            enabled = buttonEnabled,
            onClick = onClick,
            effectPadding = 2.dp,
            decorationModifier = Modifier,
            modifier = modifier
                .size(60.dp)
        ) {
            content()
        }
    } else {
        IconButton(
            onClick = onClick,
            enabled = buttonEnabled,
            modifier = modifier
                .size(58.dp)
                .shadow(
                    elevation = 8.dp,
                    shape = shape,
                    clip = false,
                    ambientColor = Color.Black.copy(alpha = 0.14f),
                    spotColor = Color.Black.copy(alpha = 0.18f)
                )
                .clip(shape),
            colors = IconButtonDefaults.iconButtonColors(
                containerColor = Color.Black,
                contentColor = Color.White,
                disabledContainerColor = Color.Black.copy(alpha = 0.38f),
                disabledContentColor = Color.White.copy(alpha = 0.62f)
            )
        ) {
            content()
        }
    }
}

private fun parseColor(hex: String): Color {
    return try {
        Color(android.graphics.Color.parseColor(resolveReaderHighlightColor(hex)))
    } catch (_: Exception) {
        Color(0xFFE85D5D)
    }
}

private fun formatDate(timestamp: Long): String {
    val sdf = SimpleDateFormat("MM/dd", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
