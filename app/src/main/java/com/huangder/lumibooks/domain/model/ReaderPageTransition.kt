package com.huangder.lumibooks.domain.model

/** Page presentation modes persisted for text-based readers. */
enum class ReaderPageTransition(val key: String) {
    SLIDE("slide"),
    CONTINUOUS("continuous"),
    VERTICAL_PAGING("scroll"),
    FADE("fade"),
    CURL("curl");

    companion object {
        fun fromKey(key: String?): ReaderPageTransition =
            entries.firstOrNull { it.key == key } ?: SLIDE

        fun normalizeKey(key: String?): String = fromKey(key).key
    }
}
