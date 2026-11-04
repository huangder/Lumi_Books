package com.huangder.lumibooks.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.huangder.lumibooks.ui.animation.VisualMotion.tween
import com.huangder.lumibooks.ui.theme.AppColors
import com.huangder.lumibooks.ui.animation.AppEasing
import com.huangder.lumibooks.ui.theme.AppRadius
import com.huangder.lumibooks.ui.theme.AppRoundedCornerShape
import com.huangder.lumibooks.ui.theme.AppSpace
import com.huangder.lumibooks.ui.theme.AppType
import com.huangder.lumibooks.ui.theme.LocalEInkMode
import com.huangder.lumibooks.ui.theme.LocalMotionEnabled

/**
 * Centered placeholder for an empty page or list: optional glyph and title, an explanatory line
 * and an optional accent action. Fades in so it does not flash while content is still arriving.
 */
@Composable
internal fun EmptyState(
    message: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    title: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    val motion = LocalMotionEnabled.current && !LocalEInkMode.current
    val appear = remember { Animatable(if (motion) 0f else 1f) }
    LaunchedEffect(Unit) { appear.animateTo(1f, tween(240, easing = AppEasing.Decelerate)) }
    Column(
        modifier
            .padding(horizontal = AppSpace.xl)
            .graphicsLayer {
                alpha = appear.value
                translationY = (1f - appear.value) * 12.dp.toPx()
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = AppColors.TextSecondary, modifier = Modifier.size(48.dp))
            Spacer(Modifier.height(AppSpace.md))
        }
        if (title != null) {
            Text(
                title, color = AppColors.TextPrimary, fontSize = AppType.Body,
                fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(AppSpace.xs))
        }
        Text(message, color = AppColors.TextSecondary, fontSize = AppType.BodySmall, textAlign = TextAlign.Center)
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(AppSpace.md))
            LiquidGlassButton(
                onClick = onAction,
                shape = AppRoundedCornerShape(AppRadius.full),
                tintedColor = AppColors.Accent,
                contentColor = AppColors.OnAccent
            ) {
                Text(actionLabel, color = AppColors.OnAccent, fontSize = AppType.BodySmall, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
