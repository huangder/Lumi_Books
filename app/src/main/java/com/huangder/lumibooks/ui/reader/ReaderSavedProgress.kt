package com.huangder.lumibooks.ui.reader

/** Calculates persisted whole-book progress from the reader's zero-based position. */
internal fun calculateSavedReadingProgress(
    currentChapterIndex: Int,
    chapterCount: Int,
    currentPageIndex: Int,
    totalPages: Int,
    isContinuousScroll: Boolean
): Float {
    if (chapterCount <= 0) return 0f

    val chapterIndex = currentChapterIndex.coerceIn(0, chapterCount - 1)
    val chapterProgress = when {
        totalPages <= 0 -> 0f
        isContinuousScroll -> currentPageIndex.toFloat() / totalPages
        else -> (currentPageIndex.toFloat() + 1f) / totalPages
    }.coerceIn(0f, 1f)

    return ((chapterIndex + chapterProgress) / chapterCount).coerceIn(0f, 1f)
}
