package com.huangder.lumibooks.ui.reader

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.huangder.lumibooks.R
import com.huangder.lumibooks.tts.TtsPauseDuration
import com.huangder.lumibooks.ui.components.LiquidGlassMenuItem

@Composable
internal fun ttsPauseDurationMenu(
    selected: TtsPauseDuration,
    onSelect: (TtsPauseDuration) -> Unit
): LiquidGlassMenuItem {
    val labels = mapOf(
        TtsPauseDuration.STANDARD to stringResource(R.string.tts_pause_standard),
        TtsPauseDuration.LONG to stringResource(R.string.tts_pause_long),
        TtsPauseDuration.SHORT to stringResource(R.string.tts_pause_short),
        TtsPauseDuration.VERY_SHORT to stringResource(R.string.tts_pause_very_short)
    )
    return LiquidGlassMenuItem(
        label = stringResource(R.string.tts_pause_duration),
        subtitle = labels.getValue(selected),
        submenuItems = labels.map { (duration, label) ->
            LiquidGlassMenuItem(label, selected = selected == duration, onClick = { onSelect(duration) })
        }
    )
}
