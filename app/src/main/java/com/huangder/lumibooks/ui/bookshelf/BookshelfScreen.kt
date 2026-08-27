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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.itemsIndexed as lazyListItemsIndexed
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.DriveFileMove
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FormatListBulleted
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Label
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.ViewModule
import androidx.compose.material.icons.outlined.ViewList
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.zIndex
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.huangder.lumibooks.domain.model.Book
import com.huangder.lumibooks.domain.model.BookFormat
import com.huangder.lumibooks.domain.model.FolderMoveResult
import com.huangder.lumibooks.domain.model.LibraryFolder
import com.huangder.lumibooks.ui.animation.AppEasing
import com.huangder.lumibooks.ui.animation.OverscrollBounce
import com.huangder.lumibooks.ui.animation.PageEntranceItem
import com.huangder.lumibooks.ui.components.StatusGradientOverlay
import com.huangder.lumibooks.ui.components.ProvideLiquidGlassBackdrop
import com.huangder.lumibooks.ui.components.LocalLiquidGlassBackdrop
import com.huangder.lumibooks.ui.components.LiquidGlassAlertDialog
import com.huangder.lumibooks.ui.components.LiquidGlassButton
import com.huangder.lumibooks.ui.components.LiquidGlassIconButton
import com.huangder.lumibooks.ui.components.LiquidGlassMenuItem
import com.huangder.lumibooks.ui.components.LiquidGlassSurface
import com.huangder.lumibooks.ui.components.LiquidGlassMenuSpec
import com.huangder.lumibooks.ui.components.LiquidGlassTextButton
import com.huangder.lumibooks.ui.components.LocalLiquidGlassMenuHost
import com.huangder.lumibooks.ui.components.ConfigurableBackHandler
import com.huangder.lumibooks.ui.home.HomeViewModel
import com.huangder.lumibooks.ui.theme.AppColors
import com.huangder.lumibooks.ui.theme.AppRadius
import com.huangder.lumibooks.ui.theme.AppSpace
import com.huangder.lumibooks.ui.theme.AppType
import com.huangder.lumibooks.ui.theme.KaiTi
import com.huangder.lumibooks.ui.theme.LocalAppTheme
import com.huangder.lumibooks.ui.theme.LocalEInkMode
import com.huangder.lumibooks.ui.theme.LocalMotionEnabled
import com.huangder.lumibooks.ui.theme.LocalUseMaterial3Theme
import com.huangder.lumibooks.ui.theme.resolveAppFontFamily
import com.huangder.lumibooks.R
import androidx.compose.ui.res.stringResource
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun BookshelfScreen(
    playEntranceAnimation: Boolean = false,
    onNavigateToReader: (bookId: String, coverPath: String?, title: String, sourceBounds: Rect?) -> Unit,
    onAddBook: (folderId: String?) -> Unit,
    requestedFolderId: String? = null,
    onRequestedFolderConsumed: () -> Unit = {},
    onMessage: (String) -> Unit = {},
    onOverlayProgressChange: (Float) -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedFilter by remember { mutableStateOf<BookshelfFilter>(BookshelfFilter.All) }
    var currentFolderId by rememberSaveable { mutableStateOf<String?>(null) }
    var renderedFolderId by rememberSaveable { mutableStateOf<String?>(null) }
    var folderNavigationForward by remember { mutableStateOf(true) }
    val folderTransitionAlpha = remember { Animatable(1f) }
    val folderTransitionOffset = remember { Animatable(0f) }
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val contextMenuState = rememberBookContextMenuState()
    val folderContextMenuState = rememberFolderContextMenuState()
    val eInkMode = LocalEInkMode.current
    val isLiquidGlass = LocalAppTheme.current == "liquid_glass" && !eInkMode
    val isMaterial3 = LocalUseMaterial3Theme.current
    val collectionBottomPadding = if (isMaterial3) {
        WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 96.dp
    } else {
        24.dp
    }
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
    var expandedListBookId by remember { mutableStateOf<String?>(null) }
    var expandedListFolderId by remember { mutableStateOf<String?>(null) }
    var searchLauncherBounds by remember { mutableStateOf(Rect.Zero) }
    var showBatchTagSheet by remember { mutableStateOf(false) }
    var showBatchMoveSheet by remember { mutableStateOf(false) }
    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var renameFolder by remember { mutableStateOf<LibraryFolder?>(null) }
    var deleteFolder by remember { mutableStateOf<LibraryFolder?>(null) }
    var relocateFolder by remember { mutableStateOf<LibraryFolder?>(null) }
    var folderCoverTarget by remember { mutableStateOf<LibraryFolder?>(null) }
    val motionEnabled = LocalMotionEnabled.current
    val liquidCollectionTopPadding = if (bookshelfHeaderHeightPx > 0) {
        with(density) { bookshelfHeaderHeightPx.toDp() } + 12.dp
    } else if (isEditing) {
        116.dp
    } else {
        240.dp
    }

    val filterTabs = buildList {
        add(BookshelfFilterTab(BookshelfFilter.All, stringResource(R.string.filter_all)))
        add(BookshelfFilterTab(BookshelfFilter.EpubMobi, stringResource(R.string.filter_epub_mobi)))
        add(BookshelfFilterTab(BookshelfFilter.Pdf, stringResource(R.string.format_pdf)))
        add(BookshelfFilterTab(BookshelfFilter.Txt, stringResource(R.string.format_txt)))
        add(BookshelfFilterTab(BookshelfFilter.Favorites, stringResource(R.string.filter_favorites)))
        uiState.tags.forEach { tag ->
            add(BookshelfFilterTab(BookshelfFilter.Tag(tag.id), tag.name))
        }
    }

    LaunchedEffect(uiState.tags) {
        val activeTagFilter = selectedFilter as? BookshelfFilter.Tag
        if (activeTagFilter != null && uiState.tags.none { it.id == activeTagFilter.tagId }) {
            selectedFilter = BookshelfFilter.All
        }
    }

    LaunchedEffect(uiState.folderMessage) {
        uiState.folderMessage?.let { message ->
            onMessage(message)
            viewModel.clearFolderMessage()
        }
    }

    LaunchedEffect(requestedFolderId, uiState.folders) {
        val requested = requestedFolderId ?: return@LaunchedEffect
        if (uiState.folders.any { it.id == requested }) {
            folderNavigationForward = true
            currentFolderId = requested
        }
        onRequestedFolderConsumed()
    }

    LaunchedEffect(uiState.folders, currentFolderId) {
        if (currentFolderId != null && uiState.folders.none { it.id == currentFolderId }) {
            folderNavigationForward = false
            currentFolderId = null
        }
        if (expandedListFolderId != null && uiState.folders.none { it.id == expandedListFolderId }) {
            expandedListFolderId = null
        }
    }

    LaunchedEffect(currentFolderId, motionEnabled, eInkMode) {
        if (currentFolderId == renderedFolderId) return@LaunchedEffect
        if (eInkMode) {
            renderedFolderId = currentFolderId
            folderTransitionAlpha.snapTo(1f)
            folderTransitionOffset.snapTo(0f)
            return@LaunchedEffect
        }
        if (!motionEnabled) {
            folderTransitionOffset.snapTo(0f)
            folderTransitionAlpha.animateTo(0f, tween(70, easing = AppEasing.Standard))
            renderedFolderId = currentFolderId
            folderTransitionAlpha.snapTo(0f)
            folderTransitionAlpha.animateTo(1f, tween(90, easing = AppEasing.Standard))
            return@LaunchedEffect
        }
        val direction = if (folderNavigationForward) -1f else 1f
        coroutineScope {
            launch { folderTransitionAlpha.animateTo(0f, tween(160, easing = AppEasing.Accelerate)) }
            launch { folderTransitionOffset.animateTo(32f * direction, tween(180, easing = AppEasing.Accelerate)) }
        }
        renderedFolderId = currentFolderId
        folderTransitionAlpha.snapTo(0f)
        folderTransitionOffset.snapTo(-32f * direction)
        coroutineScope {
            launch { folderTransitionAlpha.animateTo(1f, tween(220, easing = AppEasing.Decelerate)) }
            launch { folderTransitionOffset.animateTo(0f, tween(240, easing = AppEasing.Smooth)) }
        }
    }

    LaunchedEffect(uiState.books) {
        val existingIds = uiState.books.mapTo(mutableSetOf()) { it.id }
        selectedBookIds = selectedBookIds.intersect(existingIds)
        if (expandedSearchBookId !in existingIds) expandedSearchBookId = null
        if (expandedListBookId !in existingIds) expandedListBookId = null
    }

    LaunchedEffect(deletingBookIds) {
        if (deletingBookIds.isNotEmpty()) {
            kotlinx.coroutines.delay(350)
            val deletesContextMenuTarget = contextMenuState.selectedBook?.id in deletingBookIds
            if (deletesContextMenuTarget && contextMenuState.phase != ContextMenuPhase.Idle) {
                snapshotFlow { contextMenuState.phase }
                    .first { it == ContextMenuPhase.Idle }
            }
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
    var coverSourceBook by remember { mutableStateOf<Book?>(null) }
    var pendingContextMenuAction by remember { mutableStateOf<PendingBookMenuAction?>(null) }
    var pendingFolderMenuAction by remember { mutableStateOf<PendingFolderMenuAction?>(null) }

    // 删除动画：记录正在删除的书本 ID
    var tagTargetBook by remember { mutableStateOf<Book?>(null) }
    var showTagSheet by remember { mutableStateOf(false) }

    // 图片选择器（自定义封面）
    val coverPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        val book = coverTargetBook
        coverTargetBook = null
        if (uri != null && book != null) {
            viewModel.updateCustomCover(book, uri)
        }
    }
    val folderCoverPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        val folder = folderCoverTarget
        folderCoverTarget = null
        if (uri != null && folder != null) {
            viewModel.updateFolderCover(folder, uri)
        }
    }

    val launchCoverPicker: (Book) -> Unit = { book ->
        coverTargetBook = book
        runCatching { coverPickerLauncher.launch("image/*") }
            .onFailure { error ->
                coverTargetBook = null
                onMessage(error.message ?: "Unable to open the image picker")
            }
    }
    val launchFolderCoverPicker: (LibraryFolder) -> Unit = { folder ->
        folderCoverTarget = folder
        runCatching { folderCoverPickerLauncher.launch("image/*") }
            .onFailure { error ->
                folderCoverTarget = null
                onMessage(error.message ?: "Unable to open the image picker")
            }
    }

    val openBookNotes: (Book) -> Unit = { book ->
        runCatching {
            val intent = android.content.Intent(context, BookNotesActivity::class.java)
                .putExtra("bookId", book.id)
            context.startActivity(intent)
        }.onFailure { error ->
            onMessage(error.message ?: "Unable to open bookmarks and notes")
        }
    }

    LaunchedEffect(pendingContextMenuAction) {
        val pendingAction = pendingContextMenuAction ?: return@LaunchedEffect
        if (contextMenuState.phase != ContextMenuPhase.Idle) {
            snapshotFlow { contextMenuState.phase }
                .first { it == ContextMenuPhase.Idle }
        }

        val currentBook = uiState.books.firstOrNull { it.id == pendingAction.book.id }
        if (currentBook != null) {
            when (pendingAction.type) {
                PendingBookMenuActionType.Favorite ->
                    viewModel.updateBook(currentBook.copy(isFavorite = !currentBook.isFavorite))
                PendingBookMenuActionType.CustomCover -> coverSourceBook = currentBook
                PendingBookMenuActionType.RemoveCustomCover -> viewModel.removeCustomCover(currentBook)
                PendingBookMenuActionType.BookmarksNotes -> openBookNotes(currentBook)
                PendingBookMenuActionType.Tags -> {
                    tagTargetBook = currentBook
                    showTagSheet = true
                }
                PendingBookMenuActionType.EditInfo -> {
                    editingBook = currentBook
                    showEditDialog = true
                }
            }
        }
        pendingContextMenuAction = null
    }

    LaunchedEffect(pendingFolderMenuAction) {
        val pendingAction = pendingFolderMenuAction ?: return@LaunchedEffect
        if (folderContextMenuState.phase != ContextMenuPhase.Idle) {
            snapshotFlow { folderContextMenuState.phase }
                .first { it == ContextMenuPhase.Idle }
        }
        val currentFolder = uiState.folders.firstOrNull { it.id == pendingAction.folder.id }
        if (currentFolder != null) {
            when (pendingAction.type) {
                PendingFolderMenuActionType.Rename -> renameFolder = currentFolder
                PendingFolderMenuActionType.SetCover -> launchFolderCoverPicker(currentFolder)
                PendingFolderMenuActionType.RemoveCover -> viewModel.removeFolderCover(currentFolder)
                PendingFolderMenuActionType.Move -> relocateFolder = currentFolder
                PendingFolderMenuActionType.Delete -> deleteFolder = currentFolder
            }
        }
        pendingFolderMenuAction = null
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
    val currentPath = remember(uiState.folders, renderedFolderId) {
        folderPath(uiState.folders, renderedFolderId)
    }
    val visibleFolders = remember(uiState.folders, renderedFolderId) {
        directChildFolders(uiState.folders, renderedFolderId)
    }
    val currentLevelBooks = remember(uiState.books, uiState.bookFolderLinks, renderedFolderId) {
        booksAtFolderLevel(uiState.books, uiState.bookFolderLinks, renderedFolderId)
    }
    val folderCounts = remember(uiState.folders, uiState.bookFolderLinks) {
        folderBookCounts(uiState.folders, uiState.bookFolderLinks)
    }
    val filteredBooks = when (val filter = selectedFilter) {
        BookshelfFilter.All -> currentLevelBooks
        BookshelfFilter.EpubMobi -> currentLevelBooks.filter(Book::isEpubMobi)
        BookshelfFilter.Pdf -> currentLevelBooks.filter { it.format == BookFormat.PDF }
        BookshelfFilter.Txt -> currentLevelBooks.filter { it.format == BookFormat.TXT }
        BookshelfFilter.Favorites -> currentLevelBooks.filter { it.isFavorite }
        is BookshelfFilter.Tag -> currentLevelBooks.filter { book ->
            filter.tagId in tagIdsByBook[book.id].orEmpty()
        }
    }

    val openFolder: (LibraryFolder) -> Unit = { folder ->
        isEditing = false
        selectedBookIds = emptySet()
        expandedListBookId = null
        expandedListFolderId = null
        folderNavigationForward = true
        currentFolderId = folder.id
    }
    val navigateToFolder: (String?) -> Unit = { folderId ->
        isEditing = false
        selectedBookIds = emptySet()
        expandedListBookId = null
        expandedListFolderId = null
        folderNavigationForward = folderPath(uiState.folders, folderId).size >
            folderPath(uiState.folders, currentFolderId).size
        currentFolderId = folderId
    }

    ConfigurableBackHandler(enabled = currentFolderId != null || isEditing) {
        if (isEditing) {
            isEditing = false
            selectedBookIds = emptySet()
        } else {
            val parentId = folderPath(uiState.folders, currentFolderId).lastOrNull()?.parentId
            folderNavigationForward = false
            currentFolderId = parentId
        }
    }

    LaunchedEffect(uiState.bookshelfLayoutMode, selectedFilter) {
        expandedListBookId = null
        expandedListFolderId = null
        if (contextMenuState.phase != ContextMenuPhase.Idle) contextMenuState.dismiss()
        if (folderContextMenuState.phase != ContextMenuPhase.Idle) folderContextMenuState.dismiss()
    }

    val toggleBookSelection: (Book) -> Unit = { book ->
        selectedBookIds = if (book.id in selectedBookIds) {
            selectedBookIds - book.id
        } else {
            selectedBookIds + book.id
        }
    }
    val toggleSelectAll: () -> Unit = {
        selectedBookIds = if (filteredBooks.all { it.id in selectedBookIds }) {
            emptySet()
        } else {
            filteredBooks.mapTo(linkedSetOf()) { it.id }
        }
    }
    val editBookFromList: (Book) -> Unit = { book ->
        expandedListBookId = null
        editingBook = book
        showEditDialog = true
    }
    val deleteBookFromList: (Book) -> Unit = { book ->
        expandedListBookId = null
        booksPendingDeletion = listOf(book)
        deletingBookIds = setOf(book.id)
    }
    val chooseCoverFromList: (Book) -> Unit = { book ->
        expandedListBookId = null
        launchCoverPicker(book)
    }
    val removeCoverFromList: (Book) -> Unit = { book ->
        expandedListBookId = null
        viewModel.removeCustomCover(book)
    }
    val editTagsFromList: (Book) -> Unit = { book ->
        expandedListBookId = null
        tagTargetBook = book
        showTagSheet = true
    }
    val openNotesFromList: (Book) -> Unit = { book ->
        expandedListBookId = null
        openBookNotes(book)
    }
    val renameFolderFromList: (LibraryFolder) -> Unit = { folder ->
        expandedListFolderId = null
        renameFolder = folder
    }
    val deleteFolderFromList: (LibraryFolder) -> Unit = { folder ->
        expandedListFolderId = null
        deleteFolder = folder
    }
    val setFolderCoverFromList: (LibraryFolder) -> Unit = { folder ->
        expandedListFolderId = null
        launchFolderCoverPicker(folder)
    }
    val removeFolderCoverFromList: (LibraryFolder) -> Unit = { folder ->
        expandedListFolderId = null
        viewModel.removeFolderCover(folder)
    }
    val moveFolderFromList: (LibraryFolder) -> Unit = { folder ->
        expandedListFolderId = null
        relocateFolder = folder
    }

    val searchBlurProgress by animateFloatAsState(
        targetValue = if (isSearchActive && !eInkMode) 1f else 0f,
        animationSpec = if (eInkMode) snap() else tween(if (isSearchActive) 210 else 230),
        label = "bookshelfSearchBlur"
    )
    val overlayProgress = if (eInkMode) {
        0f
    } else {
        maxOf(
            contextMenuState.scrimAlpha.value,
            folderContextMenuState.scrimAlpha.value,
            searchBlurProgress
        )
    }
    val contentBlurRadius = if (eInkMode) {
        0f
    } else {
        maxOf(
            20f * contextMenuState.scrimAlpha.value,
            20f * folderContextMenuState.scrimAlpha.value,
            with(density) { 34.dp.toPx() } * searchBlurProgress
        )
    }
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
                        contentBlurRadius > 0.01f && android.os.Build.VERSION.SDK_INT >= 31
                    ) {
                        android.graphics.RenderEffect.createBlurEffect(
                            contentBlurRadius,
                            contentBlurRadius,
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
                    BookshelfCollection(
                        layoutMode = uiState.bookshelfLayoutMode,
                        books = filteredBooks,
                        folders = visibleFolders,
                        folderBookCounts = folderCounts,
                        tagNamesByBook = tagNamesByBook,
                        isLoading = uiState.isLoading,
                        playEntranceAnimation = playEntranceAnimation,
                        deletingBookIds = deletingBookIds,
                        isEditing = isEditing,
                        selectedBookIds = selectedBookIds,
                        contextMenuState = contextMenuState,
                        folderContextMenuState = folderContextMenuState,
                        syncedBookIds = uiState.syncedBookIds,
                        expandedListBookId = expandedListBookId,
                        expandedListFolderId = expandedListFolderId,
                        topPadding = liquidCollectionTopPadding,
                        bottomPadding = 120.dp,
                        onHaptic = { haptic.performHapticFeedback(HapticFeedbackType.LongPress) },
                        onSelectionToggle = toggleBookSelection,
                        onExpandedBookChange = {
                            expandedListFolderId = null
                            expandedListBookId = it
                        },
                        onExpandedFolderChange = {
                            expandedListBookId = null
                            expandedListFolderId = it
                        },
                        onBookClick = { book, bounds -> onNavigateToReader(book.id, book.coverPath, book.title, bounds) },
                        onFolderClick = openFolder,
                        onFolderRename = renameFolderFromList,
                        onFolderDelete = deleteFolderFromList,
                        onFolderSetCover = setFolderCoverFromList,
                        onFolderRemoveCover = removeFolderCoverFromList,
                        onFolderMove = moveFolderFromList,
                        onAddBook = { onAddBook(renderedFolderId) },
                        onEditInfo = editBookFromList,
                        onDelete = deleteBookFromList,
                        onFavorite = { book -> viewModel.updateBook(book.copy(isFavorite = !book.isFavorite)) },
                        onCustomCover = chooseCoverFromList,
                        onRemoveCustomCover = removeCoverFromList,
                        onTags = editTagsFromList,
                        onBookmarksNotes = openNotesFromList,
                        modifier = Modifier
                            .widthIn(max = 1000.dp)
                            .fillMaxWidth()
                            .graphicsLayer {
                                alpha = folderTransitionAlpha.value
                                translationY = folderTransitionOffset.value.dp.toPx()
                            }
                            .align(Alignment.TopCenter)
                    )
                }
            } else {
            OverscrollBounce(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .widthIn(max = 1000.dp)
                        .fillMaxSize()
                        .align(Alignment.TopCenter)
                ) {
                    BookshelfCapsuleHeader(
                        filterTabs = filterTabs,
                        selectedFilter = selectedFilter,
                        isEditing = isEditing,
                        selectedCount = selectedBookIds.size,
                        onFilterSelected = { selectedFilter = it },
                        onEditToggle = {
                            isEditing = !isEditing
                            expandedListBookId = null
                            expandedListFolderId = null
                            if (!isEditing) selectedBookIds = emptySet()
                        },
                        onDeleteSelected = { showBatchDeleteConfirm = true },
                        onTagSelected = { showBatchTagSheet = true },
                        onMoveSelected = { showBatchMoveSheet = true },
                        folderPath = currentPath,
                        onNavigateToFolder = navigateToFolder,
                        onCreateFolder = { showCreateFolderDialog = true },
                        onBrowseFilters = {
                            context.startActivity(
                                android.content.Intent(context, BookshelfCategoriesActivity::class.java)
                            )
                        },
                        onSearchClick = {
                            isEditing = false
                            selectedBookIds = emptySet()
                            expandedSearchBookId = null
                            isSearchActive = true
                        },
                        onSyncClick = { viewModel.syncWebdavNow() },
                        layoutMode = uiState.bookshelfLayoutMode,
                        isWebdavSyncing = uiState.isWebdavSyncing,
                        onSelectAll = toggleSelectAll,
                        onLayoutModeChange = viewModel::setBookshelfLayoutMode,
                        onSearchBoundsChanged = { searchLauncherBounds = it }
                    )

                    // ── 书架网格 ──
                    BookshelfCollection(
                        layoutMode = uiState.bookshelfLayoutMode,
                        books = filteredBooks,
                        folders = visibleFolders,
                        folderBookCounts = folderCounts,
                        tagNamesByBook = tagNamesByBook,
                        isLoading = uiState.isLoading,
                        playEntranceAnimation = playEntranceAnimation,
                        deletingBookIds = deletingBookIds,
                        isEditing = isEditing,
                        selectedBookIds = selectedBookIds,
                        contextMenuState = contextMenuState,
                        folderContextMenuState = folderContextMenuState,
                        syncedBookIds = uiState.syncedBookIds,
                        expandedListBookId = expandedListBookId,
                        expandedListFolderId = expandedListFolderId,
                        topPadding = if (isEditing) 12.dp else 0.dp,
                        bottomPadding = collectionBottomPadding,
                        onHaptic = { haptic.performHapticFeedback(HapticFeedbackType.LongPress) },
                        onSelectionToggle = toggleBookSelection,
                        onExpandedBookChange = {
                            expandedListFolderId = null
                            expandedListBookId = it
                        },
                        onExpandedFolderChange = {
                            expandedListBookId = null
                            expandedListFolderId = it
                        },
                        onBookClick = { book, bounds -> onNavigateToReader(book.id, book.coverPath, book.title, bounds) },
                        onFolderClick = openFolder,
                        onFolderRename = renameFolderFromList,
                        onFolderDelete = deleteFolderFromList,
                        onFolderSetCover = setFolderCoverFromList,
                        onFolderRemoveCover = removeFolderCoverFromList,
                        onFolderMove = moveFolderFromList,
                        onAddBook = { onAddBook(renderedFolderId) },
                        onEditInfo = editBookFromList,
                        onDelete = deleteBookFromList,
                        onFavorite = { book -> viewModel.updateBook(book.copy(isFavorite = !book.isFavorite)) },
                        onCustomCover = chooseCoverFromList,
                        onRemoveCustomCover = removeCoverFromList,
                        onTags = editTagsFromList,
                        onBookmarksNotes = openNotesFromList,
                        modifier = Modifier
                            .weight(1f)
                            .graphicsLayer {
                                alpha = folderTransitionAlpha.value
                                translationY = folderTransitionOffset.value.dp.toPx()
                            }
                    )
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
                BookshelfCapsuleHeader(
                    filterTabs = filterTabs,
                    selectedFilter = selectedFilter,
                    isEditing = isEditing,
                    selectedCount = selectedBookIds.size,
                    onFilterSelected = { selectedFilter = it },
                    onEditToggle = {
                        isEditing = !isEditing
                        expandedListBookId = null
                        expandedListFolderId = null
                        if (!isEditing) selectedBookIds = emptySet()
                    },
                    onDeleteSelected = { showBatchDeleteConfirm = true },
                    onTagSelected = { showBatchTagSheet = true },
                    onMoveSelected = { showBatchMoveSheet = true },
                    folderPath = currentPath,
                    onNavigateToFolder = navigateToFolder,
                    onCreateFolder = { showCreateFolderDialog = true },
                    onBrowseFilters = {
                        context.startActivity(
                            android.content.Intent(context, BookshelfCategoriesActivity::class.java)
                        )
                    },
                    onSearchClick = {
                        isEditing = false
                        selectedBookIds = emptySet()
                        expandedSearchBookId = null
                        isSearchActive = true
                    },
                    onSyncClick = { viewModel.syncWebdavNow() },
                    layoutMode = uiState.bookshelfLayoutMode,
                    isWebdavSyncing = uiState.isWebdavSyncing,
                    onSelectAll = toggleSelectAll,
                    onLayoutModeChange = viewModel::setBookshelfLayoutMode,
                    onSearchBoundsChanged = { searchLauncherBounds = it },
                    modifier = Modifier
                        .zIndex(2f)
                        .onGloballyPositioned { coordinates ->
                            bookshelfHeaderHeightPx = coordinates.size.height
                        }
                        .graphicsLayer {
                            renderEffect = if (
                                contentBlurRadius > 0.01f && android.os.Build.VERSION.SDK_INT >= 31
                            ) {
                                android.graphics.RenderEffect.createBlurEffect(
                                    contentBlurRadius,
                                    contentBlurRadius,
                                    android.graphics.Shader.TileMode.CLAMP
                                ).asComposeRenderEffect()
                            } else {
                                null
                            }
                        }
                        .align(Alignment.TopCenter)
                        .widthIn(max = 1000.dp)
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
                syncedBookIds = uiState.syncedBookIds,
                onQueryChange = { searchQuery = it },
                onDismiss = {
                    isSearchActive = false
                    expandedSearchBookId = null
                },
                onExpandedBookChange = { expandedSearchBookId = it },
                onBookClick = { book ->
                    onNavigateToReader(book.id, book.coverPath, book.title, null)
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
                    launchCoverPicker(book)
                },
                onRemoveCustomCover = { book ->
                    expandedSearchBookId = null
                    viewModel.removeCustomCover(book)
                },
                onTags = { book ->
                    expandedSearchBookId = null
                    tagTargetBook = book
                    showTagSheet = true
                },
                onBookmarksNotes = { book ->
                    expandedSearchBookId = null
                    openBookNotes(book)
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
            onFavorite = { book ->
                pendingContextMenuAction = PendingBookMenuAction(PendingBookMenuActionType.Favorite, book)
            },
            onCustomCover = { book ->
                pendingContextMenuAction = PendingBookMenuAction(PendingBookMenuActionType.CustomCover, book)
            },
            onRemoveCustomCover = { book ->
                pendingContextMenuAction = PendingBookMenuAction(PendingBookMenuActionType.RemoveCustomCover, book)
            },
            onBookmarksNotes = { book ->
                pendingContextMenuAction = PendingBookMenuAction(PendingBookMenuActionType.BookmarksNotes, book)
            },
            onTags = { book ->
                pendingContextMenuAction = PendingBookMenuAction(PendingBookMenuActionType.Tags, book)
            },
            onEditInfo = { book ->
                pendingContextMenuAction = PendingBookMenuAction(PendingBookMenuActionType.EditInfo, book)
            }
        )

        FolderContextMenuOverlay(
            state = folderContextMenuState,
            bookCount = folderContextMenuState.selectedFolder
                ?.let { folderCounts[it.id] }
                ?: 0,
            onRename = { folder ->
                pendingFolderMenuAction = PendingFolderMenuAction(
                    PendingFolderMenuActionType.Rename,
                    folder
                )
            },
            onDelete = { folder ->
                pendingFolderMenuAction = PendingFolderMenuAction(
                    PendingFolderMenuActionType.Delete,
                    folder
                )
            },
            onSetCover = { folder ->
                pendingFolderMenuAction = PendingFolderMenuAction(
                    PendingFolderMenuActionType.SetCover,
                    folder
                )
            },
            onRemoveCover = { folder ->
                pendingFolderMenuAction = PendingFolderMenuAction(
                    PendingFolderMenuActionType.RemoveCover,
                    folder
                )
            },
            onMove = { folder ->
                pendingFolderMenuAction = PendingFolderMenuAction(
                    PendingFolderMenuActionType.Move,
                    folder
                )
            }
        )

        // ── 自定义封面来源选择（选择图片 / 网络搜索） ──
        coverSourceBook?.let { sourceBook ->
            CustomCoverSourceSheet(
                book = sourceBook,
                onDismiss = { coverSourceBook = null },
                onPickImage = {
                    coverSourceBook = null
                    launchCoverPicker(sourceBook)
                },
                onWebSearch = {
                    coverSourceBook = null
                    runCatching {
                        CoverSearchActivity.start(context, sourceBook.id, sourceBook.title)
                    }.onFailure { error ->
                        onMessage(error.message ?: "Unable to open cover search")
                    }
                }
            )
        }

        // ── 编辑书本信息对话框（卡片风格） ──
        editingBook?.takeIf { showEditDialog }?.let { currentEditingBook ->
            com.huangder.lumibooks.ui.components.LiquidGlassDialog(
                onDismissRequest = {
                    showEditDialog = false
                    editingBook = null
                },
                modifier = Modifier.imePadding(),
                backgroundScrimColor = Color.Transparent,
                backgroundBlurRadius = 18.dp,
                properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
            ) {
                com.huangder.lumibooks.ui.components.EditInputDialog(
                    title = stringResource(R.string.edit_book_info),
                    fields = listOf(
                        Triple(stringResource(R.string.book_title_label), "显示原始书名", currentEditingBook.title),
                        Triple(stringResource(R.string.book_author_label), "显示原始作者", currentEditingBook.author)
                    ),
                    onBack = {
                        showEditDialog = false
                        editingBook = null
                    },
                    onConfirm = { values ->
                        viewModel.updateBook(
                            currentEditingBook.copy(
                                title = values.getOrElse(0) { currentEditingBook.title },
                                author = values.getOrElse(1) { currentEditingBook.author }
                            )
                        )
                        showEditDialog = false
                        editingBook = null
                    }
                )
            }
        }

        tagTargetBook?.takeIf { showTagSheet }?.let { targetBook ->
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
                onCreateTag = { name, parentId ->
                    viewModel.createAndAssignTag(targetBook.id, name, parentId)
                },
                onDeleteTag = { tag, deleteChildren ->
                    viewModel.deleteTag(tag.id, deleteChildren)
                }
            )
        }

        if (showBatchTagSheet) {
            BatchBookTagSheet(
                tags = uiState.tags,
                selectedBookCount = selectedBookIds.size,
                onDismiss = { showBatchTagSheet = false },
                onCreateTag = { name ->
                    viewModel.createAndAssignTagToBooks(selectedBookIds, name)
                },
                onApply = { tagIds ->
                    tagIds.forEach { tagId ->
                        viewModel.addTagToBooks(selectedBookIds, tagId)
                    }
                    showBatchTagSheet = false
                }
            )
        }

        if (showBatchMoveSheet) {
            FolderMoveSheet(
                folders = uiState.folders,
                selectedBookCount = selectedBookIds.size,
                sourceFolderId = renderedFolderId,
                onDismiss = { showBatchMoveSheet = false },
                onCreateFolder = { name, parentId ->
                    viewModel.createFolder(name, parentId)
                },
                onMove = { targetFolderId ->
                    val movedCount = selectedBookIds.size
                    val targetName = targetFolderId
                        ?.let { id -> uiState.folders.firstOrNull { it.id == id }?.name }
                        ?: context.getString(R.string.library_root)
                    viewModel.moveBooksToFolder(selectedBookIds, targetFolderId) { success ->
                        if (success) {
                            showBatchMoveSheet = false
                            selectedBookIds = emptySet()
                            isEditing = false
                            onMessage(
                                context.getString(R.string.folder_move_success, movedCount, targetName)
                            )
                        }
                    }
                }
            )
        }

        if (showCreateFolderDialog) {
            FolderNameDialog(
                title = stringResource(R.string.new_category_folder),
                onDismiss = { showCreateFolderDialog = false },
                onConfirm = { name ->
                    viewModel.createFolder(name, renderedFolderId) { created ->
                        if (created != null) showCreateFolderDialog = false
                    }
                }
            )
        }

        renameFolder?.let { folder ->
            FolderNameDialog(
                title = stringResource(R.string.rename_folder),
                initialName = folder.name,
                onDismiss = { renameFolder = null },
                onConfirm = { name ->
                    viewModel.renameFolder(folder.id, name) { renamed ->
                        if (renamed) renameFolder = null
                    }
                }
            )
        }

        relocateFolder?.let { folder ->
            FolderRelocationSheet(
                folders = uiState.folders,
                sourceFolder = folder,
                onDismiss = { relocateFolder = null },
                onCreateFolder = { name, parentId -> viewModel.createFolder(name, parentId) },
                onMove = { targetParentId ->
                    viewModel.moveFolder(folder.id, targetParentId) { result ->
                        if (result == FolderMoveResult.Success) relocateFolder = null
                    }
                }
            )
        }

        deleteFolder?.let { folder ->
            val count = folderCounts[folder.id] ?: 0
            LiquidGlassAlertDialog(
                onDismissRequest = { deleteFolder = null },
                title = { Text(stringResource(R.string.delete_folder_title, folder.name)) },
                text = { Text(stringResource(R.string.delete_folder_confirm, count)) },
                confirmButton = {
                    LiquidGlassTextButton(
                        text = stringResource(R.string.delete),
                        tintedColor = Color(0xFFD92D3A),
                        onClick = {
                            viewModel.deleteFolder(folder.id)
                            deleteFolder = null
                        }
                    )
                },
                dismissButton = {
                    LiquidGlassTextButton(
                        text = stringResource(R.string.cancel),
                        onClick = { deleteFolder = null }
                    )
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
internal fun BookshelfCollection(
    layoutMode: Int,
    books: List<Book>,
    tagNamesByBook: Map<String, List<String>>,
    isLoading: Boolean,
    playEntranceAnimation: Boolean,
    deletingBookIds: Set<String>,
    isEditing: Boolean,
    selectedBookIds: Set<String>,
    contextMenuState: BookContextMenuState,
    folderContextMenuState: FolderContextMenuState = rememberFolderContextMenuState(),
    syncedBookIds: Set<String>,
    expandedListBookId: String?,
    expandedListFolderId: String? = null,
    topPadding: Dp,
    bottomPadding: Dp,
    onHaptic: () -> Unit,
    onSelectionToggle: (Book) -> Unit,
    onExpandedBookChange: (String?) -> Unit,
    onExpandedFolderChange: (String?) -> Unit = {},
    onBookClick: (Book, Rect?) -> Unit,
    onAddBook: () -> Unit,
    onEditInfo: (Book) -> Unit,
    onDelete: (Book) -> Unit,
    onFavorite: (Book) -> Unit,
    onCustomCover: (Book) -> Unit,
    onRemoveCustomCover: (Book) -> Unit,
    onTags: (Book) -> Unit,
    onBookmarksNotes: (Book) -> Unit,
    folders: List<LibraryFolder> = emptyList(),
    folderBookCounts: Map<String, Int> = emptyMap(),
    onFolderClick: (LibraryFolder) -> Unit = {},
    onFolderRename: (LibraryFolder) -> Unit = {},
    onFolderDelete: (LibraryFolder) -> Unit = {},
    onFolderSetCover: (LibraryFolder) -> Unit = {},
    onFolderRemoveCover: (LibraryFolder) -> Unit = {},
    onFolderMove: (LibraryFolder) -> Unit = {},
    showAddBook: Boolean = true,
    modifier: Modifier = Modifier
) {
    val targetMode = layoutMode.coerceIn(1, 3)
    val configuration = LocalConfiguration.current
    val isTabletLandscape = configuration.smallestScreenWidthDp >= 600 &&
        configuration.screenWidthDp > configuration.screenHeightDp
    var renderedMode by remember { mutableStateOf(targetMode) }
    var transitionInProgress by remember { mutableStateOf(false) }
    val transitionAlpha = remember { Animatable(1f) }
    val transitionScale = remember { Animatable(1f) }
    val inheritedBackdrop = LocalLiquidGlassBackdrop.current

    // Sequential transition: shrink/fade the current layout out, replace it, then grow/fade the
    // new layout in. Real backdrop effects are temporarily disabled while the parent layer is
    // animated so HyperOS never has to traverse nested blur/refraction render nodes.
    LaunchedEffect(targetMode) {
        if (targetMode != renderedMode) {
            transitionInProgress = true
            try {
                // Give the fallback surfaces one frame to replace real backdrop effects.
                androidx.compose.runtime.withFrameNanos { }
                coroutineScope {
                    launch {
                        transitionAlpha.animateTo(
                            0f,
                            animationSpec = tween(140, easing = AppEasing.Standard)
                        )
                    }
                    launch {
                        transitionScale.animateTo(
                            0.93f,
                            animationSpec = tween(150, easing = AppEasing.Standard)
                        )
                    }
                }

                renderedMode = targetMode
                transitionAlpha.snapTo(0f)
                transitionScale.snapTo(0.93f)

                // Let the replacement Lazy layout finish composition while fully transparent.
                // Otherwise its first visible frame can arrive after the fade has already advanced.
                androidx.compose.runtime.withFrameNanos { }

                coroutineScope {
                    launch {
                        transitionAlpha.animateTo(
                            1f,
                            animationSpec = tween(190, easing = AppEasing.Decelerate)
                        )
                    }
                    launch {
                        transitionScale.animateTo(
                            1f,
                            animationSpec = tween(210, easing = AppEasing.Decelerate)
                        )
                    }
                }
            } finally {
                transitionAlpha.snapTo(1f)
                transitionScale.snapTo(1f)
                transitionInProgress = false
            }
        }
    }

    // List mode intentionally keeps only the glass tint, border, and highlight. Its background is
    // solid, so blur/refraction adds cost without adding useful visual detail.
    ProvideLiquidGlassBackdrop(
        backdrop = if (renderedMode == 1 || transitionInProgress) null else inheritedBackdrop
    ) {
        val transitionModifier = if (transitionInProgress) {
            modifier.graphicsLayer {
                alpha = transitionAlpha.value
                scaleX = transitionScale.value
                scaleY = transitionScale.value
            }
        } else {
            modifier
        }
        Box(modifier = transitionModifier) {
            if (renderedMode == 1) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 14.dp,
                        top = topPadding,
                        end = 14.dp,
                        bottom = bottomPadding
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    folders.forEach { folder ->
                        item(key = "folder_list_${folder.id}") {
                            FolderListItem(
                                folder = folder,
                                bookCount = folderBookCounts[folder.id] ?: 0,
                                enabled = !isEditing,
                                expanded = !isEditing && expandedListFolderId == folder.id,
                                onClick = { onFolderClick(folder) },
                                onLongClick = {
                                    onHaptic()
                                    onExpandedFolderChange(
                                        if (expandedListFolderId == folder.id) null else folder.id
                                    )
                                },
                                onRename = { onFolderRename(folder) },
                                onDelete = { onFolderDelete(folder) },
                                onSetCover = { onFolderSetCover(folder) },
                                onRemoveCover = { onFolderRemoveCover(folder) },
                                onMove = { onFolderMove(folder) }
                            )
                        }
                    }
                    lazyListItemsIndexed(books, key = { _, book -> "list_${book.id}" }) { _, book ->
                        BookshelfSearchResultItem(
                            book = book,
                            tagNames = tagNamesByBook[book.id].orEmpty(),
                            expanded = !isEditing && expandedListBookId == book.id,
                            isDeleting = book.id in deletingBookIds,
                            isSynced = book.id in syncedBookIds,
                            onExpandedChange = {
                                onExpandedBookChange(
                                    if (expandedListBookId == book.id) null else book.id
                                )
                            },
                            onClick = { onBookClick(book, null) },
                            onEditInfo = { onEditInfo(book) },
                            onDelete = { onDelete(book) },
                            onFavorite = { onFavorite(book) },
                            onCustomCover = { onCustomCover(book) },
                            onRemoveCustomCover = { onRemoveCustomCover(book) },
                            onTags = { onTags(book) },
                            onBookmarksNotes = { onBookmarksNotes(book) },
                            selectionMode = isEditing,
                            selected = book.id in selectedBookIds,
                            onSelectionToggle = { onSelectionToggle(book) }
                        )
                    }
                    if (showAddBook && !isLoading && !isEditing) {
                        item(key = "add_book_list") {
                            AddBookListItem(onClick = onAddBook)
                        }
                    }
                }
            } else {
                val gridSpacing = if (renderedMode == 3) 12.dp else AppSpace.lg
            LazyVerticalGrid(
                columns = if (isTabletLandscape && renderedMode >= 2) {
                    GridCells.Adaptive(140.dp)
                } else {
                    GridCells.Fixed(renderedMode)
                },
                contentPadding = PaddingValues(
                    start = AppSpace.lg,
                    top = topPadding,
                    end = AppSpace.lg,
                    bottom = bottomPadding
                ),
                horizontalArrangement = Arrangement.spacedBy(gridSpacing),
                verticalArrangement = Arrangement.spacedBy(AppSpace.lg),
                modifier = Modifier.fillMaxSize()
            ) {
                itemsIndexed(
                    folders,
                    key = { _, folder -> "folder_grid_${renderedMode}_${folder.id}" }
                ) { index, folder ->
                    PageEntranceItem(play = playEntranceAnimation, index = index + 2) {
                        FolderGridItem(
                            folder = folder,
                            bookCount = folderBookCounts[folder.id] ?: 0,
                            enabled = !isEditing,
                            contextMenuState = folderContextMenuState,
                            onHaptic = onHaptic,
                            onClick = { onFolderClick(folder) },
                        )
                    }
                }
                itemsIndexed(books, key = { _, book -> "grid_${renderedMode}_${book.id}" }) { index, book ->
                    PageEntranceItem(play = playEntranceAnimation, index = folders.size + index + 2) {
                        AnimatedBookGridItem(
                            book = book,
                            isDeleting = book.id in deletingBookIds,
                            isEditing = isEditing,
                            isSelected = book.id in selectedBookIds,
                            contextMenuState = contextMenuState,
                            syncedBookIds = syncedBookIds,
                            onHaptic = onHaptic,
                            onSelectionToggle = { onSelectionToggle(book) },
                            onClick = { bounds -> onBookClick(book, bounds) }
                        )
                    }
                }
                if (showAddBook && !isLoading && !isEditing) {
                    item(key = "add_book_grid_$renderedMode") {
                        PageEntranceItem(
                            play = playEntranceAnimation,
                            index = folders.size + books.size + 2
                        ) {
                            AddBookItem(onClick = onAddBook)
                        }
                    }
                }
            }
        }
    }
}
}

@Composable
private fun BookshelfTitle(
    path: List<LibraryFolder>,
    onNavigateToFolder: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    LaunchedEffect(path, scrollState.maxValue) {
        scrollState.scrollTo(scrollState.maxValue)
    }
    Row(
        modifier = modifier.horizontalScroll(scrollState),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(R.string.bookshelf_title),
            fontSize = AppType.Display,
            fontWeight = FontWeight.Bold,
            fontFamily = resolveAppFontFamily(KaiTi),
            color = if (path.isEmpty()) AppColors.TextPrimary else AppColors.Accent,
            maxLines = 1,
            modifier = Modifier.clickable(
                enabled = path.isNotEmpty(),
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) { onNavigateToFolder(null) }
        )
        path.forEachIndexed { index, folder ->
            Text(
                text = " > ",
                fontSize = AppType.Section,
                color = AppColors.TextSecondary,
                maxLines = 1
            )
            Text(
                text = folder.name,
                fontSize = AppType.Section,
                fontWeight = FontWeight.Bold,
                fontFamily = resolveAppFontFamily(KaiTi),
                color = if (index == path.lastIndex) AppColors.TextPrimary else AppColors.Accent,
                maxLines = 1,
                modifier = Modifier.clickable(
                    enabled = index != path.lastIndex,
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) { onNavigateToFolder(folder.id) }
            )
        }
    }
}

@Composable
private fun BookshelfSyncProgressIndicator(isSyncing: Boolean) {
    AnimatedVisibility(visible = isSyncing) {
        CircularProgressIndicator(
            color = AppColors.Accent,
            strokeWidth = 2.dp,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun BookshelfHeaderActions(
    layoutMode: Int,
    isSyncing: Boolean,
    onSyncClick: () -> Unit,
    onLayoutModeChange: (Int) -> Unit,
    onCreateFolder: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isLiquidGlass = LocalAppTheme.current == "liquid_glass"
    val contentColor = if (isLiquidGlass) AppColors.TextPrimary else Color.White
    val menuHost = LocalLiquidGlassMenuHost.current
    var menuExpanded by remember { mutableStateOf(false) }
    var menuAnchorBounds by remember { mutableStateOf(Rect.Zero) }
    val syncLabel = stringResource(if (isSyncing) R.string.webdav_syncing else R.string.webdav_sync_now)
    val standardGridLabel = stringResource(R.string.bookshelf_standard_grid)
    val compactGridLabel = stringResource(R.string.bookshelf_compact_grid)
    val listLayoutLabel = stringResource(R.string.bookshelf_list_layout)
    val createFolderLabel = stringResource(R.string.new_category_folder)
    // 平板横屏下 2/3 宫格都按自适应列渲染（效果一致），因此只在列表/宫格间切换
    val configuration = LocalConfiguration.current
    val isTabletLandscape = configuration.smallestScreenWidthDp >= 600 &&
        configuration.screenWidthDp > configuration.screenHeightDp
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LiquidGlassIconButton(
            imageVector = Icons.Outlined.MoreVert,
            contentDescription = stringResource(R.string.more_options),
            onClick = {
                if (menuExpanded) {
                    menuHost?.dismiss()
                } else if (menuHost != null && menuAnchorBounds != Rect.Zero) {
                    menuExpanded = true
                    menuHost.show(
                        LiquidGlassMenuSpec(
                            anchorBounds = menuAnchorBounds,
                            width = 196.dp,
                            onDismiss = { menuExpanded = false },
                            items = buildList {
                                add(
                                    LiquidGlassMenuItem(
                                        label = createFolderLabel,
                                        icon = Icons.Outlined.CreateNewFolder,
                                        onClick = onCreateFolder
                                    )
                                )
                                add(
                                    LiquidGlassMenuItem(
                                        label = syncLabel,
                                        icon = Icons.Outlined.Sync,
                                        onClick = { if (!isSyncing) onSyncClick() }
                                    )
                                )
                                add(
                                    LiquidGlassMenuItem(
                                        label = standardGridLabel,
                                        icon = layoutIcon(layoutMode = 2, compact = false),
                                        selected = layoutMode == 2,
                                        onClick = { onLayoutModeChange(2) }
                                    )
                                )
                                add(
                                    LiquidGlassMenuItem(
                                        label = compactGridLabel,
                                        icon = layoutIcon(layoutMode = 3, compact = false),
                                        selected = layoutMode == 3,
                                        onClick = { onLayoutModeChange(3) }
                                    )
                                )
                                add(
                                    LiquidGlassMenuItem(
                                        label = listLayoutLabel,
                                        icon = Icons.Outlined.ViewList,
                                        selected = layoutMode == 1,
                                        onClick = { onLayoutModeChange(1) }
                                    )
                                )
                            }
                        )
                    )
                }
            },
            size = 32.dp,
            iconSize = 15.dp,
            contentColor = contentColor,
            normalContainerColor = AppColors.Accent,
            liquidContainerColor = AppColors.CardBg,
            liquidScrimColor = AppColors.CardBg.copy(alpha = 0.58f),
            modifier = Modifier.onGloballyPositioned { menuAnchorBounds = it.boundsInRoot() }
        )
        LiquidGlassSurface(
            shape = CircleShape,
            fallbackColor = if (isLiquidGlass) AppColors.CardBg else AppColors.Accent,
            contentScrimColor = AppColors.CardBg.copy(alpha = 0.58f),
            onClick = {
                onLayoutModeChange(
                    if (isTabletLandscape) {
                        if (layoutMode == 1) 2 else 1
                    } else {
                        layoutMode % 3 + 1
                    }
                )
            },
            effectPadding = 1.dp,
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                imageVector = layoutIcon(layoutMode, compact = isTabletLandscape),
                contentDescription = stringResource(R.string.bookshelf_layout),
                tint = contentColor,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

private fun layoutIcon(layoutMode: Int, compact: Boolean): ImageVector = when {
    compact && layoutMode == 1 -> Icons.Outlined.ViewList
    compact -> Icons.Outlined.GridView
    layoutMode == 1 -> Icons.Outlined.ViewList
    layoutMode == 2 -> Icons.Outlined.ViewModule
    else -> Icons.Outlined.GridView
}

@Composable
private fun BookshelfCapsuleHeader(
    filterTabs: List<BookshelfFilterTab>,
    selectedFilter: BookshelfFilter,
    isEditing: Boolean,
    selectedCount: Int,
    onFilterSelected: (BookshelfFilter) -> Unit,
    onEditToggle: () -> Unit,
    onDeleteSelected: () -> Unit,
    onTagSelected: () -> Unit,
    onMoveSelected: () -> Unit,
    folderPath: List<LibraryFolder>,
    onNavigateToFolder: (String?) -> Unit,
    onCreateFolder: () -> Unit,
    onBrowseFilters: () -> Unit,
    onSearchClick: () -> Unit,
    onSyncClick: () -> Unit,
    onSelectAll: () -> Unit,
    layoutMode: Int,
    isWebdavSyncing: Boolean,
    onLayoutModeChange: (Int) -> Unit,
    onSearchBoundsChanged: (Rect) -> Unit,
    modifier: Modifier = Modifier
) {
    val isLiquidGlass = LocalAppTheme.current == "liquid_glass"
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
            .then(
                if (isLiquidGlass) Modifier else Modifier.background(AppColors.WindowBg)
            )
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
                tintedColor = AppColors.CardBg.takeUnless { isLiquidGlass },
                contentColor = AppColors.TextPrimary,
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
                visible = isEditing,
                enter = fadeIn(tween(120)) + scaleIn(
                    initialScale = 0.78f,
                    animationSpec = spring(dampingRatio = 0.68f, stiffness = 360f)
                ),
                exit = fadeOut(tween(110)) + scaleOut(targetScale = 0.82f)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Spacer(Modifier.width(10.dp))
                    LiquidGlassIconButton(
                        imageVector = Icons.Outlined.Delete,
                        contentDescription = stringResource(R.string.delete),
                        onClick = onDeleteSelected,
                        enabled = selectedCount > 0,
                        size = 46.dp,
                        iconSize = 21.dp,
                        contentColor = Color.White,
                        normalContainerColor = Color(0xFFD92D3A),
                        liquidContainerColor = Color(0xFFD92D3A),
                        liquidScrimColor = Color(0xB8D92D3A)
                    )
                    Spacer(Modifier.width(10.dp))
                    LiquidGlassIconButton(
                        imageVector = Icons.Outlined.Label,
                        contentDescription = stringResource(R.string.tag_sheet_title),
                        onClick = onTagSelected,
                        enabled = selectedCount > 0,
                        size = 46.dp,
                        iconSize = 21.dp,
                        contentColor = AppColors.TextPrimary,
                        normalContainerColor = AppColors.CardBg,
                        liquidContainerColor = AppColors.CardBg,
                        liquidScrimColor = AppColors.CardBg.copy(alpha = 0.58f)
                    )
                    Spacer(Modifier.width(10.dp))
                    LiquidGlassIconButton(
                        imageVector = Icons.Outlined.DriveFileMove,
                        contentDescription = stringResource(R.string.move_books),
                        onClick = onMoveSelected,
                        enabled = selectedCount > 0,
                        size = 46.dp,
                        iconSize = 21.dp,
                        contentColor = AppColors.TextPrimary,
                        normalContainerColor = AppColors.CardBg,
                        liquidContainerColor = AppColors.CardBg,
                        liquidScrimColor = AppColors.CardBg.copy(alpha = 0.58f)
                    )
                }
            }

            Spacer(Modifier.weight(1f))

            if (!isEditing) {
                LiquidGlassSurface(
                    shape = CircleShape,
                    fallbackColor = AppColors.CardBg,
                    contentScrimColor = AppColors.CardBg.copy(alpha = 0.58f),
                    effectPadding = if (isLiquidGlass) 2.dp else 0.dp,
                    decorationModifier = Modifier.shadow(
                        elevation = 16.dp,
                        shape = CircleShape,
                        clip = false,
                        ambientColor = Color.Black.copy(alpha = 0.10f),
                        spotColor = Color.Black.copy(alpha = 0.12f)
                    ),
                    modifier = Modifier
                        .width(154.dp)
                        .height(46.dp)
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        Row(
                            modifier = Modifier
                                .align(Alignment.Center),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = selectedLabel,
                                color = AppColors.TextPrimary,
                                fontSize = AppType.BodySmall,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.widthIn(max = 68.dp)
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
                            Spacer(Modifier.width(18.dp))
                            Icon(
                                imageVector = Icons.Outlined.FormatListBulleted,
                                contentDescription = null,
                                tint = AppColors.TextPrimary,
                                modifier = Modifier.size(25.dp)
                            )
                        }

                        Row(modifier = Modifier.fillMaxSize()) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxSize()
                                    .onGloballyPositioned { filterAnchorBounds = it.boundsInRoot() }
                                    .clickable(
                                        indication = null,
                                        interactionSource = remember { MutableInteractionSource() }
                                    ) {
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
                                    }
                            )
                            Box(
                                modifier = Modifier
                                    .width(52.dp)
                                    .fillMaxSize()
                                    .clickable(
                                        indication = null,
                                        interactionSource = remember { MutableInteractionSource() },
                                        onClick = onBrowseFilters
                                    )
                            )
                        }
                    }
                }
            }
        }

        AnimatedVisibility(visible = isEditing) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AppSpace.lg, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.selected_books_count_compact, selectedCount),
                    color = AppColors.TextSecondary,
                    fontSize = AppType.Caption,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )
                LiquidGlassButton(
                    onClick = onSelectAll,
                    tintedColor = AppColors.Accent,
                    contentColor = AppColors.OnAccent,
                    prominentShadow = false,
                    modifier = Modifier.height(40.dp)
                ) {
                    Text(
                        text = stringResource(R.string.select_all),
                        color = AppColors.OnAccent,
                        fontSize = AppType.BodySmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        AnimatedVisibility(visible = !isEditing) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            start = AppSpace.lg,
                            end = AppSpace.lg,
                            top = 14.dp,
                            bottom = 10.dp
                        ),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BookshelfTitle(
                        path = folderPath,
                        onNavigateToFolder = onNavigateToFolder,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    BookshelfHeaderActions(
                        layoutMode = layoutMode,
                        isSyncing = isWebdavSyncing,
                        onSyncClick = onSyncClick,
                        onLayoutModeChange = onLayoutModeChange,
                        onCreateFolder = onCreateFolder
                    )
                    Spacer(Modifier.width(8.dp))
                    BookshelfSyncProgressIndicator(isSyncing = isWebdavSyncing)
                }

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
    }
}

private enum class PendingBookMenuActionType {
    Favorite,
    CustomCover,
    RemoveCustomCover,
    BookmarksNotes,
    Tags,
    EditInfo
}

private data class PendingBookMenuAction(
    val type: PendingBookMenuActionType,
    val book: Book
)

private enum class PendingFolderMenuActionType {
    Rename,
    SetCover,
    RemoveCover,
    Move,
    Delete
}

private data class PendingFolderMenuAction(
    val type: PendingFolderMenuActionType,
    val folder: LibraryFolder
)

private sealed interface BookshelfFilter {
    data object All : BookshelfFilter
    data object EpubMobi : BookshelfFilter
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
    syncedBookIds: Set<String>,
    onHaptic: () -> Unit,
    onSelectionToggle: () -> Unit,
    onClick: (Rect?) -> Unit
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
            syncedBookIds = syncedBookIds,
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
    syncedBookIds: Set<String>,
    onHaptic: () -> Unit,
    onSelectionToggle: () -> Unit,
    onClick: (Rect?) -> Unit
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
                onClick = if (isEditing) onSelectionToggle else {
                    { onClick(coverCoordinates[0]?.boundsInRoot()) }
                },
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
                        text = formatProgressPercent(book.readingProgress),
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
            if (book.id in syncedBookIds) {
                Spacer(Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.Filled.Cloud,
                    contentDescription = stringResource(R.string.category_webdav),
                    tint = AppColors.TextSecondary,
                    modifier = Modifier.size(13.dp)
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

// ─── 分类文件夹 ────────────────────────────────────────────────

@Composable
private fun FolderListItem(
    folder: LibraryFolder,
    bookCount: Int,
    enabled: Boolean,
    expanded: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onSetCover: () -> Unit,
    onRemoveCover: () -> Unit,
    onMove: () -> Unit
) {
    val eInkMode = LocalEInkMode.current
    val shape = RoundedCornerShape(if (LocalAppTheme.current == "liquid_glass") 24.dp else 16.dp)
    Column(modifier = Modifier.fillMaxWidth()) {
        LiquidGlassSurface(
            shape = shape,
            fallbackColor = AppColors.CardBg,
            contentScrimColor = AppColors.CardBg.copy(alpha = 0.76f),
            interactive = false,
            effectPadding = 1.dp,
            decorationModifier = Modifier.shadow(
                elevation = 17.dp,
                shape = shape,
                clip = false,
                ambientColor = Color.Black.copy(alpha = 0.10f),
                spotColor = Color.Black.copy(alpha = 0.14f)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(116.dp)
                .graphicsLayer { alpha = if (enabled) 1f else 0.62f }
                .combinedClickable(
                    enabled = enabled,
                    onClick = onClick,
                    onLongClick = onLongClick
                )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FolderCover(
                    folder = folder,
                    cornerRadius = 14.dp,
                    modifier = Modifier
                        .width(69.dp)
                        .height(92.dp)
                )
                Spacer(Modifier.width(16.dp))
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = folder.name,
                        color = AppColors.TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = resolveAppFontFamily(KaiTi),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = stringResource(R.string.bookshelf_category_book_count, bookCount),
                        color = AppColors.TextSecondary,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = expanded,
            enter = if (eInkMode) {
                EnterTransition.None
            } else {
                expandVertically(
                    expandFrom = Alignment.Top,
                    animationSpec = spring(dampingRatio = 0.78f, stiffness = 300f),
                    clip = false
                ) + fadeIn(tween(130))
            },
            exit = if (eInkMode) {
                ExitTransition.None
            } else {
                shrinkVertically(
                    shrinkTowards = Alignment.Top,
                    animationSpec = tween(190),
                    clip = false
                ) + fadeOut(tween(140))
            }
        ) {
            Column {
                Spacer(Modifier.height(10.dp))
                FolderListActionRow(
                    folder = folder,
                    visible = expanded,
                    onRename = onRename,
                    onDelete = onDelete,
                    onSetCover = onSetCover,
                    onRemoveCover = onRemoveCover,
                    onMove = onMove
                )
            }
        }
    }
}

@Composable
private fun FolderGridItem(
    folder: LibraryFolder,
    bookCount: Int,
    enabled: Boolean,
    contextMenuState: FolderContextMenuState,
    onHaptic: () -> Unit,
    onClick: () -> Unit
) {
    val coverCorner = if (LocalAppTheme.current == "liquid_glass") 16.dp else AppRadius.sm
    val isTarget = contextMenuState.selectedFolder?.id == folder.id
    val overlayActive = contextMenuState.phase != ContextMenuPhase.Idle && isTarget
    val overlayAlpha = if (overlayActive) contextMenuState.itemAlpha.value else 1f
    val coverCoordinates = remember {
        arrayOfNulls<androidx.compose.ui.layout.LayoutCoordinates>(1)
    }
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
    val pressScale = if (isPressed && enabled) 0.95f else 1f
    Column(
        modifier = Modifier
            .graphicsLayer { alpha = overlayAlpha * if (enabled) 1f else 0.62f }
            .combinedClickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
                onLongClick = {
                    contextMenuState.onLongPressConfirmed(
                        folder = folder,
                        bounds = coverCoordinates[0]?.boundsInRoot() ?: Rect.Zero,
                        onHaptic = onHaptic
                    )
                }
            )
    ) {
        FolderCover(
            folder = folder,
            cornerRadius = coverCorner,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.75f)
                .onGloballyPositioned { coordinates -> coverCoordinates[0] = coordinates }
                .graphicsLayer {
                    scaleX = pressScale
                    scaleY = pressScale
                }
                .shadow(
                    10.dp,
                    RoundedCornerShape(coverCorner),
                    ambientColor = Color(0x08000000),
                    spotColor = Color(0x08000000)
                )
        )
        Spacer(Modifier.height(AppSpace.sm))
        Text(
            text = folder.name,
            fontSize = AppType.BodySmall,
            fontWeight = FontWeight.SemiBold,
            color = AppColors.TextPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = stringResource(R.string.bookshelf_category_book_count, bookCount),
            fontSize = AppType.Caption,
            color = AppColors.TextSecondary,
            maxLines = 1
        )
    }
}

// ─── 添加书籍 ──────────────────────────────────────────────────

@Composable
private fun AddBookListItem(onClick: () -> Unit) {
    val isLiquidGlass = LocalAppTheme.current == "liquid_glass"
    val shape = RoundedCornerShape(if (isLiquidGlass) 24.dp else 16.dp)
    LiquidGlassSurface(
        shape = shape,
        fallbackColor = AppColors.CardBg,
        contentScrimColor = AppColors.CardBg.copy(alpha = 0.76f),
        onClick = onClick,
        effectPadding = 1.dp,
        decorationModifier = Modifier.shadow(
            elevation = 12.dp,
            shape = shape,
            clip = false,
            ambientColor = Color.Black.copy(alpha = 0.08f),
            spotColor = Color.Black.copy(alpha = 0.10f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .height(84.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(AppColors.Accent),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(Modifier.width(16.dp))
            Text(
                text = stringResource(R.string.import_book),
                color = AppColors.TextPrimary,
                fontSize = AppType.Section,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

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

internal fun formatProgressPercent(progress: Float): String {
    val pct = progress * 100f
    return if (pct < 10f) "%.1f%%".format(pct) else "${pct.toInt()}%"
}
