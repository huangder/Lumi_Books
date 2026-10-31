package com.huangder.lumibooks.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.huangder.lumibooks.ui.theme.*

@Composable
internal fun TagTextField(
    value: String,
    onValueChange: (String) -> Unit,
    hint: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    containerColor: Color = AppColors.BgGray,
    leadingIcon: ImageVector? = null,
    trailingContent: (@Composable () -> Unit)? = null
) {
    var focused by remember { mutableStateOf(false) }
    val shape = AppRoundedCornerShape(AppRadius.lg)
    BasicTextField(
        value = value, onValueChange = onValueChange, enabled = enabled, singleLine = true,
        textStyle = LocalTextStyle.current.copy(color = AppColors.TextPrimary, fontSize = AppType.Body),
        cursorBrush = SolidColor(AppColors.Accent),
        modifier = modifier.fillMaxWidth().semantics { contentDescription = hint }
            .onFocusChanged { focused = it.isFocused }
            .clip(shape).lumiCardSurface(color = containerColor, shape = shape)
            .border(1.dp, if (focused) AppColors.Accent.copy(alpha = 0.45f) else Color.Transparent, shape),
        decorationBox = { inner ->
            Row(Modifier.heightIn(min = 54.dp).padding(start = 16.dp, end = if (trailingContent == null) 16.dp else 4.dp)
                .padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                leadingIcon?.let { Icon(it, null, tint = AppColors.TextSecondary, modifier = Modifier.size(20.dp)) }
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) Text(hint, color = AppColors.TextSecondary, fontSize = AppType.Body,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    inner()
                }
                trailingContent?.invoke()
            }
        }
    )
}
