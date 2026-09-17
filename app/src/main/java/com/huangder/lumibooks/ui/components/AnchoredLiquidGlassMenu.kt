package com.huangder.lumibooks.ui.components
import android.os.Build
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.key
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.BiasAbsoluteAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import com.huangder.lumibooks.ui.theme.AppColors
import com.huangder.lumibooks.ui.theme.LocalAppTheme
import com.huangder.lumibooks.ui.theme.LocalIsDarkTheme
import com.huangder.lumibooks.ui.theme.LocalMotionEnabled
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.rememberBackdrop
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.first

data class LiquidGlassMenuItem(
    val label: String,
    val icon: ImageVector? = null,
    val selected: Boolean = false,
    val destructive: Boolean = false,
    val onClick: () -> Unit
)

/**
 * Spring parameters for the anchored menu morph.
 *
 * Normalized progress (0 = the trigger control's own rectangle, 1 = the settled
 * menu rectangle) is animated with these specs. Keep every value overridable so
 * one menu can be tuned without editing the animation logic.
 */
data class LiquidGlassMenuMotion(
    val openStiffness: Float = DefaultOpenStiffness,
    val openDampingRatio: Float = DefaultOpenDampingRatio,
    /** Extra kick added on top of the momentum carried over from an interruption. */
    val openInitialVelocity: Float = 0f,
    val closeStiffness: Float = DefaultCloseStiffness,
    val closeDampingRatio: Float = DefaultCloseDampingRatio,
    /** Extra kick added on top of the momentum carried over from an interruption. */
    val closeInitialVelocity: Float = 0f
) {
    internal fun openSpec(): SpringSpec<Float> = spring(
        dampingRatio = openDampingRatio,
        stiffness = openStiffness,
        visibilityThreshold = LiquidGlassMenuMorph.ProgressVisibilityThreshold
    )

    internal fun closeSpec(): SpringSpec<Float> = spring(
        dampingRatio = closeDampingRatio,
        stiffness = closeStiffness,
        visibilityThreshold = LiquidGlassMenuMorph.ProgressVisibilityThreshold
    )

    companion object {
        /**
         * Readable anticipation and travel, followed by a visible, soft overshoot.
         * A spring's duration scales with `1 / sqrt(stiffness)`, so the values
         * here are the single knob for overall speed: quartering stiffness
         * roughly doubles the time taken while keeping the damping ratio (and
         * therefore the overshoot) identical.
         */
        const val DefaultOpenStiffness = 150f
        const val DefaultOpenDampingRatio = 0.62f

        /** Closing is quicker and only barely under-damped, so it settles softly. */
        const val DefaultCloseStiffness = 220f
        const val DefaultCloseDampingRatio = 0.80f

        val Default = LiquidGlassMenuMotion()
        internal val Legacy = LiquidGlassMenuMotion(120f, 0.7f, 0f, 155f, 0.82f)
    }
}

data class LiquidGlassMenuSpec(
    val anchorBounds: Rect,
    val width: Dp,
    val items: List<LiquidGlassMenuItem>,
    val alignEnd: Boolean = true,
    val maxVisibleItems: Int = 8,
    /**
     * Corner radius of the trigger control. `null` treats the trigger as a
     * capsule (half of its shortest side), which is what circular glass buttons
     * need so the menu can start out as the button itself.
     */
    val anchorCornerRadius: Dp? = null,
    val motion: LiquidGlassMenuMotion = LiquidGlassMenuMotion.Default,
    val onDismiss: () -> Unit = {},
    val sourceId: Any? = null,
    val preferAbove: Boolean = false,
    /** Window-space area that stays interactive, e.g. the TTS playback capsule. */
    val passThroughBounds: (() -> Rect)? = null,
    val surfaceColor: Color = Color.Unspecified,
    val contentColor: Color = Color.Unspecified,
    val forceSolid: Boolean = false,
    /** The host supplies scrolling and an atomic, single-submission selection callback. */
    val content: (@Composable (enabled: Boolean, select: (() -> Unit) -> Unit) -> Unit)? = null
)

/**
 * Geometry and timing math for the anchored menu morph.
 *
 * The container *is* the animation: it starts on the trigger control's rectangle
 * and settles on the menu rectangle, so the user reads one piece of glass
 * changing shape instead of a second panel popping in next to the button.
 * Keeping this free of Compose state makes every curve unit-testable.
 */
internal object LiquidGlassMenuMorph {
    /** Spring visibility threshold; also the Animatable's own threshold. */
    const val ProgressVisibilityThreshold = 0.001f

    /** Corner radius the expanded menu settles on. */
    const val TargetCornerRadiusDp = 24f

    /** Progress where the anticipation squash ends and the growth takes over. */
    const val SquashEnd = 0.35f

    /** How much smaller than the trigger the glass gets at the deepest point. */
    const val SquashAmount = 0.12f

    const val PressEnd = 0.28f
    const val PressAmount = 0.10f

    /** Container opacity reaches 1 here, both while opening and while closing. */
    const val FadeInEnd = 0.15f

    /** Blur added on top of the surface blur while the container is still fresh. */
    const val BlurBoostDp = 10f

    /** Growth progress at which the contents start to surface. */
    const val ContentRevealStart = 0.15f

    /** How far a row travels while it surfaces. */
    const val ItemTravelDp = 8f

    /** Row scale at the moment it starts to surface. */
    const val ItemStartScale = 1f

    /** Blur applied to the whole content group while it surfaces. */
    const val ContentBlurDp = 6f

    /** Total reveal spread across all items; small enough to read as one group. */
    const val ItemRevealSpread = 0.06f

    /** Lowest reveal at which a row starts accepting taps. */
    const val ItemClickableReveal = 0.55f

    /** How far past the target a spring may overshoot before the geometry clamps. */
    private const val MaxOvershoot = 0.10f

    /** Exponent that locks the anchored edge early in the expansion. */
    private const val NearEdgeExponent = 0.4f

    private const val AnchorMatchTolerancePx = 1f

    fun sameAnchor(first: Rect?, second: Rect): Boolean {
        if (first == null) return false
        return abs(first.left - second.left) < AnchorMatchTolerancePx &&
            abs(first.top - second.top) < AnchorMatchTolerancePx &&
            abs(first.width - second.width) < AnchorMatchTolerancePx &&
            abs(first.height - second.height) < AnchorMatchTolerancePx
    }

    /** A menu re-anchored on the same control resumes from the current shape. */
    fun keepsCurrentProgress(currentAnchor: Rect?, newAnchor: Rect, progress: Float): Boolean =
        progress > 0f && sameAnchor(currentAnchor, newAnchor)

    fun menuHeightPx(
        itemCount: Int,
        maxVisibleItems: Int,
        rowHeightPx: Float,
        paddingPx: Float
    ): Float = itemCount.coerceAtMost(maxVisibleItems).coerceAtLeast(0) * rowHeightPx + paddingPx

    /** Radius of a trigger that has no explicit shape: a capsule. */
    fun capsuleCornerRadiusPx(anchor: Rect): Float = min(anchor.width, anchor.height) / 2f

    fun targetRect(
        anchor: Rect,
        widthPx: Float,
        heightPx: Float,
        hostWidthPx: Float,
        hostHeightPx: Float,
        marginPx: Float,
        gapPx: Float,
        alignEnd: Boolean,
        overlapAnchor: Boolean = true,
        preferAbove: Boolean = false
    ): Rect {
        val width = widthPx.coerceIn(1f, (hostWidthPx - marginPx * 2f).coerceAtLeast(1f))
        val height = heightPx.coerceIn(1f, (hostHeightPx - marginPx * 2f).coerceAtLeast(1f))
        val preferredX = if (alignEnd) anchor.right - width else anchor.left
        val x = preferredX.coerceIn(
            marginPx,
            (hostWidthPx - width - marginPx).coerceAtLeast(marginPx)
        )
        val belowY = if (overlapAnchor) anchor.top else anchor.bottom + gapPx
        val aboveY = if (overlapAnchor) anchor.bottom - height else anchor.top - height - gapPx
        val y = if (!preferAbove && belowY + height <= hostHeightPx - marginPx) {
            belowY
        } else {
            aboveY.coerceAtLeast(marginPx)
        }
        val safeY = y.coerceIn(marginPx, (hostHeightPx - height - marginPx).coerceAtLeast(marginPx))
        return Rect(x, safeY, x + width, safeY + height)
    }

    /** 1 right after the touch, decaying to 0 as the container starts growing. */
    fun touchImpulse(progress: Float): Float =
        ((SquashEnd - progress) / SquashEnd).coerceIn(0f, 1f)

    /**
     * Parabolic anticipation: while the container is still trigger-sized it
     * compresses to [SquashAmount] below its own size and springs back, so the
     * glass reads as being pressed before it stretches out into the menu.
     */
    fun squashScale(progress: Float, continuous: Boolean = false): Float {
        val end = if (continuous) PressEnd else SquashEnd
        val amount = if (continuous) PressAmount else SquashAmount
        val peak = end / 2f
        if (progress <= 0f || progress >= end) return 1f
        val offset = (progress - peak) / peak
        return 1f - amount * (1f - offset * offset)
    }

    /**
     * 0 while the glass compresses, then 0..1 for the actual morph. Deliberately
     * unbounded above so the spring's overshoot survives the remap; [morphRect]
     * is what clamps it to the allowed overshoot.
     */
    fun growthProgress(progress: Float, continuous: Boolean = true): Float {
        val end = if (continuous) PressEnd else SquashEnd
        return ((progress - end) / (1f - end)).coerceAtLeast(0f)
    }

    /** Opacity of the container: quick fade in when opening, fade out when closing. */
    fun containerAlpha(progress: Float): Float = (progress / FadeInEnd).coerceIn(0f, 1f)

    /** Animated glass blur: very blurry while fresh, crisp once settled. */
    fun blurBoostDp(progress: Float): Float =
        (1f - progress.coerceIn(0f, 1f)) * BlurBoostDp

    /**
     * +1 when the stable (anchored) edge on this axis is the trailing one, -1
     * when it is the leading one. The anchored edge is whichever edge of the
     * trigger ends up closest to its counterpart on the menu, which keeps the
     * menu glued to the side of the button it grew out of.
     */
    fun nearEdgeBias(
        sourceStart: Float,
        sourceEnd: Float,
        targetStart: Float,
        targetEnd: Float
    ): Float = if (abs(targetEnd - sourceEnd) <= abs(targetStart - sourceStart)) 1f else -1f

    fun nearEdgeBiasX(source: Rect, target: Rect): Float =
        nearEdgeBias(source.left, source.right, target.left, target.right)

    fun nearEdgeBiasY(source: Rect, target: Rect): Float =
        nearEdgeBias(source.top, source.bottom, target.top, target.bottom)

    /** Corner of the menu the trigger stays attached to. */
    fun nearAlignment(source: Rect, target: Rect): Alignment =
        BiasAbsoluteAlignment(nearEdgeBiasX(source, target), nearEdgeBiasY(source, target))

    fun morphRect(source: Rect, target: Rect, progress: Float, continuous: Boolean = true): Rect {
        val clamped = growthProgress(progress, continuous).coerceIn(0f, 1f + MaxOvershoot)
        val nearT = clamped.coerceAtLeast(0f).pow(NearEdgeExponent)
        val farT = clamped
        val nearX = nearEdgeBiasX(source, target)
        val nearY = nearEdgeBiasY(source, target)

        val left = lerp(source.left, target.left, if (nearX < 0f) nearT else farT)
        val right = lerp(source.right, target.right, if (nearX > 0f) nearT else farT)
        val top = lerp(source.top, target.top, if (nearY < 0f) nearT else farT)
        val bottom = lerp(source.bottom, target.bottom, if (nearY > 0f) nearT else farT)

        val scale = squashScale(progress, continuous)
        if (scale >= 1f) return Rect(left, top, right, bottom)

        // The squash only happens while the container is still the trigger's own
        // rectangle, so compressing around its centre squashes the button itself.
        val centerX = (left + right) / 2f
        val centerY = (top + bottom) / 2f
        val halfWidth = (right - left) / 2f * scale
        val halfHeight = (bottom - top) / 2f * scale
        return Rect(
            centerX - halfWidth,
            centerY - halfHeight,
            centerX + halfWidth,
            centerY + halfHeight
        )
    }

    /** [growthProgress] drives the radius, so the squashing button keeps its own. */
    fun cornerRadiusPx(sourceRadiusPx: Float, targetRadiusPx: Float, growthProgress: Float): Float =
        lerp(sourceRadiusPx, targetRadiusPx, growthProgress.coerceIn(0f, 1f + MaxOvershoot))

    fun contentProgress(growthProgress: Float): Float =
        ((growthProgress - ContentRevealStart) / (0.85f - ContentRevealStart)).coerceIn(0f, 1f)

    fun sourceAlpha(progress: Float): Float = (1f - growthProgress(progress) / 0.3f).coerceIn(0f, 1f)

    fun windowToHost(bounds: Rect, hostOrigin: Offset): Rect = bounds.translate(-hostOrigin)

    fun itemRevealProgress(contentProgress: Float, index: Int, count: Int): Float {
        if (count <= 1) return contentProgress
        val start = ItemRevealSpread * index / (count - 1).toFloat()
        return ((contentProgress - start) / (1f - ItemRevealSpread)).coerceIn(0f, 1f)
    }

    /** Distance a row still has to travel along the growth axis before settling. */
    fun itemTravelPx(reveal: Float): Float = (1f - reveal.coerceIn(0f, 1f)) * ItemTravelDp

    /** Text stays at its final scale throughout the reveal. */
    fun itemScale(reveal: Float): Float = lerp(ItemStartScale, 1f, reveal.coerceIn(0f, 1f))

    /** The whole content group un-blurs as it surfaces. */
    fun contentBlurDp(contentProgress: Float): Float =
        (1f - contentProgress.coerceIn(0f, 1f)) * ContentBlurDp
}

@Stable
class LiquidGlassMenuHostState {
    var activeMenu by mutableStateOf<LiquidGlassMenuSpec?>(null)
        private set
    internal var displayedMenu by mutableStateOf<LiquidGlassMenuSpec?>(null)
    internal var drawingSourceId by mutableStateOf<Any?>(null)
    internal var measured by mutableStateOf(false)
    private val anchors = mutableMapOf<Any, LiquidGlassMenuAnchor>()

    internal fun register(anchor: LiquidGlassMenuAnchor) { anchors[anchor.id] = anchor }

    internal fun unregister(anchor: LiquidGlassMenuAnchor) {
        if (anchors[anchor.id] !== anchor) return
        anchors.remove(anchor.id)
        if (activeMenu?.sourceId == anchor.id) dismiss()
        if (displayedMenu?.sourceId == anchor.id) {
            displayedMenu = null
            drawingSourceId = null
        }
    }

    internal fun source(spec: LiquidGlassMenuSpec): LiquidGlassMenuAnchor? = anchors[spec.sourceId]
    internal fun ownsDrawing(id: Any): Boolean = drawingSourceId == id

    internal fun sameSource(first: LiquidGlassMenuSpec?, second: LiquidGlassMenuSpec): Boolean =
        if (first?.sourceId != null || second.sourceId != null) first?.sourceId == second.sourceId
        else LiquidGlassMenuMorph.sameAnchor(first?.anchorBounds, second.anchorBounds)

    private fun resolve(spec: LiquidGlassMenuSpec): LiquidGlassMenuSpec {
        if (spec.sourceId != null) return spec
        val anchor = anchors.values.firstOrNull {
            LiquidGlassMenuMorph.sameAnchor(it.rootBounds, spec.anchorBounds)
        }
        return if (anchor == null) spec else spec.copy(sourceId = anchor.id)
    }

    fun show(spec: LiquidGlassMenuSpec) {
        val previous = activeMenu
        activeMenu = resolve(spec)
        if (previous !== spec) previous?.onDismiss?.invoke()
    }

    fun dismiss() {
        val previous = activeMenu
        activeMenu = null
        previous?.onDismiss?.invoke()
    }

    fun isActive(spec: LiquidGlassMenuSpec): Boolean = sameSource(activeMenu, resolve(spec))

    fun toggle(spec: LiquidGlassMenuSpec) {
        if (isActive(spec)) dismiss() else show(spec)
    }

    internal fun select(spec: LiquidGlassMenuSpec, action: () -> Unit): Boolean {
        if (!isActive(spec)) return false
        dismiss()
        action()
        return true
    }
}

val LocalLiquidGlassMenuHost = staticCompositionLocalOf<LiquidGlassMenuHostState?> { null }

private val MenuItemRowHeight = 44.dp
private val MenuContentPadding = 8.dp
private val MenuScreenMargin = 8.dp
private val MenuAnchorGap = 6.dp
private val MenuItemCornerRadius = 12.dp
private const val ReducedMotionFadeMillis = 100

/**
 * Back handling for the menu overlay.
 *
 * [OnBackPressedDispatcher] only reaches the most recently added enabled callback, and this host
 * wraps the page it overlays: every page level handler (navigation, folder/edit modes, bottom
 * sheets) registers after it, so a plain back handler would lose to them and the page would go
 * back while the menu stays on screen. Re-adding the callback whenever a menu opens puts it back
 * on top of the queue, so back always closes the open menu first and everything opened on top of
 * the menu still wins.
 */
@Composable
private fun MenuOverlayBackHandler(enabled: Boolean, onBack: () -> Unit) {
    val currentOnBack by rememberUpdatedState(onBack)
    val dispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(dispatcher, lifecycleOwner, enabled) {
        if (dispatcher == null || !enabled) return@DisposableEffect onDispose { }
        val callback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                currentOnBack()
            }
        }
        dispatcher.addCallback(lifecycleOwner, callback)
        onDispose { callback.remove() }
    }
}

@Composable
fun LiquidGlassMenuHost(
    modifier: Modifier = Modifier,
    backdrop: Backdrop? = LocalLiquidGlassBackdrop.current,
    content: @Composable BoxScope.() -> Unit
) {
    val hostState = remember { LiquidGlassMenuHostState() }
    val motionEnabled = LocalMotionEnabled.current
    val progress = remember { Animatable(0f, LiquidGlassMenuMorph.ProgressVisibilityThreshold) }
    // Animatable clears velocity when its coroutine is cancelled; retain the last frame
    // separately so a new open/close target can continue the interrupted spring.
    var animationVelocity by remember { mutableFloatStateOf(0f) }
    val fade = remember { Animatable(0f) }
    val activeMenu = hostState.activeMenu
    var hostWindowBounds by remember { mutableStateOf(Rect.Zero) }
    var hostRootOrigin by remember { mutableStateOf(Offset.Zero) }

    LaunchedEffect(activeMenu, motionEnabled) {
        suspend fun closeDisplayed() {
            val closing = hostState.displayedMenu ?: return
            val motion = if (closing.sourceId == null) LiquidGlassMenuMotion.Legacy else closing.motion
            if (motionEnabled) {
                progress.animateTo(0f, motion.closeSpec(), animationVelocity + motion.closeInitialVelocity) {
                    animationVelocity = velocity
                }
                animationVelocity = 0f
            } else {
                fade.animateTo(0f, tween(ReducedMotionFadeMillis))
            }
            hostState.displayedMenu = null
            hostState.drawingSourceId = null
        }
        val requested = activeMenu
        if (requested == null) {
            closeDisplayed()
            return@LaunchedEffect
        }
        val current = hostState.displayedMenu
        val resume = hostState.sameSource(current, requested) && current != null
        if (!resume) {
            closeDisplayed()
            progress.snapTo(0f)
            animationVelocity = 0f
            fade.snapTo(0f)
            hostState.measured = false
        }
        hostState.displayedMenu = requested
        snapshotFlow { hostState.measured }.first { it }
        if (hostState.displayedMenu !== requested) return@LaunchedEffect
        if (motionEnabled) {
            hostState.source(requested)?.takeIf { it.recorded }?.let {
                hostState.drawingSourceId = it.id
            }
            fade.snapTo(1f)
            val motion = if (requested.sourceId == null) LiquidGlassMenuMotion.Legacy else requested.motion
            progress.animateTo(1f, motion.openSpec(), animationVelocity + motion.openInitialVelocity) {
                animationVelocity = velocity
            }
            animationVelocity = 0f
        } else {
            hostState.drawingSourceId = null
            progress.snapTo(1f)
            animationVelocity = 0f
            fade.animateTo(1f, tween(ReducedMotionFadeMillis))
        }
    }

    MenuOverlayBackHandler(enabled = hostState.displayedMenu != null || activeMenu != null) {
        hostState.dismiss()
    }
    DisposableEffect(hostState) { onDispose { hostState.dismiss() } }

    ProvideLiquidGlassBackdrop(backdrop) {
        CompositionLocalProvider(LocalLiquidGlassMenuHost provides hostState) {
            BoxWithConstraints(
                modifier = modifier.onGloballyPositioned {
                    hostWindowBounds = it.boundsInWindow()
                    hostRootOrigin = it.boundsInRoot().topLeft
                }
            ) {
                content()
                val menu = hostState.displayedMenu
                if (menu != null) {
                    val density = LocalDensity.current
                    val view = LocalView.current
                    val insets = WindowInsets.safeDrawing
                    val safeTop = (insets.getTop(density) - hostWindowBounds.top).coerceAtLeast(0f)
                    val safeBottom = (insets.getBottom(density) - (view.height - hostWindowBounds.bottom)).coerceAtLeast(0f)
                    val source = hostState.source(menu)
                    val sourceRect = source?.let {
                        LiquidGlassMenuMorph.windowToHost(it.windowBounds, hostWindowBounds.topLeft)
                    } ?: menu.anchorBounds.translate(-hostRootOrigin)
                    val passThrough = menu.passThroughBounds?.invoke()?.let {
                        LiquidGlassMenuMorph.windowToHost(it, hostWindowBounds.topLeft)
                    }
                    val hostWidthPx = with(density) { maxWidth.toPx() }
                    val hostHeightPx = with(density) { maxHeight.toPx() }
                    Box(Modifier.fillMaxSize(), contentAlignment = AbsoluteAlignment.TopLeft) {
                        MenuDismissLayer(
                            widthPx = hostWidthPx,
                            heightPx = hostHeightPx,
                            passThrough = passThrough,
                            onDismiss = { position ->
                                if (hostState.activeMenu == null && sourceRect.contains(position)) {
                                    hostState.show(menu)
                                } else hostState.dismiss()
                            }
                        )
                        key(menu.sourceId ?: menu.anchorBounds) {
                            AnchoredLiquidGlassMenu(
                                spec = menu,
                                hostState = hostState,
                                progress = progress,
                                fade = fade,
                                source = source,
                                sourceRect = sourceRect,
                                hostWidthPx = hostWidthPx,
                                hostHeightPx = hostHeightPx,
                                safeTop = safeTop,
                                safeBottom = safeBottom,
                                motionEnabled = motionEnabled,
                                backdrop = backdrop
                            )
                        }
                        if (source?.kind == LiquidGlassMenuAnchorKind.Standalone) {
                            // Keep the trigger's end icon as a toggle without covering the
                            // first option's label when the source is a wide text selector.
                            val hitWidth = min(sourceRect.width, sourceRect.height)
                            val hitLeft = if (LocalLayoutDirection.current == LayoutDirection.Ltr) {
                                sourceRect.right - hitWidth
                            } else sourceRect.left
                            Box(
                                Modifier.absoluteOffset {
                                    IntOffset(hitLeft.roundToInt(), sourceRect.top.roundToInt())
                                }.size(
                                    with(density) { hitWidth.toDp() },
                                    with(density) { sourceRect.height.toDp() }
                                ).pointerInput(menu.sourceId) {
                                    detectTapGestures { hostState.toggle(menu) }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MenuDismissLayer(
    widthPx: Float,
    heightPx: Float,
    passThrough: Rect?,
    onDismiss: (Offset) -> Unit
) {
    val density = LocalDensity.current
    val hole = passThrough?.intersect(Rect(0f, 0f, widthPx, heightPx))
    val regions = if (hole == null || hole.isEmpty) listOf(Rect(0f, 0f, widthPx, heightPx)) else listOf(
        Rect(0f, 0f, widthPx, hole.top),
        Rect(0f, hole.bottom, widthPx, heightPx),
        Rect(0f, hole.top, hole.left, hole.bottom),
        Rect(hole.right, hole.top, widthPx, hole.bottom)
    )
    regions.filterNot { it.isEmpty }.forEach { rect ->
        Box(
            Modifier.absoluteOffset { IntOffset(rect.left.roundToInt(), rect.top.roundToInt()) }
                .size(with(density) { rect.width.toDp() }, with(density) { rect.height.toDp() })
                .pointerInput(onDismiss) { detectTapGestures { onDismiss(it + rect.topLeft) } }
        )
    }
}

@Composable
private fun AnchoredLiquidGlassMenu(
    spec: LiquidGlassMenuSpec,
    hostState: LiquidGlassMenuHostState,
    progress: Animatable<Float, AnimationVector1D>,
    fade: Animatable<Float, AnimationVector1D>,
    source: LiquidGlassMenuAnchor?,
    sourceRect: Rect,
    hostWidthPx: Float,
    hostHeightPx: Float,
    safeTop: Float,
    safeBottom: Float,
    motionEnabled: Boolean,
    backdrop: Backdrop?
) {
    val density = LocalDensity.current
    val isDark = LocalIsDarkTheme.current
    val isLiquidGlass = LocalAppTheme.current == "liquid_glass" && !spec.forceSolid
    val layoutDirection = LocalLayoutDirection.current
    val continuous = source != null
    val embedded = source?.kind == LiquidGlassMenuAnchorKind.Embedded
    val p = if (motionEnabled) progress.value else 1f
    val growth = LiquidGlassMenuMorph.growthProgress(p, continuous)
    val reveal = if (continuous) LiquidGlassMenuMorph.contentProgress(growth)
        else ((growth - 0.68f) / 0.32f).coerceIn(0f, 1f)
    val sourceAlpha = if (motionEnabled && hostState.ownsDrawing(spec.sourceId ?: Unit)) {
        LiquidGlassMenuMorph.sourceAlpha(p)
    } else 0f
    val margin = with(density) { MenuScreenMargin.toPx() }
    val gap = with(density) { MenuAnchorGap.toPx() }
    val availableHeight = (hostHeightPx - safeTop - safeBottom - margin * 2f).coerceAtLeast(1f)
    val widthPx = with(density) { spec.width.toPx() }
        .coerceIn(1f, (hostWidthPx - 2f * margin).coerceAtLeast(1f))
    val menuWidth = with(density) { widthPx.toDp() }
    val aboveHeight = (sourceRect.top - safeTop - margin - gap).coerceAtLeast(1f)
    val belowHeight = (hostHeightPx - safeBottom - sourceRect.bottom - margin - gap).coerceAtLeast(1f)
    val maxHeightPx = if (embedded) {
        if (spec.preferAbove) aboveHeight else maxOf(aboveHeight, belowHeight)
    } else availableHeight
    val rowCapPx = with(density) {
        (MenuItemRowHeight * spec.maxVisibleItems.coerceAtLeast(1) * density.fontScale.coerceAtLeast(1f) +
            MenuContentPadding * 2).toPx()
    }
    val maxContentHeight = with(density) { min(maxHeightPx, rowCapPx).toDp() }
    var measuredHeightPx by remember { mutableStateOf(0f) }
    val targetRect = LiquidGlassMenuMorph.targetRect(
        anchor = sourceRect.translate(Offset(0f, -safeTop)),
        widthPx = widthPx,
        heightPx = measuredHeightPx.coerceAtLeast(1f),
        hostWidthPx = hostWidthPx,
        hostHeightPx = hostHeightPx - safeTop - safeBottom,
        marginPx = margin,
        gapPx = gap,
        alignEnd = spec.alignEnd == (layoutDirection == LayoutDirection.Ltr),
        overlapAnchor = continuous && !embedded,
        preferAbove = spec.preferAbove
    ).translate(Offset(0f, safeTop))
    val rect = if (motionEnabled) LiquidGlassMenuMorph.morphRect(sourceRect, targetRect, p, continuous) else targetRect
    val sourceRadius = with(density) { (spec.anchorCornerRadius ?: source?.cornerRadius)?.toPx() }
        ?: LiquidGlassMenuMorph.capsuleCornerRadiusPx(sourceRect)
    val radiusPx = LiquidGlassMenuMorph.cornerRadiusPx(
        sourceRadius, with(density) { LiquidGlassMenuMorph.TargetCornerRadiusDp.dp.toPx() }, growth
    ).coerceIn(0f, min(rect.width, rect.height).coerceAtLeast(0f) / 2f)
    val shape = RoundedCornerShape(with(density) { radiusPx.toDp() })
    val color = spec.surfaceColor.takeOrElse { AppColors.CardBg }
    val backdropBase = spec.surfaceColor.takeOrElse { AppColors.WindowBg }.copy(alpha = 1f)
    // Partial page captures can have transparent regions, including beyond a list's bounds.
    // Complete the sampled surface before lens/blur so those regions cannot form a rectangle.
    val menuBackdrop = backdrop?.let {
        rememberBackdrop(it) { drawCapturedBackdrop ->
            drawRect(backdropBase)
            drawCapturedBackdrop()
        }
    }
    val textColor = spec.contentColor.takeOrElse { AppColors.TextPrimary }
    val enabled = hostState.sameSource(hostState.activeMenu, spec) && reveal > LiquidGlassMenuMorph.ItemClickableReveal
    val alpha = when {
        !hostState.measured -> 0f
        continuous && motionEnabled && !hostState.ownsDrawing(source.id) -> 0f
        !motionEnabled -> fade.value
        continuous -> 1f
        else -> LiquidGlassMenuMorph.containerAlpha(p)
    }
    val blur = if (motionEnabled) LiquidGlassMenuMorph.contentBlurDp(reveal).dp else 0.dp
    val alignment = LiquidGlassMenuMorph.nearAlignment(sourceRect, targetRect)

    Box(
        Modifier.absoluteOffset { IntOffset(rect.left.roundToInt(), rect.top.roundToInt()) }
            .size(with(density) { rect.width.coerceAtLeast(1f).toDp() }, with(density) { rect.height.coerceAtLeast(1f).toDp() })
            .graphicsLayer { this.alpha = alpha }
            .pointerInput(spec, rect) {
                detectTapGestures { position ->
                    if (hostState.activeMenu == null && sourceRect.contains(position + rect.topLeft)) {
                        hostState.show(spec)
                    } else hostState.dismiss()
                }
            }
    ) {
        LiquidGlassSurface(
            shape = shape,
            fallbackColor = color,
            backdrop = menuBackdrop,
            contentScrimColor = color.copy(alpha = if (isDark) 0.68f else 0.62f),
            forceFallback = spec.forceSolid,
            decorationModifier = if (isLiquidGlass) null else Modifier.shadow(8.dp, shape),
            blurBoost = if (motionEnabled) LiquidGlassMenuMorph.blurBoostDp(p).dp else 0.dp,
            modifier = Modifier.matchParentSize().graphicsLayer { this.alpha = 1f - sourceAlpha }
        ) {}
        if (source != null && sourceAlpha > 0f) {
            Box(
                Modifier.matchParentSize().graphicsLayer { this.alpha = sourceAlpha }
                    .clearAndSetSemantics {}
                    .drawWithContent {
                        val sourceScale = min(size.width / source.layer.size.width, size.height / source.layer.size.height).coerceAtMost(1f)
                        val x = if (LiquidGlassMenuMorph.nearEdgeBiasX(sourceRect, targetRect) > 0f) size.width - source.layer.size.width * sourceScale else 0f
                        val y = if (LiquidGlassMenuMorph.nearEdgeBiasY(sourceRect, targetRect) > 0f) size.height - source.layer.size.height * sourceScale else 0f
                        translate(x, y) { scale(sourceScale, sourceScale, Offset.Zero) { drawLayer(source.layer) } }
                    }
            )
        }
        Box(
            Modifier.matchParentSize().clip(shape),
            contentAlignment = alignment
        ) {
            Column(
                Modifier.requiredWidth(menuWidth)
                    .wrapContentHeight(unbounded = true, align = Alignment.Top)
                    .heightIn(max = maxContentHeight)
                    .onSizeChanged {
                        measuredHeightPx = it.height.toFloat()
                        hostState.measured = true
                    }
                    .then(if (Build.VERSION.SDK_INT >= 31 && blur > 0.1.dp) Modifier.blur(blur) else Modifier)
                    .graphicsLayer { this.alpha = reveal }
                    .then(if (!enabled) Modifier.clearAndSetSemantics {} else Modifier)
                    .verticalScroll(rememberScrollState())
                    .padding(MenuContentPadding)
            ) {
                val customContent = spec.content
                if (customContent != null) {
                    customContent(enabled) { action -> hostState.select(spec, action) }
                } else {
                    spec.items.forEach { item ->
                        val itemColor = if (item.destructive || item.selected) AppColors.Accent else textColor
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = MenuItemRowHeight)
                                .clip(RoundedCornerShape(MenuItemCornerRadius))
                                .clickable(
                                    enabled = enabled,
                                    indication = null,
                                    interactionSource = remember { MutableInteractionSource() }
                                ) {
                                    hostState.select(spec, item.onClick)
                                }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            item.icon?.let {
                                Icon(it, null, tint = itemColor, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                            }
                            Text(
                                item.label, color = itemColor, fontSize = 13.sp,
                                modifier = Modifier.weight(1f),
                                maxLines = 2, overflow = TextOverflow.Ellipsis,
                                textAlign = if (item.icon == null) androidx.compose.ui.text.style.TextAlign.Center
                                    else androidx.compose.ui.text.style.TextAlign.Start
                            )
                        }
                    }
                }
            }
        }
    }
}
