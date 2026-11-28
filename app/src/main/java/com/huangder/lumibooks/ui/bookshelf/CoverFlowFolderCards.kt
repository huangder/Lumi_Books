package com.huangder.lumibooks.ui.bookshelf

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.huangder.lumibooks.R
import com.huangder.lumibooks.domain.model.Book
import com.huangder.lumibooks.domain.model.BookFormat
import com.huangder.lumibooks.domain.model.LibraryFolder
import com.huangder.lumibooks.ui.animation.lumiPressable
import com.huangder.lumibooks.ui.components.LiquidGlassMenuAnchorKind
import com.huangder.lumibooks.ui.components.LiquidGlassMenuItem
import com.huangder.lumibooks.ui.components.LiquidGlassMenuSpec
import com.huangder.lumibooks.ui.components.LocalLiquidGlassMenuHost
import com.huangder.lumibooks.ui.components.liquidGlassMenuAnchor
import com.huangder.lumibooks.ui.icons.AppIcons
import com.huangder.lumibooks.ui.icons.ControlIcon
import com.huangder.lumibooks.ui.theme.AppColors
import com.huangder.lumibooks.ui.theme.AppRadius
import com.huangder.lumibooks.ui.theme.AppRoundedCornerShape
import com.huangder.lumibooks.ui.theme.AppType
import com.huangder.lumibooks.ui.theme.LocalEInkMode

/**
 * Folders take part in the CoverFlow stage as cards ahead of the books. The stage, its state and
 * its gestures all work on [Book] ids, so a folder rides along as a stand-in book whose id
 * carries this prefix; every place that opens, draws or reorders an item checks for it.
 */
internal const val COVER_FLOW_FOLDER_PREFIX = "coverflow-folder:"

internal fun isCoverFlowFolderCard(id: String?): Boolean = id?.startsWith(COVER_FLOW_FOLDER_PREFIX) == true

internal fun coverFlowFolderId(cardId: String): String = cardId.removePrefix(COVER_FLOW_FOLDER_PREFIX)

/**
 * The stand-in for [folder]. It is marked pinned so a dragged book can never be dropped among
 * the folder cards (a drag stays within its pinned group).
 */
internal fun coverFlowFolderCard(folder: LibraryFolder): Book = Book(
    id = COVER_FLOW_FOLDER_PREFIX + folder.id,
    title = folder.name,
    author = "",
    filePath = "",
    coverPath = folder.coverPath,
    format = BookFormat.EPUB,
    lastReadTime = 0L,
    readingProgress = 0f,
    createdAt = 0L,
    isPinned = true
)

/** What a folder card shows. */
internal data class CoverFlowFolderCardInfo(
    val folder: LibraryFolder,
    val bookCount: Int,
    val previewBooks: List<Book?>
)

/** Drops the folder stand-ins from an ordering before it is saved. */
internal fun withoutCoverFlowFolderCards(ids: List<String>): List<String> = ids.filterNot(::isCoverFlowFolderCard)

/** A folder dressed for the stage: its collage or custom cover, the same size as a book cover. */
@Composable
internal fun CoverFlowFolderCardFace(info: CoverFlowFolderCardInfo, modifier: Modifier = Modifier) {
    val eInk = LocalEInkMode.current
    val shape = AppRoundedCornerShape(3.dp)
    Box(modifier
        .then(if (eInk) Modifier.border(1.dp, Color.Black, shape) else Modifier.shadow(8.dp, shape, clip = false,
            ambientColor = Color.Black.copy(alpha = 0.08f), spotColor = Color.Black.copy(alpha = 0.16f)))
        .clip(shape)
        .background(AppColors.BgGray)) {
        FolderCover(folder = info.folder, cornerRadius = 3.dp, previewBooks = info.previewBooks,
            modifier = Modifier.fillMaxSize())
    }
}

/** Action row shown under a centred folder card: open it, rename it, or more folder actions. */
@Composable
internal fun CoverFlowFolderActionBar(
    folder: LibraryFolder,
    onOpen: () -> Unit,
    onRename: (LibraryFolder) -> Unit,
    onCover: (LibraryFolder) -> Unit,
    onRemoveCover: (LibraryFolder) -> Unit,
    onMove: (LibraryFolder) -> Unit,
    onDelete: (LibraryFolder) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val menuHost = LocalLiquidGlassMenuHost.current
    var moreBounds by remember { mutableStateOf(Rect.Zero) }
    var ownsMenu by remember { mutableStateOf(false) }
    DisposableEffect(menuHost) { onDispose { if (ownsMenu) menuHost?.dismiss() } }
    val coverLabel = stringResource(R.string.set_folder_cover)
    val removeCoverLabel = stringResource(R.string.remove_folder_cover)
    val moveLabel = stringResource(R.string.move_to_folder)
    val deleteLabel = stringResource(R.string.delete)
    fun run(action: (LibraryFolder) -> Unit) { onDismiss(); action(folder) }
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically) {
        val openInteraction = remember { MutableInteractionSource() }
        Row(Modifier.height(40.dp).lumiPressable(openInteraction, pressedScale = 0.95f)
            .clip(AppRoundedCornerShape(AppRadius.full)).background(AppColors.Accent)
            .drawWithCache {
                val lift = Brush.verticalGradient(0f to Color.White.copy(alpha = 0.24f), 0.55f to Color.Transparent,
                    1f to Color.Black.copy(alpha = 0.06f))
                onDrawBehind { drawRect(lift) }
            }
            .clickable(interactionSource = openInteraction, indication = null, onClick = onOpen)
            .padding(start = 14.dp, end = 18.dp),
            verticalAlignment = Alignment.CenterVertically) {
            ControlIcon(AppIcons.FolderSimple, contentDescription = null, tint = AppColors.OnAccent, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.coverflow_open), color = AppColors.OnAccent, fontSize = AppType.Footnote,
                fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
        CoverFlowAction(AppIcons.PencilSimple, stringResource(R.string.rename_folder), { run(onRename) })
        CoverFlowAction(AppIcons.DotsThreeVertical, stringResource(R.string.coverflow_more), {
            ownsMenu = true
            menuHost?.show(LiquidGlassMenuSpec(
                anchorBounds = moreBounds,
                width = 220.dp,
                onDismiss = { ownsMenu = false },
                items = buildList {
                    add(LiquidGlassMenuItem(coverLabel, AppIcons.Image) { run(onCover) })
                    if (folder.coverPath != null) {
                        add(LiquidGlassMenuItem(removeCoverLabel, AppIcons.ArrowCounterClockwise) { run(onRemoveCover) })
                    }
                    add(LiquidGlassMenuItem(moveLabel, AppIcons.FolderSimple) { run(onMove) })
                    add(LiquidGlassMenuItem(deleteLabel, AppIcons.Trash, destructive = true) { run(onDelete) })
                }
            ))
        }, Modifier.liquidGlassMenuAnchor(kind = LiquidGlassMenuAnchorKind.Embedded)
            .onGloballyPositioned { moreBounds = it.boundsInRoot() })
    }
}
