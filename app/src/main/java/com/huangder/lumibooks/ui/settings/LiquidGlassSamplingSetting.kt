package com.huangder.lumibooks.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.huangder.lumibooks.R
import com.huangder.lumibooks.domain.model.LiquidGlassSamplingQuality

@Composable
internal fun LiquidGlassSamplingSettingRow(
    selected: LiquidGlassSamplingQuality,
    onSelect: (String) -> Unit
) {
    DropdownSettingRow(
        label = stringResource(R.string.liquid_glass_sampling_quality),
        options = listOf(
            LiquidGlassSamplingQuality.ORIGINAL.storedValue to stringResource(R.string.liquid_glass_sampling_original),
            LiquidGlassSamplingQuality.CLEAR.storedValue to stringResource(R.string.liquid_glass_sampling_clear),
            LiquidGlassSamplingQuality.BALANCED.storedValue to stringResource(R.string.liquid_glass_sampling_balanced),
            LiquidGlassSamplingQuality.PERFORMANCE.storedValue to stringResource(R.string.liquid_glass_sampling_performance)
        ),
        selected = selected.storedValue,
        onSelect = onSelect
    )
}
