package com.huangder.lumibooks.ui.bookshelf

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Label
import androidx.compose.material.icons.outlined.Restore
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
import androidx.compose.ui.platform.LocalConfiguration
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
import com.huangder.lumibooks.ui.components.LiquidGlassSurface

/**
 * 上下文菜单操作类型（UI 阶段仅定义，不接逻辑）
 */
sealed class ContextMenuAction {
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
    onEditInfo: (Book) -> Unit = {}
) {
    if (state.phase == ContextMenuPhase.Idle) return

    val book = state.selectedBook ?: return
    val coverBounds = state.coverBounds
    val coverScale = state.coverScale.value
    val coverPositionProgress = state.coverPositionProgress.value
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
            coverScale = coverScale,
            positionProgress = coverPositionProgress
        )

        // ── 3. 菜单布局（信息面板或操作面板任一可见时显示） ──
        if (menuAlpha > 0.01f || actionsAlpha > 0.01f) {
            ContextMenuLayout(
                book = book,
                menuAlpha = menuAlpha,
                actionsAlpha = actionsAlpha,
                coverBounds = coverBounds,
                onAction = { action ->
                    when (action) {
                        is ContextMenuAction.Tags -> onTags(book)
                        else -> {
                            state.dismiss()
                            when (action) {
                                is ContextMenuAction.Delete -> onDelete(book)
                                is ContextMenuAction.Favorite -> onFavorite(book)
                                is ContextMenuAction.CustomCover -> onCustomCover(book)
                                is ContextMenuAction.RemoveCustomCover -> onRemoveCustomCover(book)
                                is ContextMenuAction.BookmarksNotes -> onBookmarksNotes(book)
                                is ContextMenuAction.EditInfo -> onEditInfo(book)
                                is ContextMenuAction.Tags -> Unit
                            }
                        }
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
    coverScale: Float,
    positionProgress: Float
) {
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current

    val coverLeftDp = with(density) { coverBounds.left.toDp() }
    val coverTopDp = with(density) { coverBounds.top.toDp() }
    val coverWidthDp = with(density) { coverBounds.width.toDp() }
    val coverHeightDp = with(density) { coverBounds.height.toDp() }
    val context = LocalContext.current
    val isLiquidGlass = LocalAppTheme.current == "liquid_glass"
    val coverShape = RoundedCornerShape(if (isLiquidGlass) 16.dp else AppRadius.sm)
    val finalScaledOverflow = coverHeightDp * 0.08f / 2f
    val visualBottom = coverTopDp + coverHeightDp + finalScaledOverflow
    val maxVisualBottom = configuration.screenHeightDp.dp - 196.dp
    val coverOffsetY = if (isLiquidGlass) {
        val safeOffset = (visualBottom - maxVisualBottom).coerceAtLeast(0.dp)
        coverTopDp - safeOffset * positionProgress.coerceIn(0f, 1f)
    } else {
        coverTopDp
    }

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
    val configuration = LocalConfiguration.current
    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }
    val screenHeightDp = configuration.screenHeightDp.dp

    val isCoverOnLeft = coverBounds.center.x < screenWidthPx / 2

    val coverLeftDp = with(density) { coverBounds.left.toDp() }
    val coverTopDp = with(density) { coverBounds.top.toDp() }
    val coverBottomDp = with(density) { coverBounds.bottom.toDp() }
    val coverWidthDp = with(density) { coverBounds.width.toDp() }

    val panelWidth = 170.dp
    val panelGap = 12.dp
    val isLiquidGlass = LocalAppTheme.current == "liquid_glass"

    val panelX = if (isCoverOnLeft) {
        coverLeftDp + coverWidthDp + panelGap
    } else {
        coverLeftDp - panelWidth - panelGap
    }

    // 菜单面板顶部与封面顶部对齐；如果面板超出屏幕底部，则改为底部对齐
    // 液态主题需要为悬浮 Tag 栏、间距和系统导航区预留完整安全区。
    val estimatedMenuHeight = 450.dp
    val bottomMargin = if (isLiquidGlass) 148.dp else 48.dp
    val maxPanelY = (screenHeightDp - estimatedMenuHeight - bottomMargin).coerceAtLeast(0.dp)
    val panelY = if (coverTopDp + estimatedMenuHeight > screenHeightDp - bottomMargin) {
        (coverBottomDp - estimatedMenuHeight).coerceIn(0.dp, maxPanelY)
    } else {
        coverTopDp
    }

    Column(
        modifier = Modifier
            .offset(x = panelX, y = panelY)
            .width(panelWidth),
        verticalArrangement = Arrangement.spacedBy(panelGap)
    ) {
        // 上部：信息面板（整体淡入）
        BookInfoPanel(
            book = book,
            alpha = menuAlpha,
            onEditInfo = onEditInfo,
            modifier = Modifier.fillMaxWidth()
        )

        // 下部：操作面板（各项错开淡入）
        MenuActionsPanel(
            modifier = Modifier.fillMaxWidth(),
            actionsAlpha = actionsAlpha,
            isFavorite = book.isFavorite,
            hasCustomCover = com.huangder.lumibooks.util.FileUtils.isCustomCover(book.coverPath),
            onAction = onAction
        )
    }
}

// ─── 信息面板（书名 + 作者 + 编辑） ─────────────────────────────

@Composable
private fun BookInfoPanel(
    book: Book,
    alpha: Float = 1f,
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
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
        Text(
            text = book.title,
            fontSize = AppType.Section,
            fontWeight = FontWeight.Bold,
            fontFamily = KaiTi,
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
                imageVector = Icons.Outlined.Edit,
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
    isFavorite: Boolean = false,
    hasCustomCover: Boolean = false,
    onAction: (ContextMenuAction) -> Unit
) {
    val shape = RoundedCornerShape(
        if (LocalAppTheme.current == "liquid_glass") 24.dp else AppRadius.md
    )
    val motionProgress = actionsAlpha.coerceIn(-0.08f, 1.08f)
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
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
        val favoriteIcon = if (isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder
        val favoriteLabel = if (isFavorite) stringResource(R.string.remove_favorite_short) else stringResource(R.string.favorite)

        // 动态构建菜单项列表
        data class MenuItem(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector, val action: ContextMenuAction)

        val items = buildList {
            add(MenuItem(stringResource(R.string.delete), Icons.Outlined.Delete, ContextMenuAction.Delete))
            add(MenuItem(favoriteLabel, favoriteIcon, ContextMenuAction.Favorite))
            add(MenuItem(stringResource(R.string.custom_cover), Icons.Outlined.Image, ContextMenuAction.CustomCover))
            if (hasCustomCover) {
                add(MenuItem(stringResource(R.string.remove_custom_cover), Icons.Outlined.Restore, ContextMenuAction.RemoveCustomCover))
            }
            add(MenuItem(stringResource(R.string.add_tag), Icons.Outlined.Label, ContextMenuAction.Tags))
            add(MenuItem(stringResource(R.string.bookmarks_notes), Icons.Outlined.Bookmark, ContextMenuAction.BookmarksNotes))
        }

        items.forEach { item ->
            MenuActionItem(
                label = item.label,
                icon = item.icon,
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
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { this.alpha = alpha }
            .clip(RoundedCornerShape(AppRadius.sm))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 10.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = AppColors.TextPrimary,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = label,
            fontSize = AppType.BodySmall,
            color = AppColors.TextPrimary
        )
    }
}
