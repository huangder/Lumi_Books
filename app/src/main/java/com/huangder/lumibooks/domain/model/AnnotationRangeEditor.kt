package com.huangder.lumibooks.domain.model

/**
 * 单章节内的标注区间。区间使用半开语义 [start, end)。
 * locator 与 selectedText 属于持久化适配层职责，不进入纯区间运算。
 */
data class AnnotationSegment(
    val start: Int,
    val end: Int,
    val type: String,
    val color: String,
    val note: String = ""
) {
    val isValid: Boolean get() = start >= 0 && end > start
}

object AnnotationRangeEditor {
    /**
     * 用 [replacement] 覆盖同类型的既有区间。不同类型标注保持不变。
     * 被覆盖区间的左右残段保留原颜色；笔记仅保留在最左侧残段。
     */
    fun replaceRange(
        existing: List<AnnotationSegment>,
        replacement: AnnotationSegment
    ): List<AnnotationSegment> {
        if (!replacement.isValid) return existing

        val result = mutableListOf<AnnotationSegment>()
        existing.forEach { segment ->
            if (!segment.isValid || segment.type != replacement.type ||
                segment.end <= replacement.start || segment.start >= replacement.end
            ) {
                result += segment
                return@forEach
            }

            if (segment.start < replacement.start) {
                result += segment.copy(end = replacement.start)
            }
            if (segment.end > replacement.end) {
                result += segment.copy(
                    start = replacement.end,
                    note = if (segment.start < replacement.start) "" else segment.note
                )
            }
        }
        result += replacement
        return result.sortedWith(segmentOrder)
    }

    /**
     * 从指定类型标注中减去 [targetStart, targetEnd)。
     * 一条标注可能变成零段、一段或左右两段；拆成两段时笔记只保留在左段。
     */
    fun removeRange(
        existing: List<AnnotationSegment>,
        targetStart: Int,
        targetEnd: Int,
        type: String
    ): List<AnnotationSegment> {
        if (targetStart < 0 || targetEnd <= targetStart) return existing

        val result = mutableListOf<AnnotationSegment>()
        existing.forEach { segment ->
            if (!segment.isValid || segment.type != type ||
                segment.end <= targetStart || segment.start >= targetEnd
            ) {
                result += segment
                return@forEach
            }

            val hasLeft = segment.start < targetStart
            val hasRight = segment.end > targetEnd
            if (hasLeft) {
                result += segment.copy(end = targetStart)
            }
            if (hasRight) {
                result += segment.copy(
                    start = targetEnd,
                    note = if (hasLeft) "" else segment.note
                )
            }
        }
        return result.sortedWith(segmentOrder)
    }

    private val segmentOrder = compareBy<AnnotationSegment>(
        { it.start },
        { it.end },
        { it.type },
        { it.color }
    )
}
