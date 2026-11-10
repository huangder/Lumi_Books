package com.huangder.lumibooks.ui.animation

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Shader
import android.os.Build
import android.view.RoundedCorner
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntSize
import kotlin.math.ceil
import com.huangder.lumibooks.ui.theme.AppColors
import com.huangder.lumibooks.ui.theme.LocalMotionEnabled
import com.huangder.lumibooks.ui.theme.LocalReduceTransparency
import com.huangder.lumibooks.ui.theme.LocalPageVisible
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.withTimeoutOrNull

internal const val PAGE_PUSH_DURATION = 560
internal const val PAGE_PUSH_FAST_DURATION = 380
internal val PagePushEasing = CubicBezierEasing(0.25f, 0.6f, 0.2f, 1f)
internal fun pagePushOffset(progress: Float, covered: Boolean): Float =
    if (covered) -0.25f * progress else 1f - progress

internal class ToolbarLayer(val layer: GraphicsLayer) {
    var coordinates: LayoutCoordinates? = null
    var recorded by mutableStateOf(false)
    var contentSize by mutableStateOf(IntSize.Zero)
    var placed by mutableStateOf(false)
    var snapshot by mutableStateOf<ImageBitmap?>(null)
    var renderedSnapshot: ImageBitmap? = null
    var padding = 0f
    var blurEffects: List<RenderEffect?> = emptyList()
}

internal val LocalPageScene = staticCompositionLocalOf<PageScene?> { null }

/** Records only a control; the host draws it in a fixed overlay, outside the moving page clip. */
internal fun Modifier.pagePushToolbarItem(): Modifier = composed {
    val scene = LocalPageScene.current ?: return@composed this
    // Main tabs retain their composition at the same layout origin while hidden.
    // Their controls must not escape the tab's visibility into this window overlay.
    if (!LocalPageVisible.current) return@composed this
    val layer = rememberGraphicsLayer()
    val recorder = rememberGraphicsLayer()
    val item = remember(layer) { ToolbarLayer(layer) }
    val key = remember { Any() }
    val density = LocalDensity.current
    val padding = with(density) { 24.dp.toPx() }
    val effects = remember(density) {
        List(33) { step ->
            if (Build.VERSION.SDK_INT < 31 || step == 0) null else {
                val radius = with(density) { 16.dp.toPx() } * step / 32f
                android.graphics.RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.DECAL)
                    .asComposeRenderEffect()
            }
        }
    }
    DisposableEffect(scene, item) {
        scene.controls[key] = item
        onDispose { scene.controls.remove(key) }
    }
    LaunchedEffect(scene, item) {
        snapshotFlow { Triple(scene.moving || scene.coveredBy?.moving == true,
            item.contentSize, item.placed) }.collectLatest { (moving, size, placed) ->
            item.snapshot = null
            if (moving && placed && size.width > 0 && size.height > 0) {
                snapshotFlow { item.recorded }.first { it }
                // Global positioning also initializes the glass backdrop's sampling
                // coordinates. Let it redraw before copying pixels, rather than
                // freezing a first-frame sample from the wrong part of the page.
                repeat(2) { withFrameNanos { } }
                // Copy pixels while the source still draws normally. A recorded Compose
                // subtree contains live RenderNodes, so retaining its display list while
                // hiding the source can invalidate it on the hardware renderer.
                val bitmap = recorder.toImageBitmap()
                item.snapshot = bitmap
            }
        }
    }
    this.onGloballyPositioned { item.coordinates = it; item.placed = true }
        .onSizeChanged { item.contentSize = it; item.recorded = false }
        .drawWithContent {
        item.padding = padding
        item.blurEffects = effects
        val transition = scene.coveredBy?.takeIf { it.moving } ?: scene.takeIf { it.moving }
        // Keep recording while hidden so late actions (loaded settings, for example)
        // can get a new snapshot when the toolbar's size changes.
        if (transition == null || item.snapshot == null) {
            recorder.record(size = IntSize(ceil(size.width + padding * 2).toInt(),
                ceil(size.height + padding * 2).toInt())) {
                translate(padding, padding) { this@drawWithContent.drawContent() }
            }
            item.recorded = true
        }
        if (transition?.toolbarReady != true) {
            translate(-padding, -padding) { drawLayer(recorder) }
        }
    }
}

@Composable
internal fun PagePushHost(content: @Composable () -> Unit) {
    val activity = LocalContext.current.activity()
    val scene = activity?.let(PagePushTransitions::scene)
    if (Build.VERSION.SDK_INT < 30 || scene == null || LocalPageScene.current != null) {
        content()
        return
    }
    val hostActivity = scene.activity
    val motion = LocalMotionEnabled.current
    val reduceTransparency = LocalReduceTransparency.current
    val scope = rememberCoroutineScope()
    val position = remember(scene) { Animatable(scene.progress) }
    var closing by remember { mutableStateOf(false) }
    var interactive by remember { mutableStateOf(false) }
    var gestureReady by remember { mutableStateOf(false) }
    var gestureProgress by remember { mutableFloatStateOf(0f) }
    var gestureJob by remember { mutableStateOf<Job?>(null) }
    val parent = scene.opener?.get()

    suspend fun moveTo(target: Float) {
        if (!motion || scene.speed == PagePushSpeed.NONE) {
            position.snapTo(target)
            scene.progress = target
        } else {
            val duration = if (scene.speed == PagePushSpeed.FAST) PAGE_PUSH_FAST_DURATION else PAGE_PUSH_DURATION
            position.animateTo(target, tween(duration, easing = PagePushEasing)) { scene.progress = value }
        }
    }

    suspend fun awaitToolbarSnapshots() {
        scene.toolbarReady = false
        withTimeoutOrNull(250) {
            snapshotFlow {
                (scene.controls.values + parent?.controls?.values.orEmpty()).all {
                    it.placed && (it.contentSize.width == 0 || it.contentSize.height == 0 || it.snapshot != null)
                }
            }.first { it }
        }
        // Source and overlay read this same flag: no per-button handoff or double draw.
        scene.toolbarReady = true
    }

    LaunchedEffect(scene, closing, interactive) {
        if (closing || interactive) return@LaunchedEffect
        if (scene.moving) {
            // Let initial composition register and draw both sets of controls first.
            withFrameNanos { }
            awaitToolbarSnapshots()
            moveTo(1f)
        }
        scene.moving = false
    }
    LaunchedEffect(interactive, gestureReady, gestureProgress) {
        if (interactive && gestureReady && !closing) {
            position.snapTo(1f - gestureProgress.coerceIn(0f, 1f))
            scene.progress = position.value
        }
    }
    DisposableEffect(scene, motion) {
        scene.gestureStart = start@{
            if (closing || parent == null || !motion || scene.speed == PagePushSpeed.NONE) return@start
            interactive = true
            gestureReady = false
            gestureProgress = 0f
            gestureJob = scope.launch {
                position.stop()
                if (withTimeoutOrNull(500) {
                    snapshotFlow { parent.started }.first { it }
                } != null && interactive) {
                    repeat(2) { withFrameNanos { } }
                    parent.coveredBy = scene
                    scene.moving = true
                    awaitToolbarSnapshots()
                    gestureReady = true
                }
            }
        }
        scene.gestureProgress = { gestureProgress = it }
        scene.gestureCancel = {
            gestureJob?.cancel()
            gestureReady = false
            interactive = false
        }
        if (scene.page) scene.close = close@{
            if (closing) return@close
            closing = true
            scope.launch {
                gestureJob?.cancel()
                position.stop()
                if (parent == null || !motion || scene.speed == PagePushSpeed.NONE) {
                    hostActivity.finish()
                    return@launch
                }
                // Request the real underlying page before moving either page; never animate a hole.
                val visible = gestureReady || (withTimeoutOrNull(500) {
                    snapshotFlow { parent.started }.first { it }
                } != null)
                if (!visible) {
                    hostActivity.finish()
                    return@launch
                }
                if (!gestureReady) repeat(2) { withFrameNanos { } }
                scene.moving = true
                parent.coveredBy = scene
                awaitToolbarSnapshots()
                moveTo(0f)
                parent.coveredBy = null
                // Keep the outgoing page off screen until its window is removed. Resetting
                // moving here would draw it at x=0 for a frame while finish() is dispatched.
                PagePushTransitions.suppress(hostActivity)
                hostActivity.finish()
            }
        }
        onDispose {
            scene.close = null
            scene.gestureStart = null
            scene.gestureProgress = null
            scene.gestureCancel = null
        }
    }

    val view = LocalView.current
    val density = LocalDensity.current
    var corners by remember(view, density) { mutableStateOf(displayCorners(view, density, parent?.corners)) }
    val pageColor = AppColors.WindowBg
    Box(Modifier.fillMaxSize().onGloballyPositioned {
        corners = displayCorners(view, density, parent?.corners)
        scene.corners = corners
    }
        .drawWithCache {
            // Nested rounded silhouettes follow the same four radii as the page.
            val shadows = (16 downTo 1).map { step ->
                val spread = 24.dp.toPx() * step / 16f
                Triple(spread, corners.expanded(spread).createOutline(
                    Size(size.width + spread * 2, size.height + spread * 2), layoutDirection, this),
                    Color.Black.copy(alpha = 0.075f * (1f - step / 17f) * (1f - step / 17f)))
            }
            val shadowStroke = Stroke(width = 24.dp.toPx() / 16f * 1.1f)
            onDrawBehind {
                if (scene.moving) {
                    val x = pagePushOffset(scene.progress, covered = false) * size.width
                    if (x > 0.5f) for ((spread, outline, color) in shadows) {
                        translate(x - spread, -spread) { drawOutline(outline, color, style = shadowStroke) }
                    }
                }
            }
        }
        .drawWithContent {
            drawContent()
            if (scene.moving && scene.toolbarReady) {
                // Both sets are drawn in this stationary plane, without counter-translation or holes.
                parent?.takeIf { it.coveredBy === scene }?.let {
                    drawToolbar(it, 1f - scene.progress, !reduceTransparency)
                }
                drawToolbar(scene, scene.progress, !reduceTransparency)
            }
        }) {
        Box(Modifier.fillMaxSize().graphicsLayer {
            val child = scene.coveredBy
            val offset = if (child != null) pagePushOffset(child.progress, covered = true)
                else if (scene.moving) pagePushOffset(scene.progress, covered = false) else 0f
            translationX = offset * size.width
            clip = scene.moving && offset > 0.0001f
            shape = corners
        }.onPlaced { scene.pageCoordinates = it }
            .then(if (scene.page) Modifier.background(pageColor) else Modifier)) {
            CompositionLocalProvider(LocalPageScene provides scene) { content() }
        }
    }
}

internal fun DrawScope.drawToolbar(scene: PageScene, reveal: Float, allowBlur: Boolean) {
    val page = scene.pageCoordinates?.takeIf { it.isAttached } ?: return
    val shown = reveal.coerceIn(0f, 1f)
    if (shown <= 0f) return
    for (item in scene.controls.values) {
        val coordinates = item.coordinates?.takeIf { it.isAttached } ?: continue
        val bitmap = item.snapshot ?: continue
        if (item.renderedSnapshot !== bitmap) {
            item.layer.record(size = IntSize(bitmap.width, bitmap.height)) { drawImage(bitmap) }
            item.renderedSnapshot = bitmap
        }
        // Local coordinates cancel the page's translation, preserving each control's resting place.
        val offset = page.localPositionOf(coordinates, Offset.Zero)
        item.layer.alpha = shown
        item.layer.renderEffect = if (allowBlur) item.blurEffects.getOrNull(
            ((1f - shown) * 32f).toInt().coerceIn(0, 32)) else null
        translate(offset.x - item.padding, offset.y - item.padding) { drawLayer(item.layer) }
    }
}

internal data class PageCorners(val topLeft: Float, val topRight: Float, val bottomRight: Float, val bottomLeft: Float) : Shape {
    fun expanded(amount: Float) = PageCorners(topLeft + amount, topRight + amount,
        bottomRight + amount, bottomLeft + amount)
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        Outline.Rounded(RoundRect(0f, 0f, size.width, size.height,
            CornerRadius(topLeft), CornerRadius(topRight), CornerRadius(bottomRight), CornerRadius(bottomLeft)))
}

internal fun resolvedPageRadius(reported: Float?, inherited: Float?, fallback: Float): Float =
    reported?.takeIf { it > 0f } ?: inherited?.takeIf { it > 0f } ?: fallback

private fun displayCorners(view: android.view.View, density: Density, inherited: PageCorners?): PageCorners {
    val fallback = with(density) { 32.dp.toPx() }
    fun radius(position: Int, previous: Float?): Float {
        val reported = if (Build.VERSION.SDK_INT >= 31) {
            view.rootView.rootWindowInsets?.getRoundedCorner(position)?.radius?.toFloat()
        } else null
        // Some OEMs return a zero-radius corner for secondary translucent windows.
        return resolvedPageRadius(reported, previous, fallback)
    }
    return if (Build.VERSION.SDK_INT >= 31) PageCorners(
        radius(RoundedCorner.POSITION_TOP_LEFT, inherited?.topLeft), radius(RoundedCorner.POSITION_TOP_RIGHT, inherited?.topRight),
        radius(RoundedCorner.POSITION_BOTTOM_RIGHT, inherited?.bottomRight), radius(RoundedCorner.POSITION_BOTTOM_LEFT, inherited?.bottomLeft)
    ) else PageCorners(fallback, fallback, fallback, fallback)
}

private tailrec fun Context.activity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.activity()
    else -> null
}
