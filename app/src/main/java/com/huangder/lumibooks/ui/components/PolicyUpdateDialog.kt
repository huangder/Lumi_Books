package com.huangder.lumibooks.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.huangder.lumibooks.ui.theme.AppColors
import com.huangder.lumibooks.ui.theme.LocalAppTheme

@Composable
fun PolicyUpdateDialog(
    hasTermsUpdate: Boolean,
    termsVersion: Int,
    hasPrivacyUpdate: Boolean,
    privacyVersion: Int,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    onViewTerms: () -> Unit,
    onViewPrivacy: () -> Unit
) {
    val title = when {
        hasTermsUpdate && hasPrivacyUpdate -> "协议与隐私政策已更新"
        hasTermsUpdate -> "用户协议已更新"
        else -> "隐私政策已更新"
    }

    LiquidGlassAlertDialog(
        onDismissRequest = { },
        title = {
            Text(
                text = title,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = AppColors.TextPrimary
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "请查看本次更新内容后，确认是否继续使用 Lumi。",
                    fontSize = 14.sp,
                    lineHeight = 21.sp,
                    color = AppColors.TextSecondary
                )

                Spacer(modifier = Modifier.height(16.dp))

                if (hasTermsUpdate) {
                    PolicyUpdateItem(
                        title = "用户协议",
                        version = termsVersion,
                        subtitle = "了解本次服务规则更新",
                        iconContent = {
                            Icon(
                                imageVector = Icons.Outlined.Description,
                                contentDescription = null,
                                tint = AppColors.Accent
                            )
                        },
                        onClick = onViewTerms
                    )
                }

                if (hasTermsUpdate && hasPrivacyUpdate) {
                    Spacer(modifier = Modifier.height(10.dp))
                }

                if (hasPrivacyUpdate) {
                    PolicyUpdateItem(
                        title = "隐私政策",
                        version = privacyVersion,
                        subtitle = "了解个人信息保护更新",
                        iconContent = {
                            Icon(
                                imageVector = Icons.Outlined.PrivacyTip,
                                contentDescription = null,
                                tint = AppColors.Accent
                            )
                        },
                        onClick = onViewPrivacy
                    )
                }
            }
        },
        confirmButton = {
            LiquidGlassTextButton(
                text = "同意并继续",
                tintedColor = AppColors.Accent,
                onClick = onAccept
            )
        },
        dismissButton = {
            LiquidGlassTextButton(
                text = "不同意并退出",
                contentColor = AppColors.TextSecondary,
                onClick = onDecline
            )
        },
        // Keep the source content visible enough for the glass lens to refract it.
        contentScrimColor = AppColors.CardBg.copy(alpha = 0.22f)
    )
}

@Composable
private fun PolicyUpdateItem(
    title: String,
    version: Int,
    subtitle: String,
    iconContent: @Composable () -> Unit,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(18.dp)
    val isLiquidGlass = LocalAppTheme.current == "liquid_glass"

    if (isLiquidGlass) {
        LiquidGlassSurface(
            shape = shape,
            fallbackColor = AppColors.BgGray,
            contentScrimColor = AppColors.CardBg.copy(alpha = 0.10f),
            onClick = onClick,
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.CenterStart
        ) {
            PolicyUpdateItemContent(
                title = title,
                version = version,
                subtitle = subtitle,
                iconContent = iconContent
            )
        }
    } else {
        PolicyUpdateItemContent(
            title = title,
            version = version,
            subtitle = subtitle,
            iconContent = iconContent,
            modifier = Modifier
                .fillMaxWidth()
                .background(AppColors.BgGray.copy(alpha = 0.72f), shape)
                .clickable(onClick = onClick)
        )
    }
}

@Composable
private fun PolicyUpdateItemContent(
    title: String,
    version: Int,
    subtitle: String,
    iconContent: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .background(AppColors.Accent.copy(alpha = 0.12f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            iconContent()
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AppColors.TextPrimary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "v$version",
                    modifier = Modifier
                        .background(AppColors.Accent.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AppColors.Accent
                )
            }
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = subtitle,
                fontSize = 12.sp,
                color = AppColors.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.width(8.dp))
        Icon(
            imageVector = Icons.AutoMirrored.Outlined.ArrowForward,
            contentDescription = "查看更新内容",
            modifier = Modifier.size(18.dp),
            tint = AppColors.TextSecondary
        )
    }
}
