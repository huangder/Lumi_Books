package com.huangder.lumibooks.domain.model

/** Page presentation modes persisted for text-based readers. */
enum class ReaderPageTransition(val key: String) {
    NONE("none"),
    SLIDE("slide"),
    CONTINUOUS("continuous"),
    VERTICAL_PAGING("scroll"),
    FADE("fade"),
    CURL("curl"),
    WAVE("wave");

    companion object {
        fun isAvailableInEInk(key: String): Boolean =
            key == NONE.key || key == FADE.key || key == WAVE.key

        fun effectiveKey(preferredKey: String, eInkModeEnabled: Boolean): String =
            if (eInkModeEnabled && !isAvailableInEInk(preferredKey)) NONE.key else preferredKey

        fun fromKey(key: String?): ReaderPageTransition =
            entries.firstOrNull { it.key == key } ?: SLIDE

        fun normalizeKey(key: String?): String = fromKey(key).key
    }
}
