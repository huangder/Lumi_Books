package com.huangder.lumibooks.ui.components

import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
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
            Icon(
                imageVector = imageVector,
                contentDescription = contentDescription,
                modifier = Modifier.size(iconSize)
            )
        }
        return
    }

    val useWhiteSettingsSurface = settingsBackButton && !isDark
    val resolvedScrim = if (useWhiteSettingsSurface) {
        Color.White.copy(alpha = 0.96f)
    } else liquidScrimColor?.let { color ->
        if (isDark) {
            lerp(color, Color.White, 0.14f).copy(
                alpha = (color.alpha * 0.84f).coerceIn(0.52f, 0.72f)
            )
        } else {
            color
        }
    }
    val resolvedModifier = if (settingsBackButton) {
        modifier.offset(x = 8.dp)
    } else {
        modifier
    }
    LiquidGlassSurface(
        shape = CircleShape,
        fallbackColor = if (useWhiteSettingsSurface) Color.White else liquidContainerColor,
        // Keep the sampled backdrop visible, but always place the button's own
        // surface color over it so icon buttons do not become raw background cut-outs.
        contentScrimColor = resolvedScrim ?: liquidContainerColor.copy(
            alpha = if (isDark) 0.60f else 0.72f
        ),
        interactive = pressFeedbackEnabled,
        enabled = enabled,
        onClick = onClick,
        modifier = resolvedModifier.size(size),
        decorationModifier = Modifier.shadow(
            elevation = 10.dp,
            shape = CircleShape,
            clip = false,
            ambientColor = Color.Black.copy(alpha = if (isDark) 0.24f else 0.12f),
            spotColor = Color.Black.copy(alpha = if (isDark) 0.34f else 0.18f)
        )
    ) {
        Icon(
            imageVector = imageVector,
            contentDescription = contentDescription,
            tint = contentColor,
            modifier = Modifier.size(iconSize)
        )
    }
}
