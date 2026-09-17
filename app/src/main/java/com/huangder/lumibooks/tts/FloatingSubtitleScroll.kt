package com.huangder.lumibooks.tts

/** 悬浮字幕超长时的滚动参数：只从句子开头滚到句尾一次，不重复。 */
internal const val SUBTITLE_SCROLL_START_DELAY_MS = 600L
internal const val SUBTITLE_SCROLL_MIN_DURATION_MS = 900L
internal const val SUBTITLE_SCROLL_SPEED_DP_PER_SECOND = 60f

/**
 * 需要横向滚动的距离（像素）。文本连同左右内边距能放进可视宽度时为 0，即不需要滚动。
 */
internal fun subtitleScrollDistancePx(
    textWidthPx: Int,
    viewWidthPx: Int,
    horizontalPaddingPx: Int
): Int {
    if (textWidthPx <= 0 || viewWidthPx <= 0) return 0
    val contentWidth = textWidthPx + horizontalPaddingPx.coerceAtLeast(0)
    return (contentWidth - viewWidthPx).coerceAtLeast(0)
}

/**
 * 按恒定速度换算滚动时长；距离为 0 或速度非正时返回 0，表示不需要动画。
 */
internal fun subtitleScrollDurationMs(
    distancePx: Int,
    speedPxPerSecond: Float,
    minDurationMs: Long
): Long {
    if (distancePx <= 0 || speedPxPerSecond <= 0f) return 0L
    val raw = (distancePx / speedPxPerSecond * 1000f).toLong()
    return raw.coerceAtLeast(minDurationMs)
}
