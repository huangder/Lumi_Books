package com.huangder.lumibooks.domain.model

/** Glass outline treatment; does not change optics, shape or interaction. */
enum class LiquidGlassStyle(val storedValue: String) {
    DEFAULT("default"),
    ORIGINAL("original");

    companion object {
        fun fromStoredValue(value: String?): LiquidGlassStyle =
            entries.firstOrNull { it.storedValue == value } ?: DEFAULT
    }
}
