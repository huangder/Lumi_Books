package com.huangder.lumibooks.ui.home

import android.net.Uri
import android.provider.DocumentsContract
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.huangder.lumibooks.R
import com.huangder.lumibooks.ui.components.LiquidGlassButton
import com.huangder.lumibooks.ui.components.LiquidGlassDialog
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
    val name: String
)

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
        val authorizedDirectoryLabel = remember(authorizedDirectoryUris) {
            authorizedDirectoryUris
                .map(::formatAuthorizedDirectory)
                .distinct()
                .joinToString(separator = "\u3001")
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

private fun formatAuthorizedDirectory(uriString: String): String {
    return runCatching {
        val treeId = DocumentsContract.getTreeDocumentId(Uri.parse(uriString))
        val volume = treeId.substringBefore(':', missingDelimiterValue = "")
        val relativePath = treeId.substringAfter(':', missingDelimiterValue = treeId)
            .trim('/')
        when {
            volume.equals("primary", ignoreCase = true) && relativePath.isNotBlank() ->
                "\u5185\u90e8\u5b58\u50a8/$relativePath"
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
    onBookSelectionToggle: (SelectedImportBook) -> Unit,
    onSelectAll: () -> Unit,
    onConfirmImport: () -> Unit
) {
    val isLiquidGlass = LocalAppTheme.current == "liquid_glass"
    val coverBackdrop = rememberLayerBackdrop()

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 28.dp, end = 28.dp, top = 26.dp, bottom = 16.dp),
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
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (isLiquidGlass) Modifier.layerBackdrop(coverBackdrop)
                        else Modifier
                    ),
                contentPadding = PaddingValues(start = 28.dp, end = 28.dp, bottom = 118.dp),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                verticalArrangement = Arrangement.spacedBy(26.dp)
            ) {
                items(selectedBooks, key = { it.uri.toString() }) { book ->
                    SelectedBookPreview(
                        book = book,
                        isSelected = book.uri.toString() in selectedBookUris,
                        onSelectionToggle = { onBookSelectionToggle(book) }
                    )
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
    onSelectionToggle: () -> Unit
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

    val selectionAlpha by animateFloatAsState(
        targetValue = if (isSelected) 1f else 0f,
        animationSpec = tween(160),
        label = "importBookSelectionOutline"
    )
    val selectionShape = RoundedCornerShape(24.dp)

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
            .padding(8.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.72f)
                .clip(RoundedCornerShape(20.dp))
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
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = displayTitle,
            color = AppColors.TextPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = extension.ifEmpty { stringResource(R.string.import_supported_formats) },
            color = AppColors.TextSecondary,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

