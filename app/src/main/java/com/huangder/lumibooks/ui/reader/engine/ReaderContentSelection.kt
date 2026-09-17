package com.huangder.lumibooks.ui.reader.engine

/** 跨页选区的扩展方向。 */
enum class ReaderSelectionDirection { PREV, NEXT }

/**
 * 由 ReadView 自持的跨页选区状态。
 *
 * 偏移全部是**章节级**字符偏移，区间为半开语义 [start, end)。
 * [anchor] 是手势起始时固定不动的一端，[focus] 随手指移动；反向拖动时两者
 * 大小关系会互换，因此对外统一用 [start]/[end] 描述选区。
 *
 * 这个类刻意不依赖任何 Android 类型，翻页与渲染时序问题（页面是否已加载、
 * 槽位是否已轮转）都不会影响它的正确性。
 */
internal data class ReaderContentSelection(
    val chapterIndex: Int,
    val chapterLength: Int,
    val anchor: Int,
    val focus: Int
) {
    private val safeLength: Int get() = chapterLength.coerceAtLeast(0)

    val start: Int get() = minOf(anchor, focus).coerceIn(0, safeLength)
    val end: Int get() = maxOf(anchor, focus).coerceIn(0, safeLength)
    val isEmpty: Boolean get() = end <= start

    /** focus 位于结束侧时，手指向下拖动是「扩大」选区。 */
    val focusOnEndSide: Boolean get() = focus >= anchor

    fun moveFocus(offset: Int): ReaderContentSelection =
        copy(focus = offset.coerceIn(0, safeLength))

    /** 继续向后选择：焦点吸附到新页的第一个字符。 */
    fun extendFocusToNextPageStart(pageStartOffset: Int): ReaderContentSelection =
        moveFocus(pageStartOffset)

    /** 继续向前选择：焦点吸附到上一页的最后一个字符之后。 */
    fun extendFocusToPreviousPageEnd(pageEndOffset: Int): ReaderContentSelection =
        moveFocus(pageEndOffset)

    /** 取选区对应的原文切片（调用方按需做简繁转换）。 */
    fun selectedText(chapterText: CharSequence): String {
        if (isEmpty) return ""
        val source = chapterText.toString()
        val safeStart = start.coerceIn(0, source.length)
        val safeEnd = end.coerceIn(safeStart, source.length)
        return source.substring(safeStart, safeEnd)
    }
}

/** 与 Android 视图无关的跨页选区判定规则（可单测）。 */
internal object ReaderContentSelectionRules {
    /**
     * 手指越出页面内容区时请求的扩展方向；未越界返回 null。
     * 双向支持：越过页底向后翻，越过页顶向前翻。
     */
    fun directionForEdge(
        beyondTop: Boolean,
        beyondBottom: Boolean
    ): ReaderSelectionDirection? = when {
        beyondBottom -> ReaderSelectionDirection.NEXT
        beyondTop -> ReaderSelectionDirection.PREV
        else -> null
    }

    /**
     * 系统选区手柄越界时允许的方向：只有「向外」扩展才翻页，
     * 避免把向内收回手柄的拖动误判成翻页。
     */
    fun directionForHandleEdge(
        draggingStartHandle: Boolean,
        beyondTop: Boolean,
        beyondBottom: Boolean
    ): ReaderSelectionDirection? = when {
        !draggingStartHandle && beyondBottom -> ReaderSelectionDirection.NEXT
        draggingStartHandle && beyondTop -> ReaderSelectionDirection.PREV
        else -> null
    }

    /** 限单章：只有目标页仍属于选区所在章节时才允许继续扩展。 */
    fun canExtendWithinChapter(
        targetChapterIndex: Int,
        selectionChapterIndex: Int
    ): Boolean = targetChapterIndex >= 0 && targetChapterIndex == selectionChapterIndex

    /** 翻页后焦点吸附到目标页的哪一端。 */
    fun focusOffsetForDirection(
        direction: ReaderSelectionDirection,
        targetPageStart: Int,
        targetPageEnd: Int
    ): Int = when (direction) {
        ReaderSelectionDirection.NEXT -> targetPageStart
        ReaderSelectionDirection.PREV -> targetPageEnd
    }
}
