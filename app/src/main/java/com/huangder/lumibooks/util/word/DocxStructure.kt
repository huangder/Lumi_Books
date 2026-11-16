package com.huangder.lumibooks.util.word

import org.apache.commons.xml.secure.SecureDocumentBuilderFactory
import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

/** Word outline styles can have arbitrary IDs and display names. Mammoth still renders the content. */
internal object DocxStructure {
    private const val DC = "http://purl.org/dc/elements/1.1/"
    private const val MAIN_DOCUMENT = "word/document.xml"
    private val wordNamespaces = setOf(
        "http://schemas.openxmlformats.org/wordprocessingml/2006/main",
        "http://purl.oclc.org/ooxml/wordprocessingml/main"
    )
    private val headingName = Regex("(?:heading|标题|標題|見出し|제목)\\s*([1-9])", RegexOption.IGNORE_CASE)

    fun metadata(zip: ZipFile): WordMetadata {
        val properties = zip.getEntry("docProps/core.xml") ?: return WordMetadata()
        if (properties.size !in 1..(128L * 1024)) return WordMetadata()
        // The namespace URI identifies the property even when the producer uses a different prefix.
        val core = try {
            zip.xml(properties.name) ?: return WordMetadata()
        } catch (_: org.xml.sax.SAXException) {
            return WordMetadata()
        }
        fun property(name: String) = core.getElementsByTagNameNS(DC, name).item(0)?.textContent?.trim()
            ?.takeIf { it.isNotBlank() && '\uFFFD' !in it }
        return WordMetadata(property("title"), property("creator"))
    }

    fun styleMap(zip: ZipFile): String {
        val document = zip.xml("word/styles.xml") ?: return ""
        val ns = document.documentElement.namespaceURI?.takeIf(wordNamespaces::contains) ?: return ""
        val elements = document.documentElement.getElementsByTagNameNS(ns, "style")
        val styles = (0 until elements.length).mapNotNull { index ->
            val style = elements.item(index) as Element
            val id = style.getAttributeNS(ns, "styleId")
            if (id.isBlank() || style.getAttributeNS(ns, "type") != "paragraph") return@mapNotNull null
            val name = style.child(ns, "name")?.getAttributeNS(ns, "val").orEmpty()
            val outline = style.child(ns, "pPr")?.child(ns, "outlineLvl")?.getAttributeNS(ns, "val")?.toIntOrNull()
                ?: headingName.matchEntire(name.trim())?.groupValues?.get(1)?.toInt()?.minus(1)
                ?: headingName.matchEntire(id.trim())?.groupValues?.get(1)?.toInt()?.minus(1)
            id to ParagraphStyle(outline, style.child(ns, "basedOn")?.getAttributeNS(ns, "val"))
        }.toMap()
        return styles.keys.mapNotNull { id ->
            val visited = HashSet<String>()
            val outline = generateSequence(id) { styles[it]?.parent?.takeIf(String::isNotBlank) }
                .takeWhile(visited::add).mapNotNull { styles[it]?.outline }.firstOrNull()
                ?.takeIf { it in 0..8 } ?: return@mapNotNull null
            if (id.any { it == '\r' || it == '\n' }) return@mapNotNull null
            val escaped = id.mapIndexed { index, c ->
                val ordinary = c in 'a'..'z' || c in 'A'..'Z' || c == '-' || c == '_' || (index > 0 && c in '0'..'9')
                if (ordinary) c.toString() else "\\$c"
            }.joinToString("")
            "p.$escaped => h${(outline + 1).coerceAtMost(6)}:fresh"
        }.joinToString("\n")
    }

    /** Mammoth sees a paragraph's style ID, but has no matcher for a direct outline override. */
    fun normalizeDirectOutline(zip: ZipFile, output: File): String {
        val document = zip.xml(MAIN_DOCUMENT) ?: return ""
        val ns = document.documentElement.namespaceURI?.takeIf(wordNamespaces::contains) ?: return ""
        val properties = document.getElementsByTagNameNS(ns, "pPr")
        val outlines = (0 until properties.length).mapNotNull { index ->
            val props = properties.item(index) as Element
            if (props.parentNode.localName != "p") return@mapNotNull null
            val level = props.child(ns, "outlineLvl")?.getAttributeNS(ns, "val")?.toIntOrNull()
                ?.takeIf { it in 0..9 } ?: return@mapNotNull null
            props to level
        }
        if (outlines.isEmpty()) return ""
        val prefix = document.documentElement.prefix?.let { "$it:" }.orEmpty()
        val styleIds = mutableMapOf<Int, String>()
        val styles = zip.xml("word/styles.xml")
        val nodes = styles?.getElementsByTagNameNS(ns, "style")
        val existingIds = (0 until (nodes?.length ?: 0)).mapTo(HashSet()) { index ->
            (nodes?.item(index) as Element).getAttributeNS(ns, "styleId")
        }
        for ((props, level) in outlines) {
            val id = styleIds.getOrPut(level) {
                generateSequence("LumiDirectOutline$level") { "${it}_" }.first(existingIds::add)
            }
            val style = props.child(ns, "pStyle") ?: document.createElementNS(ns, "${prefix}pStyle").also {
                props.insertBefore(it, props.firstChild)
            }
            val attributePrefix = if (prefix.isEmpty()) "lumiWord:" else prefix
            style.setAttributeNS(ns, "${attributePrefix}val", id)
        }
        if (styleIds.isEmpty()) return ""
        ZipOutputStream(output.outputStream().buffered()).use { normalized ->
            for (entry in zip.entries().asSequence()) {
                checkWordCancellation()
                normalized.putNextEntry(ZipEntry(entry.name))
                if (entry.name == MAIN_DOCUMENT) {
                    TransformerFactory.newInstance().newTransformer().transform(DOMSource(document), StreamResult(normalized))
                } else if (!entry.isDirectory) {
                    zip.getInputStream(entry).use { copyWordBytes(it, normalized, entry.size) }
                }
                normalized.closeEntry()
            }
        }
        return styleIds.entries.joinToString("\n") { (level, id) ->
            "p.$id => ${if (level == 9) "p" else "h${(level + 1).coerceAtMost(6)}"}:fresh"
        }
    }

    private data class ParagraphStyle(val outline: Int?, val parent: String?)

    private fun ZipFile.xml(path: String): Document? = getEntry(path)?.let { entry ->
        getInputStream(entry).use { SecureDocumentBuilderFactory.newNSDocumentBuilder().parse(it) }
    }

    private fun Element.child(ns: String, name: String): Element? = generateSequence(firstChild) { it.nextSibling }
        .filterIsInstance<Element>().firstOrNull { it.namespaceURI == ns && it.localName == name }
}
