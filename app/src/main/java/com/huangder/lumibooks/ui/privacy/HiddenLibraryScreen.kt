package com.huangder.lumibooks.ui.privacy

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import com.huangder.lumibooks.R
import com.huangder.lumibooks.domain.model.Book
import com.huangder.lumibooks.domain.model.BookshelfLayout
import com.huangder.lumibooks.ui.animation.AppEasing
import com.huangder.lumibooks.ui.animation.VisualMotion.tween
import com.huangder.lumibooks.ui.bookshelf.BookContextMenuOverlay
import com.huangder.lumibooks.ui.bookshelf.BookDetailsActivity
import com.huangder.lumibooks.ui.bookshelf.BookNotesActivity
import com.huangder.lumibooks.ui.bookshelf.BookshelfCollection
import com.huangder.lumibooks.ui.bookshelf.ContextMenuPhase
import com.huangder.lumibooks.ui.bookshelf.CoverSearchActivity
import com.huangder.lumibooks.ui.bookshelf.CustomCoverSourceSheet
import com.huangder.lumibooks.ui.bookshelf.rememberBookContextMenuState
import com.huangder.lumibooks.ui.components.CloudAwareBookDeleteDialog
import com.huangder.lumibooks.ui.components.ConfigurableBackHandler
import com.huangder.lumibooks.ui.components.EditInputDialog
import com.huangder.lumibooks.ui.components.LiquidGlassDialog
import com.huangder.lumibooks.ui.components.ProvideLiquidGlassBackdrop
import com.huangder.lumibooks.ui.home.HomeViewModel
import com.huangder.lumibooks.ui.theme.AppColors
import com.huangder.lumibooks.ui.theme.AppSpace
import com.huangder.lumibooks.ui.theme.AppType
import com.huangder.lumibooks.ui.theme.LocalAppTheme
import com.huangder.lumibooks.ui.theme.LocalEInkMode
import com.huangder.lumibooks.ui.theme.LocalMotionEnabled
import com.huangder.lumibooks.ui.theme.LocalReduceTransparency
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Hidden books in the shelf's own grid or list. The long-press menu drops shelf organising
 * (favourite, pin, tags, folders) and offers 「取消隐藏」; editing selects books to unhide or delete.
 */
@Composable
fun HiddenLibraryScreen(
    onBack: () -> Unit,
    onOpenBook: (Book, Rect?) -> Unit,
    viewModel: HomeViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val books = uiState.hiddenBooks
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val contextMenuState = rememberBookContextMenuState()
    var isEditing by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(emptySet<String>()) }
    var expandedListBookId by remember { mutableStateOf<String?>(null) }
    /** Books playing the shrink-and-fade before they leave this page. */
    var leavingIds by remember { mutableStateOf(emptySet<String>()) }
    var pendingDelete by remember { mutableStateOf(emptyList<Book>()) }
    var editingBook by remember { mutableStateOf<Book?>(null) }
    var coverSourceBook by remember { mutableStateOf<Book?>(null) }
    var coverTargetBook by remember { mutableStateOf<Book?>(null) }
    var headerHeightPx by remember { mutableIntStateOf(0) }

    LaunchedEffect(books) {
        val ids = books.mapTo(HashSet()) { it.id }
        selectedIds = selectedIds.intersect(ids)
        if (expandedListBookId !in ids) expandedListBookId = null
        if (books.isEmpty()) isEditing = false
    }
    ConfigurableBackHandler(enabled = isEditing) {
        isEditing = false
        selectedIds = emptySet()
    }

    fun leave(ids: Set<String>, commit: suspend () -> Unit) {
        if (ids.isEmpty()) return
        leavingIds = leavingIds + ids
        scope.launch {
            delay(350)
            if (contextMenuState.phase != ContextMenuPhase.Idle) {
                snapshotFlow { contextMenuState.phase }.first { it == ContextMenuPhase.Idle }
            }
            commit()
            leavingIds = leavingIds - ids
            selectedIds = selectedIds - ids
        }
    }
    // Covers stay faded until the lists reflect the change, so they never flash back.
    fun unhide(ids: Set<String>) = leave(ids) {
        val done = kotlinx.coroutines.CompletableDeferred<Unit>()
        viewModel.setBooksHidden(ids, hidden = false) { done.complete(Unit) }
        done.await()
    }
    fun delete(targets: List<Book>, mode: com.huangder.lumibooks.domain.model.BookDeleteMode) =
        leave(targets.mapTo(HashSet()) { it.id }) {
            // deleteBooks raises the in-progress flag synchronously (Main.immediate) and clears
            // it once files and rows are gone; waiting keeps the covers from flashing back.
            viewModel.deleteBooks(targets, mode)
            viewModel.uiState.first { !it.isBookDeleteInProgress }
        }
    val coverPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        val book = coverTargetBook
        coverTargetBook = null
        if (uri != null && book != null) viewModel.updateCustomCover(book, uri)
    }
    val openNotes: (Book) -> Unit = { book ->
        context.startActivity(Intent(context, BookNotesActivity::class.java).putExtra(BookNotesActivity.EXTRA_BOOK_ID, book.id))
    }

    val isLiquidGlass = LocalAppTheme.current == "liquid_glass" && !LocalEInkMode.current
    val blurAllowed = !LocalEInkMode.current && !LocalReduceTransparency.current && android.os.Build.VERSION.SDK_INT >= 31
    val backdrop = rememberLayerBackdrop()
    val topPadding = with(LocalDensity.current) { headerHeightPx.toDp() } + 12.dp

    Box(modifier.fillMaxSize().background(AppColors.PageBg)) {
        ProvideLiquidGlassBackdrop(backdrop.takeIf { isLiquidGlass }) {
            // Same layering as the shelf: the long-press menu blurs the page behind it.
            Box(
                Modifier
                    .fillMaxSize()
                    .then(if (isLiquidGlass) Modifier.layerBackdrop(backdrop) else Modifier)
                    .graphicsLayer {
                        val radius = 20f * contextMenuState.scrimAlpha.value
                        renderEffect = if (blurAllowed && radius > 0.01f) {
                            android.graphics.RenderEffect.createBlurEffect(radius, radius, android.graphics.Shader.TileMode.CLAMP)
                                .asComposeRenderEffect()
                        } else null
                    }
                    .background(AppColors.PageBg)
            ) {
                BookshelfCollection(
                    layoutMode = BookshelfLayout.conventional(uiState.bookshelfLayoutMode),
                    books = books,
                    tagNamesByBook = emptyMap(),
                    isLoading = uiState.isLoading,
                    playEntranceAnimation = false,
                    deletingBookIds = leavingIds,
                    isEditing = isEditing,
                    selectedBookIds = selectedIds,
                    contextMenuState = contextMenuState,
                    syncedBookIds = uiState.syncedBookIds,
                    downloadStates = uiState.downloadStates,
                    expandedListBookId = expandedListBookId,
                    topPadding = topPadding,
                    bottomPadding = 32.dp,
                    onHaptic = { haptic.performHapticFeedback(HapticFeedbackType.LongPress) },
                    onSelectionToggle = { book ->
                        selectedIds = if (book.id in selectedIds) selectedIds - book.id else selectedIds + book.id
                    },
                    onExpandedBookChange = { expandedListBookId = it },
                    onBookClick = onOpenBook,
                    onAddBook = {},
                    onEditInfo = { expandedListBookId = null; editingBook = it },
                    onBookDetails = { BookDetailsActivity.start(context, it.id) },
                    onDelete = { expandedListBookId = null; pendingDelete = listOf(it) },
                    onFavorite = {},
                    onCustomCover = { expandedListBookId = null; coverSourceBook = it },
                    onRemoveCustomCover = viewModel::removeCustomCover,
                    onTags = {},
                    onBookmarksNotes = openNotes,
                    showAddBook = false,
                    onToggleHidden = { book -> expandedListBookId = null; unhide(setOf(book.id)) },
                    onToggleReadStatus = { book -> viewModel.updateBook(book.toggleReadStatus()) },
                    modifier = Modifier.fillMaxSize()
                )
                if (!uiState.isLoading && books.isEmpty()) HiddenLibraryEmptyState(Modifier.align(Alignment.Center))
            }
        }
        HiddenLibraryHeader(
            bookCount = books.size,
            isEditing = isEditing,
            selectedCount = selectedIds.size,
            onBack = onBack,
            onEditToggle = {
                isEditing = !isEditing
                expandedListBookId = null
                if (!isEditing) selectedIds = emptySet()
            },
            onSelectAll = {
                selectedIds = if (books.all { it.id in selectedIds }) emptySet() else books.mapTo(LinkedHashSet()) { it.id }
            },
            onUnhide = { unhide(selectedIds) },
            onDelete = { pendingDelete = books.filter { it.id in selectedIds } },
            modifier = Modifier.zIndex(2f).onGloballyPositioned { headerHeightPx = it.size.height }
        )
        ProvideLiquidGlassBackdrop(backdrop.takeIf { isLiquidGlass }) {
            BookContextMenuOverlay(
                state = contextMenuState,
                onDelete = { pendingDelete = listOf(it) },
                onCustomCover = { coverSourceBook = it },
                onRemoveCustomCover = viewModel::removeCustomCover,
                onBookmarksNotes = openNotes,
                onEditInfo = { editingBook = it },
                onBookDetails = { BookDetailsActivity.start(context, it.id) },
                onToggleReadStatus = { viewModel.updateBook(it.toggleReadStatus()) },
                onToggleHidden = { unhide(setOf(it.id)) },
                hiddenLibrary = true
            )
        }
    }

    coverSourceBook?.let { book ->
        CustomCoverSourceSheet(
            book = book,
            onDismiss = { coverSourceBook = null },
            onPickImage = {
                coverSourceBook = null
                coverTargetBook = book
                runCatching { coverPicker.launch("image/*") }.onFailure { coverTargetBook = null }
            },
            onWebSearch = {
                coverSourceBook = null
                runCatching { CoverSearchActivity.start(context, book.id, book.title) }
            }
        )
    }
    editingBook?.let { book ->
        LiquidGlassDialog(
            onDismissRequest = { editingBook = null },
            modifier = Modifier.imePadding(),
            backgroundScrimColor = Color.Transparent,
            fallbackBlurRadius = 18.dp,
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            EditInputDialog(
                title = stringResource(R.string.edit_book_info),
                fields = listOf(
                    Triple(stringResource(R.string.book_title_label), stringResource(R.string.book_title_original_hint), book.title),
                    Triple(stringResource(R.string.book_author_label), stringResource(R.string.book_author_original_hint), book.author)
                ),
                onBack = { editingBook = null },
                onConfirm = { values ->
                    viewModel.updateBook(book.copy(title = values.getOrElse(0) { book.title }, author = values.getOrElse(1) { book.author }))
                    editingBook = null
                }
            )
        }
    }
    if (pendingDelete.isNotEmpty()) {
        val targets = pendingDelete
        CloudAwareBookDeleteDialog(
            bookCount = targets.size,
            hasRemoteBooks = targets.any { it.remoteFileName != null },
            onDismiss = { pendingDelete = emptyList() },
            onDelete = { mode ->
                pendingDelete = emptyList()
                delete(targets, mode)
            }
        )
    }
}

@Composable
private fun HiddenLibraryEmptyState(modifier: Modifier = Modifier) {
    val motion = LocalMotionEnabled.current && !LocalEInkMode.current
    val appear = remember { Animatable(if (motion) 0f else 1f) }
    LaunchedEffect(Unit) { appear.animateTo(1f, tween(240, easing = AppEasing.Decelerate)) }
    Column(
        modifier
            .padding(horizontal = AppSpace.xl)
            .graphicsLayer {
                alpha = appear.value
                translationY = (1f - appear.value) * 12.dp.toPx()
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        androidx.compose.material3.Icon(
            imageVector = com.huangder.lumibooks.ui.icons.AppIcons.Lock,
            contentDescription = null,
            tint = AppColors.TextSecondary,
            modifier = Modifier.size(48.dp)
        )
        Spacer(Modifier.height(AppSpace.md))
        Text(
            stringResource(R.string.hidden_library_empty_title),
            color = AppColors.TextPrimary, fontSize = AppType.Body, fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(AppSpace.xs))
        Text(
            stringResource(R.string.hidden_library_empty_body),
            color = AppColors.TextSecondary, fontSize = AppType.BodySmall, textAlign = TextAlign.Center
        )
    }
}
