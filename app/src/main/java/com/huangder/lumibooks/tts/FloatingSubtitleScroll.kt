package com.huangder.lumibooks.tts

/** 悬浮字幕超长时的滚动参数：只从句子开头滚到句尾一次，不重复。 */
internal const val SUBTITLE_SCROLL_START_DELAY_MS = 600L
internal const val SUBTITLE_SCROLL_MIN_DURATION_MS = 900L
internal const val SUBTITLE_SCROLL_SPEED_DP_PER_SECOND = 60f

/** 普通语速下的估算朗读速度（字符/秒），只在引擎不提供朗读位置回调时用来推进显示。 */
internal const val SUBTITLE_CJK_CHARS_PER_SECOND = 4.5f
internal const val SUBTITLE_LATIN_CHARS_PER_SECOND = 14f

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

/**
 * 句内正在朗读的位置（相对句首的字符数），用来让长句翻到正在读的那一行。取范围起点而不是终点：
 * 外部引擎给的是估算出的整个分句，用终点会提前翻到还没读的内容。
 * 只接受落在 [utterance] 内、且比整句更短的范围：整句范围表示引擎还没报告进度，上一句遗留的范围也不能用。
 */
internal fun subtitleSpokenPosition(utterance: TtsTextSegment?, range: TtsTextSegment?): Int? {
    if (utterance == null || range == null) return null
    val start = utterance.startCharacterOffset
    val end = utterance.endCharacterOffset
    if (range.startCharacterOffset < start || range.endCharacterOffset > end) return null
    if (range.startCharacterOffset == start && range.endCharacterOffset == end) return null
    return (range.startCharacterOffset - start).coerceIn(0, utterance.text.length)
}

/** 引擎不报告进度时，按语速估算朗读完整句所需的时间。 */
internal fun subtitleEstimatedDurationMs(text: String, speechRate: Float): Long {
    val spoken = text.count { !it.isWhitespace() }
    if (spoken == 0) return 0L
    val cjk = text.count { Character.UnicodeScript.of(it.code) == Character.UnicodeScript.HAN }
    val cps = if (cjk * 2 >= spoken) SUBTITLE_CJK_CHARS_PER_SECOND else SUBTITLE_LATIN_CHARS_PER_SECOND
    val rate = speechRate.takeIf { it.isFinite() && it > 0f }?.coerceIn(0.25f, 4f) ?: 1f
    return (spoken / (cps * rate) * 1000f).toLong()
}

/** 多行歌词里，让正在读的行出现在可见窗口内，并尽量放在最上面以便看到后续内容。 */
internal fun lyricsTopLine(spokenLine: Int, lineCount: Int, visibleLines: Int): Int {
    if (lineCount <= visibleLines || visibleLines <= 0) return 0
    return spokenLine.coerceIn(0, lineCount - visibleLines)
}
