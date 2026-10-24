package com.huangder.lumibooks.ui.home.remote

import com.huangder.lumibooks.ui.theme.LocalReduceTransparency
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.huangder.lumibooks.ui.components.PROGRESSIVE_BLUR_TINT_ALPHA
import com.huangder.lumibooks.ui.components.PROGRESSIVE_BLUR_TINT_ALPHA_DARK
import com.huangder.lumibooks.ui.components.ProgressiveBottomBlur
import com.huangder.lumibooks.ui.components.ProgressiveBottomScrim
import com.huangder.lumibooks.ui.components.progressiveBlurEnabled
import com.huangder.lumibooks.ui.components.progressiveTopScrimEnabled
import com.huangder.lumibooks.ui.theme.AppColors
import com.huangder.lumibooks.ui.theme.AppSpace
import com.huangder.lumibooks.ui.theme.LocalEInkMode
import com.huangder.lumibooks.ui.theme.LocalIsDarkTheme
import com.huangder.lumibooks.ui.theme.LocalLiquidGlassCapability
import com.kyant.backdrop.backdrops.LayerBackdrop

/** Extra list padding so the last row can scroll above the floating bottom action. */
internal val RemoteBottomBarContentPadding: Dp = 132.dp

/**
 * Floating bottom action over scrolling content. Content under the button dissolves into a
 * progressive blur (or a soft scrim where blur is unavailable) instead of being cut off; e-ink
 * draws neither and relies on the button alone.
 *
 * [blurBackdrop] must capture only the scrolling content, never this bar.
 */
@Composable
internal fun BoxScope.RemoteBottomBar(
    blurBackdrop: LayerBackdrop?,
    content: @Composable ColumnScope.() -> Unit
) {
    val eInkMode = LocalEInkMode.current
    val supported = LocalLiquidGlassCapability.current.supported
    val tint = AppColors.WindowBg
    val tintAlpha = if (LocalIsDarkTheme.current) PROGRESSIVE_BLUR_TINT_ALPHA_DARK else PROGRESSIVE_BLUR_TINT_ALPHA
    val navigationBar = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val bandHeight = 120.dp + navigationBar
    Box(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
    ) {
        if (blurBackdrop != null && progressiveBlurEnabled(supported, eInkMode, LocalReduceTransparency.current)) {
            ProgressiveBottomBlur(
                backdrop = blurBackdrop,
                bandHeight = bandHeight,
                tint = tint.copy(alpha = tintAlpha * 4f),
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        } else if (progressiveTopScrimEnabled(supported, eInkMode, LocalReduceTransparency.current)) {
            ProgressiveBottomScrim(
                bandHeight = bandHeight,
                tint = tint,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = AppSpace.lg, vertical = AppSpace.md),
            content = content
        )
    }
}
