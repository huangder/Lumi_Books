package com.huangder.lumibooks.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.huangder.lumibooks.ui.theme.AppColors
import com.huangder.lumibooks.ui.theme.LocalIsDarkTheme
import com.kyant.backdrop.Backdrop
import kotlin.math.roundToInt

data class LiquidGlassMenuItem(
    val label: String,
    val icon: ImageVector? = null,
    val selected: Boolean = false,
    val destructive: Boolean = false,
    val onClick: () -> Unit
)

data class LiquidGlassMenuSpec(
    val anchorBounds: Rect,
    val width: Dp,
    val items: List<LiquidGlassMenuItem>,
    val alignEnd: Boolean = true,
    val maxVisibleItems: Int = 8,
    val onDismiss: () -> Unit = {}
)

@Stable
class LiquidGlassMenuHostState {
    var activeMenu by mutableStateOf<LiquidGlassMenuSpec?>(null)
        private set

    fun show(spec: LiquidGlassMenuSpec) {
        if (activeMenu !== spec) activeMenu?.onDismiss?.invoke()
        activeMenu = spec
    }

    fun dismiss() {
        activeMenu?.onDismiss?.invoke()
        activeMenu = null
    }
}

val LocalLiquidGlassMenuHost = staticCompositionLocalOf<LiquidGlassMenuHostState?> { null }

@Composable
fun LiquidGlassMenuHost(
    modifier: Modifier = Modifier,
    backdrop: Backdrop? = LocalLiquidGlassBackdrop.current,
    content: @Composable BoxScope.() -> Unit
) {
    val hostState = remember { LiquidGlassMenuHostState() }
    val visibility = remember { MutableTransitionState(false) }
    var displayedMenu by remember { mutableStateOf<LiquidGlassMenuSpec?>(null) }
    val activeMenu = hostState.activeMenu

    LaunchedEffect(activeMenu) {
        if (activeMenu != null) {
            displayedMenu = activeMenu
            visibility.targetState = true
        } else {
            visibility.targetState = false
        }
    }
    LaunchedEffect(visibility.isIdle, visibility.currentState) {
        if (visibility.isIdle && !visibility.currentState) {
            displayedMenu = null
        }
    }
    DisposableEffect(Unit) {
        onDispose { hostState.dismiss() }
    }

    ProvideLiquidGlassBackdrop(backdrop) {
        androidx.compose.runtime.CompositionLocalProvider(
            LocalLiquidGlassMenuHost provides hostState
        ) {
            BoxWithConstraints(modifier = modifier) {
                content()

                if (activeMenu != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable(
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() }
                            ) { hostState.dismiss() }
                    )
                }

                val menu = displayedMenu
                if (menu != null) {
                    val density = androidx.compose.ui.platform.LocalDensity.current
                    val horizontalMarginPx = with(density) { 8.dp.toPx() }
                    val verticalGapPx = with(density) { 6.dp.toPx() }
                    val menuWidthPx = with(density) { menu.width.toPx() }
                    val visibleItemCount = menu.items.size.coerceAtMost(menu.maxVisibleItems)
                    val menuHeightPx = with(density) {
                        (visibleItemCount * 44).dp.toPx() + 16.dp.toPx()
                    }
                    val hostWidthPx = with(density) { maxWidth.toPx() }
                    val hostHeightPx = with(density) { maxHeight.toPx() }
                    val preferredX = if (menu.alignEnd) {
                        menu.anchorBounds.right - menuWidthPx
                    } else {
                        menu.anchorBounds.left
                    }
                    val menuX = preferredX.coerceIn(
                        horizontalMarginPx,
                        (hostWidthPx - menuWidthPx - horizontalMarginPx).coerceAtLeast(horizontalMarginPx)
                    )
                    val belowY = menu.anchorBounds.bottom + verticalGapPx
                    val aboveY = menu.anchorBounds.top - menuHeightPx - verticalGapPx
                    val menuY = if (belowY + menuHeightPx <= hostHeightPx - horizontalMarginPx) {
                        belowY
                    } else {
                        aboveY.coerceAtLeast(horizontalMarginPx)
                    }

                    AnimatedVisibility(
                        visibleState = visibility,
                        modifier = Modifier
                            .offset {
                                IntOffset(menuX.roundToInt(), menuY.roundToInt())
                            }
                            .width(menu.width),
                        enter = fadeIn(tween(90)) + scaleIn(
                            initialScale = 0.88f,
                            transformOrigin = TransformOrigin(1f, 0f),
                            animationSpec = spring(dampingRatio = 0.74f, stiffness = 310f)
                        ),
                        exit = fadeOut(tween(100)) + scaleOut(
                            targetScale = 0.92f,
                            transformOrigin = TransformOrigin(1f, 0f),
                            animationSpec = tween(120)
                        )
                    ) {
                        AnchoredLiquidGlassMenu(menu, hostState, backdrop)
                    }
                }
            }
        }
    }
}

@Composable
private fun AnchoredLiquidGlassMenu(
    spec: LiquidGlassMenuSpec,
    hostState: LiquidGlassMenuHostState,
    backdrop: Backdrop?
) {
    val isDark = LocalIsDarkTheme.current
    val shape = RoundedCornerShape(24.dp)

    LiquidGlassSurface(
        shape = shape,
        fallbackColor = AppColors.CardBg,
        backdrop = backdrop,
        contentScrimColor = AppColors.CardBg.copy(alpha = if (isDark) 0.68f else 0.62f),
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.TopStart
    ) {
        Column(
            Modifier
                .padding(8.dp)
                .heightIn(max = (spec.maxVisibleItems * 44).dp)
                .verticalScroll(rememberScrollState())
        ) {
            spec.items.forEach { item ->
                val itemColor = if (item.destructive || item.selected) {
                    AppColors.Accent
                } else {
                    AppColors.TextPrimary
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() }
                        ) {
                            hostState.dismiss()
                            item.onClick()
                        }
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (item.icon != null) {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = null,
                            tint = itemColor,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(
                        text = item.label,
                        color = itemColor,
                        fontSize = 13.sp,
                        modifier = Modifier.weight(1f),
                        textAlign = if (item.icon == null) {
                            androidx.compose.ui.text.style.TextAlign.Center
                        } else {
                            androidx.compose.ui.text.style.TextAlign.Start
                        }
                    )
                }
            }
        }
    }
}
