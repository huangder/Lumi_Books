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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
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
    val annotatedMessage = rememberBoldMarkdown(message)

    LiquidGlassAlertDialog(
        onDismissRequest = onConfirm,
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
                    text = annotatedMessage,
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
                text = "\u6536\u5230",
                tintedColor = AppColors.Accent,
                onClick = onConfirm
            )
        },
        contentScrimColor = AppColors.CardBg.copy(alpha = if (isLiquidGlass) 0.74f else 0.92f)
    )
}


@Composable
private fun rememberBoldMarkdown(text: String) = androidx.compose.runtime.remember(text) {
    buildAnnotatedString {
        var index = 0
        while (index < text.length) {
            val start = text.indexOf("**", startIndex = index)
            if (start < 0) {
                append(text.substring(index))
                break
            }
            append(text.substring(index, start))
            val end = text.indexOf("**", startIndex = start + 2)
            if (end < 0) {
                append(text.substring(start))
                break
            }
            withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = AppColors.TextPrimary)) {
                append(text.substring(start + 2, end))
            }
            index = end + 2
        }
    }
}
