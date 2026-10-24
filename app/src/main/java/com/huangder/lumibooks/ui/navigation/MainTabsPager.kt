package com.huangder.lumibooks.ui.navigation

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.ScrollAxisRange
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.horizontalScrollAxisRange
import androidx.compose.ui.semantics.scrollToIndex
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlin.math.abs
import com.huangder.lumibooks.ui.animation.LocalMainTabItemMotion
import com.huangder.lumibooks.ui.animation.MainTabItemMotion
import com.huangder.lumibooks.ui.animation.MainTabMotion
import com.huangder.lumibooks.ui.animation.MAIN_TAB_SETTLE_LIMIT_MS
import com.huangder.lumibooks.ui.theme.LocalMotionEnabled

internal val mainTabRoutes = listOf(Screen.Home.route, Screen.Bookshelf.route, Screen.Statistics.route)

@Composable
internal fun MainTabsPager(
    state: PagerState,
    userScrollEnabled: Boolean,
    modifier: Modifier = Modifier,
    selection: MainTabSelection? = null,
    content: @Composable (Int) -> Unit
) {
    val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
    val scope = rememberCoroutineScope()
    val flingBehavior = PagerDefaults.flingBehavior(state)
    val dragInteractions = remember { MutableInteractionSource() }
    val dragging = dragInteractions.collectIsDraggedAsState()
    val motion = remember { MainTabMotion() }
    val motionEnabled = LocalMotionEnabled.current
    LaunchedEffect(state, motionEnabled, selection) {
        if (!motionEnabled) {
            motion.finish(state.currentPage.toFloat())
            return@LaunchedEffect
        }
        snapshotFlow { state.isScrollInProgress to (selection?.running == true) }
            .collectLatest { (scrolling, selecting) ->
                if (selecting) {
                    motion.finish(state.currentPage.toFloat())
                } else if (scrolling) {
                    motion.begin(state.settledPage, state.currentPage + state.currentPageOffsetFraction)
                    var sawDrag = dragging.value
                    while (true) {
                        val target = state.currentPage + state.currentPageOffsetFraction
                        val fingerDown = dragging.value
                        if (fingerDown) sawDrag = true else if (sawDrag) motion.release()
                        if (motion.isAtRest(target)) {
                            snapshotFlow {
                                (state.currentPage + state.currentPageOffsetFraction) to dragging.value
                            }.first { it.first != target || it.second != fingerDown }
                        }
                        withFrameNanos { time ->
                            if (dragging.value) sawDrag = true else if (sawDrag) motion.release()
                            motion.advance(time, state.currentPage + state.currentPageOffsetFraction)
                        }
                    }
                } else if (motion.running) {
                    motion.settle(state.currentPage + state.currentPageOffsetFraction)
                    var started = 0L
                    var finished = false
                    while (!finished) withFrameNanos { time ->
                        if (started == 0L) started = time
                        val target = state.currentPage + state.currentPageOffsetFraction
                        motion.advance(time, target)
                        finished = motion.isAtRest(target) ||
                            time - started >= MAIN_TAB_SETTLE_LIMIT_MS * 1_000_000L
                        if (finished) motion.finish(target)
                    }
                }
            }
    }
    val childScrollBoundary = remember {
        object : NestedScrollConnection {
            // Children own their entire horizontal gesture, including unused distance
            // and fling velocity at an edge. Direct page drags still reach scrollable.
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource) =
                Offset(available.x, 0f)

            override suspend fun onPostFling(consumed: Velocity, available: Velocity) =
                Velocity(available.x, 0f)
        }
    }
    Box(
        modifier.fillMaxSize().clipToBounds()
            .scrollable(
                state = state,
                orientation = Orientation.Horizontal,
                enabled = userScrollEnabled && selection?.running != true,
                reverseDirection = isLtr,
                interactionSource = dragInteractions,
                flingBehavior = flingBehavior
            )
            .nestedScroll(childScrollBoundary)
            .semantics {
                horizontalScrollAxisRange = ScrollAxisRange(
                    value = { state.currentPage + state.currentPageOffsetFraction },
                    maxValue = { mainTabRoutes.lastIndex.toFloat() },
                    reverseScrolling = !isLtr
                )
                if (userScrollEnabled) scrollToIndex { index ->
                    if (index !in mainTabRoutes.indices) false else {
                        scope.launch {
                            if (selection != null) selection.select(state, index, motionEnabled)
                            else state.animateScrollToPage(index)
                        }
                        true
                    }
                }
            }
    ) {
        // Foundation measures empty slots and owns snapping/restoration. Real pages stay
        // laid out at the origin: drawing their layers avoids global-position callbacks
        // invalidating every glass sampler, cover anchor and list on every drag frame.
        HorizontalPager(
            state = state,
            modifier = Modifier.matchParentSize().clearAndSetSemantics { },
            userScrollEnabled = false,
            key = { mainTabRoutes[it] }
        ) { Box(Modifier.fillMaxSize()) }
        mainTabRoutes.indices.forEach { page ->
            key(mainTabRoutes[page]) {
                MainTabPage(
                    state, page, isLtr,
                    motion.takeIf { motionEnabled && selection?.running != true }, selection, content
                )
            }
        }
    }
}

@Composable
private fun MainTabPage(
    state: PagerState,
    page: Int,
    isLtr: Boolean,
    motion: MainTabMotion?,
    selection: MainTabSelection?,
    content: @Composable (Int) -> Unit
) {
    val parent = LocalLifecycleOwner.current
    val owner = remember { MainTabLifecycleOwner() }
    val itemMotion = remember(motion, page, isLtr) {
        motion?.let { MainTabItemMotion(it, page, if (isLtr) 1f else -1f) }
    }
    val active = state.settledPage == page
    DisposableEffect(parent, owner, active) {
        fun updateLifecycle() {
            val parentState = parent.lifecycle.currentState
            owner.registry.currentState = if (active || parentState < Lifecycle.State.CREATED) {
                parentState
            } else {
                Lifecycle.State.CREATED
            }
        }
        val observer = LifecycleEventObserver { _, _ -> updateLifecycle() }
        parent.lifecycle.addObserver(observer)
        updateLifecycle()
        onDispose { parent.lifecycle.removeObserver(observer) }
    }
    DisposableEffect(owner) {
        onDispose { owner.registry.currentState = Lifecycle.State.DESTROYED }
    }
    // Retained neighbours must not intercept Back or resume work for the visible tab.
    CompositionLocalProvider(
        LocalLifecycleOwner provides owner,
        LocalMainTabItemMotion provides itemMotion
    ) {
        Box(
            Modifier.fillMaxSize()
                .zIndex(if (active) 1f else 0f)
                .drawWithContent {
                    if (selection?.running == true) {
                        if (page == selection.fromPage || page == selection.toPage) {
                            this@drawWithContent.drawContent()
                        }
                    } else {
                        val distance = page - state.currentPage - state.currentPageOffsetFraction
                        if (abs(distance) < 1f) {
                            translate(left = distance * size.width * if (isLtr) 1f else -1f) {
                                this@drawWithContent.drawContent()
                            }
                        }
                    }
                }
                .graphicsLayer { alpha = if (selection?.running == true) selection.alpha(page) else 1f }
                // Canvas translation leaves hit testing at the origin. The active page
                // stays on top and receives empty-area touches before retained siblings.
                .pointerInput(Unit) {
                    awaitPointerEventScope { while (true) awaitPointerEvent() }
                }
                .then(if (active) Modifier else Modifier.clearAndSetSemantics { })
        ) { content(page) }
    }
}

private class MainTabLifecycleOwner : LifecycleOwner {
    val registry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle get() = registry
}
