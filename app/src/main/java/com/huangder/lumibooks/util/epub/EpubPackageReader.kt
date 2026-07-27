package com.huangder.lumibooks.util.epub

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.parser.Parser
import org.w3c.dom.Document
import org.w3c.dom.Node
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory

object EpubPackageReader {
    fun read(filePath: String): EpubPackage = ZipFile(filePath).use { zip ->
        val entries = zip.entries().asSequence().associateBy { it.name.lowercase() }
        fun find(path: String): ZipEntry? = zip.getEntry(path) ?: entries[path.lowercase()]
        fun xml(path: String): Document {
            val entry = find(path) ?: error("Missing EPUB resource: $path")
            return zip.getInputStream(entry).use(::parseXml)
        }

        val container = xml("META-INF/container.xml")
        val opfPath = EpubPathResolver.normalize(
            container.elements("rootfile").firstOrNull()?.attribute("full-path").orEmpty()
        ) ?: error("EPUB container does not contain a valid rootfile")
        val basePath = opfPath.substringBeforeLast('/', "")
        val opf = xml(opfPath)
        val metadata = opf.elements("metadata").firstOrNull()
        val title = metadata?.descendants("title")?.firstOrNull()?.textContent?.trim().orEmpty()
            .ifBlank { filePath.substringAfterLast('/').substringAfterLast('\\').substringBeforeLast('.') }
        val author = metadata?.descendants("creator")?.firstOrNull()?.textContent?.trim().orEmpty()
            .ifBlank { "\u672A\u77E5\u4F5C\u8005" }
        val metaProperties = metadata?.descendants("meta").orEmpty().mapNotNull { node ->
            node.attribute("property").takeIf(String::isNotBlank)?.let { it to node.textContent.trim() }
        }.toMap()

        val manifest = opf.elements("manifest").firstOrNull()?.descendants("item").orEmpty()
            .mapNotNull { item ->
                val id = item.attribute("id")
                val href = item.attribute("href")
                if (id.isBlank() || href.isBlank()) return@mapNotNull null
                val fullPath = EpubPathResolver.resolve(opfPath, href) ?: return@mapNotNull null
                EpubManifestItem(
                    id = id,
                    href = href,
                    fullPath = fullPath,
                    mediaType = item.attribute("media-type").ifBlank { EpubMimeTypes.fromPath(fullPath) },
                    properties = item.attribute("properties").words()
                )
            }.associateBy { it.id }

        val packageLayout = metaProperties["rendition:layout"].toLayout()
        val spineElement = opf.elements("spine").firstOrNull()
        val progression = when (spineElement?.attribute("page-progression-direction")?.lowercase()) {
            "rtl" -> EpubPageProgressionDirection.RTL
            "ltr" -> EpubPageProgressionDirection.LTR
            else -> EpubPageProgressionDirection.DEFAULT
        }
        val spine = spineElement?.descendants("itemref").orEmpty().mapNotNull { itemRef ->
            val idRef = itemRef.attribute("idref")
            val manifestItem = manifest[idRef] ?: return@mapNotNull null
            val properties = itemRef.attribute("properties").words()
            EpubSpineItem(
                idRef = idRef,
                manifestItem = manifestItem,
                linear = itemRef.attribute("linear").lowercase() != "no",
                properties = properties,
                renditionLayout = when {
                    "rendition:layout-pre-paginated" in properties -> EpubRenditionLayout.PRE_PAGINATED
                    "rendition:layout-reflowable" in properties -> EpubRenditionLayout.REFLOWABLE
                    else -> packageLayout
                }
            )
        }
        require(spine.isNotEmpty()) { "EPUB package does not contain a readable spine" }

        EpubPackage(
            filePath = filePath,
            opfPath = opfPath,
            basePath = basePath,
            title = title,
            author = author,
            manifest = manifest,
            spine = spine,
            navigation = parseNavigation(zip, entries, manifest, spineElement?.attribute("toc")),
            pageProgressionDirection = progression,
            renditionLayout = packageLayout,
            renditionOrientation = metaProperties["rendition:orientation"],
            renditionSpread = metaProperties["rendition:spread"],
            renditionFlow = metaProperties["rendition:flow"]
        )
    }

    private fun parseNavigation(
        zip: ZipFile,
        entries: Map<String, ZipEntry>,
        manifest: Map<String, EpubManifestItem>,
        ncxId: String?
    ): List<EpubNavigationItem> {
        val navItem = manifest.values.firstOrNull { "nav" in it.properties }
        if (navItem != null) {
            val entry = zip.getEntry(navItem.fullPath) ?: entries[navItem.fullPath.lowercase()]
            if (entry != null) {
                val document = zip.getInputStream(entry).use {
                    Jsoup.parse(it, null, navItem.fullPath, Parser.xmlParser())
                }
                val toc = document.select("nav").firstOrNull {
                    it.attr("epub:type").split(' ').contains("toc") || it.attr("type") == "toc"
                } ?: document.selectFirst("nav")
                if (toc != null) return flattenHtmlNavigation(toc, navItem.fullPath)
            }
        }

        val ncxItem = ncxId?.let(manifest::get)
            ?: manifest.values.firstOrNull { it.mediaType == "application/x-dtbncx+xml" }
            ?: return emptyList()
        val entry = zip.getEntry(ncxItem.fullPath) ?: entries[ncxItem.fullPath.lowercase()] ?: return emptyList()
        val document = zip.getInputStream(entry).use(::parseXml)
        val output = mutableListOf<EpubNavigationItem>()
        document.elements("navMap").firstOrNull()?.childNodes?.asSequence()
            ?.filter { it.localNameValue() == "navPoint" }
            ?.forEach { flattenNcxNavigation(it, ncxItem.fullPath, 1, output) }
        return output
    }

    private fun flattenHtmlNavigation(nav: Element, navPath: String): List<EpubNavigationItem> {
        val output = mutableListOf<EpubNavigationItem>()
        fun visit(list: Element, level: Int) {
            list.children().filter { it.tagName().equals("li", true) }.forEach { item ->
                val anchor = item.children().firstOrNull { it.tagName().equals("a", true) }
                if (anchor != null) {
                    val resolved = EpubPathResolver.resolve(navPath, anchor.attr("href"))
                    if (resolved != null) {
                        val fragment = EpubPathResolver.fragment(anchor.attr("href"))
                        output += EpubNavigationItem(anchor.text().trim(), resolved + fragment?.let { "#$it" }.orEmpty(), level)
                    }
                }
                item.children().filter { it.tagName().equals("ol", true) || it.tagName().equals("ul", true) }
                    .forEach { visit(it, level + 1) }
            }
        }
        nav.children().filter { it.tagName().equals("ol", true) || it.tagName().equals("ul", true) }
            .forEach { visit(it, 1) }
        return output
    }

    private fun flattenNcxNavigation(
        node: Node,
        ncxPath: String,
        level: Int,
        output: MutableList<EpubNavigationItem>
    ) {
        val label = node.descendants("text").firstOrNull()?.textContent?.trim().orEmpty()
        val source = node.childNodes.asSequence().firstOrNull { it.localNameValue() == "content" }
            ?.attribute("src").orEmpty()
        val resolved = EpubPathResolver.resolve(ncxPath, source)
        if (resolved != null) {
            val fragment = EpubPathResolver.fragment(source)
            output += EpubNavigationItem(label, resolved + fragment?.let { "#$it" }.orEmpty(), level)
        }
        node.childNodes.asSequence().filter { it.localNameValue() == "navPoint" }
            .forEach { flattenNcxNavigation(it, ncxPath, level + 1, output) }
    }

    private fun parseXml(input: InputStream): Document {
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
        return factory.newDocumentBuilder().parse(input)
    }

    private fun Document.elements(name: String): List<Node> =
        getElementsByTagNameNS("*", name).asSequence().toList()
    private fun Node.descendants(name: String): List<Node> =
        (if (localNameValue() == name) listOf(this) else emptyList()) +
            childNodes.asSequence().flatMap { it.descendants(name).asSequence() }.toList()
    private fun Node.attribute(name: String): String = attributes?.getNamedItem(name)?.nodeValue.orEmpty()
    private fun Node.localNameValue(): String = localName ?: nodeName.substringAfter(':')
    private fun org.w3c.dom.NodeList.asSequence(): Sequence<Node> = sequence {
        for (index in 0 until length) yield(item(index))
    }
    private fun String.words(): Set<String> = split(Regex("\\s+")).filter(String::isNotBlank).toSet()
    private fun String?.toLayout(): EpubRenditionLayout = if (this == "pre-paginated") {
        EpubRenditionLayout.PRE_PAGINATED
    } else EpubRenditionLayout.REFLOWABLE
}
