package com.huangder.lumibooks.ui.home.remote

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
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
 * Catalog browser laid out like the folder books page: header with search pill and refresh,
 * a summary row with select-all / sort / layout, list or 2–3 column grid, and the same
 * fade-and-slide level transition when opening or leaving a folder.
 */
@Composable
internal fun RemoteBrowserScreen(
    levels: List<RemoteLevel>,
    searchAvailable: Boolean,
    searchQuery: String?,
    selectionMode: Boolean,
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
    onStartSelection: (RemoteBookItem) -> Unit,
    onSelectAll: () -> Unit,
    onExitSelection: () -> Unit,
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
    // One capture of the scrolling content: the floating button refracts it in liquid glass and
    // the bottom progressive blur samples it in every theme.
    val backdrop = contentBackdrop ?: rememberLayerBackdrop()
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
    val books = remember(page?.books, sortMode, sortAscending) {
        sortRemoteBooks(page?.books.orEmpty(), sortMode, sortAscending)
    }
    val folders = page?.folders.orEmpty()
    val breadcrumb = levels.takeIf { it.size > 1 }?.joinToString(" › ") { it.title }.orEmpty()
    val summary = when {
        page == null && level.loading -> stringResource(R.string.remote_library_loading)
        page == null -> ""
        selectionMode -> stringResource(R.string.remote_library_selected_count, selection.size)
        else -> stringResource(R.string.remote_library_level_summary, folders.size, books.size)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            RemoteBrowserHeader(
                title = level.title,
                path = breadcrumb,
                summary = summary,
                searchAvailable = searchAvailable,
                searchQuery = searchQuery,
                loading = level.loading,
                showSelectAll = books.isNotEmpty(),
                selectionMode = selectionMode,
                layoutMode = layoutMode,
                sortMode = sortMode,
                sortAscending = sortAscending,
                onBack = onBack,
                onSearchOpen = onSearchOpen,
                onSearchQueryChange = onSearchQueryChange,
                onSearchSubmit = onSearchSubmit,
                onSearchClose = onSearchClose,
                onRefresh = onRefresh,
                onSelectAll = onSelectAll,
                onExitSelection = onExitSelection,
                onSortModeChange = onSortModeChange,
                onSortAscendingChange = onSortAscendingChange,
                onLayoutModeChange = onLayoutModeChange
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .graphicsLayer {
                        this.alpha = alpha.value
                        translationY = offset.value.dp.toPx()
                    }
                    .layerBackdrop(backdrop)
            ) {
                when {
                    page == null && level.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = AppColors.Accent, strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
                    }
                    page == null -> RemoteEmptyState(
                        message = stringResource((level.error ?: RemoteErrorKind.UNKNOWN).messageRes()),
                        actionLabel = stringResource(R.string.retry),
                        onAction = onRetry,
                        modifier = Modifier.padding(horizontal = AppSpace.lg)
                    )
                    folders.isEmpty() && books.isEmpty() -> RemoteEmptyState(
                        message = stringResource(
                            if (level.url.startsWith("search:")) R.string.search_no_results else R.string.remote_library_empty_folder
                        )
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
                            folders = folders,
                            books = books,
                            layoutMode = mode,
                            hasMore = page.nextUrl != null,
                            loadingMore = level.loadingMore,
                            selectionMode = selectionMode,
                            selection = selection,
                            importedKeys = importedKeys,
                            downloadStates = downloadStates,
                            onOpenFolder = onOpenFolder,
                            onOpenBook = onOpenBook,
                            onToggleBook = onToggleBook,
                            onStartSelection = onStartSelection,
                            onLoadMore = onLoadMore
                        )
                    }
                }
            }
        }

        if (selectionMode) {
            RemoteBottomBar(blurBackdrop = backdrop) {
                // Only the floating button refracts the captured list (backdrop ownership rule).
                ProvideLiquidGlassBackdrop(backdrop.takeIf { isLiquidGlass && contentBackdrop != null }) {
                    RemotePrimaryButton(
                        text = stringResource(R.string.remote_library_download_selected, selection.size),
                        enabled = selection.isNotEmpty(),
                        floating = true,
                        onClick = onDownloadSelected
                    )
                }
            }
        }
    }
}

@Composable
private fun RemoteLevelContent(
    folders: List<RemoteFolder>,
    books: List<RemoteBookItem>,
    layoutMode: Int,
    hasMore: Boolean,
    loadingMore: Boolean,
    selectionMode: Boolean,
    selection: Set<String>,
    importedKeys: Set<String>,
    downloadStates: Map<String, RemoteDownloadState>,
    onOpenFolder: (RemoteFolder) -> Unit,
    onOpenBook: (RemoteBookItem) -> Unit,
    onToggleBook: (RemoteBookItem) -> Unit,
    onStartSelection: (RemoteBookItem) -> Unit,
    onLoadMore: () -> Unit
) {
    val padding = PaddingValues(
        start = AppSpace.lg,
        end = AppSpace.lg,
        top = AppSpace.xs,
        bottom = RemoteBottomBarContentPadding
    )
    val bookClick: (RemoteBookItem) -> Unit = { if (selectionMode) onToggleBook(it) else onOpenBook(it) }
    if (layoutMode == 1) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = padding,
            verticalArrangement = Arrangement.spacedBy(AppSpace.sm)
        ) {
            items(folders, key = { "folder:${it.url}" }) { folder ->
                RemoteFolderRow(folder = folder, enabled = !selectionMode, onClick = { onOpenFolder(folder) })
            }
            items(books, key = { "book:${it.key}" }) { book ->
                RemoteBookRow(
                    book = book,
                    selectionMode = selectionMode,
                    selected = book.key in selection,
                    imported = book.key in importedKeys,
                    downloadState = downloadStates[book.key],
                    onClick = { bookClick(book) },
                    onLongClick = { onStartSelection(book) }
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
            RemoteFolderTile(folder = folder, compact = layoutMode == 3, enabled = !selectionMode, onClick = { onOpenFolder(folder) })
        }
        items(books, key = { "book:${it.key}" }) { book ->
            RemoteBookTile(
                book = book,
                compact = layoutMode == 3,
                selectionMode = selectionMode,
                selected = book.key in selection,
                imported = book.key in importedKeys,
                downloadState = downloadStates[book.key],
                onClick = { bookClick(book) },
                onLongClick = { onStartSelection(book) }
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
    selectionMode: Boolean,
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
                role = if (selectionMode) Role.Checkbox else Role.Button
            )
            .semantics { if (selectionMode) this.selected = selected }
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
        if (selectionMode) {
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
    selectionMode: Boolean,
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
                role = if (selectionMode) Role.Checkbox else Role.Button
            )
            .semantics { if (selectionMode) this.selected = selected }
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
            if (selectionMode) {
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
    Box(
        modifier = Modifier
            .size(20.dp)
            .clip(CircleShape)
            .then(
                if (isSelected) Modifier.background(AppColors.Accent)
                else Modifier
                    .background(AppColors.CardBg.copy(alpha = 0.7f))
                    .border(1.5.dp, AppColors.TextSecondary.copy(alpha = 0.6f), CircleShape)
            ),
        contentAlignment = Alignment.Center
    ) {
        if (isSelected) {
            Icon(AppIcons.Check, contentDescription = null, tint = AppColors.OnAccent, modifier = Modifier.size(13.dp))
        }
    }
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
private fun RemoteBrowserHeader(
    title: String,
    path: String,
    summary: String,
    searchAvailable: Boolean,
    searchQuery: String?,
    loading: Boolean,
    showSelectAll: Boolean,
    selectionMode: Boolean,
    layoutMode: Int,
    sortMode: RemoteSortMode,
    sortAscending: Boolean,
    onBack: () -> Unit,
    onSearchOpen: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onSearchSubmit: () -> Unit,
    onSearchClose: () -> Unit,
    onRefresh: () -> Unit,
    onSelectAll: () -> Unit,
    onExitSelection: () -> Unit,
    onSortModeChange: (RemoteSortMode) -> Unit,
    onSortAscendingChange: (Boolean) -> Unit,
    onLayoutModeChange: () -> Unit
) {
    val searching = searchQuery != null
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(top = AppSpace.sm)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AppSpace.lg),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RemoteHeaderIconButton(
                icon = directionalIcon(AppIcons.ArrowLeft, AppIcons.ArrowRight),
                description = stringResource(R.string.back),
                onClick = if (searching) onSearchClose else onBack
            )
            Spacer(Modifier.width(AppSpace.sm))
            RemoteSearchHeaderSlot(
                title = title,
                path = path,
                searchAvailable = searchAvailable,
                query = searchQuery,
                onExpand = onSearchOpen,
                onQueryChange = onSearchQueryChange,
                onSubmit = onSearchSubmit,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(AppSpace.sm))
            if (loading) {
                Box(modifier = Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), color = AppColors.Accent, strokeWidth = 2.dp)
                }
            } else {
                RemoteHeaderIconButton(
                    icon = AppIcons.ArrowClockwise,
                    description = stringResource(R.string.import_folder_books_refresh),
                    onClick = onRefresh
                )
            }
        }
        Spacer(Modifier.height(AppSpace.sm))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AppSpace.lg),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = summary,
                color = AppColors.TextSecondary,
                fontSize = AppType.Caption,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (selectionMode) {
                Spacer(Modifier.width(AppSpace.sm))
                RemoteHeaderPill(stringResource(R.string.done), onExitSelection)
            }
            if (showSelectAll) {
                Spacer(Modifier.width(AppSpace.sm))
                RemoteAccentPill(stringResource(R.string.import_select_all), onSelectAll)
            }
            Spacer(Modifier.width(AppSpace.sm))
            RemoteSortButton(
                mode = sortMode,
                ascending = sortAscending,
                onModeChange = onSortModeChange,
                onAscendingChange = onSortAscendingChange
            )
            Spacer(Modifier.width(AppSpace.sm))
            RemoteLayoutButton(layoutMode = layoutMode, onClick = onLayoutModeChange)
        }
        Spacer(Modifier.height(AppSpace.sm))
    }
}

/**
 * Title area that turns into a search field, with the folder books page's spring: the pill
 * grows from its right edge over the title, overshoots slightly and settles.
 */
@Composable
private fun RemoteSearchHeaderSlot(
    title: String,
    path: String,
    searchAvailable: Boolean,
    query: String?,
    onExpand: () -> Unit,
    onQueryChange: (String) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val motionEnabled = LocalMotionEnabled.current
    val eInkMode = LocalEInkMode.current
    val searching = query != null
    androidx.compose.foundation.layout.BoxWithConstraints(modifier = modifier) {
        val collapsedWidth = 44.dp
        val expandedWidth = maxWidth
        val rawExpansion by androidx.compose.animation.core.animateFloatAsState(
            targetValue = if (searching) 1f else 0f,
            animationSpec = when {
                !motionEnabled || eInkMode -> tween(120)
                searching -> com.huangder.lumibooks.ui.animation.VisualMotion.spring(dampingRatio = 0.45f, stiffness = 280f)
                else -> com.huangder.lumibooks.ui.animation.VisualMotion.spring(dampingRatio = 1f, stiffness = 320f)
            },
            label = "remoteSearchExpansion"
        )
        val expansion = rawExpansion.coerceIn(0f, 1f)
        val overshoot = (rawExpansion - 1f).coerceAtLeast(0f)
        Column(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .fillMaxWidth()
                .padding(end = if (searchAvailable) collapsedWidth + AppSpace.sm else 0.dp)
                .graphicsLayer { alpha = 1f - expansion }
        ) {
            Text(
                text = title,
                color = AppColors.TextPrimary,
                fontSize = AppType.Section,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (path.isNotBlank()) {
                Text(
                    text = path,
                    color = AppColors.TextSecondary,
                    fontSize = AppType.Caption,
                    maxLines = 1,
                    overflow = TextOverflow.StartEllipsis
                )
            }
        }
        if (searchAvailable) {
            RemoteSearchPill(
                query = query.orEmpty(),
                expanded = searching,
                contentAlpha = expansion,
                onQueryChange = onQueryChange,
                onExpand = onExpand,
                onSubmit = onSubmit,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .width(collapsedWidth + (expandedWidth - collapsedWidth) * expansion)
                    .height(44.dp)
                    .graphicsLayer {
                        scaleX = 1f + overshoot * 0.5f
                        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(1f, 0.5f)
                    }
            )
        }
    }
}

@Composable
private fun RemoteSearchPill(
    query: String,
    expanded: Boolean,
    contentAlpha: Float,
    onQueryChange: (String) -> Unit,
    onExpand: () -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val keyboardController = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val focusRequester = remember { androidx.compose.ui.focus.FocusRequester() }
    LaunchedEffect(expanded) {
        if (expanded) {
            runCatching { focusRequester.requestFocus() }
            keyboardController?.show()
        } else {
            keyboardController?.hide()
        }
    }
    com.huangder.lumibooks.ui.components.LiquidGlassSurface(
        controlEdge = true,
        shape = CircleShape,
        fallbackColor = AppColors.BgGray,
        contentScrimColor = AppColors.CardBg.copy(alpha = 0.58f),
        onClick = if (expanded) null else onExpand,
        effectPadding = 1.dp,
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        if (expanded) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = contentAlpha }
                    .padding(start = AppSpace.md, end = AppSpace.xs),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(AppIcons.MagnifyingGlass, contentDescription = null, tint = AppColors.TextSecondary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(AppSpace.sm))
                androidx.compose.foundation.text.BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester),
                    singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(
                        color = AppColors.TextPrimary,
                        fontSize = AppType.BodySmall,
                        fontWeight = FontWeight.Medium
                    ),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(AppColors.Accent),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        imeAction = androidx.compose.ui.text.input.ImeAction.Search
                    ),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = {
                        keyboardController?.hide()
                        onSubmit()
                    }),
                    decorationBox = { inner ->
                        Box {
                            if (query.isEmpty()) {
                                Text(
                                    text = stringResource(R.string.remote_library_search_placeholder),
                                    color = AppColors.TextSecondary,
                                    fontSize = AppType.BodySmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            inner()
                        }
                    }
                )
            }
        } else {
            Icon(
                imageVector = AppIcons.MagnifyingGlass,
                contentDescription = stringResource(R.string.search),
                tint = AppColors.TextPrimary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
