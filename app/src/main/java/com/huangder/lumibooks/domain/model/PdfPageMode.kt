package com.huangder.lumibooks.domain.model

/** PDF page presentation modes persisted in the global reader preferences. */
enum class PdfPageMode(val key: String) {
    VERTICAL_SCROLL("vertical"),
    VERTICAL_PAGING("vertical_paging"),
    HORIZONTAL_PAGING("horizontal");

    fun next(): PdfPageMode = when (this) {
        VERTICAL_SCROLL -> VERTICAL_PAGING
        VERTICAL_PAGING -> HORIZONTAL_PAGING
        HORIZONTAL_PAGING -> VERTICAL_SCROLL
    }

    companion object {
        fun fromKey(key: String?): PdfPageMode =
            entries.firstOrNull { it.key == key } ?: VERTICAL_SCROLL

        fun normalizeKey(key: String?): String = fromKey(key).key
    }
}
