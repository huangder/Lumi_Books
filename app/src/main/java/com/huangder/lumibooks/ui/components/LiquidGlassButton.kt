package com.huangder.lumibooks.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import com.huangder.lumibooks.ui.theme.AppRoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
    shape: Shape = AppRoundedCornerShape(50),
    tintedColor: Color? = null,
    prominentShadow: Boolean = false,
    contentColor: Color = if (tintedColor != null) AppColors.OnAccent else AppColors.TextPrimary,
    /** Optional elevation decoration for controls that share a local surface treatment. */
    decorationModifier: Modifier? = null,
    /** Smaller visual capsule for dense filter rows; other buttons keep their default size. */
    compact: Boolean = false,
    content: @Composable RowScope.() -> Unit
) {
    val isLiquidGlass = LocalAppTheme.current == "liquid_glass"

    if (!isLiquidGlass) {
        TextButton(
            onClick = onClick,
            enabled = enabled,
            modifier = modifier.heightIn(min = if (compact) 32.dp else 44.dp)
                .widthIn(min = if (compact) 0.dp else 72.dp),
            contentPadding = if (compact) PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                else ButtonDefaults.TextButtonContentPadding,
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

    // Keep the component's standard outline and add elevation appropriate to its prominence.
    val isDark = LocalIsDarkTheme.current
    val shadowDecoration = if (prominentShadow) {
        Modifier.shadow(
            elevation = 20.dp,
            shape = shape,
            clip = false,
            ambientColor = Color.Black.copy(alpha = if (isDark) 0.30f else 0.16f),
            spotColor = Color.Black.copy(alpha = if (isDark) 0.44f else 0.26f)
        )
    } else {
        Modifier.shadow(
            elevation = 10.dp,
            shape = shape,
            clip = false,
            ambientColor = Color.Black.copy(alpha = if (isDark) 0.24f else 0.12f),
            spotColor = Color.Black.copy(alpha = if (isDark) 0.34f else 0.18f)
        )
    }
    LiquidGlassSurface(
        controlEdge = true,
        shape = shape,
        fallbackColor = tintedColor ?: AppColors.CardBg,
        contentScrimColor = if (tintedColor == null) {
            AppColors.CardBg.copy(alpha = 0.24f)
        } else {
            Color.Transparent
        },
        tintColor = tintedColor,
        enabled = enabled,
        onClick = onClick,
        decorationModifier = decorationModifier ?: shadowDecoration,
        modifier = modifier
            .heightIn(min = if (compact) 32.dp else 44.dp)
            .widthIn(min = if (compact) 0.dp else 72.dp)
            .alpha(if (enabled) 1f else 0.48f)
    ) {
        CompositionLocalProvider(LocalContentColor provides contentColor) {
            Row(
                modifier = Modifier
                    .heightIn(min = if (compact) 32.dp else 40.dp)
                    .padding(horizontal = if (compact) 10.dp else 16.dp,
                        vertical = if (compact) 4.dp else 0.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
                content = content
            )
        }
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
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis
        )
    }
}
