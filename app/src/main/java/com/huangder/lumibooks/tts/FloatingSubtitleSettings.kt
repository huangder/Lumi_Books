package com.huangder.lumibooks.tts

data class FloatingSubtitleSettings(
    val enabled: Boolean = true,
    val xFraction: Float = DEFAULT_X_FRACTION,
    val yFraction: Float = DEFAULT_Y_FRACTION,
    val backgroundColorHex: String = DEFAULT_BACKGROUND_COLOR,
    val backgroundOpacity: Float = DEFAULT_BACKGROUND_OPACITY,
    val cornerRadiusDp: Float = DEFAULT_CORNER_RADIUS_DP,
    val widthDp: Float = DEFAULT_WIDTH_DP,
    val heightDp: Float = DEFAULT_HEIGHT_DP
) {
    fun normalized(): FloatingSubtitleSettings = copy(
        xFraction = xFraction.coerceIn(0f, 1f),
        yFraction = yFraction.coerceIn(0f, 1f),
        backgroundColorHex = normalizeColor(backgroundColorHex),
        backgroundOpacity = backgroundOpacity.coerceIn(0f, 1f),
        cornerRadiusDp = cornerRadiusDp.coerceIn(MIN_CORNER_RADIUS_DP, MAX_CORNER_RADIUS_DP),
        widthDp = widthDp.coerceIn(MIN_WIDTH_DP, MAX_WIDTH_DP),
        heightDp = heightDp.coerceIn(MIN_HEIGHT_DP, MAX_HEIGHT_DP)
    )

    companion object {
        const val DEFAULT_X_FRACTION = 0.5f
        const val DEFAULT_Y_FRACTION = 0.2f
        const val DEFAULT_BACKGROUND_COLOR = "#202124"
        const val DEFAULT_BACKGROUND_OPACITY = 0.72f
        const val DEFAULT_CORNER_RADIUS_DP = 8f
        const val DEFAULT_WIDTH_DP = 320f
        const val DEFAULT_HEIGHT_DP = 56f

        const val MIN_CORNER_RADIUS_DP = 0f
        const val MAX_CORNER_RADIUS_DP = 32f
        const val MIN_WIDTH_DP = 160f
        const val MAX_WIDTH_DP = 720f
        const val MIN_HEIGHT_DP = 40f
        const val MAX_HEIGHT_DP = 120f

        fun normalizeColor(value: String): String {
            val raw = value.trim().removePrefix("#")
            if (raw.length != 6 || raw.any { it.digitToIntOrNull(16) == null }) {
                return DEFAULT_BACKGROUND_COLOR
            }
            return "#${raw.uppercase()}"
        }
    }
}
