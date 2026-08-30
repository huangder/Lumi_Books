package com.huangder.lumibooks.ui.settings

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.shape.RoundedCornerShape
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

@Composable
fun AppIconStyleDetail(viewModel: SettingsViewModel) {
    val uiState by viewModel.uiState.collectAsState()

    AppIconStyleOptions(
        selectedStyle = uiState.appIconStyle,
        onSelect = viewModel::saveAppIconStyle
    )
}

@Composable
internal fun AppIconStyleOptions(
    selectedStyle: String,
    onSelect: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppSpace.md, vertical = AppSpace.md)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        AppIconStyleOption(
            style = AppIconStyle.LUMI_2,
            label = stringResource(R.string.icon_style_lumi2),
            imageRes = R.mipmap.ic_launcher_lumi2,
            selected = selectedStyle == AppIconStyle.LUMI_2.storedValue,
            onSelect = onSelect,
            modifier = Modifier.weight(1f)
        )
        AppIconStyleOption(
            style = AppIconStyle.CLASSIC,
            label = stringResource(R.string.icon_style_classic),
            imageRes = R.mipmap.ic_launcher,
            selected = selectedStyle == AppIconStyle.CLASSIC.storedValue,
            onSelect = onSelect,
            modifier = Modifier.weight(1f)
        )
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
    val outerShape = RoundedCornerShape(28.dp)
    val imageShape = RoundedCornerShape(24.dp)
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
