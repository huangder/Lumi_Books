package com.huangder.lumibooks.ui.privacy

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.huangder.lumibooks.R
import com.huangder.lumibooks.ui.animation.VisualMotion.spring
import com.huangder.lumibooks.ui.animation.VisualMotion.tween
import com.huangder.lumibooks.ui.theme.AppColors
import com.huangder.lumibooks.ui.theme.LocalEInkMode
import com.huangder.lumibooks.ui.theme.LocalIsDarkTheme
import com.huangder.lumibooks.ui.theme.LocalMotionEnabled
import com.huangder.lumibooks.ui.theme.LocalUseMaterial3Theme

internal enum class DotTone { Normal, Error, Success }

/** Error tint of the lock flows, matching the destructive red used elsewhere. */
internal val PasscodeErrorColor: Color
    @Composable get() = when {
        LocalEInkMode.current -> Color.Black
        LocalUseMaterial3Theme.current -> MaterialTheme.colorScheme.error
        LocalIsDarkTheme.current -> Color(0xFFFF6B6B)
        else -> Color(0xFFD92D3A)
    }

@Composable
internal fun PasscodeDots(filled: Int, length: Int, tone: DotTone, modifier: Modifier = Modifier) {
    val description = stringResource(R.string.passcode_digits_entered, filled, length)
    val ink = when (tone) {
        DotTone.Normal -> AppColors.TextPrimary
        DotTone.Error -> PasscodeErrorColor
        DotTone.Success -> AppColors.Accent
    }
    Row(
        modifier = modifier.semantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(length) { index -> PasscodeDot(filled = index < filled, color = ink) }
    }
}

@Composable
private fun PasscodeDot(filled: Boolean, color: Color) {
    val motion = LocalMotionEnabled.current && !LocalEInkMode.current
    // An under-damped spring lets a newly filled dot pop slightly past full size.
    val progress = animateFloatAsState(
        targetValue = if (filled) 1f else 0f,
        animationSpec = when {
            !motion -> tween(0)
            filled -> spring(dampingRatio = 0.45f, stiffness = 900f)
            else -> tween(140)
        },
        label = "passcodeDot"
    )
    Canvas(Modifier.size(16.dp)) {
        val p = progress.value
        val base = 6.dp.toPx()
        drawCircle(color, base - 0.75.dp.toPx(), style = Stroke(1.5.dp.toPx()))
        val fill = p.coerceIn(0f, 1f)
        if (fill > 0f) {
            drawCircle(color.copy(alpha = color.alpha * fill), base * (1f + 0.35f * (p - 1f).coerceAtLeast(0f)))
        }
    }
}
