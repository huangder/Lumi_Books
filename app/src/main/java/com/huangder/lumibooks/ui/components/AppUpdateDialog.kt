package com.huangder.lumibooks.ui.components

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight
import com.huangder.lumibooks.ui.theme.AppColors
import com.huangder.lumibooks.ui.theme.AppType

@Composable
fun AppUpdateDialog(
    appVersion: String,
    onDownload: () -> Unit,
    onLater: () -> Unit
) {
    LiquidGlassAlertDialog(
        onDismissRequest = onLater,
        title = {
            Text(
                text = "发现新版本",
                fontSize = AppType.Body,
                fontWeight = FontWeight.Bold,
                color = AppColors.TextPrimary
            )
        },
        text = {
            Text(
                text = "新版本 $appVersion 已发布，是否前往下载？",
                fontSize = AppType.BodySmall,
                color = AppColors.TextSecondary
            )
        },
        confirmButton = {
            LiquidGlassTextButton(
                text = "下载",
                tintedColor = AppColors.Accent,
                onClick = onDownload
            )
        },
        dismissButton = {
            LiquidGlassTextButton(
                text = "稍后",
                contentColor = AppColors.TextSecondary,
                onClick = onLater
            )
        },
        contentScrimColor = AppColors.CardBg.copy(alpha = 0.22f)
    )
}
