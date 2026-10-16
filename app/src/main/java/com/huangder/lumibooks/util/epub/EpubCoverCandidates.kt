package com.huangder.lumibooks.util.epub

import org.jsoup.Jsoup
import org.jsoup.parser.Parser

/** Ordered ZIP paths to raster cover candidates. The caller validates image bytes. */
internal object EpubCoverCandidates {
    private fun isImage(path: String) = path.substringBefore('?').substringBefore('#')
        .substringAfterLast('.', "").lowercase() in setOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "avif")

    fun paths(book: EpubPackage, opfXml: String, readText: (String) -> String?): List<String> {
        val opf = Jsoup.parse(opfXml, "", Parser.xmlParser())
        val items = book.manifest.values.toList()
        val candidates = linkedSetOf<String>()
        val manifestByPath = items.associateBy { it.fullPath }

        fun add(item: EpubManifestItem?, singleImage: Boolean = false) {
            if (item == null) return
            if (isImage(item.fullPath)) {
                candidates += item.fullPath
                return
            }
            if (item.mediaType !in setOf("application/xhtml+xml", "text/html") &&
                !item.fullPath.endsWith(".xhtml", true) && !item.fullPath.endsWith(".html", true)) return
            val html = runCatching { readText(item.fullPath) }.getOrNull() ?: return
            val document = Jsoup.parse(html, "", Parser.xmlParser())
            val sources = document.select("img[src], image[href], image[xlink:href]")
                .mapNotNull { element ->
                    val reference = element.attr("src").ifBlank { element.attr("href") }
                        .ifBlank { element.attr("xlink:href") }
                    EpubPathResolver.resolve(item.fullPath, reference)?.takeIf(::isImage)
                }.distinct()
            if (singleImage) {
                val body = document.selectFirst("body") ?: document
                if (sources.size != 1 || body.text().isNotBlank()) return
            }
            candidates.addAll(sources)
        }

        val coverId = opf.select("metadata meta[name=cover]").firstOrNull()
            ?.attr("content")?.takeIf(String::isNotBlank)
        add(book.manifest[coverId])
        items.filter { "cover-image" in it.properties }.forEach { add(it) }
        opf.select("guide reference[type=cover], guide reference[type=Cover]").forEach { reference ->
            val path = EpubPathResolver.resolve(book.opfPath, reference.attr("href"))
            add(manifestByPath[path])
        }
        book.navigation.filter { it.title.contains("cover", true) || it.title.contains("封面") }
            .forEach { navigation -> add(manifestByPath[navigation.href.substringBefore('#')]) }
        val named = items.filter { it.id.contains("cover", true) || it.href.contains("cover", true) ||
            it.id.contains("封面") || it.href.contains("封面") }
        named.filterNot { isImage(it.fullPath) }.forEach { add(it) }
        named.filter { isImage(it.fullPath) }.forEach { add(it) }
        book.spine.firstOrNull { it.linear }?.manifestItem?.let { add(it, singleImage = true) }
        return candidates.toList()
    }
}
