package com.huangder.lumibooks.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.huangder.lumibooks.R
import com.huangder.lumibooks.ui.components.LiquidGlassButton
import com.huangder.lumibooks.ui.theme.AppColors
import com.huangder.lumibooks.ui.theme.AppRadius
import com.huangder.lumibooks.ui.theme.AppRoundedCornerShape
import com.huangder.lumibooks.ui.theme.AppSpace
import com.huangder.lumibooks.ui.theme.AppType
import com.huangder.lumibooks.ui.theme.LocalAppTheme

/** The selected count belongs to the primary action; select-all sits beside it. */
@Composable
internal fun BookPickerSelectionBar(
    confirmText: String,
    confirmEnabled: Boolean,
    selectAllEnabled: Boolean,
    onConfirm: () -> Unit,
    onSelectAll: () -> Unit
) {
    val shape = AppRoundedCornerShape(AppRadius.lg)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(AppSpace.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LiquidGlassButton(
            onClick = onConfirm,
            enabled = confirmEnabled,
            modifier = Modifier.weight(1f).heightIn(min = 58.dp).then(
                if (confirmEnabled && LocalAppTheme.current != "liquid_glass") {
                    Modifier.shadow(
                        elevation = 28.dp, shape = shape, clip = false,
                        ambientColor = Color.Black.copy(alpha = 0.10f),
                        spotColor = Color.Black.copy(alpha = 0.08f)
                    )
                } else Modifier
            ),
            shape = shape,
            tintedColor = if (confirmEnabled) AppColors.Accent else AppColors.BgGray,
            prominentShadow = confirmEnabled,
            contentColor = if (confirmEnabled) AppColors.OnAccent else AppColors.TextSecondary
        ) {
            Text(confirmText, fontSize = AppType.Body, fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        LiquidGlassButton(
            onClick = onSelectAll,
            enabled = selectAllEnabled,
            modifier = Modifier.heightIn(min = 58.dp).widthIn(min = 80.dp),
            shape = AppRoundedCornerShape(AppRadius.full),
            tintedColor = AppColors.BgGray,
            contentColor = AppColors.TextPrimary
        ) {
            Text(stringResource(R.string.import_select_all), fontSize = AppType.BodySmall,
                fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
        }
    }
}
