package com.huangder.lumibooks.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.huangder.lumibooks.R
import com.huangder.lumibooks.ui.theme.AppColors
import com.huangder.lumibooks.ui.theme.AppType
import com.huangder.lumibooks.ui.theme.LocalAppTheme

@Composable
fun RemoteNoticeDialog(
    title: String,
    message: String,
    onConfirm: () -> Unit
) {
    val isLiquidGlass = LocalAppTheme.current == "liquid_glass"

    LiquidGlassAlertDialog(
        onDismissRequest = {},
        title = {
            Text(
                text = title,
                fontSize = AppType.Body,
                fontWeight = FontWeight.Bold,
                color = AppColors.TextPrimary
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = message,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 260.dp)
                        .background(
                            color = AppColors.BgGray.copy(alpha = if (isLiquidGlass) 0.48f else 0.92f),
                            shape = RoundedCornerShape(18.dp)
                        )
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    fontSize = AppType.BodySmall,
                    color = AppColors.TextSecondary,
                    lineHeight = AppType.Body
                )
            }
        },
        confirmButton = {
            LiquidGlassTextButton(
                text = stringResource(R.string.remote_notice_acknowledge),
                tintedColor = AppColors.Accent,
                onClick = onConfirm
            )
        },
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false
        ),
        contentScrimColor = AppColors.CardBg.copy(alpha = if (isLiquidGlass) 0.74f else 0.92f)
    )
}
