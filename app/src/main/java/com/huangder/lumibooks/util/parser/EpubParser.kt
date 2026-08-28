package com.huangder.lumibooks.util.parser

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.text.Html
import android.text.Spanned
import android.util.Base64
import java.io.File
import java.io.FileInputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import kotlin.text.RegexOption
import com.huangder.lumibooks.util.BookFileAccess
import com.huangder.lumibooks.util.SeekableBookSource
import com.huangder.lumibooks.util.epub.EpubPackage
import com.huangder.lumibooks.util.epub.EpubPackageReader
import com.huangder.lumibooks.util.epub.EpubPathResolver
import com.huangder.lumibooks.util.epub.EpubRenderSession
import com.huangder.lumibooks.util.epub.BookRenderSource
import com.huangder.lumibooks.util.epub.BookSearchSource
import com.huangder.lumibooks.util.epub.EpubSearchMatch
import com.huangder.lumibooks.util.epub.EpubTextSearch
import com.huangder.lumibooks.util.epub.CachedEpubMetadata
import com.huangder.lumibooks.util.epub.EpubMetadataCache
import com.huangder.lumibooks.util.cache.WeightedLruCache

/**
 * EPUB 解析器 — 按需加载章节
 *
 * parse() 只提取元数据（标题、作者、章节列表、封面），不处理图片。
 * getChapterHtml() / getChapterContent() 按需读取并处理单个章节。
 */
class EpubParser(private val context: Context? = null) : BookParser, BookRenderSource, BookSearchSource {
    /** Marker copied through pagination so the first image-only spine item can use cover rendering. */
    class CoverPageSpan

    companion object {
        private const val ANCHOR_MARKER_PREFIX = "\uE000LUMIBOOKS_ANCHOR:"
        private const val ANCHOR_MARKER_SUFFIX = "\uE001"

        // ── 注释引用识别（与 WebView 版 EpubDocumentTransformer 的启发式保持一致） ──
        private val FOOTNOTE_HINT_REGEX = Regex(
            """(^|[\s_#./-])(footnotes?|endnotes?|rearnotes?|notes?|fn|en)([\s_./-]|\d|$)""",
            RegexOption.IGNORE_CASE
        )
        private val NOTEREF_SEMANTICS_REGEX = Regex("""(^|\s)(noteref|doc-noteref)(\s|$)""")
        private val NOTE_BODY_SEMANTICS_REGEX = Regex("""(^|\s)(footnote|endnote|rearnote|doc-footnote|doc-endnote)(\s|$)""")
        private val BACKLINK_SEMANTICS_REGEX = Regex("""(^|\s)(backlink|doc-backlink)(\s|$)""")
        private val BRACKETED_MARKER_REGEX = Regex("""^(?:[\[［【〔](?:[0-9０-９]{1,3}|[*＊]{1,3})[\]］】〕])+$""")
        private val CIRCLED_MARKER_REGEX = Regex("""^[①-⑳]$""")
        private val ASTERISK_MARKER_REGEX = Regex("""^[*＊]{1,3}$""")
        private val LINK_SCHEME_REGEX = Regex("""^[A-Za-z][A-Za-z0-9+.-]*:""")
        private val PARAGRAPH_LIKE_TAGS = setOf("p", "li", "dd", "dt", "td", "h1", "h2", "h3", "h4", "h5", "h6")

        /** 提取标签属性值，兼容单双引号。 */
        internal fun tagAttribute(tag: String, name: String): String? {
            val doubleQuoted = Regex("""\s${Regex.escape(name)}\s*=\s*"([^"]*)"""", RegexOption.IGNORE_CASE)
            val singleQuoted = Regex("""\s${Regex.escape(name)}\s*=\s*'([^']*)'""", RegexOption.IGNORE_CASE)
            return doubleQuoted.find(tag)?.groupValues?.get(1)
                ?: singleQuoted.find(tag)?.groupValues?.get(1)
        }

        private fun decodeTagEntities(value: String): String = value
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&apos;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")

        /**
         * 判断 <a> 标签是否为注释引用：
         * - epub:type/role/rel 含 noteref 语义 → 是
         * - class/id/title/fragment 含 footnote 等字样 → 是
         * - 链接可见文本是 [01]、［2］、【3】、① 等纯注释编号 → 是
         * - 自身是注释正文/返回链接（footnote/endnote/backlink 语义）→ 否
         */
        internal fun isFootnoteAnchorTag(openTag: String, innerHtml: String): Boolean {
            val href = tagAttribute(openTag, "href") ?: return false
            val decodedHref = decodeTagEntities(href)
            if (!decodedHref.contains('#')) return false
            val fragment = decodedHref.substringAfter('#')
            if (fragment.isBlank()) return false

            val semantics = listOf("epub:type", "role", "rel")
                .mapNotNull { tagAttribute(openTag, it) }
                .joinToString(" ")
                .lowercase()
            if (NOTE_BODY_SEMANTICS_REGEX.containsMatchIn(semantics)) return false
            if (NOTEREF_SEMANTICS_REGEX.containsMatchIn(semantics)) return true
            if (listOf("class", "id", "title").any {
                    tagAttribute(openTag, it)?.let(FOOTNOTE_HINT_REGEX::containsMatchIn) == true
                }) return true
            if (FOOTNOTE_HINT_REGEX.containsMatchIn(fragment)) return true

            val label = innerHtml
                .replace(Regex("<[^>]*>"), "")
                .replace(Regex("\\s+"), "")
            return BRACKETED_MARKER_REGEX.matches(label) ||
                CIRCLED_MARKER_REGEX.matches(label) ||
                ASTERISK_MARKER_REGEX.matches(label)
        }

        /**
         * 从章节 HTML 中提取注释正文：按 fragment 找 id/name 目标元素，
         * 移除 backlink 后返回其纯文本。找不到目标或正文为空时返回 null。
         */
        internal fun extractFootnoteElementText(html: String, fragment: String): String? {
            if (fragment.isBlank()) return null
            val document = runCatching { org.jsoup.Jsoup.parse(html) }.getOrNull() ?: return null
            val target = document.getElementById(fragment)
                ?: document.allElements.firstOrNull { it.attr("name") == fragment }
                ?: document.allElements.firstOrNull { it.id().equals(fragment, ignoreCase = true) }
                ?: return null

            // 行内 <a name="..."> 锚点只标记注释起点，正文在其父段落中
            val parent = target.parent()
            val holder = if (target.tagName() == "a" &&
                parent != null && parent.tagName() in PARAGRAPH_LIKE_TAGS
            ) {
                parent
            } else {
                target
            }.clone()
            holder.select("script,style,iframe,frame,object,embed,form,input,textarea,select,button,link").remove()
            holder.allElements.toList().forEach { element ->
                val tokens = listOf("epub:type", "role", "rel")
                    .map { element.attr(it) }
                    .filter { it.isNotBlank() }
                    .joinToString(" ")
                    .lowercase()
                if (tokens.isNotBlank() && BACKLINK_SEMANTICS_REGEX.containsMatchIn(tokens)) {
                    element.remove()
                }
            }
            return holder.text().trim().takeIf { it.isNotEmpty() }
        }
    }

    override var paragraphSpacingDp: Float = 0f
    override var firstLineIndentChars: Float = 0f

    /** 阅读区域内容宽度（像素），用于图片缩放。0 表示未设置，回退到 DisplayMetrics 计算 */
    override var contentWidth: Int = 0
    /** 是否加载 EPUB 自带 CSS 样式 */
    override var useEpubCss: Boolean = false
    private var chapters: List<Chapter> = emptyList()
    private var bookTitle: String = ""
    private var bookAuthor: String = ""
    private var basePath: String = ""
    private var epubFilePath: String = ""
    private var parsedPackage: EpubPackage? = null
    private var sourceLease: SeekableBookSource? = null
    private var sessionZipFile: ZipFile? = null
    private var lowercaseEntryIndex: Map<String, ZipEntry> = emptyMap()
    private val zipLock = Any()

    // 章节路径列表（spine 顺序）
    private var chapterPaths: List<String> = emptyList()

    // spine 中的 href 列表（用于 NCX → spine 索引映射）
    private var spineHrefs: List<String> = emptyList()

    // EPUB chapters vary greatly in size, so bound decoded data by memory weight.
    private data class HtmlCacheKey(val chapterIndex: Int, val optimizeLayout: Boolean)
    private val htmlCache = WeightedLruCache<HtmlCacheKey, String>(8L * 1024L * 1024L) {
        it.length.toLong() * Char.SIZE_BYTES
    }
    private val contentCache = WeightedLruCache<Int, CharSequence>(16L * 1024L * 1024L) {
        it.length.toLong() * Char.SIZE_BYTES
    }
    private val anchorOffsets = mutableMapOf<Int, Map<String, Int>>()
    // CSS 文件内容缓存（key = ZIP 内完整路径，避免重复读取同一文件）
    private val cssFileCache = mutableMapOf<String, String>()
    // 各章节中识别为注释引用的 href 集合（Canvas 引擎注释气泡用）
    private val footnoteHrefs = mutableMapOf<Int, Set<String>>()

    override fun parse(filePath: String): BookContent {
        close()
        val lease = context?.let { BookFileAccess.openSeekable(it, filePath) }
        sourceLease = lease
        epubFilePath = lease?.path ?: filePath
        val cachedMetadata = context?.let { EpubMetadataCache.read(it, filePath, epubFilePath) }
        val packageModel = cachedMetadata?.epubPackage ?: EpubPackageReader.read(epubFilePath)
        parsedPackage = packageModel
        basePath = packageModel.basePath
        bookTitle = packageModel.title
        bookAuthor = packageModel.author
        chapterPaths = packageModel.spine.map { it.manifestItem.fullPath }
        spineHrefs = packageModel.spine.map { it.manifestItem.href }

        val navigationTitles = packageModel.navigation.mapNotNull { item ->
            val path = EpubPathResolver.normalize(item.href.substringBefore('#')) ?: return@mapNotNull null
            path to item.title
        }.toMap()

        val zipFile = ZipFile(epubFilePath)
        sessionZipFile = zipFile
        lowercaseEntryIndex = zipFile.entries().asSequence().associateBy { it.name.lowercase() }
        val chapterTitles = cachedMetadata?.chapterTitles
            ?.takeIf { it.size == chapterPaths.size }
            ?: chapterPaths.mapIndexed { index, chapterPath ->
                navigationTitles[chapterPath]?.takeIf { it.isNotBlank() } ?: run {
                    val entry = findEntry(zipFile, chapterPath)
                    val preview = entry?.let {
                        zipFile.getInputStream(it).use { input ->
                            val buffer = ByteArray(8192)
                            val count = input.read(buffer).coerceAtLeast(0)
                            String(buffer, 0, count, Charsets.UTF_8)
                        }
                    }.orEmpty()
                    extractTitle(preview) ?: "第${index + 1}章"
                }
            }
        chapters = chapterTitles.mapIndexed { index, title ->
            Chapter(index = index, title = title, content = "", htmlContent = "")
        }

        val tocEntries = packageModel.navigation.map { item ->
            val documentPath = item.href.substringBefore('#')
            val chapterIndex = chapterPaths.indexOf(documentPath)
            TocEntry(
                title = item.title,
                level = item.level,
                chapterIndex = chapterIndex,
                isGroup = chapterIndex < 0,
                anchor = item.href.substringAfter('#', "").ifBlank { null }
            )
        }.ifEmpty {
            chapterTitles.mapIndexed { index, title -> TocEntry(title, 1, index) }
        }

        val coverPath = cachedMetadata?.coverPath?.takeIf { File(it).isFile } ?: run {
            val opfEntry = zipFile.getEntry(packageModel.opfPath)
            val opfContent = opfEntry?.let { zipFile.getInputStream(it).bufferedReader().use { reader -> reader.readText() } }.orEmpty()
            extractCover(zipFile, opfContent, filePath)
        }

        if (cachedMetadata == null) {
            context?.let {
                EpubMetadataCache.write(
                    it,
                    filePath,
                    CachedEpubMetadata(packageModel, chapterTitles, coverPath)
                )
            }
        }

        return BookContent(
            title = bookTitle,
            author = bookAuthor,
            chapters = chapters,
            coverPath = coverPath,
            tocEntries = tocEntries
        )
    }

    override fun openRenderSession(): EpubRenderSession =
        EpubRenderSession.open(epubFilePath, parsedPackage ?: EpubPackageReader.read(epubFilePath))

    override suspend fun searchBook(query: String, maxResults: Int): List<EpubSearchMatch> {
        val epubPackage = parsedPackage ?: EpubPackageReader.read(epubFilePath)
        return EpubTextSearch.search(epubFilePath, epubPackage, query, maxResults)
    }

    private fun extractOpfPath(containerXml: String): String {
        val rootfileRegex = """full-path="([^"]+)"""".toRegex()
        return rootfileRegex.find(containerXml)?.groupValues?.get(1) ?: "content.opf"
    }

    private fun extractMetadata(opfContent: String, tag: String): String? {
        val tagName = tag.substringAfterLast(":")
        val r1 = """<[a-zA-Z0-9_]*:?${Regex.escape(tagName)}[^>]*>([^<]+)</[a-zA-Z0-9_]*:?${Regex.escape(tagName)}>""".toRegex(RegexOption.IGNORE_CASE)
        val v1 = r1.find(opfContent)?.groupValues?.get(1)?.trim()
        if (!v1.isNullOrBlank()) return decodeXmlEntities(v1)

        val r2 = """<meta[^>]+\bproperty="${Regex.escape(tag)}"[^>]*>([^<]+)</meta>""".toRegex(RegexOption.IGNORE_CASE)
        val v2 = r2.find(opfContent)?.groupValues?.get(1)?.trim()
        if (!v2.isNullOrBlank()) return decodeXmlEntities(v2)

        return null
    }

    private fun decodeXmlEntities(s: String): String = s
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&apos;", "'")
        .replace("&#160;", " ")
        .replace(Regex("&#(\\d+);")) { mr -> mr.groupValues[1].toIntOrNull()?.toChar()?.toString() ?: mr.value }
        .replace(Regex("&#x([0-9a-fA-F]+);")) { mr -> mr.groupValues[1].toIntOrNull(16)?.toChar()?.toString() ?: mr.value }

    /**
     * 提取章节路径和标题（读HTML提取标题，但不处理图片）
     * 返回 Triple<标题, 原始href, 完整路径>
     */
    private fun extractChapterInfo(zipFile: ZipFile, opfContent: String): List<Triple<String, String, String>> {
        val result = mutableListOf<Triple<String, String, String>>()

        // 解析spine顺序
        val spineRegex = """<spine[^>]*>(.*?)</spine>""".toRegex(RegexOption.DOT_MATCHES_ALL)
        val spineContent = spineRegex.find(opfContent)?.groupValues?.get(1) ?: ""
        val itemrefs = """idref="([^"]+)"""".toRegex().findAll(spineContent)
            .map { it.groupValues[1] }.toList()

        // 解析manifest
        val manifestRegex = """<manifest[^>]*>(.*?)</manifest>""".toRegex(RegexOption.DOT_MATCHES_ALL)
        val manifestContent = manifestRegex.find(opfContent)?.groupValues?.get(1) ?: ""
        val items = mutableMapOf<String, String>()
        """<item\s+id="([^"]+)"\s+href="([^"]+)"[^>]*/>""".toRegex()
            .findAll(manifestContent).forEach { match ->
                items[match.groupValues[1]] = match.groupValues[2]
            }

        // 按spine顺序记录章节路径
        for ((index, itemref) in itemrefs.withIndex()) {
            val href = items[itemref] ?: continue
            val fullPath = if (basePath.isNotEmpty()) "$basePath/$href" else href

            try {
                val entry = findEntry(zipFile, fullPath)
                if (entry != null) {
                    // 只读前 4096 字节提取标题（<title>/<h1> 必定在文件头部），避免读取整个章节 HTML
                    val stream = zipFile.getInputStream(entry)
                    val preview = ByteArray(4096)
                    val bytesRead = stream.read(preview).coerceAtLeast(0)
                    stream.close()
                    val rawHtml = String(preview, 0, bytesRead, Charsets.UTF_8)
                    val title = extractTitle(rawHtml) ?: "第${index + 1}章"
                    result.add(Triple(title, href, fullPath))
                    android.util.Log.d("EpubParser", "extractChapterInfo: [$index] title=$title path=$fullPath")
                } else {
                    android.util.Log.w("EpubParser", "extractChapterInfo: [$index] findEntry FAILED for $fullPath")
                }
            } catch (e: Exception) {
                android.util.Log.e("EpubParser", "extractChapterInfo: [$index] exception", e)
                e.printStackTrace()
            }
        }

        // 回退：如果spine为空，读取所有HTML
        if (result.isEmpty()) {
            val htmlEntries = zipFile.entries().toList().filter {
                it.name.endsWith(".html") || it.name.endsWith(".xhtml") || it.name.endsWith(".htm")
            }
            for ((index, entry) in htmlEntries.withIndex()) {
                try {
                    val rawHtml = zipFile.getInputStream(entry).bufferedReader().readText()
                    if (rawHtml.isNotBlank()) {
                        val title = extractTitle(rawHtml) ?: "第${index + 1}章"
                        result.add(Triple(title, entry.name, entry.name))
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        return result
    }

    // ========== NCX/nav 层级目录解析 ==========

    /**
     * 构建层级目录。优先解析 NCX（EPUB 2），回退到 nav（EPUB 3）。
     * 如果都没有，返回空列表（调用方会回退到 flat list）。
     */
    private fun buildTocEntries(zipFile: ZipFile, opfContent: String): List<TocEntry> {
        // 尝试 NCX（EPUB 2）
        val ncxPath = findNcxPath(zipFile, opfContent)
        if (ncxPath != null) {
            try {
                val entries = parseNcx(zipFile, ncxPath)
                if (entries.isNotEmpty()) {
                    android.util.Log.d("EpubParser", "buildTocEntries: NCX parsed, ${entries.size} entries")
                    return entries
                }
            } catch (e: Exception) {
                android.util.Log.e("EpubParser", "buildTocEntries: NCX parse failed", e)
            }
        }

        // 尝试 nav（EPUB 3）
        try {
            val entries = parseNav(zipFile, opfContent)
            if (entries.isNotEmpty()) {
                android.util.Log.d("EpubParser", "buildTocEntries: Nav parsed, ${entries.size} entries")
                return entries
            }
        } catch (e: Exception) {
            android.util.Log.e("EpubParser", "buildTocEntries: Nav parse failed", e)
        }

        return emptyList()
    }

    /** 从 OPF 中找到 NCX 文件路径 */
    private fun findNcxPath(zipFile: ZipFile, opfContent: String): String? {
        // 方式1：spine 的 toc 属性引用 NCX id
        val spineTocRegex = """<spine[^>]*toc="([^"]+)"""".toRegex()
        val spineTocId = spineTocRegex.find(opfContent)?.groupValues?.get(1)

        if (spineTocId != null) {
            val manifestRegex = """<manifest[^>]*>(.*?)</manifest>""".toRegex(RegexOption.DOT_MATCHES_ALL)
            val manifestContent = manifestRegex.find(opfContent)?.groupValues?.get(1) ?: ""
            val itemRegex = """<item\s+id="${Regex.escape(spineTocId)}"\s+href="([^"]+)"""".toRegex()
            val href = itemRegex.find(manifestContent)?.groupValues?.get(1)
            if (href != null) {
                return if (basePath.isNotEmpty()) "$basePath/$href" else href
            }
        }

        // 方式2：查找 manifest 中 media-type 为 NCX 的 item
        val manifestRegex = """<manifest[^>]*>(.*?)</manifest>""".toRegex(RegexOption.DOT_MATCHES_ALL)
        val manifestContent = manifestRegex.find(opfContent)?.groupValues?.get(1) ?: ""
        val ncxItemRegex = """<item[^>]*href="([^"]+\.ncx)"[^>]*/>""".toRegex(RegexOption.IGNORE_CASE)
        val ncxHref = ncxItemRegex.find(manifestContent)?.groupValues?.get(1)
        if (ncxHref != null) {
            return if (basePath.isNotEmpty()) "$basePath/$ncxHref" else ncxHref
        }

        // 方式3：常见默认路径
        val candidates = listOf("toc.ncx", "OEBPS/toc.ncx", "content/toc.ncx", "OPS/toc.ncx")
        for (candidate in candidates) {
            if (findEntry(zipFile, candidate) != null) return candidate
        }

        return null
    }

    /**
     * 解析 NCX 文件的 navMap，构建层级目录。
     * 使用深度计数法正确处理嵌套 navPoint。
     */
    private fun parseNcx(zipFile: ZipFile, ncxPath: String): List<TocEntry> {
        val entry = findEntry(zipFile, ncxPath) ?: return emptyList()
        val ncxContent = zipFile.getInputStream(entry).bufferedReader().readText()

        // 提取 navMap 内容
        val navMapRegex = """<navMap[^>]*>(.*)</navMap>""".toRegex(RegexOption.DOT_MATCHES_ALL)
        val navMapContent = navMapRegex.find(ncxContent)?.groupValues?.get(1) ?: return emptyList()

        val result = mutableListOf<TocEntry>()
        parseNavPointsDepth(navMapContent, 1, result, ncxPath)

        // 用 NCX 标题更新 chapters 列表（NCX 更准确）
        for (tocEntry in result) {
            if (tocEntry.chapterIndex in chapters.indices) {
                val oldChapter = chapters[tocEntry.chapterIndex]
                if (oldChapter.title != tocEntry.title) {
                    chapters = chapters.toMutableList().apply {
                        set(tocEntry.chapterIndex, oldChapter.copy(title = tocEntry.title))
                    }
                }
            }
        }

        return result
    }

    /**
     * 使用深度计数法解析 navPoint，正确处理嵌套。
     * 正则 .*? 非贪婪匹配会跳过嵌套子项，因此改用逐字符扫描。
     */
    private fun parseNavPointsDepth(
        xml: String,
        level: Int,
        result: MutableList<TocEntry>,
        tocPath: String
    ) {
        // 查找每个 <navPoint 开始标签，然后用深度计数找到对应的 </navPoint>
        val openTag = "<navPoint"
        val closeTag = "</navPoint>"
        var searchFrom = 0

        while (searchFrom < xml.length) {
            val openIdx = xml.indexOf(openTag, searchFrom)
            if (openIdx < 0) break

            // 找到这个 <navPoint 标签的结束 >
            val tagEnd = xml.indexOf(">", openIdx)
            if (tagEnd < 0) break

            // 用深度计数找到匹配的 </navPoint>
            var depth = 1
            var pos = tagEnd + 1
            while (pos < xml.length && depth > 0) {
                val nextOpen = xml.indexOf(openTag, pos)
                val nextClose = xml.indexOf(closeTag, pos)

                if (nextClose < 0) break  // 格式错误

                if (nextOpen in 0 until nextClose) {
                    // 先遇到嵌套的 <navPoint
                    depth++
                    pos = xml.indexOf(">", nextOpen) + 1
                } else {
                    // 先遇到 </navPoint>
                    depth--
                    if (depth == 0) {
                        // 找到匹配的关闭标签
                        val navPointContent = xml.substring(tagEnd + 1, nextClose)

                        processNavPoint(navPointContent, level, result, tocPath)

                        searchFrom = nextClose + closeTag.length
                        break
                    }
                    pos = nextClose + closeTag.length
                }
            }

            if (depth > 0) break  // 格式错误，退出
        }
    }

    /**
     * 处理单个 navPoint：提取标题、src，判断是否有子项
     */
    private fun processNavPoint(
        content: String,
        level: Int,
        result: MutableList<TocEntry>,
        tocPath: String
    ) {
        // 提取标题
        val textRegex = """<text[^>]*>([^<]+)</text>""".toRegex()
        val title = textRegex.find(content)?.groupValues?.get(1)?.trim() ?: return

        // 提取 content src
        val srcRegex = """<content\s+src="([^"]+)"""".toRegex()
        val src = srcRegex.find(content)?.groupValues?.get(1) ?: return

        val hrefWithoutFragment = src.substringBefore("#")
        val anchor = src.substringAfter("#", "").takeIf { it.isNotEmpty() }
            ?.let(::decodeUrlComponent)
        val spineIndex = mapHrefToSpineIndex(hrefWithoutFragment, tocPath)

        // 检查是否有子 navPoint
        val hasChildren = content.contains("<navPoint")

        if (spineIndex >= 0) {
            if (hasChildren && level == 1) {
                // 有子项的顶级条目 → 分组标题
                result.add(TocEntry(title = title, level = level, chapterIndex = -1, isGroup = true))
            } else {
                // 实际章节
                result.add(
                    TocEntry(
                        title = title,
                        level = level,
                        chapterIndex = spineIndex,
                        anchor = anchor
                    )
                )
            }
        } else if (hasChildren && level == 1) {
            result.add(TocEntry(title = title, level = level, chapterIndex = -1, isGroup = true))
        }

        // 递归处理子 navPoint
        if (hasChildren) {
            parseNavPointsDepth(content, level + 1, result, tocPath)
        }
    }

    /**
     * 将 href 映射到 spine 索引
     */
    private fun mapHrefToSpineIndex(href: String, tocPath: String? = null): Int {
        val decodedHref = decodeUrlComponent(href).replace('\\', '/')
        val normalizedHref = normalizePath(decodedHref).trimStart('/')
        val candidatePaths = linkedSetOf(normalizedHref)

        // NCX/nav 内的 href 相对其自身文件，而非 OPF。卷目录常有同名章节文件，
        // 只有先解析为 EPUB 内完整路径，才能避免错误命中另一卷的同名文件。
        if (tocPath != null) {
            val tocDirectory = normalizePath(tocPath).substringBeforeLast('/', "")
            val resolvedPath = if (decodedHref.startsWith('/')) {
                normalizedHref
            } else {
                normalizePath(
                    if (tocDirectory.isEmpty()) decodedHref else "$tocDirectory/$decodedHref"
                ).trimStart('/')
            }
            candidatePaths.add(resolvedPath)
        } else if (basePath.isNotEmpty()) {
            candidatePaths.add(normalizePath("$basePath/$decodedHref").trimStart('/'))
        }

        for (candidatePath in candidatePaths) {
            val chapterIndex = chapterPaths.indexOfFirst { chapterPath ->
                normalizePath(decodeUrlComponent(chapterPath).replace('\\', '/'))
                    .trimStart('/') == candidatePath
            }
            if (chapterIndex >= 0) return chapterIndex
        }

        // 对缺少目录层级的非标准 EPUB 保留文件名兜底，但仅在文件名唯一时使用，
        // 绝不把“第二卷/chapter.xhtml”猜成“第一卷/chapter.xhtml”。
        val hrefFileName = normalizedHref.substringAfterLast('/')
        val matches = spineHrefs.indices.filter { index ->
            decodeUrlComponent(spineHrefs[index]).substringAfterLast('/') == hrefFileName
        }
        return matches.singleOrNull() ?: -1
    }

    /**
     * 解析 EPUB 3 的 nav 元素（HTML 格式的目录）
     * <nav epub:type="toc"><ol><li><a href="...">标题</a><ol>...</ol></li></ol></nav>
     */
    private fun parseNav(zipFile: ZipFile, opfContent: String): List<TocEntry> {
        // 从 manifest 中找 nav 文件
        val manifestRegex = """<manifest[^>]*>(.*?)</manifest>""".toRegex(RegexOption.DOT_MATCHES_ALL)
        val manifestContent = manifestRegex.find(opfContent)?.groupValues?.get(1) ?: ""

        // 查找 properties="nav" 的 item
        val navItemRegex = """<item[^>]*properties="[^"]*nav[^"]*"[^>]*href="([^"]+)"""".toRegex()
        val navHref = navItemRegex.find(manifestContent)?.groupValues?.get(1)
            ?: // 尝试反向顺序
            """<item[^>]*href="([^"]+)"[^>]*properties="[^"]*nav[^"]*"""".toRegex()
                .find(manifestContent)?.groupValues?.get(1)
            ?: return emptyList()

        val navPath = if (basePath.isNotEmpty()) "$basePath/$navHref" else navHref
        val navEntry = findEntry(zipFile, navPath) ?: return emptyList()
        val navHtml = zipFile.getInputStream(navEntry).bufferedReader().readText()

        // 找到 <nav epub:type="toc"> 或 <nav id="toc">
        val navRegex = """<nav[^>]*(?:epub:type="toc"|id="toc")[^>]*>(.*?)</nav>""".toRegex(
            setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
        )
        val navContent = navRegex.find(navHtml)?.groupValues?.get(1) ?: return emptyList()

        val result = mutableListOf<TocEntry>()
        parseNavOl(navContent, 1, result, navPath)
        return result
    }

    /**
     * 递归解析 nav 中的 <ol><li> 结构
     */
    private fun parseNavOl(
        html: String,
        level: Int,
        result: MutableList<TocEntry>,
        tocPath: String
    ) {
        // 使用深度计数法解析 <li>，正确处理嵌套
        val openTag = "<li"
        val closeTag = "</li>"
        var searchFrom = 0

        while (searchFrom < html.length) {
            val openIdx = html.indexOf(openTag, searchFrom)
            if (openIdx < 0) break

            val tagEnd = html.indexOf(">", openIdx)
            if (tagEnd < 0) break

            // 用深度计数找到匹配的 </li>
            var depth = 1
            var pos = tagEnd + 1
            var found = false
            while (pos < html.length && depth > 0) {
                val nextOpen = html.indexOf(openTag, pos)
                val nextClose = html.indexOf(closeTag, pos)

                if (nextClose < 0) break

                if (nextOpen in 0 until nextClose) {
                    depth++
                    pos = html.indexOf(">", nextOpen) + 1
                } else {
                    depth--
                    if (depth == 0) {
                        val liContent = html.substring(tagEnd + 1, nextClose)
                        processNavLi(liContent, level, result, tocPath)
                        searchFrom = nextClose + closeTag.length
                        found = true
                        break
                    }
                    pos = nextClose + closeTag.length
                }
            }

            if (!found) break
        }
    }

    /** 处理单个 <li>：提取 <a> 标题和 href，递归处理子 <ol> */
    private fun processNavLi(
        content: String,
        level: Int,
        result: MutableList<TocEntry>,
        tocPath: String
    ) {
        val aRegex = """<a[^>]*href="([^"]+)"[^>]*>([^<]+)</a>""".toRegex(RegexOption.IGNORE_CASE)
        val aMatch = aRegex.find(content) ?: return

        val rawHref = aMatch.groupValues[1]
        val href = rawHref.substringBefore("#")
        val anchor = rawHref.substringAfter("#", "").takeIf { it.isNotEmpty() }
            ?.let(::decodeUrlComponent)
        val title = aMatch.groupValues[2].trim()
        val spineIndex = mapHrefToSpineIndex(href, tocPath)
        val hasChildren = content.contains("<ol")

        if (spineIndex >= 0) {
            if (hasChildren && level == 1) {
                result.add(TocEntry(title = title, level = level, chapterIndex = -1, isGroup = true))
            } else {
                result.add(
                    TocEntry(
                        title = title,
                        level = level,
                        chapterIndex = spineIndex,
                        anchor = anchor
                    )
                )
            }
        } else if (hasChildren && level == 1) {
            result.add(TocEntry(title = title, level = level, chapterIndex = -1, isGroup = true))
        }

        // 递归处理子 <ol> 中的 <li>
        if (hasChildren) {
            parseNavOl(content, level + 1, result, tocPath)
        }
    }

    /**
     * 按需加载单个章节的HTML（含图片Base64内嵌），带缓存。
     * @param optimizeLayout true=使用优化排版（包裹自定义CSS），false=保留EPUB自带排版
     */
    override fun getChapterHtml(chapterIndex: Int, optimizeLayout: Boolean): String {
        val cacheKey = HtmlCacheKey(chapterIndex, optimizeLayout)
        htmlCache[cacheKey]?.let { return it }
        if (chapterIndex !in chapterPaths.indices) return ""

        val path = chapterPaths[chapterIndex]
        try {
            return synchronized(zipLock) {
                val zipFile = sessionZipFile ?: return@synchronized ""
                val zipEntry = findEntry(zipFile, path) ?: return@synchronized ""
                val rawHtml = zipFile.getInputStream(zipEntry).bufferedReader().use { it.readText() }
                processHtml(zipFile, rawHtml, optimizeLayout, chapterPath = path).also {
                    htmlCache.put(cacheKey, it)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return ""
        }
    }

    /**
     * 按需加载单个章节的 Spanned，带缓存
     */
    override fun getChapterContent(chapterIndex: Int): CharSequence {
        contentCache[chapterIndex]?.let {
            android.util.Log.d("EpubParser", "getChapterContent: idx=$chapterIndex from cache, length=${it.length}")
            return it
        }
        if (chapterIndex !in chapterPaths.indices) {
            android.util.Log.w("EpubParser", "getChapterContent: idx=$chapterIndex out of range (chapterPaths.size=${chapterPaths.size})")
            return ""
        }

        val path = chapterPaths[chapterIndex]
        android.util.Log.d("EpubParser", "getChapterContent: idx=$chapterIndex path=$path")
        try {
            return synchronized(zipLock) {
            val zipFile = sessionZipFile ?: return@synchronized ""
            val zipEntry = findEntry(zipFile, path) ?: return@synchronized ""
            val rawHtml = zipFile.getInputStream(zipEntry).bufferedReader().use { it.readText() }
            android.util.Log.d("EpubParser", "getChapterContent: idx=$chapterIndex rawHtml.length=${rawHtml.length}")
            if (chapterIndex == 0) android.util.Log.d("EpubParser", "cover HTML: $rawHtml")
            val spanned = htmlToSpanned(chapterIndex, rawHtml, zipFile)
            // 应用段间距和首行缩进（Canvas 引擎需要在 Spanned 层面处理）
            val formatted = applyParagraphFormatting(spanned)
            // 修剪末尾多余换行（防止章节末尾出现空白页）
            val trimmed = trimTrailingNewlines(formatted)
            // 移除 HTML 锚点占位，并记录其在最终章节文本中的字符位置
            val anchored = extractAnchorOffsets(chapterIndex, trimmed)
            // 验证修剪后 span 存活
            val trimSpans = anchored.getSpans(0, anchored.length, android.text.style.LeadingMarginSpan::class.java)
            android.util.Log.d("EpubParser", "getChapterContent: after trim: type=${anchored.javaClass.simpleName} LeadingMarginSpans=${trimSpans.size}")
            // 如果 Spanned 为空（纯图片章节图片加载失败），返回占位文本
            val result: CharSequence = if (anchored.isBlank()) android.text.SpannableString(" ") else anchored
            // 验证最终结果中的 span
            val finalSpans = if (result is android.text.Spannable) result.getSpans(0, result.length, Any::class.java) else emptyArray()
            val finalIndentCount = finalSpans.count { it is android.text.style.LeadingMarginSpan }
            val finalLHCount = finalSpans.count { it is ParagraphLineHeightSpan }
            android.util.Log.d("EpubParser", "getChapterContent: idx=$chapterIndex result.length=${result.length} LeadingMarginSpans=$finalIndentCount LineHeightSpans=$finalLHCount firstLineIndentChars=$firstLineIndentChars indentPx=${(firstLineIndentChars * 18f * (context?.resources?.displayMetrics?.density ?: 2.75f)).toInt()}")
            contentCache.put(chapterIndex, result)
            result
            }
        } catch (e: Exception) {
            android.util.Log.e("EpubParser", "getChapterContent: exception for idx=$chapterIndex", e)
            e.printStackTrace()
            return ""
        }
    }

    override fun getChapterCount(): Int = chapterPaths.size

    override fun resolveLink(sourceChapterIndex: Int, href: String): BookLinkTarget? {
        if (sourceChapterIndex !in chapterPaths.indices) return null

        val trimmedHref = href.trim()
        if (trimmedHref.isEmpty() || trimmedHref.startsWith("//")) return null
        if (Regex("^[A-Za-z][A-Za-z0-9+.-]*:").containsMatchIn(trimmedHref)) return null

        val documentHref = trimmedHref.substringBefore('#').substringBefore('?')
        val targetChapter = if (documentHref.isBlank()) {
            sourceChapterIndex
        } else {
            resolveLinkedChapter(sourceChapterIndex, documentHref)
        }
        if (targetChapter !in chapterPaths.indices) return null

        val fragment = if ('#' in trimmedHref) {
            decodeUrlComponent(trimmedHref.substringAfter('#'))
        } else {
            ""
        }
        if (fragment.isEmpty()) return BookLinkTarget(targetChapter)

        // 目标章可能尚未分页，先按需解析以建立 id/name → 字符偏移映射。
        getChapterContent(targetChapter)
        val offset = anchorOffsets[targetChapter]?.get(fragment)
            ?: anchorOffsets[targetChapter]?.get(fragment.lowercase())
            ?: 0
        return BookLinkTarget(targetChapter, offset)
    }

    override fun clearHtmlCache() {
        htmlCache.clear()
        contentCache.clear()  // 段间距/首行缩进变更时也需要清空内容缓存
        anchorOffsets.clear()
        cssFileCache.clear()
        footnoteHrefs.clear()
    }

    /** 扫描章节 HTML，收集注释引用链接的 href（保持 HTML 属性原样，仅解码实体）。 */
    private fun collectFootnoteHrefs(chapterIndex: Int, html: String) {
        val hrefs = mutableSetOf<String>()
        val anchorRegex = Regex(
            """<a\b[^>]*>(.*?)</a>""",
            setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
        )
        anchorRegex.findAll(html).forEach { match ->
            val tag = match.value
            val openTagEnd = tag.indexOf('>')
            if (openTagEnd < 0) return@forEach
            val openTag = tag.substring(0, openTagEnd + 1)
            val innerHtml = tag.substring(openTag.length, tag.length - "</a>".length)
            if (isFootnoteAnchorTag(openTag, innerHtml)) {
                tagAttribute(openTag, "href")?.let { hrefs.add(decodeTagEntities(it)) }
            }
        }
        footnoteHrefs[chapterIndex] = hrefs
    }

    override fun isFootnoteHref(chapterIndex: Int, href: String): Boolean {
        if (chapterIndex !in chapterPaths.indices) return false
        // 章节已展示时必有缓存；这里兜底确保 href 集合已收集
        getChapterContent(chapterIndex)
        return footnoteHrefs[chapterIndex]?.contains(href.trim()) == true
    }

    override fun resolveFootnoteText(sourceChapterIndex: Int, href: String): String? {
        val trimmed = href.trim()
        if (trimmed.isEmpty() || trimmed.startsWith("//")) return null
        if (LINK_SCHEME_REGEX.containsMatchIn(trimmed)) return null
        if (sourceChapterIndex !in chapterPaths.indices) return null

        val documentHref = trimmed.substringBefore('#').substringBefore('?')
        val targetChapter = if (documentHref.isBlank()) {
            sourceChapterIndex
        } else {
            resolveLinkedChapter(sourceChapterIndex, documentHref)
        }
        if (targetChapter !in chapterPaths.indices) return null

        val fragment = if ('#' in trimmed) decodeUrlComponent(trimmed.substringAfter('#')) else ""
        if (fragment.isEmpty()) return null

        val rawHtml = try {
            synchronized(zipLock) {
                val zipFile = sessionZipFile ?: return null
                val zipEntry = findEntry(zipFile, chapterPaths[targetChapter]) ?: return null
                zipFile.getInputStream(zipEntry).bufferedReader().use { it.readText() }
            }
        } catch (_: Exception) {
            return null
        }
        return extractFootnoteElementText(rawHtml, fragment)
    }

    /**
     * 对 Spanned 文本应用段间距和首行缩进。
     * Canvas 引擎（StaticLayout）不支持 CSS，需要在 Spanned 层面处理。
     *
     * - 首行缩进：LeadingMarginSpan.Standard 作用于每个段落
     * - 段间距：在段落之间插入空行，用 LineHeightSpan 精确控制空行高度
     */
    private fun applyParagraphFormatting(text: CharSequence): CharSequence {
        android.util.Log.d("EpubParser", "applyParagraphFormatting: paragraphSpacingDp=$paragraphSpacingDp firstLineIndentChars=$firstLineIndentChars")

        val ssb = android.text.SpannableStringBuilder(text)

        // 段间距为0时，合并连续换行（\n\n → \n），消除段落间空行
        if (paragraphSpacingDp <= 0f) {
            var i = ssb.length - 1
            while (i > 0) {
                if (ssb[i] == '\n' && ssb[i - 1] == '\n') {
                    ssb.delete(i, i + 1)
                }
                i--
            }
        }

        if (paragraphSpacingDp <= 0f && firstLineIndentChars <= 0f) {
            android.util.Log.d("EpubParser", "applyParagraphFormatting: SKIP (both <= 0)")
            return ssb
        }

        val density = context?.resources?.displayMetrics?.density ?: 2.75f
        val indentPx = if (firstLineIndentChars > 0f) (firstLineIndentChars * 18f * density).toInt() else 0
        val spacingPx = if (paragraphSpacingDp > 0f) (paragraphSpacingDp * density).toInt() else 0
        android.util.Log.d("EpubParser", "applyParagraphFormatting: density=$density spacingDp=$paragraphSpacingDp -> spacingPx=$spacingPx")

        // ── 段间距：在每个 \n 后插入一个空行，并用 LineHeightSpan 控制高度 ──
        if (spacingPx > 0) {
            // 从后往前插入，避免位置偏移
            val newlinePositions = mutableListOf<Int>()
            for (j in 0 until ssb.length) {
                if (ssb[j] == '\n') newlinePositions.add(j)
            }
            for (nl in newlinePositions.reversed()) {
                ssb.insert(nl + 1, "\n")
                ssb.setSpan(
                    ParagraphLineHeightSpan(spacingPx),
                    nl + 1, nl + 2,
                    android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
        }

        // ── 首行缩进：给每个段落添加 LeadingMarginSpan ──
        android.util.Log.d("EpubParser", "applyParagraphFormatting: indentPx=$indentPx (firstLineIndentChars=$firstLineIndentChars)")
        if (indentPx > 0) {
            var paraStart = 0
            for (j in 0..ssb.length) {
                val isEnd = j == ssb.length
                val isNewline = !isEnd && ssb[j] == '\n'
                if (isEnd || isNewline) {
                    if (paraStart < j) {
                        // 🔥 跳过含图片的段落：图片不应有首行缩进
                        val hasImage = ssb.getSpans(paraStart, j, android.text.style.ImageSpan::class.java).isNotEmpty()
                        if (!hasImage) {
                            ssb.setSpan(
                                android.text.style.LeadingMarginSpan.Standard(indentPx, 0),
                                paraStart, j,
                                android.text.Spannable.SPAN_INCLUSIVE_INCLUSIVE
                            )
                        }
                    }
                    paraStart = j + 1
                }
            }
        }

        // 验证 span 被正确添加
        val spans = ssb.getSpans(0, ssb.length, Any::class.java)
        val indentCount = spans.count { it is android.text.style.LeadingMarginSpan }
        val lineHeightCount = spans.count { it is ParagraphLineHeightSpan }
        android.util.Log.d("EpubParser", "applyParagraphFormatting: indentPx=$indentPx spacingPx=$spacingPx textLen=${ssb.length} LeadingMarginSpans=$indentCount LineHeightSpans=$lineHeightCount")

        return ssb
    }

    /**
     * 修剪末尾多余换行，防止章节末尾出现空白页。
     * 保留最多一个换行作为段落结束标记。
     */
    private fun trimTrailingNewlines(text: CharSequence): CharSequence {
        var end = text.length
        while (end > 0 && text[end - 1] == '\n') end--
        // 保留一个换行作为段落结束
        if (end < text.length) end++
        return if (end == text.length) text else text.subSequence(0, end)
    }

    /**
     * 段间距专用 LineHeightSpan。
     *
     * 仅作用于段落之间的空行，通过增加 descent 来撑大行高。
     * 不影响正文行的高度，避免之前 LineHeightSpan 导致"每页只有几句话"的问题。
     */
    class ParagraphLineHeightSpan(val extraHeightPx: Int) : android.text.style.LineHeightSpan {
        override fun chooseHeight(
            text: CharSequence, start: Int, end: Int,
            spanstartv: Int, lineHeight: Int,
            fm: android.graphics.Paint.FontMetricsInt
        ) {
            // 间距 Span 只能压缩专用空白行。分页切片边界即使残留了异常 Span，
            // 也绝不能把含正文的行高压成几像素，否则会出现整段文字重叠。
            val isSpacerLine = start < end && (start until end).all { index ->
                text[index] == '\n' || text[index] == '\r'
            }
            if (!isSpacerLine) return

            val origAscent = fm.ascent
            val origDescent = fm.descent
            // 强制覆盖字体度量，精确控制空行高度
            fm.ascent = 0
            fm.top = 0
            fm.descent = extraHeightPx
            fm.bottom = extraHeightPx
            android.util.Log.d("EpubParser", "chooseHeight: extraPx=$extraHeightPx origAscent=$origAscent origDescent=$origDescent lineHeight=$lineHeight -> newDescent=$extraHeightPx")
        }
    }

    /**
     * 处理HTML：将图片转为Base64内嵌，并根据 optimizeLayout 决定是否包裹优化CSS。
     * @param optimizeLayout true=包裹自定义CSS覆盖EPUB样式，false=保留EPUB原始CSS
     * @param chapterPath 章节在ZIP中的完整路径，用于解析相对CSS引用
     */
    private fun processHtml(zipFile: ZipFile, html: String, optimizeLayout: Boolean = true, chapterPath: String = ""): String {
        val result = embedImages(zipFile, html)

        return if (optimizeLayout) {
            wrapWithOptimizedLayout(result, paragraphSpacingDp, firstLineIndentChars)
        } else {
            val epubCss = if (useEpubCss && chapterPath.isNotEmpty()) {
                extractEpubCss(zipFile, html, chapterPath)
            } else {
                ""
            }
            wrapWithOriginalLayout(result, epubCss)
        }
    }

    /**
     * 从章节HTML中提取 <link rel="stylesheet"> 引用的CSS文件，从ZIP读取并拼接为字符串。
     * 使用 cssFileCache 避免重复读取同一文件。
     */
    private fun extractEpubCss(zipFile: ZipFile, html: String, chapterPath: String): String {
        val chapterDir = chapterPath.substringBeforeLast("/", "")
        val linkRegex = Regex(
            """<link[^>]+rel\s*=\s*["']stylesheet["'][^>]*href\s*=\s*["']([^"']+)["'][^>]*>""",
            setOf(RegexOption.IGNORE_CASE)
        )
        val hrefRegex = Regex(
            """href\s*=\s*["']([^"']+)["']""",
            RegexOption.IGNORE_CASE
        )
        // 同时支持 href 在 rel 之前的写法
        val allLinkTags = Regex("""<link\b[^>]*>""", RegexOption.IGNORE_CASE)
            .findAll(html)
            .filter { it.value.contains("stylesheet", ignoreCase = true) }
            .mapNotNull { hrefRegex.find(it.value)?.groupValues?.get(1) }
            .toList()

        if (allLinkTags.isEmpty()) return ""

        val sb = StringBuilder()
        for (href in allLinkTags) {
            if (href.startsWith("data:") || href.startsWith("http://") || href.startsWith("https://")) continue
            val rawPath = if (href.startsWith("/")) {
                href.trimStart('/')
            } else if (chapterDir.isEmpty()) {
                href
            } else {
                "$chapterDir/$href"
            }
            val cssPath = normalizePath(rawPath)
            // 命中缓存直接用，否则从 ZIP 读取并缓存
            val cssContent = cssFileCache.getOrPut(cssPath) {
                try {
                    val entry = findEntry(zipFile, cssPath)
                    entry?.let { zipFile.getInputStream(it).bufferedReader().readText() } ?: ""
                } catch (_: Exception) { "" }
            }
            if (cssContent.isNotEmpty()) {
                sb.append("/* $cssPath */\n")
                sb.append(cssContent)
                sb.append("\n")
            }
        }
        return sb.toString()
    }

    /** 将 HTML 中的图片转为 Base64 data URI */
    private fun embedImages(zipFile: ZipFile, html: String): String {
        val imgTagRegex = """<img\s[^>]*?>""".toRegex(RegexOption.IGNORE_CASE)
        val srcRegex = """src\s*=\s*["']([^"']+)["']""".toRegex(RegexOption.IGNORE_CASE)

        return imgTagRegex.replace(html) { tagMatch ->
            val tag = tagMatch.value
            val srcMatch = srcRegex.find(tag) ?: return@replace tag
            val imgPath = srcMatch.groupValues[1]

            if (imgPath.startsWith("data:")) return@replace tag

            val fullPath = resolveImagePath(imgPath)
            try {
                val entry = findEntry(zipFile, fullPath)
                if (entry != null) {
                    val bytes = zipFile.getInputStream(entry).readBytes()
                    if (bytes.isEmpty()) return@replace tag

                    val mimeType = detectMimeType(imgPath, bytes)
                    if (mimeType == "image/svg+xml") return@replace tag

                    val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                    tag.replace(srcRegex, """src="data:$mimeType;base64,$base64"""")
                } else {
                    tag
                }
            } catch (e: Exception) {
                e.printStackTrace()
                tag
            }
        }
    }

    /** 优化排版：包裹自定义CSS模板 */
    private fun wrapWithOptimizedLayout(
        body: String,
        paragraphSpacingDp: Float = 0f,
        firstLineIndentChars: Float = 0f
    ): String {
        // 段间距：用户设置 >= 0 时使用用户值，负数才用默认
        val pMargin = if (paragraphSpacingDp >= 0f) "${paragraphSpacingDp}dp 0" else "8px 0"
        // 首行缩进：用户设置 >= 0 时使用用户值，负数才用默认
        val pIndent = if (firstLineIndentChars >= 0f) "${firstLineIndentChars}em" else "2em"

        return """
            |<html>
            |<head>
            |<meta name="viewport" content="width=device-width, initial-scale=1.0, user-scalable=no">
            |<style>
            |  * { box-sizing: border-box; }
            |  body { font-family: sans-serif; font-size: 18px; line-height: 1.6; letter-spacing: 0.03em; color: #333; background: #fff; text-align: justify; word-wrap: break-word; overflow-wrap: break-word; margin: 0; padding: 0; overflow: hidden; visibility: hidden; }
            |  img { max-width: 100%; max-height: 85vh; height: auto; display: block; margin: 12px auto; border-radius: 4px; object-fit: contain; }
            |  p { margin: $pMargin; text-indent: $pIndent; }
            |  h1, h2, h3 { margin: 16px 0 8px 0; text-align: left; }
            |  h1 { font-size: 1.6em; } h2 { font-size: 1.3em; } h3 { font-size: 1.1em; }
            |  table { max-width: 100%; border-collapse: collapse; }
            |  td, th { padding: 4px 8px; border: 1px solid #ddd; }
            |</style>
            |</head>
            |<body>$body</body>
            |</html>
        """.trimMargin()
    }

    /**
     * 保留原始排版：只添加 viewport，可选注入EPUB自带CSS。
     * @param epubCss 从EPUB ZIP中提取的CSS内容，为空时不注入
     */
    private fun wrapWithOriginalLayout(body: String, epubCss: String = ""): String {
        val epubStyleBlock = if (epubCss.isNotEmpty()) {
            "\n<style id=\"epub-original-css\">\n$epubCss\n</style>"
        } else {
            ""
        }
        return """
            |<html>
            |<head>
            |<meta name="viewport" content="width=device-width, initial-scale=1.0, user-scalable=no">
            |<style>
            |  * { box-sizing: border-box; }
            |  body { overflow: hidden; visibility: hidden; }
            |  img { max-width: 100%; height: auto; }
            |</style>$epubStyleBlock
            |</head>
            |<body>$body</body>
            |</html>
        """.trimMargin()
    }

    private fun resolveImagePath(imgPath: String): String {
        if (imgPath.startsWith("/")) return imgPath.substring(1)
        val rawPath = if (basePath.isNotEmpty()) "$basePath/$imgPath" else imgPath
        return normalizePath(rawPath)
    }

    private fun normalizePath(path: String): String {
        val parts = path.split("/").toMutableList()
        val result = mutableListOf<String>()
        for (part in parts) {
            when (part) {
                ".." -> { if (result.isNotEmpty()) result.removeAt(result.size - 1) }
                "." -> { /* 跳过 */ }
                "" -> { if (result.isEmpty()) result.add(part) }
                else -> result.add(part)
            }
        }
        return result.joinToString("/")
    }

    /** 根据来源章节目录解析 EPUB 内部相对链接。 */
    private fun resolveLinkedChapter(sourceChapterIndex: Int, documentHref: String): Int {
        val decodedHref = decodeUrlComponent(documentHref).replace('\\', '/')
        val sourcePath = chapterPaths[sourceChapterIndex].replace('\\', '/')
        val sourceDirectory = sourcePath.substringBeforeLast('/', "")
        val resolvedPath = if (decodedHref.startsWith('/')) {
            decodedHref.trimStart('/')
        } else {
            normalizePath(
                if (sourceDirectory.isEmpty()) decodedHref else "$sourceDirectory/$decodedHref"
            ).trimStart('/')
        }

        val exactMatch = chapterPaths.indexOfFirst { chapterPath ->
            normalizePath(decodeUrlComponent(chapterPath).replace('\\', '/'))
                .trimStart('/') == resolvedPath
        }
        if (exactMatch >= 0) return exactMatch

        return mapHrefToSpineIndex(decodedHref.trimStart('/'))
    }

    private fun decodeUrlComponent(value: String): String {
        return try {
            // URLDecoder 会把 '+' 当空格；EPUB 文件名和锚点中的 '+' 应保留原义。
            java.net.URLDecoder.decode(value.replace("+", "%2B"), "UTF-8")
        } catch (_: Exception) {
            value
        }
    }

    /** 从 manifest XML 中按 id 提取 href，兼容属性任意顺序 */
    private fun extractHrefById(manifestContent: String, id: String): String? {
        val escaped = Regex.escape(id)
        // id 在 href 之前
        val r1 = """<item\b[^>]*\bid="$escaped"[^>]*\bhref="([^"]+)"""".toRegex(RegexOption.IGNORE_CASE)
        // href 在 id 之前
        val r2 = """<item\b[^>]*\bhref="([^"]+)"[^>]*\bid="$escaped"""".toRegex(RegexOption.IGNORE_CASE)
        return r1.find(manifestContent)?.groupValues?.get(1)
            ?: r2.find(manifestContent)?.groupValues?.get(1)
    }

    private fun findEntry(zipFile: ZipFile, path: String): java.util.zip.ZipEntry? {
        zipFile.getEntry(path)?.let { return it }
        lowercaseEntryIndex[path.lowercase()]?.let { return it }

        val decoded = try { java.net.URLDecoder.decode(path, "UTF-8") } catch (e: Exception) { path }
        if (decoded != path) {
            zipFile.getEntry(decoded)?.let { return it }
            lowercaseEntryIndex[decoded.lowercase()]?.let { return it }
        }

        val withoutDotSlash = path.removePrefix("./")
        if (withoutDotSlash != path) {
            zipFile.getEntry(withoutDotSlash)?.let { return it }
            lowercaseEntryIndex[withoutDotSlash.lowercase()]?.let { return it }
        }

        val fileName = path.substringAfterLast("/")
        if (fileName.isNotEmpty()) {
            lowercaseEntryIndex.values.forEach { entry ->
                if (entry.name.endsWith("/$fileName") || entry.name == fileName) {
                    return entry
                }
            }
        }

        return null
    }

    private fun detectMimeType(path: String, bytes: ByteArray): String {
        val extMime = when {
            path.endsWith(".png", true) -> "image/png"
            path.endsWith(".jpg", true) || path.endsWith(".jpeg", true) -> "image/jpeg"
            path.endsWith(".gif", true) -> "image/gif"
            path.endsWith(".svg", true) -> "image/svg+xml"
            path.endsWith(".webp", true) -> "image/webp"
            else -> null
        }
        if (extMime != null) return extMime

        if (bytes.size >= 4) {
            return when {
                bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() && bytes[2] == 0x4E.toByte() && bytes[3] == 0x47.toByte() -> "image/png"
                bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() -> "image/jpeg"
                bytes[0] == 0x47.toByte() && bytes[1] == 0x49.toByte() && bytes[2] == 0x46.toByte() -> "image/gif"
                bytes[0] == 0x52.toByte() && bytes[1] == 0x49.toByte() && bytes[2] == 0x46.toByte() && bytes[3] == 0x46.toByte() -> "image/webp"
                else -> "image/png"
            }
        }
        return "image/png"
    }

    private fun htmlToSpanned(chapterIndex: Int, html: String, zipFile: ZipFile): Spanned {
        val bodyContent = extractBody(html) ?: html

        // 检测是否是纯图片章节（封面）：有 SVG/image 引用但没有 <img> 标签
        val imgRefRegex = Regex("""(?:xlink:)?href\s*=\s*["']([^"']+\.(?:jpg|jpeg|png|webp|gif))["']""", RegexOption.IGNORE_CASE)
        val existingImgRegex = Regex("""<img\s[^>]*src\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
        val svgImageRefs = imgRefRegex.findAll(bodyContent).map { it.groupValues[1] }.distinct().toList()
        val existingImgSrcs = existingImgRegex.findAll(bodyContent).map { it.groupValues[1] }.toSet()

        // 如果有 SVG 图片引用且没有 <img> 标签，用 <img> 完全替换原始内容
        val preprocessed = if (svgImageRefs.isNotEmpty() && existingImgSrcs.isEmpty()) {
            svgImageRefs.joinToString("") { """<img src="$it"/>""" }
        } else {
            bodyContent
        }

        val cleaned = preprocessed
            .replace(Regex("""<svg[^>]*>.*?</svg>""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)), "")
            .replace(Regex("""<script[^>]*>.*?</script>""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)), "")
            .replace(Regex("""<style[^>]*>.*?</style>""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)), "")
            // 清理空容器（SVG 删除后留下的空 div 等）
            .replace(Regex("""<div[^>]*>\s*</div>""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)), "")
            .replace(Regex("""<span[^>]*>\s*</span>""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)), "")
            .replace(Regex("""\n{3,}"""), "\n\n")

        // 收集注释引用链接（Canvas 引擎注释气泡判定用）
        collectFootnoteHrefs(chapterIndex, cleaned)

        // Html.fromHtml 会丢弃 id/name。先插入不可见占位，转成 Spanned 后再移除并记录偏移。
        val withAnchorMarkers = insertAnchorMarkers(cleaned)

        // 图片前后插入换行，使其独占一行（块级效果）
        val withImageBreaks = withAnchorMarkers.replace(
            Regex("""(<img[^>]*/?>)""", RegexOption.IGNORE_CASE), "\n$1\n"
        )

        val imageGetter = EpubImageGetter(zipFile, contentWidth)
        val parsed = Html.fromHtml(withImageBreaks, Html.FROM_HTML_MODE_LEGACY, imageGetter, null)
        if (chapterIndex != 0) return parsed

        val cover = android.text.SpannableStringBuilder(parsed)
        val images = cover.getSpans(0, cover.length, android.text.style.ImageSpan::class.java)
        val hasVisibleText = cover.any { character ->
            !character.isWhitespace() && character != '\uFFFC'
        }
        if (images.size == 1 && !hasVisibleText && cover.isNotEmpty()) {
            cover.setSpan(
                CoverPageSpan(),
                0,
                cover.length,
                android.text.Spannable.SPAN_INCLUSIVE_INCLUSIVE
            )
        }
        return cover
    }

    private fun insertAnchorMarkers(html: String): String {
        val openingTagRegex = Regex("""<([A-Za-z][^<>]*?)>""")
        val doubleQuotedAnchor = Regex(
            """\s(?:id|name)\s*=\s*"([^"]+)"""",
            RegexOption.IGNORE_CASE
        )
        val singleQuotedAnchor = Regex(
            """\s(?:id|name)\s*=\s*'([^']+)'""",
            RegexOption.IGNORE_CASE
        )

        return openingTagRegex.replace(html) { match ->
            val tag = match.value
            val anchor = doubleQuotedAnchor.find(tag)?.groupValues?.get(1)
                ?: singleQuotedAnchor.find(tag)?.groupValues?.get(1)
                ?: return@replace tag
            val encoded = Base64.encodeToString(
                anchor.toByteArray(Charsets.UTF_8),
                Base64.NO_WRAP or Base64.URL_SAFE
            )
            "$tag$ANCHOR_MARKER_PREFIX$encoded$ANCHOR_MARKER_SUFFIX"
        }
    }

    private fun extractAnchorOffsets(
        chapterIndex: Int,
        text: CharSequence
    ): android.text.SpannableStringBuilder {
        val result = android.text.SpannableStringBuilder(text)
        val offsets = linkedMapOf<String, Int>()
        var searchFrom = 0

        while (searchFrom < result.length) {
            val currentText = result.toString()
            val markerStart = currentText.indexOf(ANCHOR_MARKER_PREFIX, searchFrom)
            if (markerStart < 0) break
            val payloadStart = markerStart + ANCHOR_MARKER_PREFIX.length
            val markerEnd = currentText.indexOf(ANCHOR_MARKER_SUFFIX, payloadStart)
            if (markerEnd < 0) break

            val encoded = currentText.substring(payloadStart, markerEnd)
            val anchor = try {
                String(Base64.decode(encoded, Base64.NO_WRAP or Base64.URL_SAFE), Charsets.UTF_8)
            } catch (_: IllegalArgumentException) {
                ""
            }
            if (anchor.isNotEmpty()) {
                val decodedAnchor = decodeUrlComponent(anchor)
                offsets.putIfAbsent(anchor, markerStart)
                offsets.putIfAbsent(decodedAnchor, markerStart)
                offsets.putIfAbsent(decodedAnchor.lowercase(), markerStart)
            }

            result.delete(markerStart, markerEnd + ANCHOR_MARKER_SUFFIX.length)
            searchFrom = markerStart
        }

        anchorOffsets[chapterIndex] = offsets
        return result
    }

    private inner class EpubImageGetter(
        private val zipFile: ZipFile,
        private val pageContentWidth: Int = 0
    ) : Html.ImageGetter {
        override fun getDrawable(source: String): Drawable? {
            return try {
                android.util.Log.d("EpubParser", "getDrawable: source=${source.take(80)}")

                // 🔥 处理 Base64 data URI（embedImages 转换后的格式）
                if (source.startsWith("data:", ignoreCase = true)) {
                    return decodeDataUri(source)
                }

                val entryPath = resolveImagePath(source) ?: run {
                    android.util.Log.w("EpubParser", "getDrawable: resolveImagePath returned null for $source")
                    return createErrorPlaceholder("Path resolution failed: ${source.take(60)}")
                }
                android.util.Log.d("EpubParser", "getDrawable: entryPath=$entryPath")
                val entry = findEntry(zipFile, entryPath) ?: run {
                    android.util.Log.w("EpubParser", "getDrawable: findEntry returned null for $entryPath")
                    return createErrorPlaceholder("Image not found: ${entryPath.take(60)}")
                }
                android.util.Log.d("EpubParser", "getDrawable: entry=${entry.name} size=${entry.size}")

                val bytes = zipFile.getInputStream(entry).readBytes()
                android.util.Log.d("EpubParser", "getDrawable: readBytes=${bytes.size}")

                // 🔥 SVG 检测：BitmapFactory 无法解码 SVG，返回占位符
                val mimeType = detectMimeType(entry.name, bytes)
                if (mimeType == "image/svg+xml") {
                    android.util.Log.w("EpubParser", "getDrawable: SVG not supported by BitmapFactory, using placeholder for ${entry.name}")
                    return createSvgPlaceholder()
                }

                // 先只读尺寸，不分配内存
                val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
                android.util.Log.d("EpubParser", "getDrawable: originalSize=${opts.outWidth}x${opts.outHeight}")

                // 计算 inSampleSize，目标尺寸 800×1200
                val targetWidth = 800
                val targetHeight = 1200
                val sampleW = opts.outWidth / targetWidth
                val sampleH = opts.outHeight / targetHeight
                opts.inSampleSize = maxOf(sampleW, sampleH).coerceAtLeast(1)
                opts.inJustDecodeBounds = false
                android.util.Log.d("EpubParser", "getDrawable: inSampleSize=${opts.inSampleSize}")

                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
                if (bitmap == null) {
                    android.util.Log.e("EpubParser", "getDrawable: decodeByteArray returned null!")
                    return createErrorPlaceholder("Decode failed: ${entry.name.take(60)}")
                }
                android.util.Log.d("EpubParser", "getDrawable: decoded ${bitmap.width}x${bitmap.height}")

                // 缩放至页面可用宽度，高度等比
                val pageW = if (pageContentWidth > 0) {
                    pageContentWidth
                } else {
                    val dm = context?.resources?.displayMetrics
                        ?: android.content.res.Resources.getSystem().displayMetrics
                    val marginPx = (44 * dm.density).toInt()  // 默认边距 44dp
                    dm.widthPixels - marginPx * 2
                }
                val drawW: Int
                val drawH: Int
                // 🔥 统一缩放到 pageW：无论图片原始尺寸大还是小，都缩放到内容宽度
                val ratio = pageW.toFloat() / bitmap.width.coerceAtLeast(1)
                drawW = pageW
                drawH = (bitmap.height * ratio).toInt()

                val drawable = BitmapDrawable(null, bitmap)
                drawable.setBounds(0, 0, drawW, drawH)
                drawable
            } catch (e: Throwable) {
                android.util.Log.e("EpubParser", "getDrawable: exception", e)
                createErrorPlaceholder("Exception: ${e.message?.take(60) ?: "unknown"}")
            }
        }

        /** 创建 SVG 占位符 Drawable（灰色矩形 + "SVG" 文字） */
        private fun createSvgPlaceholder(): Drawable {
            val dm = context?.resources?.displayMetrics
                ?: android.content.res.Resources.getSystem().displayMetrics
            val pw = if (pageContentWidth > 0) pageContentWidth else (dm.widthPixels - 88)
            val ph = (pw * 0.4f).toInt().coerceAtLeast(80)
            val bitmap = Bitmap.createBitmap(pw, ph, Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bitmap)
            // 灰色背景
            canvas.drawColor(0xFFE0E0E0.toInt())
            // 边框
            val borderPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFFBDBDBD.toInt()
                style = android.graphics.Paint.Style.STROKE
                strokeWidth = 2f
            }
            canvas.drawRect(1f, 1f, pw - 1f, ph - 1f, borderPaint)
            // "SVG" 文字
            val textPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFF757575.toInt()
                textSize = (ph * 0.25f).coerceIn(12f, 48f)
                textAlign = android.graphics.Paint.Align.CENTER
                isFakeBoldText = true
            }
            canvas.drawText("SVG", pw / 2f, ph / 2f + textPaint.textSize / 3f, textPaint)
            val drawable = BitmapDrawable(null, bitmap)
            drawable.setBounds(0, 0, pw, ph)
            return drawable
        }

        /** 创建错误占位符 Drawable（灰色矩形 + 错误图标） */
        private fun createErrorPlaceholder(reason: String): Drawable {
            android.util.Log.w("EpubParser", "createErrorPlaceholder: $reason")
            val dm = context?.resources?.displayMetrics
                ?: android.content.res.Resources.getSystem().displayMetrics
            val pw = if (pageContentWidth > 0) pageContentWidth else (dm.widthPixels - 88)
            val ph = (pw * 0.3f).toInt().coerceAtLeast(60)
            val bitmap = Bitmap.createBitmap(pw, ph, Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bitmap)
            // 浅灰背景
            canvas.drawColor(0xFFF5F5F5.toInt())
            // 虚线边框
            val borderPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFFCCCCCC.toInt()
                style = android.graphics.Paint.Style.STROKE
                strokeWidth = 2f
            }
            canvas.drawRect(1f, 1f, pw - 1f, ph - 1f, borderPaint)
            // "⚠" 文字
            val textPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFF9E9E9E.toInt()
                textSize = (ph * 0.3f).coerceIn(14f, 40f)
                textAlign = android.graphics.Paint.Align.CENTER
            }
            canvas.drawText("⚠", pw / 2f, ph / 2f + textPaint.textSize / 3f, textPaint)
            val drawable = BitmapDrawable(null, bitmap)
            drawable.setBounds(0, 0, pw, ph)
            return drawable
        }

        /** 解析 data:mime;base64,... URI 为 BitmapDrawable */
        private fun decodeDataUri(dataUri: String): Drawable? {
            try {
                // 格式: data:image/png;base64,iVBOR...
                val commaIdx = dataUri.indexOf(',')
                if (commaIdx < 0) return null
                val base64Part = dataUri.substring(commaIdx + 1)
                val bytes = Base64.decode(base64Part, Base64.DEFAULT)
                if (bytes.isEmpty()) return null

                // 先读尺寸
                val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)

                // 降采样
                val targetW = 800
                val targetH = 1200
                opts.inSampleSize = maxOf(opts.outWidth / targetW, opts.outHeight / targetH).coerceAtLeast(1)
                opts.inJustDecodeBounds = false
                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts) ?: return null

                // 缩放至页面宽度
                val dm = context?.resources?.displayMetrics
                    ?: android.content.res.Resources.getSystem().displayMetrics
                val pageW = if (pageContentWidth > 0) {
                    pageContentWidth
                } else {
                    val marginPx = (38 * dm.density).toInt()
                    dm.widthPixels - marginPx * 2
                }
                // 🔥 统一缩放到 pageW：无论图片原始尺寸大还是小，都缩放到内容宽度
                val ratio = pageW.toFloat() / bitmap.width.coerceAtLeast(1)
                val drawW = pageW
                val drawH = (bitmap.height * ratio).toInt()
                val drawable = BitmapDrawable(null, bitmap)
                drawable.setBounds(0, 0, drawW, drawH)
                return drawable
            } catch (e: Throwable) {
                android.util.Log.e("EpubParser", "decodeDataUri failed", e)
                return null
            }
        }
    }

    private fun extractBody(html: String): String? {
        val bodyRegex = Regex(
            """<body[^>]*>(.*?)</body>""",
            setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
        )
        return bodyRegex.find(html)?.groupValues?.get(1)
    }

    private fun extractTitle(html: String): String? {
        val titleRegex = """<title[^>]*>([^<]+)</title>""".toRegex(RegexOption.IGNORE_CASE)
        val titleTag = titleRegex.find(html)?.groupValues?.get(1)?.trim()
        if (!titleTag.isNullOrBlank() && !titleTag.matches(Regex("^\\s*(第?[\\d一二三四五六七八九十百千零]+[章节回卷部篇]|Chapter|CH\\s*\\d|\\d+[\\.、])\\s*$"))) {
            return titleTag
        }

        for (level in 1..6) {
            val headingRegex = """<h$level[^>]*>([^<]+)</h$level>""".toRegex(RegexOption.IGNORE_CASE)
            val heading = headingRegex.find(html)?.groupValues?.get(1)?.trim()
            if (!heading.isNullOrBlank()) {
                return heading
            }
        }

        if (!titleTag.isNullOrBlank()) return titleTag

        return null
    }

    private fun extractCover(zipFile: ZipFile, opfContent: String, sourceKey: String): String? {
        val ctx = context ?: return null

        try {
            val coverIdRegex = """<meta\s+name="cover"\s+content="([^"]+)"""".toRegex()
            val coverId = coverIdRegex.find(opfContent)?.groupValues?.get(1)

            val manifestRegex = """<manifest[^>]*>(.*?)</manifest>""".toRegex(RegexOption.DOT_MATCHES_ALL)
            val manifestContent = manifestRegex.find(opfContent)?.groupValues?.get(1) ?: ""

            var coverHref: String? = null

            // 方式1：<meta name="cover" content="ID"> → 在 manifest 中按 id 找 href
            if (coverId != null) {
                coverHref = extractHrefById(manifestContent, coverId)
            }

            // 方式2：EPUB 3 — <item properties="cover-image" href="...">
            if (coverHref == null) {
                val r1 = """<item[^>]*\bproperties="[^"]*\bcover-image\b[^"]*"[^>]*\bhref="([^"]+)"""".toRegex(RegexOption.IGNORE_CASE)
                val r2 = """<item[^>]*\bhref="([^"]+)"[^>]*\bproperties="[^"]*\bcover-image\b[^"]*"""".toRegex(RegexOption.IGNORE_CASE)
                coverHref = r1.find(manifestContent)?.groupValues?.get(1)
                    ?: r2.find(manifestContent)?.groupValues?.get(1)
            }

            // 方式3：manifest item 的 id 含 "cover"（属性顺序不限）
            if (coverHref == null) {
                val coverIdPattern = """<item\b[^>]*\bid="([^"]*cover[^"]*)"[^>]*>""".toRegex(RegexOption.IGNORE_CASE)
                coverIdPattern.find(manifestContent)?.groupValues?.get(1)?.let { id ->
                    coverHref = extractHrefById(manifestContent, id)
                }
            }

            // 方式4：href 本身含 "cover" 且是图片
            if (coverHref == null) {
                """<item\b[^>]*\bhref="([^"]*cover[^"]*\.(?:jpg|jpeg|png|webp))"""".toRegex(RegexOption.IGNORE_CASE)
                    .find(manifestContent)?.let { coverHref = it.groupValues[1] }
            }

            // 方式5：<meta name="cover"> 指向的是 HTML 章节，从该章节中提取第一张图片
            if (coverHref == null && coverId != null) {
                val htmlHref = extractHrefById(manifestContent, coverId)
                if (htmlHref != null && (htmlHref.endsWith(".html", true) || htmlHref.endsWith(".xhtml", true) || htmlHref.endsWith(".htm", true))) {
                    val htmlPath = if (basePath.isNotEmpty()) "$basePath/$htmlHref" else htmlHref
                    val htmlEntry = findEntry(zipFile, htmlPath)
                    if (htmlEntry != null) {
                        val html = zipFile.getInputStream(htmlEntry).bufferedReader().readText()
                        val imgSrc = """<img\b[^>]*\bsrc="([^"]+)"""".toRegex(RegexOption.IGNORE_CASE).find(html)?.groupValues?.get(1)
                            ?: """xlink:href="([^"]+\.(?:jpg|jpeg|png|webp))"""".toRegex(RegexOption.IGNORE_CASE).find(html)?.groupValues?.get(1)
                        if (imgSrc != null) {
                            val imgPath = normalizePath(if (basePath.isNotEmpty()) "$basePath/$imgSrc" else imgSrc)
                            if (findEntry(zipFile, imgPath) != null) coverHref = imgSrc
                        }
                    }
                }
            }

            if (coverHref == null) {
                zipFile.entries().toList().forEach { entry ->
                    if (entry.name.contains("cover", ignoreCase = true) &&
                        (entry.name.endsWith(".jpg", true) || entry.name.endsWith(".jpeg", true) ||
                         entry.name.endsWith(".png", true) || entry.name.endsWith(".webp", true))) {
                        coverHref = entry.name
                        return@forEach
                    }
                }
            }

            val href = coverHref ?: return null

            val fullPath = if (basePath.isNotEmpty()) "$basePath/$href" else href
            val entry = findEntry(zipFile, fullPath) ?: return null
            val bytes = zipFile.getInputStream(entry).readBytes()
            if (bytes.isEmpty()) return null

            val coversDir = File(ctx.filesDir, "covers")
            coversDir.mkdirs()
            val coverFile = File(coversDir, "${sourceKey.hashCode()}.jpg")
            coverFile.writeBytes(bytes)

            return coverFile.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    override fun extractCoverPath(filePath: String): String? {
        val ctx = context ?: return null
        var lease: SeekableBookSource? = null
        var zipFile: ZipFile? = null
        try {
            lease = BookFileAccess.openSeekable(ctx, filePath)
            val file = File(lease.path)
            if (!file.exists()) return null
            zipFile = ZipFile(file)

            val containerEntry = zipFile.getEntry("META-INF/container.xml") ?: return null
            val containerContent = zipFile.getInputStream(containerEntry).bufferedReader().readText()
            val opfPath = extractOpfPath(containerContent)

            val opfEntry = zipFile.getEntry(opfPath) ?: return null
            val opfContent = zipFile.getInputStream(opfEntry).bufferedReader().readText()
            basePath = opfPath.substringBeforeLast("/", missingDelimiterValue = "")

            return extractCover(zipFile, opfContent, filePath)
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        } finally {
            try { zipFile?.close() } catch (_: Exception) {}
            lease?.close()
        }
    }

    override fun close() {
        synchronized(zipLock) {
            runCatching { sessionZipFile?.close() }
            sessionZipFile = null
            lowercaseEntryIndex = emptyMap()
        }
        clearHtmlCache()
        parsedPackage = null
        sourceLease?.close()
        sourceLease = null
    }
}
