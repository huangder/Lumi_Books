package com.huangder.lumibooks.ui.reader.engine

import android.graphics.*
import android.icu.text.BreakIterator
import android.text.*
import android.text.style.*
import com.huangder.lumibooks.util.epub.*
import java.io.File
import java.util.Locale
import kotlin.math.*

internal data class NativeEpubResolvedFont(
    val family: String,
    val weight: Int,
    val italic: Boolean,
    val typeface: Typeface
)

internal data class NativeEpubRenderConfig(val width: Int, val height: Int, val fontPx: Float,
    val typeface: Typeface, val color: Int?, val paper: Int, val publisherColors: Boolean,
    val left: Float, val top: Float, val right: Float, val bottom: Float,
    val lineHeightOverride: Float? = null, val paragraphSpacingPx: Float? = null,
    val firstLineIndentChars: Float? = null, val publisherNightShade: Boolean = false,
    val embeddedTypefaces: List<NativeEpubResolvedFont> = emptyList())
internal data class NativeEpubHit(val start: Int, val end: Int, val bounds: RectF, val lineRange: IntRange? = null)
internal sealed interface NativeEpubDrawItem {
    val start: Int
    val end: Int
    fun draw(canvas: Canvas)
    fun hit(x: Float, y: Float): NativeEpubHit?
    fun highlight(canvas: Canvas, from: Int, to: Int, paint: Paint)
}
internal class NativeEpubHorizontalItem(override val start: Int, override val end: Int,
    val layout: StaticLayout, val firstLine: Int, val lastLine: Int, val x: Float, val y: Float,
    val background: Int?) : NativeEpubDrawItem {
    private val firstY = layout.getLineTop(firstLine).toFloat()
    private val height = layout.getLineBottom(lastLine - 1) - firstY
    override fun draw(canvas: Canvas) {
        canvas.save(); canvas.translate(x, y)
        background?.let { canvas.drawRect(0f, 0f, layout.width.toFloat(), height, Paint().apply { color = it }) }
        canvas.clipRect(0f, 0f, layout.width.toFloat(), height)
        canvas.translate(0f, -firstY); layout.draw(canvas); canvas.restore()
    }
    override fun hit(x: Float, y: Float): NativeEpubHit? {
        if (y < this.y || y >= this.y + height) return null
        val line = layout.getLineForVertical((y - this.y + firstY).toInt())
        val px = x - this.x
        if (line !in firstLine until lastLine || px < layout.getLineLeft(line) || px > layout.getLineRight(line)) return null
        val local = layout.getOffsetForHorizontal(line, px)
        return NativeEpubHit(start + local - layout.getLineStart(firstLine), start + local - layout.getLineStart(firstLine) + 1,
            RectF(this.x + layout.getPrimaryHorizontal(local), this.y + layout.getLineTop(line) - firstY,
                this.x + layout.getPrimaryHorizontal((local + 1).coerceAtMost(layout.text.length)), this.y + layout.getLineBottom(line) - firstY),
            (start + layout.getLineStart(line) - layout.getLineStart(firstLine)) until
                (start + layout.getLineEnd(line) - layout.getLineStart(firstLine)))
    }
    override fun highlight(canvas: Canvas, from: Int, to: Int, paint: Paint) {
        val blockStart = start - layout.getLineStart(firstLine)
        val a = max(from, start) - blockStart; val b = min(to, end) - blockStart
        if (a >= b) return
        val path = Path(); layout.getSelectionPath(a, b, path)
        canvas.save(); canvas.translate(x, y); canvas.clipRect(0f, 0f, layout.width.toFloat(), height)
        canvas.translate(0f, -firstY); canvas.drawPath(path, paint); canvas.restore()
    }
}
internal class NativeEpubGlyphItem(override val start: Int, override val end: Int,
    private val value: String, val bounds: RectF, private val paint: TextPaint,
    private val rotate: Boolean) : NativeEpubDrawItem {
    override fun draw(canvas: Canvas) {
        canvas.save()
        if (rotate) canvas.rotate(90f, bounds.centerX(), bounds.centerY())
        val fm = paint.fontMetrics
        canvas.drawText(if (rotate) value else verticalPresentationText(value),
            bounds.centerX() - paint.measureText(if (rotate) value else verticalPresentationText(value)) / 2,
            bounds.centerY() - (fm.ascent + fm.descent) / 2, paint)
        canvas.restore()
    }
    override fun hit(x: Float, y: Float) = if (bounds.contains(x, y)) NativeEpubHit(start, end, bounds) else null
    override fun highlight(canvas: Canvas, from: Int, to: Int, paint: Paint) {
        if (from < end && to > start) canvas.drawRect(bounds, paint)
    }
}
internal class NativeEpubImageItem(override val start: Int, override val end: Int,
    val path: String, private val bitmap: Bitmap, private val bounds: RectF) : NativeEpubDrawItem {
    override fun draw(canvas: Canvas) = canvas.drawBitmap(bitmap, null, bounds, Paint(Paint.FILTER_BITMAP_FLAG))
    override fun hit(x: Float, y: Float) = if (bounds.contains(x, y)) NativeEpubHit(start, end, bounds) else null
    override fun highlight(canvas: Canvas, from: Int, to: Int, paint: Paint) = Unit
}
internal data class NativeEpubPage(val start: Int, val end: Int, val items: List<NativeEpubDrawItem>)
internal class NativeEpubChapterLayout(val document: NativeEpubDocument, val pages: List<NativeEpubPage>,
    val config: NativeEpubRenderConfig, private val images: List<Bitmap>) : AutoCloseable {
    private var closed = false
    fun pageFor(offset: Int): Int = pages.indexOfLast { it.start <= offset }.coerceAtLeast(0)
    @Synchronized fun draw(canvas: Canvas, page: Int, highlights: List<Pair<IntRange, Int>> = emptyList()) {
        if (closed) return
        canvas.drawColor(config.paper)
        pages[page].items.forEach { it.draw(canvas) }
        // Highlights are overlays, with alpha supplied by the annotation/TTS caller.
        highlights.forEach { (range, color) -> val paint = Paint().apply { this.color = color }
            pages[page].items.forEach { it.highlight(canvas, range.first, range.last + 1, paint) } }
        if (config.publisherNightShade) {
            canvas.drawColor(0x73000000)
        }
    }
    @Synchronized override fun close() { closed = true; images.forEach { if (!it.isRecycled) it.recycle() } }
}

/** Ruby is a single shaped inline unit. Its metrics reserve space above the base text. */
internal class NativeRubySpan(private val base: String, private val annotation: String) : ReplacementSpan() {
    override fun getSize(paint: Paint, text: CharSequence, start: Int, end: Int, fm: Paint.FontMetricsInt?): Int {
        val rt = Paint(paint).apply { textSize *= .5f }
        fm?.let { paint.getFontMetricsInt(it); it.ascent -= ceil(rt.fontSpacing).toInt(); it.top = min(it.top, it.ascent) }
        return ceil(max(paint.measureText(base), rt.measureText(annotation))).toInt()
    }
    override fun draw(canvas: Canvas, text: CharSequence, start: Int, end: Int, x: Float, top: Int, y: Int, bottom: Int, paint: Paint) {
        val rt = Paint(paint).apply { textSize *= .5f }
        val width = max(paint.measureText(base), rt.measureText(annotation))
        canvas.drawText(base, x + (width - paint.measureText(base)) / 2, y.toFloat(), paint)
        canvas.drawText(annotation, x + (width - rt.measureText(annotation)) / 2, y + paint.fontMetrics.ascent - rt.fontMetrics.descent, rt)
    }
}
private class NativeTypefaceSpan(private val face: Typeface) : MetricAffectingSpan() {
    override fun updateDrawState(tp: TextPaint) { tp.typeface = face }
    override fun updateMeasureState(tp: TextPaint) { tp.typeface = face }
}
private class NativeLetterSpacingSpan(private val spacing: Float) : MetricAffectingSpan() {
    override fun updateDrawState(tp: TextPaint) { tp.letterSpacing = spacing }
    override fun updateMeasureState(tp: TextPaint) { tp.letterSpacing = spacing }
}

internal object NativeEpubChapterRenderer {
    private fun cssColor(value: String?): Int? = value?.let {
        val v = if (it.startsWith('#') && it.length == 4) "#" + it.drop(1).flatMap { c -> listOf(c, c) }.joinToString("") else it
        Color.parseColor(v)
    }
    fun layout(document: NativeEpubDocument, config: NativeEpubRenderConfig,
        readResource: (String) -> ByteArray?): NativeEpubChapterLayout {
        val scale = config.fontPx / 16f
        val left = config.left + document.bodyStyle.marginLeft * scale
        val right = config.width - config.right - document.bodyStyle.marginRight * scale
        val top = config.top + document.bodyStyle.marginTop * scale
        val bottom = config.height - config.bottom - document.bodyStyle.marginBottom * scale
        require(right - left >= config.fontPx * 2 && bottom - top >= config.fontPx * 2) { "Viewport too small" }
        val cfg = config.copy(paper = if (config.publisherColors) cssColor(document.bodyStyle.background) ?: config.paper else config.paper)
        val images = mutableListOf<Bitmap>()
        val pages = mutableListOf<NativeEpubPage>(); var items = mutableListOf<NativeEpubDrawItem>()
        fun flush() { if (items.isNotEmpty()) { pages += NativeEpubPage(items.minOf { it.start }, items.maxOf { it.end }, items.toList()); items = mutableListOf() } }
        fun paint(style: NativeCssStyle): TextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            textSize = style.size * scale
            val publisherColor = config.color ?: if (config.publisherColors) cssColor(style.color) ?: Color.BLACK else Color.BLACK
            color = if (config.publisherNightShade && isDarkNeutral(publisherColor)) {
                Color.rgb(0xE8, 0xE8, 0xEA)
            } else publisherColor
            val embedded = config.embeddedTypefaces
                .filter { it.family.equals(style.family, ignoreCase = true) }
                .minByOrNull { (if (it.italic == style.italic) 0 else 1000) + abs(it.weight - style.weight) }
            typeface = embedded?.let { Typeface.create(it.typeface, style.weight, style.italic) }
                ?: Typeface.create(
                    if (style.family == "serif") config.typeface else Typeface.create(style.family, Typeface.NORMAL),
                    style.weight,
                    style.italic
                )
            letterSpacing = style.letterSpacing / style.size
            isUnderlineText = style.underline
        }
        try {
            var y = top; var previousMargin = 0f
            var columnX = right; var verticalY = top
            document.blocks.forEach { block ->
                if (block.style.pageBreakBefore) { flush(); y = top; columnX = right; verticalY = top }
                if (block.imagePath != null) {
                    flush(); y = top; columnX = right; verticalY = top
                    val bytes = readResource(block.imagePath) ?: error("Missing image")
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }; BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                    require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Invalid image" }
                    var sample = 1
                    while (bounds.outWidth / sample > config.width * 2 || bounds.outHeight / sample > config.height * 2 ||
                        bounds.outWidth.toLong() / sample * (bounds.outHeight / sample) * 4 > 8 * 1024 * 1024) sample *= 2
                    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample }) ?: error("Image decode failed")
                    images += bitmap
                    require(images.sumOf { it.allocationByteCount.toLong() } <= 24 * 1024 * 1024) { "Image memory limit" }
                    val fit = min((right - left) / bitmap.width, (bottom - top) / bitmap.height)
                    val w = bitmap.width * fit; val h = bitmap.height * fit
                    items += NativeEpubImageItem(block.start, block.end, block.imagePath, bitmap,
                        RectF(left + (right - left - w) / 2, top + (bottom - top - h) / 2, left + (right - left + w) / 2, top + (bottom - top + h) / 2))
                    flush()
                } else if (!block.style.vertical) {
                    val st = block.style.let { original ->
                        if (!block.isParagraph) original else original.copy(
                            marginTop = if (config.paragraphSpacingPx != null) 0f else original.marginTop,
                            marginBottom = if (config.paragraphSpacingPx != null) 0f else original.marginBottom,
                            indent = config.firstLineIndentChars?.times(original.size) ?: original.indent
                        )
                    }
                    val blockLeft = left + st.marginLeft * scale
                    val width = (right - blockLeft - st.marginRight * scale).toInt()
                    require(width > config.fontPx)
                    val text = SpannableStringBuilder(document.text.substring(block.start, block.end))
                    document.runs.filter { it.start < block.end && it.end > block.start }.forEach { run ->
                        val a = max(run.start, block.start) - block.start; val b = min(run.end, block.end) - block.start
                        val p = paint(run.style)
                        text.setSpan(AbsoluteSizeSpan(p.textSize.toInt()), a, b, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                        text.setSpan(NativeTypefaceSpan(p.typeface), a, b, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                        text.setSpan(ForegroundColorSpan(p.color), a, b, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                        text.setSpan(NativeLetterSpacingSpan(p.letterSpacing), a, b, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                        when (run.style.baseline) {
                            "super" -> text.setSpan(SuperscriptSpan(), a, b, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                            "sub" -> text.setSpan(SubscriptSpan(), a, b, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                        }
                        if (run.style.underline) text.setSpan(UnderlineSpan(), a, b, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                        if (config.publisherColors) cssColor(run.style.background)?.let { text.setSpan(BackgroundColorSpan(it), a, b, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE) }
                    }
                    document.ruby.filter { it.start >= block.start && it.end <= block.end }.forEach { rt ->
                        text.setSpan(NativeRubySpan(document.text.substring(rt.start, rt.end), rt.annotation), rt.start - block.start, rt.end - block.start, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE) }
                    if (st.indent > 0) text.setSpan(LeadingMarginSpan.Standard((st.indent * scale).toInt(), 0), 0, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    val alignment = when (st.align) { "center" -> Layout.Alignment.ALIGN_CENTER
                        "right" -> if (st.direction == "rtl") Layout.Alignment.ALIGN_NORMAL else Layout.Alignment.ALIGN_OPPOSITE
                        "left" -> if (st.direction == "rtl") Layout.Alignment.ALIGN_OPPOSITE else Layout.Alignment.ALIGN_NORMAL
                        "end" -> Layout.Alignment.ALIGN_OPPOSITE; else -> Layout.Alignment.ALIGN_NORMAL }
                    val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint(st), width)
                        .setAlignment(alignment).setTextDirection(if (st.direction == "rtl") TextDirectionHeuristics.RTL else TextDirectionHeuristics.LTR)
                        .setIncludePad(false).setLineSpacing(0f, config.lineHeightOverride ?: st.lineHeight)
                        .setBreakStrategy(Layout.BREAK_STRATEGY_HIGH_QUALITY)
                        .setJustificationMode(if (st.align == "justify") Layout.JUSTIFICATION_MODE_INTER_WORD else Layout.JUSTIFICATION_MODE_NONE).build()
                    y += max(previousMargin, st.marginTop * scale)
                    var first = 0
                    while (first < layout.lineCount) {
                        val lineHeight = layout.getLineBottom(first) - layout.getLineTop(first)
                        require(lineHeight <= bottom - top) { "Line taller than page" }
                        if (y + lineHeight > bottom) { flush(); y = top }
                        var last = first + 1
                        while (last < layout.lineCount && y + layout.getLineBottom(last) - layout.getLineTop(first) <= bottom) last++
                        items += NativeEpubHorizontalItem(block.start + layout.getLineStart(first), block.start + layout.getLineEnd(last - 1), layout,
                            first, last, blockLeft, y, if (config.publisherColors) cssColor(st.background) else null)
                        y += layout.getLineBottom(last - 1) - layout.getLineTop(first)
                        first = last
                        if (first < layout.lineCount) { flush(); y = top }
                    }
                    previousMargin = config.paragraphSpacingPx?.takeIf { block.isParagraph }
                        ?: st.marginBottom * scale
                } else {
                    val st = block.style.let { original ->
                        if (!block.isParagraph) original else original.copy(
                            marginTop = if (config.paragraphSpacingPx != null) 0f else original.marginTop,
                            marginBottom = if (config.paragraphSpacingPx != null) 0f else original.marginBottom,
                            indent = config.firstLineIndentChars?.times(original.size) ?: original.indent
                        )
                    }
                    require(st.align == "start" && st.direction == "ltr" && st.marginLeft == 0f && st.marginRight == 0f && st.background == null) { "Unsupported vertical block decoration" }
                    val runs = document.runs.filter { it.start < block.end && it.end > block.start }
                    val em = max(st.size, runs.maxOfOrNull { it.style.size } ?: st.size) * scale
                    val hasRuby = document.ruby.any { it.start < block.end && it.end > block.start }
                    val columnWidth = max(em * st.lineHeight, em * if (hasRuby) 1.65f else 1f)
                    fun column() { columnX -= columnWidth
                        if (columnX < left) { flush(); columnX = right - columnWidth }
                        verticalY = top + st.indent * scale }
                    column(); verticalY += st.marginTop * scale
                    val breaker = BreakIterator.getCharacterInstance(Locale.ROOT)
                    val value = document.text.substring(block.start, block.end); breaker.setText(value)
                    val boundaries = mutableListOf<Int>(); var at = breaker.first()
                    while (at != BreakIterator.DONE) { boundaries += at; at = breaker.next() }
                    var i = 0
                    while (i < boundaries.lastIndex) {
                        val a = block.start + boundaries[i]; val rt = document.ruby.firstOrNull { it.start == a }
                        var finalIndex = i + 1
                        if (rt != null) { finalIndex = boundaries.indexOf(rt.end - block.start); require(finalIndex > i) }
                        val rubyLength = (finalIndex - i) * em
                        require(rubyLength <= bottom - top) { "Ruby taller than column" }
                        if (verticalY + rubyLength > bottom) column()
                        val rubyTop = verticalY
                        while (i < finalIndex) {
                            val begin = block.start + boundaries[i]; val end = block.start + boundaries[i + 1]
                            val glyph = document.text.substring(begin, end)
                            if (glyph == "\n") { column(); i++; continue }
                            val run = runs.firstOrNull { begin >= it.start && begin < it.end }
                            val glyphPaint = paint(run?.style ?: st)
                            items += NativeEpubGlyphItem(begin, end, glyph,
                                RectF(columnX, verticalY, columnX + em, verticalY + em), glyphPaint, shouldRotateVerticalCluster(glyph))
                            verticalY += em + st.letterSpacing * scale; i++
                        }
                        if (rt != null) {
                            val annotationPaint = paint(st).apply { textSize *= .5f }
                            val annotation = rt.annotation.codePoints().toArray().map { String(Character.toChars(it)) }
                            val advance = annotationPaint.textSize
                            val rtY = rubyTop + (verticalY - rubyTop - annotation.size * advance) / 2
                            require(rtY >= top && rtY + annotation.size * advance <= bottom) { "Ruby annotation overflow" }
                            annotation.forEachIndexed { n, glyph -> items += NativeEpubGlyphItem(rt.start, rt.end, glyph,
                                RectF(columnX + em, rtY + n * advance, columnX + em + advance, rtY + (n + 1) * advance), annotationPaint, shouldRotateVerticalCluster(glyph)) }
                        }
                    }
                    columnX -= config.paragraphSpacingPx?.takeIf { block.isParagraph }
                        ?: st.marginBottom * scale
                }
                if (block.style.pageBreakAfter) { flush(); y = top; columnX = right; verticalY = top }
            }
            flush(); require(pages.isNotEmpty())
            return NativeEpubChapterLayout(document, pages, cfg, images)
        } catch (error: Exception) { images.forEach { it.recycle() }; throw error }
    }

    private fun isDarkNeutral(color: Int): Boolean {
        val red = Color.red(color)
        val green = Color.green(color)
        val blue = Color.blue(color)
        val maximum = maxOf(red, green, blue)
        val minimum = minOf(red, green, blue)
        val luminance = red * 0.2126 + green * 0.7152 + blue * 0.0722
        return maximum - minimum <= 16 && luminance < 150
    }
}
