package com.huangder.lumibooks.ui.reader

internal data class GlobalProgressTarget(
    val chapterIndex: Int,
    val chapterFraction: Float
)

internal fun mapGlobalProgress(
    progressPercent: Float,
    chapterCount: Int
): GlobalProgressTarget? {
    if (chapterCount <= 0) return null

    val normalizedProgress = if (progressPercent.isFinite()) {
        progressPercent.coerceIn(0f, 100f)
    } else {
        0f
    }
    val rawPosition = normalizedProgress / 100f * chapterCount
    val chapterIndex = rawPosition.toInt().coerceIn(0, chapterCount - 1)
    return GlobalProgressTarget(
        chapterIndex = chapterIndex,
        chapterFraction = (rawPosition - chapterIndex).coerceIn(0f, 1f)
    )
}

internal fun pageIndexForChapterFraction(
    chapterFraction: Float,
    pageCount: Int
): Int {
    if (pageCount <= 0) return 0
    val normalizedFraction = if (chapterFraction.isFinite()) {
        chapterFraction.coerceIn(0f, 1f)
    } else {
        0f
    }
    return (normalizedFraction * pageCount).toInt().coerceIn(0, pageCount - 1)
}

internal fun pdfPageIndexForProgress(
    progressPercent: Float,
    pageCount: Int
): Int {
    val normalizedProgress = if (progressPercent.isFinite()) {
        progressPercent.coerceIn(0f, 100f)
    } else {
        0f
    }
    return pageIndexForChapterFraction(normalizedProgress / 100f, pageCount)
}

internal class CatalogProgressDragSession {
    var currentProgress: Float = 0f
        private set

    var isActive: Boolean = false
        private set

    fun begin(externalProgress: Float): Float {
        currentProgress = if (externalProgress.isFinite()) {
            externalProgress.coerceIn(0f, 100f)
        } else {
            0f
        }
        isActive = true
        return currentProgress
    }

    fun dragBy(deltaPercent: Float): Float {
        if (!isActive) return currentProgress
        currentProgress = (currentProgress + deltaPercent).coerceIn(0f, 100f)
        return currentProgress
    }

    fun finish(onCommit: (Float) -> Unit): Float? {
        if (!isActive) return null
        isActive = false
        return currentProgress.also(onCommit)
    }

    fun cancel() {
        isActive = false
    }
}

/**
 * 目录胶囊拖动时的实时定位去重。
 *
 * 一次拖动会产生大量进度回调，同一页可能在很多帧里被反复命中；只有目标页号真正变化时
 * 才值得发起一次跳转，否则跳转请求会堆积成卡顿。
 */
internal class CatalogProgressScrubTracker {
    private var lastRequestedPage: Int = -1

    /** 每次开始拖动重新定位，保证"落点与当前页相同"时也会跟随手指重新对齐。 */
    fun reset() {
        lastRequestedPage = -1
    }

    /** 返回本次需要跳转到的页号；目标页未变化时返回 null（不需要新的跳转）。 */
    fun nextSeek(progressPercent: Float, pageCount: Int): Int? {
        if (pageCount <= 0) return null
        val page = pdfPageIndexForProgress(progressPercent, pageCount)
        if (page == lastRequestedPage) return null
        lastRequestedPage = page
        return page
    }
}
