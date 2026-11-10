package com.huangder.lumibooks.ui.reader

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.huangder.lumibooks.ui.theme.LocalEInkMode
import com.huangder.lumibooks.ui.theme.LocalMotionEnabled

/** Keeps both slots mounted throughout the gesture so resizing cannot replace the pointer owner. */
@Composable
internal fun ReaderExpandingSliderRow(
    activeSide: Int?,
    animate: Boolean,
    modifier: Modifier = Modifier,
    first: @Composable (compressed: Boolean) -> Unit,
    second: @Composable (compressed: Boolean) -> Unit
) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val available = (maxWidth - 16.dp).coerceAtLeast(1.dp)
        val minimum = 72.dp.coerceAtMost(available / 2)
        val expanded = (available * 0.75f).coerceAtMost(available - minimum)
        val target = when (activeSide) {
            0 -> expanded / available
            1 -> (available - expanded) / available
            else -> 0.5f
        }
        val fraction by animateFloatAsState(
            targetValue = target,
            animationSpec = if (animate && LocalMotionEnabled.current && !LocalEInkMode.current)
                tween(180, easing = FastOutSlowInEasing) else snap(),
            label = "reader-slider-width"
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(Modifier.weight(fraction)) { first(fraction < 0.45f) }
            Column(Modifier.weight(1f - fraction)) { second(fraction > 0.55f) }
        }
    }
}
