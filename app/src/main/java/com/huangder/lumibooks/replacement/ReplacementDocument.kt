package com.huangder.lumibooks.replacement

import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import org.jsoup.select.NodeFilter
import org.jsoup.select.NodeTraversor

private val blockTags = setOf(
    "address", "article", "aside", "blockquote", "dd", "div", "dl", "dt", "figcaption", "figure",
    "footer", "header", "li", "main", "nav", "ol", "p", "pre", "section", "table", "td", "th",
    "ul", "h1", "h2", "h3", "h4", "h5", "h6"
)
private val excludedTags = setOf("script", "style", "noscript")
private val mediaTags = setOf("img", "picture", "svg", "audio", "video", "object", "iframe", "math", "hr")

/** Keep elements and attributes intact, including an anchor whose entire label was removed. */
internal fun Document.applyReplacementRules(program: ReplacementProgram, previewRule: String? = null): ReplacementResult {
    val source = StringBuilder()
    val nodes = mutableListOf<TextNode>()
    val segments = mutableListOf<ReplacementSegment>()
    var runStart = 0
    var runEligible = true
    var protectedDepth = 0
    fun flush() {
        if (runStart < source.length) segments.add(ReplacementSegment(source.length - runStart, runEligible))
        runStart = source.length
    }
    fun isProtected(element: Element): Boolean =
        element.normalName() in mediaTags || element.hasAttr("hidden") ||
            element.attr("aria-hidden").equals("true", ignoreCase = true)

    NodeTraversor.filter(object : NodeFilter {
        override fun head(node: Node, depth: Int): NodeFilter.FilterResult {
            if (node is Element) {
                val tag = node.normalName()
                if (tag in excludedTags) return NodeFilter.FilterResult.SKIP_ENTIRELY
                val protected = isProtected(node)
                if (tag in blockTags || protected || tag == "br") flush()
                if (protected) {
                    protectedDepth++
                    runEligible = false
                }
            } else if (node is TextNode) {
                nodes.add(node)
                source.append(node.wholeText)
            }
            return NodeFilter.FilterResult.CONTINUE
        }

        override fun tail(node: Node, depth: Int): NodeFilter.FilterResult {
            if (node is Element) {
                val protected = isProtected(node)
                if (node.normalName() in blockTags || protected || node.normalName() == "br") flush()
                if (protected) {
                    protectedDepth--
                    runEligible = protectedDepth == 0
                }
            }
            return NodeFilter.FilterResult.CONTINUE
        }
    }, body())

    val result = program.apply(source.toString(), segments, previewRule) { edits ->
        var start = 0
        var firstEdit = 0
        nodes.forEach { node ->
            val text = node.wholeText
            val end = start + text.length
            while (firstEdit < edits.size && edits[firstEdit].end <= start) firstEdit++
            if (end > start && firstEdit < edits.size && edits[firstEdit].start < end) {
                val newText = buildString {
                    var offset = 0
                    for (index in firstEdit until edits.size) {
                        val edit = edits[index]
                        if (edit.start >= end) break
                        append(text, offset, (edit.start - start).coerceAtLeast(offset))
                        if (edit.start >= start) append(edit.value)
                        offset = (edit.end - start).coerceAtMost(text.length)
                    }
                    append(text, offset, text.length)
                }
                node.text(newText)
            }
            start = end
        }
    }
    if (result.offsets.changed) outputSettings().prettyPrint(false)
    return result
}
