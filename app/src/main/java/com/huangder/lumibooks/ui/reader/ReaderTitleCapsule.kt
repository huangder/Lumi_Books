package com.huangder.lumibooks.ui.reader

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.huangder.lumibooks.ui.components.LiquidGlassSurface

@Composable
internal fun ReaderTitleCapsule(
    title: String,
    contentColor: Color,
    fallbackColor: Color,
    glassContentScrimColor: Color,
    isLiquidGlass: Boolean,
    modifier: Modifier = Modifier,
    contentAlignment: Alignment = Alignment.Center,
    textAlign: TextAlign = TextAlign.Center
) {
    Box(
        modifier = modifier.height(36.dp),
        contentAlignment = contentAlignment
    ) {
        if (isLiquidGlass) {
            LiquidGlassSurface(
                shape = RoundedCornerShape(50),
                fallbackColor = fallbackColor,
                contentScrimColor = glassContentScrimColor,
                modifier = Modifier
                    .height(32.dp)
                    .widthIn(max = 280.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = title,
                    fontSize = 12.sp,
                    color = contentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = textAlign,
                    modifier = Modifier.padding(horizontal = 14.dp)
                )
            }
        } else {
            Text(
                text = title,
                fontSize = 12.sp,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = textAlign
            )
        }
    }
}
