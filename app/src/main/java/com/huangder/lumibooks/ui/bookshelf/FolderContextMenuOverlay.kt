package com.huangder.lumibooks.ui.bookshelf

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DriveFileMove
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.HideImage
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.huangder.lumibooks.R
import com.huangder.lumibooks.domain.model.LibraryFolder
import com.huangder.lumibooks.ui.components.LiquidGlassSurface
import com.huangder.lumibooks.ui.theme.AppColors
import com.huangder.lumibooks.ui.theme.AppRadius
import com.huangder.lumibooks.ui.theme.AppType
import com.huangder.lumibooks.ui.theme.KaiTi
import com.huangder.lumibooks.ui.theme.LocalAppTheme
import com.huangder.lumibooks.ui.theme.resolveAppFontFamily

private sealed interface FolderContextAction {
    data object Delete : FolderContextAction
    data object SetCover : FolderContextAction
    data object RemoveCover : FolderContextAction
    data object Move : FolderContextAction
}

@Composable
internal fun FolderContextMenuOverlay(
    state: FolderContextMenuState,
    bookCount: Int,
    onRename: (LibraryFolder) -> Unit,
    onDelete: (LibraryFolder) -> Unit,
    onSetCover: (LibraryFolder) -> Unit,
    onRemoveCover: (LibraryFolder) -> Unit,
    onMove: (LibraryFolder) -> Unit
) {
    if (state.phase == ContextMenuPhase.Idle) return
    val folder = state.selectedFolder ?: return
    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(3f)
            .graphicsLayer { }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) { state.dismiss() }
        )
        HighlightedFolderCover(
            folder = folder,
            coverBounds = state.coverBounds,
            coverScale = state.coverScale.value,
            positionProgress = state.coverPositionProgress.value
        )
        if (state.menuAlpha.value > 0.01f || state.actionsAlpha.value > 0.01f) {
            FolderContextMenuLayout(
                folder = folder,
                bookCount = bookCount,
                menuAlpha = state.menuAlpha.value,
                actionsAlpha = state.actionsAlpha.value,
                coverBounds = state.coverBounds,
                onRename = {
                    state.dismiss()
                    onRename(folder)
                },
                onAction = { action ->
                    state.dismiss()
                    when (action) {
                        FolderContextAction.Delete -> onDelete(folder)
                        FolderContextAction.SetCover -> onSetCover(folder)
                        FolderContextAction.RemoveCover -> onRemoveCover(folder)
                        FolderContextAction.Move -> onMove(folder)
                    }
                }
            )
        }
    }
}

@Composable
private fun HighlightedFolderCover(
    folder: LibraryFolder,
    coverBounds: Rect,
    coverScale: Float,
    positionProgress: Float
) {
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val coverLeft = with(density) { coverBounds.left.toDp() }
    val coverTop = with(density) { coverBounds.top.toDp() }
    val coverWidth = with(density) { coverBounds.width.toDp() }
    val coverHeight = with(density) { coverBounds.height.toDp() }
    val overflow = coverHeight * 0.08f / 2f
    val maxBottom = configuration.screenHeightDp.dp - 196.dp
    val visualBottom = coverTop + coverHeight + overflow
    val offsetY = if (LocalAppTheme.current == "liquid_glass") {
        coverTop - (visualBottom - maxBottom).coerceAtLeast(0.dp) * positionProgress.coerceIn(0f, 1f)
    } else {
        coverTop
    }
    FolderCover(
        folder = folder,
        cornerRadius = if (LocalAppTheme.current == "liquid_glass") 16.dp else AppRadius.sm,
        modifier = Modifier
            .offset(x = coverLeft, y = offsetY)
            .size(width = coverWidth, height = coverHeight)
            .graphicsLayer {
                scaleX = coverScale
                scaleY = coverScale
            }
    )
}

@Composable
private fun FolderContextMenuLayout(
    folder: LibraryFolder,
    bookCount: Int,
    menuAlpha: Float,
    actionsAlpha: Float,
    coverBounds: Rect,
    onRename: () -> Unit,
    onAction: (FolderContextAction) -> Unit
) {
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp
    val screenHeight = configuration.screenHeightDp.dp
    val coverLeft = with(density) { coverBounds.left.toDp() }
    val coverTop = with(density) { coverBounds.top.toDp() }
    val coverBottom = with(density) { coverBounds.bottom.toDp() }
    val coverWidth = with(density) { coverBounds.width.toDp() }
    val margin = 8.dp
    val regularWidth = 170.dp
    val regularGap = 12.dp
    val availableLeft = coverLeft - margin
    val availableRight = screenWidth - coverLeft - coverWidth - margin
    val canFitLeft = availableLeft >= regularWidth + regularGap
    val canFitRight = availableRight >= regularWidth + regularGap
    val compact = !canFitLeft && !canFitRight
    val panelWidth = if (compact) 136.dp else regularWidth
    val gap = if (compact) 6.dp else regularGap
    val placeRight = when {
        canFitRight -> true
        canFitLeft -> false
        else -> availableRight >= availableLeft
    }
    val desiredX = if (placeRight) coverLeft + coverWidth + gap else coverLeft - panelWidth - gap
    val maxX = (screenWidth - panelWidth - margin).coerceAtLeast(margin)
    val panelX = desiredX.coerceIn(margin, maxX)
    val estimatedHeight = if (folder.coverPath == null) 334.dp else 382.dp
    val bottomMargin = if (LocalAppTheme.current == "liquid_glass") 148.dp else 48.dp
    val topMargin = with(density) { WindowInsets.statusBars.getTop(this).toDp() } + 14.dp
    val maxY = (screenHeight - estimatedHeight - bottomMargin).coerceAtLeast(topMargin)
    val desiredY = if (coverTop + estimatedHeight > screenHeight - bottomMargin) {
        coverBottom - estimatedHeight
    } else {
        coverTop
    }

    Column(
        modifier = Modifier
            .offset(x = panelX, y = desiredY.coerceIn(topMargin, maxY))
            .width(panelWidth),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        FolderInfoPanel(
            folder = folder,
            bookCount = bookCount,
            alpha = menuAlpha,
            compact = compact,
            onRename = onRename
        )
        FolderActionsPanel(
            folder = folder,
            alpha = actionsAlpha,
            compact = compact,
            onAction = onAction
        )
    }
}

@Composable
private fun FolderInfoPanel(
    folder: LibraryFolder,
    bookCount: Int,
    alpha: Float,
    compact: Boolean,
    onRename: () -> Unit
) {
    val shape = RoundedCornerShape(if (LocalAppTheme.current == "liquid_glass") 24.dp else AppRadius.md)
    val progress = alpha.coerceIn(-0.08f, 1.08f)
    LiquidGlassSurface(
        shape = shape,
        fallbackColor = AppColors.CardBg,
        contentScrimColor = AppColors.CardBg.copy(alpha = 0.70f),
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                this.alpha = progress.coerceIn(0f, 1f)
                val scale = 0.84f + 0.16f * progress
                scaleX = scale
                scaleY = scale
            },
        contentAlignment = Alignment.TopStart
    ) {
        Column(Modifier.fillMaxWidth().padding(if (compact) 12.dp else 16.dp)) {
            Text(
                text = folder.name,
                color = AppColors.TextPrimary,
                fontSize = AppType.Section,
                fontWeight = FontWeight.Bold,
                fontFamily = resolveAppFontFamily(KaiTi),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.bookshelf_category_book_count, bookCount),
                color = AppColors.TextSecondary,
                fontSize = AppType.BodySmall
            )
            Spacer(Modifier.height(16.dp))
            Box(Modifier.fillMaxWidth().height(0.5.dp).background(AppColors.Divider))
            Spacer(Modifier.height(12.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(AppRadius.sm))
                    .clickable(onClick = onRename)
                    .padding(vertical = 4.dp)
            ) {
                Icon(Icons.Outlined.Edit, null, tint = AppColors.Accent, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.rename_folder),
                    color = AppColors.Accent,
                    fontSize = AppType.Caption
                )
            }
        }
    }
}

@Composable
private fun FolderActionsPanel(
    folder: LibraryFolder,
    alpha: Float,
    compact: Boolean,
    onAction: (FolderContextAction) -> Unit
) {
    val shape = RoundedCornerShape(if (LocalAppTheme.current == "liquid_glass") 24.dp else AppRadius.md)
    val progress = alpha.coerceIn(-0.08f, 1.08f)
    LiquidGlassSurface(
        shape = shape,
        fallbackColor = AppColors.CardBg,
        contentScrimColor = AppColors.CardBg.copy(alpha = 0.70f),
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                this.alpha = progress.coerceIn(0f, 1f)
                val scale = 0.84f + 0.16f * progress
                scaleX = scale
                scaleY = scale
            },
        contentAlignment = Alignment.TopStart
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(if (compact) 8.dp else 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            FolderContextActionItem(
                label = stringResource(R.string.delete_folder),
                icon = Icons.Outlined.Delete,
                compact = compact,
                onClick = { onAction(FolderContextAction.Delete) }
            )
            FolderContextActionItem(
                label = stringResource(
                    if (folder.coverPath == null) R.string.set_folder_cover else R.string.change_folder_cover
                ),
                icon = Icons.Outlined.Image,
                compact = compact,
                onClick = { onAction(FolderContextAction.SetCover) }
            )
            if (folder.coverPath != null) {
                FolderContextActionItem(
                    label = stringResource(R.string.remove_folder_cover),
                    icon = Icons.Outlined.HideImage,
                    compact = compact,
                    onClick = { onAction(FolderContextAction.RemoveCover) }
                )
            }
            FolderContextActionItem(
                label = stringResource(R.string.move_folder),
                icon = Icons.Outlined.DriveFileMove,
                compact = compact,
                onClick = { onAction(FolderContextAction.Move) }
            )
        }
    }
}

@Composable
private fun FolderContextActionItem(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    compact: Boolean,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AppRadius.sm))
            .clickable(onClick = onClick)
            .padding(horizontal = if (compact) 6.dp else 10.dp, vertical = if (compact) 8.dp else 10.dp)
    ) {
        Icon(icon, null, tint = AppColors.TextPrimary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(if (compact) 7.dp else 10.dp))
        Text(text = label, color = AppColors.TextPrimary, fontSize = AppType.BodySmall)
    }
}
