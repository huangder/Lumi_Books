package com.huangder.lumibooks.ui.reader

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import com.huangder.lumibooks.domain.model.HighlightPalette

internal const val DefaultReaderHighlightColor = "#D6C58D"
internal const val DefaultReaderHighlightColorWithAlpha = "#40D6C58D"

private const val HighlightSlotPrefix = "@highlight-slot:"

private val defaultPaletteColors = listOf(
    DefaultReaderHighlightColor,
    "#CFA09A",
    "#A7B59D",
    "#9DAFC1",
    "#B2A198",
    "#AFB0AC"
)

private val defaultPalette = listOf(
    Color(0xFFD6C58D),
    Color(0xFFCFA09A),
    Color(0xFFA7B59D),
    Color(0xFF9DAFC1),
    Color(0xFFB2A198),
    Color(0xFFAFB0AC)
).mapIndexed { index, color -> defaultPaletteColors[index] to color }

private var activePaletteColors: List<String> = defaultPaletteColors
private var highlightSlotsByRgb: Map<String, Int> = buildMap {
    defaultPaletteColors.forEachIndexed { index, color -> rgbKey(color)?.let { put(it, index) } }
}

private fun rgbKey(color: String): String? {
    val value = color.trim().removePrefix("#")
    return when (value.length) {
        3 -> value.map { "$it$it" }.joinToString("")
        4 -> value.drop(1).map { "$it$it" }.joinToString("")
        6 -> value
        8 -> value.drop(2)
        else -> null
    }?.takeIf { hex -> hex.all { it.digitToIntOrNull(16) != null } }?.uppercase()
}

private fun storedAlpha(color: String): String? {
    val value = color.trim().removePrefix("#")
    return when (value.length) {
        4 -> "${value.first()}${value.first()}"
        8 -> value.take(2)
        else -> null
    }?.uppercase()
}

private fun resolvedPaletteColors(colors: List<String?>): List<String>? {
    val normalized = colors.take(6).let { it + List(6 - it.size) { null } }
    val fallback = normalized.filterNotNull().lastOrNull() ?: return null
    return List(6) { index -> normalized[index] ?: fallback }
}

private fun parseSlotReference(color: String): Pair<Int, String?>? {
    if (!color.startsWith(HighlightSlotPrefix)) return null
    val parts = color.removePrefix(HighlightSlotPrefix).split(':', limit = 2)
    val slot = parts.firstOrNull()?.toIntOrNull()?.takeIf { it in 0..5 } ?: return null
    val alpha = parts.getOrNull(1)?.takeIf { value ->
        value.length == 2 && value.all { it.digitToIntOrNull(16) != null }
    }?.uppercase()
    return slot to alpha
}

internal fun readerHighlightColorReference(slot: Int, noteType: String): String {
    val safeSlot = slot.coerceIn(0, 5)
    val alpha = if (noteType == "underline") "FF" else "40"
    return "$HighlightSlotPrefix$safeSlot:$alpha"
}

internal fun readerHighlightSlotForColor(color: String?): Int? {
    if (color == null) return null
    parseSlotReference(color)?.let { return it.first }
    return rgbKey(color)?.let(highlightSlotsByRgb::get)
}

/** Resolve a stored slot reference or legacy literal color against the active palette. */
internal fun resolveReaderHighlightColor(color: String): String {
    val reference = parseSlotReference(color)
    val slot = reference?.first ?: readerHighlightSlotForColor(color) ?: return color
    val mappedRgb = rgbKey(activePaletteColors.getOrElse(slot) { defaultPaletteColors[slot] })
        ?: return color
    val alpha = reference?.second ?: storedAlpha(color)
    return if (alpha == null) "#$mappedRgb" else "#$alpha$mappedRgb"
}

/** 当前高亮颜色色板（Compose 可观察），空列表时使用默认色板 */
internal var ReaderHighlightPalette: List<Pair<String, Color>> by mutableStateOf(defaultPalette)
    private set

/** 更新全部色卡及当前色卡；空槽位使用该色卡最后一个已设置颜色。 */
internal fun updateHighlightPalettes(palettes: List<HighlightPalette>, activeId: String?) {
    highlightSlotsByRgb = buildMap {
        defaultPaletteColors.forEachIndexed { index, color ->
            rgbKey(color)?.let { putIfAbsent(it, index) }
        }
        palettes.forEach { palette ->
            palette.normalizedColors.forEachIndexed { index, color ->
                color?.let(::rgbKey)?.let { putIfAbsent(it, index) }
            }
        }
    }

    val activePalette = palettes.firstOrNull { it.id == activeId }
        ?: palettes.firstOrNull()?.takeIf {
            it.id == "legacy" && (activeId == null || activeId == "legacy")
        }
    activePaletteColors = activePalette
        ?.let { resolvedPaletteColors(it.normalizedColors) }
        ?: defaultPaletteColors

    ReaderHighlightPalette = activePaletteColors.mapIndexed { index, hex ->
        val color = try {
            Color(android.graphics.Color.parseColor(hex))
        } catch (_: Exception) {
            defaultPalette[index].second
        }
        hex to color
    }
}
