package com.huangder.lumibooks.data.remote

import java.io.ByteArrayInputStream
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.jsoup.Jsoup
import org.w3c.dom.Element
import org.w3c.dom.Node

/** Parses OPDS 1.x (Atom) catalog feeds into [RemotePage]s. Pure and Android-free. */
object OpdsFeedParser {
    private const val ATOM_NS = "http://www.w3.org/2005/Atom"
    private const val REL_ACQUISITION = "http://opds-spec.org/acquisition"
    private const val REL_IMAGE = "http://opds-spec.org/image"
    private const val REL_THUMBNAIL = "http://opds-spec.org/image/thumbnail"
    private val NON_FREE_ACQUISITIONS = setOf("buy", "borrow", "subscribe", "sample")
    private val DRM_TYPES = setOf(
        "application/vnd.adobe.adept+xml",
        "application/vnd.readium.lcp.license.v1.0+json",
        "application/vnd.readium.license.status.v1.0+json"
    )

    /** True when the payload looks like an Atom feed rather than an HTML login page. */
    fun looksLikeAtom(body: ByteArray): Boolean {
        val head = body.copyOfRange(0, minOf(body.size, 2048)).toString(Charsets.UTF_8)
        return Regex("<\\s*([A-Za-z0-9_]+:)?feed\\b").containsMatchIn(head)
    }

    fun looksLikeJson(body: ByteArray, contentType: String?): Boolean =
        contentType.orEmpty().contains("json", ignoreCase = true) ||
            body.firstOrNull { !it.toInt().toChar().isWhitespace() }?.toInt()?.toChar() == '{'

    /** OpenSearch description documents point at the real search URL template. */
    fun parseOpenSearchTemplate(body: ByteArray, baseUrl: HttpUrl): String? {
        val root = runCatching { parse(body).documentElement }.getOrNull() ?: return null
        return root.children("Url")
            .filter { url ->
                val type = url.getAttribute("type").lowercase()
                type.contains("atom") || type.contains("opds")
            }
            .ifEmpty { root.children("Url") }
            .firstNotNullOfOrNull { url ->
                url.getAttribute("template").takeIf { it.contains("{searchTerms}") }
            }
            ?.let { resolveTemplate(baseUrl, it) }
    }

    fun parse(body: ByteArray, baseUrl: HttpUrl, sourceId: String): RemotePage {
        val document = runCatching { parse(body) }.getOrElse { error ->
            throw RemoteException(RemoteErrorKind.NOT_A_CATALOG, message = "Invalid OPDS feed", cause = error)
        }
        val feed = document.documentElement
        if (feed == null || feed.localNameOrName() != "feed") {
            throw RemoteException(RemoteErrorKind.NOT_A_CATALOG, message = "Not an Atom feed")
        }
        val feedBase = xmlBase(feed, baseUrl)
        val feedLinks = feed.children("link")
        val next = feedLinks.firstOrNull { it.getAttribute("rel").split(' ').contains("next") }
            ?.let { resolve(feedBase, it.getAttribute("href")) }
        val searchLink = feedLinks.firstOrNull { it.getAttribute("rel").split(' ').contains("search") }
        val searchTemplate = searchLink?.getAttribute("href")?.let { href ->
            if (href.contains("{searchTerms}")) resolveTemplate(feedBase, href) else null
        }
        val searchDescription = if (searchTemplate == null) {
            searchLink?.getAttribute("href")?.let { resolve(feedBase, it) }
        } else {
            null
        }

        val folders = mutableListOf<RemoteFolder>()
        val books = mutableListOf<RemoteBookItem>()
        for (entry in feed.children("entry")) {
            val entryBase = xmlBase(entry, feedBase)
            val links = entry.children("link")
            val title = entry.childText("title").ifBlank { "—" }
            val acquisitions = links.filter { isAcquisition(it) }
            if (acquisitions.isNotEmpty()) {
                books += toBook(entry, entryBase, links, acquisitions, title, sourceId)
                continue
            }
            val navigation = links.firstOrNull { isNavigation(it) } ?: continue
            val href = resolve(entryBase, navigation.getAttribute("href")) ?: continue
            folders += RemoteFolder(
                title = title,
                url = href,
                subtitle = entry.childText("content").ifBlank { entry.childText("summary") }
                    .let(::plainText)
                    .takeIf(String::isNotBlank),
                coverUrl = imageUrl(links, entryBase)
            )
        }
        return RemotePage(
            title = feed.childText("title").ifBlank { baseUrl.host },
            url = baseUrl.toString(),
            // Feeds sometimes repeat an entry; lazy lists require unique keys.
            folders = folders.distinctBy { it.url },
            books = books.distinctBy { it.key },
            nextUrl = next,
            searchTemplate = searchTemplate ?: searchDescription?.let { "$OPENSEARCH_PREFIX$it" }
        )
    }

    /** Marker for a search link that still needs its OpenSearch description fetched. */
    const val OPENSEARCH_PREFIX = "opensearch:"

    private fun toBook(
        entry: Element,
        base: HttpUrl,
        links: List<Element>,
        acquisitions: List<Element>,
        title: String,
        sourceId: String
    ): RemoteBookItem {
        val formats = acquisitions.mapNotNull { link ->
            val rel = link.getAttribute("rel")
            if (NON_FREE_ACQUISITIONS.any { rel.endsWith("/$it") }) return@mapNotNull null
            val type = RemoteBookFiles.normalizeMimeType(link.getAttribute("type"))
            if (type in DRM_TYPES || hasDrmIndirection(link)) return@mapNotNull null
            val href = resolve(base, link.getAttribute("href")) ?: return@mapNotNull null
            val extension = RemoteBookFiles.extensionForMimeType(type)
                ?: RemoteBookFiles.extensionFromName(href)
            RemoteFormat(
                url = href,
                extension = extension,
                mimeType = type,
                sizeBytes = link.getAttribute("length").toLongOrNull()
                    ?: link.attributeNS("http://purl.org/dc/terms/", "extent")?.let(::parseExtent)
                    ?: 0L,
                label = if (extension == null) RemoteBookFiles.unsupportedLabelForMimeType(type) else null
            )
        }.distinctBy { it.url }
            .sortedBy { RemoteBookFiles.formatPreference(it.extension) }
        val authors = entry.children("author").mapNotNull { author ->
            author.childText("name").takeIf(String::isNotBlank)
        }.ifEmpty {
            entry.childrenAnyNs("creator").map { it.textContent.trim() }.filter(String::isNotBlank)
        }
        val summary = entry.childrenAnyNs("content").firstOrNull()?.let(::entryText)
            ?.takeIf(String::isNotBlank)
            ?: entry.childrenAnyNs("summary").firstOrNull()?.let(::entryText)?.takeIf(String::isNotBlank)
        val image = imageUrl(links, base)
        val thumbnail = thumbnailUrl(links, base)
        val id = entry.childText("id").ifBlank { formats.firstOrNull()?.url ?: title }
        return RemoteBookItem(
            sourceId = sourceId,
            key = "opds:$id",
            title = title,
            authors = authors,
            summary = summary,
            publisher = entry.childrenAnyNs("publisher").firstOrNull()?.textContent?.trim()?.takeIf(String::isNotBlank),
            language = entry.childrenAnyNs("language").firstOrNull()?.textContent?.trim()?.takeIf(String::isNotBlank),
            published = (entry.childrenAnyNs("issued").firstOrNull()
                ?: entry.childrenAnyNs("date").firstOrNull()
                ?: entry.children("published").firstOrNull())
                ?.textContent?.trim()?.takeIf(String::isNotBlank),
            categories = entry.children("category").mapNotNull { category ->
                category.getAttribute("label").ifBlank { category.getAttribute("term") }.takeIf(String::isNotBlank)
            }.distinct(),
            coverUrl = image ?: thumbnail,
            thumbnailUrl = thumbnail ?: image,
            formats = formats
        )
    }

    private fun imageUrl(links: List<Element>, base: HttpUrl): String? =
        links.firstOrNull {
            it.getAttribute("rel") == REL_IMAGE || it.getAttribute("rel") == "x-stanza-cover-image"
        }?.let { resolve(base, it.getAttribute("href")) }
            ?: thumbnailUrl(links, base)

    private fun thumbnailUrl(links: List<Element>, base: HttpUrl): String? =
        links.firstOrNull {
            it.getAttribute("rel") == REL_THUMBNAIL || it.getAttribute("rel") == "x-stanza-cover-image-thumbnail"
        }?.let { resolve(base, it.getAttribute("href")) }

    private fun isAcquisition(link: Element): Boolean {
        val rel = link.getAttribute("rel")
        return rel == REL_ACQUISITION || rel.startsWith("$REL_ACQUISITION/")
    }

    private fun isNavigation(link: Element): Boolean {
        val type = link.getAttribute("type").lowercase()
        val rel = link.getAttribute("rel")
        if (rel.startsWith(REL_IMAGE) || rel == "alternate" && type.startsWith("text/html")) return false
        return type.contains("atom+xml") || type.contains("opds-catalog") ||
            rel == "subsection" || rel.startsWith("http://opds-spec.org/sort/") ||
            rel == "http://opds-spec.org/featured" || rel == "http://opds-spec.org/recommended" ||
            rel == "http://opds-spec.org/crawlable"
    }

    private fun hasDrmIndirection(link: Element): Boolean {
        var child = link.firstChild
        while (child != null) {
            if (child is Element && child.localNameOrName() == "indirectAcquisition") {
                val type = RemoteBookFiles.normalizeMimeType(child.getAttribute("type"))
                if (type in DRM_TYPES || hasDrmIndirection(child)) return true
            }
            child = child.nextSibling
        }
        return false
    }

    /** Atom `content`/`summary` may be text, escaped HTML or inline XHTML. */
    private fun entryText(element: Element): String {
        val type = element.getAttribute("type").lowercase()
        val raw = if (type == "xhtml") element.textContentPreservingBreaks() else element.textContent
        return if (type == "html" || type == "xhtml" || raw.contains('<')) plainText(raw) else raw.trim()
    }

    private fun plainText(html: String): String {
        val document = Jsoup.parseBodyFragment(html.replace(Regex("(?i)<br\\s*/?>"), "\n"))
        document.outputSettings().prettyPrint(false)
        document.select("p, div, li").forEach { it.appendText("\n") }
        return document.wholeText()
            .lines()
            .joinToString("\n") { it.trim() }
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()
    }

    private fun parseExtent(value: String): Long? {
        val match = Regex("([0-9.]+)\\s*(B|KB|KiB|MB|MiB|GB|GiB)?", RegexOption.IGNORE_CASE).find(value) ?: return null
        val number = match.groupValues[1].toDoubleOrNull() ?: return null
        val multiplier = when (match.groupValues[2].lowercase()) {
            "kb", "kib" -> 1024.0
            "mb", "mib" -> 1024.0 * 1024
            "gb", "gib" -> 1024.0 * 1024 * 1024
            else -> 1.0
        }
        return (number * multiplier).toLong().takeIf { it > 0L }
    }

    private fun resolve(base: HttpUrl, href: String?): String? {
        val value = href?.trim()?.takeIf(String::isNotBlank) ?: return null
        val resolved = base.resolve(value) ?: return null
        return resolved.takeIf { it.scheme == "http" || it.scheme == "https" }?.toString()
    }

    /** Resolves a URL template without letting HttpUrl percent-encode its `{placeholders}`. */
    private fun resolveTemplate(base: HttpUrl, template: String): String? {
        val placeholder = "lumisearchterms"
        // Only {searchTerms} is filled in; optional parameters such as {startPage?} are dropped.
        val prepared = template
            .replace("{searchTerms}", placeholder)
            .replace(Regex("\\{[^}]*\\}"), "")
        val resolved = resolve(base, prepared) ?: return null
        return resolved.replace(placeholder, "{searchTerms}")
    }

    private fun xmlBase(element: Element, inherited: HttpUrl): HttpUrl {
        val base = element.getAttributeNS(XMLConstants.XML_NS_URI, "base").takeIf(String::isNotBlank)
            ?: return inherited
        return inherited.resolve(base) ?: base.toHttpUrlOrNull() ?: inherited
    }

    private fun parse(body: ByteArray): org.w3c.dom.Document {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            runCatching { isXIncludeAware = false }
            runCatching { setExpandEntityReferences(false) }
            runCatching { setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true) }
            runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
            runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
            runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
            runCatching { setAttribute("http://javax.xml.XMLConstants/property/accessExternalDTD", "") }
            runCatching { setAttribute("http://javax.xml.XMLConstants/property/accessExternalSchema", "") }
        }
        // Android's parser ignores disallow-doctype-decl; reject DTDs ourselves before parsing.
        val head = body.copyOfRange(0, minOf(body.size, 4096)).toString(Charsets.ISO_8859_1)
        require(!head.contains("<!DOCTYPE", ignoreCase = true)) { "DOCTYPE is not allowed" }
        return factory.newDocumentBuilder().parse(ByteArrayInputStream(body))
    }

    private fun Node.localNameOrName(): String = localName ?: nodeName.substringAfter(':')

    /** Direct children in the Atom namespace (or with no namespace, for sloppy feeds). */
    private fun Element.children(name: String): List<Element> = buildList {
        var child = firstChild
        while (child != null) {
            if (child is Element && child.localNameOrName() == name &&
                (child.namespaceURI == null || child.namespaceURI == ATOM_NS ||
                    child.namespaceURI.orEmpty().contains("opensearch", ignoreCase = true))
            ) {
                add(child)
            }
            child = child.nextSibling
        }
    }

    /** Direct children in any namespace (Dublin Core terms are spelled several ways). */
    private fun Element.childrenAnyNs(name: String): List<Element> = buildList {
        var child = firstChild
        while (child != null) {
            if (child is Element && child.localNameOrName() == name) add(child)
            child = child.nextSibling
        }
    }

    private fun Element.childText(name: String): String =
        children(name).firstOrNull()?.textContent?.trim().orEmpty()

    private fun Element.attributeNS(namespace: String, name: String): String? =
        getAttributeNS(namespace, name).takeIf(String::isNotBlank)

    private fun Element.textContentPreservingBreaks(): String = buildString {
        fun visit(node: Node) {
            when (node.nodeType) {
                Node.TEXT_NODE, Node.CDATA_SECTION_NODE -> append(node.nodeValue)
                Node.ELEMENT_NODE -> {
                    val name = node.localNameOrName().lowercase()
                    if (name == "br") append("<br/>")
                    var child = node.firstChild
                    while (child != null) {
                        visit(child)
                        child = child.nextSibling
                    }
                    if (name == "p" || name == "div" || name == "li") append("<br/>")
                }
            }
        }
        visit(this@textContentPreservingBreaks)
    }
}
