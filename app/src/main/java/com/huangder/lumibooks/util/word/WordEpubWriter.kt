package com.huangder.lumibooks.util.word

import com.huangder.lumibooks.util.parser.TxtTocRuleBuiltIns
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Entities
import org.jsoup.safety.Cleaner
import org.jsoup.safety.Safelist
import java.io.File
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

internal object WordEpubWriter {
    private const val BODY = "document.xhtml"
    private const val CONTAINER = """<container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container"><rootfiles><rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/></rootfiles></container>"""
    private val emphasis = Regex("""(font-weight\s*:\s*bold|font-style\s*:\s*italic|text-decoration\s*:\s*(?:underline|line-through))\s*(?:;|$)""", RegexOption.IGNORE_CASE)
    private val noteReference = Regex("#word-(?:footnote|endnote)-[0-9]+")
    private val chapterLine by lazy {
        Regex(TxtTocRuleBuiltIns.all.first().chapterRegex, RegexOption.IGNORE_CASE)
    }
    private val layout = """
        table { border-collapse: collapse; max-width: 100%; }
        th, td { border: 1px solid; padding: .25em .4em; overflow-wrap: anywhere; }
        img { max-width: 100%; height: auto; }
    """.trimIndent()

    fun write(html: String, metadata: WordMetadata, images: Map<String, File>, key: String, output: File) {
        val source = Jsoup.parse(html, "https://word.invalid/")
        // POI emits CSS classes for character emphasis. Copy just these fixed declarations.
        val styles = source.select("style").flatMap { style ->
            Regex("""\.([a-zA-Z][\w-]*)\s*\{([^{}]*)}""").findAll(style.data()).toList()
        }.associate { it.groupValues[1] to it.groupValues[2] }
        source.select("[class], [style]").forEach { element ->
            val rules = element.classNames().mapNotNull(styles::get) + element.attr("style")
            val kept = rules.flatMap { rule -> emphasis.findAll(rule).map { it.groupValues[1] }.toList() }
            if (kept.isEmpty()) element.removeAttr("style") else element.attr("style", kept.distinct().joinToString(";"))
        }
        source.select("img").filter { it.attr("src") !in images }.forEach { it.remove() }
        source.select("a[href]").forEach { link ->
            val href = link.attr("href").trim()
            if (!href.startsWith('#') && !href.startsWith("http://", true) &&
                !href.startsWith("https://", true) && !href.startsWith("mailto:", true)) {
                link.removeAttr("href")
            }
        }
        val document = Cleaner(Safelist.relaxed().addTags("section", "aside", "s", "del", "ins")
            .addAttributes(":all", "id", "style", "lang", "dir")
            .addAttributes("ol", "start")
            .addAttributes("li", "value")
            .addProtocols("a", "href", "#", "mailto")
            .removeProtocols("a", "href", "ftp")
            .preserveRelativeLinks(true)).clean(source)
        document.select("a[href]").filter { noteReference.matches(it.attr("href")) }.forEach { it.attr("epub:type", "noteref") }
        document.select("li[id^=word-footnote-], li[id^=word-endnote-]").forEach { it.attr("epub:type", "footnote") }
        document.outputSettings().syntax(Document.OutputSettings.Syntax.xml).escapeMode(Entities.EscapeMode.xhtml).prettyPrint(false)
        val headingSelector = "h1, h2, h3, h4, h5, h6"
        var headings = document.select(headingSelector).filter { it.text().isNotBlank() }
        if (headings.isEmpty()) {
            val candidates = document.body().children().filter { paragraph ->
                val text = paragraph.text()
                paragraph.normalName() == "p" && text.length in 2..100 &&
                    !text.endsWith('。') && chapterLine.matches(text)
            }
            if (candidates.size >= 2) {
                candidates.forEach { it.tagName("h2") }
                headings = candidates
            }
        }
        val ids = document.select("[id]").map { it.id() }.toHashSet()
        headings.forEachIndexed { index, heading ->
            if (heading.id().isBlank()) {
                val id = generateSequence(index + 1) { it + 1 }.map { "lumi-heading-$it" }.first(ids::add)
                heading.attr("id", id)
            }
        }
        val firstHeading = headings.firstOrNull()
        val frontMatter = if (headings.size > 1 && firstHeading != null) {
            // The EPUB reader slices at navigation anchors. Keep any leading text or illustrations.
            val body = document.body()
            val preceding = body.allElements.drop(1).takeWhile { it !== firstHeading }
            if (preceding.any { it.normalName() == "img" || it.ownText().isNotBlank() }) {
                val id = generateSequence("lumi-word-start") { "$it-" }.first(ids::add)
                body.prependElement("div").attr("id", id)
                "<li><a href=\"$BODY#$id\">${xml(metadata.title.orEmpty().ifBlank { "Word" })}</a></li>"
            } else ""
        } else ""
        val body = document.body().html()
        if (document.body().text().isBlank() && document.select("img").isEmpty()) throw WordException(WordFailure.INVALID)
        val title = xml(metadata.title.orEmpty().trim())
        val author = xml(metadata.author.orEmpty().trim())
        val nav = frontMatter + headingNavigation(headings)
            .ifEmpty { "<li><a href=\"$BODY\">${title.ifBlank { "Word" }}</a></li>" }
        val usedImages = document.select("img[src]").map { it.attr("src") }.toSet()
        val assets = images.filterKeys { it in usedImages }
        val imageItems = assets.keys.mapIndexed { index, path ->
            val type = path.substringAfterLast('.').let { if (it == "jpg") "jpeg" else it }
            """<item id="image$index" href="$path" media-type="image/$type"/>"""
        }.joinToString("")
        ZipOutputStream(output.outputStream().buffered()).use { zip ->
            val mime = "application/epub+zip".toByteArray(Charsets.US_ASCII)
            zip.putNextEntry(ZipEntry("mimetype").apply {
                method = ZipEntry.STORED
                size = mime.size.toLong()
                compressedSize = size
                crc = CRC32().apply { update(mime) }.value
                time = 0L
            })
            zip.write(mime)
            zip.closeEntry()
            zip.text("META-INF/container.xml", CONTAINER)
            zip.text("OEBPS/content.opf", """<package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="book-id"><metadata xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:identifier id="book-id">urn:sha256:$key</dc:identifier><dc:title>$title</dc:title><dc:creator>$author</dc:creator><dc:language>und</dc:language><meta property="dcterms:modified">2000-01-01T00:00:00Z</meta></metadata><manifest><item id="body" href="$BODY" media-type="application/xhtml+xml"/><item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>$imageItems</manifest><spine><itemref idref="body"/></spine></package>""")
            zip.text("OEBPS/$BODY", xhtml(title, body, layout))
            zip.text("OEBPS/nav.xhtml", xhtml(title, """<nav epub:type="toc"><ol>$nav</ol></nav>"""))
            for ((path, image) in assets) {
                zip.putNextEntry(ZipEntry("OEBPS/$path").apply { time = 0L })
                image.inputStream().buffered().use { copyWordBytes(it, zip, image.length()) }
                zip.closeEntry()
            }
        }
    }

    private fun xhtml(title: String, body: String, css: String = "") =
        """<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops"><head><title>$title</title><meta charset="UTF-8" />${if (css.isEmpty()) "" else "<style>$css</style>"}</head><body>$body</body></html>"""

    private fun headingNavigation(headings: List<org.jsoup.nodes.Element>): String = buildString {
        val levels = mutableListOf<Int>()
        for (heading in headings) {
            val level = heading.tagName().last().digitToInt()
            if (levels.isEmpty()) {
                levels.add(level)
            } else if (level > levels.last()) {
                append("<ol>")
                levels.add(level)
            } else {
                append("</li>")
                while (levels.size > 1 && level < levels.last()) {
                    levels.removeAt(levels.lastIndex)
                    append("</ol></li>")
                }
                levels[levels.lastIndex] = level
            }
            append("<li><a href=\"$BODY#${xml(heading.id())}\">${xml(heading.text())}</a>")
        }
        if (levels.isNotEmpty()) {
            append("</li>")
            repeat(levels.size - 1) { append("</ol></li>") }
        }
    }

    private fun ZipOutputStream.text(path: String, content: String) {
        checkWordCancellation()
        putNextEntry(ZipEntry(path).apply { time = 0L })
        write(content.toByteArray(Charsets.UTF_8))
        closeEntry()
    }

    private fun xml(text: String): String = Entities.escape(text, Document.OutputSettings().escapeMode(Entities.EscapeMode.xhtml))
        .replace("\"", "&quot;")
}
