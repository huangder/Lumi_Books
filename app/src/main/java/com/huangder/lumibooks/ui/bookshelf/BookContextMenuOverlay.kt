package com.huangder.lumibooks.ui.bookshelf
import com.huangder.lumibooks.ui.icons.AppIcons

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.huangder.lumibooks.R
import com.huangder.lumibooks.domain.model.Book
import com.huangder.lumibooks.ui.theme.AppColors
import androidx.compose.ui.res.stringResource
import com.huangder.lumibooks.ui.theme.AppRadius
import com.huangder.lumibooks.ui.theme.AppType
import com.huangder.lumibooks.ui.theme.KaiTi
import com.huangder.lumibooks.ui.theme.LocalAppTheme
import com.huangder.lumibooks.ui.theme.resolveAppFontFamily
import com.huangder.lumibooks.ui.components.LiquidGlassSurface

/**
 * 上下文菜单操作类型（UI 阶段仅定义，不接逻辑）
 */
sealed class ContextMenuAction {
    data object BookDetails : ContextMenuAction()
    data object MoveToFolder : ContextMenuAction()
    data object Delete : ContextMenuAction()
    data object Favorite : ContextMenuAction()
    data object CustomCover : ContextMenuAction()
    data object RemoveCustomCover : ContextMenuAction()
    data object BookmarksNotes : ContextMenuAction()
    data object Tags : ContextMenuAction()
    data object EditInfo : ContextMenuAction()
}

/**
 * 书本长按上下文菜单全屏覆盖层
 */






@Composable
fun BookContextMenuOverlay(
    state: BookContextMenuState,
    onDelete: (Book) -> Unit = {},
    onFavorite: (Book) -> Unit = {},
    onCustomCover: (Book) -> Unit = {},
    onRemoveCustomCover: (Book) -> Unit = {},
    onBookmarksNotes: (Book) -> Unit = {},
    onTags: (Book) -> Unit = {},
    onEditInfo: (Book) -> Unit = {},
    onBookDetails: (Book) -> Unit = {},
    onMoveToFolder: (Book) -> Unit = {}
) {
    if (state.phase == ContextMenuPhase.Idle) return

    val book = state.selectedBook ?: return
    val coverBounds = state.coverBounds
    val coverScale = state.coverScale.value





    val menuAlpha = state.menuAlpha.value
    val actionsAlpha = state.actionsAlpha.value
    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(3f)
            .graphicsLayer { }
    ) { // 覆盖层需要始终高于书架顶部胶囊。
        // ── 1. 半透明背景 + 点击关闭 ──
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) { state.dismiss() }
        )

        // ── 2. 高亮封面（在遮罩之上，原始位置放大） ──
        HighlightedCover(
            book = book,
            coverBounds = coverBounds,
            coverScale = coverScale
        )

        // ── 3. 菜单布局（信息面板或操作面板任一可见时显示） ──
        if (menuAlpha > 0.01f || actionsAlpha > 0.01f) {
            ContextMenuLayout(
                book = book,
                menuAlpha = menuAlpha,
                actionsAlpha = actionsAlpha,
                coverBounds = coverBounds,
                onAction = { action ->
                    state.dismiss()
                    when (action) {
                        is ContextMenuAction.BookDetails -> onBookDetails(book)
                        is ContextMenuAction.MoveToFolder -> onMoveToFolder(book)
                        is ContextMenuAction.Delete -> onDelete(book)
                        is ContextMenuAction.Favorite -> onFavorite(book)
                        is ContextMenuAction.CustomCover -> onCustomCover(book)
                        is ContextMenuAction.RemoveCustomCover -> onRemoveCustomCover(book)
                        is ContextMenuAction.BookmarksNotes -> onBookmarksNotes(book)
                        is ContextMenuAction.Tags -> onTags(book)
                        is ContextMenuAction.EditInfo -> onEditInfo(book)
                    }
                },
                onEditInfo = {
                    state.dismiss()
                    onEditInfo(book)
                }
            )
        }
    }
}

// ─── 高亮封面 ─────────────────────────────────────────────────────

@Composable
private fun HighlightedCover(
    book: Book,
    coverBounds: Rect,
    coverScale: Float
) {
    val density = LocalDensity.current

    val coverLeftDp = with(density) { coverBounds.left.toDp() }
    val coverTopDp = with(density) { coverBounds.top.toDp() }
    val coverWidthDp = with(density) { coverBounds.width.toDp() }
    val coverHeightDp = with(density) { coverBounds.height.toDp() }
    val context = LocalContext.current
    val isLiquidGlass = LocalAppTheme.current == "liquid_glass"
    val coverShape = RoundedCornerShape(if (isLiquidGlass) 16.dp else AppRadius.sm)
    // Keep the highlighted cover anchored to its original position. Moving it to make
    // room for the menu causes a large jump when the pressed book is near the bottom.
    val coverOffsetY = coverTopDp

    Box(
        modifier = Modifier
            .offset(x = coverLeftDp, y = coverOffsetY)
            .size(width = coverWidthDp, height = coverHeightDp)
            .graphicsLayer {
                scaleX = coverScale
                scaleY = coverScale
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0.5f)
            }
            .shadow(
                12.dp,
                coverShape,
                ambientColor = Color(0x06000000),
                spotColor = Color(0x06000000)
            )
            .clip(coverShape)
    ) {
        if (book.coverPath != null) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(book.coverPath)
                    .memoryCacheKey("${book.id}_${book.coverPath}")
                    .build(),
                contentDescription = book.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(AppColors.BgGray),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = book.title.take(8),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AppColors.TextSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(8.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    }
}

// ─── 菜单布局 ─────────────────────────────────────────────────────
// 信息面板（上）和操作面板（下）都在封面同一侧

@Composable
private fun ContextMenuLayout(
    book: Book,
    menuAlpha: Float,
    actionsAlpha: Float,
    coverBounds: Rect,
    onAction: (ContextMenuAction) -> Unit,
    onEditInfo: () -> Unit
) {
    val density = LocalDensity.current
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        // Use the actual Compose container bounds. Configuration height includes system
        // bars on some devices, which previously let the menu extend below the viewport.
        val screenWidthDp = maxWidth
        val screenHeightDp = maxHeight
        val coverLeftDp = with(density) { coverBounds.left.toDp() }
        val coverTopDp = with(density) { coverBounds.top.toDp() }
        val coverBottomDp = with(density) { coverBounds.bottom.toDp() }
        val coverWidthDp = with(density) { coverBounds.width.toDp() }
        val horizontalMargin = 12.dp
        val regularPanelWidth = minOf(170.dp, (screenWidthDp - horizontalMargin * 2).coerceAtLeast(120.dp))
        val regularPanelGap = 12.dp
        val availableLeft = coverLeftDp - horizontalMargin
        val availableRight = screenWidthDp - (coverLeftDp + coverWidthDp) - horizontalMargin
        val canFitRegularLeft = availableLeft >= regularPanelWidth + regularPanelGap
        val canFitRegularRight = availableRight >= regularPanelWidth + regularPanelGap
        val useCompactMiddlePanel = !canFitRegularLeft && !canFitRegularRight
        val panelWidth = if (useCompactMiddlePanel) minOf(148.dp, regularPanelWidth) else regularPanelWidth
        val horizontalPanelGap = if (useCompactMiddlePanel) 8.dp else regularPanelGap
        val verticalPanelGap = 12.dp
        val isLiquidGlass = LocalAppTheme.current == "liquid_glass"
        val placePanelOnRight = when {
            canFitRegularRight -> true
            canFitRegularLeft -> false
            else -> availableRight >= availableLeft
        }
        val desiredPanelX = if (placePanelOnRight) {
            coverLeftDp + coverWidthDp + horizontalPanelGap
        } else {
            coverLeftDp - panelWidth - horizontalPanelGap
        }
        val maxPanelX = (screenWidthDp - panelWidth - horizontalMargin).coerceAtLeast(horizontalMargin)
        val panelX = desiredPanelX.coerceIn(horizontalMargin, maxPanelX)
        val estimatedMenuHeight = 520.dp
        val bottomMargin = if (isLiquidGlass) 24.dp else 16.dp
        val topMargin = with(density) { WindowInsets.statusBars.getTop(this).toDp() } + 8.dp
        val maxMenuHeight = (screenHeightDp - topMargin - bottomMargin).coerceAtLeast(180.dp)
        val maxPanelY = (screenHeightDp - minOf(estimatedMenuHeight, maxMenuHeight) - bottomMargin)
            .coerceAtLeast(topMargin)
        val desiredPanelY = if (coverTopDp + estimatedMenuHeight > screenHeightDp - bottomMargin) {
            coverBottomDp - estimatedMenuHeight
        } else {
            coverTopDp
        }
        val panelY = desiredPanelY.coerceIn(topMargin, maxPanelY)

        Column(
            modifier = Modifier
                .offset(x = panelX, y = panelY)
                .width(panelWidth)
                .heightIn(max = maxMenuHeight)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(verticalPanelGap)
        ) {
        // 上部：信息面板（整体淡入）
        BookInfoPanel(
            book = book,
            alpha = menuAlpha,
            compact = useCompactMiddlePanel,
            onEditInfo = onEditInfo,
            modifier = Modifier.fillMaxWidth()
        )

        // 下部：操作面板（各项错开淡入）
        MenuActionsPanel(
            modifier = Modifier.fillMaxWidth(),
            actionsAlpha = actionsAlpha,
            compact = useCompactMiddlePanel,
            isFavorite = book.isFavorite,
            hasCustomCover = com.huangder.lumibooks.util.FileUtils.isCustomCover(book.coverPath),
            onAction = onAction
        )
        }
    }
}

// ─── 信息面板（书名 + 作者 + 编辑） ─────────────────────────────

@Composable
private fun BookInfoPanel(
    book: Book,
    alpha: Float = 1f,
    compact: Boolean = false,
    onEditInfo: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(
        if (LocalAppTheme.current == "liquid_glass") 24.dp else AppRadius.md
    )
    val motionProgress = alpha.coerceIn(-0.08f, 1.08f)
    LiquidGlassSurface(
        shape = shape,
        fallbackColor = AppColors.CardBg,
        contentScrimColor = AppColors.CardBg.copy(alpha = 0.70f),
        modifier = modifier.graphicsLayer {
            this.alpha = motionProgress.coerceIn(0f, 1f)
            val scale = 0.84f + 0.16f * motionProgress
            scaleX = scale
            scaleY = scale
        },
        contentAlignment = Alignment.TopStart
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(if (compact) 12.dp else 16.dp)
        ) {
        Text(
            text = book.title,
            fontSize = AppType.Section,
            fontWeight = FontWeight.Bold,
            fontFamily = resolveAppFontFamily(KaiTi),
            color = AppColors.TextPrimary,
            maxLines = 3,
            lineHeight = 26.sp
        )

        Spacer(Modifier.height(6.dp))

        Text(
            text = book.author,
            fontSize = AppType.BodySmall,
            color = AppColors.TextSecondary,
            maxLines = 1
        )

        Spacer(Modifier.height(16.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(0.5.dp)
                .background(AppColors.Divider)
        )

        Spacer(Modifier.height(12.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(RoundedCornerShape(AppRadius.sm))
                .clickable { onEditInfo() }
                .padding(vertical = 4.dp)
        ) {
            Icon(
                imageVector = AppIcons.PencilSimple,
                contentDescription = null,
                tint = AppColors.Accent,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = stringResource(R.string.edit_book_info),
                fontSize = AppType.Caption,
                color = AppColors.Accent
            )
        }
    }
    }
}

// ─── 操作面板（4 个选项，从下到上依次淡入） ────────────────────

@Composable
private fun MenuActionsPanel(
    modifier: Modifier = Modifier,
    actionsAlpha: Float,
    compact: Boolean = false,
    isFavorite: Boolean = false,
    hasCustomCover: Boolean = false,
    onAction: (ContextMenuAction) -> Unit
) {
    val shape = RoundedCornerShape(
        if (LocalAppTheme.current == "liquid_glass") 24.dp else AppRadius.md
    )
    val motionProgress = actionsAlpha.coerceIn(-0.08f, 1.08f)
    val favoriteIcon = AppIcons.Heart.resolve(isFavorite)
    val favoriteLabel = if (isFavorite) stringResource(R.string.remove_favorite_short) else stringResource(R.string.favorite)
    data class MenuItem(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector, val action: ContextMenuAction)
    val menuItems = buildList {
        add(MenuItem(stringResource(R.string.book_details), AppIcons.Info, ContextMenuAction.BookDetails))
        add(MenuItem(favoriteLabel, favoriteIcon, ContextMenuAction.Favorite))
        add(MenuItem(stringResource(R.string.add_tag), AppIcons.Tag, ContextMenuAction.Tags))
        add(MenuItem(stringResource(R.string.move_to_folder), AppIcons.FolderSimple, ContextMenuAction.MoveToFolder))
        add(MenuItem(stringResource(R.string.bookmarks_notes), AppIcons.Bookmark.regular, ContextMenuAction.BookmarksNotes))
        add(MenuItem(stringResource(R.string.custom_cover), AppIcons.Image, ContextMenuAction.CustomCover))
        if (hasCustomCover) add(MenuItem(stringResource(R.string.remove_custom_cover), AppIcons.ArrowCounterClockwise, ContextMenuAction.RemoveCustomCover))
        add(MenuItem(stringResource(R.string.delete), AppIcons.Trash, ContextMenuAction.Delete))
    }
    LiquidGlassSurface(
        shape = shape,
        fallbackColor = AppColors.CardBg,
        contentScrimColor = AppColors.CardBg.copy(alpha = 0.70f),
        modifier = modifier.graphicsLayer {
            alpha = motionProgress.coerceIn(0f, 1f)
            val scale = 0.84f + 0.16f * motionProgress
            scaleX = scale
            scaleY = scale
        },
        contentAlignment = Alignment.TopStart
        ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp, max = 360.dp)
                .padding(if (compact) 8.dp else 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
        items(menuItems, key = { it.label }) { item ->
            MenuActionItem(
                label = item.label,
                icon = item.icon,
                compact = compact,
                onClick = { onAction(item.action) }
            )
        }
    }
    }
}

// ─── 单个菜单项（支持错开淡入） ─────────────────────────────────

@Composable
private fun MenuActionItem(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    alpha: Float = 1f,
    compact: Boolean = false,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { this.alpha = alpha }
            .clip(RoundedCornerShape(AppRadius.sm))
            .clickable(onClick = onClick)
            .padding(
                horizontal = if (compact) 6.dp else 10.dp,
                vertical = if (compact) 8.dp else 10.dp
            )
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = AppColors.TextPrimary,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(if (compact) 7.dp else 10.dp))
        Text(
            text = label,
            fontSize = AppType.BodySmall,
            color = AppColors.TextPrimary
        )
    }
}
