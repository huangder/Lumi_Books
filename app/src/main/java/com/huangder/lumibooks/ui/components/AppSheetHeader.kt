package com.huangder.lumibooks.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.huangder.lumibooks.R
import com.huangder.lumibooks.ui.icons.AppIcons
import com.huangder.lumibooks.ui.icons.directionalIcon
import com.huangder.lumibooks.ui.theme.*

/** Close ends a task; Back is reserved for a previous step within that task. */
@Composable
fun AppSheetHeader(
    title: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    onConfirm: (() -> Unit)? = null,
    confirmEnabled: Boolean = true,
    backContentDescription: String = stringResource(R.string.back),
    actions: @Composable RowScope.() -> Unit = {}
) {
    Row(modifier.fillMaxWidth().heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically) {
        LiquidGlassIconButton(
            imageVector = if (onBack != null) directionalIcon(AppIcons.ArrowLeft, AppIcons.ArrowRight) else AppIcons.X,
            contentDescription = if (onBack != null) backContentDescription else stringResource(R.string.close),
            onClick = onBack ?: onClose, size = 44.dp, iconSize = 20.dp
        )
        Text(title, color = AppColors.TextPrimary, fontSize = AppType.Section,
            fontWeight = FontWeight.Bold, fontFamily = resolveAppFontFamily(KaiTi),
            modifier = Modifier.weight(1f).padding(horizontal = AppSpace.sm))
        actions()
        if (onConfirm != null) {
            LiquidGlassIconButton(imageVector = AppIcons.Check,
                contentDescription = stringResource(R.string.confirm), onClick = onConfirm,
                enabled = confirmEnabled, size = 44.dp, iconSize = 20.dp,
                normalContainerColor = AppColors.Accent, liquidContainerColor = AppColors.Accent,
                liquidScrimColor = AppColors.Accent.copy(alpha = 0.85f), contentColor = AppColors.OnAccent)
        }
    }
}
