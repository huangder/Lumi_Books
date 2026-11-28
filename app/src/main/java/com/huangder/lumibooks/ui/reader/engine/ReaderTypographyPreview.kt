package com.huangder.lumibooks.ui.reader.engine

import android.text.Spanned
import android.text.style.ReplacementSpan

/** Offsets in the formatted chapter can move when a synthetic paragraph spacer is added. */
internal fun typographyOffset(before: CharSequence, after: CharSequence, offset: Int): Int {
    if (before === after) return offset.coerceIn(0, after.length)
    var old = 0
    var new = 0
    val target = offset.coerceIn(0, before.length)
    while (old < target && new < after.length) {
        when {
            before[old] == after[new] -> {
                old++
                new++
            }
            before[old] == '\n' || before[old] == '\r' -> old++
            after[new] == '\n' || after[new] == '\r' -> new++
            else -> return (new + target - old).coerceIn(0, after.length)
        }
    }
    if (target < before.length && before[target] !in "\r\n") {
        while (new < after.length && after[new] in "\r\n") new++
    }
    return new
}

internal fun typographyWindow(text: CharSequence, anchor: Int, pageCharacters: Int): IntRange {
    val radius = (pageCharacters.coerceIn(256, 3_000) * 4)
    var start = (anchor - radius).coerceAtLeast(0)
    var end = (anchor + radius).coerceAtMost(text.length)
    val paragraph = (start until minOf(start + 256, anchor)).firstOrNull { text[it] == '\n' }
    if (start > 0 && paragraph != null) start = paragraph + 1
    if (start in 1 until text.length && Character.isLowSurrogate(text[start])) start--
    if (end in 1 until text.length && Character.isLowSurrogate(text[end])) end++
    if (text is Spanned) {
        if (start < text.length) {
            text.getSpans(start, start, ReplacementSpan::class.java).forEach {
                start = minOf(start, text.getSpanStart(it))
            }
        }
        if (end < text.length) {
            text.getSpans(end, end, ReplacementSpan::class.java).forEach {
                end = maxOf(end, text.getSpanEnd(it))
            }
        }
    }
    return start until end
}

/** Bridge the visible preview to exact pagination without skipping any text. */
internal fun retainTypographyPages(exact: ChapterLayout, visible: List<PageLayout>): ChapterLayout {
    val start = visible.first().startCharOffset
    val end = visible.last().endCharOffset
    val pages = buildList {
        exact.pages.forEach { page ->
            if (page.endCharOffset <= start) add(page)
            else if (page.startCharOffset < start) {
                add(page.copy(endCharOffset = start, endHyphenEdit = 0,
                    verticalGeometry = page.verticalGeometry?.copy(
                        items = page.verticalGeometry.items.filter { it.endOffset <= start })))
            }
        }
        addAll(visible)
        exact.pages.forEach { page ->
            if (page.startCharOffset >= end) add(page)
            else if (page.endCharOffset > end) {
                add(page.copy(startCharOffset = end, verticalGeometry = page.verticalGeometry?.copy(
                    items = page.verticalGeometry.items.filter { it.startOffset >= end })))
            }
        }
    }.mapIndexed { index, page -> page.copy(pageIndex = index) }
    return exact.copy(pages = pages, totalPages = pages.size)
}
