package com.huangder.lumibooks.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.huangder.lumibooks.R
import com.huangder.lumibooks.domain.model.SelectionMenuSettings
import com.huangder.lumibooks.ui.components.LiquidGlassIconButton
import com.huangder.lumibooks.ui.components.PROGRESSIVE_BLUR_TINT_ALPHA
import com.huangder.lumibooks.ui.components.PROGRESSIVE_BLUR_TINT_ALPHA_DARK
import com.huangder.lumibooks.ui.components.ProgressiveTopBlur
import com.huangder.lumibooks.ui.components.ProgressiveTopScrim
import com.huangder.lumibooks.ui.components.ProvideLiquidGlassBackdrop
import com.huangder.lumibooks.ui.components.SelectionMenuCapsuleButton
import com.huangder.lumibooks.ui.components.SelectionMenuSettingsEditor
import com.huangder.lumibooks.ui.components.progressiveBlurEnabled
import com.huangder.lumibooks.ui.components.progressiveTopScrimEnabled
import com.huangder.lumibooks.ui.components.quantizeBlurStrength
import com.huangder.lumibooks.ui.icons.AppIcons
import com.huangder.lumibooks.ui.icons.directionalIcon
import com.huangder.lumibooks.ui.theme.AppColors
import com.huangder.lumibooks.ui.theme.AppSpace
import com.huangder.lumibooks.ui.theme.AppType
import com.huangder.lumibooks.ui.theme.LocalAppTheme
import com.huangder.lumibooks.ui.theme.LocalEInkMode
import com.huangder.lumibooks.ui.theme.LocalIsDarkTheme
import com.huangder.lumibooks.ui.theme.LocalLiquidGlassCapability
import com.huangder.lumibooks.ui.theme.LocalReduceTransparency
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop

/** Scroll distance over which the top blur reaches full strength. */
private const val TopBlurRampPx = 96f

/**
 * Settings page for the text selection menu. Changes are saved as they happen.
 *
 * Layered like the reader theme list: page background, scrolling cards, top blur band,
 * then the toolbar and the bottom reset button, which never enter the blur.
 */
@Composable
fun SelectionMenuSettingsScreen(
    settings: SelectionMenuSettings,
    onChange: (SelectionMenuSettings) -> Unit,
    onBack: () -> Unit
) {
    val isLiquidGlass = LocalAppTheme.current == "liquid_glass"
    val capability = LocalLiquidGlassCapability.current
    val eInkMode = LocalEInkMode.current
    val reduceTransparency = LocalReduceTransparency.current
    val blurEnabled = progressiveBlurEnabled(capability.supported, eInkMode, reduceTransparency)
    val scrimEnabled = progressiveTopScrimEnabled(capability.supported, eInkMode, reduceTransparency)
    // Glass controls sample the page background only; the blur band samples the cards only.
    val backgroundBackdrop = rememberLayerBackdrop()
    val cardsBackdrop = rememberLayerBackdrop()
    val listState = rememberLazyListState()
    var reordering by remember { mutableStateOf(false) }
    val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val topBandHeight = statusBarHeight + 112.dp
    val blurStrength by remember(listState) {
        derivedStateOf {
            val scrolled = if (listState.firstVisibleItemIndex > 0) 1f
            else listState.firstVisibleItemScrollOffset / TopBlurRampPx
            quantizeBlurStrength(scrolled)
        }
    }

    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .then(if (isLiquidGlass) Modifier.layerBackdrop(backgroundBackdrop) else Modifier)
                .background(AppColors.PageBg)
        )
        Box(
            Modifier
                .fillMaxSize()
                .then(if (blurEnabled) Modifier.layerBackdrop(cardsBackdrop) else Modifier)
        ) {
            SelectionMenuSettingsEditor(
                settings = settings,
                onChange = onChange,
                modifier = Modifier.align(Alignment.TopCenter).widthIn(max = 840.dp).fillMaxWidth(),
                listState = listState,
                contentPadding = PaddingValues(
                    start = AppSpace.md,
                    top = statusBarHeight + 56.dp + AppSpace.sm,
                    end = AppSpace.md,
                    bottom = 112.dp
                ),
                onReorderingChange = { reordering = it }
            )
        }
        // A drag re-renders the cards every frame. Swap the blur for the plain scrim meanwhile,
        // which keeps the toolbar readable without re-blurring the band on each frame.
        if (blurEnabled && !reordering) {
            ProgressiveTopBlur(
                backdrop = cardsBackdrop,
                strength = blurStrength,
                modifier = Modifier.align(Alignment.TopCenter),
                bandHeight = topBandHeight,
                tint = AppColors.WindowBg.copy(
                    alpha = if (LocalIsDarkTheme.current) PROGRESSIVE_BLUR_TINT_ALPHA_DARK else PROGRESSIVE_BLUR_TINT_ALPHA
                )
            )
        } else if (scrimEnabled || blurEnabled) {
            ProgressiveTopScrim(
                strength = blurStrength,
                modifier = Modifier.align(Alignment.TopCenter),
                bandHeight = topBandHeight,
                tint = AppColors.WindowBg
            )
        }
        ProvideLiquidGlassBackdrop(backgroundBackdrop.takeIf { isLiquidGlass }) {
            SelectionMenuCapsuleButton(
                text = stringResource(R.string.highlight_reset_default),
                icon = AppIcons.ArrowCounterClockwise,
                onClick = { onChange(SelectionMenuSettings()) },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(horizontal = AppSpace.md, vertical = AppSpace.sm)
            )
            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .height(56.dp)
                    .padding(horizontal = AppSpace.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LiquidGlassIconButton(
                    imageVector = directionalIcon(AppIcons.ArrowLeft, AppIcons.ArrowRight),
                    contentDescription = stringResource(R.string.back),
                    onClick = onBack,
                    settingsBackButton = true
                )
                Text(
                    text = stringResource(R.string.selection_menu_settings),
                    fontSize = AppType.Section,
                    fontWeight = FontWeight.Bold,
                    color = AppColors.TextPrimary,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(horizontal = AppSpace.sm)
                )
                // Same width as the back button so the title stays centred on screen.
                Spacer(Modifier.size(48.dp))
            }
        }
    }
}
