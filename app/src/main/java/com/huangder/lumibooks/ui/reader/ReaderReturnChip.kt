package com.huangder.lumibooks.ui.reader

import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.huangder.lumibooks.ui.icons.AppIcons
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal const val READER_RETURN_HINT_TIMEOUT_MS = 15_000L

/** The outline is 24dp tall; the surrounding hit area stays at least 48dp. */
@Composable
internal fun ReaderReturnChip(
    pageLabel: String?,
    side: ReaderReturnSide,
    headphones: Boolean,
    paperColor: Color,
    contentColor: Color,
    eInkMode: Boolean,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    entryKey: Any? = headphones
) {
    key(entryKey) {
        val paper = if (eInkMode) Color.White else paperColor.copy(alpha = 1f)
        val ink = contentColor
        val opacity = remember { Animatable(1f) }
        var dismissing by remember { mutableStateOf(false) }
        val scope = rememberCoroutineScope()
        Box(modifier = modifier
            .heightIn(min = 48.dp).widthIn(min = 48.dp)
            .graphicsLayer { alpha = opacity.value }
            .semantics(mergeDescendants = true) { contentDescription = description }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = !dismissing,
                role = Role.Button,
                onClick = {
                    if (!dismissing) {
                        dismissing = true
                        scope.launch {
                            // Finish the fade before navigation removes this corner entry.
                            opacity.animateTo(0f, tween(160))
                            onClick()
                        }
                    }
                }
            ),
            contentAlignment = if (side == ReaderReturnSide.LEFT) Alignment.CenterStart else Alignment.CenterEnd
        ) {
            Row(Modifier.height(24.dp)
                .background(paper.copy(alpha = if (eInkMode) 1f else 0.88f), RoundedCornerShape(12.dp))
                .border(0.5.dp, ink.copy(alpha = if (eInkMode) 0.35f else 0.18f), RoundedCornerShape(12.dp))
                .padding(horizontal = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (side == ReaderReturnSide.RIGHT && pageLabel != null) {
                    Text(pageLabel, color = ink, fontSize = 12.sp, modifier = Modifier.padding(start = 3.dp))
                }
                Box(Modifier.size(18.dp).background(ink, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(if (headphones) AppIcons.Headphones else AppIcons.ArrowLeft,
                        contentDescription = null, tint = paper,
                        modifier = Modifier.size(13.dp).graphicsLayer {
                            if (side == ReaderReturnSide.RIGHT) scaleX = -1f
                        })
                }
                if (side == ReaderReturnSide.LEFT && pageLabel != null) {
                    Text(pageLabel, color = ink, fontSize = 12.sp, modifier = Modifier.padding(end = 3.dp))
                }
            }
        }
    }
}

/** Menus pause the visible-time budget without restarting it. */
@Composable
internal fun ListeningResumeHintExpiry(target: Any?, visible: Boolean, onDismiss: () -> Unit,
    elapsedMillis: () -> Long = SystemClock::elapsedRealtime) {
    var remaining by remember(target) { mutableLongStateOf(READER_RETURN_HINT_TIMEOUT_MS) }
    val dismiss by rememberUpdatedState(onDismiss)
    LaunchedEffect(target, visible) {
        if (target == null || !visible) return@LaunchedEffect
        val start = elapsedMillis()
        try {
            delay(remaining.coerceAtLeast(0L))
            dismiss()
        } finally {
            remaining = (remaining - (elapsedMillis() - start)).coerceAtLeast(0L)
        }
    }
}
