package com.huangder.lumibooks.domain.model

enum class ReaderPageCorner {
    TOP_LEFT,
    TOP_RIGHT,
    BOTTOM_LEFT,
    BOTTOM_RIGHT
}

enum class ReaderEdgeTapAction {
    PREVIOUS_PAGE,
    NEXT_PAGE;

    fun reversed(): ReaderEdgeTapAction = when (this) {
        PREVIOUS_PAGE -> NEXT_PAGE
        NEXT_PAGE -> PREVIOUS_PAGE
    }
}

enum class ReaderWritingMode(val key: String) {
    HORIZONTAL("horizontal"),
    VERTICAL_RL("vertical_rl");

    val isVertical: Boolean get() = this == VERTICAL_RL

    fun effectivePageTransition(preferredTransition: String): String =
        if (isVertical && preferredTransition in setOf("continuous", "scroll")) {
            "slide"
        } else {
            preferredTransition
        }

    fun usesContinuousScroll(preferredTransition: String, eInkModeEnabled: Boolean): Boolean =
        !eInkModeEnabled && effectivePageTransition(preferredTransition) == "continuous"

    companion object {
        fun fromKey(key: String?): ReaderWritingMode =
            entries.firstOrNull { it.key == key } ?: HORIZONTAL
    }
}

enum class ReaderTextAlignment(val key: String) {
    NATURAL("natural"),
    LEFT("left"),
    CENTER("center"),
    RIGHT("right"),
    JUSTIFY("justify");

    companion object {
        fun fromKey(key: String?): ReaderTextAlignment =
            entries.firstOrNull { it.key == key } ?: NATURAL
    }
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
    BATTERY("battery"),
    TIME("time");

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

/**
 * 阅读页四角信息区（页眉 / 页脚）到屏幕边缘的距离。
 *
 * 四个字段都可为空，表示"用户还没调过这一边"，此时沿用旧版行为：
 * 左右跟随正文边距（信息区文字与正文左右对齐），上下使用 [DEFAULT_TOP_DP] / [DEFAULT_BOTTOM_DP]。
 * 用户拖动高级设置里的滑块后，才会为对应的边写入具体数值。
 */
data class ReaderCornerMargins(
    val leftDp: Float? = null,
    val rightDp: Float? = null,
    val topDp: Float? = null,
    val bottomDp: Float? = null
) {
    /** 左边距，未设置时跟随正文左边距。 */
    fun resolvedLeftDp(bodyLeftDp: Float): Float = leftDp ?: bodyLeftDp

    /** 右边距，未设置时跟随正文右边距。 */
    fun resolvedRightDp(bodyRightDp: Float): Float = rightDp ?: bodyRightDp

    /** 页眉（上边距），未设置时保持旧版固定值。 */
    fun resolvedTopDp(): Float = topDp ?: DEFAULT_TOP_DP

    /** 页脚（下边距），未设置时保持旧版固定值。 */
    fun resolvedBottomDp(): Float = bottomDp ?: DEFAULT_BOTTOM_DP

    companion object {
        /** 页眉与屏幕顶部的默认距离（旧版固定值）。 */
        const val DEFAULT_TOP_DP = 20f

        /** 页脚与屏幕底部的默认距离（旧版固定值）。 */
        const val DEFAULT_BOTTOM_DP = 16f

        val HORIZONTAL_RANGE = 0f..80f
        val VERTICAL_RANGE = 0f..120f

        fun clampHorizontal(valueDp: Float): Float =
            valueDp.coerceIn(HORIZONTAL_RANGE.start, HORIZONTAL_RANGE.endInclusive)

        fun clampVertical(valueDp: Float): Float =
            valueDp.coerceIn(VERTICAL_RANGE.start, VERTICAL_RANGE.endInclusive)
    }
}
