package com.huangder.lumibooks.ui.reader.engine

internal fun stableChapterPageCount(
    cachedPageCount: Int?,
    slotPageCount: Int,
    visiblePageIndex: Int,
    isLoaded: Boolean
): Int {
    val visibleMinimum = if (isLoaded && visiblePageIndex >= 0) visiblePageIndex + 1 else 0
    return maxOf(cachedPageCount ?: 0, slotPageCount, visibleMinimum)
}

internal fun isAbsoluteBookStart(
    chapterIndex: Int,
    primaryPageIndex: Int,
    isLoaded: Boolean
): Boolean = isLoaded && chapterIndex == 0 && primaryPageIndex == 0

internal fun isAbsoluteBookEnd(
    chapterIndex: Int,
    primaryPageIndex: Int,
    rightPageIndex: Int,
    chapterPageCount: Int,
    chapterCount: Int,
    isLoaded: Boolean
): Boolean {
    if (!isLoaded || chapterCount <= 0 || chapterPageCount <= 0) return false
    if (chapterIndex != chapterCount - 1) return false
    return maxOf(primaryPageIndex, rightPageIndex) >= chapterPageCount - 1
}
