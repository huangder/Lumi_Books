package com.huangder.lumibooks.ui.theme

internal data class LumiArtworkPlacement(val x: Float, val y: Float, val width: Float, val height: Float)

internal data class LumiMainPlacement(val x: Float, val width: Float, val topY: Float, val bottomY: Float)

internal fun lumiMainPlacement(
    width: Float, height: Float, safeTop: Float, safeBottom: Float, density: Float, headerBottom: Float
): LumiMainPlacement {
    val margin = 16f * density
    val top = safeTop + margin
    val bottom = (height - safeBottom - margin).coerceAtLeast(top)
    val lowerStart = maxOf(safeTop + (height - safeTop - safeBottom) * 0.5f, headerBottom + margin)
    val size = minOf(width * 0.85f, 560f * density, (bottom - lowerStart).coerceAtLeast(0f))
    return LumiMainPlacement(width - size, size, top, bottom - size)
}

internal fun lumiSecondaryPlacement(
    width: Float, height: Float, safeTop: Float, safeBottom: Float, density: Float
): LumiArtworkPlacement {
    val available = (height - safeTop - safeBottom).coerceAtLeast(0f)
    // Alpha >= 16 bounds of the supplied artwork. Keep all the soft pixels around it.
    val subjectWidth = 937f
    val subjectHeight = 870f
    val scale = minOf(width * 0.85f / subjectWidth, 520f * density / subjectWidth, available * 0.55f / subjectHeight)
    return LumiArtworkPlacement(
        x = width * 0.5f - (61f + subjectWidth * 0.5f) * scale,
        y = safeTop + available * 0.68f - (647f + subjectHeight * 0.5f) * scale,
        width = 1024f * scale,
        height = 1536f * scale
    )
}
