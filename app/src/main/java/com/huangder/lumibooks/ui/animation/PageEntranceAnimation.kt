package com.huangder.lumibooks.ui.animation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

const val PAGE_ENTRANCE_COOLDOWN_MILLIS = 10_000L
const val PAGE_ENTRANCE_PLAYBACK_MILLIS = 900L
class PageEntranceTracker(
    private val cooldownMillis: Long = PAGE_ENTRANCE_COOLDOWN_MILLIS
) {
    private val lastPlayedAt = mutableMapOf<String, Long>()
    private val evaluatedEntries = mutableSetOf<Pair<String, String>>()

    fun shouldPlay(pageKey: String, nowMillis: Long): Boolean {
        val previous = lastPlayedAt[pageKey]
        if (previous != null && nowMillis - previous < cooldownMillis) return false
        lastPlayedAt[pageKey] = nowMillis
        return true
    }

    /** A restored back-stack entry must not replay while it is being revealed by predictive back. */
    fun shouldPlay(pageKey: String, entryKey: String, nowMillis: Long): Boolean {
        if (!evaluatedEntries.add(pageKey to entryKey)) return false
        return shouldPlay(pageKey, nowMillis)
    }
}

@Composable
fun PageEntranceItem(
    play: Boolean,
    index: Int,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val startOffset = with(LocalDensity.current) { 18.dp.toPx() }
    val alpha = remember(play) { Animatable(if (play) 0f else 1f) }
    val offsetY = remember(play, startOffset) { Animatable(if (play) startOffset else 0f) }
    val scale = remember(play) { Animatable(if (play) 0.985f else 1f) }

    LaunchedEffect(play) {
        if (!play) return@LaunchedEffect
        delay((index * 45L).coerceAtMost(225L))
        coroutineScope {
            launch { alpha.animateTo(1f, tween(360, easing = AppEasing.Smooth)) }
            launch { offsetY.animateTo(0f, tween(440, easing = AppEasing.Decelerate)) }
            launch { scale.animateTo(1f, tween(440, easing = AppEasing.Smooth)) }
        }
    }

    Box(
        modifier = modifier.graphicsLayer {
            this.alpha = alpha.value
            translationY = offsetY.value
            scaleX = scale.value
            scaleY = scale.value
        }
    ) {
        content()
    }
}
