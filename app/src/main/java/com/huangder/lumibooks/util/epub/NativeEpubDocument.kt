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
    val unavailableReason: String? = null)

internal data class NativeCssStyle(
    val size: Float = 16f, val weight: Int = 400, val italic: Boolean = false,
    val family: String = "serif", val color: String? = null, val background: String? = null,
    val lineHeight: Float = 1.5f, val letterSpacing: Float = 0f,
    val align: String = "start", val direction: String = "ltr", val vertical: Boolean = false,
    val indent: Float = 0f, val underline: Boolean = false,
    val marginTop: Float = 0f, val marginBottom: Float = 0f,
    val marginLeft: Float = 0f, val marginRight: Float = 0f,
    val pageBreakBefore: Boolean = false, val pageBreakAfter: Boolean = false,
    val display: String = "inline", val baseline: String = "baseline"
)
internal data class NativeEpubRun(val start: Int, val end: Int, val style: NativeCssStyle, val href: String? = null)
internal data class NativeEpubRuby(val start: Int, val end: Int, val annotation: String)
internal data class NativeEpubBlock(val start: Int, val end: Int, val style: NativeCssStyle,
    val imagePath: String? = null)
internal data class NativeEpubTextNode(val rawStart: Int, val rawEnd: Int, val path: List<Int>)

/** Offsets are UTF-16, just like DOM Range and the existing EPUB JavaScript. */
internal class NativeEpubDocument(val href: String, val text: String, val rawText: String,
    val sourceOffsets: IntArray, val nodes: List<NativeEpubTextNode>, val runs: List<NativeEpubRun>,
    val ruby: List<NativeEpubRuby>, val blocks: List<NativeEpubBlock>, val anchors: Map<String, Int>,
    val bodyStyle: NativeCssStyle) {
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
        "vertical-align")
    // Mouse cursors are irrelevant on Android; StaticLayout handles justification.
    private val ignored = setOf("cursor", "text-justify")
    private val zeroBox = setOf("padding", "padding-top", "padding-right", "padding-bottom", "padding-left",
        "border", "border-top", "border-right", "border-bottom", "border-left", "border-width")
    private fun isZeroBox(value: String): Boolean = value.trim().equals("none", true) ||
        value.trim().split(Regex("\\s+")).all { it.matches(Regex("0(?:\\.0+)?(?:px|em|rem|pt|%)?", RegexOption.IGNORE_CASE)) }
    private val blockTags = setOf("p", "h1", "h2", "h3", "h4", "h5", "h6", "blockquote", "div", "section", "article", "body")
    private val tags = blockTags + setOf("span", "a", "b", "strong", "i", "em", "u", "br", "img", "ruby", "rb", "rt", "rp", "html")
    fun compile(source: NativeEpubSource): NativeEpubCompilation = try {
        if (source.layout != EpubRenditionLayout.REFLOWABLE) throw Unsupported("fixed layout")
        source.unavailableReason?.let { throw Unsupported(it) }
        val dom = Jsoup.parse(source.resource.bytes.toString(Charsets.UTF_8))
        val sheets = source.stylesheets.map { (path, css) -> path to stripFontFaceRules(css) }
        // Keep unconditional screen wrappers and EPUB metadata declarations.
        sheets.forEach { (_, css) ->
            val clean = css.replace(Regex("/\\*[\\s\\S]*?\\*/"), "")
            Regex("@([a-z-]+)([^;{]*)", RegexOption.IGNORE_CASE).findAll(clean).forEach { match ->
                val name = match.groupValues[1].lowercase()
                val prelude = match.groupValues[2].trim().lowercase()
                when (name) {
                    "charset", "namespace", "page", "keyframes", "-webkit-keyframes" -> Unit
                    "media" -> if (prelude !in setOf("screen", "all"))
                        throw Unsupported("CSS at-rule @media $prelude")
                    else -> throw Unsupported("CSS at-rule @$name")
                }
            }
        }
        val rules = EpubCssIndex.parse(sheets).rules
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
            fun accept(ds: List<EpubCssDeclaration>, score: Int, order: Int) {
                ds.forEach { d ->
                    val expanded = if (d.name == "margin") {
                        val parts = d.value.trim().split(Regex("\\s+"))
                        if (parts.size !in 1..4) throw Unsupported("margin")
                        listOf("margin-top" to parts[0], "margin-right" to parts.getOrElse(1) { parts[0] },
                            "margin-bottom" to parts.getOrElse(2) { parts[0] }, "margin-left" to parts.getOrElse(3) { parts.getOrElse(1) { parts[0] } })
                    } else listOf(d.name to d.value)
                    expanded.forEach { (key, v) ->
                        val next = Winner(d.important, score, order, v)
                        val old = winners[key]
                        if (old == null || compareValuesBy(next, old, { it.important }, { it.specificity }, { it.order }) >= 0) winners[key] = next
                    }
                }
            }
            rules.forEachIndexed { index, rule -> if (el.`is`(rule.selector)) {
                val ids = Regex("#[\\w-]+").findAll(rule.selector).count()
                val classes = Regex("\\.[\\w-]+").findAll(rule.selector).count()
                val types = rule.selector.split(Regex("[\\s>+~]+")).count { it.firstOrNull()?.isLetter() == true }
                accept(rule.declarations, ids * 10000 + classes * 100 + types, index)
            } }
            if (el.hasAttr("style")) accept(EpubCssIndex.parse(listOf("inline" to "x{${el.attr("style")}}" )).rules.flatMap { it.declarations }, 1000000, Int.MAX_VALUE)
            winners.forEach { (key, w) ->
                if (key in ignored || key in zeroBox && isZeroBox(w.value)) return@forEach
                if (key !in allowed) throw Unsupported("CSS $key")
                values[key] = when (w.value.lowercase()) { "inherit" -> parent[key] ?: "initial"; else -> w.value }
            }
            return values
        }
        dom.select("html, body, body *").forEach { el ->
            if (el.normalName() !in tags) throw Unsupported("element ${el.normalName()}")
            if (el.attributes().any { it.key.startsWith("on", true) } || el.hasAttr("hidden")) throw Unsupported("dynamic/hidden element")
            val props = cascade(el)
            val st = style(el, props, el.parent()?.let { styles[it] } ?: NativeCssStyle())
            styles[el] = st
            computed[el] = props + mapOf("font-size" to "${st.size}px", "line-height" to st.lineHeight.toString(),
                "letter-spacing" to "${st.letterSpacing}px", "text-indent" to "${st.indent}px", "font-weight" to st.weight.toString(),
                "font-style" to if (st.italic) "italic" else "normal", "font-family" to st.family,
                "text-decoration" to if (st.underline) "underline" else "none")
        }
        val body = dom.body(); val raw = StringBuilder(); val textNodes = mutableListOf<NativeEpubTextNode>()
        val starts = IdentityHashMap<TextNode, Int>()
        fun index(node: Node, path: List<Int>) {
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
        fun append(s: String, rawStart: Int, style: NativeCssStyle, href: String?) {
            val start = text.length
            s.forEachIndexed { i, c ->
                if (c in " \t\r\n\u000c") { if (text.isNotEmpty() && text.last() !in " \n") { text.append(' '); offsets += rawStart + i } }
                else { text.append(c); offsets += rawStart + i } }
            if (text.length > start) runs += NativeEpubRun(start, text.length, style, href)
        }
        fun walk(el: Element, link: String? = null) {
            val st = styles[el] ?: throw Unsupported("missing style")
            if (st.display == "none") return
            if (el.id().isNotEmpty()) anchors[el.id()] = text.length
            if (el.normalName() in setOf("rt", "rp")) return
            val block = st.display == "block"
            if (block && text.isNotEmpty() && text.last() != '\n') { text.append('\n'); offsets += offsets.lastOrNull()?.plus(1) ?: 0 }
            val start = text.length
            val href = el.attr("href").takeIf { el.normalName() == "a" && it.isNotBlank() } ?: link
            if (el.normalName() == "img") {
                if (el.hasAttr("width") || el.hasAttr("height") || !el.attr("src").matches(Regex("(?i).*\\.(png|jpe?g|webp|gif)"))) throw Unsupported("image sizing/format")
                val path = EpubPathResolver.resolve(source.resource.path, el.attr("src")) ?: throw Unsupported("image path")
                if (text.isNotEmpty() && text.last() != '\n') throw Unsupported("inline image")
                text.append('\uFFFC'); offsets += offsets.lastOrNull()?.plus(1) ?: 0
                blocks += NativeEpubBlock(start, text.length, st, path)
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
                val nested = blocks.any { it.start >= start && it.end <= text.length }
                if (nested && runs.any { it.start >= start && it.end <= text.length && text.substring(it.start, it.end).isNotBlank() && blocks.none { b -> it.start >= b.start && it.end <= b.end } }) throw Unsupported("mixed block content")
                if (!nested) blocks += NativeEpubBlock(start, text.length, st)
                else if (el != body && (st.marginTop != 0f || st.marginBottom != 0f || st.marginLeft != 0f || st.marginRight != 0f || st.background != null)) throw Unsupported("styled block container")
                if (text.last() != '\n') { text.append('\n'); offsets += offsets.lastOrNull()?.plus(1) ?: 0 }
            }
        }
        walk(body)
        val sorted = blocks.sortedBy { it.start }
        if (sorted.isEmpty() || sorted.any { it.style.vertical != styles[body]!!.vertical }) throw Unsupported("empty/mixed writing mode")
        if (styles[body]!!.vertical && runs.any { it.style.baseline != "baseline" }) throw Unsupported("vertical superscript/subscript")
        offsets += raw.length
        // Synthetic breaks can bridge hidden ruby annotations. Keep the mapping monotonic.
        for (i in 1 until offsets.size) offsets[i] = offsets[i].coerceAtLeast(offsets[i - 1]).coerceAtMost(raw.length)
        NativeEpubCompilation(ChapterRenderDecision(ChapterRenderEngine.NATIVE), NativeEpubDocument(source.href,
            text.toString(), raw.toString(), offsets.toIntArray(), textNodes, runs, ruby, sorted, anchors, styles[body]!!))
    } catch (e: Exception) {
        NativeEpubCompilation(ChapterRenderDecision(ChapterRenderEngine.WEBVIEW, e.message ?: "document error"))
    }

    /** Embedded EPUB fonts are optional for geometry.  Android's fallback font keeps
     * the chapter usable while avoiding a WebView just because fonts.css has @font-face. */
    private fun stripFontFaceRules(css: String): String {
        var value = css
        val rule = Regex("@font-face\\s*\\{[^{}]*\\}", RegexOption.IGNORE_CASE)
        repeat(8) { value = value.replace(rule, "") }
        return value
    }

    private fun length(value: String, em: Float, root: Float = 16f): Float {
        val m = Regex("([+]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+))(px|em|rem|pt|%)?").matchEntire(value.trim().lowercase()) ?: throw Unsupported("length $value")
        val number = m.groupValues[1].toFloat()
        val result = number * when (m.groupValues[2]) { "em" -> em; "rem" -> root; "%" -> em / 100; "pt" -> 4f / 3; else -> 1f }
        if (!result.isFinite() || result > 1024f) throw Unsupported("length limit")
        return result
    }
    private fun style(el: Element, p: Map<String, String>, parent: NativeCssStyle): NativeCssStyle {
        fun enum(key: String, default: String, choices: Set<String>): String = p[key]?.lowercase()?.let {
            if (it !in choices) throw Unsupported("$key $it"); it } ?: default
        val size = p["font-size"]?.let { length(it, parent.size) }
            ?: when (el.normalName()) { "h1" -> parent.size * 2; "h2" -> parent.size * 1.5f; "h3" -> parent.size * 1.17f; else -> parent.size }
        val resolvedSize = size
        val weight = p["font-weight"]?.lowercase()?.let { when (it) {
            "normal" -> 400; "bold" -> 700
            "bolder" -> if (parent.weight < 350) 400 else if (parent.weight < 550) 700 else 900
            "lighter" -> if (parent.weight < 550) 100 else if (parent.weight < 750) 400 else 700
            else -> it.toIntOrNull()?.takeIf { n -> n in 100..900 } ?: throw Unsupported("font weight")
        } }
            ?: if (el.normalName() in setOf("b", "strong", "h1", "h2", "h3", "h4", "h5", "h6")) 700 else parent.weight
        val family = p["font-family"]?.trim()?.trim('\'', '"') ?: parent.family
        // @font-face rules are stripped above; named families use the closest
        // platform family instead of forcing the whole chapter into WebView.
        val normalizedFamily = family.split(',').map { it.trim().trim('\'', '"').lowercase() }
            .firstOrNull { it in setOf("serif", "sans-serif", "monospace") } ?: "serif"
        val line = p["line-height"]?.let { v -> if (v == "normal") 1.5f else v.toFloatOrNull() ?: (length(v, resolvedSize) / resolvedSize.coerceAtLeast(1f)) } ?: parent.lineHeight
        if (!line.isFinite() || line !in 1f..4f || resolvedSize !in 4f..160f) throw Unsupported("font metrics")
        fun color(key: String): String? = p[key]?.let { value ->
            if (!Regex("#[0-9a-fA-F]{3}(?:[0-9a-fA-F]{3})?|black|white|red|green|blue|gray|grey|yellow|transparent", RegexOption.IGNORE_CASE).matches(value)) throw Unsupported("$key $value")
            value }
        val display = enum("display", if (el.normalName() in blockTags) "block" else "inline", setOf("block", "inline", "none"))
        if (el.normalName() in blockTags && display == "inline" || el.normalName() !in blockTags && display == "block") throw Unsupported("display conversion")
        val isParagraph = el.normalName() in setOf("p", "h1", "h2", "h3", "h4", "h5", "h6", "blockquote")
        fun margin(side: String) = p["margin-$side"]?.let {
            // `auto` centers a block in CSS; the native flow is already full-width,
            // so treating it as zero preserves text pagination without a fallback.
            if (it.trim().equals("auto", true)) 0f else length(it, resolvedSize)
        } ?: if (isParagraph && side in setOf("top", "bottom")) resolvedSize else 0f
        fun breakValue(key: String, other: String): Boolean = enum(key, p[other] ?: "auto", setOf("auto", "always", "page")) != "auto"
        return NativeCssStyle(resolvedSize, weight,
            enum("font-style", if (el.normalName() in setOf("i", "em")) "italic" else if (parent.italic) "italic" else "normal", setOf("normal", "italic")) == "italic",
            normalizedFamily, color("color") ?: parent.color, color("background-color") ?: color("background"), line,
            p["letter-spacing"]?.let { if (it.equals("normal", true) || it.equals("auto", true)) 0f else length(it, resolvedSize) } ?: parent.letterSpacing,
            enum("text-align", parent.align, setOf("start", "end", "left", "right", "center", "justify")),
            enum("direction", parent.direction, setOf("ltr", "rtl")),
            enum("writing-mode", if (parent.vertical) "vertical-rl" else "horizontal-tb", setOf("vertical-rl", "horizontal-tb")) == "vertical-rl",
            p["text-indent"]?.let { length(it, resolvedSize) } ?: parent.indent,
            enum("text-decoration", if (el.normalName() == "u") "underline" else if (parent.underline) "underline" else "none", setOf("none", "underline")) == "underline",
            margin("top"), margin("bottom"), margin("left"), margin("right"), breakValue("break-before", "page-break-before"), breakValue("break-after", "page-break-after"), display,
            enum("vertical-align", "baseline", setOf("baseline", "super", "sub")))
    }
}
