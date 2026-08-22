package com.huangder.lumibooks.ui.components

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

/**
 * 听书悬浮窗权限引导弹窗：提示需要「显示在其他应用上层」权限，引导前往系统设置。
 */
@Composable
fun OverlayPermissionDialog(
    onGoSettings: () -> Unit,
    onDismiss: () -> Unit
) {
    val isLiquidGlass = LocalAppTheme.current == "liquid_glass"

    LiquidGlassAlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.tts_floating_permission_title),
                fontSize = AppType.Body,
                fontWeight = FontWeight.Bold,
                color = AppColors.TextPrimary
            )
        },
        text = {
            Text(
                text = stringResource(R.string.tts_floating_permission_required),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                fontSize = AppType.BodySmall,
                color = AppColors.TextSecondary,
                lineHeight = AppType.Body
            )
        },
        confirmButton = {
            LiquidGlassTextButton(
                text = stringResource(R.string.tts_floating_go_settings),
                tintedColor = AppColors.Accent,
                onClick = onGoSettings
            )
        },
        dismissButton = {
            LiquidGlassTextButton(
                text = stringResource(R.string.cancel),
                onClick = onDismiss
            )
        },
        contentScrimColor = AppColors.CardBg.copy(alpha = if (isLiquidGlass) 0.74f else 0.92f)
    )
}
