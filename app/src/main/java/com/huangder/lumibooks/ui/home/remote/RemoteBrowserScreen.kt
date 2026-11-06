package com.huangder.lumibooks.ui.home.remote

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.huangder.lumibooks.ui.components.LocalCollapsingPageTopInset
import com.huangder.lumibooks.ui.components.CollapsingPageScaffold

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.shadow
import com.huangder.lumibooks.R
import com.huangder.lumibooks.data.remote.RemoteBookFiles
import com.huangder.lumibooks.data.remote.RemoteBookItem
import com.huangder.lumibooks.data.remote.RemoteDownloadState
import com.huangder.lumibooks.data.remote.RemoteErrorKind
import com.huangder.lumibooks.data.remote.RemoteFolder
import com.huangder.lumibooks.ui.animation.AppEasing
import com.huangder.lumibooks.ui.animation.VisualMotion.tween
import com.huangder.lumibooks.ui.components.LiquidGlassButton
import com.huangder.lumibooks.ui.components.ProvideLiquidGlassBackdrop
import com.huangder.lumibooks.ui.components.LocalCollapsingPageBottomInset
import com.huangder.lumibooks.ui.components.LumiCheckbox
import com.huangder.lumibooks.ui.home.BookPickerHeader
import com.huangder.lumibooks.ui.home.BookPickerSelectionBar
import com.huangder.lumibooks.ui.components.lumiCardSurface
import com.huangder.lumibooks.ui.icons.AppIcons
import com.huangder.lumibooks.ui.icons.directionalIcon
import com.huangder.lumibooks.ui.theme.AppColors
import com.huangder.lumibooks.ui.theme.AppRadius
import com.huangder.lumibooks.ui.theme.AppRoundedCornerShape
import com.huangder.lumibooks.ui.theme.AppSpace
import com.huangder.lumibooks.ui.theme.AppType
import com.huangder.lumibooks.ui.theme.LocalAppTheme
import com.huangder.lumibooks.ui.theme.LocalEInkMode
import com.huangder.lumibooks.ui.theme.LocalMotionEnabled
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * Local and remote pickers share the search toolbar, status line and floating action layout.
 * Tap a book to select it; long press keeps its remote details and format choices available.
 */
@Composable
internal fun RemoteBrowserScreen(
    levels: List<RemoteLevel>,
    searchAvailable: Boolean,
    searchQuery: String?,
    selection: Set<String>,
    importedKeys: Set<String>,
    downloadStates: Map<String, RemoteDownloadState>,
    layoutMode: Int,
    sortMode: RemoteSortMode,
    sortAscending: Boolean,
    navigatedForward: Boolean,
    contentBackdrop: LayerBackdrop?,
    onBack: () -> Unit,
    onSearchOpen: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onSearchSubmit: () -> Unit,
    onSearchClose: () -> Unit,
    onRefresh: () -> Unit,
    onOpenFolder: (RemoteFolder) -> Unit,
    onOpenBook: (RemoteBookItem) -> Unit,
    onToggleBook: (RemoteBookItem) -> Unit,
    onSelectAll: (List<RemoteBookItem>) -> Unit,
    onSortModeChange: (RemoteSortMode) -> Unit,
    onSortAscendingChange: (Boolean) -> Unit,
    onLayoutModeChange: () -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    onDownloadSelected: () -> Unit
) {
    val isLiquidGlass = LocalAppTheme.current == "liquid_glass"
    val motionEnabled = LocalMotionEnabled.current
    val eInkMode = LocalEInkMode.current
    // Capture only the scrolling content for the glass action, never the action itself.
    val backdrop = contentBackdrop ?: rememberLayerBackdrop()
    val useBackdrop = isLiquidGlass && contentBackdrop != null
    val currentLevel = levels.lastOrNull() ?: return

    // The drawn level lags one animation behind the requested one, exactly like the folder books page.
    var renderedLevel by remember { mutableStateOf(currentLevel) }
    val alpha = remember { Animatable(1f) }
    val offset = remember { Animatable(0f) }
    LaunchedEffect(currentLevel.url, levels.size) {
        if (currentLevel.url == renderedLevel.url) return@LaunchedEffect
        if (eInkMode) {
            renderedLevel = currentLevel
            alpha.snapTo(1f)
            offset.snapTo(0f)
            return@LaunchedEffect
        }
        if (!motionEnabled) {
            offset.snapTo(0f)
            alpha.animateTo(0f, tween(70, easing = AppEasing.Standard))
            renderedLevel = currentLevel
            alpha.animateTo(1f, tween(90, easing = AppEasing.Standard))
            return@LaunchedEffect
        }
        val direction = if (navigatedForward) -1f else 1f
        coroutineScope {
            launch { alpha.animateTo(0f, tween(160, easing = AppEasing.Accelerate)) }
            launch { offset.animateTo(32f * direction, tween(180, easing = AppEasing.Accelerate)) }
        }
        renderedLevel = currentLevel
        alpha.snapTo(0f)
        offset.snapTo(-32f * direction)
        coroutineScope {
            launch { alpha.animateTo(1f, tween(220, easing = AppEasing.Decelerate)) }
            launch { offset.animateTo(0f, tween(240, easing = AppEasing.Smooth)) }
        }
    }
    // Same URL, new data (load finished, next page appended): show it immediately.
    val level = if (renderedLevel.url == currentLevel.url) currentLevel else renderedLevel
    val page = level.page
    // Libraries without a server search endpoint still support filtering the loaded level.
    val localQuery = if (searchAvailable) "" else searchQuery.orEmpty().trim()
    val books = remember(page?.books, sortMode, sortAscending, localQuery) {
        sortRemoteBooks(page?.books.orEmpty().filter { book ->
            localQuery.isBlank() || book.title.contains(localQuery, ignoreCase = true) ||
                book.authors.any { it.contains(localQuery, ignoreCase = true) }
        }, sortMode, sortAscending)
    }
    val folders = page?.folders.orEmpty().filter {
        localQuery.isBlank() || it.title.contains(localQuery, ignoreCase = true)
    }
    val breadcrumb = levels.takeIf { it.size > 1 }?.joinToString(" › ") { it.title }.orEmpty()
    val summary = when {
        page == null && level.loading -> stringResource(R.string.remote_library_loading)
        page == null -> ""
        else -> stringResource(R.string.folder_books_level_summary, folders.size, books.size)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        CollapsingPageScaffold(
            drawContentBehindHeader = true,
            bottomBarOverlay = true,
            bottomBar = {
                Box(
                    Modifier.fillMaxWidth().navigationBarsPadding()
                        .padding(horizontal = AppSpace.lg, vertical = AppSpace.md)
                ) {
                    ProvideLiquidGlassBackdrop(backdrop.takeIf { useBackdrop }) {
                        BookPickerSelectionBar(
                            confirmText = stringResource(R.string.remote_library_download_selected, selection.size),
                            confirmEnabled = selection.isNotEmpty(),
                            selectAllEnabled = books.any { it.preferredFormat != null },
                            onSelectAll = { onSelectAll(books.filter { it.preferredFormat != null }) },
                            onConfirm = onDownloadSelected
                        )
                    }
                }
            },
            title = level.title,
            state = androidx.compose.runtime.key(level.url) { com.huangder.lumibooks.ui.components.rememberCollapsingPageState() },
            onBack = if (searchQuery != null) onSearchClose else onBack,
            pinnedContent = {
                RemoteBrowserHeader(
                    path = breadcrumb,
                    summary = summary,
                    searchAvailable = searchAvailable,
                    searchQuery = searchQuery,
                    loading = level.loading,
                    layoutMode = layoutMode,
                    sortMode = sortMode,
                    sortAscending = sortAscending,
                    onSearchOpen = onSearchOpen,
                    onSearchQueryChange = onSearchQueryChange,
                    onSearchSubmit = onSearchSubmit,
                    onSearchClose = onSearchClose,
                    onRefresh = onRefresh,
                    onSortModeChange = onSortModeChange,
                    onSortAscendingChange = onSortAscendingChange,
                    onLayoutModeChange = onLayoutModeChange
                )
            }
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .graphicsLayer {
                        this.alpha = alpha.value
                        translationY = offset.value.dp.toPx()
                    }
                    .then(if (useBackdrop) Modifier.layerBackdrop(backdrop) else Modifier)
            ) {
                when {
                    page == null && level.loading -> Box(Modifier.fillMaxSize().padding(top = LocalCollapsingPageTopInset.current), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = AppColors.Accent, strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
                    }
                    page == null -> RemoteEmptyState(
                        message = stringResource((level.error ?: RemoteErrorKind.UNKNOWN).messageRes()),
                        actionLabel = stringResource(R.string.retry),
                        onAction = onRetry,
                        modifier = Modifier.padding(horizontal = AppSpace.lg).padding(top = LocalCollapsingPageTopInset.current)
                    )
                    folders.isEmpty() && books.isEmpty() && page.nextUrl == null -> RemoteEmptyState(
                        message = stringResource(
                            if (localQuery.isNotBlank() || level.url.startsWith("search:")) R.string.search_no_results else R.string.remote_library_empty_folder
                        ),
                        modifier = Modifier.padding(top = LocalCollapsingPageTopInset.current)
                    )
                    else -> AnimatedContent(
                        targetState = layoutMode,
                        modifier = Modifier.fillMaxSize(),
                        transitionSpec = {
                            if (!motionEnabled || eInkMode) {
                                fadeIn(tween(100)) togetherWith fadeOut(tween(100))
                            } else {
                                (fadeIn(tween(200, delayMillis = 60)) +
                                    scaleIn(initialScale = 0.94f, animationSpec = tween(240, delayMillis = 60))) togetherWith
                                    (fadeOut(tween(140)) + scaleOut(targetScale = 0.92f, animationSpec = tween(180)))
                            }
                        },
                        label = "remoteLayout"
                    ) { mode ->
                        RemoteLevelContent(
                            topPadding = LocalCollapsingPageTopInset.current,
                            folders = folders,
                            books = books,
                            layoutMode = mode,
                            hasMore = page.nextUrl != null,
                            loadingMore = level.loadingMore,
                            selection = selection,
                            importedKeys = importedKeys,
                            downloadStates = downloadStates,
                            onOpenFolder = onOpenFolder,
                            onOpenBook = onOpenBook,
                            onToggleBook = onToggleBook,
                            onLoadMore = onLoadMore
                        )
                    }
                }
            }
        }

    }
}

@Composable
private fun RemoteLevelContent(
    topPadding: androidx.compose.ui.unit.Dp = 0.dp,
    folders: List<RemoteFolder>,
    books: List<RemoteBookItem>,
    layoutMode: Int,
    hasMore: Boolean,
    loadingMore: Boolean,
    selection: Set<String>,
    importedKeys: Set<String>,
    downloadStates: Map<String, RemoteDownloadState>,
    onOpenFolder: (RemoteFolder) -> Unit,
    onOpenBook: (RemoteBookItem) -> Unit,
    onToggleBook: (RemoteBookItem) -> Unit,
    onLoadMore: () -> Unit
) {
    val padding = PaddingValues(
        start = AppSpace.lg,
        end = AppSpace.lg,
        top = topPadding + AppSpace.xs,
        bottom = LocalCollapsingPageBottomInset.current + AppSpace.lg
    )
    val bookClick: (RemoteBookItem) -> Unit = {
        if (it.preferredFormat != null) onToggleBook(it) else onOpenBook(it)
    }
    if (layoutMode == 1) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = padding,
            verticalArrangement = Arrangement.spacedBy(AppSpace.sm)
        ) {
            items(folders, key = { "folder:${it.url}" }) { folder ->
                RemoteFolderRow(folder = folder, enabled = true, onClick = { onOpenFolder(folder) })
            }
            items(books, key = { "book:${it.key}" }) { book ->
                RemoteBookRow(
                    book = book,
                    selected = book.key in selection,
                    imported = book.key in importedKeys,
                    downloadState = downloadStates[book.key],
                    onClick = { bookClick(book) },
                    onLongClick = { onOpenBook(book) }
                )
            }
            if (hasMore) item(key = "more") { LoadMoreFooter(loadingMore, onLoadMore) }
        }
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(layoutMode),
        modifier = Modifier.fillMaxSize(),
        contentPadding = padding,
        horizontalArrangement = Arrangement.spacedBy(AppSpace.sm),
        verticalArrangement = Arrangement.spacedBy(AppSpace.md)
    ) {
        items(folders, key = { "folder:${it.url}" }) { folder ->
            RemoteFolderTile(folder = folder, compact = layoutMode == 3, enabled = true, onClick = { onOpenFolder(folder) })
        }
        items(books, key = { "book:${it.key}" }) { book ->
            RemoteBookTile(
                book = book,
                compact = layoutMode == 3,
                selected = book.key in selection,
                imported = book.key in importedKeys,
                downloadState = downloadStates[book.key],
                onClick = { bookClick(book) },
                onLongClick = { onOpenBook(book) }
            )
        }
        if (hasMore) {
            item(key = "more", span = { GridItemSpan(maxLineSpan) }) { LoadMoreFooter(loadingMore, onLoadMore) }
        }
    }
}

/** Appears at the end of a paged catalog; composing it is what requests the next page. */
@Composable
private fun LoadMoreFooter(loadingMore: Boolean, onLoadMore: () -> Unit) {
    LaunchedEffect(Unit) { onLoadMore() }
    Box(Modifier.fillMaxWidth().padding(AppSpace.md), contentAlignment = Alignment.Center) {
        if (loadingMore) {
            CircularProgressIndicator(color = AppColors.Accent, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
        } else {
            RemoteHeaderPill(stringResource(R.string.remote_library_load_more), onLoadMore)
        }
    }
}

@Composable
private fun RemoteFolderThumb(
    folder: RemoteFolder,
    modifier: Modifier = Modifier,
    iconSize: androidx.compose.ui.unit.Dp = 24.dp
) {
    if (folder.coverUrl != null) {
        RemoteBookCover(
            model = folder.coverUrl,
            extension = null,
            title = folder.title,
            shape = AppRoundedCornerShape(AppRadius.sm),
            placeholderFontSize = AppType.Caption,
            modifier = modifier
        )
    } else {
        Box(
            modifier = modifier.clip(AppRoundedCornerShape(AppRadius.sm)).background(AppColors.BgGray),
            contentAlignment = Alignment.Center
        ) {
            Icon(AppIcons.Folder, contentDescription = null, tint = AppColors.Accent, modifier = Modifier.size(iconSize))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RemoteFolderRow(folder: RemoteFolder, enabled: Boolean, onClick: () -> Unit) {
    val shape = AppRoundedCornerShape(AppRadius.md)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .lumiCardSurface(shape = shape)
            .combinedClickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(AppSpace.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RemoteFolderThumb(folder, modifier = Modifier.width(40.dp).height(56.dp))
        Spacer(Modifier.width(AppSpace.sm))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = folder.title,
                color = if (enabled) AppColors.TextPrimary else AppColors.TextSecondary,
                fontSize = AppType.BodySmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            folder.subtitle?.let {
                Spacer(Modifier.height(2.dp))
                Text(it, color = AppColors.TextSecondary, fontSize = AppType.Caption, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.width(AppSpace.sm))
        Icon(
            imageVector = directionalIcon(AppIcons.CaretRight, AppIcons.CaretLeft),
            contentDescription = null,
            tint = AppColors.TextSecondary,
            modifier = Modifier.size(18.dp)
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RemoteFolderTile(folder: RemoteFolder, compact: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(AppRoundedCornerShape(AppRadius.lg))
            .combinedClickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(AppSpace.sm)
    ) {
        RemoteFolderThumb(
            folder = folder,
            modifier = Modifier.fillMaxWidth().aspectRatio(0.72f),
            iconSize = if (compact) 28.dp else 34.dp
        )
        Spacer(Modifier.height(AppSpace.sm))
        Text(
            text = folder.title,
            color = if (enabled) AppColors.TextPrimary else AppColors.TextSecondary,
            fontSize = if (compact) AppType.Caption else AppType.BodySmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private fun RemoteBookItem.metaLine(sizeLabel: String?): String = listOfNotNull(
    authorLabel.takeIf(String::isNotBlank),
    RemoteBookFiles.displayFormat(preferredFormat?.extension).takeIf(String::isNotBlank),
    sizeLabel
).joinToString(" · ")

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RemoteBookRow(
    book: RemoteBookItem,
    selected: Boolean,
    imported: Boolean,
    downloadState: RemoteDownloadState?,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val haptics = LocalHapticFeedback.current
    val shape = AppRoundedCornerShape(AppRadius.md)
    val format = book.preferredFormat
    val meta = book.metaLine(formatRemoteSize(format?.sizeBytes ?: 0L))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.5.dp, AppColors.Accent.copy(alpha = if (selected) 1f else 0f), shape)
            .clip(shape)
            .lumiCardSurface(shape = shape)
            .combinedClickable(
                onClick = onClick,
                onLongClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongClick()
                },
                role = if (format != null) Role.Checkbox else Role.Button
            )
            .semantics { if (format != null) this.selected = selected }
            .padding(AppSpace.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RemoteBookCover(
            model = book.thumbnailUrl ?: book.coverUrl,
            extension = format?.extension,
            title = book.title,
            shape = AppRoundedCornerShape(AppRadius.sm),
            placeholderFontSize = AppType.Caption,
            modifier = Modifier.width(40.dp).height(56.dp)
        )
        Spacer(Modifier.width(AppSpace.sm))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = book.title,
                color = AppColors.TextPrimary,
                fontSize = AppType.BodySmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (meta.isNotBlank()) {
                    Text(
                        meta,
                        color = AppColors.TextSecondary,
                        fontSize = AppType.Caption,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                }
                if (imported || downloadState is RemoteDownloadState.Completed) {
                    Spacer(Modifier.width(AppSpace.xs))
                    RemoteImportedBadge()
                }
            }
            RemoteDownloadProgress(downloadState)
        }
        if (format != null) {
            Spacer(Modifier.width(AppSpace.sm))
            RemoteSelectionIndicator(selected)
            Spacer(Modifier.width(AppSpace.sm))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RemoteBookTile(
    book: RemoteBookItem,
    compact: Boolean,
    selected: Boolean,
    imported: Boolean,
    downloadState: RemoteDownloadState?,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val haptics = LocalHapticFeedback.current
    val shape = AppRoundedCornerShape(AppRadius.lg)
    val format = book.preferredFormat
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.5.dp, AppColors.Accent.copy(alpha = if (selected) 1f else 0f), shape)
            .clip(shape)
            .combinedClickable(
                onClick = onClick,
                onLongClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongClick()
                },
                role = if (format != null) Role.Checkbox else Role.Button
            )
            .semantics { if (format != null) this.selected = selected }
            .padding(AppSpace.sm)
    ) {
        Box {
            RemoteBookCover(
                model = book.thumbnailUrl ?: book.coverUrl,
                extension = format?.extension,
                title = book.title,
                shape = AppRoundedCornerShape(AppRadius.md),
                placeholderFontSize = if (compact) AppType.Caption else AppType.BodySmall,
                modifier = Modifier.fillMaxWidth().aspectRatio(0.72f)
            )
            if (format != null) {
                Box(Modifier.align(Alignment.TopEnd).padding(6.dp)) { RemoteSelectionIndicator(selected) }
            }
        }
        Spacer(Modifier.height(AppSpace.sm))
        Text(
            text = book.title,
            color = AppColors.TextPrimary,
            fontSize = if (compact) AppType.Caption else AppType.BodySmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(2.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = RemoteBookFiles.displayFormat(format?.extension),
                color = AppColors.TextSecondary,
                fontSize = AppType.Caption,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (imported || downloadState is RemoteDownloadState.Completed) {
                Spacer(Modifier.width(AppSpace.xs))
                RemoteImportedBadge()
            }
        }
        RemoteDownloadProgress(downloadState)
    }
}

@Composable
private fun RemoteDownloadProgress(state: RemoteDownloadState?) {
    val label = remoteDownloadLabel(state) ?: return
    Text(label, color = AppColors.Accent, fontSize = AppType.Caption, maxLines = 1)
    if (state is RemoteDownloadState.Downloading) {
        Spacer(Modifier.height(AppSpace.xs))
        val progress = state.progress
        val modifier = Modifier.fillMaxWidth().height(3.dp).clip(CircleShape)
        if (progress != null) {
            LinearProgressIndicator(progress = { progress }, modifier = modifier, color = AppColors.Accent, trackColor = AppColors.BgGray)
        } else {
            LinearProgressIndicator(modifier = modifier, color = AppColors.Accent, trackColor = AppColors.BgGray)
        }
    }
}

@Composable
private fun RemoteImportedBadge() {
    Box(
        modifier = Modifier
            .clip(AppRoundedCornerShape(AppRadius.full))
            .background(AppColors.Accent.copy(alpha = 0.14f))
            .padding(horizontal = 5.dp, vertical = 1.dp)
    ) {
        Text(
            text = stringResource(R.string.import_folder_books_imported),
            color = AppColors.Accent,
            fontSize = AppType.Caption,
            fontWeight = FontWeight.Medium,
            maxLines = 1
        )
    }
}

@Composable
private fun RemoteSelectionIndicator(isSelected: Boolean) {
    LumiCheckbox(checked = isSelected, onCheckedChange = null, size = 20.dp)
}

@Composable
internal fun RemoteHeaderPill(text: String, onClick: () -> Unit) {
    LiquidGlassButton(
        onClick = onClick,
        modifier = Modifier.heightIn(min = 44.dp),
        shape = AppRoundedCornerShape(AppRadius.full),
        tintedColor = AppColors.BgGray,
        contentColor = AppColors.TextPrimary
    ) {
        Text(
            text = text,
            color = AppColors.TextPrimary,
            fontSize = AppType.BodySmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** Full-width primary action; matches the import sheet's confirm button in every theme. */
@Composable
internal fun RemotePrimaryButton(
    text: String,
    enabled: Boolean,
    floating: Boolean,
    onClick: () -> Unit,
    leading: (@Composable () -> Unit)? = null
) {
    val shape = AppRoundedCornerShape(AppRadius.lg)
    LiquidGlassButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 58.dp)
            .then(
                if (floating && enabled && LocalAppTheme.current != "liquid_glass") {
                    Modifier.shadow(
                        elevation = 28.dp, shape = shape, clip = false,
                        ambientColor = Color.Black.copy(alpha = 0.10f),
                        spotColor = Color.Black.copy(alpha = 0.08f)
                    )
                } else {
                    Modifier
                }
            ),
        shape = shape,
        tintedColor = if (enabled) AppColors.Accent else AppColors.BgGray,
        prominentShadow = enabled,
        contentColor = if (enabled) AppColors.OnAccent else AppColors.TextSecondary
    ) {
        leading?.let {
            it()
            Spacer(Modifier.width(AppSpace.sm))
        }
        Text(
            text = text,
            color = if (enabled) AppColors.OnAccent else AppColors.TextSecondary,
            fontSize = AppType.Body,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
internal fun RemoteBrowserHeader(
    path: String,
    summary: String,
    searchAvailable: Boolean,
    searchQuery: String?,
    loading: Boolean,
    layoutMode: Int,
    sortMode: RemoteSortMode,
    sortAscending: Boolean,
    onSearchOpen: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onSearchSubmit: () -> Unit,
    onSearchClose: () -> Unit,
    onRefresh: () -> Unit,
    onSortModeChange: (RemoteSortMode) -> Unit,
    onSortAscendingChange: (Boolean) -> Unit,
    onLayoutModeChange: () -> Unit
) {
    BookPickerHeader(
        path = path,
        levelSummary = summary,
        isScanning = loading,
        isSearching = searchQuery != null,
        searchQuery = searchQuery.orEmpty(),
        layoutMode = layoutMode,
        onRefresh = onRefresh,
        onSearchOpen = onSearchOpen,
        onSearchQueryChange = onSearchQueryChange,
        onLayoutModeChange = onLayoutModeChange,
        sortControl = {
            RemoteSortButton(sortMode, sortAscending, onSortModeChange, onSortAscendingChange)
        },
        searchPlaceholder = stringResource(
            if (searchAvailable) R.string.remote_library_search_placeholder else R.string.import_search_placeholder
        ),
        searchTag = "remote-search-pill",
        onSearchSubmit = onSearchSubmit
    )
}
