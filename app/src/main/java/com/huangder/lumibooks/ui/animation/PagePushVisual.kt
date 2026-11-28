package com.huangder.lumibooks.ui.animation

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.os.Build
import android.os.SystemClock
import android.os.Trace
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
import androidx.compose.ui.graphics.drawscope.scale
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
import kotlinx.coroutines.CancellationException
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
internal const val PAGE_PUSH_TOOLBAR_BUDGET_MS = 50L
private const val TOOLBAR_BLUR_STEPS = 32
private const val TOOLBAR_BLUR_MAX_DP = 16f
internal val PagePushEasing = CubicBezierEasing(0.25f, 0.6f, 0.2f, 1f)
internal fun pagePushOffset(progress: Float, covered: Boolean): Float =
    if (covered) -0.25f * progress else 1f - progress

/** Buffers only while the underlying page and toolbar are being prepared. */
internal class PagePushGesture(private val setPosition: (Float) -> Unit) {
    private enum class State { IDLE, PREPARING, READY }

    private var state = State.IDLE
    private var latest = 1f

    fun start(position: Float) {
        latest = position
        state = State.PREPARING
    }

    fun ready() {
        if (state != State.PREPARING) return
        state = State.READY
        setPosition(latest)
    }

    fun update(progress: Float) {
        if (state == State.IDLE) return
        latest = 1f - progress.coerceIn(0f, 1f)
        if (state == State.READY) setPosition(latest)
    }

    fun stop(): Boolean {
        val ready = state == State.READY
        state = State.IDLE
        return ready
    }
}

internal class ToolbarLayer(val layer: GraphicsLayer, val padding: Float) {
    var coordinates: LayoutCoordinates? = null
    var recordedFor by mutableStateOf<PagePushToolbarCapture?>(null)
    var contentSize by mutableStateOf(IntSize.Zero)
    var placed by mutableStateOf(false)
    var renderedSnapshot: ImageBitmap? = null
    var renderedScale = 1f

    fun isReadyFor(capture: PagePushToolbarCapture) =
        placed && (contentSize.width == 0 || contentSize.height == 0 || capture[this] != null)
}

/** An all-or-nothing handoff, fixed before the page starts moving. */
internal class PagePushToolbarCapture(val deadline: Long) {
    enum class Mode { CAPTURING, OVERLAY, LIVE }

    var mode by mutableStateOf(Mode.CAPTURING)
        private set
    var failed by mutableStateOf(false)
        private set
    private val snapshots = mutableStateMapOf<ToolbarLayer, ImageBitmap>()

    operator fun get(item: ToolbarLayer): ImageBitmap? = snapshots[item]

    fun submit(item: ToolbarLayer, image: ImageBitmap?) {
        if (mode != Mode.CAPTURING || snapshots.containsKey(item)) return
        if (image == null) failed = true else snapshots[item] = image
    }

    fun complete(ready: Boolean) {
        if (mode != Mode.CAPTURING) return
        val useOverlay = ready && !failed
        mode = if (useOverlay) Mode.OVERLAY else Mode.LIVE
        if (!useOverlay) snapshots.clear()
    }
}

private var cachedBlurDensity = 0f
private var cachedBlurEffects: Array<RenderEffect?> = arrayOfNulls(TOOLBAR_BLUR_STEPS + 1)

private fun toolbarBlurSamplingScale(step: Int): Float =
    if (TOOLBAR_BLUR_MAX_DP * step / TOOLBAR_BLUR_STEPS > 2f) 0.5f else 1f

// Lazily shared by every toolbar on the same display density; UI thread only.
private fun toolbarBlur(density: Float, step: Int): RenderEffect? {
    if (Build.VERSION.SDK_INT < 31 || step == 0) return null
    if (cachedBlurDensity != density) {
        cachedBlurDensity = density
        cachedBlurEffects = arrayOfNulls(TOOLBAR_BLUR_STEPS + 1)
    }
    return cachedBlurEffects[step] ?: run {
        val radius = TOOLBAR_BLUR_MAX_DP * density * step / TOOLBAR_BLUR_STEPS * toolbarBlurSamplingScale(step)
        android.graphics.RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.DECAL)
            .asComposeRenderEffect().also { cachedBlurEffects[step] = it }
    }
}

private fun PageScene.activeToolbarCapture(): PagePushToolbarCapture? =
    coveredBy?.toolbarCapture ?: toolbarCapture

internal val LocalPageScene = staticCompositionLocalOf<PageScene?> { null }

/** Records only a control; the host draws it in a fixed overlay, outside the moving page clip. */
internal fun Modifier.pagePushToolbarItem(): Modifier = composed {
    val scene = LocalPageScene.current ?: return@composed this
    // Main tabs retain their composition at the same layout origin while hidden.
    // Their controls must not escape the tab's visibility into this window overlay.
    if (!LocalPageVisible.current) return@composed this
    val layer = rememberGraphicsLayer()
    val recorder = rememberGraphicsLayer()
    val padding = with(LocalDensity.current) { 24.dp.toPx() }
    val item = remember(layer, padding) { ToolbarLayer(layer, padding) }
    DisposableEffect(scene, item) {
        scene.controls[item] = item
        onDispose { scene.controls.remove(item) }
    }
    LaunchedEffect(scene, item) {
        snapshotFlow {
            scene.activeToolbarCapture()?.takeIf {
                it.mode == PagePushToolbarCapture.Mode.CAPTURING && item.placed &&
                    item.contentSize.width > 0 && item.contentSize.height > 0
            }
        }.collectLatest { capture ->
            if (capture == null || capture[item] != null) return@collectLatest
            snapshotFlow { item.recordedFor === capture }.first { it }
            // Allow the glass sampling coordinates from placement to reach the recorded layer.
            withFrameNanos { }
            if (capture.mode != PagePushToolbarCapture.Mode.CAPTURING) return@collectLatest
            val image = try {
                val cookie = System.identityHashCode(item)
                Trace.beginAsyncSection("Lumi.PagePush.toolbarCopy", cookie)
                try { recorder.toImageBitmap() } finally {
                    Trace.endAsyncSection("Lumi.PagePush.toolbarCopy", cookie)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            }
            capture.submit(item, image)
        }
    }
    this.onGloballyPositioned { item.coordinates = it; item.placed = true }
        .onSizeChanged { item.contentSize = it }
        .drawWithContent {
            val capture = scene.activeToolbarCapture()
            val overlay = capture?.mode == PagePushToolbarCapture.Mode.OVERLAY
            if (!overlay && item.renderedSnapshot != null) {
                item.layer.record(size = IntSize(1, 1)) { }
                item.renderedSnapshot = null
            }
            if (capture?.mode == PagePushToolbarCapture.Mode.CAPTURING &&
                capture[item] == null && size.width > 0f && size.height > 0f) {
                recorder.record(size = IntSize(ceil(size.width + padding * 2).toInt(),
                    ceil(size.height + padding * 2).toInt())) {
                    translate(padding, padding) { this@drawWithContent.drawContent() }
                }
                item.recordedFor = capture
                translate(-padding, -padding) { drawLayer(recorder) }
            } else {
                if (item.recordedFor != null) {
                    recorder.record(size = IntSize(1, 1)) { }
                    item.recordedFor = null
                }
                if (!overlay) drawContent()
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
    val gesture = remember(scene) { PagePushGesture { scene.progress = it } }
    var gestureJob by remember { mutableStateOf<Job?>(null) }
    val parent = scene.opener?.get()

    suspend fun moveTo(target: Float) {
        if (!motion || scene.speed == PagePushSpeed.NONE || scene.progress == target) {
            scene.progress = target
        } else {
            position.snapTo(scene.progress)
            val duration = if (scene.speed == PagePushSpeed.FAST) PAGE_PUSH_FAST_DURATION else PAGE_PUSH_DURATION
            position.animateTo(target, tween(duration, easing = PagePushEasing)) { scene.progress = value }
        }
    }

    suspend fun prepareToolbars() {
        val capture = scene.toolbarCapture ?: PagePushToolbarCapture(
            SystemClock.uptimeMillis() + PAGE_PUSH_TOOLBAR_BUDGET_MS
        ).also { scene.toolbarCapture = it }
        if (capture.mode != PagePushToolbarCapture.Mode.CAPTURING) return
        val remaining = capture.deadline - SystemClock.uptimeMillis()
        val ready = withTimeoutOrNull(remaining) {
            // Let the first draw register newly composed/laid out controls before sealing the group.
            withFrameNanos { }
            snapshotFlow {
                capture.failed || scene.controls.values.all { it.isReadyFor(capture) } &&
                    parent?.controls?.values.orEmpty().all { it.isReadyFor(capture) }
            }.first { it }
            !capture.failed
        }
        capture.complete(ready == true && SystemClock.uptimeMillis() <= capture.deadline)
    }

    LaunchedEffect(scene, closing, interactive) {
        if (closing || interactive) return@LaunchedEffect
        if (scene.moving) {
            if (motion && scene.speed != PagePushSpeed.NONE) prepareToolbars()
            moveTo(1f)
        }
        scene.moving = false
        scene.toolbarCapture = null
    }
    DisposableEffect(scene, motion) {
        scene.gestureStart = start@{
            if (closing || parent == null || !motion || scene.speed == PagePushSpeed.NONE) return@start
            interactive = true
            gesture.start(scene.progress)
            gestureJob = scope.launch {
                position.stop()
                if (withTimeoutOrNull(500) {
                    snapshotFlow { parent.started }.first { it }
                } != null && interactive) {
                    parent.coveredBy = scene
                    scene.moving = true
                    prepareToolbars()
                    gesture.ready()
                }
            }
        }
        scene.gestureProgress = gesture::update
        scene.gestureCancel = {
            gestureJob?.cancel()
            gesture.stop()
            interactive = false
        }
        if (scene.page) scene.close = close@{
            if (closing) return@close
            closing = true
            val gestureWasReady = gesture.stop()
            scope.launch {
                gestureJob?.cancel()
                position.stop()
                if (parent == null || !motion || scene.speed == PagePushSpeed.NONE) {
                    hostActivity.finish()
                    return@launch
                }
                // Request the real underlying page before moving either page; never animate a hole.
                val visible = gestureWasReady || (withTimeoutOrNull(500) {
                    snapshotFlow { parent.started }.first { it }
                } != null)
                if (!visible) {
                    hostActivity.finish()
                    return@launch
                }
                scene.moving = true
                parent.coveredBy = scene
                prepareToolbars()
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
    DisposableEffect(scene) {
        onDispose { scene.toolbarCapture = null }
    }

    val view = LocalView.current
    val density = LocalDensity.current
    var corners by remember(view, density) { mutableStateOf(displayCorners(view, density, parent?.corners)) }
    val pageColor = AppColors.WindowBg
    val shadowModifier = if (scene.page) remember(scene, corners) {
        Modifier.drawWithCache {
            val shadow = if (size.width > 0f && size.height > 0f) {
                buildPageShadow(size, corners, 24.dp.toPx())
            } else null
            onDrawBehind {
                if (scene.moving && shadow != null) {
                    val x = pagePushOffset(scene.progress, covered = false) * size.width
                    if (x > 0.5f) drawImage(shadow.image, Offset(x - shadow.padding, 0f))
                }
            }
        }
    } else Modifier
    Box(Modifier.fillMaxSize().onGloballyPositioned {
        corners = displayCorners(view, density, parent?.corners)
        scene.corners = corners
    }
        .then(shadowModifier)
        .drawWithContent {
            drawContent()
            val capture = scene.toolbarCapture
            if (scene.moving && capture?.mode == PagePushToolbarCapture.Mode.OVERLAY) {
                // Both sets are drawn in this stationary plane, without counter-translation or holes.
                parent?.takeIf { it.coveredBy === scene }?.let {
                    drawToolbar(it, capture, 1f - scene.progress, !reduceTransparency)
                }
                drawToolbar(scene, capture, scene.progress, !reduceTransparency)
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

internal fun DrawScope.drawToolbar(
    scene: PageScene, capture: PagePushToolbarCapture, reveal: Float, allowBlur: Boolean
) {
    val page = scene.pageCoordinates?.takeIf { it.isAttached } ?: return
    val shown = reveal.coerceIn(0f, 1f)
    if (shown <= 0f) return
    val blurStep = if (allowBlur && Build.VERSION.SDK_INT >= 31) {
        ((1f - shown) * TOOLBAR_BLUR_STEPS).toInt()
    } else 0
    val sampleScale = toolbarBlurSamplingScale(blurStep)
    val effect = toolbarBlur(density, blurStep)
    for (item in scene.controls.values) {
        val coordinates = item.coordinates?.takeIf { it.isAttached } ?: continue
        val bitmap = capture[item] ?: continue
        if (item.renderedSnapshot !== bitmap || item.renderedScale != sampleScale) {
            val sampledSize = IntSize(
                ceil(bitmap.width * sampleScale).toInt(),
                ceil(bitmap.height * sampleScale).toInt()
            )
            item.layer.record(size = sampledSize) {
                scale(sampleScale, sampleScale, pivot = Offset.Zero) { drawImage(bitmap) }
            }
            // Keep the padding and the toolbar's anchor in logical pixels at either resolution.
            item.layer.pivotOffset = Offset.Zero
            item.layer.scaleX = 1f / sampleScale
            item.layer.scaleY = 1f / sampleScale
            item.renderedSnapshot = bitmap
            item.renderedScale = sampleScale
        }
        // Local coordinates cancel the page's translation, preserving each control's resting place.
        val offset = page.localPositionOf(coordinates, Offset.Zero)
        item.layer.alpha = shown
        if (item.layer.renderEffect !== effect) item.layer.renderEffect = effect
        translate(offset.x - item.padding, offset.y - item.padding) { drawLayer(item.layer) }
    }
}

internal data class PageShadow(val image: ImageBitmap, val padding: Float)

/** The right side is always off screen; only the left edge and corners can cast a visible shadow. */
internal fun buildPageShadow(size: Size, corners: PageCorners, extent: Float): PageShadow {
    val stroke = extent / 16f * 1.1f
    val padding = ceil(extent + stroke / 2f)
    val leftRadius = maxOf(corners.topLeft, corners.bottomLeft).coerceAtMost(size.width)
    val width = ceil(padding + leftRadius + stroke / 2f + 1).toInt()
    val bitmap = Bitmap.createBitmap(width, ceil(size.height).toInt(), Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = stroke
    }
    val path = Path()
    val rect = RectF()
    val radii = FloatArray(8)
    for (step in 16 downTo 1) {
        val spread = extent * step / 16f
        val expanded = corners.expanded(spread)
        radii[0] = expanded.topLeft; radii[1] = expanded.topLeft
        radii[2] = expanded.topRight; radii[3] = expanded.topRight
        radii[4] = expanded.bottomRight; radii[5] = expanded.bottomRight
        radii[6] = expanded.bottomLeft; radii[7] = expanded.bottomLeft
        rect.set(padding - spread, -spread, padding + size.width + spread, size.height + spread)
        path.rewind()
        path.addRoundRect(rect, radii, Path.Direction.CW)
        val fade = 1f - step / 17f
        paint.alpha = (255 * 0.075f * fade * fade + 0.5f).toInt()
        canvas.drawPath(path, paint)
    }
    return PageShadow(bitmap.asImageBitmap(), padding)
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
        val reported = reportedScreenCornerPx(view, position)
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
