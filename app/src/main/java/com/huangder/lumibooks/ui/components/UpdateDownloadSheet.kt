package com.huangder.lumibooks.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.huangder.lumibooks.R
import com.huangder.lumibooks.ui.theme.AppColors
import com.huangder.lumibooks.ui.theme.AppRoundedCornerShape
import com.huangder.lumibooks.ui.theme.AppSpace
import com.huangder.lumibooks.ui.theme.AppType

@Composable
internal fun UpdateDownloadSheet(
    githubUrl: String,
    onDismiss: () -> Unit,
    onOpenChannel: (String) -> Unit
) {
    val githubBackground = if (AppColors.CardBg.luminance() < 0.5f) Color.White else Color.Black
    val githubForeground = if (githubBackground == Color.Black) Color.White else Color.Black
    AppBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = appSheetMaxHeight())
                .verticalScroll(rememberScrollState())
                .padding(appSheetPadding()),
            verticalArrangement = Arrangement.spacedBy(AppSpace.sm)
        ) {
            AppSheetHeader(
                title = stringResource(R.string.app_update_download_channel),
                onClose = onDismiss,
                onBack = onDismiss
            )
            LiquidGlassButton(
                onClick = { onOpenChannel(githubUrl) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                shape = AppRoundedCornerShape(16.dp),
                tintedColor = githubBackground,
                contentColor = githubForeground
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_github_mark),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = githubForeground
                )
                Spacer(Modifier.size(AppSpace.sm))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "GitHub",
                        modifier = Modifier.alignByBaseline(),
                        fontSize = AppType.Body,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = stringResource(R.string.app_update_download_recommended),
                        modifier = Modifier.alignByBaseline(),
                        color = githubForeground.copy(alpha = 0.72f),
                        fontSize = AppType.Caption
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AppSpace.sm)
            ) {
                val password = stringResource(R.string.app_update_download_code, "lumi")
                BackupDownloadButton(
                    title = stringResource(R.string.app_update_download_baidu),
                    detail = password,
                    modifier = Modifier.weight(1f)
                ) { onOpenChannel("https://pan.baidu.com/s/1vDdFsoqQuuZlntUTUjssZw?pwd=lumi") }
                BackupDownloadButton(
                    title = stringResource(R.string.app_update_download_lanzou),
                    detail = password,
                    modifier = Modifier.weight(1f)
                ) { onOpenChannel("https://wwaug.lanzoub.com/b0xxfmxvi") }
                BackupDownloadButton(title = "Gitee", modifier = Modifier.weight(1f)) {
                    onOpenChannel("https://gitee.com/huangder/lumi_-books_-gitee")
                }
            }
        }
    }
}

@Composable
private fun BackupDownloadButton(
    title: String,
    modifier: Modifier = Modifier,
    detail: String? = null,
    onClick: () -> Unit
) {
    LiquidGlassButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = 64.dp),
        shape = AppRoundedCornerShape(16.dp),
        tintedColor = AppColors.BgGray,
        contentColor = AppColors.TextPrimary,
        compact = true
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, fontSize = AppType.BodySmall, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)
            detail?.let {
                Text(it, color = AppColors.TextSecondary, fontSize = AppType.Caption, textAlign = TextAlign.Center)
            }
        }
    }
}
