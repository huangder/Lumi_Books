package com.huangder.lumibooks.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.huangder.lumibooks.ui.theme.AppColors
import com.huangder.lumibooks.ui.theme.LocalIsDarkTheme
import com.huangder.lumibooks.ui.theme.LocalLumiBackgroundBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur

@Composable
fun Modifier.lumiCardSurface(
    color: Color = AppColors.CardBg,
    shape: Shape = RectangleShape,
    controlEdge: Boolean = false
): Modifier {
    val backdrop = LocalLumiBackgroundBackdrop.current ?: return background(color, shape)
    val tint = if (LocalIsDarkTheme.current) Color(0xFF151518).copy(alpha = 0.42f)
        else Color.White.copy(alpha = 0.42f)
    return drawBackdrop(
        backdrop = backdrop,
        shape = { shape },
        effects = { blur(8.dp.toPx()) },
        highlight = null,
        onDrawSurface = { drawRect(tint) }
    ).then(
        if (controlEdge) Modifier.liquidGlassControlEdge(
            shape, color, LocalIsDarkTheme.current, LocalLiquidGlassControlEdgeForceCanvas.current
        ) else Modifier.border(0.5.dp, Color.White.copy(alpha = 0.38f), shape)
    )
}
