package com.huangder.lumibooks.domain.model

enum class AppUiIconStyle(
    val storedValue: String,
    val usesFilledGlyph: Boolean,
    val isColorful: Boolean
) {
    FILLED_COLORFUL("filled_colorful", usesFilledGlyph = true, isColorful = true),
    OUTLINE_COLORFUL("outline_colorful", usesFilledGlyph = false, isColorful = true),
    FILLED_MONOCHROME("filled_monochrome", usesFilledGlyph = true, isColorful = false),
    OUTLINE_MONOCHROME("outline_monochrome", usesFilledGlyph = false, isColorful = false);

    companion object {
        val DEFAULT = FILLED_COLORFUL

        fun fromStoredValue(value: String?): AppUiIconStyle =
            entries.firstOrNull { it.storedValue == value } ?: DEFAULT

        fun normalize(value: String?): String = fromStoredValue(value).storedValue
    }
}
