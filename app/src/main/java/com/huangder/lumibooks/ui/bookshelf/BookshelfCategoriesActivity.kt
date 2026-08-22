package com.huangder.lumibooks.ui.bookshelf

import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Label
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.huangder.lumibooks.MainActivity
import com.huangder.lumibooks.R
import com.huangder.lumibooks.data.local.DataStoreManager
import com.huangder.lumibooks.domain.model.Book
import com.huangder.lumibooks.domain.model.BookFormat
import com.huangder.lumibooks.domain.model.LibraryTag
import com.huangder.lumibooks.ui.components.ConfigurableActivityBack
import com.huangder.lumibooks.ui.components.EditInputDialog
import com.huangder.lumibooks.ui.components.LiquidGlassAlertDialog
import com.huangder.lumibooks.ui.components.LiquidGlassButton
import com.huangder.lumibooks.ui.components.LiquidGlassDialog
import com.huangder.lumibooks.ui.components.LiquidGlassDialogHost
import com.huangder.lumibooks.ui.components.LiquidGlassIconButton
import com.huangder.lumibooks.ui.components.LiquidGlassTextButton
import com.huangder.lumibooks.ui.components.ProvideLiquidGlassBackdrop
import com.huangder.lumibooks.ui.home.HomeViewModel
import com.huangder.lumibooks.ui.theme.AppColors
import com.huangder.lumibooks.ui.theme.AppSpace
import com.huangder.lumibooks.ui.theme.AppType
import com.huangder.lumibooks.ui.theme.EBookReaderTheme
import com.huangder.lumibooks.ui.theme.rememberLiquidGlassCapability
import com.huangder.lumibooks.ui.theme.effectiveAppTheme
import com.huangder.lumibooks.ui.theme.KaiTi
import com.huangder.lumibooks.ui.theme.LocalAppTheme
import com.huangder.lumibooks.ui.theme.resolveAppFontFamily
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import javax.inject.Inject

@AndroidEntryPoint
class BookshelfCategoriesActivity : ComponentActivity() {
    private var systemDarkMode by mutableStateOf(false)

    @Inject
    lateinit var dataStoreManager: DataStoreManager

    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(com.huangder.lumibooks.util.LocaleHelper.applyLanguage(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        systemDarkMode = resources.configuration.isNightModeEnabled()

        setContent {
            val appTheme by dataStoreManager.appTheme.collectAsState(initial = "lumi")
            val globalFontMode by dataStoreManager.globalFontMode.collectAsState(initial = "system")
            val liquidGlassTransparency by dataStoreManager.liquidGlassTransparency.collectAsState(initial = 0.55f)
            val liquidGlassHdrHighlightEnabled by dataStoreManager.liquidGlassHdrHighlightEnabled.collectAsState(initial = false)
            val darkMode by dataStoreManager.darkMode.collectAsState(initial = "system")
            val eInkMode by dataStoreManager.eInkModeEnabled.collectAsState(initial = false)
            val predictiveBackEnabled by dataStoreManager.predictiveBackEnabled.collectAsState(initial = true)
            val isDark = if (eInkMode) false else when (darkMode) {
                "dark" -> true
                "light" -> false
                else -> systemDarkMode
            }
            val capability = rememberLiquidGlassCapability(eInkMode, LocalView.current)
            val effectiveTheme = effectiveAppTheme(appTheme, capability)
            EBookReaderTheme(
                darkTheme = isDark,
                dynamicColor = effectiveTheme == "material3",
                appTheme = effectiveTheme,
                liquidGlassTransparency = liquidGlassTransparency,
                liquidGlassHdrHighlightEnabled = liquidGlassHdrHighlightEnabled && !eInkMode,
                eInkMode = eInkMode,
                globalFontMode = globalFontMode
            ) {
                ConfigurableActivityBack(
                    predictiveBackEnabled = predictiveBackEnabled,
                    onBack = { finish() }
                )
                Surface(modifier = Modifier.fillMaxSize(), color = AppColors.WindowBg) {
                    LiquidGlassDialogHost(modifier = Modifier.fillMaxSize()) {
                        BookshelfCategoriesScreen(
                            onTargetSelected = { target ->
                                startActivity(
                                    BookshelfCategoryBooksActivity.createIntent(
                                        this@BookshelfCategoriesActivity,
                                        target
                                    )
                                )
                            },
                            onBack = { finish() }
                        )
                    }
                }
            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        systemDarkMode = newConfig.isNightModeEnabled()
    }

    private fun Configuration.isNightModeEnabled(): Boolean =
        (uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
}

internal sealed interface BookshelfCategoryTarget {
    val title: String

    data class All(override val title: String) : BookshelfCategoryTarget
    data class Downloaded(override val title: String) : BookshelfCategoryTarget
    data class Pdf(override val title: String) : BookshelfCategoryTarget
    data class Txt(override val title: String) : BookshelfCategoryTarget
    data class Favorites(override val title: String) : BookshelfCategoryTarget
    data class Tag(val id: String, override val title: String) : BookshelfCategoryTarget
}

private data class CategoryRowModel(
    val target: BookshelfCategoryTarget,
    val count: Int,
    val icon: ImageVector
)

@Composable
private fun BookshelfCategoriesScreen(
    onTargetSelected: (BookshelfCategoryTarget) -> Unit,
    onBack: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val tagIdsByBook = remember(uiState.bookTagLinks) {
        uiState.bookTagLinks.groupBy { it.bookId }
            .mapValues { (_, links) -> links.mapTo(mutableSetOf()) { it.tagId } }
    }
    val allTitle = stringResource(R.string.filter_all)
    val downloadedTitle = stringResource(R.string.filter_downloaded)
    val favoritesTitle = stringResource(R.string.filter_favorites)
    val rows = buildList {
        add(CategoryRowModel(BookshelfCategoryTarget.All(allTitle), uiState.books.size, Icons.Outlined.MenuBook))
        add(CategoryRowModel(BookshelfCategoryTarget.Downloaded(downloadedTitle), uiState.books.size, Icons.Outlined.FolderOpen))
        add(CategoryRowModel(BookshelfCategoryTarget.Pdf("PDF"), uiState.books.count { it.format == BookFormat.PDF }, Icons.Outlined.Description))
        add(CategoryRowModel(BookshelfCategoryTarget.Txt("TXT"), uiState.books.count { it.format == BookFormat.TXT }, Icons.Outlined.Description))
        add(CategoryRowModel(BookshelfCategoryTarget.Favorites(favoritesTitle), uiState.books.count { it.isFavorite }, Icons.Outlined.FavoriteBorder))
    }
    CategoryListPage(
        categories = rows,
        tags = uiState.tags,
        tagIdsByBook = tagIdsByBook,
        onBack = onBack,
        onTargetSelected = onTargetSelected
    )
}

@Composable
internal fun BookshelfCategoryBooksRoute(
    selectedTarget: BookshelfCategoryTarget,
    onBack: () -> Unit,
    onOpenBook: (Book) -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val tagIdsByBook = remember(uiState.bookTagLinks) {
        uiState.bookTagLinks.groupBy { it.bookId }
            .mapValues { (_, links) -> links.mapTo(mutableSetOf()) { it.tagId } }
    }
    val tagNamesByBook = remember(uiState.tags, uiState.bookTagLinks) {
        val names = uiState.tags.associate { it.id to it.name }
        uiState.bookTagLinks.groupBy { it.bookId }
            .mapValues { (_, links) -> links.mapNotNull { names[it.tagId] } }
    }
    val selectedBooks = remember(selectedTarget, uiState.books, tagIdsByBook) {
        when (selectedTarget) {
            is BookshelfCategoryTarget.All -> uiState.books
            is BookshelfCategoryTarget.Downloaded -> uiState.books
            is BookshelfCategoryTarget.Pdf -> uiState.books.filter { it.format == BookFormat.PDF }
            is BookshelfCategoryTarget.Txt -> uiState.books.filter { it.format == BookFormat.TXT }
            is BookshelfCategoryTarget.Favorites -> uiState.books.filter { it.isFavorite }
            is BookshelfCategoryTarget.Tag -> uiState.books.filter {
                selectedTarget.id in tagIdsByBook[it.id].orEmpty()
            }
        }
    }

    CategoryBooksPage(
        title = selectedTarget.title,
        books = selectedBooks,
        tagNamesByBook = tagNamesByBook,
        syncedBookIds = uiState.syncedBookIds,
        layoutMode = uiState.bookshelfLayoutMode,
        isLoading = uiState.isLoading,
        tags = uiState.tags,
        tagIdsByBook = tagIdsByBook,
        onBack = onBack,
        onOpenBook = onOpenBook,
        viewModel = viewModel
    )
}

@Composable
private fun CategoryListPage(
    categories: List<CategoryRowModel>,
    tags: List<LibraryTag>,
    tagIdsByBook: Map<String, Set<String>>,
    onBack: () -> Unit,
    onTargetSelected: (BookshelfCategoryTarget) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.WindowBg)
            .statusBarsPadding()
    ) {
        CategoriesPageHeader(
            title = stringResource(R.string.bookshelf_categories_title),
            onBack = onBack
        )
        LazyColumn(
            contentPadding = PaddingValues(horizontal = AppSpace.lg, vertical = AppSpace.md),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            item {
                CategorySectionTitle(stringResource(R.string.bookshelf_builtin_categories))
            }
            items(categories, key = { "category_${it.target}" }) { row ->
                CategoryRow(
                    title = row.target.title,
                    count = row.count,
                    icon = row.icon,
                    onClick = { onTargetSelected(row.target) }
                )
            }
            item {
                CategorySectionTitle(
                    text = stringResource(R.string.bookshelf_user_tags),
                    modifier = Modifier.padding(top = 18.dp)
                )
            }
            if (tags.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.no_tags),
                        color = AppColors.TextSecondary,
                        fontSize = AppType.BodySmall,
                        modifier = Modifier.padding(vertical = AppSpace.md)
                    )
                }
            } else {
                items(tags, key = { "tag_${it.id}" }) { tag ->
                    CategoryRow(
                        title = tag.name,
                        count = tagIdsByBook.values.count { tag.id in it },
                        icon = Icons.Outlined.Label,
                        onClick = {
                            onTargetSelected(BookshelfCategoryTarget.Tag(tag.id, tag.name))
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoriesPageHeader(title: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppSpace.lg, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LiquidGlassIconButton(
            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
            contentDescription = stringResource(R.string.back),
            onClick = onBack,
            settingsBackButton = true
        )
        Spacer(Modifier.width(20.dp))
        Text(
            text = title,
            color = AppColors.TextPrimary,
            fontSize = AppType.Section,
            fontWeight = FontWeight.Bold,
            fontFamily = resolveAppFontFamily(KaiTi),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun CategorySectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = AppColors.TextSecondary,
        fontSize = AppType.Caption,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier.padding(bottom = 4.dp)
    )
}

@Composable
private fun CategoryRow(
    title: String,
    count: Int,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = AppColors.CardBg,
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, tint = AppColors.TextSecondary, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(14.dp))
            Text(
                text = title,
                color = AppColors.TextPrimary,
                fontSize = AppType.Body,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = stringResource(R.string.bookshelf_category_book_count, count),
                color = AppColors.TextSecondary,
                fontSize = AppType.Caption
            )
            Spacer(Modifier.width(6.dp))
            Icon(
                Icons.Outlined.KeyboardArrowRight,
                null,
                tint = AppColors.TextSecondary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun CategoryBooksPage(
    title: String,
    books: List<Book>,
    tagNamesByBook: Map<String, List<String>>,
    syncedBookIds: Set<String>,
    layoutMode: Int,
    isLoading: Boolean,
    tags: List<LibraryTag>,
    tagIdsByBook: Map<String, Set<String>>,
    onBack: () -> Unit,
    onOpenBook: (Book) -> Unit,
    viewModel: HomeViewModel
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val contextMenuState = rememberBookContextMenuState()
    var isEditing by remember { mutableStateOf(false) }
    var selectedBookIds by remember { mutableStateOf(emptySet<String>()) }
    var expandedListBookId by remember { mutableStateOf<String?>(null) }
    var deletingBookIds by remember { mutableStateOf(emptySet<String>()) }
    var pendingDeleteBooks by remember { mutableStateOf(emptyList<Book>()) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showBatchTagSheet by remember { mutableStateOf(false) }
    var tagTargetBook by remember { mutableStateOf<Book?>(null) }
    var editingBook by remember { mutableStateOf<Book?>(null) }
    var coverTargetBook by remember { mutableStateOf<Book?>(null) }
    var coverSourceBook by remember { mutableStateOf<Book?>(null) }
    var headerHeightPx by remember { mutableStateOf(0) }
    val collectionTopPadding = if (headerHeightPx > 0) {
        with(density) { headerHeightPx.toDp() } + 12.dp
    } else {
        116.dp
    }

    val coverPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        val book = coverTargetBook
        coverTargetBook = null
        if (uri != null && book != null) viewModel.updateCustomCover(book, uri)
    }

    LaunchedEffect(deletingBookIds) {
        if (deletingBookIds.isNotEmpty()) {
            kotlinx.coroutines.delay(350)
            if (contextMenuState.phase != ContextMenuPhase.Idle) {
                snapshotFlow { contextMenuState.phase }.first { it == ContextMenuPhase.Idle }
            }
            viewModel.deleteBooks(pendingDeleteBooks)
            deletingBookIds = emptySet()
            pendingDeleteBooks = emptyList()
            selectedBookIds = emptySet()
            isEditing = false
        }
    }
    LaunchedEffect(books) {
        val ids = books.mapTo(mutableSetOf()) { it.id }
        selectedBookIds = selectedBookIds.intersect(ids)
    }

    ProvideLiquidGlassBackdrop(null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(AppColors.WindowBg)
        ) {
            BookshelfCollection(
                layoutMode = layoutMode,
                books = books,
                tagNamesByBook = tagNamesByBook,
                isLoading = isLoading,
                playEntranceAnimation = false,
                deletingBookIds = deletingBookIds,
                isEditing = isEditing,
                selectedBookIds = selectedBookIds,
                contextMenuState = contextMenuState,
                syncedBookIds = syncedBookIds,
                expandedListBookId = expandedListBookId,
                topPadding = collectionTopPadding,
                bottomPadding = 32.dp,
                onHaptic = { haptic.performHapticFeedback(HapticFeedbackType.LongPress) },
                onSelectionToggle = { book ->
                    selectedBookIds = if (book.id in selectedBookIds) {
                        selectedBookIds - book.id
                    } else {
                        selectedBookIds + book.id
                    }
                },
                onExpandedBookChange = { expandedListBookId = it },
                onBookClick = { book, _ -> onOpenBook(book) },
                onAddBook = {},
                onEditInfo = { editingBook = it },
                onDelete = {
                    pendingDeleteBooks = listOf(it)
                    deletingBookIds = setOf(it.id)
                },
                onFavorite = { viewModel.updateBook(it.copy(isFavorite = !it.isFavorite)) },
                onCustomCover = { coverSourceBook = it },
                onRemoveCustomCover = viewModel::removeCustomCover,
                onTags = { tagTargetBook = it },
                onBookmarksNotes = {
                    context.startActivity(
                        Intent(context, BookNotesActivity::class.java).putExtra("bookId", it.id)
                    )
                },
                showAddBook = false,
                modifier = Modifier.fillMaxSize()
            )

            CategoryBooksHeader(
                title = title,
                isEditing = isEditing,
                selectedCount = selectedBookIds.size,
                onBack = onBack,
                onEditToggle = {
                    isEditing = !isEditing
                    if (!isEditing) selectedBookIds = emptySet()
                },
                onDelete = { showDeleteConfirm = true },
                onTags = { showBatchTagSheet = true },
                modifier = Modifier
                    .zIndex(2f)
                    .onGloballyPositioned { headerHeightPx = it.size.height }
            )

            BookContextMenuOverlay(
                state = contextMenuState,
                onDelete = {
                    pendingDeleteBooks = listOf(it)
                    deletingBookIds = setOf(it.id)
                },
                onFavorite = { viewModel.updateBook(it.copy(isFavorite = !it.isFavorite)) },
                onCustomCover = { coverSourceBook = it },
                onRemoveCustomCover = viewModel::removeCustomCover,
                onBookmarksNotes = {
                    context.startActivity(
                        Intent(context, BookNotesActivity::class.java).putExtra("bookId", it.id)
                    )
                },
                onTags = { tagTargetBook = it },
                onEditInfo = { editingBook = it }
            )
        }
    }

    // ── 自定义封面来源选择（选择图片 / 网络搜索） ──
    coverSourceBook?.let { sourceBook ->
        CustomCoverSourceSheet(
            book = sourceBook,
            onDismiss = { coverSourceBook = null },
            onPickImage = {
                coverSourceBook = null
                coverTargetBook = sourceBook
                runCatching { coverPicker.launch("image/*") }
            },
            onWebSearch = {
                coverSourceBook = null
                runCatching {
                    CoverSearchActivity.start(context, sourceBook.id, sourceBook.title)
                }
            }
        )
    }

    tagTargetBook?.let { book ->
        BookTagBottomSheet(
            tags = tags,
            selectedTagIds = tagIdsByBook[book.id].orEmpty(),
            onDismiss = { tagTargetBook = null },
            onTagCheckedChange = { tag, checked -> viewModel.setBookTag(book.id, tag.id, checked) },
            onCreateTag = { name, parentId ->
                viewModel.createAndAssignTag(book.id, name, parentId)
            },
            onDeleteTag = { tag, deleteChildren ->
                viewModel.deleteTag(tag.id, deleteChildren)
            }
        )
    }
    if (showBatchTagSheet) {
        BatchBookTagSheet(
            tags = tags,
            selectedBookCount = selectedBookIds.size,
            onDismiss = { showBatchTagSheet = false },
            onCreateTag = { name ->
                viewModel.createAndAssignTagToBooks(selectedBookIds, name)
            },
            onApply = { tagIds ->
                tagIds.forEach { viewModel.addTagToBooks(selectedBookIds, it) }
                showBatchTagSheet = false
            }
        )
    }
    editingBook?.let { book ->
        LiquidGlassDialog(
            onDismissRequest = { editingBook = null },
            backgroundScrimColor = Color.Transparent,
            backgroundBlurRadius = 18.dp,
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
        ) {
            EditInputDialog(
                title = stringResource(R.string.edit_book_info),
                fields = listOf(
                    Triple(stringResource(R.string.book_title_label), "", book.title),
                    Triple(stringResource(R.string.book_author_label), "", book.author)
                ),
                onBack = { editingBook = null },
                onConfirm = { values ->
                    viewModel.updateBook(
                        book.copy(
                            title = values.getOrElse(0) { book.title },
                            author = values.getOrElse(1) { book.author }
                        )
                    )
                    editingBook = null
                }
            )
        }
    }
    if (showDeleteConfirm) {
        LiquidGlassAlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.delete_selected_books_title)) },
            text = {
                Text(stringResource(R.string.delete_selected_books_confirm, selectedBookIds.size))
            },
            confirmButton = {
                LiquidGlassTextButton(
                    text = stringResource(R.string.delete),
                    tintedColor = Color(0xFFD92D3A),
                    onClick = {
                        val selected = books.filter { it.id in selectedBookIds }
                        pendingDeleteBooks = selected
                        deletingBookIds = selectedBookIds
                        showDeleteConfirm = false
                    }
                )
            },
            dismissButton = {
                LiquidGlassTextButton(
                    text = stringResource(R.string.cancel),
                    onClick = { showDeleteConfirm = false }
                )
            }
        )
    }
}

@Composable
private fun CategoryBooksHeader(
    title: String,
    isEditing: Boolean,
    selectedCount: Int,
    onBack: () -> Unit,
    onEditToggle: () -> Unit,
    onDelete: () -> Unit,
    onTags: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isLiquidGlass = LocalAppTheme.current == "liquid_glass"
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(AppColors.WindowBg.copy(alpha = 0.94f))
            .statusBarsPadding()
            .padding(horizontal = AppSpace.lg, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isEditing) {
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
                    text = stringResource(R.string.done),
                    color = AppColors.TextPrimary,
                    fontSize = AppType.BodySmall,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(Modifier.width(10.dp))
            LiquidGlassIconButton(
                imageVector = Icons.Outlined.Delete,
                contentDescription = stringResource(R.string.delete),
                onClick = onDelete,
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
                onClick = onTags,
                enabled = selectedCount > 0,
                size = 46.dp,
                iconSize = 21.dp,
                contentColor = AppColors.TextPrimary,
                normalContainerColor = AppColors.CardBg,
                liquidContainerColor = AppColors.CardBg,
                liquidScrimColor = AppColors.CardBg.copy(alpha = 0.58f)
            )
        } else {
            LiquidGlassIconButton(
                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = stringResource(R.string.back),
                onClick = onBack,
                settingsBackButton = true
            )
            Spacer(Modifier.width(20.dp))
            Text(
                text = title,
                color = AppColors.TextPrimary,
                fontSize = AppType.Section,
                fontWeight = FontWeight.Bold,
                fontFamily = resolveAppFontFamily(KaiTi),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            LiquidGlassButton(
                onClick = onEditToggle,
                tintedColor = AppColors.CardBg,
                modifier = Modifier
                    .width(78.dp)
                    .height(48.dp)
            ) {
                Text(stringResource(R.string.edit), color = AppColors.TextPrimary)
            }
        }
    }
}
