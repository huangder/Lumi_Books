package com.huangder.lumibooks.util.epub

import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import org.json.JSONArray
import org.json.JSONObject

/** An occurrence, not a unique image: repeated resources remain separate pages. */
data class EpubComicPage(
    val documentPath: String,
    val imagePath: String,
    val occurrence: Int,
    val chapterIndex: Int,
    val anchor: String? = null
)

data class EpubComicIndex(
    val pages: List<EpubComicPage>,
    val chapterTitles: List<String>,
    val hasOmittedContent: Boolean,
    val rightToLeft: Boolean
) {
    fun pageForChapter(chapter: Int): Int = pages.indexOfFirst { it.chapterIndex >= chapter }
        .takeIf { it >= 0 } ?: pages.lastIndex.coerceAtLeast(0)

    fun find(position: EpubComicPosition): Int? = pages.indexOfFirst {
        it.documentPath == position.documentPath && it.imagePath == position.imagePath &&
            it.occurrence == position.occurrence
    }.takeIf { it >= 0 }

    fun restore(position: EpubComicPosition): Int = find(position) ?: run {
        val sameDocument = pages.indexOfFirst { it.documentPath == position.documentPath }
        if (sameDocument >= 0) sameDocument else pageForChapter(position.chapterIndex)
    }

    fun encode(): String = JSONObject().put("version", VERSION)
        .put("omitted", hasOmittedContent).put("rtl", rightToLeft)
        .put("titles", JSONArray(chapterTitles)).put("pages", JSONArray().apply {
            pages.forEach { put(EpubComicPosition.fromPage(it).json()) }
        }).toString()

    companion object {
        const val VERSION = 1
        fun decode(value: String): EpubComicIndex? = runCatching {
            val json = JSONObject(value)
            require(json.getInt("version") == VERSION)
            val titles = json.getJSONArray("titles")
            val pages = json.getJSONArray("pages")
            EpubComicIndex((0 until pages.length()).map {
                EpubComicPosition.parse(pages.getJSONObject(it)).page()
            }, (0 until titles.length()).map(titles::getString), json.getBoolean("omitted"), json.getBoolean("rtl"))
        }.getOrNull()
    }
}

data class EpubComicPosition(
    val documentPath: String,
    val imagePath: String,
    val occurrence: Int,
    val chapterIndex: Int,
    val anchor: String? = null,
    val scrollFraction: Float = 0f
) {
    fun page() = EpubComicPage(documentPath, imagePath, occurrence, chapterIndex, anchor)
    fun json(): JSONObject = JSONObject().put("type", TYPE).put("version", 1)
        .put("document", documentPath).put("image", imagePath).put("occurrence", occurrence)
        .put("chapter", chapterIndex).put("anchor", anchor)
        .put("scroll", scrollFraction.takeIf(Float::isFinite)?.coerceIn(0f, 1f) ?: 0f)
    fun encode(): String = json().toString()
    companion object {
        const val TYPE = "lumi_epub_comic_position"
        fun fromPage(page: EpubComicPage, fraction: Float = 0f) = EpubComicPosition(
            page.documentPath, page.imagePath, page.occurrence, page.chapterIndex, page.anchor, fraction)
        fun decode(value: String?): EpubComicPosition? = runCatching { parse(JSONObject(value ?: "")) }.getOrNull()
        internal fun parse(json: JSONObject): EpubComicPosition {
            require(json.getString("type") == TYPE && json.getInt("version") == 1)
            val document = json.getString("document")
            val image = json.getString("image")
            require(EpubPathResolver.normalize(document) != null && EpubPathResolver.normalize(image) != null)
            val occurrence = json.getInt("occurrence")
            val chapter = json.getInt("chapter")
            require(occurrence >= 0 && chapter >= 0)
            return EpubComicPosition(document, image, occurrence, chapter,
                json.optString("anchor").takeIf { it.isNotBlank() && it != "null" },
                json.optDouble("scroll", 0.0).toFloat().takeIf(Float::isFinite)?.coerceIn(0f, 1f) ?: 0f)
        }
    }
}

/** Uses the same anchored logical chapter convention as the EPUB text engines. */
internal object EpubComicIndexer {
    private val bitmapExtensions = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "avif")
    fun annotate(book: EpubPackage, path: String, chapter: Int, bytes: ByteArray): String {
        val document = Jsoup.parse(bytes.inputStream(), null, path, Parser.xmlParser())
        var occurrence = 0
        document.select("img,image").forEach { element ->
            val ref = if (element.normalName() == "img") element.attr("src") else element.attr("href").ifBlank { element.attr("xlink:href") }
            val image = EpubPathResolver.resolve(path, ref) ?: return@forEach
            if (!isBitmap(book, image)) return@forEach
            val anchor = (listOf(element) + element.parents()).firstOrNull { it.id().isNotBlank() }?.id()
            element.attr("data-lumi-comic-image", EpubComicPosition(path, image, occurrence++, chapter, anchor).encode())
        }
        return document.outerHtml()
    }

    private fun isBitmap(book: EpubPackage, path: String): Boolean =
        path.substringAfterLast('.').lowercase() in bitmapExtensions ||
            book.manifestByPath[path]?.mediaType in setOf("image/jpeg", "image/png", "image/webp", "image/gif", "image/bmp", "image/avif")

    fun build(book: EpubPackage, read: (String) -> ByteArray?, checkCancelled: () -> Unit = {}): EpubComicIndex {
        val pages = mutableListOf<EpubComicPage>()
        val titles = mutableListOf<String>()
        var omitted = false
        val navigation = book.navigation.groupBy { EpubPathResolver.normalize(it.href)?.lowercase() }
        fun bitmap(path: String) = isBitmap(book, path)
        for (spine in book.spine) {
            checkCancelled()
            val path = spine.manifestItem.fullPath
            val nav = navigation[path.lowercase()].orEmpty()
            val anchored = nav.filter { EpubPathResolver.fragment(it.href) != null }
                .distinctBy { EpubPathResolver.fragment(it.href)?.lowercase() }
            val split = anchored.size >= 2
            val base = titles.size
            titles += if (split) anchored.map { it.title } else listOf(nav.firstOrNull()?.title.orEmpty())
            if (bitmap(path)) {
                pages += EpubComicPage(path, path, 0, base)
                continue
            }
            val bytes = read(path)
            if (bytes == null) { omitted = true; continue }
            val document = Jsoup.parse(bytes.inputStream(), null, path, Parser.xmlParser())
            val body = document.selectFirst("body") ?: document
            val elements = body.getAllElements()
            val anchorOffsets = if (split) anchored.map { item ->
                val anchor = EpubPathResolver.fragment(item.href)
                elements.indexOfFirst { it.id() == anchor || it.attr("name") == anchor }
            } else emptyList()
            val text = body.clone().apply { select("style,script,noscript,svg").remove() }.text()
            if (text.isNotBlank() || body.select("svg").any { it.selectFirst("image") == null }) omitted = true
            val css = document.select("style,[style]").joinToString("\n") { it.data() + it.attr("style") }
            if (Regex("background(?:-image)?\\s*:", RegexOption.IGNORE_CASE).containsMatchIn(css)) omitted = true
            val seenCss = mutableSetOf<String>()
            fun inspectCss(cssPath: String) {
                if (!seenCss.add(cssPath)) return
                checkCancelled()
                val content = read(cssPath)?.toString(Charsets.UTF_8) ?: return
                if (Regex("background(?:-image)?\\s*:[^;}]*(?:url\\(|gradient\\()", RegexOption.IGNORE_CASE).containsMatchIn(content)) omitted = true
                EpubCssIndex.imports(content).forEach { ref -> EpubPathResolver.resolve(cssPath, ref)?.let(::inspectCss) }
            }
            document.select("link[href]").forEach { EpubPathResolver.resolve(path, it.attr("href"))?.let(::inspectCss) }
            var occurrence = 0
            for ((offset, element) in elements.withIndex()) {
                val tag = element.normalName()
                if (tag != "img" && tag != "image") continue
                checkCancelled()
                val ref = if (tag == "img") element.attr("src") else element.attr("href").ifBlank { element.attr("xlink:href") }
                val image = EpubPathResolver.resolve(path, ref)
                if (image == null || !bitmap(image)) { omitted = true; continue }
                val chapter = if (split) anchorOffsets.indexOfLast { it >= 0 && it <= offset }.coerceAtLeast(0) else 0
                val anchor = (listOf(element) + element.parents()).firstOrNull { it.id().isNotBlank() }?.id()
                pages += EpubComicPage(path, image, occurrence++, base + chapter, anchor)
            }
        }
        return EpubComicIndex(pages, titles, omitted, book.pageProgressionDirection == EpubPageProgressionDirection.RTL)
    }
}
