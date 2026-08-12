package com.huangder.lumibooks.ui.reader

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

internal const val DefaultReaderHighlightColor = "#D6C58D"
internal const val DefaultReaderHighlightColorWithAlpha = "#40D6C58D"

private val defaultPalette = listOf(
    DefaultReaderHighlightColor to Color(0xFFD6C58D),
    "#CFA09A" to Color(0xFFCFA09A),
    "#A7B59D" to Color(0xFFA7B59D),
    "#9DAFC1" to Color(0xFF9DAFC1),
    "#B2A198" to Color(0xFFB2A198),
    "#AFB0AC" to Color(0xFFAFB0AC)
)

/** 当前高亮颜色色板（Compose 可观察），空列表时使用默认色板 */
internal var ReaderHighlightPalette: List<Pair<String, Color>> by mutableStateOf(defaultPalette)
    private set

/** 用自定义颜色列表更新色板，空列表恢复默认 */
internal fun updateHighlightPalette(customColors: List<String>) {
    ReaderHighlightPalette = if (customColors.isEmpty()) {
        defaultPalette
    } else {
        customColors.map { hex ->
            try {
                val color = Color(android.graphics.Color.parseColor(hex))
                hex to color
            } catch (_: Exception) {
                hex to Color(0xFFD6C58D)
            }
        }
    }
}
