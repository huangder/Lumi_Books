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
