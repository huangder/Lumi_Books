package com.huangder.lumibooks.domain.model

enum class ReaderPageCorner {
    TOP_LEFT,
    TOP_RIGHT,
    BOTTOM_LEFT,
    BOTTOM_RIGHT
}

enum class ReaderCornerContent(val key: String) {
    NONE("none"),
    CHAPTER_INFO("chapter_info"),
    BOOK_PROGRESS("book_progress"),
    PAGE_NUMBER("page_number"),
    BATTERY("battery");

    companion object {
        fun fromKey(key: String?): ReaderCornerContent =
            entries.firstOrNull { it.key == key } ?: NONE
    }
}

fun defaultReaderCornerContent(corner: ReaderPageCorner): ReaderCornerContent = when (corner) {
    ReaderPageCorner.TOP_LEFT -> ReaderCornerContent.CHAPTER_INFO
    ReaderPageCorner.TOP_RIGHT -> ReaderCornerContent.BATTERY
    ReaderPageCorner.BOTTOM_LEFT -> ReaderCornerContent.BOOK_PROGRESS
    ReaderPageCorner.BOTTOM_RIGHT -> ReaderCornerContent.PAGE_NUMBER
}
