package com.huangder.lumibooks.ui.components

import android.graphics.Paint
import android.graphics.RectF
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.huangder.lumibooks.ui.theme.AppColors
import com.huangder.lumibooks.ui.theme.LocalIsDarkTheme
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeChild

data class TabItem(
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

val tabs = listOf(
    TabItem(Icons.Filled.Home, Icons.Outlined.Home),
    TabItem(Icons.Filled.MenuBook, Icons.Outlined.MenuBook),
    TabItem(Icons.Filled.BarChart, Icons.Outlined.BarChart)
)

@Composable
fun FloatingTabBar(
    selectedIndex: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    hazeState: HazeState? = null
) {
    val isDark = LocalIsDarkTheme.current
    val glassShape = CircleShape
    val glassBrush = if (isDark) {
        Brush.verticalGradient(
            colors = listOf(
                Color(0xCC2C2C2E),
                Color(0xB01C1C1E)
            )
        )
    } else {
        Brush.verticalGradient(
            colors = listOf(
                Color(0xDFFFFFFF),
                Color(0xB8FFFFFF)
            )
        )
    }
    val borderBrush = if (isDark) {
        Brush.verticalGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.34f),
                Color.White.copy(alpha = 0.10f)
            )
        )
    } else {
        Brush.verticalGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.96f),
                Color.White.copy(alpha = 0.30f)
            )
        )
    }
    val hazeModifier = hazeState?.let { state ->
        Modifier.hazeChild(state) {
            backgroundColor = if (isDark) Color(0xFF1C1C1E) else Color.White
            tints = listOf(
                HazeTint(
                    if (isDark) Color(0x521C1C1E) else Color(0x5CFFFFFF)
                )
            )
            blurRadius = 36.dp
            noiseFactor = 0.08f
            fallbackTint = HazeTint(
                if (isDark) Color(0xC81C1C1E) else Color(0xCCFFFFFF)
            )
        }
    } ?: Modifier
    val shadowColor = if (isDark) {
        Color.White.copy(alpha = 0.10f)
    } else {
        Color.Black.copy(alpha = 0.14f)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 80.dp, vertical = 14.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .drawBehind {
                    val shadowRadius = 28.dp.toPx()
                    val cornerRadius = size.height / 2f
                    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.White.copy(alpha = 0.01f).toArgb()
                        setShadowLayer(shadowRadius, 0f, 0f, shadowColor.toArgb())
                    }
                    drawIntoCanvas { canvas ->
                        canvas.nativeCanvas.drawRoundRect(
                            RectF(0f, 0f, size.width, size.height),
                            cornerRadius,
                            cornerRadius,
                            paint
                        )
                    }
                }
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .clip(glassShape)
                .then(hazeModifier)
                .background(glassBrush)
                .border(width = 0.8.dp, brush = borderBrush, shape = glassShape)
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            tabs.forEachIndexed { index, tab ->
                TabItemView(
                    tab = tab,
                    isSelected = index == selectedIndex,
                    onClick = { onTabSelected(index) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun TabItemView(
    tab: TabItem,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(16.dp))
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(
                    if (isSelected) AppColors.Accent.copy(alpha = 0.16f)
                    else Color.Transparent
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isSelected) tab.selectedIcon else tab.unselectedIcon,
                contentDescription = null,
                tint = if (isSelected) AppColors.TextPrimary else AppColors.TextSecondary,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}
