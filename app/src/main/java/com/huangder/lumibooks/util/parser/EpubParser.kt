package com.huangder.lumibooks.util.parser

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.text.Html
import android.text.NoCopySpan
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.LeadingMarginSpan
import android.text.style.QuoteSpan
import kotlin.math.roundToInt
import android.util.Base64
import com.huangder.lumibooks.R
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import java.util.zip.ZipEntry
import java.util.zip.ZipException
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.text.RegexOption
import com.huangder.lumibooks.util.BookFileAccess
import com.huangder.lumibooks.util.SeekableBookSource
import com.huangder.lumibooks.util.epub.EpubPackage
import com.huangder.lumibooks.util.epub.EpubPackageReader
import com.huangder.lumibooks.util.epub.EpubPathResolver
import com.huangder.lumibooks.util.epub.EpubCssIndex
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
        private const val NATIVE_HTML_CHUNK_THRESHOLD = 192 * 1024
        private const val NATIVE_HTML_CHUNK_TARGET = 64 * 1024
        private const val IMAGE_BITMAP_CACHE_BYTES = 16L * 1024L * 1024L
        private const val MAX_IMAGE_RESOURCE_BYTES = 64 * 1024 * 1024
        private const val MAX_VECTOR_BITMAP_WIDTH = 2048
        private const val MAX_VECTOR_BITMAP_HEIGHT = 4096
        private const val JPEG_HEADER_READ_LIMIT = 256 * 1024
        private const val SIMPLE_IMAGE_HEADER_READ_LIMIT = 64
        private val hrefRegex = Regex("""href\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
        private val IMAGE_RESOURCE_EXTENSIONS =
            setOf("jpg", "jpeg", "png", "gif", "webp", "avif", "bmp", "svg")
        private val NATIVE_HTML_BOUNDARY_REGEX = Regex(
            """</(?:p|div|blockquote|li|dd|dt|h[1-6])\s*>""",
            RegexOption.IGNORE_CASE
        )
        private val JPEG_START_OF_FRAME_MARKERS = setOf(
            0xC0, 0xC1, 0xC2, 0xC3,
            0xC5, 0xC6, 0xC7,
            0xC9, 0xCA, 0xCB,
            0xCD, 0xCE, 0xCF
        )

        // ── 注释引用识别（与 WebView 版 EpubDocumentTransformer 的启发式保持一致） ──
        private val FOOTNOTE_HINT_REGEX = Regex(
            """(^|[\s_#./-])(footnotes?|endnotes?|rearnotes?|notes?|fn|en)([\s_./-]|\d|$)""",
            RegexOption.IGNORE_CASE
        )
        // 多看/掌阅等导出器会把注释锚点拼成 footnotebookmark，单词边界
        // 不在 footnote 后面，因此不能只依赖上面的通用边界表达式。
        private val COMPOUND_FOOTNOTE_HINT_REGEX = Regex(
            """(?:footnotebookmark|duokan[-_]footnote)""",
            RegexOption.IGNORE_CASE
        )
        private val FOOTNOTE_BACKLINK_HINT_REGEX = Regex(
            """footnotebookmark[-_]?(?:start|back)""",
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

        private fun hasFootnoteHint(value: String?): Boolean {
            val candidate = value.orEmpty()
            return FOOTNOTE_HINT_REGEX.containsMatchIn(candidate) ||
                COMPOUND_FOOTNOTE_HINT_REGEX.containsMatchIn(candidate)
        }

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
            // 多看注释正文中的链接指回正文锚点（*_start_*），它不是新的注释引用。
            if (FOOTNOTE_BACKLINK_HINT_REGEX.containsMatchIn(fragment)) return false

            val semantics = listOf("epub:type", "role", "rel")
                .mapNotNull { tagAttribute(openTag, it) }
                .joinToString(" ")
                .lowercase()
            if (NOTE_BODY_SEMANTICS_REGEX.containsMatchIn(semantics)) return false
            if (NOTEREF_SEMANTICS_REGEX.containsMatchIn(semantics)) return true
            if (listOf("class", "id", "title").any {
                    tagAttribute(openTag, it)?.let(::hasFootnoteHint) == true
                }) return true
            if (hasFootnoteHint(fragment)) return true

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

        /** `<img>` 标签（含自闭合写法）。 */
        internal val IMAGE_TAG_REGEX = Regex("""<img\b[^>]*>""", RegexOption.IGNORE_CASE)

        private val ANCHOR_TAG_REGEX = Regex(
            """<a\b[^>]*>.*?</a>""",
            setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
        )

        private const val INLINE_IMAGE_PLACEHOLDER_PREFIX = "\u0001lumi-inline-image-"
        private const val INLINE_IMAGE_PLACEHOLDER_SUFFIX = "\u0001"

        /**
         * 注释引用锚点两端的空白（含不换行空格实体）。
         * 这些空白同样落在 `URLSpan` 范围内，会被画成紧贴图标的一小段下划线。
         */
        private val FOOTNOTE_MARKER_PADDING_REGEX = Regex(
            """^(?:[\s\u00A0]|&nbsp;|&#160;|&#xa0;)+|(?:[\s\u00A0]|&nbsp;|&#160;|&#xa0;)+$""",
            RegexOption.IGNORE_CASE
        )

        internal fun inlineImagePlaceholder(index: Int): String =
            INLINE_IMAGE_PLACEHOLDER_PREFIX + index + INLINE_IMAGE_PLACEHOLDER_SUFFIX

        /** 去掉注释引用锚点两端的空白，标记本身保持原有顺序与行内形态。 */
        internal fun trimFootnoteMarkerPadding(innerHtml: String): String =
            innerHtml.replace(FOOTNOTE_MARKER_PADDING_REGEX, "")

        /**
         * 注释引用图标（注释链接内的 `<img>`，如多看/掌阅导出的"注"字小图）是行内标记，
         * 不能像插图那样前后插换行独占一行：否则图标会被顶到左边距，引用所在段落也被拆行。
         * 返回处理后的 HTML 和需要还原的图片标签。
         */
        internal fun protectInlineFootnoteImages(html: String): Pair<String, List<String>> {
            val images = mutableListOf<String>()
            val result = ANCHOR_TAG_REGEX.replace(html) { match ->
                val anchor = match.value
                val openTagEnd = anchor.indexOf('>')
                val closeTagStart = anchor.length - "</a>".length
                if (openTagEnd < 0 || closeTagStart <= openTagEnd) return@replace anchor
                val openTag = anchor.substring(0, openTagEnd + 1)
                val innerHtml = anchor.substring(openTagEnd + 1, closeTagStart)
                if (!isFootnoteAnchorTag(openTag, innerHtml)) return@replace anchor
                val rewrittenInner = IMAGE_TAG_REGEX.replace(innerHtml) { image ->
                    images += image.value
                    inlineImagePlaceholder(images.size - 1)
                }
                openTag + trimFootnoteMarkerPadding(rewrittenInner) + "</a>"
            }
            return result to images
        }

        /**
         * Canvas 引擎用注释气泡呈现注释正文，正文流里不该再重复出现同一段注释。
         *
         * 只处理本章确实引用到的 fragment，并且按锚点自身定位要移除的块：
         * 先匹配"自身带该 id/name 的块级元素"，再匹配"块首以该锚点开头的注释段落"。
         * 不能改成先匹配任意 aside/section/div —— 外层 `<div class="calibre3">`
         * 会先命中，把内层注释一起吞掉（曾经的真实事故）。
         */
        internal fun stripFootnoteBodies(html: String, fragments: Set<String>): String {
            if (fragments.isEmpty()) return html
            var result = html
            fragments.forEach { fragment ->
                val anchor = decodeTagEntities(fragment)
                if (anchor.isBlank()) return@forEach
                result = stripFootnoteTarget(result, anchor.trim())
            }
            return result
        }

        /** 可能承载整段注释正文的块级标签。 */
        private const val FOOTNOTE_BLOCK_TAGS =
            "aside|section|div|blockquote|li|dd|dt|p|td"

        private val BLOCK_OPEN_REGEX = Regex(
            """<($FOOTNOTE_BLOCK_TAGS)\b[^>]*>""",
            RegexOption.IGNORE_CASE
        )

        private val HREF_ATTRIBUTE_REGEX = Regex("""href\s*=\s*["']([^"']*)["']""", RegexOption.IGNORE_CASE)

        /** 语义上明确是注释正文的块（`epub:type="footnote"`、`class="...footnote..."` 等）。 */
        private val FOOTNOTE_SEMANTIC_BODY_REGEX = Regex(
            """<($FOOTNOTE_BLOCK_TAGS)\b[^>]*(?:epub:type|role|class)\s*=\s*["'][^"']*""" +
                """(?:footnote|endnote|rearnote)[^"']*["'][^>]*>""",
            RegexOption.IGNORE_CASE
        )

        /** 块内出现这些标记说明它是注释"引用"所在的正文段，不是注释正文。 */
        private val FOOTNOTE_MARKER_INSIDE_REGEX = Regex(
            """epub:type\s*=\s*["']?noteref|doc-noteref|class\s*=\s*["'][^"']*epub-footnote""",
            RegexOption.IGNORE_CASE
        )

        /** 单条注释正文块过大时不再移除，避免误删整章外壳。 */
        private const val MAX_FOOTNOTE_BODY_LENGTH = 16_000

        /** 向前搜索承载注释的行内锚点所在块时的最大窗口。 */
        private const val MAX_FOOTNOTE_BLOCK_SCAN = 4_000

        /** 只保留标签的文本，用来判断块首是否还有正文。 */
        private val TAG_ONLY_REGEX = Regex("""<[^>]*>""")

        /**
         * 注释段落开头允许出现的标记（注释编号、星号、圆圈数字、"注"等）。
         * 锚点前若还有正文，说明这一块不是注释正文，保持原样。
         */
        private val FOOTNOTE_LEADING_MARK_REGEX = Regex(
            """^[\s\[［【〔(（\]］】〕)）\d０-９*＊①-⑳※注:：.、，,；;]*$"""
        )

        /** 注释锚点所在的块级容器。 */
        private data class EnclosingBlock(
            val start: Int,
            val contentStart: Int,
            val tag: String
        )

        /** 一处注释引用：记下 href 属性在原文里的位置，便于重写。 */
        private data class FootnoteReferenceAnchor(
            val hrefStart: Int,
            val hrefEndExclusive: Int,
            val href: String
        )

        /** 一处注释正文块。 */
        private data class FootnoteBodyElement(
            val start: Int,
            val openTagEnd: Int,
            val openTag: String,
            val tag: String,
            val id: String?,
            val contentStart: Int,
            val closeStart: Int,
            val closeEnd: Int
        )

        /** 注释引用与正文对齐后的结果。 */
        internal data class FootnoteAlignment(
            val html: String,
            val hrefs: Set<String>,
            val textByHref: Map<String, String>
        )

        /**
         * 把注释"引用"和注释"正文"一一配对。
         *
         * 出版方导出常有错漏：正文 `<aside epub:type="footnote">` 没有 id，
         * 或者多个引用 href 指向同一个 id（《一生之敌》就是这样）。这里先按 id 精确配对，
         * 剩下的按文档顺序配对，然后把引用 href 改写到配对正文的 id 上；
         * 正文缺 id 时补一个合成 id 并预先抽出正文文本，保证气泡能取到正确内容。
         */
        internal fun alignFootnoteReferences(html: String, idPrefix: String): FootnoteAlignment {
            // 绝大多数章节没有注释，先做一次廉价判断，避免整章多次正则扫描。
            val mentionsNotes = listOf("footnote", "noteref", "endnote", "rearnote", "epub:type")
                .any { html.contains(it, ignoreCase = true) }
            if (!mentionsNotes) {
                return FootnoteAlignment(html, emptySet(), emptyMap())
            }
            val references = collectFootnoteReferences(html)
            if (references.isEmpty()) {
                return FootnoteAlignment(html, emptySet(), emptyMap())
            }
            val fragments = references.mapNotNull { footnoteFragmentOf(it.href) }.toSet()
            val bodies = collectFootnoteBodies(html, fragments)
            if (bodies.isEmpty()) {
                return FootnoteAlignment(html, references.map { it.href }.toSet(), emptyMap())
            }

            val used = mutableSetOf<Int>()
            val edits = mutableListOf<Triple<Int, Int, String>>()
            val hrefs = linkedSetOf<String>()
            val textByHref = linkedMapOf<String, String>()
            var generated = 0

            references.forEach { reference ->
                val fragment = footnoteFragmentOf(reference.href)
                var bodyIndex = bodies.indices.firstOrNull { index ->
                    index !in used && fragment != null &&
                        bodies[index].id?.equals(fragment, ignoreCase = true) == true
                }
                if (bodyIndex == null) bodyIndex = bodies.indices.firstOrNull { it !in used }
                if (bodyIndex == null) {
                    hrefs += reference.href
                    return@forEach
                }
                used += bodyIndex
                val body = bodies[bodyIndex]
                var bodyId = body.id
                if (bodyId == null) {
                    bodyId = idPrefix + (++generated)
                    edits += Triple(body.start, body.openTagEnd + 1, insertIdAttribute(body.openTag, bodyId))
                    textByHref["#" + bodyId] =
                        footnoteBodyText(html.substring(body.contentStart, body.closeStart))
                }
                val target = "#" + bodyId
                hrefs += target
                if (target != reference.href) {
                    edits += Triple(
                        reference.hrefStart,
                        reference.hrefEndExclusive,
                        """href="$target""""
                    )
                }
            }

            var aligned = html
            edits.sortedByDescending { it.first }.forEach { (start, end, replacement) ->
                if (start in 0..aligned.length && end in start..aligned.length) {
                    aligned = aligned.replaceRange(start, end, replacement)
                }
            }
            return FootnoteAlignment(aligned, hrefs, textByHref)
        }

        /** href 里的 fragment，解码后返回；没有 fragment 时返回 null。 */
        internal fun footnoteFragmentOf(href: String): String? {
            if ('#' !in href) return null
            val raw = href.substringAfter('#')
            if (raw.isBlank()) return null
            return try {
                java.net.URLDecoder.decode(raw.replace("+", "%2B"), "UTF-8")
            } catch (_: Exception) {
                raw
            }
        }

        private fun collectFootnoteReferences(html: String): List<FootnoteReferenceAnchor> {
            val result = mutableListOf<FootnoteReferenceAnchor>()
            ANCHOR_TAG_REGEX.findAll(html).forEach { match ->
                val anchor = match.value
                val openTagEnd = anchor.indexOf('>')
                val closeTagStart = anchor.length - "</a>".length
                if (openTagEnd < 0 || closeTagStart <= openTagEnd) return@forEach
                val openTag = anchor.substring(0, openTagEnd + 1)
                val innerHtml = anchor.substring(openTagEnd + 1, closeTagStart)
                if (!isFootnoteAnchorTag(openTag, innerHtml)) return@forEach
                val hrefMatch = HREF_ATTRIBUTE_REGEX.find(openTag) ?: return@forEach
                result += FootnoteReferenceAnchor(
                    hrefStart = match.range.first + hrefMatch.range.first,
                    hrefEndExclusive = match.range.first + hrefMatch.range.last + 1,
                    href = decodeTagEntities(hrefMatch.groupValues[1])
                )
            }
            return result
        }

        private fun collectFootnoteBodies(html: String, fragments: Set<String>): List<FootnoteBodyElement> {
            val starts = linkedSetOf<Int>()
            FOOTNOTE_SEMANTIC_BODY_REGEX.findAll(html).forEach { starts += it.range.first }
            fragments.forEach { fragment ->
                containerMatch(html, fragment)?.let { starts += it.range.first }
            }
            return starts.sorted().mapNotNull { footnoteBodyElementAt(html, it) }
        }

        /** 块级元素自身带 id/name 时的匹配（注释正文常见形态）。 */
        private fun containerMatch(html: String, anchor: String): MatchResult? {
            val pattern = Regex(
                """<($FOOTNOTE_BLOCK_TAGS)\b[^>]*\b(?:id|name)\s*=\s*["']""" +
                    Regex.escape(anchor) +
                    """["'][^>]*>.*?</\1\s*>""",
                setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
            )
            return pattern.find(html)
        }

        private fun footnoteBodyElementAt(html: String, start: Int): FootnoteBodyElement? {
            val openMatch = BLOCK_OPEN_REGEX.find(html, start) ?: return null
            if (openMatch.range.first != start) return null
            val openTag = openMatch.value
            val tag = Regex("""<\s*([A-Za-z0-9]+)""")
                .find(openTag)
                ?.groupValues
                ?.get(1)
                ?.lowercase()
                ?: return null
            val close = Regex("""</$tag\s*>""", RegexOption.IGNORE_CASE)
                .find(html, openMatch.range.last + 1)
                ?: return null
            val contentStart = openMatch.range.last + 1
            val inner = html.substring(contentStart, close.range.first)
            if (inner.isBlank()) return null
            // 块里还有注释标记，说明这是引用所在的正文段，不能当作注释正文。
            if (FOOTNOTE_MARKER_INSIDE_REGEX.containsMatchIn(inner)) return null
            return FootnoteBodyElement(
                start = start,
                openTagEnd = openMatch.range.last,
                openTag = openTag,
                tag = tag,
                id = tagAttribute(openTag, "id") ?: tagAttribute(openTag, "name"),
                contentStart = contentStart,
                closeStart = close.range.first,
                closeEnd = close.range.last + 1
            )
        }

        /** 给开放标签补 id（自闭合标签除外）。 */
        private fun insertIdAttribute(openTag: String, id: String): String {
            var insertAt = openTag.lastIndexOf('>')
            if (insertAt < 0) return openTag
            if (insertAt > 0 && openTag[insertAt - 1] == '/') insertAt -= 1
            return openTag.substring(0, insertAt) + """ id="$id"""" + openTag.substring(insertAt)
        }

        /** 注释正文纯文本（去脚本/返回链接/标签）。 */
        private fun footnoteBodyText(innerHtml: String): String {
            var text = Regex(
                """<(script|style)\b.*?</\1\s*>""",
                setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
            ).replace(innerHtml, "")
            text = Regex(
                """<a\b[^>]*(?:backlink|doc-backlink)[^>]*>.*?</a\s*>""",
                setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
            ).replace(text, "")
            text = TAG_ONLY_REGEX.replace(text, "")
            return decodeTagEntities(text).replace(Regex("""\s+"""), " ").trim()
        }

        /**
         * 单条注释对应正文块的移除。两种常见结构：
         * 1. 块级元素自身带注释 id：`<aside epub:type="footnote" id="fn1">…`、`<p id="note-1">…`；
         * 2. 行内锚点标记注释起点：`<p class="zs"><a id="id1a">〔1〕</a>注释正文…</p>`（《毛泽东选集》）。
         */
        private fun stripFootnoteTarget(html: String, anchor: String): String {
            stripContainerTarget(html, anchor)?.let { return it }
            stripInlineAnchorTarget(html, anchor)?.let { return it }
            return html
        }

        /** 结构 1：块级元素自身带 id/name。 */
        private fun stripContainerTarget(html: String, anchor: String): String? {
            val match = containerMatch(html, anchor) ?: return null
            if (match.value.length > MAX_FOOTNOTE_BODY_LENGTH) return null
            if (linksToAnchor(match.value, anchor)) return null
            val openTag = match.value.substringBefore('>') + ">"
            if (!isFootnoteBodyContainer(openTag, anchor)) return null
            return html.removeRange(match.range)
        }

        /** 结构 2：行内锚点位于块首，整块都是注释正文。 */
        private fun stripInlineAnchorTarget(html: String, anchor: String): String? {
            val pattern = Regex(
                """<(a|span|sup|em|i|b|strong|font)\b[^>]*\b(?:id|name)\s*=\s*["']""" +
                    Regex.escape(anchor) +
                    """["'][^>]*>""",
                RegexOption.IGNORE_CASE
            )
            var searchFrom = 0
            while (true) {
                val match = pattern.find(html, searchFrom) ?: return null
                searchFrom = match.range.last + 1
                val block = enclosingBlock(html, match.range.first) ?: continue
                val leadingHtml = html.substring(block.contentStart, match.range.first)
                // 块首只能有注释编号之类的标记，否则整块不是注释正文。
                val leadingText = decodeTagEntities(TAG_ONLY_REGEX.replace(leadingHtml, ""))
                if (!FOOTNOTE_LEADING_MARK_REGEX.matches(leadingText)) continue
                val close = Regex("""</${block.tag}\s*>""", RegexOption.IGNORE_CASE)
                    .find(html, match.range.last)
                    ?: continue
                val end = close.range.last + 1
                if (end - block.start > MAX_FOOTNOTE_BODY_LENGTH) continue
                // 块内若有指向该注释自身的链接，说明这一块是引用所在正文，不能删。
                if (linksToAnchor(html.substring(block.start, end), anchor)) continue
                return html.removeRange(block.start, end)
            }
        }

        /** 片段内是否存在 `href="#anchor"` 形式的链接（即注释引用本身）。 */
        private fun linksToAnchor(fragmentHtml: String, anchor: String): Boolean {
            if (!fragmentHtml.contains("href", ignoreCase = true)) return false
            val pattern = Regex(
                """<a\b[^>]*href\s*=\s*["'][^"']*#""" + Regex.escape(anchor) + """["']""",
                RegexOption.IGNORE_CASE
            )
            return pattern.containsMatchIn(fragmentHtml)
        }

        /** 从 position 向前找到包含它的最近块级元素（且该元素尚未闭合）。 */
        private fun enclosingBlock(html: String, position: Int): EnclosingBlock? {
            val from = (position - MAX_FOOTNOTE_BLOCK_SCAN).coerceAtLeast(0)
            val window = html.substring(from, position)
            val tagPattern = Regex(
                """<($FOOTNOTE_BLOCK_TAGS)\b[^>]*>""",
                RegexOption.IGNORE_CASE
            )
            tagPattern.findAll(window).toList().asReversed().forEach { candidate ->
                val tag = Regex("""<\s*([A-Za-z0-9]+)""")
                    .find(candidate.value)
                    ?.groupValues
                    ?.get(1)
                    ?.lowercase()
                    ?: return@forEach
                val between = window.substring(candidate.range.last + 1)
                if (Regex("""</$tag\s*>""", RegexOption.IGNORE_CASE).containsMatchIn(between)) {
                    return@forEach
                }
                return EnclosingBlock(
                    start = from + candidate.range.first,
                    contentStart = from + candidate.range.last + 1,
                    tag = tag
                )
            }
            return null
        }

        /**
         * 注释正文容器判定：`aside`/`section` 语义明确；`p`/`li` 等行文段落直接可用；
         * `div` 可能是整章外壳，需要额外带注释语义才移除。
         */
        private fun isFootnoteBodyContainer(openTag: String, anchor: String): Boolean {
            val tag = Regex("""<\s*([A-Za-z0-9]+)""")
                .find(openTag)
                ?.groupValues
                ?.get(1)
                ?.lowercase()
                ?: return false
            if (tag != "div") return true
            val semantics = listOf("epub:type", "role", "class")
                .mapNotNull { tagAttribute(openTag, it) }
                .joinToString(" ")
            return hasFootnoteHint(semantics) || hasFootnoteHint(anchor)
        }
    }

    override var paragraphSpacingDp: Float = 0f
    override var firstLineIndentChars: Float = 0f

    /** 阅读区域内容宽度（像素），用于图片缩放。0 表示未设置，回退到 DisplayMetrics 计算 */
    override var contentWidth: Int = 0
    /** 是否加载 EPUB 自带 CSS 样式 */
    override var useEpubCss: Boolean = false
    /**
     * 阅读器排版是否还原原书背景装饰（跟随「保留原书背景」设置）。
     *
     * 关闭时章节正文里不再插入出版社 CSS 的 background-image 装饰图；墨水屏模式会把它关掉。
     */
    override var preserveEpubBackground: Boolean = true
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

    /**
     * Reader-layout chapters. Some EPUB 2 books put the whole book in one spine item and
     * expose real chapters only as NCX fragments. Treat those fragments as logical chapters
     * so opening a book does not require laying out hundreds of thousands of characters.
     */
    private data class ChapterSource(
        val path: String,
        val title: String? = null,
        val startAnchor: String? = null,
        val endAnchor: String? = null
    )
    private var chapterSources: List<ChapterSource> = emptyList()
    private var logicalChapterIndicesByPath: Map<String, List<Int>> = emptyMap()

    // spine 中的 href 列表（用于 NCX → spine 索引映射）
    private var spineHrefs: List<String> = emptyList()

    // EPUB chapters vary greatly in size, so bound decoded data by memory weight.
    private data class HtmlCacheKey(
        val revision: Long,
        val chapterIndex: Int,
        val optimizeLayout: Boolean
    )
    private data class ContentLoadKey(val revision: Long, val chapterIndex: Int)
    private data class AnchoredChapter(
        val text: SpannableStringBuilder,
        val offsets: Map<String, Int>
    )
    private val htmlCache = WeightedLruCache<HtmlCacheKey, String>(8L * 1024L * 1024L) {
        it.length.toLong() * Char.SIZE_BYTES
    }
    private val contentCache = WeightedLruCache<ContentLoadKey, CharSequence>(16L * 1024L * 1024L) {
        it.length.toLong() * Char.SIZE_BYTES
    }
    /** Keep only a small set of decoded images; individual spans reload on demand after eviction. */
    private val imageBitmapCache = WeightedLruCache<String, Bitmap>(IMAGE_BITMAP_CACHE_BYTES) {
        it.allocationByteCount.toLong().coerceAtLeast(0L)
    }
    private val anchorOffsets = mutableMapOf<Int, Map<String, Int>>()
    // CSS 文件内容缓存（key = ZIP 内完整路径，避免重复读取同一文件）
    private val cssFileCache = mutableMapOf<String, String>()
    // 章节样式表规则索引缓存（key = 章节路径，小写）
    private val cssIndexCache = mutableMapOf<String, EpubCssIndex>()
    // 各章节中识别为注释引用的 href 集合（Canvas 引擎注释气泡用）
    private val footnoteHrefs = mutableMapOf<Int, Set<String>>()
    // 导出错误（正文缺 id）时按顺序配对得到的注释正文，key = 重写后的 href
    private val footnoteTextByHref = mutableMapOf<Int, Map<String, String>>()
    private val contentRevision = AtomicLong(0L)
    private val contentLoadLocks = ConcurrentHashMap<ContentLoadKey, Any>()

    override fun parse(filePath: String): BookContent {
        close()
        android.util.Log.i("EpubParser", "parse: start path=$filePath")
        val lease = context?.let { BookFileAccess.openSeekable(it, filePath) }
        sourceLease = lease
        epubFilePath = prepareCompatibleEpub(lease?.path ?: filePath)
        val cachedMetadata = context?.let { EpubMetadataCache.read(it, filePath, epubFilePath) }
        android.util.Log.i("EpubParser", "parse: metadata cache=${cachedMetadata != null}")
        val packageModel = cachedMetadata?.epubPackage ?: EpubPackageReader.read(epubFilePath)
        parsedPackage = packageModel
        basePath = packageModel.basePath
        bookTitle = packageModel.title
        bookAuthor = packageModel.author.ifBlank {
            context?.getString(R.string.book_author_unknown) ?: "Unknown author"
        }
        val spinePaths = packageModel.spine.map { it.manifestItem.fullPath }
        spineHrefs = packageModel.spine.map { it.manifestItem.href }

        chapterSources = buildLogicalChapterSources(spinePaths, packageModel.navigation)
        chapterPaths = chapterSources.map(ChapterSource::path)
        logicalChapterIndicesByPath = chapterSources.indices.groupBy { index ->
            canonicalPath(chapterSources[index].path).orEmpty().lowercase()
        }

        val navigationTitles = packageModel.navigation.mapNotNull { item ->
            val path = canonicalPath(item.href.substringBefore('#')) ?: return@mapNotNull null
            path to item.title
        }.toMap()
        val chapterIndexByPath = chapterPaths.mapIndexedNotNull { index, path ->
            canonicalPath(path)?.let { it to index }
        }.toMap()
        val chapterIndexByLowerPath = chapterPaths.mapIndexedNotNull { index, path ->
            canonicalPath(path)?.lowercase()?.let { it to index }
        }.toMap()

        val zipFile = ZipFile(epubFilePath)
        sessionZipFile = zipFile
        lowercaseEntryIndex = zipFile.entries().asSequence().associateBy { it.name.lowercase() }
        val chapterTitles = cachedMetadata?.chapterTitles
            ?.takeIf { it.size == chapterSources.size && chapterSources.none { source -> source.startAnchor != null } }
            ?: chapterSources.mapIndexed { index, source ->
                source.title?.takeIf { it.isNotBlank() }
                    ?: navigationTitles[canonicalPath(source.path)]?.takeIf { it.isNotBlank() } ?: run {
                    val chapterPath = source.path
                    val entry = findEntry(zipFile, chapterPath)
                    val preview = entry?.let {
                        zipFile.getInputStream(it).use { input ->
                            val buffer = ByteArray(8192)
                            val count = input.read(buffer).coerceAtLeast(0)
                            String(buffer, 0, count, Charsets.UTF_8)
                        }
                    }.orEmpty()
                    extractTitle(preview)
                        ?: context?.getString(R.string.chapter_number, index + 1)
                        ?: "Chapter ${index + 1}"
                }
            }
        chapters = chapterTitles.mapIndexed { index, title ->
            Chapter(index = index, title = title, content = "", htmlContent = "")
        }

        val tocEntries = packageModel.navigation.map { item ->
            val documentPath = item.href.substringBefore('#')
            val normalizedPath = canonicalPath(documentPath)
            val anchor = item.href.substringAfter('#', "").ifBlank { null }
                ?.let(::decodeUrlComponent)
            val chapterIndex = findLogicalChapterIndex(normalizedPath, anchor)
                ?: normalizedPath?.let { chapterIndexByPath[it] }
                ?: normalizedPath?.lowercase()?.let { chapterIndexByLowerPath[it] }
                ?: chapterPaths.indexOf(documentPath)
            TocEntry(
                title = item.title,
                level = item.level,
                chapterIndex = chapterIndex,
                isGroup = chapterIndex < 0,
                // A fragment used as a logical chapter boundary now maps to offset zero.
                anchor = anchor?.takeUnless {
                    chapterSources.getOrNull(chapterIndex)?.startAnchor.equals(it, ignoreCase = true)
                }
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
        ).also {
            android.util.Log.i(
                "EpubParser",
                "parse: complete spine=${spinePaths.size} logical=${chapterSources.size} toc=${tocEntries.size}"
            )
        }
    }

    /**
     * Android's ZipFile rejects archives containing duplicate central-directory names. A number
     * of otherwise valid EPUBs in the wild contain a second `mimetype` entry. Build a cached,
     * byte-for-byte resource copy with later duplicate entries omitted; the user's book is never
     * modified and normal archives continue to use their original path.
     */
    private fun prepareCompatibleEpub(sourcePath: String): String {
        val appContext = context ?: run {
            // Test-only path: surface the raw ZipException instead of consulting the cache.
            ZipFile(sourcePath).use { }
            return sourcePath
        }
        val prepared = com.huangder.lumibooks.util.zip.ZipCompatRepair.prepare(
            context = appContext,
            sourcePath = sourcePath,
            cacheDirectoryName = "epub_compat",
            cachedExtension = "epub",
            emptyArchiveMessage = "EPUB archive is empty"
        )
        if (prepared != sourcePath) {
            android.util.Log.i("EpubParser", "parse: using cached duplicate-entry repair")
        }
        return prepared
    }

    private fun buildLogicalChapterSources(
        spinePaths: List<String>,
        navigation: List<com.huangder.lumibooks.util.epub.EpubNavigationItem>
    ): List<ChapterSource> {
        val navigationByPath = navigation.mapNotNull { item ->
            val path = canonicalPath(item.href.substringBefore('#')) ?: return@mapNotNull null
            val anchor = item.href.substringAfter('#', "").ifBlank { null }
                ?.let(::decodeUrlComponent)
            Triple(path.lowercase(), item, anchor)
        }.groupBy { it.first }

        return buildList {
            spinePaths.forEach { path ->
                val key = canonicalPath(path)?.lowercase()
                val anchoredItems = key?.let(navigationByPath::get).orEmpty()
                    .filter { it.third != null }
                    .distinctBy { it.third?.lowercase() }
                if (anchoredItems.size < 2) {
                    add(ChapterSource(path = path))
                } else {
                    anchoredItems.forEachIndexed { index, (_, item, anchor) ->
                        add(
                            ChapterSource(
                                path = path,
                                title = item.title,
                                startAnchor = anchor,
                                endAnchor = anchoredItems.getOrNull(index + 1)?.third
                            )
                        )
                    }
                }
            }
        }
    }

    private fun findLogicalChapterIndex(path: String?, anchor: String?): Int? {
        val key = path?.lowercase() ?: return null
        val candidates = logicalChapterIndicesByPath[key].orEmpty()
        if (candidates.isEmpty()) return null
        if (anchor != null) {
            candidates.firstOrNull { index ->
                chapterSources[index].startAnchor.equals(anchor, ignoreCase = true)
            }?.let { return it }
        }
        return candidates.first()
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
                    val title = extractTitle(rawHtml)
                        ?: context?.getString(R.string.chapter_number, index + 1)
                        ?: "Chapter ${index + 1}"
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
                        val title = extractTitle(rawHtml)
                            ?: context?.getString(R.string.chapter_number, index + 1)
                            ?: "Chapter ${index + 1}"
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
        val spineIndex = matches.singleOrNull() ?: return -1
        val matchedPath = parsedPackage?.spine?.getOrNull(spineIndex)?.manifestItem?.fullPath
            ?: return -1
        return findLogicalChapterIndex(canonicalPath(matchedPath), anchor = null) ?: -1
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
        val revision = contentRevision.get()
        val cacheKey = HtmlCacheKey(revision, chapterIndex, optimizeLayout)
        htmlCache[cacheKey]?.let { return it }
        if (chapterIndex !in chapterPaths.indices) return ""

        val source = chapterSources.getOrNull(chapterIndex) ?: return ""
        val path = source.path
        return try {
            val sourcePath = synchronized(zipLock) {
                if (sessionZipFile == null || contentRevision.get() != revision) return ""
                epubFilePath
            }
            val processed = ZipFile(sourcePath).use { zipFile ->
                val zipEntry = findEntry(zipFile, path) ?: return ""
                val rawHtml = zipFile.getInputStream(zipEntry).bufferedReader().use { it.readText() }
                    .let { sliceLogicalChapterHtml(it, source) }
                processHtml(zipFile, rawHtml, optimizeLayout, chapterPath = path)
            }
            if (contentRevision.get() == revision) {
                processed.also {
                    htmlCache.put(cacheKey, it)
                }
            } else {
                ""
            }
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }
    }

    /**
     * 按需加载单个章节的 Spanned，带缓存
     */
    override fun getChapterContent(chapterIndex: Int): CharSequence {
        if (chapterIndex !in chapterPaths.indices) {
            android.util.Log.w("EpubParser", "getChapterContent: idx=$chapterIndex out of range (chapterPaths.size=${chapterPaths.size})")
            return ""
        }

        val revision = contentRevision.get()
        val loadKey = ContentLoadKey(revision, chapterIndex)
        contentCache[loadKey]?.let {
            android.util.Log.d("EpubParser", "getChapterContent: idx=$chapterIndex from cache, length=${it.length}")
            return it
        }
        val loadLock = contentLoadLocks.getOrPut(loadKey) { Any() }
        try {
            return synchronized(loadLock) chapterLoad@{
                contentCache[loadKey]?.let {
                    if (contentRevision.get() == revision) return@chapterLoad it
                }

                val source = chapterSources.getOrNull(chapterIndex) ?: return@chapterLoad ""
                val path = source.path
                android.util.Log.d("EpubParser", "getChapterContent: idx=$chapterIndex path=$path")
                val chapterSource = synchronized(zipLock) sourceRead@{
                    if (contentRevision.get() != revision) return@sourceRead null
                    val activeZip = sessionZipFile ?: return@sourceRead null
                    val zipEntry = findEntry(activeZip, path) ?: return@sourceRead null
                    activeZip to activeZip.getInputStream(zipEntry).bufferedReader().use { reader ->
                        sliceLogicalChapterHtml(reader.readText(), source)
                    }
                } ?: return@chapterLoad ""
                val (zipFile, rawHtml) = chapterSource
                android.util.Log.d("EpubParser", "getChapterContent: idx=$chapterIndex rawHtml.length=${rawHtml.length}")

                val parseStartedAt = android.os.SystemClock.elapsedRealtime()
                val spanned = htmlToSpanned(chapterIndex, rawHtml, zipFile)
                val htmlParsedAt = android.os.SystemClock.elapsedRealtime()
                if (!isActiveRevision(revision, zipFile)) return@chapterLoad ""

                // 应用段间距和首行缩进（Canvas 引擎需要在 Spanned 层面处理）
                val formatted = applyParagraphFormatting(
                    spanned,
                    chapterTitle = chapters.getOrNull(chapterIndex)?.title.orEmpty()
                )
                val formattedAt = android.os.SystemClock.elapsedRealtime()
                // 修剪末尾多余换行（防止章节末尾出现空白页）
                val trimmed = trimTrailingNewlines(formatted)
                // 移除 HTML 锚点占位，并记录其在最终章节文本中的字符位置
                val anchored = extractAnchorOffsets(trimmed)
                val anchoredAt = android.os.SystemClock.elapsedRealtime()
                // 如果 Spanned 为空（纯图片章节图片加载失败），返回占位文本
                val result: CharSequence = if (anchored.text.isBlank()) {
                    android.text.SpannableString(" ")
                } else {
                    anchored.text
                }

                val stored = synchronized(zipLock) storeResult@{
                    if (!isActiveRevisionLocked(revision, zipFile)) return@storeResult false
                    anchorOffsets[chapterIndex] = anchored.offsets
                    contentCache.put(loadKey, result)
                    true
                }
                if (!stored) return@chapterLoad ""
                android.util.Log.d(
                    "EpubParser",
                    "getChapterContent: idx=$chapterIndex result.length=${result.length} " +
                        "html=${htmlParsedAt - parseStartedAt}ms format=${formattedAt - htmlParsedAt}ms " +
                        "anchors=${anchoredAt - formattedAt}ms total=${anchoredAt - parseStartedAt}ms"
                )
                result
            }
        } catch (e: Exception) {
            android.util.Log.e("EpubParser", "getChapterContent: exception for idx=$chapterIndex", e)
            e.printStackTrace()
            return ""
        } finally {
            contentLoadLocks.remove(loadKey, loadLock)
        }
    }

    private fun isActiveRevision(revision: Long, zipFile: ZipFile): Boolean = synchronized(zipLock) {
        isActiveRevisionLocked(revision, zipFile)
    }

    private fun isActiveRevisionLocked(revision: Long, zipFile: ZipFile): Boolean =
        contentRevision.get() == revision && sessionZipFile === zipFile

    override fun getChapterCount(): Int = chapterPaths.size

    private fun sliceLogicalChapterHtml(html: String, source: ChapterSource): String {
        val startAnchor = source.startAnchor ?: return html
        val start = findAnchorElementStart(html, startAnchor) ?: return html
        val end = source.endAnchor
            ?.let { findAnchorElementStart(html, it, start + 1) }
            ?.coerceAtLeast(start)
            ?: html.length
        return html.substring(start, end)
    }

    private fun findAnchorElementStart(html: String, anchor: String, fromIndex: Int = 0): Int? {
        val escaped = Regex.escape(anchor)
        val anchorTag = Regex(
            """<[^>]+\b(?:id|name)\s*=\s*[\"']$escaped[\"'][^>]*>""",
            RegexOption.IGNORE_CASE
        )
        return anchorTag.find(html, fromIndex)?.range?.first
    }

    override fun resolveLink(sourceChapterIndex: Int, href: String): BookLinkTarget? {
        if (sourceChapterIndex !in chapterPaths.indices) return null

        val trimmedHref = href.trim()
        if (trimmedHref.isEmpty() || trimmedHref.startsWith("//")) return null
        if (Regex("^[A-Za-z][A-Za-z0-9+.-]*:").containsMatchIn(trimmedHref)) return null

        val documentHref = trimmedHref.substringBefore('#').substringBefore('?')
        var targetChapter = if (documentHref.isBlank()) {
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

        val targetPath = chapterSources.getOrNull(targetChapter)?.path
        val logicalTarget = findLogicalChapterIndex(canonicalPath(targetPath.orEmpty()), fragment)
        if (logicalTarget != null &&
            chapterSources[logicalTarget].startAnchor.equals(fragment, ignoreCase = true)
        ) {
            return BookLinkTarget(logicalTarget, 0)
        }

        // 目标章可能尚未分页，先按需解析以建立 id/name → 字符偏移映射。
        getChapterContent(targetChapter)
        val offset = synchronized(zipLock) {
            anchorOffsets[targetChapter]?.get(fragment)
                ?: anchorOffsets[targetChapter]?.get(fragment.lowercase())
                ?: 0
        }
        return BookLinkTarget(targetChapter, offset)
    }

    override fun clearHtmlCache() {
        contentRevision.incrementAndGet()
        htmlCache.clear()
        contentCache.clear()  // 段间距/首行缩进变更时也需要清空内容缓存
        clearDecodedImageCache()
        synchronized(zipLock) {
            anchorOffsets.clear()
            cssFileCache.clear()
            footnoteHrefs.clear()
            footnoteTextByHref.clear()
        }
        synchronized(cssIndexCache) { cssIndexCache.clear() }
    }

    internal fun decodedImageCacheSizeForTest(): Int = imageBitmapCache.size

    private fun clearDecodedImageCache() {
        synchronized(zipLock) {
            imageBitmapCache.values.toSet().forEach { bitmap ->
                if (!bitmap.isRecycled) bitmap.recycle()
            }
            imageBitmapCache.clear()
        }
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
        synchronized(zipLock) {
            footnoteHrefs[chapterIndex] = hrefs
        }
    }

    override fun isFootnoteHref(chapterIndex: Int, href: String): Boolean {
        if (chapterIndex !in chapterPaths.indices) return false
        // 章节已展示时必有缓存；这里兜底确保 href 集合已收集
        getChapterContent(chapterIndex)
        val key = href.trim()
        return synchronized(zipLock) {
            footnoteHrefs[chapterIndex]?.contains(key) == true ||
                footnoteTextByHref[chapterIndex]?.containsKey(key) == true
        }
    }

    override fun resolveFootnoteText(sourceChapterIndex: Int, href: String): String? {
        val trimmed = href.trim()
        if (trimmed.isEmpty() || trimmed.startsWith("//")) return null
        if (LINK_SCHEME_REGEX.containsMatchIn(trimmed)) return null
        if (sourceChapterIndex !in chapterPaths.indices) return null

        // 导出器漏 id 的注释正文：用配对时预抽出的文本
        synchronized(zipLock) { footnoteTextByHref[sourceChapterIndex]?.get(trimmed) }
            ?.let { if (it.isNotBlank()) return it }

        val documentHref = trimmed.substringBefore('#').substringBefore('?')
        var targetChapter = if (documentHref.isBlank()) {
            sourceChapterIndex
        } else {
            resolveLinkedChapter(sourceChapterIndex, documentHref)
        }
        if (targetChapter !in chapterPaths.indices) return null

        val fragment = if ('#' in trimmed) decodeUrlComponent(trimmed.substringAfter('#')) else ""
        if (fragment.isEmpty()) return null

        val targetPath = chapterSources.getOrNull(targetChapter)?.path
        targetChapter = findLogicalChapterIndex(canonicalPath(targetPath.orEmpty()), fragment)
            ?: targetChapter

        val rawHtml = try {
            synchronized(zipLock) {
                val zipFile = sessionZipFile ?: return null
                val source = chapterSources.getOrNull(targetChapter) ?: return null
                val zipEntry = findEntry(zipFile, source.path) ?: return null
                zipFile.getInputStream(zipEntry).bufferedReader().use {
                    sliceLogicalChapterHtml(it.readText(), source)
                }
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
    private fun applyParagraphFormatting(
        text: CharSequence,
        chapterTitle: String = ""
    ): CharSequence {
        val density = context?.resources?.displayMetrics?.density ?: 2.75f
        val indentPx = if (firstLineIndentChars > 0f) (firstLineIndentChars * 18f * density).toInt() else 0
        val spacingPx = if (paragraphSpacingDp > 0f) (paragraphSpacingDp * density).toInt() else 0

        // Build paragraph separators once. Repeated insert/delete on a 400K+ Spanned
        // relocates every following character and span thousands of times.
        val ssb = rebuildParagraphBreaks(text, spacingPx)
        if (indentPx <= 0) return ssb

        // ── 首行缩进：给每个段落添加 LeadingMarginSpan ──
        val imageRanges = ssb.getSpans(
            0,
            ssb.length,
            android.text.style.ImageSpan::class.java
        ).map { ssb.getSpanStart(it) until ssb.getSpanEnd(it) }
            .sortedBy { it.first }
        var imageRangeIndex = 0
        var indentCount = 0
        var paraStart = 0
        var firstContentParagraphSeen = false
        val normalizedChapterTitle = normalizeHeadingText(chapterTitle)
        for (j in 0..ssb.length) {
            val isEnd = j == ssb.length
            val isNewline = !isEnd && ssb[j] == '\n'
            if (isEnd || isNewline) {
                if (paraStart < j) {
                    while (imageRangeIndex < imageRanges.size &&
                        imageRanges[imageRangeIndex].last < paraStart
                    ) {
                        imageRangeIndex++
                    }
                    val imageRange = imageRanges.getOrNull(imageRangeIndex)
                    val hasImage = imageRange != null && imageRange.first < j
                    // 标题不参与首行缩进：h1/h2 之类的标题同样以段落形式出现在文本里，
                    // 用 CSS 类排版的标题则靠"首段 == 章节标题"兜底。
                    val isHeading = isHeadingParagraph(ssb, paraStart, j)
                    val isChapterTitle = !firstContentParagraphSeen &&
                        isChapterTitleParagraph(
                            ssb.subSequence(paraStart, j),
                            normalizedChapterTitle
                        )
                    if (!hasImage) firstContentParagraphSeen = true
                    if (!hasImage && !isHeading && !isChapterTitle) {
                        ssb.setSpan(
                            android.text.style.LeadingMarginSpan.Standard(indentPx, 0),
                            paraStart, j,
                            android.text.Spannable.SPAN_INCLUSIVE_INCLUSIVE
                        )
                        indentCount++
                    }
                }
                paraStart = j + 1
            }
        }

        android.util.Log.d(
            "EpubParser",
            "Paragraph formatting complete: length=${ssb.length} indentSpans=$indentCount spacingPx=$spacingPx"
        )

        return ssb
    }

    private fun rebuildParagraphBreaks(
        text: CharSequence,
        spacingPx: Int
    ): SpannableStringBuilder {
        val source = text.toString()
        val startOffsets = IntArray(source.length + 1)
        val endOffsets = IntArray(source.length + 1)
        val spacerOffsets = mutableListOf<Int>()
        val plainText = StringBuilder(source.length)
        var sourceIndex = 0

        while (sourceIndex < source.length) {
            val newline = source.indexOf('\n', sourceIndex).let { if (it < 0) source.length else it }
            val outputStart = plainText.length
            for (boundary in sourceIndex..newline) {
                val mapped = outputStart + boundary - sourceIndex
                startOffsets[boundary] = mapped
                endOffsets[boundary] = mapped
            }
            plainText.append(source, sourceIndex, newline)
            if (newline == source.length) break

            var runEnd = newline + 1
            while (runEnd < source.length && (source[runEnd] == '\n' || source[runEnd] == '\r')) {
                runEnd++
            }
            plainText.append('\n')
            val beforeSpacer = plainText.length
            for (boundary in newline + 1..runEnd) {
                endOffsets[boundary] = beforeSpacer
            }
            if (spacingPx > 0 && runEnd < source.length) {
                spacerOffsets += plainText.length
                plainText.append('\n')
            }
            for (boundary in newline + 1..runEnd) {
                startOffsets[boundary] = plainText.length
            }
            sourceIndex = runEnd
        }

        if (source.isEmpty()) {
            startOffsets[0] = 0
            endOffsets[0] = 0
        }
        val result = SpannableStringBuilder(plainText)
        copyMappedSpans(text, result, startOffsets, endOffsets)
        spacerOffsets.forEach { spacerStart ->
            result.setSpan(
                ParagraphLineHeightSpan(spacingPx),
                spacerStart,
                spacerStart + 1,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
        return result
    }

    /** Copies all source spans once after a linear text transformation. */
    private fun copyMappedSpans(
        source: CharSequence,
        target: SpannableStringBuilder,
        startOffsets: IntArray,
        endOffsets: IntArray
    ) {
        if (source !is Spanned || source.isEmpty()) return
        source.getSpans(0, source.length, Any::class.java).forEach { span ->
            if (span is NoCopySpan) return@forEach
            val sourceStart = source.getSpanStart(span)
            val sourceEnd = source.getSpanEnd(span)
            if (sourceStart !in startOffsets.indices || sourceEnd !in endOffsets.indices) return@forEach
            val targetStart = startOffsets[sourceStart]
            val targetEnd = endOffsets[sourceEnd]
            if (targetStart > targetEnd) return@forEach
            try {
                target.setSpan(span, targetStart, targetEnd, source.getSpanFlags(span))
            } catch (error: RuntimeException) {
                android.util.Log.w(
                    "EpubParser",
                    "Skipping invalid mapped span ${span.javaClass.simpleName}: $targetStart..$targetEnd",
                    error
                )
            }
        }
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

            // 强制覆盖字体度量，精确控制空行高度
            fm.ascent = 0
            fm.top = 0
            fm.descent = extraHeightPx
            fm.bottom = extraHeightPx
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
    /**
     * 章节预处理：还原出版社的背景装饰图、收下发布方的图片尺寸与内联 SVG。
     *
     * 页面在阅读器排版下没有 CSS 布局，背景图原本无处可画；这里把块级元素上命中的
     * background-image 变成一张插在该元素内容前的装饰图，尽量留住原书观感。
     */
    private fun prepareChapterHtml(
        chapterPath: String,
        bodyContent: String,
        zipFile: ZipFile
    ): PreparedChapterHtml {
        val cssIndex = chapterCssIndex(chapterPath, bodyContent, zipFile)
        return ReaderChapterPreparer.prepare(
            rawHtml = bodyContent,
            chapterPath = chapterPath,
            cssIndex = cssIndex,
            includeBackgroundDecorations = preserveEpubBackground,
            resourceExists = { path -> isImageResource(zipFile, path) }
        )
    }

    private fun isImageResource(zipFile: ZipFile, path: String): Boolean {
        if (path.substringAfterLast('.', "").lowercase() !in IMAGE_RESOURCE_EXTENSIONS) return false
        return synchronized(zipLock) {
            if (sessionZipFile !== zipFile) false else findEntry(zipFile, path) != null
        }
    }

    private fun chapterCssIndex(
        chapterPath: String,
        html: String,
        zipFile: ZipFile
    ): EpubCssIndex {
        if (chapterPath.isBlank()) return EpubCssIndex.EMPTY
        val cacheKey = chapterPath.lowercase()
        synchronized(cssIndexCache) { cssIndexCache[cacheKey] }?.let { return it }
        val styleSheets = mutableListOf<Pair<String, String>>()
        styleSheetPaths(chapterPath, html).forEach sheetLoop@{ cssPath ->
            val css = readStyleSheetText(zipFile, cssPath) ?: return@sheetLoop
            // @import 的规则排在导入它的样式表之前（CSS 语义）。
            EpubCssIndex.imports(css).forEach importLoop@{ imported ->
                val importedPath = EpubPathResolver.resolve(cssPath, imported) ?: return@importLoop
                readStyleSheetText(zipFile, importedPath)?.let { styleSheets += importedPath to it }
            }
            styleSheets += cssPath to css
        }
        val index = EpubCssIndex.parse(styleSheets)
        synchronized(cssIndexCache) { cssIndexCache[cacheKey] = index }
        return index
    }

    private fun styleSheetPaths(chapterPath: String, html: String): List<String> {
        if (!html.contains("stylesheet", ignoreCase = true)) return emptyList()
        val allLinkTags = Regex("""<link\b[^>]*>""", RegexOption.IGNORE_CASE)
            .findAll(html)
            .filter { it.value.contains("stylesheet", ignoreCase = true) }
            .mapNotNull { hrefRegex.find(it.value)?.groupValues?.get(1) }
        return allLinkTags
            .filter { href ->
                href.isNotBlank() &&
                    !href.startsWith("data:", ignoreCase = true) &&
                    !href.startsWith("http://", ignoreCase = true) &&
                    !href.startsWith("https://", ignoreCase = true)
            }
            .mapNotNull { href -> EpubPathResolver.resolve(chapterPath, href) }
            .distinct()
            .toList()
    }

    private fun readStyleSheetText(zipFile: ZipFile, cssPath: String): String? {
        val cached = synchronized(zipLock) {
            if (sessionZipFile !== zipFile) null else cssFileCache[cssPath]
        }
        if (cached != null) return cached
        val text = synchronized(zipLock) {
            if (sessionZipFile !== zipFile) return@synchronized null
            val entry = findEntry(zipFile, cssPath) ?: return@synchronized null
            runCatching {
                zipFile.getInputStream(entry).bufferedReader().use { reader -> reader.readText() }
            }.getOrNull()?.removePrefix("\uFEFF")
        } ?: return null
        synchronized(zipLock) {
            if (sessionZipFile === zipFile) cssFileCache[cssPath] = text
        }
        return text
    }

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

    /** Canonical path used when comparing OPF/spine/NCX references. */
    private fun canonicalPath(path: String): String? =
        EpubPathResolver.normalize(decodeUrlComponent(path).replace('\\', '/'))

    /** 根据来源章节目录解析 EPUB 内部相对链接。 */
    private fun resolveLinkedChapter(sourceChapterIndex: Int, documentHref: String): Int {
        val decodedHref = decodeUrlComponent(documentHref).replace('\\', '/')
        val sourcePath = chapterSources[sourceChapterIndex].path.replace('\\', '/')
        val sourceDirectory = sourcePath.substringBeforeLast('/', "")
        val resolvedPath = if (decodedHref.startsWith('/')) {
            decodedHref.trimStart('/')
        } else {
            normalizePath(
                if (sourceDirectory.isEmpty()) decodedHref else "$sourceDirectory/$decodedHref"
            ).trimStart('/')
        }

        val exactPath = canonicalPath(resolvedPath)
        findLogicalChapterIndex(exactPath, anchor = null)?.let { return it }

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

    private data class EncodedImageSize(val width: Int, val height: Int)

    /** Reads dimensions from encoded headers without starting Android's bitmap decoder. */
    private fun readEncodedImageSize(zipFile: ZipFile, entry: ZipEntry): EncodedImageSize? {
        val readLimit = if (entry.name.endsWith(".jpg", true) || entry.name.endsWith(".jpeg", true)) {
            JPEG_HEADER_READ_LIMIT
        } else {
            SIMPLE_IMAGE_HEADER_READ_LIMIT
        }
        val header = synchronized(zipLock) {
            if (sessionZipFile !== zipFile) return null
            zipFile.getInputStream(entry).use { input ->
                val output = ByteArrayOutputStream(4096)
                val buffer = ByteArray(4096)
                var remaining = readLimit
                while (remaining > 0) {
                    val count = input.read(buffer, 0, minOf(buffer.size, remaining))
                    if (count <= 0) break
                    output.write(buffer, 0, count)
                    remaining -= count
                }
                output.toByteArray()
            }
        }
        return parseEncodedImageSize(header)
    }

    private fun parseEncodedImageSize(bytes: ByteArray): EncodedImageSize? {
        if (bytes.size >= 24 &&
            bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() &&
            bytes[2] == 0x4E.toByte() && bytes[3] == 0x47.toByte()
        ) {
            return validImageSize(readBigEndianInt(bytes, 16), readBigEndianInt(bytes, 20))
        }
        if (bytes.size >= 10 &&
            bytes[0] == 'G'.code.toByte() && bytes[1] == 'I'.code.toByte() &&
            bytes[2] == 'F'.code.toByte()
        ) {
            return validImageSize(readLittleEndianShort(bytes, 6), readLittleEndianShort(bytes, 8))
        }
        if (bytes.size >= 12 &&
            bytes[0] == 'R'.code.toByte() && bytes[1] == 'I'.code.toByte() &&
            bytes[2] == 'F'.code.toByte() && bytes[3] == 'F'.code.toByte() &&
            bytes[8] == 'W'.code.toByte() && bytes[9] == 'E'.code.toByte() &&
            bytes[10] == 'B'.code.toByte() && bytes[11] == 'P'.code.toByte()
        ) {
            parseWebpSize(bytes)?.let { return it }
        }
        if (bytes.size >= 4 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte()) {
            parseJpegSize(bytes)?.let { return it }
        }
        return null
    }

    private fun parseJpegSize(bytes: ByteArray): EncodedImageSize? {
        var index = 2
        while (index + 8 < bytes.size) {
            while (index < bytes.size && bytes[index] != 0xFF.toByte()) index++
            while (index < bytes.size && bytes[index] == 0xFF.toByte()) index++
            if (index >= bytes.size) return null
            val marker = bytes[index].toInt() and 0xFF
            index++
            if (marker == 0xD8 || marker == 0xD9 || marker in 0xD0..0xD7 || marker == 0x01) continue
            if (index + 1 >= bytes.size) return null
            val segmentLength = readBigEndianShort(bytes, index)
            if (segmentLength < 2 || index + segmentLength > bytes.size) return null
            if (marker in JPEG_START_OF_FRAME_MARKERS && segmentLength >= 7) {
                return validImageSize(
                    readBigEndianShort(bytes, index + 5),
                    readBigEndianShort(bytes, index + 3)
                )
            }
            index += segmentLength
        }
        return null
    }

    private fun parseWebpSize(bytes: ByteArray): EncodedImageSize? {
        if (bytes.size < 30) return null
        val chunk = String(bytes, 12, 4, Charsets.US_ASCII)
        return when (chunk) {
            "VP8X" -> validImageSize(
                1 + readLittleEndian24(bytes, 24),
                1 + readLittleEndian24(bytes, 27)
            )
            "VP8 " -> {
                var index = 20
                while (index + 6 < bytes.size) {
                    if (bytes[index] == 0x9D.toByte() && bytes[index + 1] == 0x01.toByte() &&
                        bytes[index + 2] == 0x2A.toByte()
                    ) {
                        return validImageSize(
                            readLittleEndianShort(bytes, index + 3) and 0x3FFF,
                            readLittleEndianShort(bytes, index + 5) and 0x3FFF
                        )
                    }
                    index++
                }
                null
            }
            "VP8L" -> {
                if (bytes[20] != 0x2F.toByte()) return null
                val packed = (bytes[21].toInt() and 0xFF) or
                    ((bytes[22].toInt() and 0xFF) shl 8) or
                    ((bytes[23].toInt() and 0xFF) shl 16) or
                    ((bytes[24].toInt() and 0xFF) shl 24)
                validImageSize((packed and 0x3FFF) + 1, ((packed ushr 14) and 0x3FFF) + 1)
            }
            else -> null
        }
    }

    private fun validImageSize(width: Int, height: Int): EncodedImageSize? =
        if (width > 0 && height > 0) EncodedImageSize(width, height) else null

    /**
     * 矢量图的固有尺寸：优先 viewBox（它决定画面比例），其次声明的 width/height。
     *
     * 许多封面写 `<svg width="100%" viewBox="0 0 1000 1333">`，按声明宽度算会退化成正方形。
     */
    private fun readVectorIntrinsicSize(markup: ByteArray): EncodedImageSize? {
        val svg = runCatching {
            com.caverock.androidsvg.SVG.getFromInputStream(java.io.ByteArrayInputStream(markup))
        }.getOrNull() ?: return null
        val viewBox = runCatching { svg.documentViewBox }.getOrNull()
        if (viewBox != null && viewBox.width() > 0f && viewBox.height() > 0f) {
            validImageSize(viewBox.width().roundToInt(), viewBox.height().roundToInt())?.let { return it }
        }
        val width = runCatching { svg.documentWidth }.getOrDefault(0f)
        val height = runCatching { svg.documentHeight }.getOrDefault(0f)
        if (!width.isFinite() || !height.isFinite() || width <= 0f || height <= 0f) return null
        return validImageSize(width.roundToInt(), height.roundToInt())
    }

    private fun readBigEndianInt(bytes: ByteArray, offset: Int): Int =
        ((bytes[offset].toInt() and 0xFF) shl 24) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 8) or
            (bytes[offset + 3].toInt() and 0xFF)

    private fun readBigEndianShort(bytes: ByteArray, offset: Int): Int =
        ((bytes[offset].toInt() and 0xFF) shl 8) or (bytes[offset + 1].toInt() and 0xFF)

    private fun readLittleEndianShort(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xFF) or ((bytes[offset + 1].toInt() and 0xFF) shl 8)

    private fun readLittleEndian24(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xFF) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 16)

    private fun htmlToSpanned(chapterIndex: Int, html: String, zipFile: ZipFile): Spanned {
        val bodyContent = extractBody(html) ?: html

        // 出版社用 CSS/内联背景图做的装饰在阅读器排版里没有渲染路径，先把它还原成图片；
        // 同时收下发布方给的图片尺寸（width="12%" 这类）与内联 SVG，交给 ImageSpan 处理。
        val chapterPath = chapterSources.getOrNull(chapterIndex)?.path.orEmpty()
        val prepared = prepareChapterHtml(chapterPath, bodyContent, zipFile)

        val cleaned = prepared.html
            .replace(Regex("""<script[^>]*>.*?</script>""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)), "")
            .replace(Regex("""<style[^>]*>.*?</style>""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)), "")
            // 兜底：预处理没能转换的矢量内容（例如 jsoup 解析失败）按旧行为丢弃，
            // 避免整段 SVG 标记被 Html.fromHtml 当正文吐成乱码。
            .replace(Regex("""<svg[^>]*>.*?</svg>""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)), "")
            // 清理空容器（SVG 处理完留下的空 div 等）
            .replace(Regex("""<div[^>]*>\s*</div>""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)), "")
            .replace(Regex("""<span[^>]*>\s*</span>""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)), "")
            .replace(Regex("""\n{3,}"""), "\n\n")

        // 注释引用与正文先配对：导出器常把 href 指错、正文漏 id，配对后 href 才能稳定解析。
        val alignment = alignFootnoteReferences(cleaned, "lumi-footnote-$chapterIndex-")
        synchronized(zipLock) {
            if (alignment.textByHref.isEmpty()) footnoteTextByHref.remove(chapterIndex)
            else footnoteTextByHref[chapterIndex] = alignment.textByHref
        }

        // 收集注释引用链接（Canvas 引擎注释气泡判定用，用重写后的 href）
        collectFootnoteHrefs(chapterIndex, alignment.html)

        // 注释正文改由注释气泡呈现：只移除本章确实被引用到的注释容器，正文流里不再重复出现。
        val withoutFootnoteBodies = stripReferencedFootnoteBodies(chapterIndex, alignment.html)

        // 注释引用图标（注释链接里的 <img>）是行内标记，必须保持行内。
        val (inlineProtected, inlineImages) = protectInlineFootnoteImages(withoutFootnoteBodies)

        // Html.fromHtml 会丢弃 id/name。先插入不可见占位，转成 Spanned 后再移除并记录偏移。
        val withAnchorMarkers = insertAnchorMarkers(inlineProtected)

        // 图片前后插入换行，使其独占一行（块级效果）
        var withImageBreaks = IMAGE_TAG_REGEX.replace(withAnchorMarkers, "\n$0\n")
        inlineImages.forEachIndexed { index, image ->
            withImageBreaks = withImageBreaks.replace(inlineImagePlaceholder(index), image)
        }

        val inlineMarkerSources = inlineImages
            .mapNotNull { image -> tagAttribute(image, "src")?.trim()?.lowercase() }
            .toSet()
        val imageGetter = EpubImageGetter(
            zipFile = zipFile,
            pageContentWidth = contentWidth,
            inlineMarkerSources = inlineMarkerSources,
            sizeHints = prepared.sizeHints,
            inlineSvgSources = prepared.inlineSvgSources
        )
        val parsed = parseNativeHtmlInChunks(withImageBreaks, imageGetter)
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

    /**
     * Canvas 引擎用注释气泡呈现注释正文，正文流里不该再重复出现同一段注释。
     *
     * 只移除本章确实检测到引用链接、且 id/name 与引用 fragment 对应的容器，
     * 避免误删没有引用标记的注释段落（例如《毛泽东选集》式的正文注释）。
     */
    private fun stripReferencedFootnoteBodies(chapterIndex: Int, html: String): String {
        val hrefs = synchronized(zipLock) { footnoteHrefs[chapterIndex] } ?: return html
        val fragments = hrefs.mapNotNull { href ->
            decodeTagEntities(href)
                .substringAfter('#', "")
                .takeIf { it.isNotBlank() }
                ?.let(::decodeUrlComponent)
        }.toSet()
        return stripFootnoteBodies(html, fragments)
    }

    /**
     * Android's HTML parser becomes disproportionately expensive for the very
     * large single-file chapters produced by some EPUB exporters. Split only
     * at block boundaries so inline markup and image/anchor spans remain
     * intact while each parser invocation stays bounded.
     */
    private fun parseNativeHtmlInChunks(
        html: String,
        imageGetter: Html.ImageGetter
    ): Spanned {
        val chunks = splitNativeHtml(html)
        if (chunks.size == 1) {
            return replaceDefaultQuoteSpans(
                Html.fromHtml(chunks[0], Html.FROM_HTML_MODE_LEGACY, imageGetter, null)
            )
        }

        val combined = android.text.SpannableStringBuilder()
        chunks.forEachIndexed { index, chunk ->
            val parsed = Html.fromHtml(chunk, Html.FROM_HTML_MODE_LEGACY, imageGetter, null)
            if (index > 0 && combined.isNotEmpty() && !combined.endsWith('\n') &&
                parsed.isNotEmpty() && parsed[0] != '\n'
            ) {
                combined.append('\n')
            }
            combined.append(parsed)
        }
        return replaceDefaultQuoteSpans(combined)
    }

    /**
     * Html.fromHtml renders every blockquote with Android's fixed blue QuoteSpan stripe.
     * Keep its leading margin, but replace the system-colored decoration so EPUB quotes
     * remain readable in every reader theme without an unrelated blue/purple accent.
     */
    private fun replaceDefaultQuoteSpans(text: Spanned): Spanned {
        val quoteSpans = text.getSpans(0, text.length, QuoteSpan::class.java)
        if (quoteSpans.isEmpty()) return text

        val result = SpannableStringBuilder(text)
        quoteSpans.forEach { quoteSpan ->
            val start = result.getSpanStart(quoteSpan)
            val end = result.getSpanEnd(quoteSpan)
            val flags = result.getSpanFlags(quoteSpan)
            val margin = quoteSpan.getLeadingMargin(true).coerceAtLeast(0)
            result.removeSpan(quoteSpan)
            if (start in 0 until end && end <= result.length && margin > 0) {
                result.setSpan(
                    LeadingMarginSpan.Standard(margin, margin),
                    start,
                    end,
                    flags
                )
            }
        }
        return result
    }

    private fun splitNativeHtml(html: String): List<String> {
        if (html.length <= NATIVE_HTML_CHUNK_THRESHOLD) return listOf(html)

        val chunks = mutableListOf<String>()
        var start = 0
        while (start < html.length) {
            val target = (start + NATIVE_HTML_CHUNK_TARGET).coerceAtMost(html.length)
            if (target == html.length) {
                chunks += html.substring(start)
                break
            }

            val boundary = findNativeHtmlBoundary(html, target)
            chunks += html.substring(start, boundary)
            start = boundary
        }
        return chunks.filter(String::isNotEmpty)
    }

    private fun findNativeHtmlBoundary(html: String, target: Int): Int {
        val boundary = NATIVE_HTML_BOUNDARY_REGEX.find(html, target)?.range?.last?.plus(1)
        // Never split inside a paragraph or tag. A single unusually large
        // block remains one chunk, which preserves its spans and markup.
        return boundary ?: html.length
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

    private fun extractAnchorOffsets(text: CharSequence): AnchoredChapter {
        val source = text.toString()
        val startOffsets = IntArray(source.length + 1)
        val endOffsets = IntArray(source.length + 1)
        val plainText = StringBuilder(source.length)
        val offsets = linkedMapOf<String, Int>()
        var searchFrom = 0
        var copyFrom = 0

        while (searchFrom < source.length) {
            val markerStart = source.indexOf(ANCHOR_MARKER_PREFIX, searchFrom)
            if (markerStart < 0) break
            val payloadStart = markerStart + ANCHOR_MARKER_PREFIX.length
            val markerEnd = source.indexOf(ANCHOR_MARKER_SUFFIX, payloadStart)
            if (markerEnd < 0) break

            val outputStart = plainText.length
            for (boundary in copyFrom..markerStart) {
                val mapped = outputStart + boundary - copyFrom
                startOffsets[boundary] = mapped
                endOffsets[boundary] = mapped
            }
            plainText.append(source, copyFrom, markerStart)
            val anchorOffset = plainText.length
            val encoded = source.substring(payloadStart, markerEnd)
            val anchor = try {
                String(Base64.decode(encoded, Base64.NO_WRAP or Base64.URL_SAFE), Charsets.UTF_8)
            } catch (_: IllegalArgumentException) {
                ""
            }
            if (anchor.isNotEmpty()) {
                val decodedAnchor = decodeUrlComponent(anchor)
                offsets.putIfAbsent(anchor, anchorOffset)
                offsets.putIfAbsent(decodedAnchor, anchorOffset)
                offsets.putIfAbsent(decodedAnchor.lowercase(), anchorOffset)
            }

            copyFrom = markerEnd + ANCHOR_MARKER_SUFFIX.length
            for (boundary in markerStart..copyFrom) {
                startOffsets[boundary] = anchorOffset
                endOffsets[boundary] = anchorOffset
            }
            searchFrom = copyFrom
        }
        val outputStart = plainText.length
        for (boundary in copyFrom..source.length) {
            val mapped = outputStart + boundary - copyFrom
            startOffsets[boundary] = mapped
            endOffsets[boundary] = mapped
        }
        plainText.append(source, copyFrom, source.length)

        val result = SpannableStringBuilder(plainText)
        copyMappedSpans(text, result, startOffsets, endOffsets)
        return AnchoredChapter(result, offsets)
    }

    /**
     * Drawable used by ImageSpan. It keeps only intrinsic dimensions while a
     * chapter is being parsed; bitmap bytes are read and decoded on the first
     * actual draw, then retained only by the bounded parser cache.
     */
    private inner class LazyEpubImageDrawable(
        private val zipFile: ZipFile,
        private val entry: ZipEntry?,
        private val cacheKey: String,
        private val originalWidth: Int,
        private val originalHeight: Int,
        private val pageWidth: Int,
        private val drawWidth: Int,
        private val drawHeight: Int,
        override val isInlineFootnoteMarker: Boolean = false,
        /** 矢量图源码：非空时按 [drawWidth] × [drawHeight] 光栅化，而不是解码位图。 */
        private val vectorSource: ByteArray? = null
    ) : Drawable(), InlineFootnoteMarkerDrawable {
        private val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG or android.graphics.Paint.FILTER_BITMAP_FLAG)
        private var bitmapRef: WeakReference<Bitmap>? = null
        private var decodeFailed = false

        override fun draw(canvas: android.graphics.Canvas) {
            val bitmap = bitmapRef?.get()
                ?: imageBitmapCache[cacheKey]?.also { bitmapRef = WeakReference(it) }
                ?: loadBitmap()?.also {
                    bitmapRef = WeakReference(it)
                    if (it.allocationByteCount.toLong() <= IMAGE_BITMAP_CACHE_BYTES) {
                        imageBitmapCache.put(cacheKey, it)
                    }
                }
            if (bitmap != null && !bitmap.isRecycled) {
                canvas.drawBitmap(bitmap, null, bounds, paint)
            } else {
                drawFailurePlaceholder(canvas)
            }
        }

        private fun loadBitmap(): Bitmap? {
            if (decodeFailed) return null
            vectorSource?.let { markup ->
                return try {
                    renderVectorBitmap(markup, drawWidth, drawHeight)
                } catch (error: Throwable) {
                    decodeFailed = true
                    android.util.Log.w("EpubParser", "Vector render failed: $cacheKey", error)
                    null
                }
            }
            val sourceEntry = entry ?: return null
            return try {
                synchronized(zipLock) {
                    val opts = BitmapFactory.Options().apply {
                        inSampleSize = ReaderImageSizing.decodeSampleSize(
                            originalWidth,
                            originalHeight,
                            pageWidth
                        )
                        inPreferredConfig = Bitmap.Config.ARGB_8888
                    }
                    zipFile.getInputStream(sourceEntry).use { input ->
                        BitmapFactory.decodeStream(input, null, opts)
                    }
                }
            } catch (error: Throwable) {
                decodeFailed = true
                android.util.Log.w("EpubParser", "Lazy image decode failed: ${sourceEntry.name}", error)
                null
            }
        }

        private fun renderVectorBitmap(markup: ByteArray, width: Int, height: Int): Bitmap? {
            if (width <= 0 || height <= 0) return null
            // 超高矢量图（整页横幅）按上限降采样光栅化：绘制时仍按排版尺寸铺开，
            // 只是像素少一点，避免一次分配几十 MB。
            val scale = minOf(
                1f,
                MAX_VECTOR_BITMAP_WIDTH.toFloat() / width,
                MAX_VECTOR_BITMAP_HEIGHT.toFloat() / height
            )
            val bitmapWidth = (width * scale).toInt().coerceAtLeast(1)
            val bitmapHeight = (height * scale).toInt().coerceAtLeast(1)
            val svg = com.caverock.androidsvg.SVG.getFromInputStream(
                java.io.ByteArrayInputStream(markup)
            )
            svg.setDocumentWidth(bitmapWidth.toFloat())
            svg.setDocumentHeight(bitmapHeight.toFloat())
            val bitmap = Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888)
            svg.renderToCanvas(android.graphics.Canvas(bitmap))
            return bitmap
        }

        private fun drawFailurePlaceholder(canvas: android.graphics.Canvas) {
            val fill = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFFF5F5F5.toInt()
                style = android.graphics.Paint.Style.FILL
            }
            canvas.drawRect(bounds, fill)
            val border = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFFCCCCCC.toInt()
                style = android.graphics.Paint.Style.STROKE
                strokeWidth = 2f
            }
            canvas.drawRect(
                bounds.left + 1f,
                bounds.top + 1f,
                bounds.right - 1f,
                bounds.bottom - 1f,
                border
            )
        }

        override fun setAlpha(alpha: Int) {
            paint.alpha = alpha
        }

        override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) {
            paint.colorFilter = colorFilter
        }

        @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
        override fun getOpacity(): Int = android.graphics.PixelFormat.TRANSLUCENT

        override fun getIntrinsicWidth(): Int = drawWidth

        override fun getIntrinsicHeight(): Int = drawHeight
    }

    private inner class EpubImageGetter(
        private val zipFile: ZipFile,
        private val pageContentWidth: Int = 0,
        /** 本章节里作为注释引用标记的行内小图（src 原样、小写） */
        private val inlineMarkerSources: Set<String> = emptySet(),
        /** 发布方给的图片尺寸（`width="12%"` 这类），key = src 原样 */
        private val sizeHints: Map<String, ReaderImageSizeHint> = emptyMap(),
        /** 章节里的内联矢量图，key = 虚拟 src（`lumi-inline-svg-N`） */
        private val inlineSvgSources: Map<String, ByteArray> = emptyMap()
    ) : Html.ImageGetter {
        override fun getDrawable(source: String): Drawable? {
            return try {
                // data: 图片只在正文确实包含内嵌资源时出现；保留已有解码路径。
                if (source.startsWith("data:", ignoreCase = true)) {
                    return decodeDataUri(source)
                }
                // 内联矢量图：由 ReaderChapterPreparer 换成虚拟 src，光栅化后按图片排版。
                inlineSvgSources[source]?.let { markup ->
                    return svgDrawable(source, markup, pageContentWidth)
                        ?: createSvgPlaceholder()
                }

                val entryPath = resolveImagePath(source)
                val entry = findEntry(zipFile, entryPath) ?: run {
                    android.util.Log.w("EpubParser", "getDrawable: findEntry returned null for $entryPath")
                    return createErrorPlaceholder("Image not found: ${entryPath.take(60)}")
                }
                val density = (context?.resources?.displayMetrics
                    ?: android.content.res.Resources.getSystem().displayMetrics).density
                val isInlineMarker = source.trim().lowercase() in inlineMarkerSources
                val isVector = entry.name.endsWith(".svg", ignoreCase = true)
                val vectorMarkup = if (isVector) readEntryBytes(entry) else null
                val encodedSize = if (isVector) {
                    vectorMarkup?.let(::readVectorIntrinsicSize)
                } else {
                    readEncodedImageSize(zipFile, entry) ?: run {
                        val bounds = synchronized(zipLock) {
                            if (sessionZipFile !== zipFile) return createErrorPlaceholder("Reader closed")
                            BitmapFactory.Options().apply { inJustDecodeBounds = true }.also { options ->
                                zipFile.getInputStream(entry).use { input ->
                                    BitmapFactory.decodeStream(input, null, options)
                                }
                            }
                        }
                        validImageSize(bounds.outWidth, bounds.outHeight)
                    }
                } ?: return if (isVector) {
                    createSvgPlaceholder()
                } else {
                    createErrorPlaceholder("Invalid image dimensions: ${entry.name.take(60)}")
                }
                val originalWidth = encodedSize.width
                val originalHeight = encodedSize.height
                val pageW = pageContentWidth.takeIf { it > 0 } ?: run {
                    val dm = context?.resources?.displayMetrics
                        ?: android.content.res.Resources.getSystem().displayMetrics
                    val marginPx = (44 * dm.density).toInt()
                    (dm.widthPixels - marginPx * 2).coerceAtLeast(1)
                }
                // 注释引用图标按正文字号留出排版槽位，而不是按图片原始像素（多为 72px 大图）
                val targetWidth = if (isInlineMarker) {
                    ReaderImageSizing.inlineMarkerSizePx(density)
                } else {
                    // 出版社给了尺寸（width="12%"、style="width:60px"）就按它排，
                    // 装饰小图不再被拉满正文列宽。
                    sizeHints[source]?.resolveWidthPx(pageW, density) ?: pageW
                }
                val imageBounds = ReaderImageSizing.bounds(originalWidth, originalHeight, targetWidth)
                    ?: return createErrorPlaceholder("Invalid image dimensions: ${entry.name.take(60)}")

                LazyEpubImageDrawable(
                    zipFile = zipFile,
                    entry = entry,
                    cacheKey = entry.name,
                    originalWidth = originalWidth,
                    originalHeight = originalHeight,
                    pageWidth = pageW,
                    drawWidth = imageBounds.width,
                    drawHeight = imageBounds.height,
                    isInlineFootnoteMarker = isInlineMarker,
                    vectorSource = vectorMarkup
                ).apply {
                    setBounds(0, 0, imageBounds.width, imageBounds.height)
                }
            } catch (e: Throwable) {
                android.util.Log.e("EpubParser", "getDrawable: exception", e)
                createErrorPlaceholder("Exception: ${e.message?.take(60) ?: "unknown"}")
            }
        }

        /** 内联/独立矢量图：按目标宽度等比光栅化，失败才回落到占位框。 */
        private fun svgDrawable(source: String, markup: ByteArray, contentWidth: Int): Drawable? {
            val intrinsic = readVectorIntrinsicSize(markup) ?: return null
            val pageW = contentWidth.takeIf { it > 0 } ?: run {
                val dm = context?.resources?.displayMetrics
                    ?: android.content.res.Resources.getSystem().displayMetrics
                (dm.widthPixels - (88 * dm.density).toInt()).coerceAtLeast(1)
            }
            val density = (context?.resources?.displayMetrics
                ?: android.content.res.Resources.getSystem().displayMetrics).density
            val targetWidth = sizeHints[source]?.resolveWidthPx(pageW, density) ?: pageW
            val bounds = ReaderImageSizing.bounds(intrinsic.width, intrinsic.height, targetWidth)
                ?: return null
            return LazyEpubImageDrawable(
                zipFile = zipFile,
                entry = null,
                cacheKey = "vector:$source:${bounds.width}x${bounds.height}",
                originalWidth = intrinsic.width,
                originalHeight = intrinsic.height,
                pageWidth = pageW,
                drawWidth = bounds.width,
                drawHeight = bounds.height,
                vectorSource = markup
            ).apply {
                setBounds(0, 0, bounds.width, bounds.height)
            }
        }

        private fun readEntryBytes(entry: ZipEntry): ByteArray? = synchronized(zipLock) {
            if (sessionZipFile !== zipFile) return@synchronized null
            runCatching {
                zipFile.getInputStream(entry).use { input ->
                    val output = java.io.ByteArrayOutputStream()
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var total = 0
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        if (total > MAX_IMAGE_RESOURCE_BYTES) return@synchronized null
                        output.write(buffer, 0, count)
                    }
                    output.toByteArray()
                }
            }.getOrNull()
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
                val metadata = dataUri.substring(5, commaIdx)
                val isBase64 = metadata.split(';').any { it.equals("base64", ignoreCase = true) }
                val bytes = if (isBase64) {
                    Base64.decode(base64Part, Base64.DEFAULT)
                } else {
                    android.net.Uri.decode(base64Part).toByteArray(Charsets.UTF_8)
                }
                if (bytes.isEmpty()) return null

                // 内联矢量图（data:image/svg+xml;base64,...）走同一条矢量光栅化路径。
                if (metadata.contains("svg", ignoreCase = true)) {
                    return svgDrawable(dataUri, bytes, pageContentWidth)
                }

                // 先读尺寸
                val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)

                val originalWidth = opts.outWidth
                val originalHeight = opts.outHeight
                opts.inSampleSize = ReaderImageSizing.decodeSampleSize(
                    originalWidth,
                    originalHeight,
                    pageContentWidth.takeIf { it > 0 } ?: 800
                )
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
                val imageBounds = ReaderImageSizing.bounds(originalWidth, originalHeight, pageW)
                    ?: return null
                val ratio = imageBounds.width.toFloat() / bitmap.width.coerceAtLeast(1)
                val drawW = imageBounds.width
                val drawH = (bitmap.height * ratio).toInt().coerceAtLeast(1)
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
        chapterSources = emptyList()
        chapterPaths = emptyList()
        logicalChapterIndicesByPath = emptyMap()
        spineHrefs = emptyList()
        sourceLease?.close()
        sourceLease = null
    }
}
