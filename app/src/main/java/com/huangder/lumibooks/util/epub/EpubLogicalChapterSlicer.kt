package com.huangder.lumibooks.util.epub

import org.jsoup.Jsoup
import org.jsoup.nodes.Node
import org.jsoup.parser.Parser
import org.jsoup.select.NodeTraversor
import org.jsoup.select.NodeVisitor

/** Keep a DOM interval, including its ancestors and the publisher's document shell. */
internal fun sliceEpubLogicalChapter(html: String, startAnchor: String?, endAnchor: String?): String {
    if (startAnchor == null) return html
    val document = Jsoup.parse(html, "", Parser.xmlParser())
    val root = document.selectFirst("html") ?: return html
    val body = root.selectFirst("body") ?: return html
    val nodes = mutableListOf<Node>()
    NodeTraversor.traverse(object : NodeVisitor {
        override fun head(node: Node, depth: Int) { nodes += node }
        override fun tail(node: Node, depth: Int) = Unit
    }, body)
    fun anchorIndex(anchor: String): Int = nodes.indexOfFirst {
        it.attr("id") == anchor || it.attr("name") == anchor
    }
    val start = anchorIndex(startAnchor)
    val end = endAnchor?.let(::anchorIndex) ?: nodes.size
    // Missing, reversed or out-of-body anchors must never produce a bare fragment.
    if (start < 1 || end <= start) return html
    val retained = nodes.subList(start, end).toMutableSet()
    retained.toList().forEach { node ->
        var parent = node.parentNode()
        while (parent != null && parent !== body) {
            retained += parent
            parent = parent.parentNode()
        }
    }
    nodes.drop(1).filter { it !in retained }.forEach { node ->
        if (node.parentNode() === body || node.parentNode() in retained) node.remove()
    }
    document.outputSettings().syntax(org.jsoup.nodes.Document.OutputSettings.Syntax.xml)
    return document.outerHtml()
}
