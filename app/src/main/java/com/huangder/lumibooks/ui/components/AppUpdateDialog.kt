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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.huangder.lumibooks.ui.theme.AppColors
import com.huangder.lumibooks.ui.theme.AppType
import com.huangder.lumibooks.ui.theme.LocalAppTheme

@Composable
fun AppUpdateDialog(
    appVersion: String,
    updateTitle: String = "\u53d1\u73b0\u65b0\u7248\u672c",
    updateMessage: String = "",
    changelog: String = "",
    force: Boolean = false,
    onDownload: () -> Unit,
    onLater: () -> Unit,
    onIgnoreVersion: (() -> Unit)? = null
) {
    val isLiquidGlass = LocalAppTheme.current == "liquid_glass"
    val displayMessage = updateMessage.ifBlank {
        if (force) {
            "\u5f53\u524d\u7248\u672c\u9700\u8981\u66f4\u65b0\u540e\u624d\u80fd\u7ee7\u7eed\u4f7f\u7528\u3002"
        } else {
            "\u65b0\u7248\u672c $appVersion \u5df2\u53d1\u5e03\uff0c\u662f\u5426\u524d\u5f80\u4e0b\u8f7d\uff1f"
        }
    }
    val displayChangelog = changelog.ifBlank { "\u6682\u65e0\u66f4\u65b0\u65e5\u5fd7\u3002" }

    LiquidGlassAlertDialog(
        onDismissRequest = {},
        title = {
            Text(
                text = updateTitle.ifBlank {
                    if (force) "\u9700\u8981\u66f4\u65b0" else "\u53d1\u73b0\u65b0\u7248\u672c"
                },
                fontSize = AppType.Body,
                fontWeight = FontWeight.Bold,
                color = AppColors.TextPrimary
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = displayMessage,
                    fontSize = AppType.BodySmall,
                    color = AppColors.TextSecondary
                )
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = "\u66f4\u65b0\u65e5\u5fd7",
                    fontSize = AppType.BodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = AppColors.TextPrimary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = displayChangelog,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 220.dp)
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
                text = if (force) "\u4e0b\u8f7d\u65b0\u7248\u672c" else "\u4e0b\u8f7d",
                tintedColor = AppColors.Accent,
                onClick = onDownload
            )
        },
        dismissButton = if (force) null else {
            {
                onIgnoreVersion?.let { ignore ->
                    LiquidGlassTextButton(
                        text = "\u5ffd\u7565\u8be5\u7248\u672c",
                        contentColor = AppColors.TextSecondary,
                        onClick = ignore
                    )
                }
                LiquidGlassTextButton(
                    text = "\u7a0d\u540e",
                    contentColor = AppColors.TextSecondary,
                    onClick = onLater
                )
            }
        },
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false
        ),
        contentScrimColor = AppColors.CardBg.copy(alpha = if (isLiquidGlass) 0.74f else 0.92f)
    )
}
