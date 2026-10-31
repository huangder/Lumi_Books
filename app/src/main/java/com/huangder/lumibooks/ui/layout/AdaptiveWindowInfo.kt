package com.huangder.lumibooks.ui.layout

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.isSpecified
import kotlin.math.roundToInt

internal const val MEDIUM_WINDOW_WIDTH_DP = 600
internal const val EXPANDED_WINDOW_WIDTH_DP = 840
internal const val MEDIUM_WINDOW_HEIGHT_DP = 480
internal const val EXPANDED_WINDOW_HEIGHT_DP = 900

/** M3 window size classes; height uses the same three tiers with its own breakpoints. */
internal enum class WindowSizeTier { COMPACT, MEDIUM, EXPANDED }

/** Unit-independent shape check: up to 25% taller than wide still counts as near-square. */
internal fun isLandscapeOrNearSquare(width: Int, height: Int): Boolean =
    width > 0 && height > 0 && height.toLong() * 4 <= width.toLong() * 5

/**
 * Tablets and unfolded foldables share these window-based rules. A narrow split window or
 * cover display returns to compact layout even if the reported smallest width is still large.
 */
internal data class AdaptiveWindowInfo(
    val widthDp: Int,
    val heightDp: Int,
    /** Configuration.smallestScreenWidthDp; may lag behind a fold or display change. */
    val smallestWidthDp: Int = minOf(widthDp, heightDp)
) {
    val widthTier: WindowSizeTier = sizeTier(widthDp, MEDIUM_WINDOW_WIDTH_DP, EXPANDED_WINDOW_WIDTH_DP)
    val heightTier: WindowSizeTier = sizeTier(heightDp, MEDIUM_WINDOW_HEIGHT_DP, EXPANDED_WINDOW_HEIGHT_DP)
    val isMediumWidthOrLarger: Boolean = widthTier != WindowSizeTier.COMPACT
    val usesWideLayout: Boolean = isMediumWidthOrLarger && isLandscapeOrNearSquare(widthDp, heightDp)

    /**
     * Wide landscape and near-square windows use an edge rail, including unfolded inner
     * displays in either rotation. Clearly tall portrait windows keep the bottom capsule.
     * Short landscape phone windows also use the rail to preserve their limited height.
     */
    val usesSideChrome: Boolean = usesWideLayout ||
        (widthDp > heightDp && heightTier == WindowSizeTier.COMPACT)

    /**
     * Medium-width windows, including landscape phones, present sheets as centered cards.
     * The card frame caps its height to the available safe area on short windows.
     */
    val usesCardSheets: Boolean = isMediumWidthOrLarger

    /** Configuration size hint only; adaptive layouts follow the live window width instead. */
    val isPhoneSized: Boolean = smallestWidthDp < MEDIUM_WINDOW_WIDTH_DP
}

private fun sizeTier(valueDp: Int, mediumDp: Int, expandedDp: Int): WindowSizeTier = when {
    valueDp >= expandedDp -> WindowSizeTier.EXPANDED
    valueDp >= mediumDp -> WindowSizeTier.MEDIUM
    else -> WindowSizeTier.COMPACT
}

@Composable
internal fun currentAdaptiveWindowInfo(): AdaptiveWindowInfo {
    val configuration = LocalConfiguration.current
    // WindowInfo observes the live host window, independently of resource locale
    // overrides. Configuration dimensions can lag behind rotation on OEM devices.
    val windowSize = LocalWindowInfo.current.containerDpSize
    val widthDp = if (windowSize.isSpecified && windowSize.width.value > 0f) {
        windowSize.width.value.roundToInt()
    } else configuration.screenWidthDp
    val heightDp = if (windowSize.isSpecified && windowSize.height.value > 0f) {
        windowSize.height.value.roundToInt()
    } else configuration.screenHeightDp
    return remember(
        widthDp,
        heightDp,
        configuration.smallestScreenWidthDp
    ) {
        AdaptiveWindowInfo(
            widthDp = widthDp,
            heightDp = heightDp,
            // 0 means undefined; fall back to the current window rather than treating it as a phone.
            smallestWidthDp = configuration.smallestScreenWidthDp.takeIf { it > 0 }
                ?: minOf(widthDp, heightDp)
        )
    }
}
