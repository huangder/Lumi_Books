package com.huangder.lumibooks.ui.reader

import kotlin.math.roundToInt

internal fun calculateBookProgressPercent(
    chapterIndex: Int,
    chapterCount: Int,
    pageIndex: Int,
    chapterPageCount: Int
): Int {
    if (chapterCount <= 0) return 0

    val safeChapterIndex = chapterIndex.coerceIn(0, chapterCount - 1)
    val pageFraction = if (chapterPageCount > 0) {
        ((pageIndex.coerceAtLeast(0) + 1f) / chapterPageCount).coerceIn(0f, 1f)
    } else {
        0f
    }
    return (((safeChapterIndex + pageFraction) / chapterCount) * 100f)
        .roundToInt()
        .coerceIn(0, 100)
}
