package com.huangder.lumibooks.util.markdown.native

import android.graphics.Typeface
import android.text.Layout
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

data class MarkdownPageStyle(val render: MarkdownRenderStyle, val typeface: Typeface,
    val lineHeight: Float, val letterSpacing: Float,
    val alignment: Layout.Alignment = Layout.Alignment.ALIGN_NORMAL, val justify: Boolean = false)
data class MarkdownPageFragment(val block: MarkdownBlock, val localStart: Int, val localEnd: Int,
    val text: CharSequence, val height: Int, val cells: List<MarkdownCellFragment> = emptyList(), val repeatedHeader: Boolean = false)
data class MarkdownCellFragment(val localStart: Int, val text: CharSequence, val width: Int, val height: Int, val column: Int = 0)
data class MarkdownTableCell(val start: Int, val end: Int, val width: Int, val layout: StaticLayout, val column: Int)
data class MarkdownPage(val start: Int, val end: Int, val fragments: List<MarkdownPageFragment>)

/** Layouts are block-sized. Page lookup never requires a layout of the entire logical chapter. */
class MarkdownPageProvider(private val source: MarkdownBlockSource, val style: MarkdownPageStyle) {
    private val layouts = object : LruCache<Int, Pair<Spanned, StaticLayout>>(24 * 1024 * 1024) {
        override fun sizeOf(key: Int, value: Pair<Spanned, StaticLayout>): Int =
            markdownRenderBytes(value.first) + value.second.lineCount * 40
    }
    private val tableLayouts = LruCache<Int, List<MarkdownTableCell>>(16)
    private val tableHeaders = source.document.blocks.filter { it.kind == "table" }.groupBy { it.tableId }.mapValues { it.value.first() }
    val length get() = source.document.text.length
    private val paint = TextPaint(TextPaint.ANTI_ALIAS_FLAG).apply {
        textSize = this@MarkdownPageProvider.style.render.fontSize
        color = this@MarkdownPageProvider.style.render.color
        typeface = this@MarkdownPageProvider.style.typeface
        letterSpacing = this@MarkdownPageProvider.style.letterSpacing
    }
    suspend fun block(index: Int): Pair<Spanned, StaticLayout> = withContext(Dispatchers.Default) {
        layouts.get(index)?.let { return@withContext it }
        val text = source.renderBlock(index, style.render)
        val value = text to StaticLayout.Builder.obtain(text, 0, text.length, paint, style.render.width.coerceAtLeast(1))
            .setAlignment(style.alignment).setIncludePad(false)
            .setJustificationMode(if (style.justify) Layout.JUSTIFICATION_MODE_INTER_WORD else Layout.JUSTIFICATION_MODE_NONE)
            .setLineSpacing(0f, style.lineHeight).setBreakStrategy(Layout.BREAK_STRATEGY_SIMPLE).build()
        layouts.put(index, value)
        value
    }
    suspend fun tableCells(index: Int): List<MarkdownTableCell> = withContext(Dispatchers.Default) {
        tableLayouts.get(index)?.let { return@withContext it }
        val node = source.document.blocks[index]
        val text = block(index).first
        val starts = node.cells.ifEmpty { listOf(0) }
        val width = maxOf((style.render.fontSize * 8).toInt(), style.render.width / node.tableColumnCount.coerceAtLeast(starts.size))
        starts.mapIndexed { i, start ->
            val end = starts.getOrNull(i + 1)?.minus(1) ?: text.length
            val cellText = text.subSequence(start, end.coerceAtLeast(start))
            val layout = StaticLayout.Builder.obtain(cellText, 0, cellText.length, paint, (width - style.render.fontSize.toInt()).coerceAtLeast(1))
                .setIncludePad(false).setLineSpacing(0f, style.lineHeight).setBreakStrategy(Layout.BREAK_STRATEGY_SIMPLE).build()
            MarkdownTableCell(start, end, width, layout, node.cellColumns.getOrElse(i) { i })
        }.also { tableLayouts.put(index, it) }
    }
    private suspend fun tableFragment(node: MarkdownBlock, fromLine: Int, available: Int, force: Boolean): Pair<MarkdownPageFragment, Int>? {
        val cells = tableCells(node.index)
        val text = block(node.index).first
        val maxLines = cells.maxOf { it.layout.lineCount }
        var last = fromLine
        fun height(endLine: Int) = cells.maxOf { cell ->
            val l = cell.layout
            if (fromLine >= l.lineCount) 0 else l.getLineBottom(minOf(endLine, l.lineCount) - 1) - l.getLineTop(fromLine)
        } + style.render.fontSize.toInt()
        while (last < maxLines && height(last + 1) <= available) last++
        if (last == fromLine) { if (!force) return null; last++ }
        val slices = cells.map { cell ->
            val l = cell.layout
            val start = if (fromLine >= l.lineCount) cell.end else cell.start + l.getLineStart(fromLine)
            val end = if (last >= l.lineCount) cell.end else cell.start + l.getLineStart(last)
            MarkdownCellFragment(start, text.subSequence(start, end), cell.width, height(last), cell.column)
        }
        val next = if (last >= maxLines) node.end else cells.filter { last < it.layout.lineCount }
            .minOf { it.start + it.layout.getLineStart(last) } + node.start
        return MarkdownPageFragment(node, slices.minOf { it.localStart }, slices.maxOf { it.localStart + it.text.length },
            "", height(last), slices) to next
    }
    suspend fun page(offset: Int): MarkdownPage = withContext(Dispatchers.Default) {
        val start = offset.coerceIn(0, length)
        var at = start
        var remaining = style.render.height.coerceAtLeast(1)
        val fragments = mutableListOf<MarkdownPageFragment>()
        var index = source.document.index.blockAt(start)
        while (index < source.document.blocks.size) {
            ensureActive()
            val node = source.document.blocks[index]
            if (node.end <= at) { index++; continue }
            if (node.kind == "table") {
                val cells = tableCells(index)
                val local = (at - node.start).coerceAtLeast(0)
                val cell = cells.firstOrNull { local in it.start..it.end } ?: cells.first()
                val line = cell.layout.getLineForOffset(local.coerceIn(cell.start, cell.end) - cell.start)
                val header = tableHeaders[node.tableId]
                if (fragments.isEmpty() && header != null && header.index != index) {
                    tableFragment(header, 0, remaining / 3, false)?.let { (part, _) ->
                        if (part.localEnd == header.text.length) {
                            fragments.add(part.copy(repeatedHeader = true)); remaining -= part.height
                        }
                    }
                }
                val table = tableFragment(node, line, remaining, fragments.isEmpty() || fragments.all { it.repeatedHeader }) ?: break
                fragments.add(table.first); remaining -= table.first.height
                at = table.second
                if (at < node.end) break
                index++
                at = source.document.blocks.getOrNull(index)?.start ?: length
                if (remaining <= 0) break
                continue
            }
            val (text, layout) = block(index)
            val local = (at - node.start).coerceIn(0, text.length)
            val first = layout.getLineForOffset(local)
            var last = first
            while (last < layout.lineCount && layout.getLineBottom(last) - layout.getLineTop(first) <= remaining) last++
            if (last == first) {
                if (fragments.isNotEmpty()) break
                last++
            }
            val end = if (last < layout.lineCount) layout.getLineStart(last) else text.length
            if (end <= local) { index++; continue }
            val h = (layout.getLineBottom(last - 1) - layout.getLineTop(first)).coerceAtLeast(1)
            fragments.add(MarkdownPageFragment(node, local, end, text.subSequence(local, end), h))
            at = node.start + end
            remaining -= h
            if (end < text.length) break
            index++
            at = source.document.blocks.getOrNull(index)?.start ?: length
            remaining -= style.render.paragraphSpacing
            if (remaining <= 0) break
        }
        MarkdownPage(start, at, fragments)
    }
    fun clear() { layouts.evictAll(); tableLayouts.evictAll() }
}
