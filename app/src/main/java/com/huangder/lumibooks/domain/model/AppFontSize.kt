package com.huangder.lumibooks.domain.model

/** Application chrome only; book typography has its own settings. */
enum class AppFontSize(val storedValue: String, val scale: Float) {
    EXTRA_SMALL("extra_small", 0.80f),
    SMALL("small", 0.90f),
    STANDARD("standard", 1f),
    MEDIUM("medium", 1.10f),
    LARGE("large", 1.20f),
    EXTRA_LARGE("extra_large", 1.35f);

    companion object {
        fun fromStoredValue(value: String?): AppFontSize =
            entries.firstOrNull { it.storedValue == value } ?: STANDARD
    }
}
