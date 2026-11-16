package com.huangder.lumibooks.ui.bookshelf

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import com.huangder.lumibooks.ui.animation.VisualMotion.spring
import com.huangder.lumibooks.ui.animation.VisualMotion.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.huangder.lumibooks.R
import com.huangder.lumibooks.data.sync.BookDownloadState
import com.huangder.lumibooks.domain.model.Book
import com.huangder.lumibooks.domain.model.LibraryFolder
import com.huangder.lumibooks.ui.animation.BookReaderTransitionPhase
import com.huangder.lumibooks.ui.animation.LocalBookReaderAnchorScope
import com.huangder.lumibooks.ui.animation.coverFlowEntranceItem
import com.huangder.lumibooks.ui.components.ConfigurableBackHandler
import com.huangder.lumibooks.ui.components.BookProgressStatus
import com.huangder.lumibooks.ui.components.ProvideLiquidGlassBackdrop
import com.huangder.lumibooks.ui.icons.AppIcons
import com.huangder.lumibooks.ui.theme.AppColors
import com.huangder.lumibooks.ui.theme.LocalAppTheme
import com.huangder.lumibooks.ui.theme.LocalEInkMode
import com.huangder.lumibooks.ui.theme.LocalMotionEnabled
import com.huangder.lumibooks.ui.theme.AppType
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs
import kotlin.math.floor
import com.huangder.lumibooks.ui.components.EmptyState
import com.huangder.lumibooks.ui.components.AmbientGlow
import com.huangder.lumibooks.ui.components.shiftHue
import com.huangder.lumibooks.ui.components.rememberCoverColors
import com.huangder.lumibooks.ui.components.FinishedReadingIndicator
import com.huangder.lumibooks.ui.components.formatReadingProgressPercent
import com.huangder.lumibooks.ui.theme.AppRadius
import com.huangder.lumibooks.ui.theme.KaiTi
import com.huangder.lumibooks.ui.theme.LocalCoverReadingProgressEnabled
import com.huangder.lumibooks.ui.theme.LocalIsDarkTheme
import com.huangder.lumibooks.ui.theme.resolveAppFontFamily
import androidx.compose.foundation.border
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap

@Composable
internal fun CoverFlowBookshelf(
    books: List<Book>,
    folders: List<LibraryFolder>,
    folderBookCounts: Map<String, Int>,
    tagNamesByBook: Map<String, List<String>>,
    isLoading: Boolean,
    isEditing: Boolean,
    selectedBookIds: Set<String>,
    deletingBookIds: Set<String>,
    syncedBookIds: Set<String>,
    downloadStates: Map<String, BookDownloadState>,
    topPadding: Dp,
    bottomPadding: Dp,
    showAddBook: Boolean,
    enabled: Boolean,
    actions: CoverFlowBookActions,
    onMenuVisibleChange: (Boolean) -> Unit,
    onHaptic: () -> Unit,
    onSelectionToggle: (Book) -> Unit,
    onAddBook: () -> Unit,
    onFolderClick: (LibraryFolder) -> Unit,
    onFolderRename: (LibraryFolder) -> Unit,
    onFolderDelete: (LibraryFolder) -> Unit,
    onFolderSetCover: (LibraryFolder) -> Unit,
    onFolderRemoveCover: (LibraryFolder) -> Unit,
    onFolderMove: (LibraryFolder) -> Unit,
    modifier: Modifier = Modifier,
    pickerMode: Boolean = false,
    /** Commits a drag on the stage with the new order of [books]' ids; null turns dragging off. */
    onReorder: ((List<String>) -> Unit)? = null
) {
    val state = rememberSaveable(saver = CoverFlowState.Saver) { CoverFlowState() }
    val motionEnabled = LocalMotionEnabled.current && !LocalEInkMode.current
    val isLiquidGlass = LocalAppTheme.current == "liquid_glass" && !LocalEInkMode.current
    val controlsBackdrop = rememberLayerBackdrop()
    val sceneLayout = remember { CoverFlowSceneLayout() }
    val bookIds = remember(books) { books.map { it.id } }
    val progress = remember { Animatable(if (isEditing) 1f else 0f) }
    val listState = rememberLazyListState()
    val readerTransition = LocalBookReaderAnchorScope.current?.transitionState
    val closingBookId = readerTransition?.activeBookId?.takeIf { readerTransition.phase == BookReaderTransitionPhase.Closing }
    val libraryReady = readerTransition == null || readerTransition.phase == BookReaderTransitionPhase.Library
    var listMounted by remember { mutableStateOf(isEditing) }
    var transferring by remember { mutableStateOf(false) }
    var heroBookId by remember { mutableStateOf<String?>(null) }
    var heroSource by remember { mutableStateOf<CoverFlowHero?>(null) }
    var listCoverBounds by remember { mutableStateOf(Rect.Zero) }
    val rootCoordinates = remember { arrayOfNulls<LayoutCoordinates>(1) }
    val currentMenuCallback by rememberUpdatedState(onMenuVisibleChange)
    val currentEditing by rememberUpdatedState(isEditing)
    val density = LocalDensity.current

    // Light-field colours follow the focused cover, but only once the flow settles: a fast fling
    // across a large library never decodes a palette per book it passes.
    val eInk = LocalEInkMode.current
    var lightCoverPath by remember { mutableStateOf(books.getOrNull(state.focusedIndex)?.coverPath) }
    val currentBooks by rememberUpdatedState(books)
    LaunchedEffect(state, eInk) {
        if (eInk) return@LaunchedEffect
        snapshotFlow { if (state.isMoving) null else currentBooks.getOrNull(state.focusedIndex)?.let { it.id to it.coverPath } }
            .collect { settled -> if (settled != null) lightCoverPath = settled.second }
    }
    // rememberCoverColors maps a fresh list on each call; keep one instance per distinct palette
    // so the stage and its atmosphere skip recomposition unless the colours really change.
    val pickedColors = rememberCoverColors(lightCoverPath)
    val lightColors = remember(pickedColors) { pickedColors }
    val ribbonLight = androidx.compose.animation.animateColorAsState(lightColors.first(), tween(700), label = "coverFlowRibbon")

    LaunchedEffect(bookIds) { state.updateBooks(bookIds) }
    LaunchedEffect(closingBookId, bookIds, topPadding, bottomPadding) {
        closingBookId?.let {
            state.updateBooks(bookIds)
            state.restoreBook(it)
            withFrameNanos { }
            withFrameNanos { }
            while (readerTransition.phase == BookReaderTransitionPhase.Closing) {
                sceneLayout.hero(state.focusedIndex, state.position)?.bounds?.let { bounds ->
                    readerTransition.updateCoverFlowReturnSource(it, bounds)
                }
                withFrameNanos { }
            }
        }
    }
    LaunchedEffect(enabled, libraryReady) { if (!enabled || !libraryReady) state.interrupt() }
    LaunchedEffect(pickerMode) { if (pickerMode) state.dismissMenu() }
    LaunchedEffect(state.menuBookId, pickerMode) { currentMenuCallback(!pickerMode && state.menuBookId != null) }
    DisposableEffect(state) {
        onDispose { state.interrupt(); currentMenuCallback(false) }
    }
    ConfigurableBackHandler(state.menuBookId != null) { state.dismissMenu() }

    // The list and scene coexist only as needed. Their duplicate hero covers are hidden while a
    // single GPU layer flies between actual root-coordinate anchors. Reversal keeps the progress.
    LaunchedEffect(isEditing, bookIds) {
        state.updateBooks(bookIds)
        if (!isEditing && !listMounted) return@LaunchedEffect
        state.interrupt()
        if (books.isEmpty()) {
            heroBookId = null
            heroSource = null
            progress.snapTo(if (isEditing) 1f else 0f)
            listMounted = isEditing
            transferring = false
            return@LaunchedEffect
        }
        val returningId = heroBookId?.takeIf { it in bookIds } ?: state.focusedId ?: books.first().id
        if (isEditing && !listMounted) {
            heroBookId = state.focusedId ?: books[state.focusedIndex].id
            heroSource = sceneLayout.hero(state.focusedIndex, state.position)
            listCoverBounds = Rect.Zero
            transferring = true
            listMounted = true
            listState.scrollToItem(bookIds.indexOf(heroBookId).coerceAtLeast(0))
        } else {
            if (heroBookId != returningId) listCoverBounds = Rect.Zero
            heroBookId = returningId
            if (!isEditing) {
                state.restoreBook(returningId)
                val index = bookIds.indexOf(returningId)
                if (progress.value >= 0.999f) {
                    // Keep the list visible during this scroll, then reverse the same cover flight.
                    val row = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == returningId }
                    if (row == null || row.offset < listState.layoutInfo.viewportStartOffset) {
                        listState.animateScrollToItem(index.coerceAtLeast(0))
                    }
                    withFrameNanos { }
                    heroSource = sceneLayout.hero(index, state.position)
                }
            }
            transferring = true
        }
        withTimeoutOrNull(1000) { snapshotFlow { listCoverBounds }.first { it.width > 0f && it.height > 0f } }
        if (heroSource == null) heroSource = sceneLayout.hero(state.focusedIndex, state.position)
        progress.animateTo(
            if (isEditing) 1f else 0f,
            if (motionEnabled) spring(dampingRatio = 1f, stiffness = 240f, visibilityThreshold = 0.001f) else tween(100)
        )
        transferring = false
        if (!isEditing) listMounted = false
    }

    // This whole bookshelf is recorded by the outer bookshelf backdrop. Sampling that
    // backdrop from a folder/action surface here would make RenderNode traversal cyclic
    // (a native RenderThread stack overflow on HyperOS). Controls sample an opaque, full-size
    // sibling background. A stage-only capture does not cover the folder row and produces
    // rectangular sampling artifacts inside its glass buttons.
    ProvideLiquidGlassBackdrop(controlsBackdrop.takeIf { isLiquidGlass }) {
    Box(modifier.fillMaxSize().testTag("cover_flow_bookshelf")
        .onGloballyPositioned { rootCoordinates[0] = it }) {
        if (isLiquidGlass) {
            Box(Modifier.matchParentSize()
                .layerBackdrop(controlsBackdrop)
                .background(AppColors.PageBg)) {
                val accent = AppColors.Accent
                AmbientGlow(
                    remember(accent) { listOf(accent, accent.shiftHue(32f), accent.shiftHue(-28f)) },
                    intensity = .55f, height = 360.dp
                )
            }
        }
        Column(Modifier.fillMaxSize().padding(top = topPadding, bottom = bottomPadding)) {
            CoverFlowFolderNavigation(
                folders, folderBookCounts, onFolderClick, onFolderRename, onFolderDelete,
                onFolderSetCover, onFolderRemoveCover, onFolderMove, showAddBook && !isLoading,
                onAddBook, enabled && !isEditing && !transferring, onHaptic
            )
            if (books.isEmpty()) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    if (isLoading) CircularProgressIndicator(color = AppColors.Accent)
                    else EmptyState(message = stringResource(R.string.no_books), icon = AppIcons.Books)
                }
            } else {
                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                    val landscape = maxWidth >= 520.dp && maxHeight < 400.dp
                    val stage: @Composable (Modifier) -> Unit = { stageModifier ->
                        ProvideLiquidGlassBackdrop(null) {
                        CoverFlowScene(
                            books, state, sceneLayout, motionEnabled,
                            enabled && libraryReady && !isEditing && !transferring,
                            heroBookId.takeIf { transferring && heroSource != null && motionEnabled },
                            downloadStates, deletingBookIds,
                            onOpen = { book, bounds ->
                                state.dismissMenu()
                                actions.open(book, bounds ?: sceneLayout.hero(state.focusedIndex, state.position)?.bounds)
                            },
                            onHaptic = onHaptic,
                            menusEnabled = !pickerMode,
                            modifier = stageModifier,
                            sceneAlpha = { 1f - progress.value },
                            lightColors = lightColors,
                            onReorder = onReorder.takeIf { !pickerMode }
                        )
                        }
                    }
                    val details: @Composable (Modifier) -> Unit = { detailsModifier ->
                        Column(detailsModifier.coverFlowEntranceItem(5).graphicsLayer {
                            val departure = if (readerTransition?.activeBookId != null &&
                                readerTransition.presentation == com.huangder.lumibooks.ui.animation.BookReaderPresentation.CoverFlow) {
                                com.huangder.lumibooks.ui.animation.CoverFlowReaderMotion.sideExit(readerTransition.coverFlowProgressSnapshot.value)
                            } else 0f
                            alpha = (1f - progress.value) * (1f - departure)
                        },
                            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                            CoverFlowPaperLabel(books, state, syncedBookIds, downloadStates, { ribbonLight.value },
                                Modifier.fillMaxWidth().height((112f * density.fontScale.coerceIn(1f, 1.6f)).dp))
                            Box(Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 12.dp), contentAlignment = Alignment.TopCenter) {
                                CoverFlowScrollbar(
                                    state = state,
                                    bookCount = books.size,
                                    enabled = enabled && libraryReady && !isEditing && !transferring,
                                    motionEnabled = motionEnabled,
                                    modifier = Modifier.widthIn(max = 360.dp).fillMaxWidth().padding(horizontal = 20.dp),
                                    thumbColor = { ribbonLight.value }.takeIf { !eInk }
                                )
                                val menuBook = books.firstOrNull { it.id == state.menuBookId }
                                androidx.compose.animation.AnimatedVisibility(!pickerMode && menuBook != null && !isEditing && enabled,
                                    enter = fadeIn(tween(110)) + scaleIn(initialScale = if (motionEnabled) 0.92f else 1f,
                                        animationSpec = spring(dampingRatio = 0.9f, stiffness = 450f)),
                                    exit = fadeOut(tween(90)) + scaleOut(spring(stiffness = 400f), targetScale = if (motionEnabled) 0.96f else 1f)) {
                                    // Keep the outgoing content until AnimatedVisibility finishes its exit.
                                    val displayed = remember { menuBook ?: books[state.focusedIndex.coerceIn(books.indices)] }
                                    val current = books.firstOrNull { it.id == displayed.id } ?: displayed
                                    CoverFlowActionBar(current, actions,
                                        onOpen = {
                                            state.dismissMenu()
                                            actions.open(current, sceneLayout.coverBounds(current.id)
                                                ?: sceneLayout.hero(state.focusedIndex, state.position)?.bounds)
                                        },
                                        onDismiss = state::dismissMenu)
                                }
                            }
                        }
                    }
                    if (landscape) Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                        stage(Modifier.weight(0.62f).fillMaxHeight())
                        details(Modifier.weight(0.38f))
                    } else Column(Modifier.fillMaxSize()) {
                        stage(Modifier.weight(1f).fillMaxWidth())
                        details(Modifier.fillMaxWidth())
                    }
                }
            }
        }
        if (listMounted && !pickerMode) {
            // Animated glass capture nested in the flying cover's parent is deliberately avoided.
            ProvideLiquidGlassBackdrop(null) {
                LazyColumn(state = listState,
                    modifier = Modifier.fillMaxSize().testTag("cover_flow_edit_list")
                        .graphicsLayer { alpha = progress.value }
                        .then(if (transferring) Modifier.clearAndSetSemantics { } else Modifier),
                    userScrollEnabled = isEditing && !transferring,
                    contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = topPadding + 12.dp, bottom = bottomPadding),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(books, key = { it.id }) { book ->
                        BookshelfSearchResultItem(
                            book = book, tagNames = tagNamesByBook[book.id].orEmpty(), expanded = false,
                            isDeleting = book.id in deletingBookIds, isSynced = book.id in syncedBookIds,
                            downloadState = downloadStates[book.id],
                            onExpandedChange = {}, onClick = {},
                            onEditInfo = { actions.editInfo(book) }, onBookDetails = { actions.details(book) },
                            onMoveToFolder = { actions.move(book) }, onDelete = { actions.delete(book) },
                            onFavorite = { actions.favorite(book) }, onCustomCover = { actions.customCover(book) },
                            onRemoveCustomCover = { actions.removeCustomCover(book) }, onTags = { actions.tags(book) },
                            onBookmarksNotes = { actions.notes(book) }, selectionMode = true,
                            selected = book.id in selectedBookIds,
                            onSelectionToggle = { if (currentEditing && !transferring) onSelectionToggle(book) },
                            selectionScaleEnabled = false,
                            coverModifier = Modifier.onGloballyPositioned {
                                if (book.id == heroBookId) listCoverBounds = it.boundsInRoot()
                            }.graphicsLayer {
                                alpha = if (transferring && book.id == heroBookId && heroSource != null && motionEnabled) 0f else 1f
                            }
                        )
                    }
                }
            }
        }
        val hero = books.firstOrNull { it.id == heroBookId }
        val source = heroSource
        if (transferring && motionEnabled && hero != null && source != null) {
            val heroShape = com.huangder.lumibooks.ui.theme.AppRoundedCornerShape(2.dp)
            val width = with(density) { source.bounds.width.toDp() }
            val height = with(density) { source.bounds.height.toDp() }
            CoverFlowCover(hero, Modifier.size(width, height)
                .clearAndSetSemantics { }
                .graphicsLayer {
                    val p = progress.value.coerceIn(0f, 1f)
                    val target = listCoverBounds.takeIf { it.width > 0f && it.height > 0f } ?: source.bounds
                    val origin = rootCoordinates[0]?.takeIf { it.isAttached }?.localToRoot(Offset.Zero) ?: Offset.Zero
                    transformOrigin = TransformOrigin.Center
                    val centerX = source.bounds.center.x + (target.center.x - source.bounds.center.x) * p
                    val centerY = source.bounds.center.y + (target.center.y - source.bounds.center.y) * p
                    translationX = centerX - source.bounds.width / 2f - origin.x
                    translationY = centerY - source.bounds.height / 2f - origin.y
                    scaleX = (source.bounds.width + (target.width - source.bounds.width) * p) / source.bounds.width
                    scaleY = (source.bounds.height + (target.height - source.bounds.height) * p) / source.bounds.height
                    rotationY = source.rotation * (1f - p)
                    cameraDistance = source.camera
                    shape = heroShape.copy(androidx.compose.foundation.shape.CornerSize((2f + 12f * p).dp / scaleX))
                    clip = true
                })
        }
    }
    }
}

/**
 * A paper label for the centred book. The card itself stays put; only its text crossfades
 * between neighbours (layer alpha), and the ribbon/progress colours are read in draw.
 */
@Composable
private fun CoverFlowPaperLabel(
    books: List<Book>,
    state: CoverFlowState,
    syncedIds: Set<String>,
    downloadStates: Map<String, BookDownloadState>,
    light: () -> Color,
    modifier: Modifier
) {
    val first by remember(state) { derivedStateOf { floor(state.position).toInt() } }
    val dark = LocalIsDarkTheme.current
    val eInk = LocalEInkMode.current
    val shape = com.huangder.lumibooks.ui.theme.AppRoundedCornerShape(AppRadius.md)
    val paper = if (eInk) Color.White else coverFlowPaperColor(AppColors.CardBg, dark)
    val accent = AppColors.Accent
    val titleFont = resolveAppFontFamily(KaiTi)
    Box(modifier.clearAndSetSemantics { }.padding(top = 6.dp, bottom = 4.dp), contentAlignment = Alignment.TopCenter) {
        Box(Modifier.widthIn(max = 340.dp).fillMaxWidth().fillMaxHeight().padding(horizontal = 28.dp)) {
            Box(Modifier.matchParentSize()
                .then(if (eInk) Modifier.border(1.dp, Color.Black, shape)
                    else Modifier.shadow(6.dp, shape, clip = false,
                        ambientColor = Color.Black.copy(alpha = if (dark) 0.30f else 0.06f),
                        spotColor = Color.Black.copy(alpha = if (dark) 0.40f else 0.10f)))
                .clip(shape).background(paper)
                .then(if (eInk) Modifier else Modifier.coverFlowPaperSheen(dark)
                    .border(0.5.dp, AppColors.Divider.copy(alpha = if (dark) 0.5f else 0.7f), shape)))
            (first..first + 1).forEach { index ->
                val book = books.getOrNull(index) ?: return@forEach
                key(book.id) {
                    Box(Modifier.matchParentSize()
                        .graphicsLayer { alpha = (1f - abs(index - state.position)).coerceIn(0f, 1f) }) {
                        CoverFlowLabelContent(book, book.id in syncedIds, downloadStates[book.id], light, titleFont)
                        // The bookmark ribbon hangs over the label's top edge.
                        val favorite = book.isFavorite
                        Box(Modifier.align(Alignment.TopEnd).padding(end = 18.dp).offset(y = (-4).dp)
                            .size(11.dp, 26.dp)
                            .drawWithCache {
                                val path = ribbonPath(size.width, size.height)
                                val fold = Color.Black.copy(alpha = 0.18f)
                                onDrawBehind {
                                    val c = if (eInk) Color.Black else if (favorite) accent else light()
                                    drawPath(path, c)
                                    drawLine(fold, Offset(0f, 4.dp.toPx()), Offset(size.width, 4.dp.toPx()), 1f)
                                }
                            }, contentAlignment = Alignment.TopCenter) {
                            if (favorite) Icon(AppIcons.Heart.filled, null, tint = Color.White.copy(alpha = 0.92f),
                                modifier = Modifier.padding(top = 7.dp).size(7.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CoverFlowLabelContent(
    book: Book,
    synced: Boolean,
    downloadState: BookDownloadState?,
    light: () -> Color,
    titleFont: FontFamily
) {
    val secondary = AppColors.TextSecondary
    Column(Modifier.fillMaxSize().padding(horizontal = 36.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text(book.title, color = AppColors.TextPrimary, fontFamily = titleFont, fontWeight = FontWeight.Normal,
            fontSize = AppType.Headline, lineHeight = 22.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center)
        Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(12.dp, 1.dp).background(secondary.copy(alpha = 0.35f)))
            Text(book.author, color = secondary, fontSize = AppType.Caption, letterSpacing = 0.8.sp,
                fontFamily = FontFamily.Serif, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 6.dp).weight(1f, fill = false))
            Box(Modifier.size(12.dp, 1.dp).background(secondary.copy(alpha = 0.35f)))
            if (synced) Icon(AppIcons.CloudFilled, null, tint = secondary.copy(alpha = 0.7f),
                modifier = Modifier.padding(start = 6.dp).size(11.dp))
        }
        val downloading = downloadState is BookDownloadState.Downloading
        val ribbon = !LocalCoverReadingProgressEnabled.current && !book.isCloudOnly && !downloading
        if (ribbon) {
            CoverFlowProgressRibbon(book, light, Modifier.padding(top = 9.dp).widthIn(max = 200.dp))
        } else {
            BookProgressStatus(book = book, downloadState = downloadState, compact = true,
                modifier = Modifier.padding(top = 6.dp))
        }
        if (book.isMissing) Text(stringResource(R.string.book_file_unavailable),
            color = AppColors.Destructive, fontSize = AppType.Caption, maxLines = 1,
            modifier = Modifier.padding(top = 2.dp))
    }
}

/** A silk-thread progress line; finished books show the shared finished mark instead of a percentage. */
@Composable
private fun CoverFlowProgressRibbon(book: Book, light: () -> Color, modifier: Modifier) {
    val track = AppColors.Divider
    val eInk = LocalEInkMode.current
    val fraction = if (book.isReadingFinished) 1f
        else book.readingProgress.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 0f
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f).height(2.dp).drawWithCache {
            val h = size.height
            val rtl = layoutDirection == androidx.compose.ui.unit.LayoutDirection.Rtl
            onDrawBehind {
                drawLine(track, Offset(h / 2f, h / 2f), Offset(size.width - h / 2f, h / 2f), h, StrokeCap.Round)
                if (fraction > 0f) {
                    val end = (size.width - h) * fraction + h / 2f
                    val color = if (eInk) Color.Black else light()
                    if (rtl) drawLine(color, Offset(size.width - h / 2f, h / 2f), Offset(size.width - end, h / 2f), h, StrokeCap.Round)
                    else drawLine(color, Offset(h / 2f, h / 2f), Offset(end, h / 2f), h, StrokeCap.Round)
                }
            }
        })
        Spacer(Modifier.width(8.dp))
        if (book.isReadingFinished) FinishedReadingIndicator(compact = true, tint = AppColors.TextSecondary)
        else Text(formatReadingProgressPercent(book.readingProgress), color = AppColors.TextSecondary,
            fontSize = AppType.Caption2, maxLines = 1)
    }
}
