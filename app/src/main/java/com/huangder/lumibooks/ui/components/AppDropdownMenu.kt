package com.huangder.lumibooks.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.DropdownMenu
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import com.huangder.lumibooks.ui.theme.AppColors
import com.huangder.lumibooks.ui.theme.AppRadius
import com.huangder.lumibooks.ui.theme.AppRoundedCornerShape
import com.huangder.lumibooks.ui.theme.LocalIsDarkTheme

/**
 * Pull-down menu used where the anchored liquid glass menu is unavailable (non-glass themes).
 * Keeps every fallback menu on the same card shape, surface and hairline outline.
 */
@Composable
internal fun AppDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    offset: DpOffset = DpOffset(0.dp, 0.dp),
    properties: PopupProperties = PopupProperties(focusable = true),
    content: @Composable ColumnScope.() -> Unit
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        offset = offset,
        properties = properties,
        shape = AppRoundedCornerShape(AppRadius.lg),
        containerColor = AppColors.CardBg,
        border = BorderStroke(0.5.dp, AppColors.TextPrimary.copy(alpha = if (LocalIsDarkTheme.current) 0.14f else 0.08f)),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        content = content
    )
}
