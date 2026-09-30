package com.huangder.lumibooks.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.huangder.lumibooks.R
import com.huangder.lumibooks.ui.icons.AppIcons
import com.huangder.lumibooks.ui.theme.AppColors
import com.huangder.lumibooks.ui.theme.AppType
import com.huangder.lumibooks.ui.theme.LocalAppTheme

/** Wrapping labels remain visible on narrow cards and at large font scales. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AnnotationTagChips(tags: List<String>, onEdit: (() -> Unit)? = null, modifier: Modifier = Modifier) {
    if (tags.isEmpty() && onEdit == null) return
    val shape = RoundedCornerShape(12.dp)
    val glass = LocalAppTheme.current == "liquid_glass"
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        tags.forEach { tag ->
            Row(
                Modifier.widthIn(max = 280.dp)
                    .then(if (onEdit != null) Modifier.heightIn(min = 44.dp) else Modifier)
                    .clip(shape)
                    .then(if (onEdit != null) Modifier.clickable(onClick = onEdit) else Modifier)
                    .background(AppColors.Accent.copy(alpha = if (glass) 0.14f else 0.08f), shape)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Icon(AppIcons.Tag, null, tint = AppColors.Accent, modifier = Modifier.size(13.dp))
                Text(tag, color = AppColors.TextPrimary, fontSize = AppType.Caption,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        if (onEdit != null) LiquidGlassIconButton(
            imageVector = if (tags.isEmpty()) AppIcons.Tag else AppIcons.PencilSimple,
            contentDescription = stringResource(if (tags.isEmpty()) R.string.annotation_tag_add else R.string.annotation_tags),
            onClick = onEdit, size = 44.dp, iconSize = 17.dp,
            contentColor = AppColors.TextSecondary,
            normalContainerColor = AppColors.BgGray
        )
    }
}
