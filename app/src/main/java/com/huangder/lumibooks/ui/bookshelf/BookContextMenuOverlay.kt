package com.huangder.lumibooks.ui.bookshelf

import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
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
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Image
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
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.huangder.lumibooks.domain.model.Book
import com.huangder.lumibooks.ui.theme.AppColors
import com.huangder.lumibooks.ui.theme.AppRadius
import com.huangder.lumibooks.ui.theme.AppType
import com.huangder.lumibooks.ui.theme.KaiTi

/**
 * 上下文菜单操作类型（UI 阶段仅定义，不接逻辑）
 */
sealed class ContextMenuAction {
    data object Delete : ContextMenuAction()
    data object Favorite : ContextMenuAction()
    data object CustomCover : ContextMenuAction()
    data object BookmarksNotes : ContextMenuAction()
    data object EditInfo : ContextMenuAction()
}

/**
 * 内容层高斯模糊 Modifier
 *
 * 使用 CompositingStrategy.Offscreen 强制离屏渲染，
 * 确保 RenderEffect 模糊应用到整个内容层（包含所有子元素）。
 */
fun Modifier.contentBlur(alpha: Float): Modifier {
    if (alpha < 0.01f) return this
    return this.graphicsLayer {
        // 强制离屏渲染，确保模糊作用于所有子元素
        compositingStrategy = CompositingStrategy.Offscreen
        if (Build.VERSION.SDK_INT >= 31) {
            renderEffect = RenderEffect
                .createBlurEffect(20f * alpha, 20f * alpha, Shader.TileMode.CLAMP)
                .asComposeRenderEffect()
        }
    }
}

/**
 * 书本长按上下文菜单全屏覆盖层
 *
 * 注意：模糊效果通过 contentBlur() 应用到 BookshelfScreen 的内容层，
 * 这里只负责渲染高亮封面和菜单。
 */
@Composable
fun BookContextMenuOverlay(state: BookContextMenuState) {
    if (state.phase == ContextMenuPhase.Idle) return

    val book = state.selectedBook ?: return
    val coverBounds = state.coverBounds
    val coverScale = state.coverScale.value
    val menuAlpha = state.menuAlpha.value
    val scrimAlpha = state.scrimAlpha.value

    Box(modifier = Modifier.fillMaxSize()) {
        // ── 1. 透明点击层（点击关闭菜单） ──
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

        // ── 3. 菜单布局 ──
        if (menuAlpha > 0.01f) {
            ContextMenuLayout(
                book = book,
                menuAlpha = menuAlpha,
                coverBounds = coverBounds,
                onAction = { /* UI 阶段，暂不处理 */ }
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

    Box(
        modifier = Modifier
            .offset(x = coverLeftDp, y = coverTopDp)
            .size(width = coverWidthDp, height = coverHeightDp)
            .graphicsLayer {
                scaleX = coverScale
                scaleY = coverScale
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0.5f)
            }
            .shadow(
                16.dp * coverScale,
                RoundedCornerShape(AppRadius.sm),
                ambientColor = Color(0x20000000),
                spotColor = Color(0x20000000)
            )
            .clip(RoundedCornerShape(AppRadius.sm))
    ) {
        AsyncImage(
            model = book.coverPath,
            contentDescription = book.title,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
    }
}

// ─── 菜单布局 ─────────────────────────────────────────────────────
// 信息面板（上）和操作面板（下）都在封面同一侧

@Composable
private fun ContextMenuLayout(
    book: Book,
    menuAlpha: Float,
    coverBounds: Rect,
    onAction: (ContextMenuAction) -> Unit
) {
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }

    val isCoverOnLeft = coverBounds.center.x < screenWidthPx / 2

    val coverLeftDp = with(density) { coverBounds.left.toDp() }
    val coverTopDp = with(density) { coverBounds.top.toDp() }
    val coverWidthDp = with(density) { coverBounds.width.toDp() }

    val panelWidth = 170.dp
    val panelGap = 12.dp

    val panelX = if (isCoverOnLeft) {
        coverLeftDp + coverWidthDp + panelGap
    } else {
        coverLeftDp - panelWidth - panelGap
    }

    // 单侧面板：信息在上，操作在下
    Column(
        modifier = Modifier
            .offset(x = panelX, y = coverTopDp)
            .width(panelWidth),
        verticalArrangement = Arrangement.spacedBy(panelGap)
    ) {
        // 上部：信息面板（整体淡入）
        BookInfoPanel(
            book = book,
            alpha = menuAlpha,
            modifier = Modifier.fillMaxWidth()
        )

        // 下部：操作面板（各项错开淡入）
        MenuActionsPanel(
            modifier = Modifier.fillMaxWidth(),
            menuAlpha = menuAlpha,
            onAction = onAction
        )
    }
}

// ─── 信息面板（书名 + 作者 + 编辑） ─────────────────────────────

@Composable
private fun BookInfoPanel(
    book: Book,
    alpha: Float = 1f,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .graphicsLayer { this.alpha = alpha }
            .clip(RoundedCornerShape(AppRadius.md))
            .background(AppColors.CardBg)
            .padding(16.dp)
    ) {
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
                .clickable { /* UI 阶段，暂不处理 */ }
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
                text = "编辑书本信息",
                fontSize = AppType.Caption,
                color = AppColors.Accent
            )
        }
    }
}

// ─── 操作面板（4 个选项，从下到上依次淡入） ────────────────────

@Composable
private fun MenuActionsPanel(
    modifier: Modifier = Modifier,
    menuAlpha: Float,
    onAction: (ContextMenuAction) -> Unit
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(AppRadius.md))
            .background(AppColors.CardBg)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        // 从下到上：删除在最下，书签在最上
        // staggerIndex 0 = 最先出现（删除），3 = 最后出现（书签）
        val items = listOf(
            Triple("删除", Icons.Outlined.Delete, ContextMenuAction.Delete) to 0,
            Triple("收藏", Icons.Outlined.FavoriteBorder, ContextMenuAction.Favorite) to 1,
            Triple("自定义封面", Icons.Outlined.Image, ContextMenuAction.CustomCover) to 2,
            Triple("书签高亮与笔记", Icons.Outlined.Bookmark, ContextMenuAction.BookmarksNotes) to 3,
        )

        items.forEach { (data, staggerIndex) ->
            val (label, icon, action) = data
            // 每项延迟 60ms，从下往上依次淡入
            // menuAlpha 0→1 的过程中，各项目在不同时刻开始出现
            val itemDelay = staggerIndex * 0.15f // 0, 0.15, 0.30, 0.45
            val delayedAlpha = ((menuAlpha - itemDelay) / 0.3f).coerceIn(0f, 1f)
            MenuActionItem(
                label = label,
                icon = icon,
                alpha = delayedAlpha,
                onClick = { onAction(action) }
            )
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
