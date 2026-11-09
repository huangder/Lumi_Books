package com.huangder.lumibooks.ui.components

import com.huangder.lumibooks.ui.icons.ControlIcon

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.huangder.lumibooks.ui.theme.AppColors
import com.huangder.lumibooks.ui.theme.LocalAppTheme
import com.huangder.lumibooks.ui.theme.LocalIsDarkTheme

@Composable
fun LiquidGlassIconButton(
    imageVector: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    iconSize: Dp = 20.dp,
    contentColor: Color = AppColors.TextPrimary,
    normalContainerColor: Color = Color.Transparent,
    liquidContainerColor: Color = AppColors.CardBg,
    liquidScrimColor: Color? = null,
    /** Retained for source compatibility; toolbar parents own the button's position. */
    settingsBackButton: Boolean = false,
    /** Disable the liquid-glass press stretch/highlight while keeping the click action. */
    pressFeedbackEnabled: Boolean = true,
    enabled: Boolean = true,
    forceFallback: Boolean = false
) {
    val isLiquidGlass = LocalAppTheme.current == "liquid_glass" && !forceFallback
    val isDark = LocalIsDarkTheme.current
    if (!isLiquidGlass) {
        IconButton(
            onClick = onClick,
            enabled = enabled,
            modifier = modifier.size(size),
            colors = IconButtonDefaults.iconButtonColors(
                containerColor = normalContainerColor,
                contentColor = contentColor,
                disabledContainerColor = normalContainerColor.copy(alpha = 0.38f),
                disabledContentColor = contentColor.copy(alpha = 0.38f)
            )
        ) {
            ControlIcon(
                imageVector = imageVector,
                contentDescription = contentDescription,
                modifier = Modifier.size(iconSize)
            )
        }
        return
    }

    val resolvedScrim = liquidScrimColor?.let { color ->
        if (isDark) {
            lerp(color, Color.White, 0.14f).copy(
                alpha = (color.alpha * 0.84f).coerceIn(0.52f, 0.72f)
            )
        } else {
            color
        }
    }
    LiquidGlassSurface(
        controlEdge = true,
        shape = CircleShape,
        fallbackColor = liquidContainerColor,
        // Keep the sampled backdrop visible, but always place the button's own
        // surface color over it so icon buttons do not become raw background cut-outs.
        contentScrimColor = resolvedScrim ?: liquidContainerColor.copy(
            alpha = 0.24f
        ),
        interactive = pressFeedbackEnabled,
        enabled = enabled,
        onClick = onClick,
        modifier = modifier.size(size),
        decorationModifier = Modifier
    ) {
        ControlIcon(
            imageVector = imageVector,
            contentDescription = contentDescription,
            tint = contentColor,
            modifier = Modifier.size(iconSize)
        )
    }
}
