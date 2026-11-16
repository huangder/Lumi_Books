package com.huangder.lumibooks.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import com.huangder.lumibooks.R
import com.huangder.lumibooks.ui.theme.AppColors
import com.huangder.lumibooks.ui.theme.AppRoundedCornerShape
import com.huangder.lumibooks.ui.theme.AppType
import com.huangder.lumibooks.ui.theme.LocalAppTheme

@Composable
fun RemoteNoticeDialog(
    title: String,
    message: String,
    imageUrl: String? = null,
    @DrawableRes imageResId: Int? = null,
    dismissible: Boolean = false,
    onConfirm: () -> Unit
) {
    val isLiquidGlass = LocalAppTheme.current == "liquid_glass"
    val imageModel = imageResId ?: imageUrl
    val maxContentHeight = LocalConfiguration.current.screenHeightDp.dp * 0.72f

    LiquidGlassAlertDialog(
        onDismissRequest = { if (dismissible) onConfirm() },
        modifier = if (isLiquidGlass) Modifier else Modifier
            .widthIn(max = 480.dp)
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        titleText = title,
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Spacer(modifier = Modifier.height(2.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = maxContentHeight)
                        .background(
                            color = AppColors.BgGray.copy(alpha = if (isLiquidGlass) 0.48f else 0.92f),
                            shape = AppRoundedCornerShape(18.dp)
                        )
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                ) {
                    Text(
                        text = message,
                        fontSize = AppType.BodySmall,
                        color = AppColors.TextSecondary,
                        lineHeight = AppType.Body
                    )
                    if (imageModel != null) {
                        SubcomposeAsyncImage(
                            model = imageModel,
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxWidth().heightIn(max = 220.dp),
                            loading = {},
                            error = {},
                            success = {
                                SubcomposeAsyncImageContent(
                                    modifier = Modifier.padding(top = 10.dp)
                                        .clip(AppRoundedCornerShape(12.dp))
                                )
                            }
                        )
                    }
                }
            }
        },
        actions = listOf(
            AppDialogAction(
                text = stringResource(if (dismissible) R.string.close else R.string.remote_notice_acknowledge),
                onClick = onConfirm,
                role = AppDialogActionRole.Primary,
                tintedColor = AppColors.Accent
            )
        ),
        properties = DialogProperties(
            dismissOnBackPress = dismissible,
            dismissOnClickOutside = dismissible,
            usePlatformDefaultWidth = false
        ),
        contentScrimColor = AppColors.CardBg.copy(alpha = if (isLiquidGlass) 0.74f else 0.92f)
    )
}
