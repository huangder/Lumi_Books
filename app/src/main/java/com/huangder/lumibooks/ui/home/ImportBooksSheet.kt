package com.huangder.lumibooks.ui.home

import android.net.Uri
import android.provider.DocumentsContract
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.padding
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Sort
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.huangder.lumibooks.R
import com.huangder.lumibooks.ui.components.LiquidGlassMenuItem
import com.huangder.lumibooks.ui.components.LiquidGlassMenuSpec
import com.huangder.lumibooks.ui.components.LiquidGlassButton
import com.huangder.lumibooks.ui.components.LiquidGlassDialog
import com.huangder.lumibooks.ui.components.LiquidGlassIconButton
import com.huangder.lumibooks.ui.components.LiquidGlassSurface
import com.huangder.lumibooks.ui.components.LocalLiquidGlassMenuHost
import com.huangder.lumibooks.ui.components.ProvideLiquidGlassBackdrop
import com.huangder.lumibooks.ui.theme.AppColors
import com.huangder.lumibooks.ui.theme.LocalAppTheme
import com.huangder.lumibooks.util.parser.BookParserFactory
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import coil.compose.AsyncImage
import com.huangder.lumibooks.domain.model.BookFormat

/** A file waiting for confirmation in the multi-book import flow. */
data class SelectedImportBook(
    val uri: Uri,
    val name: String,
    val sourceDirectoryUri: String? = null,
    val sourceDirectoryName: String? = null,
    val sourceRelativeDirectory: String? = null,
    val sourceDirectoryDocumentUri: String? = null,
    val sourceDocumentKey: String? = null,
    val sourceLastModified: Long = 0L,
    val sourceSize: Long = 0L,
    val sourceDirectoryBindings: List<com.huangder.lumibooks.domain.repository.FolderRepository.StorageBinding> = emptyList()
) {
    val lastModified: Long get() = sourceLastModified
    val documentKey: String? get() = sourceDocumentKey
    val physicalParentUri: String? get() = sourceDirectoryDocumentUri
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportDestinationSheet(
    primaryLabel: String,
    primaryDetail: String? = null,
    onPrimary: () -> Unit,
    onRoot: () -> Unit,
    onDismiss: () -> Unit
) {
    ImportBooksContainer(expanded = false, onDismiss = onDismiss) { _ ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 22.dp)
        ) {
            Text(
                text = stringResource(R.string.import_destination_title),
                color = AppColors.TextPrimary,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(18.dp))
            ImportDestinationButton(
                text = primaryLabel,
                detail = primaryDetail,
                onClick = onPrimary,
                primary = true
            )
            Spacer(Modifier.height(12.dp))
            ImportDestinationButton(
                text = stringResource(R.string.import_to_root),
                detail = null,
                onClick = onRoot,
                primary = false
            )
        }
    }
}

@Composable
private fun ImportDestinationButton(
    text: String,
    detail: String?,
    onClick: () -> Unit,
    primary: Boolean
) {
    LiquidGlassButton(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 58.dp),
        shape = RoundedCornerShape(16.dp),
        tintedColor = if (primary) AppColors.Accent else AppColors.BgGray,
        prominentShadow = primary,
        contentColor = if (primary) AppColors.OnAccent else AppColors.TextPrimary
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = text,
                color = if (primary) AppColors.OnAccent else AppColors.TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            detail?.let {
                Text(
                    text = it,
                    color = if (primary) AppColors.OnAccent.copy(alpha = 0.78f) else AppColors.TextSecondary,
                    fontSize = 12.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportBooksActionSheet(
    isPreparing: Boolean,
    authorizedDirectoryUris: List<String>,
    onDismiss: () -> Unit,
    onSelectFiles: () -> Unit,
    onAuthorizeDirectory: () -> Unit,
    onRefreshDirectories: () -> Unit
) {
    ImportBooksContainer(
        expanded = false,
        onDismiss = onDismiss
    ) { floatingContainer ->
        ImportActionsStage(
            floatingContainer = floatingContainer,
            isPreparing = isPreparing,
            authorizedDirectoryUris = authorizedDirectoryUris,
            onSelectFiles = onSelectFiles,
            onAuthorizeDirectory = onAuthorizeDirectory,
            onRefreshDirectories = onRefreshDirectories
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportBooksConfirmationSheet(
    selectedBooks: List<SelectedImportBook>,
    selectedBookUris: Set<String>,
    layoutMode: Int,
    onLayoutModeChange: (Int) -> Unit,
    onBookSelectionToggle: (SelectedImportBook) -> Unit,
    onSelectAll: () -> Unit,
    onDismiss: () -> Unit,
    onConfirmImport: () -> Unit
) {
    if (selectedBooks.isEmpty()) return
    ImportBooksContainer(
        expanded = true,
        onDismiss = onDismiss
    ) { floatingContainer ->
        SelectedBooksStage(
            selectedBooks = selectedBooks,
            selectedBookUris = selectedBookUris,
            floatingContainer = floatingContainer,
            layoutMode = layoutMode,
            onLayoutModeChange = onLayoutModeChange,
            onBookSelectionToggle = onBookSelectionToggle,
            onSelectAll = onSelectAll,
            onConfirmImport = onConfirmImport
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ImportBooksContainer(
    expanded: Boolean,
    onDismiss: () -> Unit,
    content: @Composable (floatingContainer: Boolean) -> Unit
) {
    val isLiquidGlass = LocalAppTheme.current == "liquid_glass"
    val shape = RoundedCornerShape(
        topStart = 28.dp,
        topEnd = 28.dp,
        bottomStart = if (isLiquidGlass) 28.dp else 0.dp,
        bottomEnd = if (isLiquidGlass) 28.dp else 0.dp
    )
    val sizeModifier = Modifier
        .widthIn(max = 480.dp)
        .fillMaxWidth()
        .then(if (expanded) Modifier.fillMaxHeight(0.82f) else Modifier)

    if (isLiquidGlass) {
        LiquidGlassDialog(
            onDismissRequest = onDismiss,
            alignment = Alignment.BottomCenter,
            shape = shape,
            modifier = sizeModifier,
            backgroundBlurRadius = 0.dp,
            backgroundScrimColor = Color.Black.copy(alpha = 0.16f),
            contentScrimColor = AppColors.CardBg.copy(alpha = 0.70f)
        ) {
            content(true)
        }
    } else {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState = sheetState,
            shape = shape,
            containerColor = AppColors.CardBg,
            contentColor = AppColors.TextPrimary,
            tonalElevation = 0.dp,
            scrimColor = Color.Black.copy(alpha = 0.16f),
            sheetMaxWidth = 480.dp,
            dragHandle = null
        ) {
            Box(modifier = sizeModifier) {
                content(false)
            }
        }
    }
}

@Composable
private fun ImportActionsStage(
    floatingContainer: Boolean,
    isPreparing: Boolean,
    authorizedDirectoryUris: List<String>,
    onSelectFiles: () -> Unit,
    onAuthorizeDirectory: () -> Unit,
    onRefreshDirectories: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(
                start = 28.dp,
                end = 28.dp,
                top = 34.dp,
                bottom = if (floatingContainer) 28.dp else 24.dp
            )
    ) {
        Text(
            text = stringResource(R.string.import_books),
            color = AppColors.TextPrimary,
            fontSize = 25.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.import_dialog_description),
            color = AppColors.TextSecondary,
            fontSize = 13.sp,
            lineHeight = 19.sp
        )
        Spacer(Modifier.height(28.dp))
        ImportActionButton(
            text = stringResource(R.string.import_select_files),
            onClick = onSelectFiles,
            primary = true,
            enabled = !isPreparing
        )
        Spacer(Modifier.height(12.dp))
        ImportActionButton(
            text = stringResource(R.string.import_authorize_directory),
            onClick = onAuthorizeDirectory,
            enabled = !isPreparing
        )
        Spacer(Modifier.height(12.dp))
        ImportActionButton(
            text = stringResource(R.string.import_refresh_directories),
            onClick = onRefreshDirectories,
            enabled = !isPreparing
        )
        if (isPreparing) {
            Spacer(Modifier.height(18.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.width(20.dp),
                    color = AppColors.Accent,
                    strokeWidth = 2.dp
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = stringResource(R.string.import_scanning_directory),
                    color = AppColors.TextSecondary,
                    fontSize = 13.sp
                )
            }
        }
        Spacer(Modifier.height(18.dp))
        val internalStorageLabel = stringResource(R.string.book_detail_internal_storage)
        val listSeparator = stringResource(R.string.list_separator)
        val authorizedDirectoryLabel = remember(authorizedDirectoryUris, internalStorageLabel, listSeparator) {
            authorizedDirectoryUris
                .map { formatAuthorizedDirectory(it, internalStorageLabel) }
                .distinct()
                .joinToString(separator = listSeparator)
        }
        Text(
            text = if (authorizedDirectoryLabel.isBlank()) {
                stringResource(R.string.import_authorized_directories_none)
            } else {
                stringResource(R.string.import_authorized_directories_current, authorizedDirectoryLabel)
            },
            color = AppColors.TextSecondary,
            fontSize = 12.sp,
            lineHeight = 17.sp
        )
    }
}

private fun formatAuthorizedDirectory(uriString: String, internalStorageLabel: String): String {
    return runCatching {
        val treeId = DocumentsContract.getTreeDocumentId(Uri.parse(uriString))
        val volume = treeId.substringBefore(':', missingDelimiterValue = "")
        val relativePath = treeId.substringAfter(':', missingDelimiterValue = treeId)
            .trim('/')
        when {
            volume.equals("primary", ignoreCase = true) && relativePath.isNotBlank() ->
                "$internalStorageLabel/$relativePath"
            volume.isNotBlank() && relativePath.isNotBlank() -> "$volume/$relativePath"
            relativePath.isNotBlank() -> relativePath
            else -> treeId
        }
    }.getOrElse { uriString }
}

@Composable
private fun ImportActionButton(
    text: String,
    onClick: () -> Unit,
    primary: Boolean = false,
    enabled: Boolean = true,
    floatingShadow: Boolean = false
) {
    val shape = RoundedCornerShape(16.dp)
    val activePrimary = primary && enabled
    val buttonColor = if (activePrimary) AppColors.Accent else AppColors.BgGray
    val labelColor = if (activePrimary) AppColors.OnAccent else if (enabled) {
        AppColors.TextPrimary
    } else {
        AppColors.TextSecondary
    }
    LiquidGlassButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp)
            .then(
                if (floatingShadow && enabled) {
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
        tintedColor = buttonColor,
        prominentShadow = activePrimary,
        contentColor = labelColor
    ) {
        Text(
            text = text,
            color = labelColor,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun SelectedBooksStage(
    selectedBooks: List<SelectedImportBook>,
    selectedBookUris: Set<String>,
    floatingContainer: Boolean,
    layoutMode: Int,
    onLayoutModeChange: (Int) -> Unit,
    onBookSelectionToggle: (SelectedImportBook) -> Unit,
    onSelectAll: () -> Unit,
    onConfirmImport: () -> Unit
) {
    val isLiquidGlass = LocalAppTheme.current == "liquid_glass"
    val coverBackdrop = rememberLayerBackdrop()
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var sortBy by rememberSaveable { mutableStateOf(ImportBookSort.DEFAULT) }
    val normalizedLayoutMode = layoutMode.coerceIn(1, 3)

    val visibleBooks = remember(selectedBooks, searchQuery, sortBy) {
        val sorted = when (sortBy) {
            ImportBookSort.DEFAULT -> selectedBooks
            ImportBookSort.NAME -> selectedBooks.sortedBy { it.name.lowercase() }
            ImportBookSort.FORMAT -> selectedBooks.sortedBy {
                it.name.substringAfterLast('.', missingDelimiterValue = "").lowercase()
            }
            ImportBookSort.TIME -> selectedBooks.sortedWith(
                compareByDescending<SelectedImportBook> { it.sourceLastModified > 0L }
                    .thenByDescending { it.sourceLastModified }
                    .thenBy { it.name.lowercase() }
            )
        }
        val query = searchQuery.trim()
        if (query.isEmpty()) sorted
        else sorted.filter { it.name.contains(query, ignoreCase = true) }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 28.dp, end = 28.dp, top = 26.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.import_selected_count, selectedBookUris.size),
                    color = AppColors.TextPrimary,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(16.dp))
                ImportLayoutSwitchButton(
                    layoutMode = normalizedLayoutMode,
                    onLayoutModeChange = onLayoutModeChange
                )
                Spacer(Modifier.width(10.dp))
                LiquidGlassButton(
                    onClick = onSelectAll,
                    modifier = Modifier.height(44.dp),
                    shape = RoundedCornerShape(50),
                    tintedColor = AppColors.Accent,
                    prominentShadow = false,
                    contentColor = AppColors.OnAccent
                ) {
                    Text(
                        text = stringResource(R.string.import_select_all),
                        color = AppColors.OnAccent,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 28.dp, end = 28.dp, bottom = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ImportSearchField(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(10.dp))
                ImportSortButton(
                    sortBy = sortBy,
                    onSortChange = { sortBy = it }
                )
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .then(
                        if (isLiquidGlass) Modifier.layerBackdrop(coverBackdrop)
                        else Modifier
                    )
            ) {
                AnimatedContent(
                    targetState = normalizedLayoutMode,
                    modifier = Modifier.fillMaxSize(),
                    transitionSpec = {
                        // 当前排列缩小淡出，下一个排列放大淡入
                        val enter = fadeIn(tween(200, delayMillis = 60)) +
                            scaleIn(
                                initialScale = 0.94f,
                                animationSpec = tween(240, delayMillis = 60)
                            )
                        val exit = fadeOut(tween(140)) +
                            scaleOut(
                                targetScale = 0.92f,
                                animationSpec = tween(180)
                            )
                        enter togetherWith exit
                    },
                    label = "importLayoutModeTransition"
                ) { mode ->
                    when (mode) {
                        1 -> LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(
                                start = 28.dp,
                                end = 28.dp,
                                bottom = 118.dp
                            ),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            lazyListItems(visibleBooks, key = { it.uri.toString() }) { book ->
                                SelectedBookListRow(
                                    book = book,
                                    isSelected = book.uri.toString() in selectedBookUris,
                                    onSelectionToggle = { onBookSelectionToggle(book) }
                                )
                            }
                        }
                        else -> LazyVerticalGrid(
                            columns = GridCells.Fixed(mode),
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(
                                start = 28.dp,
                                end = 28.dp,
                                bottom = 118.dp
                            ),
                            horizontalArrangement = Arrangement.spacedBy(
                                if (mode == 3) 12.dp else 18.dp
                            ),
                            verticalArrangement = Arrangement.spacedBy(
                                if (mode == 3) 18.dp else 26.dp
                            )
                        ) {
                            items(visibleBooks, key = { it.uri.toString() }) { book ->
                                SelectedBookPreview(
                                    book = book,
                                    isSelected = book.uri.toString() in selectedBookUris,
                                    compact = mode == 3,
                                    onSelectionToggle = { onBookSelectionToggle(book) }
                                )
                            }
                        }
                    }
                }

                if (visibleBooks.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 64.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.search_no_results),
                            color = AppColors.TextSecondary,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(
                    start = 28.dp,
                    end = 28.dp,
                    bottom = if (floatingContainer) 22.dp else 18.dp
                )
        ) {
            ProvideLiquidGlassBackdrop(coverBackdrop.takeIf { isLiquidGlass }) {
                ImportActionButton(
                    text = stringResource(R.string.import_confirm),
                    onClick = onConfirmImport,
                    primary = true,
                    enabled = selectedBookUris.isNotEmpty(),
                    floatingShadow = true
                )
            }
        }
    }
}

@Composable
private fun SelectedBookPreview(
    book: SelectedImportBook,
    isSelected: Boolean,
    compact: Boolean = false,
    onSelectionToggle: () -> Unit
) {
    val extension = book.name.substringAfterLast('.', missingDelimiterValue = "").uppercase()
    val displayTitle = book.name.substringBeforeLast('.', missingDelimiterValue = book.name)

    val selectionAlpha by animateFloatAsState(
        targetValue = if (isSelected) 1f else 0f,
        animationSpec = tween(160),
        label = "importBookSelectionOutline"
    )
    val selectionShape = RoundedCornerShape(if (compact) 20.dp else 24.dp)
    val contentPadding = if (compact) 6.dp else 8.dp

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 2.dp,
                color = AppColors.Accent.copy(alpha = selectionAlpha),
                shape = selectionShape
            )
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onSelectionToggle
            )
            .padding(contentPadding)
    ) {
        ImportBookCoverArt(
            book = book,
            shape = RoundedCornerShape(if (compact) 16.dp else 20.dp),
            placeholderFontSize = if (compact) 12.sp else 15.sp,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.72f)
        )
        Spacer(Modifier.height(if (compact) 6.dp else 10.dp))
        Text(
            text = displayTitle,
            color = AppColors.TextPrimary,
            fontSize = if (compact) 13.sp else 16.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(if (compact) 1.dp else 2.dp))
        Text(
            text = extension.ifEmpty { stringResource(R.string.import_supported_formats) },
            color = AppColors.TextSecondary,
            fontSize = if (compact) 10.sp else 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private enum class ImportBookSort {
    DEFAULT,
    NAME,
    FORMAT,
    TIME
}

@Composable
private fun ImportLayoutSwitchButton(
    layoutMode: Int,
    onLayoutModeChange: (Int) -> Unit
) {
    val isLiquidGlass = LocalAppTheme.current == "liquid_glass"
    val layoutModeDescription = stringResource(R.string.import_layout_mode)
    LiquidGlassSurface(
        shape = CircleShape,
        fallbackColor = if (isLiquidGlass) AppColors.CardBg else AppColors.Accent,
        contentScrimColor = AppColors.CardBg.copy(alpha = 0.58f),
        onClick = { onLayoutModeChange(layoutMode % 3 + 1) },
        effectPadding = 1.dp,
        modifier = Modifier
            .size(44.dp)
            .semantics {
                contentDescription = layoutModeDescription
            }
    ) {
        Text(
            text = layoutMode.toString(),
            color = if (isLiquidGlass) AppColors.TextPrimary else AppColors.OnAccent,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun ImportSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    LiquidGlassSurface(
        shape = CircleShape,
        fallbackColor = AppColors.CardBg,
        contentScrimColor = AppColors.CardBg.copy(alpha = 0.72f),
        effectPadding = 1.dp,
        modifier = modifier.height(44.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 4.dp),
            singleLine = true,
            textStyle = TextStyle(
                color = AppColors.TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            ),
            cursorBrush = SolidColor(AppColors.Accent),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { keyboardController?.hide() }),
            decorationBox = { innerTextField ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        if (query.isEmpty()) {
                            Text(
                                text = stringResource(R.string.import_search_placeholder),
                                color = AppColors.TextSecondary,
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        innerTextField()
                    }
                    Icon(
                        imageVector = Icons.Outlined.Search,
                        contentDescription = stringResource(R.string.search),
                        tint = AppColors.TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                    if (query.isNotEmpty()) {
                        Spacer(Modifier.width(4.dp))
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
                                imageVector = Icons.Filled.Close,
                                contentDescription = stringResource(R.string.import_search_clear),
                                tint = AppColors.TextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        )
    }
}

@Composable
private fun ImportSortButton(
    sortBy: ImportBookSort,
    onSortChange: (ImportBookSort) -> Unit
) {
    val isLiquidGlass = LocalAppTheme.current == "liquid_glass"
    val menuHost = LocalLiquidGlassMenuHost.current
    var sortAnchorBounds by remember { mutableStateOf(Rect.Zero) }
    var sortExpanded by remember { mutableStateOf(false) }
    val defaultLabel = stringResource(R.string.import_sort_default)
    val nameLabel = stringResource(R.string.import_sort_name)
    val formatLabel = stringResource(R.string.import_sort_format)
    val timeLabel = stringResource(R.string.import_sort_time)

    DisposableEffect(menuHost) {
        onDispose {
            if (sortExpanded) menuHost?.dismiss()
        }
    }

    Box {
        LiquidGlassIconButton(
            imageVector = Icons.Outlined.Sort,
            contentDescription = stringResource(R.string.import_sort),
            onClick = {
                if (isLiquidGlass && menuHost != null && sortAnchorBounds != Rect.Zero) {
                    if (sortExpanded) {
                        menuHost.dismiss()
                    } else {
                        sortExpanded = true
                        menuHost.show(
                            LiquidGlassMenuSpec(
                                anchorBounds = sortAnchorBounds,
                                width = 176.dp,
                                maxVisibleItems = 8,
                                onDismiss = { sortExpanded = false },
                                items = ImportBookSort.entries.map { option ->
                                    LiquidGlassMenuItem(
                                        label = when (option) {
                                            ImportBookSort.DEFAULT -> defaultLabel
                                            ImportBookSort.NAME -> nameLabel
                                            ImportBookSort.FORMAT -> formatLabel
                                            ImportBookSort.TIME -> timeLabel
                                        },
                                        selected = option == sortBy,
                                        onClick = { onSortChange(option) }
                                    )
                                }
                            )
                        )
                    }
                } else {
                    sortExpanded = true
                }
            },
            size = 44.dp,
            iconSize = 20.dp,
            normalContainerColor = AppColors.BgGray,
            liquidContainerColor = AppColors.CardBg,
            liquidScrimColor = AppColors.CardBg.copy(alpha = 0.58f),
            modifier = Modifier.onGloballyPositioned { sortAnchorBounds = it.boundsInRoot() }
        )

        if (!isLiquidGlass) {
            DropdownMenu(
                expanded = sortExpanded,
                onDismissRequest = { sortExpanded = false },
                modifier = Modifier.width(176.dp),
                shape = RoundedCornerShape(16.dp),
                containerColor = AppColors.WindowBg,
                tonalElevation = 0.dp,
                shadowElevation = 0.dp
            ) {
                ImportBookSort.entries.forEach { option ->
                    val label = when (option) {
                        ImportBookSort.DEFAULT -> defaultLabel
                        ImportBookSort.NAME -> nameLabel
                        ImportBookSort.FORMAT -> formatLabel
                        ImportBookSort.TIME -> timeLabel
                    }
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = label,
                                color = if (option == sortBy) AppColors.Accent else AppColors.TextPrimary,
                                fontSize = 14.sp
                            )
                        },
                        onClick = {
                            sortExpanded = false
                            onSortChange(option)
                        },
                        trailingIcon = if (option == sortBy) {
                            {
                                Icon(
                                    imageVector = Icons.Filled.Check,
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
            }
        }
    }
}

@Composable
private fun SelectedBookListRow(
    book: SelectedImportBook,
    isSelected: Boolean,
    onSelectionToggle: () -> Unit
) {
    val extension = book.name.substringAfterLast('.', missingDelimiterValue = "").uppercase()
    val displayTitle = book.name.substringBeforeLast('.', missingDelimiterValue = book.name)
    val selectionAlpha by animateFloatAsState(
        targetValue = if (isSelected) 1f else 0f,
        animationSpec = tween(160),
        label = "importBookListSelection"
    )
    val shape = RoundedCornerShape(20.dp)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 2.dp,
                color = AppColors.Accent.copy(alpha = selectionAlpha),
                shape = shape
            )
            .clip(shape)
            .background(AppColors.CardBg)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onSelectionToggle
            )
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ImportBookCoverArt(
            book = book,
            shape = RoundedCornerShape(14.dp),
            placeholderFontSize = 11.sp,
            modifier = Modifier
                .width(46.dp)
                .height(64.dp)
        )
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = displayTitle,
                color = AppColors.TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = extension.ifEmpty { stringResource(R.string.import_supported_formats) },
                color = AppColors.TextSecondary,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(12.dp))
        Box(
            modifier = Modifier
                .size(26.dp)
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
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = AppColors.OnAccent,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun ImportBookCoverArt(
    book: SelectedImportBook,
    shape: RoundedCornerShape,
    placeholderFontSize: androidx.compose.ui.unit.TextUnit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val extension = book.name.substringAfterLast('.', missingDelimiterValue = "").uppercase()
    val displayTitle = book.name.substringBeforeLast('.', missingDelimiterValue = book.name)
    val coverPath by produceState<String?>(
        initialValue = null,
        key1 = book.uri,
        key2 = extension
    ) {
        val format = runCatching { BookFormat.valueOf(extension) }.getOrNull()
        if (format == BookFormat.EPUB || format == BookFormat.PDF || format == BookFormat.MOBI) {
            value = withContext(Dispatchers.IO) {
                runCatching {
                    val parser = BookParserFactory.createParser(format, context)
                    try {
                        parser.extractCoverPath(book.uri.toString())
                    } finally {
                        runCatching { parser.close() }
                    }
                }.getOrNull()
            }
        }
    }
    val coverBrush = when (extension) {
        "EPUB" -> Brush.linearGradient(listOf(Color(0xFF9BB7D4), Color(0xFF657D9A)))
        "PDF" -> Brush.linearGradient(listOf(Color(0xFFDFA19C), Color(0xFFB95E5B)))
        "MOBI" -> Brush.linearGradient(listOf(Color(0xFFA8C3A0), Color(0xFF5F8A5C)))
        else -> Brush.linearGradient(listOf(Color(0xFFB8B0D6), Color(0xFF8179A8)))
    }

    Box(
        modifier = modifier
            .clip(shape)
            .background(coverBrush),
        contentAlignment = Alignment.Center
    ) {
        if (coverPath != null) {
            AsyncImage(
                model = coverPath,
                contentDescription = displayTitle,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Text(
                text = extension.ifEmpty { "BOOK" },
                color = Color.White.copy(alpha = 0.88f),
                fontSize = placeholderFontSize,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

