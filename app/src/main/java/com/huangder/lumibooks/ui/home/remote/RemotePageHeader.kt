package com.huangder.lumibooks.ui.home.remote

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.huangder.lumibooks.R
import com.huangder.lumibooks.ui.components.LiquidGlassIconButton
import com.huangder.lumibooks.ui.icons.AppIcons
import com.huangder.lumibooks.ui.icons.directionalIcon
import com.huangder.lumibooks.ui.theme.AppColors
import com.huangder.lumibooks.ui.theme.AppSpace
import com.huangder.lumibooks.ui.theme.AppType

/** Header in the folder-books page style: glass back button, title with subtitle, actions. */
@Composable
internal fun RemotePageHeader(
    title: String,
    subtitle: String?,
    onBack: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(top = AppSpace.sm, bottom = AppSpace.sm)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AppSpace.lg),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RemoteHeaderIconButton(
                icon = directionalIcon(AppIcons.ArrowLeft, AppIcons.ArrowRight),
                description = stringResource(R.string.back),
                onClick = onBack
            )
            Spacer(Modifier.width(AppSpace.sm))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = AppColors.TextPrimary,
                    fontSize = AppType.Section,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        color = AppColors.TextSecondary,
                        fontSize = AppType.Caption,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(AppSpace.sm),
                verticalAlignment = Alignment.CenterVertically,
                content = actions
            )
        }
    }
}

@Composable
internal fun RemoteHeaderIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    size: Dp = 44.dp
) {
    LiquidGlassIconButton(
        imageVector = icon,
        contentDescription = description,
        onClick = onClick,
        enabled = enabled,
        size = size,
        iconSize = 20.dp,
        normalContainerColor = AppColors.BgGray,
        liquidContainerColor = AppColors.CardBg,
        liquidScrimColor = AppColors.CardBg.copy(alpha = 0.58f)
    )
}
