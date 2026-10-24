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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.huangder.lumibooks.ui.theme.AppColors
import com.huangder.lumibooks.ui.theme.AppType
import com.huangder.lumibooks.ui.theme.LocalAppTheme

@Composable
fun LiquidGlassButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = AppRoundedCornerShape(50),
    tintedColor: Color? = null,
    /** Retained for source compatibility; transparent buttons use their glass rim for depth. */
    prominentShadow: Boolean = false,
    contentColor: Color = if (tintedColor != null) AppColors.OnAccent else AppColors.TextPrimary,
    /** Optional decoration for controls that share a local surface treatment. */
    decorationModifier: Modifier? = null,
    /** Smaller visual capsule for dense filter rows; other buttons keep their default size. */
    compact: Boolean = false,
    content: @Composable RowScope.() -> Unit
) {
    val isLiquidGlass = LocalAppTheme.current == "liquid_glass"
    val textHeight = with(LocalDensity.current) { AppType.BodySmall.toDp() }
    val minHeight = maxOf(if (compact) 32.dp else 44.dp, textHeight * 1.5f + if (compact) 8.dp else 12.dp)
    val minWidth = if (compact) 0.dp else maxOf(72.dp, textHeight * 2f + 24.dp)
    val horizontalPadding = if (compact) 10.dp else 12.dp
    val verticalPadding = if (compact) 4.dp else 6.dp

    if (!isLiquidGlass) {
        TextButton(
            onClick = onClick,
            enabled = enabled,
            modifier = modifier.heightIn(min = minHeight).widthIn(min = minWidth),
            contentPadding = PaddingValues(horizontal = horizontalPadding, vertical = verticalPadding),
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
        decorationModifier = decorationModifier ?: Modifier,
        modifier = modifier
            .heightIn(min = minHeight)
            .widthIn(min = minWidth)
            .alpha(if (enabled) 1f else 0.48f)
    ) {
        CompositionLocalProvider(LocalContentColor provides contentColor) {
            Row(
                modifier = Modifier
                    .heightIn(min = minHeight)
                    .padding(horizontal = horizontalPadding, vertical = verticalPadding),
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
