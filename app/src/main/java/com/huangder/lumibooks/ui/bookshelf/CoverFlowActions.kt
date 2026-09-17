package com.huangder.lumibooks.ui.bookshelf

import com.huangder.lumibooks.ui.components.liquidGlassMenuAnchor

import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.huangder.lumibooks.R
import com.huangder.lumibooks.ui.animation.coverFlowEntranceItem
import com.huangder.lumibooks.domain.model.Book
import com.huangder.lumibooks.domain.model.LibraryFolder
import com.huangder.lumibooks.ui.components.*
import com.huangder.lumibooks.ui.icons.AppIcons
import com.huangder.lumibooks.ui.theme.AppColors
import com.huangder.lumibooks.util.FileUtils

/** Callbacks deliberately terminate in BookshelfScreen's existing operations and dialogs. */
internal data class CoverFlowBookActions(
    val open: (Book, Rect?) -> Unit,
    val details: (Book) -> Unit,
    val favorite: (Book) -> Unit,
    val editInfo: (Book) -> Unit,
    val customCover: (Book) -> Unit,
    val removeCustomCover: (Book) -> Unit,
    val tags: (Book) -> Unit,
    val notes: (Book) -> Unit,
    val move: (Book) -> Unit,
    val delete: (Book) -> Unit
)

@Composable
internal fun CoverFlowActionBar(
    book: Book,
    actions: CoverFlowBookActions,
    onOpen: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val menuHost = LocalLiquidGlassMenuHost.current
    var moreBounds by remember { mutableStateOf(Rect.Zero) }
    val editLabel = stringResource(R.string.edit_book_info)
    val coverLabel = stringResource(R.string.custom_cover)
    val removeCoverLabel = stringResource(R.string.remove_custom_cover)
    val tagsLabel = stringResource(R.string.add_tag)
    val notesLabel = stringResource(R.string.bookmarks_notes)
    val moveLabel = stringResource(R.string.move_to_folder)
    val deleteLabel = stringResource(R.string.delete)
    var ownsMenu by remember { mutableStateOf(false) }
    DisposableEffect(menuHost) {
        onDispose { if (ownsMenu) menuHost?.dismiss() }
    }
    fun run(action: (Book) -> Unit) { onDismiss(); action(book) }
    LiquidGlassSurface(
        shape = RoundedCornerShape(22.dp),
        fallbackColor = AppColors.CardBg,
        contentScrimColor = AppColors.CardBg.copy(alpha = 0.68f),
        modifier = modifier.widthIn(max = 264.dp).fillMaxWidth(),
        interactive = false
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            CoverFlowAction(AppIcons.BookOpen, stringResource(R.string.coverflow_open), onOpen, Modifier.weight(1f))
            CoverFlowAction(AppIcons.Info, stringResource(R.string.coverflow_info), { run(actions.details) }, Modifier.weight(1f))
            CoverFlowAction(AppIcons.Heart.resolve(book.isFavorite), stringResource(R.string.favorite),
                { run(actions.favorite) }, Modifier.weight(1f))
            CoverFlowAction(AppIcons.DotsThreeVertical, stringResource(R.string.coverflow_more), {
                ownsMenu = true
                menuHost?.show(LiquidGlassMenuSpec(
                    anchorBounds = moreBounds,
                    width = 220.dp,
                    onDismiss = { ownsMenu = false },
                    items = buildList {
                        add(LiquidGlassMenuItem(editLabel, AppIcons.PencilSimple) { run(actions.editInfo) })
                        add(LiquidGlassMenuItem(coverLabel, AppIcons.Image) { run(actions.customCover) })
                        if (FileUtils.isCustomCover(book.coverPath)) {
                            add(LiquidGlassMenuItem(removeCoverLabel, AppIcons.ArrowCounterClockwise) { run(actions.removeCustomCover) })
                        }
                        add(LiquidGlassMenuItem(tagsLabel, AppIcons.Tag) { run(actions.tags) })
                        add(LiquidGlassMenuItem(notesLabel, AppIcons.Bookmark.regular) { run(actions.notes) })
                        add(LiquidGlassMenuItem(moveLabel, AppIcons.FolderSimple) { run(actions.move) })
                        add(LiquidGlassMenuItem(deleteLabel, AppIcons.Trash, destructive = true) { run(actions.delete) })
                    }
                ))
            }, Modifier.weight(1f).liquidGlassMenuAnchor(kind = LiquidGlassMenuAnchorKind.Embedded)
                .onGloballyPositioned { moreBounds = it.boundsInRoot() })
        }
    }
}

@Composable
private fun CoverFlowAction(icon: ImageVector, label: String, onClick: () -> Unit, modifier: Modifier) {
    Column(
        modifier.heightIn(min = 52.dp).clickable(
            interactionSource = remember { MutableInteractionSource() }, indication = null,
            onClick = onClick).padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(icon, contentDescription = null, tint = AppColors.TextPrimary, modifier = Modifier.size(18.dp))
        Text(label, color = AppColors.TextPrimary, fontSize = 11.sp, maxLines = 1,
            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(), overflow = TextOverflow.Ellipsis)
    }
}

@Composable
internal fun CoverFlowFolderNavigation(
    folders: List<LibraryFolder>,
    counts: Map<String, Int>,
    onOpen: (LibraryFolder) -> Unit,
    onRename: (LibraryFolder) -> Unit,
    onDelete: (LibraryFolder) -> Unit,
    onCover: (LibraryFolder) -> Unit,
    onRemoveCover: (LibraryFolder) -> Unit,
    onMove: (LibraryFolder) -> Unit,
    showAddBook: Boolean,
    onAddBook: () -> Unit,
    enabled: Boolean,
    onHaptic: () -> Unit
) {
    if (folders.isEmpty() && !showAddBook) return
    val host = LocalLiquidGlassMenuHost.current
    val rename = stringResource(R.string.rename_folder)
    val delete = stringResource(R.string.delete)
    val cover = stringResource(R.string.set_folder_cover)
    val removeCover = stringResource(R.string.remove_folder_cover)
    val move = stringResource(R.string.move_to_folder)
    Row(Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        LazyRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(folders, key = { it.id }) { folder ->
                var bounds by remember { mutableStateOf(Rect.Zero) }
                LiquidGlassSurface(
                    shape = RoundedCornerShape(16.dp), fallbackColor = AppColors.CardBg,
                    interactive = true,
                    enabled = enabled,
                    contentScrimColor = AppColors.CardBg.copy(alpha = 0.24f),
                    modifier = Modifier.coverFlowEntranceItem(3 + folders.indexOf(folder).coerceAtMost(3))
                        .onGloballyPositioned { bounds = it.boundsInRoot() }
                        .combinedClickable(enabled = enabled,
                            interactionSource = remember { MutableInteractionSource() }, indication = null,
                            onClick = { onOpen(folder) },
                            onLongClick = {
                                onHaptic()
                                host?.show(LiquidGlassMenuSpec(bounds, 220.dp, buildList {
                                    add(LiquidGlassMenuItem(rename, AppIcons.PencilSimple) { onRename(folder) })
                                    add(LiquidGlassMenuItem(cover, AppIcons.Image) { onCover(folder) })
                                    if (folder.coverPath != null) add(LiquidGlassMenuItem(removeCover, AppIcons.ArrowCounterClockwise) { onRemoveCover(folder) })
                                    add(LiquidGlassMenuItem(move, AppIcons.FolderSimple) { onMove(folder) })
                                    add(LiquidGlassMenuItem(delete, AppIcons.Trash, destructive = true) { onDelete(folder) })
                                }))
                            })
                ) {
                    Row(Modifier.heightIn(min = 44.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(AppIcons.Folder, null, tint = AppColors.Accent, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(folder.name, color = AppColors.TextPrimary, fontSize = 13.sp,
                            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 130.dp))
                        Text("  ${counts[folder.id] ?: 0}", color = AppColors.TextSecondary, fontSize = 11.sp)
                        if (folder.storageMissing) Icon(AppIcons.Link, stringResource(R.string.book_file_unavailable),
                            tint = androidx.compose.ui.graphics.Color(0xFFD92D3A), modifier = Modifier.size(14.dp))
                    }
                }
            }
        }
        if (showAddBook) LiquidGlassIconButton(
            imageVector = AppIcons.Plus, contentDescription = stringResource(R.string.import_books),
            onClick = { if (enabled) onAddBook() }, size = 44.dp, iconSize = 20.dp
        )
    }
}
