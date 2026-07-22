package com.huangder.lumibooks.domain.model

enum class ReaderPageCorner {
    TOP_LEFT,
    TOP_RIGHT,
    BOTTOM_LEFT,
    BOTTOM_RIGHT
}

enum class ReaderEdgeTapAction {
    PREVIOUS_PAGE,
    NEXT_PAGE
}

enum class ReaderEdgeTapMode(
    val key: String,
    val leftAction: ReaderEdgeTapAction,
    val rightAction: ReaderEdgeTapAction
) {
    LEFT_PREVIOUS_RIGHT_NEXT(
        key = "left_previous_right_next",
        leftAction = ReaderEdgeTapAction.PREVIOUS_PAGE,
        rightAction = ReaderEdgeTapAction.NEXT_PAGE
    ),
    LEFT_NEXT_RIGHT_PREVIOUS(
        key = "left_next_right_previous",
        leftAction = ReaderEdgeTapAction.NEXT_PAGE,
        rightAction = ReaderEdgeTapAction.PREVIOUS_PAGE
    ),
    BOTH_PREVIOUS(
        key = "both_previous",
        leftAction = ReaderEdgeTapAction.PREVIOUS_PAGE,
        rightAction = ReaderEdgeTapAction.PREVIOUS_PAGE
    ),
    BOTH_NEXT(
        key = "both_next",
        leftAction = ReaderEdgeTapAction.NEXT_PAGE,
        rightAction = ReaderEdgeTapAction.NEXT_PAGE
    );

    companion object {
        fun fromKey(key: String?): ReaderEdgeTapMode =
            entries.firstOrNull { it.key == key } ?: LEFT_PREVIOUS_RIGHT_NEXT
    }
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
