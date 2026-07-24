package com.huangder.lumibooks.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
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
    enabled: Boolean = true
) {
    val isLiquidGlass = LocalAppTheme.current == "liquid_glass"
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
    val decorationModifier = if (settingsBackButton) {
        Modifier
            .shadow(
                elevation = 16.dp,
                shape = CircleShape,
                clip = false,
                ambientColor = Color.Black.copy(alpha = if (isDark) 0.18f else 0.07f),
                spotColor = Color.Black.copy(alpha = if (isDark) 0.16f else 0.10f)
            )
            .then(
                if (useWhiteSettingsSurface) {
                    Modifier.border(
                        width = 1.dp,
                        brush = Brush.verticalGradient(
                            listOf(
                                Color.White,
                                Color.Black.copy(alpha = 0.07f)
                            )
                        ),
                        shape = CircleShape
                    )
                } else {
                    Modifier
                }
            )
    } else {
        Modifier
    }
    LiquidGlassSurface(
        shape = CircleShape,
        fallbackColor = if (useWhiteSettingsSurface) Color.White else liquidContainerColor,
        contentScrimColor = resolvedScrim ?: if (isDark) {
            Color.White.copy(alpha = 0.16f)
        } else {
            Color.Black.copy(alpha = 0.08f)
        },
        enabled = enabled,
        onClick = onClick,
        effectPadding = 2.dp,
        decorationModifier = decorationModifier,
        modifier = resolvedModifier.size(size)
    ) {
        Icon(
            imageVector = imageVector,
            contentDescription = contentDescription,
            tint = contentColor,
            modifier = Modifier.size(iconSize)
        )
    }
}
