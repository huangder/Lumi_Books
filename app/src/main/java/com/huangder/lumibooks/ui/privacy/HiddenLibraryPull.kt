package com.huangder.lumibooks.ui.privacy

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.huangder.lumibooks.ui.animation.OverscrollBounceState
import com.huangder.lumibooks.ui.animation.VisualMotion.spring
import com.huangder.lumibooks.ui.animation.VisualMotion.tween
import com.huangder.lumibooks.ui.theme.AppColors
import com.huangder.lumibooks.ui.theme.LocalEInkMode
import com.huangder.lumibooks.ui.theme.LocalMotionEnabled
import kotlinx.coroutines.launch

/** Content offset where the lock starts to show, so small rubber-band pulls stay quiet. */
internal const val HIDDEN_PULL_REVEAL_DP = 24f
/** Content offset that arms the gesture; with the 0.4 damping that is about 250dp of finger travel. */
internal const val HIDDEN_PULL_THRESHOLD_DP = 100f
/** Once armed, the pull must drop this far below the threshold to disarm. */
internal const val HIDDEN_PULL_DISARM_GAP_DP = 8f
private val IndicatorSize = 44.dp

internal fun hiddenPullProgress(offsetPx: Float, revealPx: Float, thresholdPx: Float): Float {
    if (offsetPx <= revealPx || thresholdPx <= revealPx) return 0f
    return ((offsetPx - revealPx) / (thresholdPx - revealPx)).coerceIn(0f, 1f)
}

internal fun hiddenPullArmed(offsetPx: Float, wasArmed: Boolean, thresholdPx: Float, disarmGapPx: Float): Boolean =
    if (wasArmed) offsetPx > thresholdPx - disarmGapPx else offsetPx >= thresholdPx

/** Lock center in the gap that opens above the content, kept clear of the status bar. */
internal fun hiddenPullLockCenterY(gapTopPx: Float, restingGapPx: Float, offsetPx: Float, minCenterPx: Float): Float =
    maxOf(gapTopPx + (restingGapPx + offsetPx) / 2f, minCenterPx)

@Stable
internal class HiddenLibraryPullState {
    var armed by mutableStateOf(false)
        internal set
    /** A reverse drag cancels this release, even if the rubber band is still past the threshold. */
    private var reversedDuringPull = false
    private var pullStarted = false
    /** Suppresses the disarm tick while the content springs back after a release. */
    internal var settling = false
    internal var centerX = 0f
    internal var baseY = 0f
    internal var gapTopPx = 0f
    internal var restingGapPx = 0f
    internal var minCenterPx = 0f
    internal var thresholdPx = Float.MAX_VALUE
    internal var disarmGapPx = 0f
    internal var revealPx = 0f

    /** Records a deliberate upward move after this pull started. */
    fun onDrag(offsetPx: Float, deltaPx: Float) {
        if (offsetPx <= 0f) {
            pullStarted = false
            reversedDuringPull = false
            return
        }
        if (deltaPx > 0f) {
            pullStarted = true
        } else if (deltaPx < -0.5f && pullStarted) {
            reversedDuringPull = true
        }
    }

    /** Clears a partial pull when the bookshelf temporarily cannot accept this gesture. */
    fun cancel() {
        armed = false
        reversedDuringPull = false
        pullStarted = false
        settling = false
    }

    /** Called with the offset at release; true means the passcode panel should open. */
    fun release(offsetPx: Float): Boolean {
        val open = !reversedDuringPull && hiddenPullArmed(offsetPx, armed, thresholdPx, disarmGapPx)
        armed = false
        reversedDuringPull = false
        pullStarted = false
        // Only a visible pull springs back through the threshold; a plain fling has nothing to settle.
        settling = offsetPx > revealPx
        return open
    }

    /** Lock position in root coordinates for the given pull offset; the gate flies from here. */
    fun anchorAt(offsetPx: Float): Offset =
        Offset(centerX, baseY + hiddenPullLockCenterY(gapTopPx, restingGapPx, offsetPx, minCenterPx))
}

@Composable
internal fun rememberHiddenLibraryPullState(): HiddenLibraryPullState = remember { HiddenLibraryPullState() }

/**
 * Lock revealed by pulling the bookshelf down past its top. Place it top-center over the
 * area that moves with the pull; [gapTop] is where the opening gap starts in that area and
 * [restingGap] the space already there before pulling.
 */
@Composable
internal fun HiddenLibraryPullIndicator(
    bounceState: OverscrollBounceState,
    pullState: HiddenLibraryPullState,
    enabled: Boolean,
    gapTop: Dp,
    restingGap: Dp,
    minCenter: Dp,
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    val motion = LocalMotionEnabled.current && !LocalEInkMode.current
    val density = LocalDensity.current
    val revealPx = with(density) { HIDDEN_PULL_REVEAL_DP.dp.toPx() }
    with(density) {
        pullState.gapTopPx = gapTop.toPx()
        pullState.restingGapPx = restingGap.toPx()
        pullState.minCenterPx = minCenter.toPx()
        pullState.thresholdPx = HIDDEN_PULL_THRESHOLD_DP.dp.toPx()
        pullState.disarmGapPx = HIDDEN_PULL_DISARM_GAP_DP.dp.toPx()
        pullState.revealPx = revealPx
    }
    val pop = remember { Animatable(1f) }
    val jiggle = remember { Animatable(0f) }
    LaunchedEffect(enabled) {
        if (!enabled) {
            pullState.cancel()
            return@LaunchedEffect
        }
        snapshotFlow { bounceState.offset }.collect { offset ->
            if (pullState.settling) {
                if (offset <= revealPx) pullState.settling = false
                return@collect
            }
            val next = hiddenPullArmed(offset, pullState.armed, pullState.thresholdPx, pullState.disarmGapPx)
            if (next == pullState.armed) return@collect
            pullState.armed = next
            if (next) LockHaptics.arm(view) else LockHaptics.disarm(view)
            if (next && motion) {
                launch {
                    pop.animateTo(1.14f, tween(90))
                    pop.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = 600f))
                }
                launch {
                    jiggle.snapTo(0f)
                    jiggle.animateTo(1f, tween(380, easing = LinearEasing))
                }
            }
        }
    }
    val armedProgress = animateFloatAsState(
        targetValue = if (pullState.armed) 1f else 0f,
        animationSpec = if (motion) spring(dampingRatio = 0.8f, stiffness = 520f) else tween(100),
        label = "hiddenPullArmed"
    )
    if (!enabled) return
    val idle = AppColors.TextSecondary
    val armedColor = AppColors.Accent
    val track = AppColors.Divider
    Box(
        modifier
            .size(IndicatorSize)
            .onGloballyPositioned { coordinates ->
                val position = coordinates.positionInRoot()
                pullState.centerX = position.x + coordinates.size.width / 2f
                pullState.baseY = position.y
            }
            .graphicsLayer {
                val offset = bounceState.offset
                val p = hiddenPullProgress(offset, revealPx, pullState.thresholdPx)
                alpha = p
                translationY = hiddenPullLockCenterY(
                    pullState.gapTopPx, pullState.restingGapPx, offset, pullState.minCenterPx
                ) - size.height / 2f
                val scale = if (motion) (0.7f + 0.3f * p) * pop.value else 1f
                scaleX = scale
                scaleY = scale
                rotationZ = gateShakeOffset(jiggle.value, 8f)
            }
    ) {
        // Ring fills with the pull; at the threshold ring and lock turn to the accent together.
        androidx.compose.foundation.Canvas(Modifier.size(IndicatorSize)) {
            drawPullRing(
                progress = hiddenPullProgress(bounceState.offset, revealPx, pullState.thresholdPx),
                color = lerp(idle, armedColor, armedProgress.value),
                track = track
            )
        }
        LockIcon(
            iconSize = IndicatorLockSize,
            tint = { lerp(idle, armedColor, armedProgress.value) },
            modifier = Modifier.size(IndicatorSize)
        )
    }
}

/** Lock inside the 44dp ring; the gate's flight starts at exactly this size. */
internal val IndicatorLockSize = 22.dp
