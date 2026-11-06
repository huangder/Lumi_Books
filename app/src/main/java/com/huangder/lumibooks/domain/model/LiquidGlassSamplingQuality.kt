package com.huangder.lumibooks.domain.model

import androidx.compose.ui.unit.IntSize
import kotlin.math.ceil

/** Linear resolution of glass pixels; layout and foreground content stay at native resolution. */
enum class LiquidGlassSamplingQuality(val storedValue: String, val scale: Float) {
    ORIGINAL("original", 1f),
    // Stored IDs predate the display names; preserve existing selections by scale.
    CLEAR("balanced", 2f / 3f),
    BALANCED("smooth", 0.5f),
    PERFORMANCE("performance", 1f / 3f);

    fun sampleSize(viewport: IntSize): IntSize = IntSize(
        ceil(viewport.width.coerceAtLeast(1) * scale).toInt().coerceAtLeast(1),
        ceil(viewport.height.coerceAtLeast(1) * scale).toInt().coerceAtLeast(1)
    )

    companion object {
        fun fromStoredValue(value: String?): LiquidGlassSamplingQuality =
            entries.firstOrNull { it.storedValue == value } ?: BALANCED
    }
}
