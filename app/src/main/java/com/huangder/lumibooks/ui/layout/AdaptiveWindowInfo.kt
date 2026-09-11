package com.huangder.lumibooks.ui.layout

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration

internal const val MEDIUM_WINDOW_WIDTH_DP = 600

internal data class AdaptiveWindowInfo(
    val widthDp: Int,
    val heightDp: Int
) {
    val isMediumWidthOrLarger: Boolean = widthDp >= MEDIUM_WINDOW_WIDTH_DP
    val isWideLandscape: Boolean = isMediumWidthOrLarger && widthDp > heightDp
}

@Composable
internal fun currentAdaptiveWindowInfo(): AdaptiveWindowInfo {
    val configuration = LocalConfiguration.current
    return remember(configuration.screenWidthDp, configuration.screenHeightDp) {
        AdaptiveWindowInfo(
            widthDp = configuration.screenWidthDp,
            heightDp = configuration.screenHeightDp
        )
    }
}
