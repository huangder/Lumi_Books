package com.huangder.lumibooks.ui.theme

import androidx.compose.runtime.compositionLocalOf
import com.huangder.lumibooks.domain.model.LiquidGlassSamplingQuality

val LocalLiquidGlassSamplingQuality = compositionLocalOf { LiquidGlassSamplingQuality.BALANCED }

internal val LocalSelectedLiquidGlassSamplingQuality = compositionLocalOf<LiquidGlassSamplingQuality?> { null }
