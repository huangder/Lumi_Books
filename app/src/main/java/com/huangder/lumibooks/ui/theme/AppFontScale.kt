package com.huangder.lumibooks.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import com.huangder.lumibooks.domain.model.AppFontSize

val LocalAppFontSize = staticCompositionLocalOf { AppFontSize.STANDARD }

// Keep the original density across nested themes and content-only reader boundaries.
internal val LocalAppBaseDensity = staticCompositionLocalOf<Density?> { null }

internal fun appFontDensity(base: Density, size: AppFontSize): Density {
    if (size == AppFontSize.STANDARD) return base
    return object : Density {
        override val density = base.density
        override val fontScale = base.fontScale * size.scale

        // Delegate the actual conversion to Android's original density so its nonlinear
        // accessibility font scaling is preserved, including at the standard setting.
        override fun TextUnit.toDp(): Dp = with(base) { (this@toDp * size.scale).toDp() }
        override fun Dp.toSp(): TextUnit = with(base) { this@toSp.toSp() / size.scale }
    }
}
