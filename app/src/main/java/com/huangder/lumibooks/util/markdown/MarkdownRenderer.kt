package com.huangder.lumibooks.util.markdown

import org.commonmark.ext.autolink.AutolinkExtension
import org.commonmark.ext.footnotes.FootnotesExtension
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension
import org.commonmark.ext.gfm.tables.TablesExtension
import org.commonmark.ext.task.list.items.TaskListItemsExtension
import org.commonmark.ext.front.matter.YamlFrontMatterExtension
import org.commonmark.ext.front.matter.YamlFrontMatterVisitor
import org.commonmark.parser.Parser
import org.commonmark.renderer.html.HtmlRenderer
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.net.URLDecoder
import java.text.Normalizer
import java.util.Locale

data class MarkdownHeading(val title: String, val level: Int, val anchor: String)
data class MarkdownDocument(
    val title: String?, val author: String?, val html: String,
    val headings: List<MarkdownHeading>, val imageSources: List<String>
)

/** Shared by standalone Markdown and MinerU. Parse the entire source before rendering. */
object MarkdownRenderer {
    private val extensions = listOf(
        TablesExtension.create(), StrikethroughExtension.create(), TaskListItemsExtension.create(),
        AutolinkExtension.create(), FootnotesExtension.create(), YamlFrontMatterExtension.create()
    )
    fun render(source: String, imageResolver: ((String) -> String?)? = null): MarkdownDocument {
        val ast = Parser.builder().extensions(extensions).build().parse(source.removePrefix("\uFEFF"))
        val metadata = YamlFrontMatterVisitor().also { ast.accept(it) }.data
        val html = HtmlRenderer.builder().extensions(extensions).escapeHtml(true).sanitizeUrls(true)
            .build().render(ast)
        val document = Jsoup.parseBodyFragment(html)
        val used = document.select("[id]").map { it.id() }.toMutableSet()
        val headings = document.select("h1,h2,h3,h4,h5,h6").mapNotNull { heading ->
            val title = heading.text().trim()
            if (title.isEmpty()) return@mapNotNull null
            val base = slug(title).ifBlank { "heading" }
            var anchor = base
            var count = 2
            while (!used.add(anchor)) anchor = "$base-${count++}"
            heading.attr("id", anchor)
            MarkdownHeading(title, heading.tagName().substring(1).toInt(), anchor)
        }
        val sources = document.select("img[src]").map { it.attr("src") }.distinct()
        document.select("img").forEach { image ->
            val sourceUrl = image.attr("src")
            val safe = if (sourceUrl.startsWith("data:image/", true)) sourceUrl
                else safeRelativePath(sourceUrl)?.let { imageResolver?.invoke(sourceUrl) ?: if (imageResolver == null) it else null }
            if (safe != null) image.attr("src", safe)
            else image.tagName("span").removeAttr("src").addClass("md-missing-image")
                .text("[${image.attr("alt").ifBlank { "Image" }}]")
        }
        document.select("a[href]").forEach { link ->
            val href = link.attr("href").trim()
            if (!(href.startsWith("#") || href.startsWith("https://", true) || href.startsWith("http://", true) ||
                    href.startsWith("mailto:", true))) link.removeAttr("href")
        }
        // Static checkboxes avoid interactive form nodes being stripped by the EPUB sanitizer.
        document.select("input").forEach { input ->
            input.tagName("span").text(if (input.hasAttr("checked")) "☑ " else "☐ ")
            input.clearAttributes()
        }
        document.select("script,iframe,object,embed,form").remove()
        // Serializing must not add whitespace: locators count the DOM's actual text nodes.
        document.outputSettings().syntax(Document.OutputSettings.Syntax.xml).prettyPrint(false)
        return MarkdownDocument(metadata["title"]?.firstOrNull(), metadata["author"]?.joinToString(", "),
            document.body().html(), headings, sources)
    }

    fun slug(title: String): String = Normalizer.normalize(title, Normalizer.Form.NFC)
        .lowercase(Locale.ROOT).trim().replace(Regex("[^\\p{L}\\p{N}\\p{M}\\s_-]"), "")
        .replace(Regex("[\\s_]+"), "-")

    /** Relative assets only; percent-encoded traversal must obey the same boundary. */
    fun safeRelativePath(source: String): String? {
        val value = runCatching { URLDecoder.decode(source.replace("+", "%2B"), "UTF-8") }.getOrNull()
            ?.substringBefore('#')?.substringBefore('?') ?: return null
        if (value.isBlank() || value.startsWith('/') || value.contains('\\') || value.contains(':') || value.contains('\u0000')) return null
        val parts = value.split('/').filter { it.isNotEmpty() && it != "." }
        if (parts.any { it == ".." }) return null
        return parts.joinToString("/").takeIf { it.isNotEmpty() }
    }

    const val STYLE = """
      body { line-height:1.7; overflow-wrap:break-word; }
      h1,h2,h3,h4,h5,h6,li,blockquote,pre,td,th { text-indent:0 !important; }
      img { max-width:100%; height:auto; }
      table { width:100%; max-width:100%; table-layout:fixed; border-collapse:collapse; }
      th,td { border:1px solid currentColor; padding:.3em; overflow-wrap:anywhere; }
      pre { white-space:pre-wrap; overflow-wrap:anywhere; word-break:break-word; }
      pre,code,pre * { font-family:monospace !important; }
      blockquote { margin-inline:1em; border-inline-start:2px solid currentColor; padding-inline-start:.8em; }
      .md-missing-image { opacity:.65; }
    """
}
