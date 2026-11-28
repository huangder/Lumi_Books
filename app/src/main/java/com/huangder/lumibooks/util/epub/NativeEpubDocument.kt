package com.huangder.lumibooks.util.epub

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import java.util.IdentityHashMap

internal enum class ChapterRenderEngine { NATIVE, WEBVIEW }
internal data class ChapterRenderDecision(val engine: ChapterRenderEngine, val reason: String? = null)
internal data class NativeEpubSource(val href: String, val resource: EpubResource,
    val stylesheets: List<Pair<String, String>>, val layout: EpubRenditionLayout,
    val unavailableReason: String? = null,
    val duokanPage: EpubDuokanPage? = null)
internal data class NativeEpubFontFace(
    val family: String,
    val resource: EpubResource,
    val weight: Int,
    val italic: Boolean
)

internal data class NativeCssStyle(
    val size: Float = 16f, val weight: Int = 400, val italic: Boolean = false,
    val family: String = "serif", val color: String? = null, val background: String? = null,
    val lineHeight: Float = 1.5f, val letterSpacing: Float = 0f,
    val align: String = "start", val direction: String = "ltr", val vertical: Boolean = false,
    val indent: Float = 0f, val underline: Boolean = false,
    val marginTop: Float = 0f, val marginBottom: Float = 0f,
    val marginLeft: Float = 0f, val marginRight: Float = 0f,
    val pageBreakBefore: Boolean = false, val pageBreakAfter: Boolean = false,
    val display: String = "inline", val baseline: String = "baseline",
    val width: NativeEpubLength? = null, val height: NativeEpubLength? = null,
    val floatSide: String = "none", val clearSide: String = "none",
    val percentMargins: Set<String> = emptySet(),
    val padding: NativeEpubInsets = NativeEpubInsets(),
    val border: Float = 0f, val borderColor: String? = null, val radius: Float = 0f,
    val borderImage: NativeEpubBorderImage? = null, val backgroundImage: String? = null,
    val avoidBreak: Boolean = false, val bleed: String? = null,
    val shadow: NativeEpubShadow? = null,
    val collapsedTopMargins: List<NativeEpubLength> = emptyList(),
    val collapsedBottomMargins: List<NativeEpubLength> = emptyList(),
    val fontFallbacks: List<String> = emptyList()
) {
    fun resolveMargins(width: Float, scale: Float): NativeCssStyle {
        if (percentMargins.isEmpty() && collapsedTopMargins.isEmpty() && collapsedBottomMargins.isEmpty()) return this
        fun resolve(side: String, value: Float) = if (side in percentMargins) value * width / (100f * scale) else value
        fun collapse(value: Float, margins: List<NativeEpubLength>): Float {
            val values = margins.map { it.pixels(width, scale) / scale } + value + 0f
            return values.max() + values.min()
        }
        return copy(marginTop = collapse(resolve("top", marginTop), collapsedTopMargins), marginRight = resolve("right", marginRight),
            marginBottom = collapse(resolve("bottom", marginBottom), collapsedBottomMargins), marginLeft = resolve("left", marginLeft),
            percentMargins = emptySet(), collapsedTopMargins = emptyList(), collapsedBottomMargins = emptyList())
    }
}
internal data class NativeEpubInsets(val top: Float = 0f, val right: Float = 0f, val bottom: Float = 0f, val left: Float = 0f)
internal data class NativeEpubBorderImage(val path: String, val verticalSlice: Int, val horizontalSlice: Int)
internal data class NativeEpubShadow(val x: Float, val y: Float, val blur: Float, val color: String)
internal data class NativeEpubLength(val value: Float, val relative: Boolean) {
    fun pixels(reference: Float, scale: Float): Float = if (relative) reference * value / 100f else value * scale
}
internal data class NativeEpubImage(val offset: Int, val path: String, val style: NativeCssStyle, val href: String?)
internal data class NativeEpubFootnote(val text: String, val imagePaths: List<String>)
internal data class NativeEpubRun(val start: Int, val end: Int, val style: NativeCssStyle, val href: String? = null)
internal data class NativeEpubRuby(val start: Int, val end: Int, val annotation: String)
internal data class NativeEpubGroup(val start: Int, val end: Int, val style: NativeCssStyle)
internal data class NativeEpubBlock(val start: Int, val end: Int, val style: NativeCssStyle,
    val imagePath: String? = null, val isParagraph: Boolean = false, val imageStyle: NativeCssStyle? = null,
    val outerStyles: List<NativeCssStyle> = emptyList(), val group: NativeEpubGroup? = null,
    val ruleColor: String? = null)
internal data class NativeEpubTextNode(val rawStart: Int, val rawEnd: Int, val path: List<Int>)

/** Offsets are UTF-16, just like DOM Range and the existing EPUB JavaScript. */
internal class NativeEpubDocument(val href: String, val text: String, val rawText: String,
    val sourceOffsets: IntArray, val nodes: List<NativeEpubTextNode>, val runs: List<NativeEpubRun>,
    val ruby: List<NativeEpubRuby>, val blocks: List<NativeEpubBlock>, val anchors: Map<String, Int>,
    val bodyStyle: NativeCssStyle, val embeddedFonts: List<NativeEpubFontFace> = emptyList(),
    val inlineImages: List<NativeEpubImage> = emptyList(),
    val imageLinks: Map<Int, String> = emptyMap(),
    val footnotes: Map<String, NativeEpubFootnote> = emptyMap(), val viewportImage: Boolean = false,
    val stretchViewportImage: Boolean = false) {
    fun selectionText(start: Int, end: Int): String = text.substring(start, end).replace("\uFFFC", "")
    fun sourcePosition(offset: Int): Int = sourceOffsets[offset.coerceIn(0, text.length)]
    fun visiblePosition(raw: Int): Int {
        var low = 0; var high = sourceOffsets.lastIndex
        while (low < high) { val mid = (low + high) ushr 1
            if (sourceOffsets[mid] < raw) low = mid + 1 else high = mid }
        // Synthetic paragraph breaks can share the next DOM character's offset.
        // Prefer that character so saved positions do not drift to the previous page.
        while (low < sourceOffsets.lastIndex && sourceOffsets[low + 1] == raw) low++
        return low
    }
    fun locator(offset: Int, progression: Float = 0f): EpubLocator {
        val pos = sourcePosition(offset)
        val node = nodes.firstOrNull { pos >= it.rawStart && pos < it.rawEnd } ?: nodes.lastOrNull()
        return EpubLocator(version = 2, href = href, domPath = node?.path.orEmpty(),
            textOffset = (pos - (node?.rawStart ?: pos)).coerceAtLeast(0), textPosition = pos,
            textLength = rawText.length, exact = rawText.substring(pos, (pos + 96).coerceAtMost(rawText.length)),
            prefix = rawText.substring((pos - 32).coerceAtLeast(0), pos),
            suffix = rawText.substring(pos, (pos + 32).coerceAtMost(rawText.length)), progression = progression)
    }
    fun resolve(locator: EpubLocator): Int? {
        // DOM/text offsets from a WebView may differ after its document transformation.
        // Quote context takes precedence when the stored offset no longer matches.
        val pos = locator.textPosition.coerceIn(0, rawText.length)
        if (locator.exact.isNotEmpty()) {
            if (rawText.startsWith(locator.exact, pos)) return visiblePosition(pos)
            val matches = mutableListOf<Int>(); var at = rawText.indexOf(locator.exact)
            while (at >= 0 && matches.size < 1000) { matches += at; at = rawText.indexOf(locator.exact, at + 1) }
            val contextual = matches.filter { p ->
                (locator.prefix.isEmpty() || rawText.substring(0, p).endsWith(locator.prefix)) &&
                    (locator.suffix.isEmpty() || rawText.startsWith(locator.suffix, p)) }
            val found = contextual.minByOrNull { kotlin.math.abs(it - pos) }
                ?: matches.singleOrNull() ?: return null
            return visiblePosition(found)
        }
        val node = nodes.firstOrNull { it.path == locator.domPath }
        return visiblePosition(node?.let { (it.rawStart + locator.textOffset).coerceIn(it.rawStart, it.rawEnd) } ?: pos)
    }
}
internal data class NativeEpubCompilation(val decision: ChapterRenderDecision, val document: NativeEpubDocument? = null)

/** Deliberately bounded CSS profile. Unsupported semantics never become a guessed layout. */
internal object NativeEpubCompiler {
    private class Unsupported(message: String) : IllegalArgumentException(message)
    private val inherited = setOf("font-size", "font-weight", "font-style", "font-family", "color", "line-height",
        "letter-spacing", "text-align", "direction", "writing-mode", "text-indent", "text-decoration")
    private val allowed = inherited + setOf("background", "background-color", "margin", "margin-top", "margin-right",
        "margin-bottom", "margin-left", "display", "page-break-before", "page-break-after", "break-before", "break-after",
        "vertical-align", "width", "height", "float", "clear", "padding-top", "padding-right", "padding-bottom", "padding-left",
        "border", "border-radius", "border-image", "background-image", "background-size", "background-position",
        "page-break-inside", "break-inside", "duokan-bleed", "duokan-text-indent", "text-shadow", "overflow", "ruby-align",
        "background-repeat", "border-width")
    // Mouse cursors are irrelevant on Android; StaticLayout handles justification.
    private val ignored = setOf("cursor", "text-justify")
    private val zeroBox = setOf("padding", "padding-top", "padding-right", "padding-bottom", "padding-left",
        "border", "border-top", "border-right", "border-bottom", "border-left", "border-width")
    private fun isZeroBox(value: String): Boolean = value.trim().equals("none", true) ||
        value.trim().split(Regex("\\s+")).all { it.matches(Regex("0(?:\\.0+)?(?:px|em|rem|pt|%)?", RegexOption.IGNORE_CASE)) }
    private val blockTags = setOf("p", "h1", "h2", "h3", "h4", "h5", "h6", "blockquote", "div", "section", "article", "body", "hr")
    private val tags = blockTags + setOf("span", "a", "b", "strong", "i", "em", "u", "sup", "sub", "br", "img", "ruby", "rb", "rt", "rp", "html")
    private fun imagePath(base: String, value: String): String = EpubPathResolver.resolve(base, value)
        ?.takeIf { it.substringAfterLast('.').lowercase() in setOf("png", "jpg", "jpeg", "webp") }
        ?: throw Unsupported("image path/format")
    fun compile(source: NativeEpubSource, readResource: (String) -> EpubResource? = { null }): NativeEpubCompilation = try {
        if (source.layout != EpubRenditionLayout.REFLOWABLE) throw Unsupported("fixed layout")
        source.unavailableReason?.let { throw Unsupported(it) }
        val dom = Jsoup.parse(source.resource.bytes.toString(Charsets.UTF_8))
        if (dom.select(WEREAD_FOOTNOTE_SELECTOR).any {
                it.attr(WEREAD_FOOTNOTE_TEXT_ATTR).isNotBlank() && it.closest("a[href]") == null
            }) throw Unsupported("WeRead footnote icon")
        val viewportImage = source.duokanPage != null
        var stretchViewportImage = false
        if (viewportImage) {
            val body = dom.body()
            val svg = body.select("svg").singleOrNull()
            val image = svg?.children()?.singleOrNull()
            val aspect = svg?.attr("preserveAspectRatio").orEmpty()
            if (source.duokanPage !in setOf(EpubDuokanPage.FULLSCREEN, EpubDuokanPage.FIT_WINDOW,
                    EpubDuokanPage.SPREAD_LEFT, EpubDuokanPage.SPREAD_RIGHT) ||
                svg == null || svg.hasAttr("style") || svg.hasAttr("class") || svg.hasAttr("transform") || svg.hasAttr("overflow") ||
                image?.normalName() != "image" || image.hasAttr("transform") || image.hasAttr("style") ||
                aspect !in setOf("", "none", "xMidYMid", "xMidYMid meet") ||
                (image.hasAttr("x") && image.attr("x") != "0") || (image.hasAttr("y") && image.attr("y") != "0") ||
                body.wholeText().isNotBlank() ||
                body.select("*").any { it !== body && it !== svg && it !== image && it.normalName() != "div" }) {
                throw Unsupported("Duokan page presentation")
            }
            val path = image.attr("xlink:href").ifBlank { image.attr("href") }
            imagePath(source.resource.path, path)
            stretchViewportImage = aspect == "none"
            svg.before(Element("img").attr("src", path))
            svg.remove()
        }
        val sheets = source.stylesheets
        val cssIndex = EpubCssIndex.parse(sheets)
        val noteBodies = dom.select("aside[epub:type=footnote], aside[role=doc-footnote]")
        val hidden = noteBodies.flatMap { it.getAllElements() }.toSet()
        if (dom.select("img.duokan-footnote").any { it.closest("a.duokan-footnote, a[epub:type=noteref], a[role=doc-noteref]") == null }) {
            throw Unsupported("unlinked footnote image")
        }
        val footnotes = dom.select("a.duokan-footnote, a[epub:type=noteref], a[role=doc-noteref]").associate { link ->
            val href = link.attr("href")
            val target = href.takeIf { it.startsWith('#') && it.length > 1 }?.drop(1)?.let(dom::getElementById)
            if (target == null || target !in hidden) throw Unsupported("unsupported footnote target")
            if (target.selectFirst("table, video, audio, svg, iframe, object, input, button, script") != null) {
                throw Unsupported("complex footnote body")
            }
            href to NativeEpubFootnote(target.text(), target.select("img").map { imagePath(source.resource.path, it.attr("src")) })
        }
        // Keep unconditional screen wrappers and EPUB metadata declarations.
        sheets.forEach { (_, css) ->
            val clean = css.replace(Regex("/\\*[\\s\\S]*?\\*/"), "")
            Regex("@([a-z-]+)([^;{]*)", RegexOption.IGNORE_CASE).findAll(clean).forEach { match ->
                val name = match.groupValues[1].lowercase()
                val prelude = match.groupValues[2].trim().lowercase()
                when (name) {
                    "font-face" -> Unit
                    "charset", "namespace", "page", "keyframes", "-webkit-keyframes" -> Unit
                    "media" -> if (prelude !in setOf("screen", "all"))
                        throw Unsupported("CSS at-rule @media $prelude")
                    else -> throw Unsupported("CSS at-rule @$name")
                }
            }
        }
        val fontFaces = mutableListOf<NativeEpubFontFace>()
        val fontFamilies = mutableSetOf<String>()
        val missingFonts = mutableMapOf<String, String>()
        val fontFaceRegex = Regex("@font-face\\s*\\{([^}]*)\\}", RegexOption.IGNORE_CASE)
        sheets.forEach { (sheetPath, css) ->
            fontFaceRegex.findAll(css.replace(Regex("/\\*[\\s\\S]*?\\*/"), "")).forEach { match ->
                fun descriptor(name: String): String? = Regex(
                    "(?:^|;)\\s*${Regex.escape(name)}\\s*:\\s*([^;]+)", RegexOption.IGNORE_CASE
                ).find(match.groupValues[1])?.groupValues?.get(1)?.trim()
                val family = descriptor("font-family")?.trim()?.trim('\'', '"')
                    ?.takeIf(String::isNotBlank) ?: throw Unsupported("font-face family")
                val src = descriptor("src") ?: throw Unsupported("font-face source")
                val candidates = EpubCssIndex.urls(src).mapNotNull { EpubPathResolver.resolve(sheetPath, it) }.distinct().toList()
                val resource = candidates.asSequence().filter { it.substringAfterLast('.', "").lowercase() in setOf("ttf", "otf", "ttc") }
                    .mapNotNull(readResource).firstOrNull { it.bytes.isNotEmpty() && it.bytes.size <= 64 * 1024 * 1024 }
                if (resource == null) {
                    if (!src.contains("local(", true)) missingFonts[family.lowercase()] = candidates.firstOrNull() ?: family
                    return@forEach
                }
                val weightDescriptor = descriptor("font-weight").orEmpty().lowercase()
                val weight = when (weightDescriptor) {
                    "bold" -> 700
                    "normal", "" -> 400
                    else -> weightDescriptor.split(Regex("\\s+")).first().toIntOrNull()
                        ?.takeIf { it in 100..900 } ?: 400
                }
                val italic = descriptor("font-style")?.trim()?.equals("italic", true) == true
                fontFaces += NativeEpubFontFace(family, resource, weight, italic)
                fontFamilies += family.lowercase()
            }
        }
        val rules = cssIndex.rules
        rules.forEach { rule ->
            if (!Regex("[a-zA-Z0-9_.*#\\-\\s>+~]+").matches(rule.selector)) throw Unsupported("selector ${rule.selector}")
        }
        val computed = IdentityHashMap<Element, Map<String, String>>()
        val styles = IdentityHashMap<Element, NativeCssStyle>()
        fun cascade(el: Element): Map<String, String> {
            val parent = el.parent()?.let { computed[it] }.orEmpty()
            val values = parent.filterKeys { it in inherited }.toMutableMap()
            val parentSize = el.parent()?.let { styles[it]?.size } ?: 16f
            when (el.normalName()) {
                "h1" -> values["font-size"] = "${parentSize * 2}px"
                "h2" -> values["font-size"] = "${parentSize * 1.5f}px"
                "h3" -> values["font-size"] = "${parentSize * 1.17f}px"
            }
            if (el.normalName() in setOf("b", "strong", "h1", "h2", "h3", "h4", "h5", "h6")) values["font-weight"] = "700"
            if (el.normalName() in setOf("i", "em")) values["font-style"] = "italic"
            if (el.normalName() == "u") values["text-decoration"] = "underline"
            data class Winner(val important: Boolean, val specificity: Int, val order: Int, val value: String)
            val winners = mutableMapOf<String, Winner>()
            fun accept(ds: List<EpubCssDeclaration>, score: Int, order: Int, path: String) {
                ds.forEach { d ->
                    val expanded = if (d.name in setOf("margin", "padding")) {
                        val parts = d.value.trim().split(Regex("\\s+"))
                        if (parts.size !in 1..4) throw Unsupported(d.name)
                        if (d.name == "margin" && parts.any { it.matches(Regex("[a-zA-Z]+")) &&
                                it.lowercase() !in setOf("auto", "inherit", "initial", "unset", "revert", "revert-layer") }) {
                            return@forEach
                        }
                        listOf("${d.name}-top" to parts[0], "${d.name}-right" to parts.getOrElse(1) { parts[0] },
                            "${d.name}-bottom" to parts.getOrElse(2) { parts[0] }, "${d.name}-left" to parts.getOrElse(3) { parts.getOrElse(1) { parts[0] } })
                    } else if (d.name == "background" && el.normalName() == "body" && EpubCssIndex.firstUrl(d.value) != null) {
                        val presentation = Regex("(?i)^([^\\s]+)\\s+(url\\([^)]*\\))\\s+no-repeat\\s+bottom\\s+center$")
                            .matchEntire(d.value.trim()) ?: throw Unsupported("body background shorthand")
                        listOf("background-color" to presentation.groupValues[1], "background-image" to presentation.groupValues[2],
                            "background-position" to "bottom center", "background-repeat" to "no-repeat", "background-size" to "auto")
                    } else listOf(d.name to d.value)
                    expanded.forEach { (key, v) ->
                        val next = Winner(d.important, score, order, if (key in setOf("background-image", "border-image")) {
                            val url = EpubCssIndex.firstUrl(v)
                            if (url == null) v else {
                                val asset = EpubPathResolver.resolve(path, url) ?: throw Unsupported("external CSS image")
                                v.substringAfter(')').trim().let { "url($asset) $it" }.trim()
                            }
                        } else v)
                        val old = winners[key]
                        if (old == null || compareValuesBy(next, old, { it.important }, { it.specificity }, { it.order }) >= 0) winners[key] = next
                    }
                }
            }
            rules.forEachIndexed { index, rule -> if (el.`is`(rule.selector)) {
                val ids = Regex("#[\\w-]+").findAll(rule.selector).count()
                val classes = Regex("\\.[\\w-]+").findAll(rule.selector).count()
                val types = rule.selector.split(Regex("[\\s>+~]+")).count { it.firstOrNull()?.isLetter() == true }
                accept(rule.declarations, ids * 10000 + classes * 100 + types, index, rule.sourcePath)
            } }
            if (el.hasAttr("style")) accept(EpubCssIndex.parse(listOf("inline" to "x{${el.attr("style")}}" )).rules.flatMap { it.declarations }, 1000000, Int.MAX_VALUE, source.resource.path)
            winners.forEach { (key, w) ->
                if (key in ignored || key in zeroBox && isZeroBox(w.value)) return@forEach
                if (key !in allowed) throw Unsupported("CSS $key")
                values[key] = when (w.value.lowercase()) { "inherit" -> parent[key] ?: "initial"; else -> w.value }
            }
            return values
        }
        dom.select("html, body, body *").forEach { el ->
            if (el in hidden) return@forEach
            if (el.normalName() !in tags) throw Unsupported("element ${el.normalName()}")
            if (el.attributes().any { it.key.startsWith("on", true) } || el.hasAttr("hidden")) throw Unsupported("dynamic/hidden element")
            val props = cascade(el)
            val st = style(el, props, el.parent()?.let { styles[it] } ?: NativeCssStyle(), fontFamilies, missingFonts)
            styles[el] = st
            computed[el] = props + mapOf("font-size" to "${st.size}px", "line-height" to st.lineHeight.toString(),
                "letter-spacing" to "${st.letterSpacing}px", "text-indent" to "${st.indent}px", "font-weight" to st.weight.toString(),
                "font-style" to if (st.italic) "italic" else "normal", "font-family" to (listOf(st.family) + st.fontFallbacks).joinToString(", "),
                "text-decoration" to if (st.underline) "underline" else "none")
        }
        val body = dom.body(); val raw = StringBuilder(); val textNodes = mutableListOf<NativeEpubTextNode>()
        val starts = IdentityHashMap<TextNode, Int>()
        val imageOffsets = IdentityHashMap<Element, Int>()
        fun index(node: Node, path: List<Int>) {
            if (node is Element && node.normalName() == "img") imageOffsets[node] = raw.length
            if (node is TextNode) { starts[node] = raw.length; val start = raw.length; raw.append(node.wholeText)
                textNodes += NativeEpubTextNode(start, raw.length, path) }
            node.childNodes().forEachIndexed { i, child -> index(child, path + i) }
        }
        val bodyPath = mutableListOf<Int>(); var ancestor: Node = body
        while (ancestor.parent() != null && ancestor != dom.selectFirst("html")) {
            bodyPath.add(0, ancestor.siblingIndex()); ancestor = ancestor.parent()!! }
        index(body, bodyPath)
        val text = StringBuilder(); val offsets = mutableListOf<Int>(); val runs = mutableListOf<NativeEpubRun>()
        val ruby = mutableListOf<NativeEpubRuby>(); val blocks = mutableListOf<NativeEpubBlock>(); val anchors = mutableMapOf<String, Int>()
        val inlineImages = mutableListOf<NativeEpubImage>()
        val imageLinks = mutableMapOf<Int, String>()
        fun append(s: String, rawStart: Int, style: NativeCssStyle, href: String?) {
            val start = text.length
            s.forEachIndexed { i, c ->
                if (c in " \t\r\n\u000c") { if (text.isNotEmpty() && text.last() !in " \n") { text.append(' '); offsets += rawStart + i } }
                else { text.append(c); offsets += rawStart + i } }
            if (text.length > start) runs += NativeEpubRun(start, text.length, style, href)
        }
        fun walk(el: Element, link: String? = null) {
            if (el in hidden) return
            val st = styles[el] ?: throw Unsupported("missing style")
            if (st.display == "none") return
            if (el.id().isNotEmpty()) anchors[el.id()] = text.length
            if (el.normalName() in setOf("rt", "rp")) return
            val block = st.display == "block"
            if (block && text.isNotEmpty() && text.last() != '\n') { text.append('\n'); offsets += offsets.lastOrNull()?.plus(1) ?: 0 }
            val start = text.length
            val firstBlock = blocks.size
            val href = el.attr("href").takeIf { el.normalName() == "a" && it.isNotBlank() } ?: link
            if (el.normalName() == "img") {
                val path = imagePath(source.resource.path, el.attr("src"))
                val standalone = block || st.floatSide != "none" || EpubFootnoteImagePolicy.isPublisherIllustration(el, cssIndex) ||
                    el.parents().takeWhile { it != body }.any { wrapper ->
                        styles[wrapper]?.display == "block" && wrapper.text().isBlank() && wrapper.select("img").size == 1
                    } || el.parent() == body
                text.append('\uFFFC'); offsets += imageOffsets.getValue(el)
                if (href != null) imageLinks[start] = href
                if (standalone) {
                    blocks += NativeEpubBlock(start, text.length, st, path, imageStyle = st)
                } else inlineImages += NativeEpubImage(start, path, st, href)
            } else if (el.normalName() == "hr") {
                text.append('\uFFFC'); offsets += offsets.lastOrNull() ?: 0
                blocks += NativeEpubBlock(start, text.length, st, ruleColor = st.borderColor ?: "gray")
            } else if (el.normalName() == "br") { text.append('\n'); offsets += offsets.lastOrNull()?.plus(1) ?: 0 }
            else el.childNodes().forEach { node -> when (node) {
                is TextNode -> append(node.wholeText, starts[node] ?: 0, st, href)
                is Element -> walk(node, href)
            } }
            if (el.normalName() == "ruby") {
                val annotations = el.select("rt")
                if (annotations.size != 1 || annotations[0].select("*").size > 1) throw Unsupported("complex ruby")
                if (text.length > start) ruby += NativeEpubRuby(start, text.length, annotations[0].text())
            }
            if (block && text.length > start) {
                val nested = blocks.size > firstBlock
                if (nested && runs.any { it.start >= start && it.end <= text.length && text.substring(it.start, it.end).isNotBlank() && blocks.none { b -> it.start >= b.start && it.end <= b.end } }) throw Unsupported("mixed block content")
                if (!nested) blocks += NativeEpubBlock(start, text.length, st, isParagraph = el.normalName() == "p")
                else if (el != body) {
                    val last = blocks.last()
                    if (blocks.size == firstBlock + 1 && last.imagePath != null && last.start == start && text.substring(last.end).isBlank() && el.text().isBlank()) {
                        blocks[blocks.lastIndex] = last.copy(style = st.copy(
                            align = if (st.align == "start") last.style.align else st.align,
                            floatSide = if (st.floatSide == "none") last.style.floatSide else st.floatSide,
                            clearSide = if (st.clearSide == "none") last.style.clearSide else st.clearSide,
                            bleed = st.bleed ?: last.style.bleed
                        ))
                    } else if (st.floatSide != "none" && st.avoidBreak && st.borderImage != null &&
                        blocks.size == firstBlock + 1 && last.imagePath == null && last.outerStyles.isEmpty()) {
                        blocks[firstBlock] = last.copy(outerStyles = listOf(st))
                    } else if (!st.vertical && st.width == null && st.height == null && st.floatSide == "none" &&
                        !st.avoidBreak && !st.pageBreakBefore && !st.pageBreakAfter && st.clearSide == "none" &&
                        st.bleed == null && st.borderImage == null && st.backgroundImage == null &&
                        (st.background != null || st.border > 0f || st.padding != NativeEpubInsets()) &&
                        (firstBlock until blocks.size).all { blocks[it].let { child -> child.group == null &&
                            child.imagePath == null && child.style.floatSide == "none" && child.outerStyles.isEmpty() } }) {
                        val group = NativeEpubGroup(blocks[firstBlock].start, last.end, st)
                        for (index in firstBlock until blocks.size) blocks[index] = blocks[index].copy(group = group)
                    } else if (st.background != null || st.width != null || st.height != null || st.floatSide != "none" ||
                        st.border != 0f || st.bleed != null || st.backgroundImage != null || st.padding != NativeEpubInsets() ||
                        st.avoidBreak || st.pageBreakBefore || st.pageBreakAfter) {
                        throw Unsupported("styled block container")
                    } else {
                        val first = blocks[firstBlock]
                        blocks[firstBlock] = first.copy(style = first.style.copy(
                            collapsedTopMargins = first.style.collapsedTopMargins + NativeEpubLength(st.marginTop, "top" in st.percentMargins),
                            clearSide = if (st.clearSide == "none") first.style.clearSide else st.clearSide))
                        val tail = blocks.last()
                        blocks[blocks.lastIndex] = tail.copy(style = tail.style.copy(
                            collapsedBottomMargins = tail.style.collapsedBottomMargins + NativeEpubLength(st.marginBottom, "bottom" in st.percentMargins)))
                        if (st.percentMargins.any { it == "left" || it == "right" } || st.marginLeft != 0f || st.marginRight != 0f) {
                            for (index in firstBlock until blocks.size) {
                                val child = blocks[index]
                                if (st.percentMargins.any { it == "left" || it == "right" } || child.style.percentMargins.any { it == "left" || it == "right" }) {
                                    throw Unsupported("relative container margins")
                                }
                                blocks[index] = child.copy(style = child.style.copy(
                                    marginLeft = child.style.marginLeft + st.marginLeft, marginRight = child.style.marginRight + st.marginRight))
                            }
                        }
                    }
                }
                if (text.last() != '\n') { text.append('\n'); offsets += offsets.lastOrNull()?.plus(1) ?: 0 }
            }
        }
        walk(body)
        val sorted = blocks.sortedBy { it.start }
        if (sorted.any { it.imagePath == null && (it.style.floatSide != "none" || it.style.height != null) }) {
            throw Unsupported("floating text or fixed-height text block")
        }
        if (styles[body]!!.vertical && (inlineImages.isNotEmpty() || sorted.any { it.style.floatSide != "none" })) {
            throw Unsupported("vertical inline or floating image")
        }
        if (sorted.isEmpty() || sorted.any { it.style.vertical != styles[body]!!.vertical }) throw Unsupported("empty/mixed writing mode")
        if (styles[body]!!.vertical && runs.any { it.style.baseline != "baseline" }) throw Unsupported("vertical superscript/subscript")
        offsets += raw.length
        // Synthetic breaks can bridge hidden ruby annotations. Keep the mapping monotonic.
        for (i in 1 until offsets.size) offsets[i] = offsets[i].coerceAtLeast(offsets[i - 1]).coerceAtMost(raw.length)
        val usedFontFamilies = (runs.map { it.style } + sorted.map { it.style })
            .flatMap { listOf(it.family) + it.fontFallbacks }.map(String::lowercase).toSet()
        NativeEpubCompilation(ChapterRenderDecision(ChapterRenderEngine.NATIVE), NativeEpubDocument(source.href,
            text.toString(), raw.toString(), offsets.toIntArray(), textNodes, runs, ruby, sorted, anchors,
            styles[body]!!, fontFaces.filter { it.family.lowercase() in usedFontFamilies }.distinct(), inlineImages, imageLinks,
            footnotes, viewportImage, stretchViewportImage))
    } catch (e: Exception) {
        NativeEpubCompilation(ChapterRenderDecision(ChapterRenderEngine.WEBVIEW, e.message ?: "document error"))
    }

    private fun length(value: String, em: Float, root: Float = 16f): Float {
        val m = Regex("([+-]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+))(px|em|rem|pt|%)?").matchEntire(value.trim().lowercase()) ?: throw Unsupported("length $value")
        val number = m.groupValues[1].toFloat()
        val result = number * when (m.groupValues[2]) { "em" -> em; "rem" -> root; "%" -> em / 100; "pt" -> 4f / 3; else -> 1f }
        if (!result.isFinite() || result !in -1024f..1024f) throw Unsupported("length limit")
        return result
    }
    private fun style(
        el: Element,
        p: Map<String, String>,
        parent: NativeCssStyle,
        embeddedFamilies: Set<String>, missingFonts: Map<String, String>
    ): NativeCssStyle {
        fun enum(key: String, default: String, choices: Set<String>): String = p[key]?.lowercase()?.let {
            if (it !in choices) throw Unsupported("$key $it"); it } ?: default
        val size = p["font-size"]?.let { if (it == "x-small") 10f else length(it, parent.size) }
            ?: when (el.normalName()) { "h1" -> parent.size * 2; "h2" -> parent.size * 1.5f; "h3" -> parent.size * 1.17f; else -> parent.size }
        val resolvedSize = size
        val weight = p["font-weight"]?.lowercase()?.let { when (it) {
            "normal" -> 400; "bold" -> 700
            "bolder" -> if (parent.weight < 350) 400 else if (parent.weight < 550) 700 else 900
            "lighter" -> if (parent.weight < 550) 100 else if (parent.weight < 750) 400 else 700
            else -> it.toIntOrNull()?.takeIf { n -> n in 100..900 } ?: throw Unsupported("font weight")
        } }
            ?: if (el.normalName() in setOf("b", "strong", "h1", "h2", "h3", "h4", "h5", "h6")) 700 else parent.weight
        val family = p["font-family"] ?: (listOf(parent.family) + parent.fontFallbacks).joinToString(", ")
        val familyStack = family.split(',').map { it.trim().trim('\'', '"') }
        val supportedFamilies = familyStack.filter {
            it.lowercase() in embeddedFamilies || it.lowercase() in setOf("serif", "sans-serif", "monospace")
        }
        val normalizedFamily = supportedFamilies.firstOrNull() ?: run {
            familyStack.firstNotNullOfOrNull { missingFonts[it.lowercase()] }?.let { throw Unsupported("missing embedded font $it") }
            "serif"
        }
        val line = p["line-height"]?.let { v -> if (v == "normal") 1.5f else v.toFloatOrNull() ?: (length(v, resolvedSize) / resolvedSize.coerceAtLeast(1f)) } ?: parent.lineHeight
        if (!line.isFinite() || line !in 1f..4f || resolvedSize !in 4f..160f) throw Unsupported("font metrics")
        fun color(key: String): String? = p[key]?.let { value ->
            if (!Regex("#[0-9a-fA-F]{3}(?:[0-9a-fA-F]{3})?|rgba\\(\\s*\\d{1,3}\\s*,\\s*\\d{1,3}\\s*,\\s*\\d{1,3}\\s*,\\s*(?:0?\\.\\d+|[01])\\s*\\)|black|white|red|green|blue|gray|grey|yellow|transparent", RegexOption.IGNORE_CASE).matches(value)) throw Unsupported("$key $value")
            value }
        val display = enum("display", if (el.normalName() in blockTags) "block" else "inline", setOf("block", "inline", "none"))
        if (el.normalName() in blockTags && display == "inline" || el.normalName() !in blockTags && el.normalName() != "img" && display == "block") throw Unsupported("display conversion")
        val isParagraph = el.normalName() in setOf("p", "h1", "h2", "h3", "h4", "h5", "h6", "blockquote")
        fun margin(side: String) = p["margin-$side"]?.let {
            // `auto` centers a block in CSS; the native flow is already full-width,
            // so treating it as zero preserves text pagination without a fallback.
            when {
                it.trim().equals("auto", true) -> 0f
                it.endsWith('%') -> it.dropLast(1).toFloatOrNull()?.takeIf { it in -100f..100f } ?: throw Unsupported("margin $it")
                else -> length(it, resolvedSize)
            }
        } ?: if (isParagraph && side in setOf("top", "bottom")) resolvedSize else 0f
        fun breakValue(key: String, other: String): Boolean = enum(key, p[other] ?: "auto", setOf("auto", "always", "page")) != "auto"
        fun dimension(key: String): NativeEpubLength? {
            val value = (p[key] ?: el.attr(key).takeIf { el.normalName() == "img" })?.trim()?.lowercase().orEmpty()
            if (value.isEmpty() || value == "auto") return null
            if (value.endsWith('%')) {
                val percent = value.dropLast(1).toFloatOrNull()?.takeIf { it in 0.01f..100f }
                    ?: throw Unsupported("$key $value")
                return NativeEpubLength(percent, true)
            }
            return NativeEpubLength(length(value, resolvedSize).takeIf { it > 0f } ?: throw Unsupported("$key $value"), false)
        }
        fun padding(side: String): Float = p["padding-$side"]?.let {
            length(it, resolvedSize).takeIf { value -> value >= 0f && (!it.endsWith('%') || value == 0f) }
                ?: throw Unsupported("padding $it")
        } ?: 0f
        val borderParts = p["border"]?.split(Regex("\\s+"))
        val ruleColor = borderParts?.takeIf { el.normalName() == "hr" && it.size == 2 && it[0] == "dotted" &&
            p["border-width"] == "1px 0 0 0" }?.get(1)
        if (p["border-width"] != null && ruleColor == null) throw Unsupported("border width")
        val border = borderParts?.takeIf { ruleColor == null }?.let { parts ->
            if (parts.size != 3 || parts[1] != "solid") throw Unsupported("border style")
            length(parts[0], resolvedSize).takeIf { it >= 0f } ?: throw Unsupported("border width")
        } ?: 0f
        val borderImage = p["border-image"]?.let { value ->
            val path = EpubCssIndex.firstUrl(value) ?: throw Unsupported("border image source")
            val slices = Regex("([1-9]\\d*)\\s+([1-9]\\d*)\\s+stretch").matchEntire(value.substringAfter(')').trim())
                ?: throw Unsupported("border image slices")
            NativeEpubBorderImage(path, slices.groupValues[1].toInt(), slices.groupValues[2].toInt())
        }
        val backgroundImage = p["background-image"]?.takeUnless { it == "none" }?.let {
            val bodyArt = el.normalName() == "body" && p["background-size"] == "contain" &&
                p["background-position"] == "bottom center" && p["background-repeat"] == "no-repeat"
            if (!bodyArt && (borderImage == null || p["background-size"] != "100%" || p["background-position"] != "center")) {
                throw Unsupported("background image presentation")
            }
            EpubCssIndex.firstUrl(it) ?: throw Unsupported("background image source")
        }
        val shadow = p["text-shadow"]?.takeUnless { it == "none" }?.let {
            val parts = it.split(Regex("\\s+"))
            if (parts.size != 4) throw Unsupported("text shadow")
            NativeEpubShadow(length(parts[0], resolvedSize), length(parts[1], resolvedSize), length(parts[2], resolvedSize), parts[3])
        }
        if (p["overflow"] !in setOf(null, "visible", "hidden") || p["ruby-align"] !in setOf(null, "center")) {
            throw Unsupported("overflow or ruby alignment")
        }
        return NativeCssStyle(resolvedSize, weight,
            enum("font-style", if (el.normalName() in setOf("i", "em")) "italic" else if (parent.italic) "italic" else "normal", setOf("normal", "italic")) == "italic",
            normalizedFamily, color("color") ?: parent.color, color("background-color") ?: color("background"), line,
            p["letter-spacing"]?.let { if (it.equals("normal", true) || it.equals("auto", true)) 0f else length(it, resolvedSize) } ?: parent.letterSpacing,
            enum("text-align", parent.align, setOf("start", "end", "left", "right", "center", "justify")),
            enum("direction", parent.direction, setOf("ltr", "rtl")),
            enum("writing-mode", if (parent.vertical) "vertical-rl" else "horizontal-tb", setOf("vertical-rl", "horizontal-tb")) == "vertical-rl",
            (p["duokan-text-indent"] ?: p["text-indent"])?.let { length(it, resolvedSize) } ?: parent.indent,
            enum("text-decoration", if (el.normalName() == "u") "underline" else if (parent.underline) "underline" else "none", setOf("none", "underline")) == "underline",
            margin("top"), margin("bottom"), margin("left"), margin("right"), breakValue("break-before", "page-break-before"), breakValue("break-after", "page-break-after"), display,
            enum("vertical-align", when (el.normalName()) { "sup" -> "super"; "sub" -> "sub"; else -> "baseline" },
                if (el.normalName() == "img") setOf("baseline", "middle", "super", "sub") else setOf("baseline", "super", "sub")),
            dimension("width"), dimension("height"), enum("float", "none", setOf("none", "left", "right")),
            enum("clear", "none", setOf("none", "left", "right", "both")),
            setOf("top", "right", "bottom", "left").filterTo(mutableSetOf()) { p["margin-$it"]?.endsWith('%') == true },
            NativeEpubInsets(padding("top"), padding("right"), padding("bottom"), padding("left")),
            border, ruleColor ?: borderParts?.get(2), p["border-radius"]?.let { length(it, resolvedSize) } ?: 0f,
            borderImage, backgroundImage, enum("break-inside", p["page-break-inside"] ?: "auto", setOf("auto", "avoid")) == "avoid",
            p["duokan-bleed"]?.also { if (it !in setOf("leftright", "lefttopright")) throw Unsupported("bleed $it") }, shadow,
            fontFallbacks = supportedFamilies.drop(1))
    }
}
