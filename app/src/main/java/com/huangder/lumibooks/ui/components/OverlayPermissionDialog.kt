package com.huangder.lumibooks.ui.components

import com.huangder.lumibooks.ui.components.AppDialogAction
import com.huangder.lumibooks.ui.components.AppDialogActionRole

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.huangder.lumibooks.R
import com.huangder.lumibooks.ui.theme.AppColors
import com.huangder.lumibooks.ui.theme.AppType
import com.huangder.lumibooks.ui.theme.LocalAppTheme

/** 悬浮窗字幕权限引导弹窗。 */
@Composable
fun OverlayPermissionDialog(
    onGoSettings: () -> Unit,
    onDismiss: () -> Unit
) {
    val isLiquidGlass = LocalAppTheme.current == "liquid_glass"

    LiquidGlassAlertDialog(
        onDismissRequest = onDismiss,
        titleText = stringResource(R.string.floating_subtitle_permission_title),
        text = {
            Text(
                text = stringResource(R.string.floating_subtitle_permission_message),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                fontSize = AppType.BodySmall,
                color = AppColors.TextSecondary,
                lineHeight = AppType.Body
            )
        },
        actions = listOf(
            AppDialogAction(
                text = stringResource(R.string.floating_subtitle_permission_open_settings),
                onClick = onGoSettings,
                role = AppDialogActionRole.Primary,
                tintedColor = AppColors.Accent
            ),
            AppDialogAction(
                text = stringResource(R.string.cancel),
                onClick = onDismiss,
                role = AppDialogActionRole.Cancel
            )
        ),
        contentScrimColor = AppColors.CardBg.copy(alpha = if (isLiquidGlass) 0.74f else 0.92f)
    )
}
