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
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DriveFileMove
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.HideImage
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material3.Icon
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
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
import com.huangder.lumibooks.util.AuthorizedStorageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun FolderNameDialog(
    title: String,
    initialName: String = "",
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    storageChoices: List<Pair<String, String?>> = emptyList(),
    onConfirmWithStorage: ((String, String?) -> Unit)? = null
) {
    var selectedStorage by remember(storageChoices) { mutableStateOf(storageChoices.firstOrNull()?.second) }
    val context = LocalContext.current
    val resolvedStorageChoices by produceState(
        initialValue = storageChoices,
        key1 = storageChoices,
        key2 = context
    ) {
        val manager = AuthorizedStorageManager()
        value = withContext(Dispatchers.IO) {
            storageChoices.map { (label, uri) ->
                if (uri.isNullOrBlank()) {
                    label to uri
                } else {
                    val displayName = runCatching {
                        manager.queryDisplayName(context, manager.treeRootUri(android.net.Uri.parse(uri)))
                    }.getOrNull()?.takeIf { it.isNotBlank() }
                    val fallbackName = uri.substringAfterLast('/').let(android.net.Uri::decode)
                        .substringAfterLast(':')
                        .ifBlank { uri }
                    context.getString(R.string.folder_storage_authorized, displayName ?: fallbackName) to uri
                }
            }
        }
    }
    LiquidGlassDialog(
        onDismissRequest = onDismiss,
        backgroundScrimColor = Color.Transparent,
        backgroundBlurRadius = 18.dp,
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 520.dp),
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 520.dp),
            verticalArrangement = Arrangement.spacedBy(AppSpace.sm)
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
                onConfirm = { values ->
                    val name = values.firstOrNull().orEmpty()
                    if (onConfirmWithStorage != null) onConfirmWithStorage(name, selectedStorage)
                    else onConfirm(name)
                }
            )
            if (storageChoices.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .offset(y = (-8).dp)
                        .padding(horizontal = AppSpace.sm)
                ) {
                    Text(
                        text = stringResource(R.string.folder_storage_section_title),
                        color = AppColors.TextSecondary,
                        fontSize = AppType.Caption,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(start = AppSpace.xs, bottom = 2.dp)
                    )
                    LiquidGlassSurface(
                        shape = RoundedCornerShape(14.dp),
                        fallbackColor = AppColors.BgGray,
                        contentScrimColor = AppColors.CardBg.copy(alpha = 0.42f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(vertical = 4.dp)) {
                            resolvedStorageChoices.forEach { (label, uri) ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { selectedStorage = uri }
                                        .padding(horizontal = 8.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(selected = selectedStorage == uri, onClick = { selectedStorage = uri })
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(1.dp)
                                    ) {
                                        Text(
                                            text = label,
                                            color = AppColors.TextPrimary,
                                            fontSize = AppType.BodySmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = stringResource(
                                                if (uri.isNullOrBlank()) {
                                                    R.string.folder_storage_virtual_description
                                                } else {
                                                    R.string.folder_storage_authorized_description
                                                }
                                            ),
                                            color = AppColors.TextSecondary,
                                            fontSize = AppType.Caption,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FolderActionsSheet(
    folder: LibraryFolder,
    onDismiss: () -> Unit,
    onRename: () -> Unit,
    onSetCover: () -> Unit,
    onRemoveCover: () -> Unit,
    onMove: () -> Unit,
    onDelete: () -> Unit
) {
    val isLiquidGlass = LocalAppTheme.current == "liquid_glass"
    val content: @Composable () -> Unit = {
        Column(
            modifier = Modifier
                .widthIn(max = 520.dp)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = AppSpace.lg, end = AppSpace.lg, top = AppSpace.md, bottom = AppSpace.lg)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = folder.name,
                        color = AppColors.TextPrimary,
                        fontSize = AppType.Section,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = stringResource(R.string.bookshelf_custom_categories),
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
            FolderActionRow(
                icon = Icons.Outlined.Edit,
                title = stringResource(R.string.rename_folder),
                onClick = onRename
            )
            FolderActionRow(
                icon = Icons.Outlined.Image,
                title = stringResource(
                    if (folder.coverPath == null) R.string.set_folder_cover else R.string.change_folder_cover
                ),
                onClick = onSetCover
            )
            if (folder.coverPath != null) {
                FolderActionRow(
                    icon = Icons.Outlined.HideImage,
                    title = stringResource(R.string.remove_folder_cover),
                    onClick = onRemoveCover
                )
            }
            FolderActionRow(
                icon = Icons.Outlined.DriveFileMove,
                title = stringResource(R.string.move_folder),
                onClick = onMove
            )
            FolderActionRow(
                icon = Icons.Outlined.Delete,
                title = stringResource(R.string.delete_folder),
                tint = Color(0xFFD92D3A),
                onClick = onDelete
            )
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
}

@Composable
private fun FolderActionRow(
    icon: ImageVector,
    title: String,
    tint: Color = AppColors.Accent,
    onClick: () -> Unit
) {
    LiquidGlassSurface(
        shape = RoundedCornerShape(14.dp),
        fallbackColor = AppColors.BgGray,
        contentScrimColor = AppColors.CardBg.copy(alpha = 0.42f),
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .padding(vertical = 3.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(12.dp))
            Text(
                text = title,
                color = if (tint == Color(0xFFD92D3A)) tint else AppColors.TextPrimary,
                fontSize = AppType.Body,
                fontWeight = FontWeight.Medium
            )
        }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FolderRelocationSheet(
    folders: List<LibraryFolder>,
    sourceFolder: LibraryFolder,
    onDismiss: () -> Unit,
    onCreateFolder: (String, String?) -> Unit,
    onMove: (String?) -> Unit
) {
    val isLiquidGlass = LocalAppTheme.current == "liquid_glass"
    val hiddenFolderIds = remember(folders, sourceFolder.id) {
        descendantFolderIds(folders, sourceFolder.id)
    }
    val availableFolders = remember(folders, hiddenFolderIds) {
        folders.filterNot { it.id in hiddenFolderIds }
    }
    var browseFolderId by remember(sourceFolder.id) {
        mutableStateOf(sourceFolder.parentId?.takeIf { parent -> availableFolders.any { it.id == parent } })
    }
    var showCreateDialog by remember { mutableStateOf(false) }
    val currentChildren = remember(availableFolders, browseFolderId) {
        directChildFolders(availableFolders, browseFolderId)
    }
    val currentPath = remember(availableFolders, browseFolderId) {
        folderPath(availableFolders, browseFolderId)
    }

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
                        text = stringResource(R.string.move_folder),
                        color = AppColors.TextPrimary,
                        fontSize = AppType.Section,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = sourceFolder.name,
                        color = AppColors.TextSecondary,
                        fontSize = AppType.Caption,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
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
                enabled = browseFolderId != sourceFolder.parentId,
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
            Row(
                modifier = Modifier
                    .then(if (index == path.lastIndex) Modifier.weight(1f, fill = false) else Modifier)
                    .clickable(
                        enabled = index != path.lastIndex,
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) { onFolderClick(folder) },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = folder.name,
                    color = if (index == path.lastIndex) AppColors.TextPrimary else AppColors.Accent,
                    fontSize = AppType.BodySmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (folder.storageDocumentUri != null) {
                    Icon(
                        imageVector = Icons.Outlined.Link,
                        contentDescription = stringResource(R.string.folder_storage_linked),
                        tint = if (folder.storageMissing) Color(0xFFD92D3A) else AppColors.Accent,
                        modifier = Modifier
                            .padding(start = 3.dp)
                            .size(14.dp)
                    )
                }
            }
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
            if (folder.storageDocumentUri != null) {
                Icon(
                    imageVector = Icons.Outlined.Link,
                    contentDescription = stringResource(R.string.folder_storage_linked),
                    tint = if (folder.storageMissing) Color(0xFFD92D3A) else AppColors.Accent,
                    modifier = Modifier
                        .padding(start = 4.dp)
                        .size(15.dp)
                )
            }
            if (folder.storageMissing) {
                Text(
                    text = stringResource(R.string.folder_storage_missing),
                    color = Color(0xFFD92D3A),
                    fontSize = AppType.Caption,
                    maxLines = 1
                )
            }
            Icon(Icons.Outlined.KeyboardArrowRight, null, tint = AppColors.TextSecondary)
        }
    }
}
