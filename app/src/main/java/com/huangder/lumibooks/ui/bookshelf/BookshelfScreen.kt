package com.huangder.lumibooks.ui.bookshelf

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.zIndex
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.huangder.lumibooks.domain.model.Book
import com.huangder.lumibooks.domain.model.BookFormat
import com.huangder.lumibooks.ui.animation.AppEasing
import com.huangder.lumibooks.ui.animation.OverscrollBounce
import com.huangder.lumibooks.ui.animation.PageEntranceItem
import com.huangder.lumibooks.ui.components.StatusGradientOverlay
import com.huangder.lumibooks.ui.components.ProvideLiquidGlassBackdrop
import com.huangder.lumibooks.ui.components.LiquidGlassAlertDialog
import com.huangder.lumibooks.ui.components.LiquidGlassButton
import com.huangder.lumibooks.ui.components.LiquidGlassIconButton
import com.huangder.lumibooks.ui.components.LiquidGlassMenuItem
import com.huangder.lumibooks.ui.components.LiquidGlassMenuSpec
import com.huangder.lumibooks.ui.components.LiquidGlassTextButton
import com.huangder.lumibooks.ui.components.LocalLiquidGlassMenuHost
import com.huangder.lumibooks.ui.home.HomeViewModel
import com.huangder.lumibooks.ui.theme.AppColors
import com.huangder.lumibooks.ui.theme.AppRadius
import com.huangder.lumibooks.ui.theme.AppSpace
import com.huangder.lumibooks.ui.theme.AppType
import com.huangder.lumibooks.ui.theme.KaiTi
import com.huangder.lumibooks.ui.theme.LocalAppTheme
import com.huangder.lumibooks.R
import com.huangder.lumibooks.util.FileUtils
import androidx.compose.ui.res.stringResource
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun BookshelfScreen(
    playEntranceAnimation: Boolean = false,
    onNavigateToReader: (bookId: String, coverPath: String?, title: String) -> Unit,
    onOverlayProgressChange: (Float) -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedFilter by remember { mutableStateOf<BookshelfFilter>(BookshelfFilter.All) }
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val contextMenuState = rememberBookContextMenuState()
    val isLiquidGlass = LocalAppTheme.current == "liquid_glass"
    val density = LocalDensity.current
    val bookshelfBackdrop = rememberLayerBackdrop()
    val bookshelfTopBlurBackdrop = rememberLayerBackdrop()
    var bookshelfHeaderHeightPx by remember { mutableStateOf(0) }
    var isEditing by remember { mutableStateOf(false) }
    var selectedBookIds by remember { mutableStateOf(emptySet<String>()) }
    var showBatchDeleteConfirm by remember { mutableStateOf(false) }
    var deletingBookIds by remember { mutableStateOf(emptySet<String>()) }
    var booksPendingDeletion by remember { mutableStateOf(emptyList<Book>()) }
    var isSearchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var expandedSearchBookId by remember { mutableStateOf<String?>(null) }
    var searchLauncherBounds by remember { mutableStateOf(Rect.Zero) }

    val filterTabs = buildList {
        add(BookshelfFilterTab(BookshelfFilter.All, stringResource(R.string.filter_all)))
        add(BookshelfFilterTab(BookshelfFilter.Downloaded, stringResource(R.string.filter_downloaded)))
        add(BookshelfFilterTab(BookshelfFilter.Pdf, stringResource(R.string.format_pdf)))
        add(BookshelfFilterTab(BookshelfFilter.Txt, stringResource(R.string.format_txt)))
        add(BookshelfFilterTab(BookshelfFilter.Favorites, stringResource(R.string.filter_favorites)))
        uiState.tags.forEach { tag ->
            add(BookshelfFilterTab(BookshelfFilter.Tag(tag.id), tag.name))
        }
    }

    LaunchedEffect(uiState.importMessage) {
        uiState.importMessage?.let {
            android.widget.Toast.makeText(context, it, android.widget.Toast.LENGTH_SHORT).show()
            viewModel.clearImportMessage()
        }
    }

    LaunchedEffect(uiState.tagMessage) {
        uiState.tagMessage?.let {
            android.widget.Toast.makeText(context, it, android.widget.Toast.LENGTH_SHORT).show()
            viewModel.clearTagMessage()
        }
    }

    LaunchedEffect(uiState.tags) {
        val activeTagFilter = selectedFilter as? BookshelfFilter.Tag
        if (activeTagFilter != null && uiState.tags.none { it.id == activeTagFilter.tagId }) {
            selectedFilter = BookshelfFilter.All
        }
    }

    LaunchedEffect(uiState.books) {
        val existingIds = uiState.books.mapTo(mutableSetOf()) { it.id }
        selectedBookIds = selectedBookIds.intersect(existingIds)
        if (expandedSearchBookId !in existingIds) expandedSearchBookId = null
    }

    LaunchedEffect(deletingBookIds) {
        if (deletingBookIds.isNotEmpty()) {
            kotlinx.coroutines.delay(350)
            viewModel.deleteBooks(booksPendingDeletion)
            deletingBookIds = emptySet()
            booksPendingDeletion = emptyList()
            selectedBookIds = emptySet()
            isEditing = false
        }
    }

    LaunchedEffect(isSearchActive) {
        if (!isSearchActive) {
            kotlinx.coroutines.delay(540)
            if (!isSearchActive) searchQuery = ""
        }
    }

    // 通知 NavGraph 隐藏/显示底部 TabBar
    // 编辑书本信息对话框状态
    var showEditDialog by remember { mutableStateOf(false) }
    var editingBook by remember { mutableStateOf<Book?>(null) }

    // 自定义封面：记录正在操作的书本
    var coverTargetBook by remember { mutableStateOf<Book?>(null) }

    // 删除动画：记录正在删除的书本 ID
    var tagTargetBook by remember { mutableStateOf<Book?>(null) }
    var showTagSheet by remember { mutableStateOf(false) }

    // 图片选择器（自定义封面）
    val coverPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val book = coverTargetBook ?: return@let
            val newCoverPath = FileUtils.copyCoverImage(context, it, book.id)
            if (newCoverPath != null) {
                viewModel.updateBook(book.copy(coverPath = newCoverPath))
            }
            coverTargetBook = null
        }
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.importBook(context, it) }
    }

    val tagIdsByBook = remember(uiState.bookTagLinks) {
        uiState.bookTagLinks
            .groupBy { it.bookId }
            .mapValues { (_, links) -> links.map { it.tagId }.toSet() }
    }
    val tagNamesByBook = remember(uiState.tags, uiState.bookTagLinks) {
        val tagNamesById = uiState.tags.associate { it.id to it.name }
        uiState.bookTagLinks
            .groupBy { it.bookId }
            .mapValues { (_, links) ->
                links.mapNotNull { link -> tagNamesById[link.tagId] }
            }
    }
    val filteredBooks = when (val filter = selectedFilter) {
        BookshelfFilter.All -> uiState.books
        BookshelfFilter.Downloaded -> uiState.books // 下载内容
        BookshelfFilter.Pdf -> uiState.books.filter { it.format == BookFormat.PDF }
        BookshelfFilter.Txt -> uiState.books.filter { it.format == BookFormat.TXT }
        BookshelfFilter.Favorites -> uiState.books.filter { it.isFavorite }
        is BookshelfFilter.Tag -> uiState.books.filter { book ->
            filter.tagId in tagIdsByBook[book.id].orEmpty()
        }
    }

    val searchBlurProgress by animateFloatAsState(
        targetValue = if (isSearchActive) 1f else 0f,
        animationSpec = tween(if (isSearchActive) 210 else 230),
        label = "bookshelfSearchBlur"
    )
    val overlayProgress = maxOf(contextMenuState.scrimAlpha.value, searchBlurProgress)
    SideEffect {
        onOverlayProgressChange(overlayProgress)
    }
    DisposableEffect(Unit) {
        onDispose { onOverlayProgressChange(0f) }
    }

    ProvideLiquidGlassBackdrop(bookshelfBackdrop.takeIf { isLiquidGlass }) {
    Box(modifier = Modifier.fillMaxSize().background(AppColors.WindowBg)) {
        // ── 内容层（高斯模糊） ──
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (isLiquidGlass) Modifier.layerBackdrop(bookshelfBackdrop) else Modifier
                )
                .graphicsLayer {
                    renderEffect = if (
                        overlayProgress > 0.01f && android.os.Build.VERSION.SDK_INT >= 31
                    ) {
                        android.graphics.RenderEffect.createBlurEffect(
                            maxOf(
                                20f * contextMenuState.scrimAlpha.value,
                                34.dp.toPx() * searchBlurProgress
                            ),
                            maxOf(
                                20f * contextMenuState.scrimAlpha.value,
                                34.dp.toPx() * searchBlurProgress
                            ),
                            android.graphics.Shader.TileMode.CLAMP
                        ).asComposeRenderEffect()
                    } else {
                        null
                    }
                }
                .background(AppColors.WindowBg)
        ) {
            if (isLiquidGlass) {
                OverscrollBounce(modifier = Modifier.fillMaxSize()) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        contentPadding = PaddingValues(
                            start = AppSpace.lg,
                            top = 240.dp,
                            end = AppSpace.lg,
                            bottom = 120.dp
                        ),
                        horizontalArrangement = Arrangement.spacedBy(AppSpace.lg),
                        verticalArrangement = Arrangement.spacedBy(AppSpace.lg),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        itemsIndexed(filteredBooks, key = { _, book -> book.id }) { index, book ->
                            PageEntranceItem(play = playEntranceAnimation, index = index + 2) {
                                AnimatedBookGridItem(
                                    book = book,
                                    isDeleting = book.id in deletingBookIds,
                                    isEditing = isEditing,
                                    isSelected = book.id in selectedBookIds,
                                    contextMenuState = contextMenuState,
                                    onHaptic = { haptic.performHapticFeedback(HapticFeedbackType.LongPress) },
                                    onSelectionToggle = {
                                        selectedBookIds = if (book.id in selectedBookIds) {
                                            selectedBookIds - book.id
                                        } else {
                                            selectedBookIds + book.id
                                        }
                                    },
                                    onClick = { onNavigateToReader(book.id, book.coverPath, book.title) }
                                )
                            }
                        }
                        if (!uiState.isLoading && !isEditing) {
                            item(key = "add_book") {
                                PageEntranceItem(
                                    play = playEntranceAnimation,
                                    index = filteredBooks.size + 2
                                ) {
                                    AddBookItem(onClick = { launcher.launch("*/*") })
                                }
                            }
                        }
                    }
                }
            } else {
            OverscrollBounce(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                ) {
                    Spacer(modifier = Modifier.height(AppSpace.md))
                    PageEntranceItem(
                        play = playEntranceAnimation,
                        index = 0,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = stringResource(R.string.bookshelf_title),
                            fontSize = AppType.Display,
                            fontWeight = FontWeight.Bold,
                            fontFamily = KaiTi,
                            letterSpacing = (-0.02).sp,
                            color = AppColors.TextPrimary,
                            modifier = Modifier.padding(horizontal = AppSpace.lg, vertical = AppSpace.md)
                        )
                    }

                    BookshelfSearchLauncher(
                        onClick = {
                            expandedSearchBookId = null
                            isSearchActive = true
                        },
                        onBoundsChanged = { searchLauncherBounds = it },
                        modifier = Modifier.padding(horizontal = AppSpace.lg)
                    )

                    // ── 筛选标签（可横向滚动） ──
                    PageEntranceItem(
                        play = playEntranceAnimation,
                        index = 1,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = AppSpace.lg, vertical = AppSpace.sm),
                            horizontalArrangement = Arrangement.spacedBy(AppSpace.lg)
                        ) {
                            filterTabs.forEach { tab ->
                                val isSelected = tab.filter == selectedFilter
                                Text(
                                    text = tab.label,
                                    fontSize = AppType.Body,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) AppColors.TextPrimary else AppColors.TextSecondary,
                                    modifier = Modifier.clickable { selectedFilter = tab.filter }
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(AppSpace.md))

                    // ── 书架网格 ──
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        contentPadding = PaddingValues(horizontal = AppSpace.lg),
                        horizontalArrangement = Arrangement.spacedBy(AppSpace.lg),
                        verticalArrangement = Arrangement.spacedBy(AppSpace.lg),
                        modifier = Modifier.weight(1f)
                    ) {
                        itemsIndexed(filteredBooks, key = { _, book -> book.id }) { index, book ->
                            PageEntranceItem(play = playEntranceAnimation, index = index + 2) {
                                AnimatedBookGridItem(
                                    book = book,
                                    isDeleting = book.id in deletingBookIds,
                                    isEditing = false,
                                    isSelected = false,
                                    contextMenuState = contextMenuState,
                                    onHaptic = { haptic.performHapticFeedback(HapticFeedbackType.LongPress) },
                                    onSelectionToggle = {},
                                    onClick = { onNavigateToReader(book.id, book.coverPath, book.title) }
                                )
                            }
                        }
                        // 添加按钮
                        if (!uiState.isLoading) {
                            item(key = "add_book") {
                                PageEntranceItem(
                                    play = playEntranceAnimation,
                                    index = filteredBooks.size + 2
                                ) {
                                    AddBookItem(onClick = { launcher.launch("*/*") })
                                }
                            }
                        }
                    }
                } // Column 结束
            } // OverscrollBounce 结束
        } // 内容层 Box 结束（renderEffect 模糊作用于此）

        // ── 长按上下文菜单覆盖层（在内容层之外，不被模糊） ──
        }

        if (isLiquidGlass) {
            val blurHeight = if (bookshelfHeaderHeightPx > 0) {
                with(density) { bookshelfHeaderHeightPx.toDp() } - 10.dp
            } else {
                154.dp
            }
            StatusGradientOverlay(
                backdrop = bookshelfBackdrop,
                exportedBackdrop = bookshelfTopBlurBackdrop,
                height = blurHeight,
                blurRadius = 38.dp,
                solidFraction = 0.68f
            )
            ProvideLiquidGlassBackdrop(bookshelfTopBlurBackdrop) {
                LiquidBookshelfHeader(
                    filterTabs = filterTabs,
                    selectedFilter = selectedFilter,
                    isEditing = isEditing,
                    selectedCount = selectedBookIds.size,
                    onFilterSelected = { selectedFilter = it },
                    onEditToggle = {
                        isEditing = !isEditing
                        if (!isEditing) selectedBookIds = emptySet()
                    },
                    onDeleteSelected = { showBatchDeleteConfirm = true },
                    onSearchClick = {
                        isEditing = false
                        selectedBookIds = emptySet()
                        expandedSearchBookId = null
                        isSearchActive = true
                    },
                    onSearchBoundsChanged = { searchLauncherBounds = it },
                    modifier = Modifier
                        .zIndex(2f)
                        .onGloballyPositioned { coordinates ->
                            bookshelfHeaderHeightPx = coordinates.size.height
                        }
                        .graphicsLayer {
                            renderEffect = if (
                                overlayProgress > 0.01f && android.os.Build.VERSION.SDK_INT >= 31
                            ) {
                                android.graphics.RenderEffect.createBlurEffect(
                                    maxOf(
                                        20f * contextMenuState.scrimAlpha.value,
                                        34.dp.toPx() * searchBlurProgress
                                    ),
                                    maxOf(
                                        20f * contextMenuState.scrimAlpha.value,
                                        34.dp.toPx() * searchBlurProgress
                                    ),
                                    android.graphics.Shader.TileMode.CLAMP
                                ).asComposeRenderEffect()
                            } else {
                                null
                            }
                        }
                )
            }
        }

        ProvideLiquidGlassBackdrop(bookshelfBackdrop.takeIf { isLiquidGlass }) {
            BookshelfSearchOverlay(
                visible = isSearchActive,
                query = searchQuery,
                books = uiState.books,
                tagNamesByBook = tagNamesByBook,
                expandedBookId = expandedSearchBookId,
                deletingBookIds = deletingBookIds,
                onQueryChange = { searchQuery = it },
                onDismiss = {
                    isSearchActive = false
                    expandedSearchBookId = null
                },
                onExpandedBookChange = { expandedSearchBookId = it },
                onBookClick = { book ->
                    onNavigateToReader(book.id, book.coverPath, book.title)
                },
                onEditInfo = { book ->
                    expandedSearchBookId = null
                    editingBook = book
                    showEditDialog = true
                },
                onDelete = { book ->
                    expandedSearchBookId = null
                    booksPendingDeletion = listOf(book)
                    deletingBookIds = setOf(book.id)
                },
                onFavorite = { book ->
                    viewModel.updateBook(book.copy(isFavorite = !book.isFavorite))
                },
                onCustomCover = { book ->
                    expandedSearchBookId = null
                    coverTargetBook = book
                    coverPickerLauncher.launch("image/*")
                },
                onRemoveCustomCover = { book ->
                    expandedSearchBookId = null
                    FileUtils.deleteCustomCover(context, book.id)
                    viewModel.reExtractCover(context, book)
                },
                onTags = { book ->
                    expandedSearchBookId = null
                    tagTargetBook = book
                    showTagSheet = true
                },
                onBookmarksNotes = { book ->
                    expandedSearchBookId = null
                    val intent = android.content.Intent(context, BookNotesActivity::class.java)
                    intent.putExtra("bookId", book.id)
                    context.startActivity(intent)
                },
                launcherBounds = searchLauncherBounds,
                modifier = Modifier.zIndex(2.6f)
            )
        }

        BookContextMenuOverlay(
            state = contextMenuState,
            onDelete = { book ->
                booksPendingDeletion = listOf(book)
                deletingBookIds = setOf(book.id)
            },
            onFavorite = { book -> viewModel.updateBook(book.copy(isFavorite = !book.isFavorite)) },
            onCustomCover = { book ->
                coverTargetBook = book
                coverPickerLauncher.launch("image/*")
            },
            onRemoveCustomCover = { book ->
                FileUtils.deleteCustomCover(context, book.id)
                viewModel.reExtractCover(context, book)
            },
            onBookmarksNotes = { book ->
                val intent = android.content.Intent(context, BookNotesActivity::class.java)
                intent.putExtra("bookId", book.id)
                context.startActivity(intent)
            },
            onTags = { book ->
                tagTargetBook = book
                showTagSheet = true
            },
            onEditInfo = { book ->
                editingBook = book
                showEditDialog = true
            }
        )

        // ── 编辑书本信息对话框（卡片风格） ──
        if (showEditDialog && editingBook != null) {
            com.huangder.lumibooks.ui.components.LiquidGlassDialog(
                onDismissRequest = { showEditDialog = false },
                modifier = Modifier.imePadding(),
                backgroundScrimColor = Color.Transparent,
                backgroundBlurRadius = 18.dp,
                properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
            ) {
                com.huangder.lumibooks.ui.components.EditInputDialog(
                            title = stringResource(R.string.edit_book_info),
                            fields = listOf(
                                Triple(stringResource(R.string.book_title_label), "显示原始书名", editingBook!!.title),
                                Triple(stringResource(R.string.book_author_label), "显示原始作者", editingBook!!.author)
                            ),
                            onBack = { showEditDialog = false },
                            onConfirm = { values ->
                                viewModel.updateBook(editingBook!!.copy(title = values[0], author = values[1]))
                                showEditDialog = false
                            }
                        )
            }
        }

        if (showTagSheet && tagTargetBook != null) {
            val targetBook = tagTargetBook!!
            BookTagBottomSheet(
                tags = uiState.tags,
                selectedTagIds = tagIdsByBook[targetBook.id].orEmpty(),
                onDismiss = {
                    showTagSheet = false
                    tagTargetBook = null
                },
                onTagCheckedChange = { tag, isChecked ->
                    viewModel.setBookTag(targetBook.id, tag.id, isChecked)
                },
                onCreateTag = { name ->
                    viewModel.createAndAssignTag(targetBook.id, name)
                },
                onDeleteTag = { tag ->
                    viewModel.deleteTag(tag.id)
                }
            )
        }

        if (showBatchDeleteConfirm) {
            LiquidGlassAlertDialog(
                onDismissRequest = { showBatchDeleteConfirm = false },
                title = {
                    Text(
                        text = stringResource(R.string.delete_selected_books_title),
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Text(
                        text = stringResource(
                            R.string.delete_selected_books_confirm,
                            selectedBookIds.size
                        )
                    )
                },
                confirmButton = {
                    LiquidGlassTextButton(
                        text = stringResource(R.string.delete),
                        tintedColor = Color(0xFFD92D3A),
                        onClick = {
                            val selectedBooks = uiState.books.filter { it.id in selectedBookIds }
                            showBatchDeleteConfirm = false
                            booksPendingDeletion = selectedBooks
                            deletingBookIds = selectedBooks.mapTo(mutableSetOf()) { it.id }
                        }
                    )
                },
                dismissButton = {
                    LiquidGlassTextButton(
                        text = stringResource(R.string.cancel),
                        onClick = { showBatchDeleteConfirm = false },
                        contentColor = AppColors.TextSecondary
                    )
                }
            )
        }

    }
    }
}

@Composable
private fun LiquidBookshelfHeader(
    filterTabs: List<BookshelfFilterTab>,
    selectedFilter: BookshelfFilter,
    isEditing: Boolean,
    selectedCount: Int,
    onFilterSelected: (BookshelfFilter) -> Unit,
    onEditToggle: () -> Unit,
    onDeleteSelected: () -> Unit,
    onSearchClick: () -> Unit,
    onSearchBoundsChanged: (Rect) -> Unit,
    modifier: Modifier = Modifier
) {
    val menuHost = LocalLiquidGlassMenuHost.current
    var filterAnchorBounds by remember { mutableStateOf(Rect.Zero) }
    var filterExpanded by remember { mutableStateOf(false) }
    val arrowRotation by animateFloatAsState(
        targetValue = if (filterExpanded) 180f else 0f,
        animationSpec = spring(dampingRatio = 0.78f, stiffness = 420f),
        label = "bookshelfFilterArrow"
    )
    val selectedLabel = filterTabs.firstOrNull { it.filter == selectedFilter }?.label
        ?: stringResource(R.string.filter_all)

    DisposableEffect(menuHost) {
        onDispose {
            if (filterExpanded) menuHost?.dismiss()
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(top = 12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AppSpace.lg),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LiquidGlassButton(
                onClick = onEditToggle,
                prominentShadow = true,
                modifier = Modifier
                    .width(88.dp)
                    .height(46.dp)
            ) {
                Text(
                    text = stringResource(if (isEditing) R.string.done else R.string.edit),
                    color = AppColors.TextPrimary,
                    fontSize = AppType.BodySmall,
                    fontWeight = FontWeight.SemiBold
                )
            }

            AnimatedVisibility(
                visible = selectedCount > 0,
                enter = fadeIn(tween(120)) + scaleIn(
                    initialScale = 0.78f,
                    animationSpec = spring(dampingRatio = 0.68f, stiffness = 360f)
                ),
                exit = fadeOut(tween(110)) + scaleOut(targetScale = 0.82f)
            ) {
                Row {
                    Spacer(Modifier.width(10.dp))
                    LiquidGlassIconButton(
                        imageVector = Icons.Outlined.Delete,
                        contentDescription = stringResource(R.string.delete),
                        onClick = onDeleteSelected,
                        size = 46.dp,
                        iconSize = 21.dp,
                        contentColor = Color.White,
                        liquidContainerColor = Color(0xFFD92D3A),
                        liquidScrimColor = Color(0xB8D92D3A)
                    )
                }
            }

            Spacer(Modifier.weight(1f))

            LiquidGlassButton(
                onClick = {
                    if (filterExpanded) {
                        menuHost?.dismiss()
                    } else if (menuHost != null && filterAnchorBounds != Rect.Zero) {
                        filterExpanded = true
                        menuHost.show(
                            LiquidGlassMenuSpec(
                                anchorBounds = filterAnchorBounds,
                                width = 176.dp,
                                maxVisibleItems = 8,
                                onDismiss = { filterExpanded = false },
                                items = filterTabs.map { tab ->
                                    LiquidGlassMenuItem(
                                        label = tab.label,
                                        selected = tab.filter == selectedFilter,
                                        onClick = { onFilterSelected(tab.filter) }
                                    )
                                }
                            )
                        )
                    }
                },
                prominentShadow = true,
                modifier = Modifier
                    .width(136.dp)
                    .height(46.dp)
                    .onGloballyPositioned { filterAnchorBounds = it.boundsInRoot() }
            ) {
                Text(
                    text = selectedLabel,
                    color = AppColors.TextPrimary,
                    fontSize = AppType.BodySmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Spacer(Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.Outlined.KeyboardArrowDown,
                    contentDescription = null,
                    tint = AppColors.TextPrimary,
                    modifier = Modifier
                        .size(19.dp)
                        .graphicsLayer { rotationZ = arrowRotation }
                )
            }
        }

        Text(
            text = stringResource(R.string.bookshelf_title),
            fontSize = AppType.Display,
            fontWeight = FontWeight.Bold,
            fontFamily = KaiTi,
            letterSpacing = (-0.02).sp,
            color = AppColors.TextPrimary,
            modifier = Modifier.padding(
                start = AppSpace.lg,
                top = 14.dp,
                bottom = 10.dp
            )
        )

        BookshelfSearchLauncher(
            onClick = onSearchClick,
            onBoundsChanged = onSearchBoundsChanged,
            modifier = Modifier.padding(
                start = AppSpace.lg,
                end = AppSpace.lg,
                bottom = 14.dp
            )
        )
    }
}

private sealed interface BookshelfFilter {
    data object All : BookshelfFilter
    data object Downloaded : BookshelfFilter
    data object Pdf : BookshelfFilter
    data object Txt : BookshelfFilter
    data object Favorites : BookshelfFilter
    data class Tag(val tagId: String) : BookshelfFilter
}

private data class BookshelfFilterTab(
    val filter: BookshelfFilter,
    val label: String
)

// ─── 带删除动画的书籍网格项 ──────────────────────────────────────

@Composable
private fun AnimatedBookGridItem(
    book: Book,
    isDeleting: Boolean,
    isEditing: Boolean,
    isSelected: Boolean,
    contextMenuState: BookContextMenuState,
    onHaptic: () -> Unit,
    onSelectionToggle: () -> Unit,
    onClick: () -> Unit
) {
    val scale by animateFloatAsState(
        targetValue = when {
            isDeleting -> 0.8f
            isEditing -> 0.955f
            else -> 1f
        },
        animationSpec = if (isDeleting) {
            tween(300, easing = AppEasing.Accelerate)
        } else {
            spring(dampingRatio = 0.78f, stiffness = 360f)
        },
        label = "deleteScale"
    )
    val alphaAnim by animateFloatAsState(
        targetValue = if (isDeleting) 0f else 1f,
        animationSpec = tween(300, easing = AppEasing.Accelerate),
        label = "deleteAlpha"
    )

    // 等动画完成后执行实际删除（delay 期间动画持续播放）
    // 在组合期间读取值 → graphicsLayer 拿到最新值
    Box(
        modifier = Modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                alpha = alphaAnim
            }
    ) {
        BookGridItem(
            book = book,
            isEditing = isEditing,
            isSelected = isSelected,
            contextMenuState = contextMenuState,
            onHaptic = onHaptic,
            onSelectionToggle = onSelectionToggle,
            onClick = onClick
        )
    }
}

// ─── 书籍网格项 ────────────────────────────────────────────────

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun BookGridItem(
    book: Book,
    isEditing: Boolean,
    isSelected: Boolean,
    contextMenuState: BookContextMenuState,
    onHaptic: () -> Unit,
    onSelectionToggle: () -> Unit,
    onClick: () -> Unit
) {
    val coverCorner = if (LocalAppTheme.current == "liquid_glass") 16.dp else AppRadius.sm
    // 是否为当前操作的目标书本（在组合期间读取，确保触发重组）
    val isTarget = contextMenuState.selectedBook?.id == book.id
    val isOverlayActive = contextMenuState.phase != ContextMenuPhase.Idle && isTarget
    // 在组合期间读取 pressScale → 值变化时触发重组 → graphicsLayer 拿到最新值
    val pressScaleValue = if (isTarget) contextMenuState.pressScale.value else 1f
    val overlayAlpha = if (isOverlayActive) contextMenuState.itemAlpha.value else 1f

    // 只保存坐标引用，避免滚动时把每帧变化的窗口坐标写入 Compose State。
    val coverCoordinates = remember {
        arrayOfNulls<androidx.compose.ui.layout.LayoutCoordinates>(1)
    }

    // 监听 combinedClickable 的按下/抬起事件，驱动封面缩小动画
    val interactionSource = remember { MutableInteractionSource() }
    var isPressed by remember { mutableStateOf(false) }
    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is androidx.compose.foundation.interaction.PressInteraction.Press -> isPressed = true
                is androidx.compose.foundation.interaction.PressInteraction.Release -> isPressed = false
                is androidx.compose.foundation.interaction.PressInteraction.Cancel -> isPressed = false
            }
        }
    }
    // 按下时缩小到 0.95，否则用 contextMenuState 的 pressScale
    val coverScale = if (isPressed && !isEditing) 0.95f else pressScaleValue
    val selectionAlpha by animateFloatAsState(
        targetValue = if (isEditing && isSelected) 1f else 0f,
        animationSpec = tween(160),
        label = "bookSelectionOutline"
    )
    val selectionColor = AppColors.Accent

    Column(
        modifier = Modifier
            .graphicsLayer { alpha = overlayAlpha }
            .drawWithContent {
                drawContent()
                if (selectionAlpha > 0.001f) {
                    val gap = 7.dp.toPx()
                    drawRoundRect(
                        color = selectionColor.copy(alpha = selectionAlpha),
                        topLeft = Offset(-gap, -gap),
                        size = Size(
                            width = size.width + gap * 2f,
                            height = size.height + gap * 2f
                        ),
                        cornerRadius = CornerRadius(22.dp.toPx()),
                        style = Stroke(width = 2.dp.toPx())
                    )
                }
            }
            .combinedClickable(
                indication = null,
                interactionSource = interactionSource,
                onClick = if (isEditing) onSelectionToggle else onClick,
                onLongClick = if (isEditing) null else {
                    {
                        onHaptic()
                        contextMenuState.onLongPressConfirmed(
                            book = book,
                            bounds = coverCoordinates[0]?.boundsInRoot()
                                ?: androidx.compose.ui.geometry.Rect.Zero,
                            onHaptic = onHaptic
                        )
                    }
                }
            )
    ) {
        // 封面（3:4 比例）— 按下缩小 + overlay 状态控制
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.75f)
                .onGloballyPositioned { coordinates ->
                    coverCoordinates[0] = coordinates
                }
                .graphicsLayer {
                    scaleX = coverScale
                    scaleY = coverScale
                }
                .shadow(12.dp, RoundedCornerShape(coverCorner), ambientColor = Color(0x06000000), spotColor = Color(0x06000000))
                .clip(RoundedCornerShape(coverCorner))
                .background(AppColors.BgGray)
        ) {
            if (book.coverPath != null) {
                val imgContext = LocalContext.current
                AsyncImage(
                    model = ImageRequest.Builder(imgContext)
                        .data(book.coverPath)
                        .memoryCacheKey("${book.id}_${book.coverPath}") // book.id 区分同名书，coverPath 变化时刷新
                        .build(),
                    contentDescription = book.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                // 无封面：灰色背景 + 书名
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(AppColors.BgGray),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = book.title.take(8),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = AppColors.TextSecondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(8.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
            // 进度指示
            if (book.readingProgress > 0f) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.Black.copy(alpha = 0.6f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "${(book.readingProgress * 100).toInt()}%",
                        fontSize = 10.sp,
                        color = Color.White
                    )
                }
                // 底部进度条
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(3.dp)
                        .background(Color.Black.copy(alpha = 0.15f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(book.readingProgress)
                            .height(3.dp)
                            .background(AppColors.Accent)
                    )
                }
            }
        }

        Spacer(Modifier.height(AppSpace.sm))

        // 书名（收藏时加心形）
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = book.title,
                fontSize = AppType.BodySmall,
                fontWeight = FontWeight.SemiBold,
                color = AppColors.TextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            if (book.isFavorite) {
                Spacer(Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.Filled.Favorite,
                    contentDescription = stringResource(R.string.favorite),
                    tint = AppColors.Accent,
                    modifier = Modifier.size(12.dp)
                )
            }
        }

        // 作者
        Text(
            text = book.author,
            fontSize = AppType.Caption,
            color = AppColors.TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// ─── 添加书籍 ──────────────────────────────────────────────────

@Composable
private fun AddBookItem(onClick: () -> Unit) {
    val coverCorner = if (LocalAppTheme.current == "liquid_glass") 16.dp else AppRadius.sm
    Column(
        modifier = Modifier.clickable(indication = null, interactionSource = remember { MutableInteractionSource() }, onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.75f)
                .clip(RoundedCornerShape(coverCorner))
                .background(AppColors.WindowBg),
            contentAlignment = Alignment.Center
        ) {
            // 虚线边框（用 Canvas 绘制）
            val dividerColor = AppColors.Divider
            Canvas(modifier = Modifier.fillMaxSize()) {
                val pw = 2.dp.toPx()
                val r = coverCorner.toPx()
                val dashW = 8.dp.toPx()
                val dashGap = 6.dp.toPx()
                drawRoundRect(
                    color = dividerColor,
                    cornerRadius = CornerRadius(r),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(
                        width = pw,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(dashW, dashGap))
                    )
                )
            }
            Icon(
                Icons.Default.Add,
                contentDescription = stringResource(R.string.import_books),
                tint = AppColors.TextSecondary,
                modifier = Modifier.size(32.dp)
            )
        }
        Spacer(Modifier.height(AppSpace.sm))
        Text(
            text = stringResource(R.string.import_book_label),
            fontSize = AppType.Caption,
            color = AppColors.TextSecondary
        )
    }
}
