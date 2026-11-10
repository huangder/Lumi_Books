package com.huangder.lumibooks.ui.animation

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import com.huangder.lumibooks.ui.animation.VisualMotion.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import com.huangder.lumibooks.ui.theme.AppRoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.huangder.lumibooks.ui.theme.LocalMotionEnabled
import com.huangder.lumibooks.ui.theme.LocalPageVisible
import kotlinx.coroutines.flow.collectLatest

/**
 * 按钮按压效果（缩小 0.95 + 透明度降低）
 */
fun Modifier.lumiPressable(
    interactionSource: MutableInteractionSource? = null,
    pressedScale: Float = 0.97f,
    pressedAlpha: Float = 0.88f
): Modifier = composed {
    val motionEnabled = LocalMotionEnabled.current
    val resolvedInteractionSource = interactionSource ?: remember { MutableInteractionSource() }
    var pressed by remember { mutableStateOf(false) }
    LaunchedEffect(resolvedInteractionSource) {
        resolvedInteractionSource.interactions.collectLatest { interaction ->
            when (interaction) {
                is PressInteraction.Press -> pressed = true
                is PressInteraction.Release,
                is PressInteraction.Cancel -> pressed = false
            }
        }
    }
    val scale by animateFloatAsState(
        targetValue = if (motionEnabled && pressed) pressedScale else 1f,
        animationSpec = tween(LumiMotion.PressMillis, easing = AppEasing.Standard),
        label = "lumiPressableScale"
    )
    val alpha by animateFloatAsState(
        targetValue = if (pressed) pressedAlpha else 1f,
        animationSpec = tween(LumiMotion.PressMillis),
        label = "lumiPressableAlpha"
    )

    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
            this.alpha = alpha
        }
}

fun Modifier.pressEffect(): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    this
        .lumiPressable(
            interactionSource,
            pressedScale = 0.95f,
            pressedAlpha = 0.8f
        )
        .pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val press = PressInteraction.Press(down.position)
                    interactionSource.tryEmit(press)
                    val up = waitForUpOrCancellation()
                    interactionSource.tryEmit(
                        if (up != null) PressInteraction.Release(press)
                        else PressInteraction.Cancel(press)
                    )
                }
            }
        }
}

/**
 * 卡片按下效果（缩小 0.96）
 */
fun Modifier.cardPressEffect(): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    this
        .lumiPressable(
            interactionSource,
            pressedScale = 0.96f,
            pressedAlpha = 1f
        )
        .pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val press = PressInteraction.Press(down.position)
                    interactionSource.tryEmit(press)
                    val up = waitForUpOrCancellation()
                    interactionSource.tryEmit(
                        if (up != null) PressInteraction.Release(press)
                        else PressInteraction.Cancel(press)
                    )
                }
            }
        }
}

private val ShimmerColors = listOf(Color(0xFFE0E0E0), Color(0xFFF5F5F5), Color(0xFFE0E0E0))
private const val SHIMMER_START_X = -300f
private const val SHIMMER_END_X = 1000f
private const val SHIMMER_BAND = 300f

private fun shimmerGradient(translateX: Float): Brush = Brush.linearGradient(
    colors = ShimmerColors,
    start = Offset(translateX, 0f),
    end = Offset(translateX + SHIMMER_BAND, 0f)
)

@Composable
private fun shimmerPosition(): androidx.compose.runtime.State<Float> =
    rememberInfiniteTransition(label = "shimmer").animateFloat(
        initialValue = SHIMMER_START_X,
        targetValue = SHIMMER_END_X,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerX"
    )

/**
 * 骨架屏微光扫过效果
 */
@Composable
fun shimmerBrush(): Brush {
    val translateX by shimmerPosition()
    return shimmerGradient(translateX)
}

/**
 * 骨架屏占位块
 *
 * The sweep is read while drawing, so it repaints the block instead of recomposing it on every
 * frame. Main tabs stay composed while hidden; a skeleton left on a hidden tab (Statistics
 * before it first loads) holds still instead of driving a frame on every vsync indefinitely.
 */
@Composable
fun ShimmerBox(
    modifier: Modifier = Modifier
) {
    val position = if (LocalPageVisible.current) shimmerPosition() else null
    Box(
        modifier = modifier
            .clip(AppRoundedCornerShape(8.dp))
            .drawBehind { drawRect(shimmerGradient(position?.value ?: SHIMMER_START_X)) }
    )
}
