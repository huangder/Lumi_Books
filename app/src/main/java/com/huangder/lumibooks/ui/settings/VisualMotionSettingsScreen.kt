package com.huangder.lumibooks.ui.settings

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Text
import com.huangder.lumibooks.ui.theme.AppSpace
import com.huangder.lumibooks.ui.theme.AppType
import com.huangder.lumibooks.ui.theme.AppColors
import com.huangder.lumibooks.R
import com.huangder.lumibooks.domain.model.BookOpenTransition
import com.huangder.lumibooks.ui.theme.LocalLiquidGlassCapability

@Composable
fun VisualMotionDetail(viewModel: SettingsViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    VisualMotionContent(
        uiState = uiState,
        onGlassStyle = viewModel::saveLiquidGlassStyle,
        onGlassTransparency = viewModel::saveLiquidGlassTransparency,
        onPreviewGlassTransparency = viewModel::previewLiquidGlassTransparency,
        onHdrHighlight = viewModel::saveLiquidGlassHdrHighlightEnabled,
        onCardOutlines = viewModel::saveCardOutlinesEnabled,
        onReduceTransparency = viewModel::saveReduceTransparencyEnabled,
        onPredictiveBack = viewModel::savePredictiveBackEnabled,
        onMotion = viewModel::saveMotionPreference,
        onMenuAnimation = viewModel::saveMenuAnimationStyle,
        onBookTransition = viewModel::saveBookOpenTransition
    )
}

@Composable
internal fun VisualMotionContent(
    uiState: SettingsUiState,
    onGlassStyle: (String) -> Unit,
    onGlassTransparency: (Float) -> Unit,
    onPreviewGlassTransparency: (Float) -> Unit,
    onHdrHighlight: (Boolean) -> Unit,
    onCardOutlines: (Boolean) -> Unit,
    onReduceTransparency: (Boolean) -> Unit,
    onPredictiveBack: (Boolean) -> Unit,
    onMotion: (String) -> Unit,
    onMenuAnimation: (String) -> Unit,
    onBookTransition: (String) -> Unit
) {
    val capability = LocalLiquidGlassCapability.current
    val showGlassControls = uiState.appTheme == "liquid_glass" &&
        !uiState.eInkModeEnabled && capability.supported

    if (showGlassControls) {
        SettingsSectionTitle(
            stringResource(R.string.settings_section_glass),
            sectionKey = "visual_glass"
        )
        DetailCard {
            LiquidGlassStyleSettingRow(uiState.liquidGlassStyle, onGlassStyle)
            SettingsDivider()
            SettingsSliderItem(
                label = stringResource(R.string.liquid_glass_transparency),
                value = uiState.liquidGlassTransparency,
                range = 0f..1f,
                valueText = "${(uiState.liquidGlassTransparency * 100).toInt()}%",
                step = 0.05f,
                onDragChange = onPreviewGlassTransparency,
                onChange = onGlassTransparency
            )
            if (capability.hdrSupported) {
                SettingsDivider()
                SettingsSwitchRow(
                    label = stringResource(R.string.liquid_glass_hdr_highlight),
                    checked = uiState.liquidGlassHdrHighlightEnabled,
                    onCheckedChange = onHdrHighlight
                )
            }
        }
        Spacer(Modifier.height(8.dp))
    }

    SettingsSectionTitle(
        stringResource(R.string.settings_section_visual_accessibility),
        sectionKey = "visual_accessibility"
    )
    DetailCard {
        SettingsSwitchRow(
            label = stringResource(R.string.label_card_outlines),
            checked = uiState.cardOutlinesEnabled,
            onCheckedChange = onCardOutlines
        )
        SettingsDivider()
        SettingsSwitchRow(
            label = stringResource(R.string.reduce_transparency_label),
            supportingText = stringResource(R.string.reduce_transparency_description),
            checked = uiState.reduceTransparencyEnabled,
            onCheckedChange = onReduceTransparency
        )
    }

    SettingsSectionTitle(
        stringResource(R.string.settings_section_motion),
        sectionKey = "visual_motion"
    )
    DetailCard {
        SettingsSwitchRow(
            label = stringResource(R.string.label_predictive_back),
            checked = uiState.predictiveBackEnabled,
            onCheckedChange = onPredictiveBack
        )
        SettingsDivider()
        DropdownSettingRow(
            label = stringResource(R.string.motion_preference_label),
            options = listOf(
                "standard" to stringResource(R.string.motion_preference_standard),
                "fast" to stringResource(R.string.motion_preference_fast),
                "reduced" to stringResource(R.string.motion_preference_reduced)
            ),
            selected = uiState.motionPreference,
            onSelect = onMotion
        )
        SettingsDivider()
        DropdownSettingRow(
            label = stringResource(R.string.menu_animation_style_label),
            options = listOf(
                "normal" to stringResource(R.string.menu_animation_style_normal),
                "liquid" to stringResource(R.string.menu_animation_style_liquid)
            ),
            selected = uiState.menuAnimationStyle,
            onSelect = onMenuAnimation
        )
        Text(
            stringResource(R.string.menu_animation_style_hint),
            modifier = Modifier.padding(horizontal = AppSpace.md).padding(bottom = AppSpace.md),
            fontSize = AppType.Caption,
            color = AppColors.TextSecondary
        )
        SettingsDivider()
        DropdownSettingRow(
            label = stringResource(R.string.book_open_transition_label),
            options = listOf(
                BookOpenTransition.HERO.storedValue to stringResource(R.string.book_open_transition_hero),
                BookOpenTransition.LOADING_PAGE.storedValue to
                    stringResource(R.string.book_open_transition_loading_page)
            ),
            selected = uiState.bookOpenTransition,
            onSelect = onBookTransition
        )
    }
}
