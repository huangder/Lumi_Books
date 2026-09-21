package com.huangder.lumibooks.ui.reader

import kotlin.math.roundToInt

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

internal fun rasterReadingProgress(pageIndex: Int, pageCount: Int): Float {
    if (pageCount <= 0) return 0f
    val safePageIndex = pageIndex.coerceIn(0, pageCount - 1)
    return if (safePageIndex == pageCount - 1) {
        1f
    } else {
        safePageIndex.toFloat() / pageCount.toFloat()
    }
}

internal fun restoredRasterPageIndex(readingProgress: Float, pageCount: Int): Int {
    if (pageCount <= 0 || !readingProgress.isFinite()) return 0
    return (readingProgress.coerceIn(0f, 1f) * pageCount)
        .roundToInt()
        .coerceIn(0, pageCount - 1)
}

/**
 * 事件没有携带文字锚点（locator）时，是否沿用上一次的锚点。
 *
 * WebView 的翻页提交偶尔不带锚点（例如 curl 提交时没有可用的预加载页），
 * 沿用旧锚点会把"上一页"的位置存进进度，重新进入就停在前面几页。
 * 只有同一页重复上报（位置没动）才保留旧锚点。
 */
internal fun retainedEpubLocator(
    previous: String?,
    incoming: String?,
    chapterChanged: Boolean,
    pageChanged: Boolean
): String? = incoming ?: previous.takeUnless { chapterChanged || pageChanged }

/** 书籍原排版在缺少文字锚点时，用整书进度反推出的章节与页内分数。 */
internal data class BookLayoutRestorePoint(
    val chapterIndex: Int,
    val chapterFraction: Float
)

internal data class EpubChapterTurnTarget(
    val chapterIndex: Int,
    val chapterFraction: Float
)

/**
 * 原排版章节边界翻章的落点。
 *
 * 向后翻进入下一章时落到章首；向前翻进入上一章时落到章尾，滚动模式下必须
 * 把 1.0 作为恢复分数，等文档完成分页后再定位，否则会在分页前被夹到第 0 页。
 */
internal fun epubChapterTurnTarget(
    currentChapterIndex: Int,
    chapterCount: Int,
    direction: Int
): EpubChapterTurnTarget? {
    if (direction == 0 || chapterCount <= 0) return null
    val chapterDelta = if (direction > 0) 1 else -1
    val targetChapter = currentChapterIndex + chapterDelta
    if (targetChapter !in 0 until chapterCount) return null
    return EpubChapterTurnTarget(
        chapterIndex = targetChapter,
        chapterFraction = if (chapterDelta < 0) 1f else 0f
    )
}

/**
 * 由整书进度反推书籍原排版的落点。
 *
 * 进度按"页尾"保存（`(pageIndex + 1) / totalPages`），所以小数部分为 0 时
 * 说明上一章的最后一页被存成了页尾；此时必须回到上一章的页尾，
 * 而不是落到下一章第一页。
 */
internal fun bookLayoutRestorePoint(
    readingProgress: Float,
    chapterCount: Int
): BookLayoutRestorePoint {
    if (chapterCount <= 0) return BookLayoutRestorePoint(0, 0f)
    val scaled = (readingProgress.coerceIn(0f, 1f) * chapterCount)
    var chapterIndex = scaled.toInt().coerceIn(0, chapterCount - 1)
    var chapterFraction = (scaled - chapterIndex).coerceIn(0f, 1f)
    if (chapterFraction <= 0f && scaled > 0f && chapterIndex > 0) {
        chapterIndex -= 1
        chapterFraction = 1f
    }
    return BookLayoutRestorePoint(chapterIndex, chapterFraction)
}
