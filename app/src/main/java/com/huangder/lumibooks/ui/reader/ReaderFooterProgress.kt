package com.huangder.lumibooks.ui.reader

import java.util.Locale

internal fun calculateBookProgressPercent(
    chapterIndex: Int,
    chapterCount: Int,
    pageIndex: Int,
    chapterPageCount: Int
): Float {
    if (chapterCount <= 0) return 0f

    val safeChapterIndex = chapterIndex.coerceIn(0, chapterCount - 1)
    val pageFraction = if (chapterPageCount > 0) {
        ((pageIndex.coerceAtLeast(0) + 1f) / chapterPageCount).coerceIn(0f, 1f)
    } else {
        0f
    }
    return (((safeChapterIndex + pageFraction) / chapterCount) * 100f)
        .coerceIn(0f, 100f)
}

internal fun formatReadingProgressPercent(progressPercent: Float): String =
    String.format(Locale.ROOT, "%.2f%%", progressPercent.coerceIn(0f, 100f))
