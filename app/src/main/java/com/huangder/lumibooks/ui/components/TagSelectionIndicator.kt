package com.huangder.lumibooks.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.huangder.lumibooks.ui.icons.AppIcons
import com.huangder.lumibooks.ui.theme.AppColors
import com.huangder.lumibooks.ui.theme.AppRoundedCornerShape
import com.huangder.lumibooks.ui.theme.LocalEInkMode
import com.huangder.lumibooks.ui.theme.LocalMotionEnabled
import com.huangder.lumibooks.ui.animation.VisualMotion

@Composable
internal fun TagSelectionIndicator(
    checked: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    indeterminate: Boolean = false
) {
    val active = checked || indeterminate
    val motion = LocalMotionEnabled.current && !LocalEInkMode.current
    val background by animateColorAsState(
        if (active) AppColors.Accent else AppColors.BgGray,
        animationSpec = if (motion) VisualMotion.tween(180) else snap(), label = "tag-selection-fill"
    )
    val border by animateColorAsState(
        if (active) AppColors.Accent else AppColors.TextSecondary.copy(alpha = 0.35f),
        animationSpec = if (motion) VisualMotion.tween(180) else snap(), label = "tag-selection-border"
    )
    val shape = AppRoundedCornerShape(7.dp)
    Box(
        modifier.size(22.dp).alpha(if (enabled) 1f else 0.4f).clip(shape)
            .background(background)
            .border(1.dp, border, shape),
        contentAlignment = Alignment.Center
    ) {
        Crossfade(
            targetState = if (indeterminate) 2 else if (checked) 1 else 0,
            animationSpec = if (motion) VisualMotion.tween(140) else snap(),
            label = "tag-selection-symbol"
        ) { state ->
            Box(Modifier.size(15.dp), contentAlignment = Alignment.Center) {
                if (state == 2) {
                    Box(Modifier.width(10.dp).height(2.dp).background(AppColors.OnAccent, AppRoundedCornerShape(1.dp)))
                } else if (state == 1) {
                    Icon(AppIcons.Check, null, tint = AppColors.OnAccent, modifier = Modifier.size(15.dp))
                }
            }
        }
    }
}
