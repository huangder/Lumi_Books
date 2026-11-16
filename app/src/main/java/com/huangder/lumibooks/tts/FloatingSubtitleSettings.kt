package com.huangder.lumibooks.tts

enum class FloatingSubtitleStyle {
    /** Single line that follows the spoken position horizontally. */
    BAR,
    /** Desktop-lyrics layout: the current sentence wraps over two lines and the next one follows dimmed. */
    LYRICS;

    companion object {
        fun fromName(name: String?): FloatingSubtitleStyle =
            entries.firstOrNull { it.name == name } ?: LYRICS
    }
}

data class FloatingSubtitleSettings(
    val enabled: Boolean = true,
    val xFraction: Float = DEFAULT_X_FRACTION,
    val yFraction: Float = DEFAULT_Y_FRACTION,
    val backgroundColorHex: String = DEFAULT_BACKGROUND_COLOR,
    val backgroundOpacity: Float = DEFAULT_BACKGROUND_OPACITY,
    val cornerRadiusDp: Float = DEFAULT_CORNER_RADIUS_DP,
    val widthDp: Float = DEFAULT_WIDTH_DP,
    val heightDp: Float = DEFAULT_HEIGHT_DP,
    val style: FloatingSubtitleStyle = FloatingSubtitleStyle.LYRICS,
    val fontSizeSp: Float = DEFAULT_FONT_SIZE_SP,
    val highlightColorHex: String = DEFAULT_HIGHLIGHT_COLOR,
    val showNextLine: Boolean = true,
    /** Locked overlays ignore touches so the screen below stays usable; unlock from the notification. */
    val locked: Boolean = false
) {
    fun normalized(): FloatingSubtitleSettings = copy(
        xFraction = xFraction.coerceIn(0f, 1f),
        yFraction = yFraction.coerceIn(0f, 1f),
        backgroundColorHex = normalizeColor(backgroundColorHex),
        backgroundOpacity = backgroundOpacity.coerceIn(0f, 1f),
        cornerRadiusDp = cornerRadiusDp.coerceIn(MIN_CORNER_RADIUS_DP, MAX_CORNER_RADIUS_DP),
        widthDp = widthDp.coerceIn(MIN_WIDTH_DP, MAX_WIDTH_DP),
        heightDp = heightDp.coerceIn(MIN_HEIGHT_DP, MAX_HEIGHT_DP),
        fontSizeSp = fontSizeSp.coerceIn(MIN_FONT_SIZE_SP, MAX_FONT_SIZE_SP),
        highlightColorHex = normalizeColor(highlightColorHex, DEFAULT_HIGHLIGHT_COLOR)
    )

    companion object {
        const val DEFAULT_X_FRACTION = 0.5f
        const val DEFAULT_Y_FRACTION = 0.2f
        const val DEFAULT_BACKGROUND_COLOR = "#202124"
        const val DEFAULT_BACKGROUND_OPACITY = 0.72f
        const val DEFAULT_CORNER_RADIUS_DP = 28f
        const val DEFAULT_WIDTH_DP = 320f
        const val DEFAULT_HEIGHT_DP = 56f
        const val DEFAULT_FONT_SIZE_SP = 16f
        const val DEFAULT_HIGHLIGHT_COLOR = "#5AC8FA"

        const val MIN_CORNER_RADIUS_DP = 0f
        const val MAX_CORNER_RADIUS_DP = 32f
        const val MIN_WIDTH_DP = 160f
        const val MAX_WIDTH_DP = 720f
        const val MIN_HEIGHT_DP = 40f
        const val MAX_HEIGHT_DP = 120f
        const val MIN_FONT_SIZE_SP = 12f
        const val MAX_FONT_SIZE_SP = 28f

        fun normalizeColor(value: String, fallback: String = DEFAULT_BACKGROUND_COLOR): String {
            val raw = value.trim().removePrefix("#")
            if (raw.length != 6 || raw.any { it.digitToIntOrNull(16) == null }) {
                return fallback
            }
            return "#${raw.uppercase()}"
        }
    }
}
