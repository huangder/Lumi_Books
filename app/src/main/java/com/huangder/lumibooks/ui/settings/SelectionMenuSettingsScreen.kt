package com.huangder.lumibooks.ui.settings

import com.huangder.lumibooks.ui.theme.LocalReduceTransparency
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.huangder.lumibooks.R
import com.huangder.lumibooks.domain.model.SelectionMenuSettings
import com.huangder.lumibooks.ui.components.*
import com.huangder.lumibooks.ui.icons.AppIcons
import com.huangder.lumibooks.ui.icons.directionalIcon
import com.huangder.lumibooks.ui.theme.*
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop

/** Matches ThemeSuiteListScreen, including its fixed header and bottom command capsule. */
@Composable
internal fun SelectionMenuSettingsScreen(
    settings: SelectionMenuSettings,
    onChange: (SelectionMenuSettings) -> Unit,
    onBack: () -> Unit
) {
    val isGlass = LocalAppTheme.current == "liquid_glass"
    val controlsBackdrop = rememberLayerBackdrop().takeIf { isGlass }
    val listBackdrop = rememberLayerBackdrop()
    val listState = rememberLazyListState()
    var dragging by remember { mutableStateOf(false) }
    val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navigationBarHeight = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val capability = LocalLiquidGlassCapability.current
    val eInk = LocalEInkMode.current
    val blurEnabled = progressiveBlurEnabled(capability.supported, eInk, LocalReduceTransparency.current)
    val blurStrength by remember(listState) {
        derivedStateOf {
            if (listState.firstVisibleItemIndex > 0) 1f
            else (listState.firstVisibleItemScrollOffset / 96f).coerceIn(0f, 1f)
        }
    }
    Box(Modifier.fillMaxSize().background(AppColors.WindowBg)) {
        // Inline controls sample only the background, never themselves.
        Box(Modifier.matchParentSize()
            .then(controlsBackdrop?.let { Modifier.layerBackdrop(it) } ?: Modifier)
            .background(AppColors.WindowBg))
        ProvideLiquidGlassBackdrop(controlsBackdrop) {
            SelectionMenuSettingsEditor(
                settings, onChange,
                modifier = Modifier.align(Alignment.TopCenter).widthIn(max = 840.dp).fillMaxSize()
                    .then(if (blurEnabled && !dragging) Modifier.layerBackdrop(listBackdrop) else Modifier),
                listState = listState,
                contentPadding = PaddingValues(
                    start = AppSpace.md, end = AppSpace.md,
                    top = statusBarHeight + 56.dp + AppSpace.sm,
                    bottom = navigationBarHeight + 80.dp
                ),
                onDraggingChange = { dragging = it }
            )
            if (blurEnabled && !dragging) {
                ProgressiveTopBlur(listBackdrop, blurStrength, Modifier.align(Alignment.TopCenter),
                    bandHeight = statusBarHeight + 112.dp,
                    tint = AppColors.WindowBg.copy(alpha = if (LocalIsDarkTheme.current)
                        PROGRESSIVE_BLUR_TINT_ALPHA_DARK else PROGRESSIVE_BLUR_TINT_ALPHA))
            } else if (!dragging && progressiveTopScrimEnabled(capability.supported, eInk, LocalReduceTransparency.current)) {
                ProgressiveTopScrim(blurStrength, Modifier.align(Alignment.TopCenter),
                    bandHeight = statusBarHeight + 112.dp, tint = AppColors.WindowBg)
            }
            Row(Modifier.align(Alignment.TopCenter).fillMaxWidth()
                .then(if (dragging) Modifier.background(AppColors.WindowBg) else Modifier).statusBarsPadding()
                .height(56.dp).padding(horizontal = AppSpace.sm), verticalAlignment = Alignment.CenterVertically) {
                LiquidGlassIconButton(directionalIcon(AppIcons.ArrowLeft, AppIcons.ArrowRight),
                    stringResource(R.string.reader_back), onBack, settingsBackButton = true)
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.selection_menu_settings), color = AppColors.TextPrimary,
                        fontSize = AppType.Section, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.size(48.dp))
            }
            SelectionMenuCapsuleButton(
                stringResource(R.string.highlight_reset_default), AppIcons.ArrowCounterClockwise,
                { onChange(SelectionMenuSettings()) },
                modifier = Modifier.align(Alignment.BottomCenter).widthIn(max = 840.dp).fillMaxWidth()
                    .padding(horizontal = AppSpace.md, vertical = AppSpace.sm).navigationBarsPadding()
            )
        }
    }
}
