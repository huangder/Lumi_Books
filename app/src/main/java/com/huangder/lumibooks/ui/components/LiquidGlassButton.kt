package com.huangder.lumibooks.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.huangder.lumibooks.ui.theme.AppColors
import com.huangder.lumibooks.ui.theme.AppType
import com.huangder.lumibooks.ui.theme.LocalAppTheme
import com.huangder.lumibooks.ui.theme.LocalIsDarkTheme

@Composable
fun LiquidGlassButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = RoundedCornerShape(50),
    tintedColor: Color? = null,
    prominentShadow: Boolean = false,
    contentColor: Color = if (tintedColor != null) AppColors.OnAccent else AppColors.TextPrimary,
    content: @Composable RowScope.() -> Unit
) {
    val isLiquidGlass = LocalAppTheme.current == "liquid_glass"
    val isDark = LocalIsDarkTheme.current

    if (!isLiquidGlass) {
        TextButton(
            onClick = onClick,
            enabled = enabled,
            modifier = modifier.heightIn(min = 44.dp).widthIn(min = 72.dp),
            shape = shape,
            colors = ButtonDefaults.textButtonColors(
                containerColor = tintedColor ?: Color.Transparent,
                contentColor = contentColor,
                disabledContainerColor = (tintedColor ?: AppColors.BgGray).copy(alpha = 0.38f),
                disabledContentColor = AppColors.TextSecondary
            ),
            content = content
        )
        return
    }

    val scrim = tintedColor?.let { color ->
        if (isDark) {
            lerp(color, Color.White, 0.16f).copy(alpha = 0.62f)
        } else {
            color.copy(alpha = 0.72f)
        }
    } ?: AppColors.CardBg.copy(alpha = 0.24f)
    val diffuseShadow = if (tintedColor == null) {
        Modifier.shadow(
            elevation = if (prominentShadow) 20.dp else 16.dp,
            shape = shape,
            clip = false,
            ambientColor = Color.Black.copy(
                alpha = if (isDark) {
                    if (prominentShadow) 0.24f else 0.18f
                } else {
                    if (prominentShadow) 0.15f else 0.10f
                }
            ),
            spotColor = Color.Black.copy(
                alpha = if (isDark) {
                    if (prominentShadow) 0.21f else 0.16f
                } else {
                    if (prominentShadow) 0.12f else 0.08f
                }
            )
        )
    } else {
        Modifier
    }
    LiquidGlassSurface(
        shape = shape,
        fallbackColor = tintedColor ?: AppColors.CardBg,
        contentScrimColor = scrim,
        enabled = enabled,
        onClick = onClick,
        effectPadding = 2.dp,
        decorationModifier = diffuseShadow,
        modifier = modifier
            .heightIn(min = 44.dp)
            .widthIn(min = 72.dp)
            .alpha(if (enabled) 1f else 0.48f)
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = 40.dp)
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
            content = content
        )
    }
}

@Composable
fun LiquidGlassTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tintedColor: Color? = null,
    prominentShadow: Boolean = false,
    contentColor: Color = if (tintedColor != null) AppColors.OnAccent else AppColors.TextPrimary
) {
    LiquidGlassButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        tintedColor = tintedColor,
        prominentShadow = prominentShadow,
        contentColor = contentColor
    ) {
        Text(
            text = text,
            color = contentColor,
            fontSize = AppType.BodySmall,
            fontWeight = FontWeight.SemiBold
        )
    }
}
