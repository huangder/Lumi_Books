package com.huangder.lumibooks.util.markdown.native

import android.content.Context
import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.*
import android.util.Base64
import android.util.LruCache
import coil.ImageLoader
import coil.decode.SvgDecoder
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.huangder.lumibooks.util.markdown.MarkdownRenderer
import io.noties.markwon.Markwon
import io.noties.prism4j.GrammarLocator
import io.noties.prism4j.Prism4j
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.commonmark.node.*
import ru.noties.jlatexmath.JLatexMathDrawable
import java.io.File
import java.util.regex.Pattern

data class MarkdownRenderStyle(val width: Int, val height: Int, val fontSize: Float, val color: Int,
    val dark: Boolean, val paragraphSpacing: Int = 0, val firstLineIndent: Float = 0f)

interface MarkdownBlockSource : AutoCloseable {
    val document: MarkdownDocument
    suspend fun renderBlock(index: Int, style: MarkdownRenderStyle): Spanned
}

/** Serialises Markwon/TeX work, caches only a bounded viewport, and never reparses Markdown. */
class MarkwonBlockSource(
    private val context: Context,
    override val document: MarkdownDocument,
    private val assets: Map<String, File>,
) : MarkdownBlockSource {
    private val renderer = Markwon.create(context)
    private val lock = Mutex()
    private val cache = object : LruCache<Pair<Int, MarkdownRenderStyle>, Spanned>(24 * 1024 * 1024) {
        override fun sizeOf(key: Pair<Int, MarkdownRenderStyle>, value: Spanned): Int = markdownRenderBytes(value)
    }
    @Volatile private var closed = false
    private val prism = Prism4j(LumiCodeGrammars)
    private val imageLoaderDelegate = lazy {
        ImageLoader.Builder(context).components { add(SvgDecoder.Factory()) }.build()
    }

    override suspend fun renderBlock(index: Int, style: MarkdownRenderStyle): Spanned = withContext(Dispatchers.Default) {
        check(!closed) { "Markdown document is closed" }
        val key = index to style
        cache.get(key)?.let { return@withContext it }
        lock.withLock {
            ensureActive()
            cache.get(key)?.let { return@withLock it }
            val block = document.blocks[index]
            val root = Document()
            val paragraph = Paragraph().also { root.appendChild(it) }
            val bounds = (listOf(0, block.text.length) + block.runs.flatMap { listOf(it.start, it.end) }).distinct().sorted()
            bounds.zipWithNext().forEach { (start, end) ->
                var parent: Node = paragraph
                block.runs.filter { it.start <= start && it.end >= end }.forEach { run ->
                    val wrapper: Node? = when (run.style) {
                        "bold" -> StrongEmphasis()
                        "italic" -> Emphasis()
                        "link" -> Link(run.value, null)
                        else -> null
                    }
                    if (wrapper != null) { parent.appendChild(wrapper); parent = wrapper }
                }
                parent.appendChild(Text(block.text.substring(start, end)))
            }
            val rendered = renderer.render(root)
            // CommonMark paragraph separators must never leak into persisted coordinates.
            check(rendered.toString().trimEnd('\n') == block.text.trimEnd('\n'))
            val text = SpannableStringBuilder(block.text)
            android.text.TextUtils.copySpansFrom(rendered, 0, minOf(rendered.length, text.length), Any::class.java, text, 0)
            fun span(value: Any, start: Int = 0, end: Int = text.length) {
                if (end > start) text.setSpan(value, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            if (block.kind == "heading") { span(StyleSpan(Typeface.BOLD)); span(RelativeSizeSpan(when (block.level) { 1 -> 1.7f; 2 -> 1.45f; 3 -> 1.25f; else -> 1.1f })) }
            if (block.kind == "rule") span(MarkdownRuleSpan(style.width, style.color))
            if (block.kind == "paragraph" && block.level == 0 && style.firstLineIndent > 0 &&
                (block.index == 0 || document.blocks[block.index - 1].sourceStart != block.sourceStart)) {
                span(MarkdownParagraphIndentSpan((style.fontSize * style.firstLineIndent).toInt()))
            }
            if (block.kind == "quote") { span(QuoteSpan(style.color)); span(LeadingMarginSpan.Standard((style.fontSize * 0.6f).toInt())) }
            if (block.level > 0 && block.kind != "heading") span(LeadingMarginSpan.Standard((style.fontSize * block.level).toInt()))
            if (block.label.isNotBlank() && block.label !in setOf("☑", "☐")) span(MarkdownListMarkerSpan(block.label, style.fontSize.toInt()))
            if (block.kind == "code") {
                span(TypefaceSpan("monospace"))
                val language = block.language.substringBefore(' ').lowercase()
                val grammar = prism.grammar(language)
                if (grammar != null) {
                    var offset = 0
                    fun apply(nodes: List<Prism4j.Node>) {
                        nodes.forEach { node ->
                            val begin = offset
                            if (node is Prism4j.Syntax) {
                                apply(node.children())
                                val color = when (node.type()) {
                                    "comment" -> if (style.dark) 0xff9ba59a.toInt() else 0xff62725d.toInt()
                                    "string" -> if (style.dark) 0xffc3e88d.toInt() else 0xff456d18.toInt()
                                    "number" -> if (style.dark) 0xfff0b67f.toInt() else 0xff9c541a.toInt()
                                    else -> if (style.dark) 0xffb9a4ef.toInt() else 0xff7044a0.toInt()
                                }
                                span(ForegroundColorSpan(color), begin, offset)
                            } else offset += node.textLength()
                        }
                    }
                    val prefix = block.codeContext
                    if (prefix.isEmpty()) apply(prism.tokenize(block.text, grammar))
                    else {
                        var end = 0
                        val closing = when (prefix) { "/*" -> "*/"; "//", "#" -> "\n"; else -> prefix }
                        while (end < block.text.length) {
                            if (block.text[end] == '\\' && prefix !in setOf("/*", "//", "#")) { end = minOf(end + 2, block.text.length); continue }
                            if (block.text.startsWith(closing, end)) { end += closing.length; break }
                            end++
                        }
                        span(ForegroundColorSpan(if (prefix in setOf("/*", "//", "#")) {
                            if (style.dark) 0xff9ba59a.toInt() else 0xff62725d.toInt()
                        } else if (style.dark) 0xffc3e88d.toInt() else 0xff456d18.toInt()), 0, end)
                        offset = end
                        apply(prism.tokenize(block.text.substring(end), grammar))
                    }
                }
            }
            for (run in block.runs) {
                ensureActive()
                when (run.style) {
                    "code" -> span(TypefaceSpan("monospace"), run.start, run.end)
                    "strike" -> span(StrikethroughSpan(), run.start, run.end)
                    "image" -> {
                        val data: Any? = if (run.value.startsWith("data:image/", true)) {
                            runCatching { Base64.decode(run.value.substringAfter(','), Base64.DEFAULT) }.getOrNull()
                        } else if (run.value.startsWith("https://", true) || run.value.startsWith("http://", true)) {
                            run.value
                        } else MarkdownRenderer.safeRelativePath(run.value)?.let(assets::get)
                        val drawable = data?.let {
                            (withTimeoutOrNull(10_000) { imageLoaderDelegate.value.execute(ImageRequest.Builder(context).data(it)
                                .size(style.width.coerceAtLeast(1), style.height.coerceAtLeast(1))
                                .allowHardware(false).build()) } as? SuccessResult)?.drawable
                        }
                        if (drawable != null) {
                            val w = drawable.intrinsicWidth.coerceAtLeast(1)
                            val h = drawable.intrinsicHeight.coerceAtLeast(1)
                            // Markdown images follow the reading column width.  Capping by the
                            // viewport height makes tall images too small and changes the scroll
                            // anchor when the real drawable is installed.
                            val scale = minOf(1f, style.width.toFloat() / w)
                            drawable.setBounds(0, 0, (w * scale).toInt().coerceAtLeast(1), (h * scale).toInt().coerceAtLeast(1))
                            span(ImageSpan(drawable, run.value), run.start, run.end)
                        } else span(MissingMarkdownImageSpan(style.fontSize, style.color), run.start, run.end)
                    }
                    "math", "math-block" -> {
                        val drawable = runCatching {
                            require(!Regex("\\\\(?:newcommand|renewcommand|def|loop|input|include)\\b").containsMatchIn(run.value))
                            ru.noties.jlatexmath.JLatexMathAndroid.init(context)
                            JLatexMathDrawable.builder(run.value).textSize(style.fontSize).color(style.color).build()
                        }.getOrNull()
                        if (drawable != null) {
                            drawable.setBounds(0, 0, drawable.intrinsicWidth.coerceAtLeast(1), drawable.intrinsicHeight.coerceAtLeast(1))
                            span(ImageSpan(drawable, ImageSpan.ALIGN_BASELINE), run.start, run.end)
                        }
                    }
                }
            }
            ensureActive()
            if (!closed) cache.put(key, text)
            text
        }
    }
    override fun close() {
        closed = true; cache.evictAll()
        if (imageLoaderDelegate.isInitialized()) imageLoaderDelegate.value.shutdown()
    }
}

/** Occupies the reading column while retaining the rule's original text coordinates. */
internal class MarkdownRuleSpan(private val width: Int, private val color: Int) : ReplacementSpan() {
    override fun getSize(paint: android.graphics.Paint, text: CharSequence, start: Int, end: Int,
        fm: android.graphics.Paint.FontMetricsInt?): Int {
        if (fm != null) paint.getFontMetricsInt(fm)
        return width.coerceAtLeast(1)
    }
    override fun draw(canvas: android.graphics.Canvas, text: CharSequence, start: Int, end: Int,
        x: Float, top: Int, y: Int, bottom: Int, paint: android.graphics.Paint) {
        val previousColor = paint.color
        val previousWidth = paint.strokeWidth
        paint.color = color
        paint.strokeWidth = (paint.textSize / 16f).coerceAtLeast(1f)
        val center = (top + bottom) / 2f
        canvas.drawLine(x, center, x + width, center, paint)
        paint.color = previousColor
        paint.strokeWidth = previousWidth
    }
}

class MarkdownParagraphIndentSpan(indent: Int) : LeadingMarginSpan.Standard(indent, 0)

internal fun markdownRenderBytes(text: Spanned): Int = (text.length.toLong() * 4 + 256 +
    text.getSpans(0, text.length, ImageSpan::class.java).sumOf {
        it.drawable.bounds.width().toLong().coerceAtLeast(1) * it.drawable.bounds.height().coerceAtLeast(1) * 4
    }).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()

private class MarkdownListMarkerSpan(private val label: String, private val size: Int) : LeadingMarginSpan {
    override fun getLeadingMargin(first: Boolean) = size * 2
    override fun drawLeadingMargin(c: android.graphics.Canvas, p: android.graphics.Paint, x: Int, dir: Int,
        top: Int, baseline: Int, bottom: Int, text: CharSequence, start: Int, end: Int, first: Boolean, layout: android.text.Layout?) {
        if (first) c.drawText(label, x.toFloat(), baseline.toFloat(), p)
    }
}
private class MissingMarkdownImageSpan(private val size: Float, private val color: Int) : ReplacementSpan() {
    override fun getSize(p: android.graphics.Paint, text: CharSequence, start: Int, end: Int, fm: android.graphics.Paint.FontMetricsInt?) = (size * 2).toInt()
    override fun draw(c: android.graphics.Canvas, text: CharSequence, start: Int, end: Int, x: Float, top: Int, y: Int, bottom: Int, p: android.graphics.Paint) {
        val old = p.color; p.color = color; c.drawText("▧", x, y.toFloat(), p); p.color = old
    }
}

/** Small bounded grammars; unknown languages deliberately remain plain code. */
private object LumiCodeGrammars : GrammarLocator {
    override fun languages() = setOf("kotlin", "java", "javascript", "js", "typescript", "ts", "python", "py", "json", "c", "cpp", "css", "sql", "bash", "sh", "yaml", "yml")
    override fun grammar(prism4j: Prism4j, language: String): Prism4j.Grammar? {
        if (language !in languages()) return null
        val lineComment = if (language in setOf("python", "py", "bash", "sh", "yaml", "yml")) "#[^\\n]*" else "//[^\\n]*|/\\*[\\s\\S]*?(?:\\*/|$)"
        fun token(name: String, regex: String) = Prism4j.token(name, Prism4j.pattern(Pattern.compile(regex)))
        return Prism4j.grammar(language,
            token("comment", lineComment),
            token("string", "\"(?:\\\\.|[^\"\\\\])*\"|'(?:\\\\.|[^'\\\\])*'"),
            token("keyword", "\\b(?:val|var|fun|class|object|interface|public|private|protected|static|final|void|int|import|package|return|if|else|when|for|while|in|is|as|try|catch|throw|new|this|super|true|false|null|None|True|False|def|from|async|await|function|const|let|export|SELECT|FROM|WHERE)\\b"),
            token("number", "\\b\\d+(?:\\.\\d+)?\\b"))
    }
}
