package com.huangder.lumibooks.ui.privacy

import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin

/**
 * Presence of the passcode gate, shared with the host so it can blur its content and hide the
 * tab bar in step with the gate. Read [presence] in draw or graphicsLayer blocks where possible.
 */
@Stable
class GateMotionState internal constructor() {
    internal val progress = Animatable(0f)
    val presence: Float get() = progress.value
}

@Composable
fun rememberGateMotionState(): GateMotionState = remember { GateMotionState() }

/**
 * Staggered entrance of the gate's parts within one 300ms timeline. Each part takes 60% of the
 * timeline; later parts start 8% later. Running the timeline backwards makes later parts leave first.
 */
internal fun gateStagger(progress: Float, index: Int): Float =
    ((progress - index * 0.08f) / 0.6f).coerceIn(0f, 1f)

/** Three decaying horizontal swings for a rejected passcode; zero at both ends. */
internal fun gateShakeOffset(progress: Float, amplitude: Float): Float =
    if (progress <= 0f || progress >= 1f) 0f
    else sin(progress * PI * 6.0).toFloat() * amplitude * (1f - progress)

/** Key diameter that fits the keypad, header and dots on short phones, capped at 78dp. */
internal fun gateKeySize(width: Dp, height: Dp, landscape: Boolean): Dp {
    // Portrait: header (~196dp) + gap + footer leave the rest to four key rows and their gaps.
    val byHeight = if (landscape) (height - 72.dp) / 4.6f else (height - 288.dp) / 4.6f
    val availableWidth = if (landscape) width / 2f - 24.dp else width - 48.dp
    val byWidth = availableWidth / 3.64f
    return minOf(78.dp, minOf(byHeight, byWidth)).coerceAtLeast(52.dp)
}

/** Lock size in the gate; the slot around it leaves room for the opening bounce. */
internal val GateLockSize = 44.dp
internal val GateGlyphSlot = 56.dp

/** Pull-indicator lock (22dp) relative to the gate lock, so the flight starts at the size the user saw. */
internal const val GATE_GLYPH_START_SCALE = 0.5f

/** Height kept for「忘记密码？」at the bottom, shown or not, so nothing above it moves. */
internal val GateFooterHeight = 56.dp

internal class GlyphSlot {
    var center: Offset = Offset.Unspecified
}
