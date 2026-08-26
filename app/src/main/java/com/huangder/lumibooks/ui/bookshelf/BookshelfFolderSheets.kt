package com.huangder.lumibooks.ui.bookshelf

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.huangder.lumibooks.R
import com.huangder.lumibooks.domain.model.LibraryFolder
import com.huangder.lumibooks.ui.components.EditInputDialog
import com.huangder.lumibooks.ui.components.LiquidGlassButton
import com.huangder.lumibooks.ui.components.LiquidGlassDialog
import com.huangder.lumibooks.ui.components.LiquidGlassIconButton
import com.huangder.lumibooks.ui.components.LiquidGlassSurface
import com.huangder.lumibooks.ui.theme.AppColors
import com.huangder.lumibooks.ui.theme.AppSpace
import com.huangder.lumibooks.ui.theme.AppType
import com.huangder.lumibooks.ui.theme.LocalAppTheme

@Composable
internal fun FolderNameDialog(
    title: String,
    initialName: String = "",
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    LiquidGlassDialog(
        onDismissRequest = onDismiss,
        backgroundScrimColor = Color.Transparent,
        backgroundBlurRadius = 18.dp,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        EditInputDialog(
            title = title,
            fields = listOf(
                Triple(
                    stringResource(R.string.folder_name),
                    stringResource(R.string.folder_name_hint),
                    initialName
                )
            ),
            onBack = onDismiss,
            onConfirm = { values -> onConfirm(values.firstOrNull().orEmpty()) }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FolderMoveSheet(
    folders: List<LibraryFolder>,
    selectedBookCount: Int,
    sourceFolderId: String?,
    onDismiss: () -> Unit,
    onCreateFolder: (String, String?) -> Unit,
    onMove: (String?) -> Unit
) {
    val isLiquidGlass = LocalAppTheme.current == "liquid_glass"
    var browseFolderId by remember { mutableStateOf<String?>(null) }
    var showCreateDialog by remember { mutableStateOf(false) }
    val currentChildren = remember(folders, browseFolderId) {
        directChildFolders(folders, browseFolderId)
    }
    val currentPath = remember(folders, browseFolderId) { folderPath(folders, browseFolderId) }

    val content: @Composable () -> Unit = {
        Column(
            modifier = Modifier
                .widthIn(max = 520.dp)
                .fillMaxWidth()
                .heightIn(max = 620.dp)
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = AppSpace.lg, vertical = AppSpace.md)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.move_books),
                        color = AppColors.TextPrimary,
                        fontSize = AppType.Section,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = stringResource(R.string.selected_books_count, selectedBookCount),
                        color = AppColors.TextSecondary,
                        fontSize = AppType.Caption
                    )
                }
                LiquidGlassIconButton(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = stringResource(R.string.close),
                    onClick = onDismiss,
                    size = 40.dp,
                    iconSize = 20.dp,
                    normalContainerColor = AppColors.BgGray
                )
            }

            Spacer(Modifier.height(AppSpace.md))
            FolderBreadcrumb(
                path = currentPath,
                onRootClick = { browseFolderId = null },
                onFolderClick = { browseFolderId = it.id }
            )
            Spacer(Modifier.height(AppSpace.md))

            LiquidGlassSurface(
                shape = RoundedCornerShape(16.dp),
                fallbackColor = AppColors.BgGray,
                contentScrimColor = AppColors.CardBg.copy(alpha = 0.4f),
                onClick = { showCreateDialog = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Outlined.CreateNewFolder, null, tint = AppColors.Accent)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = stringResource(R.string.new_category_folder),
                        color = AppColors.TextPrimary,
                        fontSize = AppType.Body,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(Modifier.height(AppSpace.sm))
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(vertical = AppSpace.sm),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (currentChildren.isEmpty()) {
                    item {
                        Text(
                            text = stringResource(R.string.no_subfolders),
                            color = AppColors.TextSecondary,
                            fontSize = AppType.BodySmall,
                            modifier = Modifier.padding(vertical = AppSpace.lg)
                        )
                    }
                } else {
                    items(currentChildren, key = { it.id }) { folder ->
                        FolderDestinationRow(folder = folder) { browseFolderId = folder.id }
                    }
                }
            }

            LiquidGlassButton(
                onClick = { onMove(browseFolderId) },
                enabled = browseFolderId != sourceFolderId,
                tintedColor = AppColors.Accent,
                contentColor = AppColors.OnAccent,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Text(
                    text = if (browseFolderId == null) {
                        stringResource(R.string.move_to_library_root)
                    } else {
                        stringResource(R.string.move_to_here)
                    },
                    color = AppColors.OnAccent,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }

    if (isLiquidGlass) {
        LiquidGlassDialog(
            onDismissRequest = onDismiss,
            alignment = Alignment.BottomCenter,
            shape = RoundedCornerShape(28.dp),
            contentScrimColor = AppColors.CardBg.copy(alpha = 0.82f),
            backgroundScrimColor = Color.Black.copy(alpha = 0.12f)
        ) { content() }
    } else {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState = sheetState,
            containerColor = AppColors.CardBg,
            contentColor = AppColors.TextPrimary,
            scrimColor = Color.Black.copy(alpha = 0.12f),
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        ) { content() }
    }

    if (showCreateDialog) {
        FolderNameDialog(
            title = stringResource(R.string.new_category_folder),
            onDismiss = { showCreateDialog = false },
            onConfirm = { name ->
                onCreateFolder(name, browseFolderId)
                showCreateDialog = false
            }
        )
    }
}

@Composable
internal fun FolderBreadcrumb(
    path: List<LibraryFolder>,
    onRootClick: () -> Unit,
    onFolderClick: (LibraryFolder) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(R.string.bookshelf_title),
            color = AppColors.Accent,
            fontSize = AppType.BodySmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onRootClick
            )
        )
        path.forEachIndexed { index, folder ->
            Icon(
                imageVector = Icons.Outlined.KeyboardArrowRight,
                contentDescription = null,
                tint = AppColors.TextSecondary,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = folder.name,
                color = if (index == path.lastIndex) AppColors.TextPrimary else AppColors.Accent,
                fontSize = AppType.BodySmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .then(if (index == path.lastIndex) Modifier.weight(1f, fill = false) else Modifier)
                    .clickable(
                        enabled = index != path.lastIndex,
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) { onFolderClick(folder) }
            )
        }
    }
}

@Composable
private fun FolderDestinationRow(folder: LibraryFolder, onClick: () -> Unit) {
    LiquidGlassSurface(
        shape = RoundedCornerShape(16.dp),
        fallbackColor = AppColors.CardBg,
        contentScrimColor = AppColors.CardBg.copy(alpha = 0.5f),
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Outlined.Folder, null, tint = AppColors.Accent)
            Spacer(Modifier.width(12.dp))
            Text(
                text = folder.name,
                color = AppColors.TextPrimary,
                fontSize = AppType.Body,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Icon(Icons.Outlined.KeyboardArrowRight, null, tint = AppColors.TextSecondary)
        }
    }
}
