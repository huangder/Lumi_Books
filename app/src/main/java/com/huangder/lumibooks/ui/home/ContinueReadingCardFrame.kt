package com.huangder.lumibooks.ui.home

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.huangder.lumibooks.ui.animation.cardPressEffect
import com.huangder.lumibooks.ui.components.lumiCardSurface
import com.huangder.lumibooks.ui.theme.AppColors
import com.huangder.lumibooks.ui.theme.AppRadius
import com.huangder.lumibooks.ui.theme.AppRoundedCornerShape
import com.huangder.lumibooks.ui.theme.cardOutline
import com.huangder.lumibooks.ui.theme.LocalEInkMode
import com.huangder.lumibooks.ui.theme.LocalIsDarkTheme
import com.huangder.lumibooks.ui.theme.LocalLiquidGlassCapability
import com.huangder.lumibooks.ui.theme.LocalReduceTransparency
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur

/** Blur only the page behind a default cover; the cover, labels and controls stay sharp. */
@Composable
internal fun ContinueReadingCardFrame(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    frostedBackdrop: Backdrop? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val shape = AppRoundedCornerShape(AppRadius.lg)
    val useFrostedSurface = frostedBackdrop != null && LocalLiquidGlassCapability.current.supported &&
        !LocalEInkMode.current && !LocalReduceTransparency.current
    val dark = LocalIsDarkTheme.current
    val tint = if (dark) Color(0xFF181A20).copy(alpha = 0.32f)
        else Color.White.copy(alpha = 0.22f)
    val surface = if (useFrostedSurface) {
        Modifier.drawBackdrop(
            backdrop = requireNotNull(frostedBackdrop),
            shape = { shape },
            effects = { blur(20.dp.toPx()) },
            highlight = null,
            onDrawSurface = { drawRect(tint) }
        ).border(0.5.dp, (if (dark) Color.Black else Color.White).copy(alpha = 0.70f), shape)
    } else {
        Modifier.lumiCardSurface(shape = shape)
    }
    Box(
        modifier.fillMaxWidth()
            // Transform the surface and its mask together with the cover. Scaling only the
            // content exposes the unscaled white card underneath along all four edges.
            .cardPressEffect()
            .shadow(12.dp, shape, ambientColor = AppColors.CardShadow, spotColor = AppColors.CardShadow)
            .cardOutline(shape)
            .clip(shape)
            .then(surface)
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }, onClick = onClick),
        content = content
    )
}
