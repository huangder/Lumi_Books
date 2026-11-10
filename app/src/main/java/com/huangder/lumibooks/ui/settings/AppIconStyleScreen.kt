package com.huangder.lumibooks.ui.settings

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import com.huangder.lumibooks.ui.animation.VisualMotion.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import com.huangder.lumibooks.ui.theme.AppRoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.huangder.lumibooks.R
import com.huangder.lumibooks.domain.model.AppIconStyle
import com.huangder.lumibooks.ui.theme.AppColors
import com.huangder.lumibooks.ui.theme.AppSpace
import com.huangder.lumibooks.ui.theme.AppType
import com.huangder.lumibooks.ui.theme.LocalMotionEnabled
import com.huangder.lumibooks.ui.components.LumiSegmentedControl

@Composable
fun AppIconStyleDetail(viewModel: SettingsViewModel) {
    val uiState by viewModel.uiState.collectAsState()

    AppIconStyleOptions(
        selectedStyle = uiState.appIconStyle,
        easterEggUnlocked = uiState.lumiEasterEggUnlocked,
        onSelect = viewModel::saveAppIconStyle
    )
}

@Composable
internal fun AppIconStyleOptions(
    selectedStyle: String,
    easterEggUnlocked: Boolean = false,
    onSelect: (String) -> Unit
) {
    val selected = AppIconStyle.fromStoredValue(selectedStyle)
    val lumi2Selected = selected.isLumi2Family
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AppSpace.md, vertical = AppSpace.md)
                .selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            AppIconStyleOption(
                // Tapping the card keeps the current Lumi 2 appearance; from another style it
                // starts with the light artwork.
                style = if (lumi2Selected) selected else AppIconStyle.LUMI_2,
                label = stringResource(R.string.icon_style_lumi2),
                imageRes = lumi2PreviewRes(if (lumi2Selected) selected else AppIconStyle.LUMI_2),
                selected = lumi2Selected,
                onSelect = onSelect,
                modifier = Modifier.weight(1f)
            )
            if (easterEggUnlocked) {
                AppIconStyleOption(
                    style = AppIconStyle.LUMI_CHAN,
                    label = stringResource(R.string.icon_style_lumi_chan),
                    imageRes = R.mipmap.ic_launcher_lumi_chan,
                    selected = selected == AppIconStyle.LUMI_CHAN,
                    onSelect = onSelect,
                    modifier = Modifier.weight(1f)
                )
            }
            AppIconStyleOption(
                style = AppIconStyle.CLASSIC,
                label = stringResource(R.string.icon_style_classic),
                imageRes = R.mipmap.ic_launcher,
                selected = selected == AppIconStyle.CLASSIC,
                onSelect = onSelect,
                modifier = Modifier.weight(1f)
            )
        }
        if (lumi2Selected) {
            Lumi2AppearanceSelector(selected = selected, onSelect = onSelect)
        }
    }
}

internal val Lumi2AppearanceStyles = listOf(
    AppIconStyle.LUMI_2,
    AppIconStyle.LUMI_2_DARK,
    AppIconStyle.LUMI_2_AUTO
)

@DrawableRes
@Composable
private fun lumi2PreviewRes(style: AppIconStyle): Int = when (style) {
    AppIconStyle.LUMI_2_DARK -> R.mipmap.ic_launcher_lumi2_dark
    // The launcher uses bitmap XML to retain a stable resource ID. Compose's painterResource
    // accepts raster/vector assets, so select the corresponding raster for this preview.
    AppIconStyle.LUMI_2_AUTO -> if (isSystemInDarkTheme()) {
        R.mipmap.ic_launcher_lumi2_dark
    } else R.mipmap.ic_launcher_lumi2
    else -> R.mipmap.ic_launcher_lumi2
}

@Composable
private fun Lumi2AppearanceSelector(selected: AppIconStyle, onSelect: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = AppSpace.md, end = AppSpace.md, bottom = AppSpace.md)
    ) {
        LumiSegmentedControl(
            labels = listOf(
                stringResource(R.string.dark_mode_light),
                stringResource(R.string.dark_mode_dark),
                stringResource(R.string.icon_style_appearance_auto)
            ),
            selectedIndex = Lumi2AppearanceStyles.indexOf(selected).coerceAtLeast(0),
            onSelect = { index -> onSelect(Lumi2AppearanceStyles[index].storedValue) }
        )
        if (selected == AppIconStyle.LUMI_2_AUTO) {
            Spacer(Modifier.height(AppSpace.sm))
            Text(
                text = stringResource(R.string.icon_style_appearance_auto_hint),
                color = AppColors.TextSecondary,
                fontSize = AppType.Caption,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun AppIconStyleOption(
    style: AppIconStyle,
    label: String,
    @DrawableRes imageRes: Int,
    selected: Boolean,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val outerShape = AppRoundedCornerShape(28.dp)
    val imageShape = AppRoundedCornerShape(24.dp)
    val interactionSource = androidx.compose.runtime.remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val motionEnabled = LocalMotionEnabled.current
    val imageScale by animateFloatAsState(
        targetValue = if (motionEnabled && pressed) 0.96f else 1f,
        animationSpec = if (motionEnabled) tween(durationMillis = 90) else snap(),
        label = "appIconStyleImageScale"
    )

    Column(
        modifier = modifier.selectable(
            selected = selected,
            role = Role.RadioButton,
            interactionSource = interactionSource,
            indication = null,
            onClick = { onSelect(style.storedValue) }
        ),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .then(
                    if (selected) {
                        Modifier.border(2.dp, AppColors.Accent, outerShape)
                    } else {
                        Modifier
                    }
                )
                .padding(6.dp)
        ) {
            Image(
                painter = painterResource(imageRes),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = imageScale
                        scaleY = imageScale
                    }
                    .clip(imageShape)
                    .border(
                        width = 1.dp,
                        color = if (selected) {
                            AppColors.Divider.copy(alpha = 0.35f)
                        } else {
                            AppColors.TextSecondary.copy(alpha = 0.28f)
                        },
                        shape = imageShape
                    )
            )
        }
        Spacer(Modifier.height(AppSpace.sm))
        Text(
            text = label,
            color = if (selected) AppColors.Accent else AppColors.TextPrimary,
            fontSize = AppType.BodySmall,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
