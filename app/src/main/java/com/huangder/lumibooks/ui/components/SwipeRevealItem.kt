package com.huangder.lumibooks.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.CircleShape
import com.huangder.lumibooks.R
import com.huangder.lumibooks.ui.icons.AppIcons
import com.huangder.lumibooks.ui.theme.AppColors
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.launch
import kotlin.math.exp

/** A card that reveals edit and delete actions after a left swipe. */
@Composable
fun SwipeRevealItem(
    onEdit: (() -> Unit)? = null,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    resetRevealedKey: Int = 0,
    onRevealedChanged: (Boolean) -> Unit = {},
    onEditTags: (() -> Unit)? = null,
    onShare: (() -> Unit)? = null,
    content: @Composable RowScope.() -> Unit
) {
    val density = LocalDensity.current
    // Leave a small breathing space between the card and the revealed actions.
    val actionCount = 1 + (if (onEdit != null) 1 else 0) + (if (onEditTags != null) 1 else 0) + (if (onShare != null) 1 else 0)
    val revealPx = with(density) { (16 + actionCount * 52).dp.toPx() }
    val deletePx = with(density) { 500.dp.toPx() }
    val scope = rememberCoroutineScope()
    val offset = remember { Animatable(0f) }
    var rawOffset by remember { mutableFloatStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }
    var revealed by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }

    LaunchedEffect(resetRevealedKey) {
        if (resetRevealedKey > 0 && revealed) {
            revealed = false
            offset.animateTo(0f, spring(dampingRatio = 0.6f, stiffness = 300f))
            rawOffset = 0f
        }
    }
    LaunchedEffect(revealed) { onRevealedChanged(revealed) }

    val displayOffset = if (dragging) rawOffset else offset.value
    val progress = (-displayOffset / revealPx).coerceAtLeast(0f)
    fun damp(excess: Float): Float {
        if (excess == 0f) return 0f
        val d = density.density
        return 40f * d * (1f - exp(-kotlin.math.abs(excess) / (80f * d))) * if (excess > 0) 1f else -1f
    }

    Box(modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .matchParentSize()
                .padding(end = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (onShare != null) {
                RevealAction(
                    icon = AppIcons.ShareNetwork,
                    description = stringResource(R.string.excerpt_share),
                    color = Color(0xFF398B78),
                    progress = progress.coerceIn(0f, 1f),
                    enabled = revealed && !deleting,
                    onClick = {
                        revealed = false
                        scope.launch { offset.animateTo(0f, spring(dampingRatio = 0.6f, stiffness = 300f)) }
                        onShare()
                    }
                )
            }
            if (onEditTags != null) {
                RevealAction(
                    icon = AppIcons.Tag,
                    description = stringResource(R.string.annotation_tags),
                    color = AppColors.Accent,
                    progress = progress.coerceIn(0f, 1f),
                    enabled = revealed && !deleting,
                    onClick = {
                        revealed = false
                        scope.launch { offset.animateTo(0f, spring(dampingRatio = 0.6f, stiffness = 300f)) }
                        onEditTags()
                    }
                )
            }
            if (onEdit != null) {
                RevealAction(
                    icon = AppIcons.PencilSimple,
                    description = stringResource(R.string.bookmark_remark_edit),
                    color = Color(0xFF4F7DFF),
                    progress = progress.coerceIn(0f, 1f),
                    enabled = revealed && !deleting,
                    onClick = {
                        revealed = false
                        scope.launch { offset.animateTo(0f, spring(dampingRatio = 0.6f, stiffness = 300f)) }
                        onEdit()
                    }
                )
            }
            RevealAction(
                icon = AppIcons.Trash,
                description = stringResource(R.string.delete),
                color = Color(0xFFE53935),
                progress = progress.coerceIn(0f, 1f),
                enabled = revealed && !deleting,
                onClick = {
                    deleting = true
                    scope.launch {
                        offset.animateTo(-deletePx, tween(250, easing = FastOutSlowInEasing))
                        onDelete()
                    }
                }
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(displayOffset.toInt(), 0) }
                .graphicsLayer {
                    if (deleting) alpha = 1f - (-displayOffset / deletePx).coerceIn(0f, 1f)
                    if (progress > 1f) scaleX = 1f - (progress - 1f) * 0.01f
                }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragStart = {
                            rawOffset = offset.value
                            dragging = true
                        },
                        onDragEnd = {
                            dragging = false
                            scope.launch {
                                offset.snapTo(rawOffset)
                                if (revealed) {
                                    if (-rawOffset < revealPx * 0.3f) {
                                        revealed = false
                                        offset.animateTo(0f, spring(dampingRatio = 0.6f, stiffness = 300f))
                                    } else {
                                        offset.animateTo(-revealPx, spring(dampingRatio = 0.6f, stiffness = 300f))
                                    }
                                } else if (-rawOffset > revealPx * 0.4f) {
                                    revealed = true
                                    offset.animateTo(-revealPx, spring(dampingRatio = 0.6f, stiffness = 300f))
                                } else {
                                    offset.animateTo(0f, spring(dampingRatio = 0.6f, stiffness = 300f))
                                }
                                rawOffset = 0f
                            }
                        },
                        onDragCancel = {
                            dragging = false
                            scope.launch {
                                offset.animateTo(if (revealed) -revealPx else 0f, spring(dampingRatio = 0.6f, stiffness = 300f))
                                rawOffset = 0f
                            }
                        },
                        onHorizontalDrag = { _, amount ->
                            val next = rawOffset + amount
                            rawOffset = when {
                                next < -revealPx -> -revealPx - damp((-next) - revealPx)
                                next > 0f -> damp(next)
                                else -> next
                            }
                        }
                    )
                }
                .clickable(
                    enabled = !revealed && onClick != null,
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = { onClick?.invoke() }
                ),
            content = content
        )
    }
}

@Composable
private fun RevealAction(
    icon: ImageVector,
    description: String,
    color: Color,
    progress: Float,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .graphicsLayer {
                alpha = progress
                translationX = (1f - progress) * 24.dp.toPx()
            }
            .clip(CircleShape)
            .background(color)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, description, tint = Color.White, modifier = Modifier.size(20.dp))
    }
}
