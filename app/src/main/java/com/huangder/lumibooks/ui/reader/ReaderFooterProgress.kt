package com.huangder.lumibooks.ui.reader

import java.util.Locale

internal fun calculateBookProgressPercent(
    chapterIndex: Int,
    chapterCount: Int,
    pageIndex: Int,
    chapterPageCount: Int,
    rightChapterIndex: Int? = null,
    rightPageIndex: Int? = null,
    rightChapterPageCount: Int? = null
): Float {
    return calculateSavedReadingProgress(
        currentChapterIndex = chapterIndex,
        chapterCount = chapterCount,
        currentPageIndex = pageIndex,
        totalPages = chapterPageCount,
        isContinuousScroll = false,
        rightChapterIndex = rightChapterIndex,
        rightPageIndex = rightPageIndex,
        rightChapterTotalPages = rightChapterPageCount
    ) * 100f
}

internal fun formatReadingProgressPercent(progressPercent: Float): String =
    String.format(Locale.ROOT, "%.2f%%", progressPercent.coerceIn(0f, 100f))
