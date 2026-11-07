package com.huangder.lumibooks.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.huangder.lumibooks.R
import com.huangder.lumibooks.ui.icons.AppIcons
import com.huangder.lumibooks.ui.icons.directionalIcon
import com.huangder.lumibooks.ui.theme.*
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop

/** Saved as a fraction so changes in density, font size and title wrapping preserve the position. */
@Stable
internal class CollapsingPageState(initialProgress: Float = 0f) {
    var progress by mutableFloatStateOf(initialProgress.coerceIn(0f, 1f))
        private set
    internal var titleHeightPx by mutableFloatStateOf(0f)
        private set

    internal fun updateTitleHeight(height: Float) {
        if (height.isFinite() && height > 0f) titleHeightPx = height
    }

    internal fun remainingTitleHeightPx(): Float = titleHeightPx * (1f - progress)

    /** Finger coordinates: negative collapses, positive expands. Returns only the consumed distance. */
    fun consumeDelta(delta: Float): Float {
        if (!delta.isFinite() || titleHeightPx <= 0f) return 0f
        val before = progress
        progress = (before - delta / titleHeightPx).coerceIn(0f, 1f)
        return (before - progress) * titleHeightPx
    }

    companion object {
        val Saver = Saver<CollapsingPageState, Float>(save = { it.progress }, restore = { CollapsingPageState(it) })
    }
}

@Composable
internal fun rememberCollapsingPageState(): CollapsingPageState = rememberSaveable(saver = CollapsingPageState.Saver) {
    CollapsingPageState()
}

/** Padding for a page's own scroll content, inside its list or scroll modifier, never outside it. */
internal val LocalCollapsingPageTopInset = compositionLocalOf { 0.dp }

/** Lazy content reads this during measurement, keeping scroll-driven insets out of composition. */
internal val LocalCollapsingPageTopPaddingValues = compositionLocalOf<PaddingValues> { PaddingValues(0.dp) }

/** Height of an overlaid bottom bar; scrolling content pads its end by this much. */
internal val LocalCollapsingPageBottomInset = compositionLocalOf { 0.dp }

private class DynamicCollapsingTopPaddingValues(
    private val state: CollapsingPageState,
    private val density: androidx.compose.ui.unit.Density,
    private val fixedInsetPx: Float
) : PaddingValues {
    override fun calculateLeftPadding(layoutDirection: androidx.compose.ui.unit.LayoutDirection) = 0.dp
    override fun calculateRightPadding(layoutDirection: androidx.compose.ui.unit.LayoutDirection) = 0.dp
    override fun calculateBottomPadding() = 0.dp
    override fun calculateTopPadding(): androidx.compose.ui.unit.Dp = with(density) {
        (fixedInsetPx + state.remainingTitleHeightPx()).toDp()
    }
}

internal val PageToolbarButtonSize = 48.dp
internal val PageToolbarIconSize = 20.dp

/** Title presentation shared with the settings scaffold; scroll ownership stays with each page. */
@Composable
internal fun CollapsingPageLargeTitle(title: String, progress: () -> Float, modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE) {
    Text(title, modifier = modifier.graphicsLayer {
        val fraction = progress()
        scaleX = largeTitleScale(fraction)
        scaleY = scaleX
        alpha = largeTitleAlpha(fraction)
        transformOrigin = TransformOrigin(0f, 0.5f)
    }, fontSize = AppType.Display, lineHeight = AppType.Display * 1.2f, fontWeight = FontWeight.Bold,
        fontFamily = resolveAppFontFamily(KaiTi), color = AppColors.TextPrimary,
        maxLines = maxLines, overflow = TextOverflow.Ellipsis)
}

@Composable
internal fun CollapsingPageSmallTitle(title: String, progress: () -> Float, modifier: Modifier = Modifier) {
    Text(title, modifier = modifier.graphicsLayer {
        val fraction = progress()
        scaleX = smallTitleScale(fraction)
        scaleY = scaleX
        alpha = smallTitleAlpha(fraction)
    }, fontSize = AppType.Section, fontWeight = FontWeight.Bold,
        fontFamily = resolveAppFontFamily(KaiTi), color = AppColors.TextPrimary,
        maxLines = 1, overflow = TextOverflow.Ellipsis)
}

/**
 * A single bounded content viewport, supporting lazy lists, grids and native previews without an
 * outer scroll container. Only user scrolling moves the heading; programmatic report printing
 * and scroll-to-item operations never consume its height. Filters and bottom actions stay pinned.
 */
@Composable
internal fun CollapsingPageScaffold(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    actions: (@Composable RowScope.() -> Unit)? = null,
    pinnedContent: (@Composable () -> Unit)? = null,
    bottomBar: (@Composable () -> Unit)? = null,
    contentScrollEnabled: Boolean = true,
    headerGesturesEnabled: Boolean = true,
    drawContentBehindHeader: Boolean = false,
    /**
     * Float [bottomBar] over the content instead of stacking it below.
     * Content must pad its scroll end with [LocalCollapsingPageBottomInset].
     */
    bottomBarOverlay: Boolean = false,
    /** Draw a blur or fallback scrim behind an overlaid bottom bar. */
    bottomBarOverlayBackground: Boolean = true,
    state: CollapsingPageState = rememberCollapsingPageState(),
    content: @Composable ColumnScope.() -> Unit
) {
    val currentScrollEnabled by rememberUpdatedState(contentScrollEnabled)
    val connection = remember(state) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (!currentScrollEnabled || source != NestedScrollSource.UserInput || available.y >= 0f) return Offset.Zero
                return Offset(0f, state.consumeDelta(available.y))
            }
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                // Fling momentum that reaches the list top keeps going into the header, so one
                // upward fling from deep in the list lands on the expanded large title (iOS-like).
                if (!currentScrollEnabled || available.y <= 0f) return Offset.Zero
                if (source != NestedScrollSource.UserInput && source != NestedScrollSource.SideEffect) return Offset.Zero
                return Offset(0f, state.consumeDelta(available.y))
            }
        }
    }
    val density = LocalDensity.current
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val toolbarHeight = statusTop + 56.dp
    var pinnedHeightPx by remember { mutableIntStateOf(0) }
    var bottomBarHeightPx by remember { mutableIntStateOf(0) }
    val bottomBarHeight = with(density) { bottomBarHeightPx.toDp() }
    val backdrop = rememberLayerBackdrop()
    val capability = LocalLiquidGlassCapability.current
    val eInkMode = LocalEInkMode.current
    val blurEnabled = progressiveBlurEnabled(capability.supported, eInkMode, LocalReduceTransparency.current)
    val remainingTitleHeight = with(density) { (state.titleHeightPx * (1f - state.progress)).toDp() }
    val pinnedHeight = with(density) { pinnedHeightPx.toDp() }
    val dynamicTopPadding = remember(state, density, toolbarHeight, pinnedHeightPx, drawContentBehindHeader) {
        if (drawContentBehindHeader) {
            DynamicCollapsingTopPaddingValues(state, density, with(density) { toolbarHeight.toPx() + pinnedHeightPx })
        } else PaddingValues(0.dp)
    }
    // Browsing pages expand only when their content reaches the top. A native preview owns its
    // gestures, so its header can instead be dragged in either direction independently.
    val drag = rememberDraggableState { delta ->
        if (delta < 0f || !drawContentBehindHeader) state.consumeDelta(delta)
    }
    Box(modifier.fillMaxSize().background(AppColors.PageBg)) {
        Column(Modifier.fillMaxSize().then(if (blurEnabled) Modifier.layerBackdrop(backdrop) else Modifier)) {
            val topInset = toolbarHeight + remainingTitleHeight + pinnedHeight
            if (!drawContentBehindHeader) Spacer(Modifier.height(topInset))
            CompositionLocalProvider(LocalCollapsingPageTopInset provides if (drawContentBehindHeader) topInset else 0.dp,
                LocalCollapsingPageTopPaddingValues provides dynamicTopPadding,
                LocalCollapsingPageBottomInset provides if (bottomBarOverlay && bottomBar != null) bottomBarHeight else 0.dp) {
                Column(Modifier.weight(1f).fillMaxWidth().nestedScroll(connection), content = content)
            }
            if (!bottomBarOverlay) bottomBar?.invoke()
        }
        if (bottomBarOverlay && bottomBar != null) {
            // Keep the floating controls outside the content's captured layer.
            Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
                if (bottomBarOverlayBackground) {
                    // Tight band: blur only right behind the bar, fading out by its top edge.
                    val band = bottomBarHeight * .8f
                    if (blurEnabled) {
                        ProgressiveBottomBlur(backdrop = backdrop, bandHeight = band, modifier = Modifier.align(Alignment.BottomCenter),
                            tint = AppColors.WindowBg.copy(alpha = 4f * if (LocalIsDarkTheme.current) PROGRESSIVE_BLUR_TINT_ALPHA_DARK else PROGRESSIVE_BLUR_TINT_ALPHA))
                    } else if (progressiveTopScrimEnabled(capability.supported, eInkMode, LocalReduceTransparency.current)) {
                        ProgressiveBottomScrim(bandHeight = band, tint = AppColors.WindowBg, modifier = Modifier.align(Alignment.BottomCenter))
                    } else if (eInkMode) {
                        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(bottomBarHeight).background(AppColors.WindowBg))
                    }
                }
                Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().onSizeChanged { bottomBarHeightPx = it.height }) { bottomBar() }
            }
        }
        val strength = quantizeBlurStrength(state.progress)
        if (blurEnabled) {
            ProgressiveTopBlur(backdrop = backdrop, strength = strength,
                bandHeight = toolbarHeight + remainingTitleHeight + pinnedHeight + 56.dp,
                tint = AppColors.WindowBg.copy(alpha = if (LocalIsDarkTheme.current) PROGRESSIVE_BLUR_TINT_ALPHA_DARK else PROGRESSIVE_BLUR_TINT_ALPHA))
        } else {
            val headerHeight = toolbarHeight + remainingTitleHeight + pinnedHeight
            val bandHeight = headerHeight + if (eInkMode) 0.dp else 56.dp
            if (eInkMode) {
                Box(Modifier.fillMaxWidth().height(bandHeight)
                    .graphicsLayer { alpha = if (state.progress > 0f) 1f else strength }
                    .background(AppColors.WindowBg))
            } else {
                ProgressiveTopScrim(strength = strength, bandHeight = bandHeight,
                    tint = AppColors.WindowBg,
                    solidFraction = (headerHeight / bandHeight).coerceIn(0f, 1f))
            }
        }
        val headerDragModifier = if (headerGesturesEnabled) Modifier.draggable(drag, Orientation.Vertical) else Modifier
        Column(Modifier.align(Alignment.TopCenter).fillMaxWidth()) {
            Row(Modifier.fillMaxWidth().statusBarsPadding().height(56.dp).padding(horizontal = AppSpace.sm).then(headerDragModifier),
                verticalAlignment = Alignment.CenterVertically) {
                LiquidGlassIconButton(directionalIcon(AppIcons.ArrowLeft, AppIcons.ArrowRight),
                    stringResource(R.string.back), onBack, settingsBackButton = true)
                Box(Modifier.weight(1f).padding(horizontal = AppSpace.sm), contentAlignment = Alignment.Center) {
                    CollapsingPageSmallTitle(title, { state.progress })
                }
                if (actions != null) Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(AppSpace.sm), content = actions)
                else Spacer(Modifier.size(48.dp))
            }
            // Measure the unrestricted title, then clip its viewport; measuring a constrained title
            // would feed the shrinking height back into the collapse distance.
            Box(Modifier.fillMaxWidth().height(remainingTitleHeight).clipToBounds().then(headerDragModifier)) {
                CollapsingPageLargeTitle(title, { state.progress }, Modifier
                    .wrapContentHeight(unbounded = true, align = Alignment.Top)
                    .onSizeChanged { state.updateTitleHeight(it.height.toFloat()) }
                    .padding(horizontal = AppSpace.lg, vertical = AppSpace.sm)
                    .graphicsLayer { translationY = -state.titleHeightPx * state.progress }, maxLines = 3)
            }
            if (pinnedContent != null) Box(Modifier.fillMaxWidth().onSizeChanged { pinnedHeightPx = it.height }) {
                pinnedContent()
            }
        }
    }
}
