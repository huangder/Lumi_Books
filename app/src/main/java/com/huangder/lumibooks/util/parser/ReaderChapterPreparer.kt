package com.huangder.lumibooks.util.parser

import com.huangder.lumibooks.util.epub.EpubCssIndex
import com.huangder.lumibooks.util.epub.EpubPathResolver
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

/**
 * 发布方给的图片尺寸（`width="12%"`、`style="width:60px"`、CSS `width` 规则）。
 *
 * 阅读器排版以前一律把插图排成正文列宽，装饰小图（章节分隔符、注记图标）会被放大到整页宽。
 */
internal data class ReaderImageSizeHint(val width: String) {
    /**
     * 换算成排版目标宽度；`em` 依赖正文字号，解析器拿不到，返回 null 走默认策略。
     */
    fun resolveWidthPx(contentWidth: Int, density: Float): Int? {
        val value = width.trim().lowercase()
        if (value.isEmpty()) return null
        val number = NUMBER_REGEX.find(value)?.value?.toFloatOrNull() ?: return null
        if (number <= 0f) return null
        return when {
            value.endsWith("%") -> (contentWidth * number / 100f).toInt()
            value.endsWith("px") -> (number * density).toInt()
            value.endsWith("em") || value.endsWith("rem") -> null
            value.endsWith("vh") || value.endsWith("vw") -> null
            // 无单位（HTML width 属性）按 CSS px 处理。
            else -> (number * density).toInt()
        }?.takeIf { it > 0 }
    }

    companion object {
        private val NUMBER_REGEX = Regex("""[0-9]+(\.[0-9]+)?""")
    }
}

/** 章节预处理结果：注入装饰图之后的 HTML，以及图片尺寸/内联矢量图查询表。 */
internal data class PreparedChapterHtml(
    val html: String,
    val sizeHints: Map<String, ReaderImageSizeHint>,
    val inlineSvgSources: Map<String, ByteArray>,
    val changed: Boolean
)

/**
 * 阅读器排版的章节预处理：还原出版社用 CSS 背景图做的装饰，并保留发布方的图片尺寸意图。
 *
 * 纯 JVM 实现（jsoup + 路径解析），方便单测覆盖；资源是否存在的判断交给调用方，
 * 避免把 ZipFile 耦合进来。
 */
internal object ReaderChapterPreparer {
    private const val INLINE_SVG_PREFIX = "lumi-inline-svg-"
    private const val DECORATION_ATTR = "data-lumi-decoration"
    private const val MAX_DECORATIONS_PER_CHAPTER = 40
    private val BLOCK_TAGS = setOf(
        "body", "div", "section", "article", "aside", "header", "footer", "figure",
        "figcaption", "p", "td", "th", "li", "blockquote", "dd", "dt",
        "h1", "h2", "h3", "h4", "h5", "h6"
    )
    private val MEDIA_TAGS = setOf("img", "svg", "video", "canvas", "picture", "iframe")
    private val RASTER_EXTENSIONS = setOf("jpg", "jpeg", "png", "gif", "webp", "avif", "bmp")
    private val BACKGROUND_IMAGE_EXTENSIONS = RASTER_EXTENSIONS + setOf("svg")

    /**
     * @param resourceExists 判断解析出的 zip 路径是否真的是一个可显示的图片资源。
     */
    fun prepare(
        rawHtml: String,
        chapterPath: String,
        cssIndex: EpubCssIndex,
        includeBackgroundDecorations: Boolean,
        resourceExists: (String) -> Boolean
    ): PreparedChapterHtml {
        val needsSvgRewrite = rawHtml.contains("<svg", ignoreCase = true)
        // 只在该书样式表真的声明了背景图/宽度时才为此解析章节，普通小说不付这份开销。
        val needsDecorations = includeBackgroundDecorations &&
            (cssIndex.hasBackgroundImageRules || rawHtml.contains("background", ignoreCase = true))
        // HTML 的 width 属性也要收（`<img width="12%">`），因此章节里有图片就当有尺寸可还原。
        val needsSizeHints = cssIndex.hasWidthRules || rawHtml.contains("<img", ignoreCase = true)
        if (!needsSvgRewrite && !needsDecorations && !needsSizeHints) {
            return PreparedChapterHtml(rawHtml, emptyMap(), emptyMap(), false)
        }

        val document = runCatching { Jsoup.parse(rawHtml) }.getOrNull()
            ?: return PreparedChapterHtml(rawHtml, emptyMap(), emptyMap(), false)
        val body = document.body() ?: return PreparedChapterHtml(rawHtml, emptyMap(), emptyMap(), false)

        val sizeHints = mutableMapOf<String, ReaderImageSizeHint>()
        val inlineSvgs = mutableMapOf<String, ByteArray>()
        if (needsDecorations) injectBackgroundDecorations(body, cssIndex, chapterPath, resourceExists)
        if (needsSvgRewrite) rewriteVectorMedia(body, inlineSvgs, sizeHints)
        if (needsSizeHints || needsDecorations) collectElementSizeHints(body, cssIndex, sizeHints)

        return PreparedChapterHtml(
            html = body.html(),
            sizeHints = sizeHints,
            inlineSvgSources = inlineSvgs,
            changed = true
        )
    }

    private fun injectBackgroundDecorations(
        body: Element,
        cssIndex: EpubCssIndex,
        chapterPath: String,
        resourceExists: (String) -> Boolean
    ) {
        var injected = 0
        body.allElements.forEach { element ->
            if (injected >= MAX_DECORATIONS_PER_CHAPTER) return
            val tag = element.tagName().lowercase()
            if (tag !in BLOCK_TAGS || tag in MEDIA_TAGS) return@forEach
            if (element.hasAttr(DECORATION_ATTR)) return@forEach
            val background = cssIndex.background(element)
            val inlineBackground = EpubCssIndex.firstUrl(elementInlineBackground(element))
            val reference = background?.url ?: inlineBackground ?: return@forEach
            // 内联样式的 url 相对章节解析，样式表里的相对样式表本身解析。
            val basePath = if (background != null) background.sourcePath else chapterPath
            val extension = reference.substringAfterLast('.', "").lowercase()
            if (extension !in BACKGROUND_IMAGE_EXTENSIONS) return@forEach
            val path = EpubPathResolver.resolve(basePath, reference) ?: return@forEach
            if (!resourceExists(path)) return@forEach
            val decoration = element.ownerDocument()!!.createElement("img")
            decoration.attr("src", "/$path")
            decoration.attr(DECORATION_ATTR, "true")
            decoration.attr("alt", "")
            background?.size?.let { size -> decoration.attr("width", backgroundWidthValue(size)) }
            element.prependChild(decoration)
            injected++
        }
    }

    /**
     * `background-size` 只取宽度维度：阅读器排版里装饰图没有绝对定位容器，
     * 高度交给图片比例，避免把装饰压扁。
     */
    private fun backgroundWidthValue(size: String): String {
        val first = size.trim().split(Regex("\\s+")).firstOrNull().orEmpty()
        return when {
            first.equals("cover", ignoreCase = true) ||
                first.equals("contain", ignoreCase = true) ||
                first.equals("auto", ignoreCase = true) ||
                first.isEmpty() -> "100%"
            first.startsWith("calc", ignoreCase = true) -> "100%"
            else -> first
        }
    }

    /** 把内联 `<svg>` 换成图片：含位图 href 的直接用原图，纯矢量转成待光栅化的虚拟资源。 */
    private fun rewriteVectorMedia(
        body: Element,
        inlineSvgs: MutableMap<String, ByteArray>,
        sizeHints: MutableMap<String, ReaderImageSizeHint>
    ) {
        val vectors = body.select("svg").filter { svg ->
            svg.parents().none { parent -> parent.tagName().equals("svg", ignoreCase = true) }
        }
        vectors.forEach { svg ->
            val reference = svg.selectFirst("image")?.let { image ->
                listOf("xlink:href", "href").firstNotNullOfOrNull { attribute ->
                    image.attr(attribute).takeIf(String::isNotBlank)
                }
            }
            val rasterReference = reference
                ?.substringBefore('#')
                ?.takeIf { it.substringAfterLast('.', "").lowercase() in RASTER_EXTENSIONS }
            if (rasterReference != null) {
                val replacement = svg.ownerDocument()!!.createElement("img")
                replacement.attr("src", rasterReference)
                declaredWidth(svg)?.let { replacement.attr("width", it) }
                svg.replaceWith(replacement)
                return@forEach
            }
            val key = INLINE_SVG_PREFIX + inlineSvgs.size
            inlineSvgs[key] = svg.outerHtml().toByteArray(Charsets.UTF_8)
            declaredWidth(svg)?.let { sizeHints[key] = ReaderImageSizeHint(it) }
            val replacement = svg.ownerDocument()!!.createElement("img")
            replacement.attr("src", key)
            replacement.attr("alt", "")
            svg.replaceWith(replacement)
        }
    }

    private fun declaredWidth(element: Element): String? =
        listOfNotNull(
            element.attr("width").takeIf(String::isNotBlank),
            elementInlineWidth(element)
        ).firstOrNull { value -> LENGTH_REGEX.containsMatchIn(value) }

    private fun collectElementSizeHints(
        body: Element,
        cssIndex: EpubCssIndex,
        sizeHints: MutableMap<String, ReaderImageSizeHint>
    ) {
        body.select("img").forEach { image ->
            val source = image.attr("src")
            if (source.isBlank() || sizeHints.containsKey(source)) return@forEach
            val width = listOfNotNull(
                image.attr("width").takeIf(String::isNotBlank),
                elementInlineWidth(image),
                if (cssIndex.isEmpty) null else cssIndex.widthDeclaration(image)
            ).firstOrNull { value -> LENGTH_REGEX.containsMatchIn(value) }
            width?.let { sizeHints[source] = ReaderImageSizeHint(it) }
        }
    }

    private fun elementInlineWidth(element: Element): String? =
        element.attr("style")
            .split(';')
            .firstOrNull { it.substringBefore(':').trim().equals("width", ignoreCase = true) }
            ?.substringAfter(':', "")
            ?.takeIf(String::isNotBlank)

    private fun elementInlineBackground(element: Element): String? =
        element.attr("style")
            .split(';')
            .firstOrNull { declaration ->
                val name = declaration.substringBefore(':').trim()
                name.equals("background-image", ignoreCase = true) ||
                    name.equals("background", ignoreCase = true)
            }
            ?.substringAfter(':', "")
            ?.takeIf(String::isNotBlank)

    private val LENGTH_REGEX = Regex("""[0-9]+(\.[0-9]+)?\s*(%|px|em|rem|vw|vh)?""", RegexOption.IGNORE_CASE)
}
