package com.huangder.lumibooks.util.markdown.native

import com.huangder.lumibooks.util.markdown.MarkdownRenderer
import com.huangder.lumibooks.util.parser.TocEntry
import org.commonmark.node.*
import org.commonmark.parser.Parser
import org.commonmark.parser.IncludeSourceSpans
import org.commonmark.ext.autolink.AutolinkExtension
import org.commonmark.ext.footnotes.FootnotesExtension
import org.commonmark.ext.front.matter.YamlFrontMatterExtension
import org.commonmark.ext.front.matter.YamlFrontMatterVisitor
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension
import org.commonmark.ext.gfm.tables.TablesExtension
import org.commonmark.ext.task.list.items.TaskListItemsExtension
import org.commonmark.ext.task.list.items.TaskListItemMarker
import org.commonmark.ext.footnotes.FootnoteReference
import org.commonmark.ext.footnotes.FootnoteDefinition
import org.commonmark.ext.gfm.tables.TableBlock
import org.commonmark.ext.gfm.strikethrough.Strikethrough
import org.commonmark.ext.front.matter.YamlFrontMatterBlock
import org.commonmark.parser.beta.*
import java.io.Serializable

data class MarkdownRun(val start: Int, val end: Int, val style: String, val value: String = "") : Serializable
data class MarkdownBlock(
    val index: Int, val start: Int, val text: String, val kind: String,
    val runs: List<MarkdownRun>, val sourceStart: Int, val sourceEnd: Int,
    val level: Int = 0, val label: String = "", val language: String = "",
    val cells: List<Int> = emptyList(), val tableId: Int = -1, val codeContext: String = "",
    val cellColumns: List<Int> = emptyList(), val tableColumnCount: Int = 0,
) : Serializable { val end: Int get() = start + text.length }
data class MarkdownOutline(val title: String, val level: Int, val anchor: String, val offset: Int) : Serializable
data class MarkdownDocument(
    val title: String?, val author: String?, val blocks: List<MarkdownBlock>,
    val outline: List<MarkdownOutline>, val text: String, val images: List<String>,
) : Serializable {
    val index get() = MarkdownTextIndex(blocks)
    val toc get() = outline.map { TocEntry(it.title, it.level, 0, false, it.anchor) }
}

/** All persisted positions are UTF-16 positions in visible text, independent of layout. */
class MarkdownTextIndex(private val blocks: List<MarkdownBlock>) {
    fun blockAt(offset: Int): Int {
        var low = 0; var high = blocks.lastIndex
        while (low <= high) {
            val mid = (low + high).ushr(1)
            if (blocks[mid].start <= offset) low = mid + 1 else high = mid - 1
        }
        return high.coerceAtLeast(0)
    }
}

private class MathNode(val latex: String, val display: Boolean) : CustomNode()
private class MathInlineFactory : InlineContentParserFactory {
    override fun getTriggerCharacters() = setOf('$', '\\')
    override fun create() = InlineContentParser { state ->
        val scanner = state.scanner()
        val begin = scanner.position()
        val previous = scanner.peekPreviousCodePoint()
        val close: String
        val display: Boolean
        when {
            scanner.next("$$") -> { close = "$$"; display = true }
            scanner.next("\\(") -> { close = "\\)"; display = false }
            scanner.next("\\[") -> { close = "\\]"; display = true }
            scanner.next('$') -> {
                close = "$"; display = false
                if (scanner.peek().isWhitespace() || Character.isLetterOrDigit(previous)) {
                    scanner.setPosition(begin); return@InlineContentParser ParsedInline.none()
                }
            }
            else -> { scanner.setPosition(begin); return@InlineContentParser ParsedInline.none() }
        }
        val content = StringBuilder()
        while (scanner.hasNext() && content.length < 16_384) {
            if (scanner.next(close)) {
                val value = content.toString()
                // Single-dollar currency amounts and whitespace-delimited prose are not math.
                if (value.isNotBlank() && (close != "$" ||
                    (!value.last().isWhitespace() && !scanner.peek().isDigit() &&
                        !value.matches(Regex("[\\d.,\\s]+"))))) {
                    return@InlineContentParser ParsedInline.of(MathNode(value, display), scanner.position())
                }
                break
            }
            if (!display && scanner.peek() == '\n') break
            val c = scanner.peek(); scanner.next(); content.append(c)
            if (c == '\\' && scanner.peek() == '$') { content.append('$'); scanner.next() }
        }
        scanner.setPosition(begin)
        ParsedInline.none()
    }
}

/** One full semantic parse; bounded blocks contain no AST references. */
object MarkdownNativeParser {
    const val MAX_BLOCK_CHARS = 4096
    const val CACHE_VERSION = 4
    private fun parser() = Parser.builder().includeSourceSpans(IncludeSourceSpans.BLOCKS_AND_INLINES)
            .extensions(listOf(TablesExtension.create(), StrikethroughExtension.create(),
                TaskListItemsExtension.create(), AutolinkExtension.create(), FootnotesExtension.create(),
                YamlFrontMatterExtension.create()))
            .customInlineContentParserFactory(MathInlineFactory()).build()
    data class Metadata(val title: String?, val author: String?, val images: List<String>)
    fun inspect(source: String): Metadata {
        val root = parser().parse(source.removePrefix("\uFEFF"))
        val metadata = YamlFrontMatterVisitor().also { root.accept(it) }.data
        val images = linkedSetOf<String>()
        root.accept(object : AbstractVisitor() {
            override fun visit(image: Image) { images.add(image.destination) }
        })
        return Metadata(metadata["title"]?.firstOrNull(), metadata["author"]?.joinToString(", "), images.toList())
    }
    fun parse(source: String): MarkdownDocument {
        val root = parser().parse(source.removePrefix("\uFEFF"))
        val metadata = YamlFrontMatterVisitor().also { root.accept(it) }.data
        val blocks = mutableListOf<MarkdownBlock>()
        val outline = mutableListOf<MarkdownOutline>()
        val images = linkedSetOf<String>()
        val all = StringBuilder()
        val anchors = mutableSetOf<String>()
        val anchorSuffixes = mutableMapOf<String, Int>()
        var nextTable = 0
        fun children(node: Node) = generateSequence(node.firstChild) { it.next }
        fun kindOf(node: Node): String = when (node) {
            is TaskListItemMarker -> "TaskListItemMarker"
            is FootnoteReference -> "FootnoteReference"
            is FootnoteDefinition -> "FootnoteDefinition"
            is TableBlock -> "TableBlock"
            is Strikethrough -> "Strikethrough"
            is YamlFrontMatterBlock -> "YamlFrontMatterBlock"
            is LinkReferenceDefinition -> "LinkReferenceDefinition"
            else -> ""
        }
        fun property(node: Node, name: String): String = when (node) {
            is TaskListItemMarker -> node.isChecked.toString()
            is FootnoteReference -> node.label
            is FootnoteDefinition -> node.label
            else -> ""
        }
        fun emit(node: Node, kind: String, level: Int, label: String, table: Int = -1) {
            val text = StringBuilder()
            val runs = mutableListOf<MarkdownRun>()
            val cells = mutableListOf<Int>()
            fun inline(n: Node) {
                val start = text.length
                when (n) {
                    is Text -> text.append(n.literal)
                    is Code -> { text.append(n.literal); runs.add(MarkdownRun(start, text.length, "code")) }
                    is SoftLineBreak -> text.append(' ')
                    is HardLineBreak -> text.append('\n')
                    is HtmlInline -> text.append(n.literal)
                    is MathNode -> { text.append(n.latex); runs.add(MarkdownRun(start, text.length, if (n.display) "math-block" else "math", n.latex)) }
                    is Image -> {
                        images.add(n.destination)
                        text.append('\uFFFC')
                        runs.add(MarkdownRun(start, text.length, "image", n.destination))
                    }
                    else -> {
                        val name = kindOf(n)
                        when (name) {
                            "TaskListItemMarker" -> text.append(if (property(n, "isChecked") == "true") "☑ " else "☐ ")
                            "FootnoteReference" -> {
                                val id = property(n, "getLabel")
                                text.append("[$id]"); runs.add(MarkdownRun(start, text.length, "link", "#footnote-$id"))
                            }
                            else -> children(n).forEach { inline(it) }
                        }
                        val style = when (n) {
                            is StrongEmphasis -> "bold"
                            is Emphasis -> "italic"
                            is Link -> "link"
                            else -> if (name == "Strikethrough") "strike" else null
                        }
                        if (style != null && start < text.length) {
                            val value = (n as? Link)?.destination.orEmpty()
                            if (style != "link" || value.startsWith('#') || value.startsWith("https://", true) ||
                                value.startsWith("http://", true) || value.startsWith("mailto:", true))
                                runs.add(MarkdownRun(start, text.length, style, value))
                        }
                    }
                }
            }
            when (node) {
                is FencedCodeBlock -> text.append(node.literal.removeSuffix("\n"))
                is IndentedCodeBlock -> text.append(node.literal.removeSuffix("\n"))
                is HtmlBlock -> text.append(node.literal)
                is ThematicBreak -> text.append("—")
                else -> if (kind == "table") children(node).forEach { cell ->
                    if (cells.isNotEmpty()) text.append('\t')
                    cells.add(text.length); inline(cell)
                } else {
                    if (label == "☑" || label == "☐") text.append(label).append(' ')
                    inline(node)
                }
            }
            val spans = node.sourceSpans
            val sourceStart = spans.firstOrNull()?.inputIndex ?: 0
            val sourceEnd = spans.lastOrNull()?.let { it.inputIndex + it.length } ?: sourceStart
            if (node is Heading && text.isNotBlank()) {
                val title = text.toString().trim()
                val base = MarkdownRenderer.slug(title).ifBlank { "heading" }
                var anchor = base; var suffix = anchorSuffixes[base] ?: 2
                while (!anchors.add(anchor)) anchor = "$base-${suffix++}"
                anchorSuffixes[base] = suffix
                outline.add(MarkdownOutline(title, node.level, anchor, all.length))
            }
            if (node is FootnoteDefinition) {
                outline.add(MarkdownOutline("", 0, "footnote-${property(node, "getLabel")}", all.length))
            }
            if (text.isEmpty()) return
            var from = 0
            var codeContext = ""
            val orderedRuns = runs.sortedBy { it.start }
            val activeRuns = mutableListOf<MarkdownRun>()
            var runIndex = 0
            while (from < text.length) {
                if (Thread.currentThread().isInterrupted) throw java.util.concurrent.CancellationException()
                var to = minOf(from + MAX_BLOCK_CHARS, text.length)
                // Never split surrogate pairs, formula ranges or image placeholders.
                if (to < text.length && Character.isHighSurrogate(text[to - 1])) to--
                activeRuns.removeAll { it.end <= from }
                while (runIndex < orderedRuns.size && orderedRuns[runIndex].start < to) activeRuns.add(orderedRuns[runIndex++])
                activeRuns.firstOrNull { it.style.startsWith("math") && it.start < to && it.end > to }?.let {
                    to = if (it.start > from) it.start else it.end
                }
                while (runIndex < orderedRuns.size && orderedRuns[runIndex].start < to) activeRuns.add(orderedRuns[runIndex++])
                val value = text.substring(from, to)
                val localRuns = activeRuns.filter { it.start < to && it.end > from }.map {
                    it.copy(start = maxOf(it.start, from) - from, end = minOf(it.end, to) - from)
                }
                val localCells = if (kind == "table") (listOf(0) + cells.filter { it > from && it < to }.map { it - from }).distinct() else emptyList()
                blocks.add(MarkdownBlock(blocks.size, all.length, value, kind, localRuns, sourceStart,
                    sourceEnd, level, if (from == 0) label else "", (node as? FencedCodeBlock)?.info.orEmpty(),
                    localCells, table, codeContext,
                    localCells.map { position -> cells.indexOfLast { it <= from + position }.coerceAtLeast(0) }, cells.size))
                all.append(value)
                if (kind == "code") codeContext = MarkdownCodeContext.advance(value, codeContext, (node as? FencedCodeBlock)?.info.orEmpty())
                from = to
            }
            all.append('\n')
        }
        fun walk(node: Node, depth: Int = 0, label: String = "", quoted: Boolean = false) {
            if (Thread.currentThread().isInterrupted) throw java.util.concurrent.CancellationException()
            when (node) {
                is Heading -> emit(node, "heading", node.level, label)
                is Paragraph -> emit(node, if (quoted) "quote" else "paragraph", depth, label)
                is FencedCodeBlock, is IndentedCodeBlock -> emit(node, "code", depth, label)
                is HtmlBlock -> emit(node, "paragraph", depth, label)
                is ThematicBreak -> emit(node, "rule", depth, label)
                is BulletList, is OrderedList -> {
                    var number = (node as? OrderedList)?.markerStartNumber ?: 1
                    children(node).forEach { item ->
                        val task = children(item).firstOrNull { it is TaskListItemMarker }
                        val marker = if (task != null) { if (property(task, "isChecked") == "true") "☑" else "☐" }
                            else if (node is OrderedList) "${number++}." else "•"
                        children(item).filter { it !== task }.forEachIndexed { i, child -> walk(child, depth + 1, if (i == 0) marker else "", quoted) }
                    }
                }
                is BlockQuote -> children(node).forEach { walk(it, depth, label, true) }
                else -> when (kindOf(node)) {
                    "YamlFrontMatterBlock", "LinkReferenceDefinition" -> Unit
                    "TableBlock" -> {
                        val id = nextTable++
                        children(node).forEach { part -> children(part).forEach { row -> emit(row, "table", 0, "", id) } }
                    }
                    "FootnoteDefinition" -> emit(node, "footnote", depth, label)
                    else -> children(node).forEach { walk(it, depth, label, quoted) }
                }
            }
        }
        walk(root)
        if (blocks.isEmpty()) blocks.add(MarkdownBlock(0, 0, "", "paragraph", emptyList(), 0, 0))
        return MarkdownDocument(metadata["title"]?.firstOrNull(), metadata["author"]?.joinToString(", "),
            blocks, outline, all.toString(), images.toList())
    }
}

/** Carries multiline comment/string state across bounded code fragments. */
internal object MarkdownCodeContext {
    fun advance(text: String, initial: String, language: String): String {
        var state = initial; var i = 0
        val hashComments = language.substringBefore(' ') in setOf("python", "py", "bash", "sh", "yaml", "yml")
        while (i < text.length) {
            when {
                state == "//" || state == "#" -> { if (text[i] == '\n') state = ""; i++ }
                state == "/*" -> if (text.startsWith("*/", i)) { state = ""; i += 2 } else i++
                state.isNotEmpty() -> when {
                    text[i] == '\\' -> i += 2
                    text.startsWith(state, i) -> { i += state.length; state = "" }
                    else -> i++
                }
                text.startsWith("/*", i) && !hashComments -> { state = "/*"; i += 2 }
                text.startsWith("//", i) && !hashComments -> { state = "//"; i += 2 }
                text[i] == '#' && hashComments -> { state = "#"; i++ }
                text.startsWith("\"\"\"", i) || text.startsWith("'''", i) -> { state = text.substring(i, i + 3); i += 3 }
                text[i] == '\'' || text[i] == '"' || text[i] == '`' -> { state = text[i].toString(); i++ }
                else -> i++
            }
        }
        return state
    }
}
