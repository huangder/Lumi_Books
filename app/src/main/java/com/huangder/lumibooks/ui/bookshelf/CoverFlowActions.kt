package com.huangder.lumibooks.ui.bookshelf

import com.huangder.lumibooks.ui.icons.ControlIcon

import com.huangder.lumibooks.ui.components.liquidGlassMenuAnchor

import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import com.huangder.lumibooks.ui.theme.AppRoundedCornerShape
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
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import com.huangder.lumibooks.ui.animation.lumiPressable
import com.huangder.lumibooks.ui.theme.AppRadius
import com.huangder.lumibooks.R
import com.huangder.lumibooks.ui.animation.coverFlowEntranceItem
import com.huangder.lumibooks.domain.model.Book
import com.huangder.lumibooks.domain.model.LibraryFolder
import com.huangder.lumibooks.ui.components.*
import com.huangder.lumibooks.ui.icons.AppIcons
import com.huangder.lumibooks.ui.theme.AppColors
import com.huangder.lumibooks.ui.theme.AppType
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
    val delete: (Book) -> Unit,
    val hide: ((Book) -> Unit)? = null,
    val archive: ((Book) -> Unit)? = null
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
    val archiveLabel = stringResource(R.string.archive_book)
    val hideLabel = stringResource(R.string.hidden_library_hide)
    val deleteLabel = stringResource(R.string.delete)
    var ownsMenu by remember { mutableStateOf(false) }
    DisposableEffect(menuHost) {
        onDispose { if (ownsMenu) menuHost?.dismiss() }
    }
    fun run(action: (Book) -> Unit) { onDismiss(); action(book) }
    // A primary "open" capsule plus quiet round glass buttons: small floating controls that
    // sit under the title instead of a toolbar slab across the stage.
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically) {
        val openInteraction = remember { MutableInteractionSource() }
        Row(Modifier.height(40.dp).lumiPressable(openInteraction, pressedScale = 0.95f)
            .clip(AppRoundedCornerShape(AppRadius.full)).background(AppColors.Accent)
            .clickable(interactionSource = openInteraction, indication = null, onClick = onOpen)
            .padding(start = 14.dp, end = 18.dp),
            verticalAlignment = Alignment.CenterVertically) {
            ControlIcon(AppIcons.BookOpen, contentDescription = null, tint = AppColors.OnAccent, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.coverflow_open), color = AppColors.OnAccent, fontSize = AppType.Footnote,
                fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
        CoverFlowAction(AppIcons.Info, stringResource(R.string.coverflow_info), { run(actions.details) })
        CoverFlowAction(AppIcons.Heart.resolve(book.isFavorite), stringResource(R.string.favorite),
            { run(actions.favorite) }, tint = if (book.isFavorite) AppColors.Accent else AppColors.TextPrimary)
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
                    actions.archive?.let { archive ->
                        add(LiquidGlassMenuItem(archiveLabel, AppIcons.Archive) { run(archive) })
                    }
                    actions.hide?.let { hide ->
                        add(LiquidGlassMenuItem(hideLabel, AppIcons.EyeSlash) { run(hide) })
                    }
                    add(LiquidGlassMenuItem(deleteLabel, AppIcons.Trash, destructive = true) { run(actions.delete) })
                }
            ))
        }, Modifier.liquidGlassMenuAnchor(kind = LiquidGlassMenuAnchorKind.Embedded)
            .onGloballyPositioned { moreBounds = it.boundsInRoot() })
    }
}

@Composable
private fun CoverFlowAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = AppColors.TextPrimary
) {
    LiquidGlassSurface(
        shape = AppRoundedCornerShape(AppRadius.full),
        fallbackColor = AppColors.CardBg,
        contentScrimColor = AppColors.CardBg.copy(alpha = 0.5f),
        controlEdge = true,
        onClick = onClick,
        modifier = modifier.size(40.dp).semantics(mergeDescendants = true) {
            contentDescription = label
            role = Role.Button
        }
    ) {
        ControlIcon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
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
    // Quiet text tabs instead of glass capsules: the stage stays the visual focus. Overflow
    // fades at both edges rather than being cut by the row bounds.
    val fadeWidth = 20.dp
    Row(Modifier.fillMaxWidth().height(40.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        // Bleed through the row's 8.dp padding so tabs scroll (and fade) at the screen edge;
        // the end side stays put when the add button sits next to it.
        val bleedEnd = if (showAddBook) 0.dp else 8.dp
        LazyRow(Modifier.horizontalBleed(8.dp, bleedEnd).weight(1f)
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                val fade = (fadeWidth.toPx() / size.width).coerceIn(0f, 0.5f)
                drawRect(Brush.horizontalGradient(
                    0f to Color.Transparent, fade to Color.Black, 1f - fade to Color.Black, 1f to Color.Transparent
                ), blendMode = BlendMode.DstIn)
            },
            contentPadding = PaddingValues(start = 12.dp + 8.dp, end = 12.dp + bleedEnd),
            horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally)) {
            items(folders, key = { it.id }) { folder ->
                var bounds by remember { mutableStateOf(Rect.Zero) }
                val interaction = remember { MutableInteractionSource() }
                Row(Modifier.coverFlowEntranceItem(3 + folders.indexOf(folder).coerceAtMost(3))
                    .heightIn(min = 40.dp)
                    .onGloballyPositioned { bounds = it.boundsInRoot() }
                    .lumiPressable(interaction, pressedScale = 0.95f)
                    .clip(AppRoundedCornerShape(AppRadius.full))
                    .combinedClickable(enabled = enabled,
                        interactionSource = interaction, indication = null,
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
                    .padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    ControlIcon(AppIcons.Folder, null, tint = AppColors.TextSecondary, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(5.dp))
                    Text(folder.name, color = AppColors.TextPrimary, fontSize = AppType.Footnote,
                        fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 130.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("${counts[folder.id] ?: 0}", color = AppColors.TextSecondary.copy(alpha = 0.7f),
                        fontSize = AppType.Caption2)
                    if (folder.storageMissing) ControlIcon(AppIcons.Link, stringResource(R.string.book_file_unavailable),
                        tint = AppColors.Destructive, modifier = Modifier.padding(start = 4.dp).size(14.dp))
                }
            }
        }
        if (showAddBook) {
            val addInteraction = remember { MutableInteractionSource() }
            Box(Modifier.size(40.dp).lumiPressable(addInteraction, pressedScale = 0.92f)
                .clickable(interactionSource = addInteraction, indication = null,
                    onClickLabel = null, onClick = { if (enabled) onAddBook() })
                .semantics { role = Role.Button },
                contentAlignment = Alignment.Center) {
                ControlIcon(AppIcons.Plus, stringResource(R.string.import_books),
                    tint = AppColors.TextSecondary, modifier = Modifier.size(20.dp))
            }
        }
    }
}
