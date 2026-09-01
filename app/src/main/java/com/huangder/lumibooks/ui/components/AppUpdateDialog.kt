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
fun AppUpdateDialog(
    appVersion: String,
    updateTitle: String = "",
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
            stringResource(R.string.app_update_required_message)
        } else {
            stringResource(R.string.app_update_available_message, appVersion)
        }
    }
    val displayChangelog = changelog.ifBlank { stringResource(R.string.app_update_empty_changelog) }

    LiquidGlassAlertDialog(
        onDismissRequest = {},
        title = {
            Text(
                text = updateTitle.ifBlank {
                    stringResource(if (force) R.string.app_update_required_title else R.string.app_update_default_title)
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
                    text = stringResource(R.string.app_update_changelog_label),
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
                text = stringResource(if (force) R.string.app_update_download_new_version else R.string.app_update_download),
                tintedColor = AppColors.Accent,
                onClick = onDownload
            )
        },
        dismissButton = if (force) null else {
            {
                onIgnoreVersion?.let { ignore ->
                    LiquidGlassTextButton(
                        text = stringResource(R.string.app_update_ignore_version),
                        contentColor = AppColors.TextSecondary,
                        onClick = ignore
                    )
                }
                LiquidGlassTextButton(
                    text = stringResource(R.string.later),
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
