package com.huangder.lumibooks.ui.home

import com.huangder.lumibooks.ui.components.liquidGlassMenuAnchor

import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items as lazyListItems
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.huangder.lumibooks.R
import com.huangder.lumibooks.ui.animation.AppEasing
import com.huangder.lumibooks.ui.components.ConfigurableBackHandler
import com.huangder.lumibooks.ui.components.LiquidGlassButton
import com.huangder.lumibooks.ui.components.LiquidGlassMenuItem
import com.huangder.lumibooks.ui.components.LiquidGlassMenuSpec
import com.huangder.lumibooks.ui.components.LocalPredictiveBackEnabled
import com.huangder.lumibooks.ui.components.LiquidGlassIconButton
import com.huangder.lumibooks.ui.components.LiquidGlassSurface
import com.huangder.lumibooks.ui.components.LocalLiquidGlassMenuHost
import com.huangder.lumibooks.ui.components.ProvideLiquidGlassBackdrop
import com.huangder.lumibooks.ui.icons.AppIcons
import com.huangder.lumibooks.ui.theme.AppColors
import com.huangder.lumibooks.ui.theme.AppRadius
import com.huangder.lumibooks.ui.theme.AppSpace
import com.huangder.lumibooks.ui.theme.AppType
import com.huangder.lumibooks.ui.theme.LocalAppTheme
import com.huangder.lumibooks.ui.theme.LocalEInkMode
import com.huangder.lumibooks.ui.theme.LocalMotionEnabled
import com.huangder.lumibooks.util.FileUtils
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.backdrops.LayerBackdrop
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * State of the folder books page. Lives in the Activity so the system back gesture, the header
 * back button and the folder navigation all share one source of truth.
 */
@Composable
internal fun AuthorizedFolderBooksRoute(
    predictiveBackEnabled: Boolean,
    contentBackdrop: LayerBackdrop? = null,
    onExit: () -> Unit,
    onConfirmSelection: (Set<String>) -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val motionEnabled = LocalMotionEnabled.current
    val eInkMode = LocalEInkMode.current
    var requestedFolderKey by remember { mutableStateOf<String?>(null) }
    var selection by remember { mutableStateOf<Set<String>>(emptySet()) }
    var folderNavigationForward by remember { mutableStateOf(true) }
    var searchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    val files = uiState.authorizedFolderFiles
    val fileInputs = remember(files) { files.map { it.toFolderBooksFile() } }
    val filesByKey = remember(files) { files.associateBy { it.folderBooksKey() } }
    val tree = remember(fileInputs, uiState.authorizedFolderFolders) {
        buildAuthorizedFolderTree(fileInputs, uiState.authorizedFolderFolders)
    }
    val currentFolderKey = tree.resolve(requestedFolderKey)
    // The drawn level lags one animation behind the requested one, exactly like the bookshelf.
    var renderedFolderKey by remember { mutableStateOf(currentFolderKey) }
    var hasRenderedContent by remember { mutableStateOf(tree.items(renderedFolderKey).isNotEmpty()) }
    val folderTransitionAlpha = remember { Animatable(1f) }
    val folderTransitionOffset = remember { Animatable(0f) }

    LaunchedEffect(currentFolderKey, motionEnabled, eInkMode) {
        if (currentFolderKey == renderedFolderKey) return@LaunchedEffect
        if (eInkMode || !hasRenderedContent) {
            // The first real level (remembered content finishing its load) appears instantly.
            renderedFolderKey = currentFolderKey
            hasRenderedContent = true
            folderTransitionAlpha.snapTo(1f)
            folderTransitionOffset.snapTo(0f)
            return@LaunchedEffect
        }
        if (!motionEnabled) {
            folderTransitionOffset.snapTo(0f)
            folderTransitionAlpha.animateTo(0f, tween(70, easing = AppEasing.Standard))
            renderedFolderKey = currentFolderKey
            folderTransitionAlpha.snapTo(0f)
            folderTransitionAlpha.animateTo(1f, tween(90, easing = AppEasing.Standard))
            return@LaunchedEffect
        }
        val direction = if (folderNavigationForward) -1f else 1f
        coroutineScope {
            launch {
                folderTransitionAlpha.animateTo(0f, tween(160, easing = AppEasing.Accelerate))
            }
            launch {
                folderTransitionOffset.animateTo(
                    32f * direction,
                    tween(180, easing = AppEasing.Accelerate)
                )
            }
        }
        renderedFolderKey = currentFolderKey
        hasRenderedContent = true
        folderTransitionAlpha.snapTo(0f)
        folderTransitionOffset.snapTo(-32f * direction)
        coroutineScope {
            launch {
                folderTransitionAlpha.animateTo(1f, tween(220, easing = AppEasing.Decelerate))
            }
            launch {
                folderTransitionOffset.animateTo(0f, tween(240, easing = AppEasing.Smooth))
            }
        }
    }

    val levelTransitionModifier = Modifier.graphicsLayer {
        alpha = folderTransitionAlpha.value
        translationY = folderTransitionOffset.value.dp.toPx()
    }
    val items = tree.items(renderedFolderKey)
    val levelBooks = remember(items, filesByKey) {
        items.mapNotNull { item -> item.file?.let { file -> filesByKey[file.key] } }
    }
    val sortMode = uiState.folderBooksSortMode
    val sortAscending = uiState.folderBooksSortAscending
    val sortedItems = remember(items, sortMode, sortAscending) {
        sortFolderBooksItems(items, sortMode, sortAscending)
    }
    // A blank query keeps browsing the level; results only replace it once the user types.
    val showingSearchResults = searchActive && searchQuery.isNotBlank()
    val searchHits = remember(
        tree,
        renderedFolderKey,
        searchQuery,
        showingSearchResults,
        sortMode,
        sortAscending
    ) {
        if (!showingSearchResults) {
            emptyList()
        } else {
            sortFolderBooksSearchHits(
                tree.searchSubtree(renderedFolderKey, searchQuery),
                sortMode,
                sortAscending
            )
        }
    }
    val displayedItems = if (showingSearchResults) {
        searchHits.map { hit ->
            FolderBooksItem(key = hit.file.key, name = hit.file.name, file = hit.file)
        }
    } else {
        sortedItems
    }
    val displayedBooks = remember(displayedItems, filesByKey) {
        displayedItems.mapNotNull { item -> item.file?.let { file -> filesByKey[file.key] } }
    }
    val pathByKey = if (showingSearchResults) {
        searchHits
            .filter { it.relativePath.isNotEmpty() }
            .associate { it.file.key to it.relativePath.joinToString(" › ") }
    } else {
        emptyMap()
    }
    val breadcrumb = remember(tree, renderedFolderKey) { tree.breadcrumb(renderedFolderKey) }
    // A refresh can drop files that are still selected; only keep live selections.
    val availableUris = remember(files) { files.mapTo(mutableSetOf()) { it.uri.toString() } }
    val effectiveSelection = remember(selection, availableUris) { selection intersect availableUris }
    val importedUris = remember(files) {
        files.filter { it.isImported }.mapTo(mutableSetOf()) { it.uri.toString() }
    }
    val knownCovers = remember(files) {
        files.mapNotNull { file -> file.importedCoverPath?.let { file.uri.toString() to it } }.toMap()
    }
    val goUpOrExit = {
        val parent = tree.parentOf(currentFolderKey)
        if (parent == null) {
            onExit()
        } else {
            searchActive = false
            searchQuery = ""
            folderNavigationForward = false
            requestedFolderKey = parent
        }
    }
    val parentFolderKey = tree.parentOf(currentFolderKey)
    val closeSearch = {
        searchActive = false
        searchQuery = ""
    }
    val summaryText = when {
        showingSearchResults && searchHits.isEmpty() -> stringResource(R.string.search_no_results)
        showingSearchResults -> stringResource(R.string.folder_books_search_results, searchHits.size)
        else -> stringResource(
            R.string.folder_books_level_summary,
            items.count { it.isFolder },
            levelBooks.size,
            effectiveSelection.size
        )
    }

    LaunchedEffect(viewModel) { viewModel.startAuthorizedFolderSnapshot(context) }
    LaunchedEffect(uiState.authorizedFolderMessage) {
        val text = uiState.authorizedFolderMessage ?: return@LaunchedEffect
        Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
        viewModel.clearAuthorizedFolderMessage()
    }
    // Nested levels consume back to go up one level; at the top the system handles it, so the
    // Activity still plays the OEM/predictive back animation.
    CompositionLocalProvider(LocalPredictiveBackEnabled provides predictiveBackEnabled) {
        ConfigurableBackHandler(enabled = searchActive || parentFolderKey != null) {
            if (searchActive) closeSearch() else goUpOrExit()
        }
    }

    AuthorizedFolderBooksScreen(
        items = displayedItems,
        breadcrumb = breadcrumb,
        levelBooks = displayedBooks,
        filesByKey = filesByKey,
        pathByKey = pathByKey,
        levelSummary = if (uiState.authorizedFolderScanning) {
            stringResource(R.string.import_scanning_directory)
        } else {
            summaryText
        },
        selection = effectiveSelection,
        importedUris = importedUris,
        coverPaths = knownCovers,
        contentBackdrop = contentBackdrop,
        levelTransitionModifier = levelTransitionModifier,
        isScanning = uiState.authorizedFolderScanning,
        emptyLabel = when {
            showingSearchResults -> stringResource(R.string.search_no_results)
            files.isEmpty() && uiState.authorizedFolderFolders.isEmpty() ->
                stringResource(R.string.import_folder_books_empty)
            else -> stringResource(R.string.folder_books_empty_folder)
        },
        isSearching = searchActive,
        showingSearchResults = showingSearchResults,
        searchQuery = searchQuery,
        layoutMode = uiState.folderBooksLayoutMode,
        sortMode = sortMode,
        sortAscending = sortAscending,
        onBack = { if (searchActive) closeSearch() else goUpOrExit() },
        onOpenFolder = { folder ->
            searchActive = false
            searchQuery = ""
            folderNavigationForward = true
            requestedFolderKey = folder.key
        },
        onToggleBook = { file -> selection = toggleSelection(selection, file.uri.toString()) },
        onSelectAll = { selection = toggleSelection(selection, displayedBooks) },
        onLayoutModeChange = viewModel::setFolderBooksLayoutMode,
        onSortModeChange = viewModel::setFolderBooksSortMode,
        onSortAscendingChange = viewModel::setFolderBooksSortAscending,
        onSearchOpen = { searchActive = true },
        onSearchQueryChange = { searchQuery = it },
        onSearchClose = closeSearch,
        onRefresh = {
            closeSearch()
            viewModel.refreshAuthorizedFolders(context)
        },
        onConfirm = { onConfirmSelection(effectiveSelection) }
    )
}

/**
 * File-manager style page for the books remembered inside the authorized folders. One level is
 * shown at a time: folders open into the next level, books are picked with the accent outline.
 */
@Composable
internal fun AuthorizedFolderBooksScreen(
    items: List<FolderBooksItem>,
    breadcrumb: List<FolderBooksNode>,
    levelBooks: List<AuthorizedFolderFile>,
    filesByKey: Map<String, AuthorizedFolderFile>,
    pathByKey: Map<String, String>,
    levelSummary: String,
    selection: Set<String>,
    importedUris: Set<String>,
    coverPaths: Map<String, String?>,
    contentBackdrop: LayerBackdrop? = null,
    levelTransitionModifier: Modifier = Modifier,
    isScanning: Boolean,
    emptyLabel: String,
    isSearching: Boolean,
    showingSearchResults: Boolean,
    searchQuery: String,
    layoutMode: Int,
    sortMode: FolderBooksSortMode,
    sortAscending: Boolean,
    onBack: () -> Unit,
    onOpenFolder: (FolderBooksNode) -> Unit,
    onToggleBook: (AuthorizedFolderFile) -> Unit,
    onSelectAll: () -> Unit,
    onLayoutModeChange: (Int) -> Unit,
    onSortModeChange: (FolderBooksSortMode) -> Unit,
    onSortAscendingChange: (Boolean) -> Unit,
    onSearchOpen: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onSearchClose: () -> Unit,
    onRefresh: () -> Unit,
    onConfirm: () -> Unit
) {
    val isLiquidGlass = LocalAppTheme.current == "liquid_glass"
    val motionEnabled = LocalMotionEnabled.current
    val backdrop = contentBackdrop ?: rememberLayerBackdrop()
    val useBackdrop = isLiquidGlass && contentBackdrop != null
    val normalizedLayoutMode = layoutMode.coerceIn(1, 3)

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            FolderBooksHeader(
                title = breadcrumb.lastOrNull()?.name
                    ?: stringResource(R.string.import_select_folder_books),
                path = breadcrumb.takeIf { it.size > 1 }
                    ?.joinToString(" › ") { it.name }
                    .orEmpty(),
                levelSummary = levelSummary,
                isScanning = isScanning,
                isSearching = isSearching,
                searchQuery = searchQuery,
                showSelectAll = levelBooks.isNotEmpty(),
                layoutMode = normalizedLayoutMode,
                sortMode = sortMode,
                sortAscending = sortAscending,
                onBack = onBack,
                onRefresh = onRefresh,
                onSearchOpen = onSearchOpen,
                onSearchQueryChange = onSearchQueryChange,
                onSearchClose = onSearchClose,
                onSelectAll = onSelectAll,
                onSortModeChange = onSortModeChange,
                onSortAscendingChange = onSortAscendingChange,
                onLayoutModeChange = { onLayoutModeChange(normalizedLayoutMode % 3 + 1) }
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .then(levelTransitionModifier)
                    .then(if (useBackdrop) Modifier.layerBackdrop(backdrop) else Modifier)
            ) {
                // Directory levels swap instantly; only the list/grid switch is animated so rows
                // never re-enter and appear to twitch when the page opens.
                AnimatedContent(
                    targetState = normalizedLayoutMode to showingSearchResults,
                    modifier = Modifier.fillMaxSize(),
                    transitionSpec = {
                        if (!motionEnabled) {
                            fadeIn(tween(100)) togetherWith fadeOut(tween(100))
                        } else {
                            val enter = fadeIn(tween(200, delayMillis = 60)) +
                                scaleIn(
                                    initialScale = 0.94f,
                                    animationSpec = tween(240, delayMillis = 60)
                                )
                            val exit = fadeOut(tween(140)) +
                                scaleOut(targetScale = 0.92f, animationSpec = tween(180))
                            enter togetherWith exit
                        }
                    },
                    label = "folderBooksLayout"
                ) { (layoutModeForLevel, _) ->
                    FolderBooksLevel(
                        items = items,
                        filesByKey = filesByKey,
                        pathByKey = pathByKey,
                        isScanning = isScanning,
                        emptyLabel = emptyLabel,
                        layoutMode = layoutModeForLevel,
                        selectedUris = selection,
                        importedUris = importedUris,
                        coverPaths = coverPaths,
                        onOpenFolder = onOpenFolder,
                        onToggleBook = onToggleBook
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = AppSpace.lg, vertical = AppSpace.md)
        ) {
            if (useBackdrop) {
                ProvideLiquidGlassBackdrop(backdrop) {
                    FolderBooksConfirmButton(enabled = selection.isNotEmpty(), onClick = onConfirm)
                }
            } else {
                FolderBooksConfirmButton(enabled = selection.isNotEmpty(), onClick = onConfirm)
            }
        }
    }
}

@Composable
private fun FolderBooksHeader(
    title: String,
    path: String,
    levelSummary: String,
    isScanning: Boolean,
    isSearching: Boolean,
    searchQuery: String,
    showSelectAll: Boolean,
    layoutMode: Int,
    sortMode: FolderBooksSortMode,
    sortAscending: Boolean,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onSearchOpen: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onSearchClose: () -> Unit,
    onSelectAll: () -> Unit,
    onSortModeChange: (FolderBooksSortMode) -> Unit,
    onSortAscendingChange: (Boolean) -> Unit,
    onLayoutModeChange: () -> Unit
) {
    val selectAllLabel = stringResource(R.string.import_select_all)
    val layoutModeLabel = stringResource(R.string.import_layout_mode)
    val motionEnabled = LocalMotionEnabled.current
    val eInkMode = LocalEInkMode.current
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
            LiquidGlassIconButton(
                imageVector = AppIcons.ArrowLeft,
                contentDescription = stringResource(R.string.back),
                onClick = onBack,
                size = 44.dp,
                iconSize = 20.dp,
                normalContainerColor = AppColors.BgGray,
                liquidContainerColor = AppColors.CardBg,
                liquidScrimColor = AppColors.CardBg.copy(alpha = 0.58f)
            )
            Spacer(Modifier.width(AppSpace.sm))
            BoxWithConstraints(modifier = Modifier.weight(1f)) {
                val collapsedWidth = 44.dp
                val expandedWidth = maxWidth
                val rawExpansion by animateFloatAsState(
                    targetValue = if (isSearching) 1f else 0f,
                    animationSpec = when {
                        !motionEnabled || eInkMode -> tween(120)
                        // Opening stretches past the target and springs back; closing is a plain
                        // critically damped spring so the shrink eases out without bouncing or
                        // looking linear.
                        isSearching -> spring(dampingRatio = 0.45f, stiffness = 280f)
                        else -> spring(dampingRatio = 1f, stiffness = 320f)
                    },
                    label = "folderBooksSearchExpansion"
                )
                val expansion = rawExpansion.coerceIn(0f, 1f)
                // Layout width cannot exceed the row, so the visible overshoot is drawn: the pill
                // stretches past its full width from its right edge, then settles back.
                val overshoot = (rawExpansion - 1f).coerceAtLeast(0f)
                val titleAlpha = 1f - expansion
                val fieldExpansion = expansion
                Column(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .fillMaxWidth()
                        .alpha(titleAlpha)
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
                FolderBooksSearchPill(
                    query = searchQuery,
                    expanded = isSearching,
                    contentAlpha = fieldExpansion,
                    onQueryChange = onSearchQueryChange,
                    onExpand = onSearchOpen,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .width(collapsedWidth + (expandedWidth - collapsedWidth) * expansion)
                        .height(44.dp)
                        .graphicsLayer {
                            scaleX = 1f + overshoot * 0.5f
                            transformOrigin = TransformOrigin(1f, 0.5f)
                        }
                )
            }
            Spacer(Modifier.width(AppSpace.sm))
            if (isScanning) {
                Box(modifier = Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = AppColors.Accent,
                        strokeWidth = 2.dp
                    )
                }
            } else {
                LiquidGlassIconButton(
                    imageVector = AppIcons.ArrowClockwise,
                    contentDescription = stringResource(R.string.import_folder_books_refresh),
                    onClick = onRefresh,
                    size = 44.dp,
                    iconSize = 20.dp,
                    normalContainerColor = AppColors.BgGray,
                    liquidContainerColor = AppColors.CardBg,
                    liquidScrimColor = AppColors.CardBg.copy(alpha = 0.58f)
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
                text = levelSummary,
                color = AppColors.TextSecondary,
                fontSize = AppType.Caption,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (showSelectAll) {
                Spacer(Modifier.width(AppSpace.sm))
                LiquidGlassButton(
                    onClick = onSelectAll,
                    modifier = Modifier.height(44.dp),
                    shape = RoundedCornerShape(AppRadius.capsule),
                    tintedColor = AppColors.Accent,
                    prominentShadow = false,
                    contentColor = AppColors.OnAccent
                ) {
                    Text(
                        text = selectAllLabel,
                        color = AppColors.OnAccent,
                        fontSize = AppType.BodySmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            Spacer(Modifier.width(AppSpace.sm))
            FolderBooksSortButton(
                sortMode = sortMode,
                sortAscending = sortAscending,
                onSortModeChange = onSortModeChange,
                onSortAscendingChange = onSortAscendingChange
            )
            Spacer(Modifier.width(AppSpace.sm))
            LiquidGlassSurface(
                shape = CircleShape,
                fallbackColor = AppColors.BgGray,
                contentScrimColor = AppColors.CardBg.copy(alpha = 0.58f),
                onClick = onLayoutModeChange,
                effectPadding = 1.dp,
                modifier = Modifier.size(44.dp)
            ) {
                Icon(
                    imageVector = if (layoutMode == 1) AppIcons.List else AppIcons.SquaresFour,
                    contentDescription = layoutModeLabel,
                    tint = AppColors.TextPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Spacer(Modifier.height(AppSpace.xs))
    }
}

@Composable
private fun FolderBooksSearchPill(
    query: String,
    expanded: Boolean,
    contentAlpha: Float,
    onQueryChange: (String) -> Unit,
    onExpand: () -> Unit,
    modifier: Modifier = Modifier
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(expanded) {
        if (expanded) {
            focusRequester.requestFocus()
            keyboardController?.show()
        } else {
            keyboardController?.hide()
        }
    }
    LiquidGlassSurface(
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
                    .alpha(contentAlpha)
                    .padding(start = AppSpace.md, end = AppSpace.xs),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = AppIcons.MagnifyingGlass,
                    contentDescription = null,
                    tint = AppColors.TextSecondary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(AppSpace.sm))
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester),
                    singleLine = true,
                    textStyle = TextStyle(
                        color = AppColors.TextPrimary,
                        fontSize = AppType.BodySmall,
                        fontWeight = FontWeight.Medium
                    ),
                    cursorBrush = SolidColor(AppColors.Accent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { keyboardController?.hide() }),
                    decorationBox = { innerTextField ->
                        Box {
                            if (query.isEmpty()) {
                                Text(
                                    text = stringResource(R.string.import_search_placeholder),
                                    color = AppColors.TextSecondary,
                                    fontSize = AppType.BodySmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            innerTextField()
                        }
                    }
                )
                if (query.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .clickable(
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() }
                            ) { onQueryChange("") },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = AppIcons.X,
                            contentDescription = stringResource(R.string.import_search_clear),
                            tint = AppColors.TextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        } else {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = AppIcons.MagnifyingGlass,
                    contentDescription = stringResource(R.string.search),
                    tint = AppColors.TextPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun FolderBooksSortButton(
    sortMode: FolderBooksSortMode,
    sortAscending: Boolean,
    onSortModeChange: (FolderBooksSortMode) -> Unit,
    onSortAscendingChange: (Boolean) -> Unit
) {
    val isLiquidGlass = LocalAppTheme.current == "liquid_glass"
    val menuHost = LocalLiquidGlassMenuHost.current
    var sortExpanded by remember { mutableStateOf(false) }
    var sortAnchorBounds by remember { mutableStateOf(Rect.Zero) }
    val isDefaultSort = sortMode == FolderBooksSortMode.NAME && sortAscending
    val ascendingLabel = stringResource(R.string.folder_books_sort_ascending)
    val descendingLabel = stringResource(R.string.folder_books_sort_descending)
    val modeLabels = FolderBooksSortMode.entries.associateWith { sortModeLabel(it) }

    DisposableEffect(menuHost) {
        onDispose { if (sortExpanded) menuHost?.dismiss() }
    }

    Box {
        LiquidGlassIconButton(
            imageVector = AppIcons.SortAscending,
            contentDescription = stringResource(R.string.import_sort),
            onClick = {
                if (menuHost != null && sortAnchorBounds != Rect.Zero) {
                    if (sortExpanded) {
                        menuHost.dismiss()
                    } else {
                        sortExpanded = true
                        menuHost.show(
                            LiquidGlassMenuSpec(
                                anchorBounds = sortAnchorBounds,
                                width = 196.dp,
                                maxVisibleItems = 8,
                                onDismiss = { sortExpanded = false },
                                items = FolderBooksSortMode.entries.map { mode ->
                                    LiquidGlassMenuItem(
                                        label = modeLabels.getValue(mode),
                                        selected = mode == sortMode,
                                        onClick = { onSortModeChange(mode) }
                                    )
                                } + listOf(
                                    LiquidGlassMenuItem(
                                        label = ascendingLabel,
                                        selected = sortAscending,
                                        onClick = { onSortAscendingChange(true) }
                                    ),
                                    LiquidGlassMenuItem(
                                        label = descendingLabel,
                                        selected = !sortAscending,
                                        onClick = { onSortAscendingChange(false) }
                                    )
                                )
                            )
                        )
                    }
                } else {
                    sortExpanded = true
                }
            },
            size = 44.dp,
            iconSize = 20.dp,
            contentColor = if (isDefaultSort) AppColors.TextPrimary else AppColors.Accent,
            normalContainerColor = AppColors.BgGray,
            liquidContainerColor = AppColors.CardBg,
            liquidScrimColor = AppColors.CardBg.copy(alpha = 0.58f),
            modifier = Modifier.liquidGlassMenuAnchor().onGloballyPositioned { sortAnchorBounds = it.boundsInRoot() }
        )

        if (!isLiquidGlass || menuHost == null) {
            DropdownMenu(
                expanded = sortExpanded,
                onDismissRequest = { sortExpanded = false },
                modifier = Modifier.width(196.dp),
                shape = RoundedCornerShape(AppRadius.md),
                containerColor = AppColors.WindowBg,
                tonalElevation = 0.dp,
                shadowElevation = 0.dp
            ) {
                FolderBooksSortMode.entries.forEach { mode ->
                    FolderBooksSortMenuItem(
                        label = sortModeLabel(mode),
                        selected = mode == sortMode,
                        onClick = {
                            sortExpanded = false
                            onSortModeChange(mode)
                        }
                    )
                }
                FolderBooksSortMenuItem(
                    label = ascendingLabel,
                    selected = sortAscending,
                    onClick = {
                        sortExpanded = false
                        onSortAscendingChange(true)
                    }
                )
                FolderBooksSortMenuItem(
                    label = descendingLabel,
                    selected = !sortAscending,
                    onClick = {
                        sortExpanded = false
                        onSortAscendingChange(false)
                    }
                )
            }
        }
    }
}

@Composable
private fun FolderBooksSortMenuItem(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    DropdownMenuItem(
        text = {
            Text(
                text = label,
                color = if (selected) AppColors.Accent else AppColors.TextPrimary,
                fontSize = AppType.BodySmall
            )
        },
        onClick = onClick,
        trailingIcon = if (selected) {
            {
                Icon(
                    imageVector = AppIcons.CheckFilled,
                    contentDescription = null,
                    tint = AppColors.Accent,
                    modifier = Modifier.size(18.dp)
                )
            }
        } else {
            null
        }
    )
}

@Composable
private fun sortModeLabel(mode: FolderBooksSortMode): String = stringResource(
    when (mode) {
        FolderBooksSortMode.NAME -> R.string.folder_books_sort_name
        FolderBooksSortMode.SIZE -> R.string.folder_books_sort_size
        FolderBooksSortMode.TIME -> R.string.folder_books_sort_time
        FolderBooksSortMode.FORMAT -> R.string.folder_books_sort_format
    }
)

@Composable
private fun FolderBooksLevel(
    items: List<FolderBooksItem>,
    filesByKey: Map<String, AuthorizedFolderFile>,
    pathByKey: Map<String, String>,
    isScanning: Boolean,
    emptyLabel: String,
    layoutMode: Int,
    selectedUris: Set<String>,
    importedUris: Set<String>,
    coverPaths: Map<String, String?>,
    onOpenFolder: (FolderBooksNode) -> Unit,
    onToggleBook: (AuthorizedFolderFile) -> Unit
) {
    if (items.isEmpty()) {
        FolderBooksEmptyState(isScanning = isScanning, emptyText = emptyLabel)
        return
    }
    if (layoutMode == 1) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = AppSpace.lg,
                end = AppSpace.lg,
                top = AppSpace.xs,
                bottom = 118.dp
            ),
            verticalArrangement = Arrangement.spacedBy(AppSpace.sm)
        ) {
            lazyListItems(items, key = { it.key }) { item ->
                if (item.isFolder) {
                    FolderBooksFolderRow(folder = item.folder!!, onClick = onOpenFolder)
                } else {
                    item.file?.let { input -> filesByKey[input.key] }?.let { file ->
                        FolderBooksBookRow(
                            file = file,
                            isSelected = file.uri.toString() in selectedUris,
                            isImported = file.uri.toString() in importedUris,
                            coverPath = coverPaths[file.uri.toString()],
                            relativePath = pathByKey[file.uri.toString()].orEmpty(),
                            onClick = { onToggleBook(file) }
                        )
                    }
                }
            }
        }
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(layoutMode),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = AppSpace.lg,
            end = AppSpace.lg,
            top = AppSpace.xs,
            bottom = 118.dp
        ),
        horizontalArrangement = Arrangement.spacedBy(AppSpace.sm),
        verticalArrangement = Arrangement.spacedBy(AppSpace.md)
    ) {
        items(items, key = { it.key }) { item ->
            if (item.isFolder) {
                FolderBooksFolderTile(
                    folder = item.folder!!,
                    compact = layoutMode == 3,
                    onClick = onOpenFolder
                )
            } else {
                item.file?.let { input -> filesByKey[input.key] }?.let { file ->
                    FolderBooksBookTile(
                        file = file,
                        isSelected = file.uri.toString() in selectedUris,
                        isImported = file.uri.toString() in importedUris,
                        coverPath = coverPaths[file.uri.toString()],
                        relativePath = pathByKey[file.uri.toString()].orEmpty(),
                        compact = layoutMode == 3,
                        onClick = { onToggleBook(file) }
                    )
                }
            }
        }
    }
}

@Composable
private fun FolderBooksEmptyState(
    isScanning: Boolean,
    emptyText: String
) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(AppSpace.sm)
        ) {
            if (isScanning) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    color = AppColors.Accent,
                    strokeWidth = 2.dp
                )
            }
            Text(
                text = emptyText,
                color = AppColors.TextSecondary,
                fontSize = AppType.BodySmall,
                maxLines = 3
            )
        }
    }
}

@Composable
private fun FolderBooksFolderRow(
    folder: FolderBooksNode,
    onClick: (FolderBooksNode) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AppRadius.md))
            .background(AppColors.CardBg)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) { onClick(folder) }
            .padding(AppSpace.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FolderThumb(modifier = Modifier.width(40.dp).height(56.dp))
        Spacer(Modifier.width(AppSpace.sm))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = folder.name,
                color = AppColors.TextPrimary,
                fontSize = AppType.BodySmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = stringResource(R.string.folder_books_folder_count, folder.bookCount),
                color = AppColors.TextSecondary,
                fontSize = AppType.Caption,
                maxLines = 1
            )
        }
        Spacer(Modifier.width(AppSpace.sm))
        Icon(
            imageVector = AppIcons.CaretRight,
            contentDescription = null,
            tint = AppColors.TextSecondary,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun FolderBooksBookRow(
    file: AuthorizedFolderFile,
    isSelected: Boolean,
    isImported: Boolean,
    coverPath: String?,
    relativePath: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.5.dp,
                color = AppColors.Accent.copy(alpha = if (isSelected) 1f else 0f),
                shape = RoundedCornerShape(AppRadius.md)
            )
            .clip(RoundedCornerShape(AppRadius.md))
            .background(AppColors.CardBg)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onClick
            )
            .padding(AppSpace.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ImportBookCoverArt(
            book = file.toSelectedImportBook(),
            shape = RoundedCornerShape(AppRadius.sm),
            placeholderFontSize = AppType.Caption,
            coverPathOverride = coverPath,
            modifier = Modifier
                .width(40.dp)
                .height(56.dp)
        )
        Spacer(Modifier.width(AppSpace.sm))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = file.name.substringBeforeLast('.', missingDelimiterValue = file.name),
                color = AppColors.TextPrimary,
                fontSize = AppType.BodySmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = file.bookMeta(),
                    color = AppColors.TextSecondary,
                    fontSize = AppType.Caption,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (isImported) {
                    Spacer(Modifier.width(AppSpace.xs))
                    ImportedBadge()
                }
            }
            if (relativePath.isNotBlank()) {
                Text(
                    text = relativePath,
                    color = AppColors.TextSecondary,
                    fontSize = AppType.Caption,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Spacer(Modifier.width(AppSpace.sm))
        SelectionIndicator(isSelected = isSelected)
        Spacer(Modifier.width(AppSpace.sm))
    }
}

@Composable
private fun FolderBooksFolderTile(
    folder: FolderBooksNode,
    compact: Boolean,
    onClick: (FolderBooksNode) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AppRadius.lg))
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) { onClick(folder) }
            .padding(AppSpace.sm)
    ) {
        FolderThumb(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.72f),
            iconSize = if (compact) 28.dp else 34.dp
        )
        Spacer(Modifier.height(AppSpace.sm))
        Text(
            text = folder.name,
            color = AppColors.TextPrimary,
            fontSize = if (compact) AppType.Caption else AppType.BodySmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = stringResource(R.string.folder_books_folder_count, folder.bookCount),
            color = AppColors.TextSecondary,
            fontSize = AppType.Caption,
            maxLines = 1
        )
    }
}

@Composable
private fun FolderBooksBookTile(
    file: AuthorizedFolderFile,
    isSelected: Boolean,
    isImported: Boolean,
    coverPath: String?,
    relativePath: String,
    compact: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.5.dp,
                color = AppColors.Accent.copy(alpha = if (isSelected) 1f else 0f),
                shape = RoundedCornerShape(AppRadius.lg)
            )
            .clip(RoundedCornerShape(AppRadius.lg))
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onClick
            )
            .padding(AppSpace.sm)
    ) {
        ImportBookCoverArt(
            book = file.toSelectedImportBook(),
            shape = RoundedCornerShape(AppRadius.md),
            placeholderFontSize = if (compact) AppType.Caption else AppType.BodySmall,
            coverPathOverride = coverPath,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.72f)
        )
        Spacer(Modifier.height(AppSpace.sm))
        Text(
            text = file.name.substringBeforeLast('.', missingDelimiterValue = file.name),
            color = AppColors.TextPrimary,
            fontSize = if (compact) AppType.Caption else AppType.BodySmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(2.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = file.extensionLabel(),
                color = AppColors.TextSecondary,
                fontSize = AppType.Caption,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (isImported) {
                Spacer(Modifier.width(AppSpace.xs))
                ImportedBadge()
            }
        }
        if (relativePath.isNotBlank()) {
            Text(
                text = relativePath,
                color = AppColors.TextSecondary,
                fontSize = AppType.Caption,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun FolderThumb(
    modifier: Modifier = Modifier,
    iconSize: androidx.compose.ui.unit.Dp = 24.dp
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(AppRadius.sm))
            .background(AppColors.BgGray),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = AppIcons.Folder,
            contentDescription = null,
            tint = AppColors.Accent,
            modifier = Modifier.size(iconSize)
        )
    }
}

/** Compact radio-style marker: selected rows show a white check inside the accent circle. */
@Composable
private fun SelectionIndicator(isSelected: Boolean) {
    Box(
        modifier = Modifier
            .size(20.dp)
            .clip(CircleShape)
            .then(
                if (isSelected) {
                    Modifier.background(AppColors.Accent)
                } else {
                    Modifier.border(
                        width = 1.5.dp,
                        color = AppColors.TextSecondary.copy(alpha = 0.6f),
                        shape = CircleShape
                    )
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        if (isSelected) {
            Icon(
                imageVector = AppIcons.Check,
                contentDescription = null,
                tint = AppColors.OnAccent,
                modifier = Modifier.size(13.dp)
            )
        }
    }
}

@Composable
private fun ImportedBadge() {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(AppRadius.full))
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
private fun FolderBooksConfirmButton(
    enabled: Boolean,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(AppRadius.lg)
    LiquidGlassButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp)
            .then(
                if (enabled) {
                    Modifier.shadow(
                        elevation = 28.dp,
                        shape = shape,
                        clip = false,
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
        Text(
            text = stringResource(R.string.import_confirm),
            color = if (enabled) AppColors.OnAccent else AppColors.TextSecondary,
            fontSize = AppType.Body,
            fontWeight = FontWeight.SemiBold
        )
    }
}

private fun toggleSelection(selection: Set<String>, uri: String): Set<String> =
    if (uri in selection) selection - uri else selection + uri

private fun toggleSelection(
    selection: Set<String>,
    books: List<AuthorizedFolderFile>
): Set<String> {
    val uris = books.mapTo(linkedSetOf()) { it.uri.toString() }
    if (uris.isEmpty()) return selection
    return if (selection.containsAll(uris)) selection - uris else selection + uris
}

private fun AuthorizedFolderFile.folderBooksKey(): String = "file:$uri"

private fun AuthorizedFolderFile.toFolderBooksFile() = FolderBooksFile(
    key = folderBooksKey(),
    name = name,
    treeUri = treeUri,
    folderName = folderName,
    relativeDirectory = relativeDirectory,
    size = size,
    lastModified = lastModified
)

private fun AuthorizedFolderFile.extensionLabel(): String =
    name.substringAfterLast('.', missingDelimiterValue = "").uppercase()

private fun AuthorizedFolderFile.bookMeta(): String {
    val extension = extensionLabel()
    val size = FileUtils.formatFileSize(size).takeIf { size > 0L }
    return listOfNotNull(extension.takeIf { it.isNotBlank() }, size).joinToString(" · ")
}

internal fun AuthorizedFolderFile.toSelectedImportBook() = SelectedImportBook(
    uri = uri,
    name = name,
    sourceDirectoryUri = treeUri,
    sourceDirectoryName = folderName,
    sourceRelativeDirectory = relativeDirectory,
    sourceDirectoryDocumentUri = parentDocumentUri,
    sourceDocumentKey = documentKey,
    sourceLastModified = lastModified,
    sourceSize = size,
    sourceDirectoryBindings = bindings
)
