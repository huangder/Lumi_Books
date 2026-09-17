package com.huangder.lumibooks.util.parser

import org.w3c.dom.Element
import javax.xml.parsers.DocumentBuilderFactory
import java.io.ByteArrayInputStream

/**
 * Metadata carried by the `ComicInfo.xml` sidecar that comic archives commonly ship at their root.
 * Every field is optional because the sidecar itself is optional and often only partly filled.
 */
internal data class CbzComicInfo(
    val title: String? = null,
    val series: String? = null,
    val number: String? = null,
    val volume: String? = null,
    val writer: String? = null,
    val penciller: String? = null,
    val author: String? = null,
    val summary: String? = null,
    val manga: String? = null,
    val pageCount: Int? = null,
    val language: String? = null
) {
    /** `Manga=YesAndRightToLeft` marks a Japanese-style volume that starts on the right. */
    val prefersRightToLeft: Boolean
        get() = manga?.replace(" ", "")?.startsWith("YesAndRightToLeft", ignoreCase = true) == true

    /** Title falls back through Title → "Series #Number" → Series → the file name. */
    fun resolveTitle(fallback: String): String {
        title?.trim()?.takeIf(String::isNotEmpty)?.let { return it }
        val seriesName = series?.trim().orEmpty()
        val numberLabel = number?.trim().orEmpty()
        if (seriesName.isNotEmpty() && numberLabel.isNotEmpty()) return "$seriesName #$numberLabel"
        if (seriesName.isNotEmpty()) return seriesName
        return fallback
    }

    fun resolveAuthor(unknownAuthor: String): String {
        val candidates = listOf(writer, penciller, author)
            .mapNotNull { it?.trim() }
            .firstOrNull(String::isNotEmpty)
        return candidates?.split(',', ';')
            ?.map(String::trim)
            ?.filter(String::isNotEmpty)
            ?.distinct()
            ?.joinToString(", ")
            ?.takeIf(String::isNotEmpty)
            ?: unknownAuthor
    }

    companion object {
        /** Root-element names seen in the wild; anything else is ignored. */
        private const val ROOT_ELEMENT = "ComicInfo"

        fun parse(xml: String?): CbzComicInfo? {
            val payload = xml?.takeIf { it.isNotBlank() } ?: return null
            val document = runCatching {
                DocumentBuilderFactory.newInstance().apply {
                    isNamespaceAware = false
                    // The sidecar comes from untrusted archives: no external entity resolution.
                    runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
                    runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
                    runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
                    isXIncludeAware = false
                    isExpandEntityReferences = false
                }.newDocumentBuilder().parse(ByteArrayInputStream(payload.toByteArray(Charsets.UTF_8)))
            }.getOrNull() ?: return null

            val root = document.documentElement ?: return null
            if (!root.nodeName.equals(ROOT_ELEMENT, ignoreCase = true)) return null
            val info = CbzComicInfo(
                title = root.text("Title"),
                series = root.text("Series"),
                number = root.text("Number"),
                volume = root.text("Volume"),
                writer = root.text("Writer"),
                penciller = root.text("Penciller"),
                author = root.text("Author"),
                summary = root.text("Summary"),
                manga = root.text("Manga"),
                pageCount = root.text("PageCount")?.toIntOrNull(),
                language = root.text("LanguageISO")
            )
            return info.takeIf { it != CbzComicInfo() }
        }

        private fun Element.text(tagName: String): String? {
            val nodes = getElementsByTagName(tagName)
            if (nodes.length == 0) return null
            val value = nodes.item(0)?.textContent?.trim()
            return value?.takeIf(String::isNotEmpty)
        }
    }
}
