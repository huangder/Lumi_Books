package com.huangder.lumibooks.ui.reader.engine

import com.huangder.lumibooks.util.readerLetterSpacingEm
import android.graphics.*
import android.icu.text.BreakIterator
import android.text.*
import android.text.style.*
import androidx.core.graphics.withClip
import com.huangder.lumibooks.ui.reader.ReaderNaturalAlignmentSpan
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
    val embeddedTypefaces: List<NativeEpubResolvedFont> = emptyList(), val overridePublisherFont: Boolean = false,
    val readerFontWeight: Int = 400, val density: Float = 1f)
internal data class NativeEpubHit(val start: Int, val end: Int, val bounds: RectF, val lineRange: IntRange? = null,
    val imagePath: String? = null)
internal sealed interface NativeEpubDrawItem {
    val start: Int
    val end: Int
    fun draw(canvas: Canvas)
    fun hit(x: Float, y: Float): NativeEpubHit?
    fun highlight(canvas: Canvas, from: Int, to: Int, paint: Paint)
    fun addSentenceBounds(from: Int, to: Int, density: Float, bounds: RectF) = Unit
}
internal class NativeEpubHorizontalItem(override val start: Int, override val end: Int,
    val layout: StaticLayout, val firstLine: Int, val lastLine: Int, val x: Float, val y: Float,
    val background: Int?, adaptivePunctuation: Boolean = false,
    private val decoration: NativeEpubBox? = null) : NativeEpubDrawItem {
    private val firstY = layout.getLineTop(firstLine).toFloat()
    private val height = layout.getLineBottom(lastLine - 1) - firstY
    private val geometry = if (adaptivePunctuation) ReaderLineGeometry(layout, layout.text, Layout.JUSTIFICATION_MODE_NONE) else null
    private val adjustedLines = geometry?.let { positions -> (firstLine until lastLine).filter(positions::isLineStretched) }.orEmpty()
    private val textPainter = if (adjustedLines.isEmpty()) null else ReaderTextPainter()
    override fun draw(canvas: Canvas) {
        decoration?.draw(canvas)
        canvas.save(); canvas.translate(x, y)
        background?.let { canvas.drawRect(0f, 0f, layout.width.toFloat(), height, Paint().apply { color = it }) }
        canvas.clipRect(0f, 0f, layout.width.toFloat(), height)
        canvas.translate(0f, -firstY)
        canvas.save()
        for (line in adjustedLines) canvas.clipOutRect(0, layout.getLineTop(line), layout.width, layout.getLineBottom(line))
        layout.draw(canvas)
        canvas.restore()
        textPainter?.draw(canvas, layout, layout.text as Spanned, checkNotNull(geometry),
            lines = adjustedLines, drawSpanBackgrounds = true)
        canvas.restore()
    }
    override fun hit(x: Float, y: Float): NativeEpubHit? {
        if (y < this.y || y >= this.y + height) return null
        val line = layout.getLineForVertical((y - this.y + firstY).toInt())
        val px = x - this.x
        if (line !in firstLine until lastLine) return null
        val text = layout.text as Spanned
        for (image in text.getSpans(layout.getLineStart(line), layout.getLineEnd(line), NativeInlineImageSpan::class.java)) {
            val offset = text.getSpanStart(image)
            if (offset !in layout.getLineStart(line) until layout.getLineEnd(line)) continue
            val leading = layout.getPrimaryHorizontal(offset)
            val imageX = image.left(if (layout.isRtlCharAt(offset)) leading - image.advance else leading)
            val imageY = layout.getLineBaseline(line) + image.top
            val rect = RectF(this.x + imageX, this.y + imageY - firstY,
                this.x + imageX + image.width, this.y + imageY + image.height - firstY)
            if (rect.contains(x, y)) {
                val position = start - layout.getLineStart(firstLine) + offset
                return NativeEpubHit(position, position + 1, rect, imagePath = image.path)
            }
        }
        val adjusted = geometry?.takeIf { line in adjustedLines }
        val bounds = adjusted?.lineRange(line)
        if (px < (bounds?.left ?: layout.getLineLeft(line)) || px > (bounds?.right ?: layout.getLineRight(line))) return null
        if (adjusted != null) {
            val local = adjusted.offsetForHorizontal(line, px)?.coerceAtMost(layout.getLineEnd(line) - 1) ?: return null
            val range = adjusted.horizontalRange(line, local, local + 1) ?: return null
            val base = start - layout.getLineStart(firstLine)
            return NativeEpubHit(base + local, base + local + 1,
                RectF(this.x + range.left, this.y + layout.getLineTop(line) - firstY,
                    this.x + range.right, this.y + layout.getLineBottom(line) - firstY),
                (base + layout.getLineStart(line)) until (base + layout.getLineEnd(line)))
        }
        val local = layout.getOffsetForHorizontal(line, px).coerceAtMost(layout.getLineEnd(line) - 1)
        if (local < layout.getLineStart(line) || text[local] == '\uFFFC') return null
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
        canvas.translate(0f, -firstY)
        canvas.save()
        for (line in adjustedLines) canvas.clipOutRect(0, layout.getLineTop(line), layout.width, layout.getLineBottom(line))
        canvas.drawPath(path, paint)
        canvas.restore()
        for (line in adjustedLines) {
            val range = geometry?.horizontalRange(line, a, b) ?: continue
            canvas.drawRect(range.left, layout.getLineTop(line).toFloat(), range.right,
                layout.getLineBottom(line).toFloat(), paint)
        }
        canvas.restore()
    }

    override fun addSentenceBounds(from: Int, to: Int, density: Float, bounds: RectF) {
        if (from >= end || to <= start) return
        val base = start - layout.getLineStart(firstLine)
        val lineGeometry = geometry ?: ReaderLineGeometry(layout, layout.text, Layout.JUSTIFICATION_MODE_NONE)
        val metrics = layout.paint.fontMetrics
        val gap = 1.5f * density
        val padding = 2f * density
        for (line in firstLine until lastLine) {
            val range = lineGeometry.horizontalRange(line, from - base, to - base) ?: continue
            val baseline = layout.getLineBaseline(line)
            val top = maxOf(layout.getLineTop(line) + gap, baseline + metrics.ascent - padding)
            val bottom = minOf(layout.getLineBottom(line) - gap, baseline + metrics.descent + padding)
            bounds.union(x + range.left - 3f * density, y + top - firstY,
                x + range.right + 3f * density, y + bottom - firstY)
        }
    }
}
internal class NativeEpubBox(private val bounds: RectF, private val color: Int?, private val radius: Float,
    private val border: Float, private val borderColor: Int?, private val background: Bitmap?,
    private val borderBitmap: Bitmap?, private val slices: NativeEpubBorderImage?) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    fun draw(canvas: Canvas) {
        color?.let { paint.color = it; canvas.drawRoundRect(bounds, radius, radius, paint) }
        if (background != null) {
            paint.color = Color.WHITE
            canvas.drawBitmap(background, null, bounds, paint)
        }
        if (borderBitmap != null && slices != null) {
            val image = borderBitmap
            val horizontal = slices.horizontalSlice.coerceAtMost(image.width / 2)
            val vertical = slices.verticalSlice.coerceAtMost(image.height / 2)
            val sourceX = intArrayOf(0, horizontal, image.width - horizontal, image.width)
            val sourceY = intArrayOf(0, vertical, image.height - vertical, image.height)
            val x = floatArrayOf(bounds.left, bounds.left + border, bounds.right - border, bounds.right)
            val y = floatArrayOf(bounds.top, bounds.top + border, bounds.bottom - border, bounds.bottom)
            paint.color = Color.WHITE
            for (row in 0..2) for (column in 0..2) {
                if (row == 1 && column == 1) continue
                val src = Rect(sourceX[column], sourceY[row], sourceX[column + 1], sourceY[row + 1])
                val dst = RectF(x[column], y[row], x[column + 1], y[row + 1])
                if (!src.isEmpty && !dst.isEmpty) canvas.drawBitmap(image, src, dst, paint)
            }
        } else if (border > 0f && borderColor != null) {
            paint.color = borderColor
            paint.strokeWidth = border
            paint.style = Paint.Style.STROKE
            val inset = RectF(bounds).apply { inset(border / 2f, border / 2f) }
            canvas.drawRoundRect(inset, radius, radius, paint)
            paint.style = Paint.Style.FILL
        }
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
    override fun addSentenceBounds(from: Int, to: Int, density: Float, bounds: RectF) {
        if (from < end && to > start) {
            bounds.union(this.bounds.left + 1.5f * density, this.bounds.top - 3f * density,
                this.bounds.right - 1.5f * density, this.bounds.bottom + 3f * density)
        }
    }
}
internal class NativeEpubImageItem(override val start: Int, override val end: Int,
    val path: String, private val bitmap: Bitmap, val bounds: RectF) : NativeEpubDrawItem {
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
    override fun draw(canvas: Canvas) = canvas.drawBitmap(bitmap, null, bounds, paint)
    override fun hit(x: Float, y: Float) = if (bounds.contains(x, y)) NativeEpubHit(start, end, bounds, imagePath = path) else null
    override fun highlight(canvas: Canvas, from: Int, to: Int, paint: Paint) = Unit
}

private class NativeEpubRuleItem(override val start: Int, override val end: Int,
    private val left: Float, private val right: Float, private val top: Float, thickness: Float, color: Int
) : NativeEpubDrawItem {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        strokeWidth = thickness
        pathEffect = DashPathEffect(floatArrayOf(thickness, thickness * 2), 0f)
    }
    override fun draw(canvas: Canvas) = canvas.drawLine(left, top, right, top, paint)
    override fun hit(x: Float, y: Float): NativeEpubHit? = null
    override fun highlight(canvas: Canvas, from: Int, to: Int, paint: Paint) = Unit
}
private class NativeInlineImageSpan(val path: String, private val bitmap: Bitmap, val width: Float,
    val height: Float, val top: Float, private val marginLeft: Float, private val marginRight: Float) : ReplacementSpan() {
    private val filter = Paint(Paint.FILTER_BITMAP_FLAG)
    val advance = ceil(width + marginLeft + marginRight).toInt()
    override fun getSize(paint: Paint, text: CharSequence?, start: Int, end: Int, fm: Paint.FontMetricsInt?): Int {
        fm?.let {
            val base = paint.fontMetricsInt
            it.ascent = min(base.ascent, floor(top).toInt())
            it.descent = max(base.descent, ceil(top + height).toInt())
            it.top = min(base.top, it.ascent)
            it.bottom = max(base.bottom, it.descent)
        }
        return advance
    }
    fun left(edge: Float) = edge + marginLeft
    override fun draw(canvas: Canvas, text: CharSequence?, start: Int, end: Int, x: Float,
        top: Int, y: Int, bottom: Int, paint: Paint) {
        canvas.drawBitmap(bitmap, null, RectF(left(x), y + this.top, left(x) + width, y + this.top + height), filter)
    }
}
internal data class NativeEpubPage(val start: Int, val end: Int, val items: List<NativeEpubDrawItem>,
    val decorations: List<NativeEpubBox> = emptyList())
internal data class NativeEpubHighlights(
    val annotations: List<Pair<IntRange, Int>> = emptyList(),
    val sentence: Pair<IntRange, Int>? = null
)
internal class NativeEpubChapterLayout(val document: NativeEpubDocument, val pages: List<NativeEpubPage>,
    val config: NativeEpubRenderConfig, private val images: List<Bitmap>,
    private val backdrop: Bitmap? = null) : AutoCloseable {
    private var closed = false
    fun pageFor(offset: Int): Int = pages.indexOfLast { it.start <= offset }.coerceAtLeast(0)

    @Synchronized fun draw(canvas: Canvas, page: Int, highlights: NativeEpubHighlights = NativeEpubHighlights()) {
        if (closed) return
        canvas.withClip(0, 0, config.width, config.height) {
            drawColor(config.paper)
            backdrop?.let {
                val fit = min(config.width.toFloat() / it.width, config.height.toFloat() / it.height)
                val left = (config.width - it.width * fit) / 2f
                drawBitmap(it, null, RectF(left, config.height - it.height * fit, config.width - left,
                    config.height.toFloat()), Paint(Paint.FILTER_BITMAP_FLAG))
            }
            pages[page].decorations.forEach { it.draw(this) }
            pages[page].items.forEach { it.draw(this) }
            highlights.annotations.forEach { (range, color) ->
                val paint = Paint().apply { this.color = color }
                pages[page].items.forEach { it.highlight(this, range.first, range.last + 1, paint) }
            }
            highlights.sentence?.let { (range, color) ->
                val bounds = RectF()
                pages[page].items.forEach { it.addSentenceBounds(range.first, range.last + 1, config.density, bounds) }
                if (!bounds.isEmpty) {
                    val radius = minOf(6f * config.density, bounds.width() / 2f, bounds.height() / 2f)
                    drawRoundRect(bounds, radius, radius, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color })
                }
            }
            if (config.publisherNightShade) drawColor(0x73000000)
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
private class NativeShadowSpan(private val radius: Float, private val x: Float, private val y: Float,
    private val color: Int) : CharacterStyle() {
    override fun updateDrawState(tp: TextPaint) = tp.setShadowLayer(radius, x, y, color)
}

internal object NativeEpubChapterRenderer {
    private fun cssColor(value: String?): Int? = value?.let {
        if (it.equals("transparent", true)) return Color.TRANSPARENT
        if (it.startsWith("rgba(", ignoreCase = true)) {
            val parts = it.substringAfter('(').substringBeforeLast(')').split(',').map(String::trim)
            return Color.argb((parts[3].toFloat().coerceIn(0f, 1f) * 255).roundToInt(),
                parts[0].toInt().coerceIn(0, 255), parts[1].toInt().coerceIn(0, 255), parts[2].toInt().coerceIn(0, 255))
        }
        val v = if (it.startsWith('#') && it.length == 4) "#" + it.drop(1).flatMap { c -> listOf(c, c) }.joinToString("") else it
        Color.parseColor(v)
    }
    fun layout(document: NativeEpubDocument, config: NativeEpubRenderConfig, checkActive: () -> Unit = {},
        readResource: (String) -> ByteArray?): NativeEpubChapterLayout {
        val scale = config.fontPx / 16f
        val body = document.bodyStyle.resolveMargins(config.width - config.left - config.right, scale)
        val left = if (document.viewportImage) 0f else config.left + (body.marginLeft + body.padding.left) * scale
        val right = if (document.viewportImage) config.width.toFloat() else config.width - config.right - (body.marginRight + body.padding.right) * scale
        val top = if (document.viewportImage) 0f else config.top + (body.marginTop + body.padding.top) * scale
        val bottom = if (document.viewportImage) config.height.toFloat() else config.height - config.bottom - (body.marginBottom + body.padding.bottom) * scale
        require(right - left >= config.fontPx * 2 && bottom - top >= config.fontPx * 2) { "Viewport too small" }
        val cfg = config.copy(paper = if (config.publisherColors) cssColor(document.bodyStyle.background) ?: config.paper else config.paper)
        val images = mutableListOf<Bitmap>()
        val decoded = mutableMapOf<String, Triple<Bitmap, Int, Int>>()
        var imageBytes = 0L
        fun image(path: String): Triple<Bitmap, Int, Int> = decoded.getOrPut(path) {
            checkActive()
            val bytes = readResource(path) ?: error("Missing image $path")
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Invalid image $path" }
            var sample = 1
            while (bounds.outWidth / sample > config.width * 2 || bounds.outHeight / sample > config.height * 2 ||
                bounds.outWidth.toLong() / sample * (bounds.outHeight / sample) * 4 > 8 * 1024 * 1024) sample *= 2
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size,
                BitmapFactory.Options().apply { inSampleSize = sample }) ?: error("Image decode failed")
            images += bitmap
            imageBytes += bitmap.allocationByteCount
            require(imageBytes <= 24 * 1024 * 1024) { "Image memory limit" }
            Triple(bitmap, bounds.outWidth, bounds.outHeight)
        }
        fun dimensions(style: NativeCssStyle, width: Float, height: Float, intrinsicWidth: Int, intrinsicHeight: Int,
            fitPage: Boolean = false): Pair<Float, Float> {
            require(width > 0f && height > 0f) { "Image has no layout space" }
            val aspect = intrinsicWidth.toFloat() / intrinsicHeight
            val requestedWidth = style.width?.pixels(width, scale)
            val requestedHeight = style.height?.pixels(height, scale)
            val desiredWidth = requestedWidth ?: requestedHeight?.times(aspect) ?: if (fitPage) width else intrinsicWidth * scale
            val desiredHeight = requestedHeight ?: desiredWidth / aspect
            val fit = min(1f, min(width / desiredWidth, height / desiredHeight))
            return desiredWidth * fit to desiredHeight * fit
        }
        fun box(style: NativeCssStyle, bounds: RectF): NativeEpubBox? {
            if (!config.publisherColors) return null
            if (style.background == null && style.backgroundImage == null && style.border == 0f) return null
            return NativeEpubBox(bounds, cssColor(style.background), style.radius * scale,
                style.border * scale, cssColor(style.borderColor), style.backgroundImage?.let { image(it).first },
                style.borderImage?.let { image(it.path).first }, style.borderImage)
        }
        fun collapsed(first: Float, second: Float) = maxOf(0f, first, second) + minOf(0f, first, second)
        val pages = mutableListOf<NativeEpubPage>(); var items = mutableListOf<NativeEpubDrawItem>()
        val groupBounds = linkedMapOf<NativeEpubGroup, RectF>()
        fun addGroupBounds(group: NativeEpubGroup, bounds: RectF) {
            groupBounds[group]?.union(bounds) ?: run { groupBounds[group] = bounds }
        }
        var floatBottom = top
        var floatEdge = 0f
        var floatSide = "none"
        fun flush() {
            if (items.isNotEmpty()) {
                pages += NativeEpubPage(items.minOf { it.start }, items.maxOf { it.end }, items.toList(),
                    groupBounds.mapNotNull { (group, bounds) -> box(group.style, bounds) })
                items = mutableListOf()
                groupBounds.clear()
            }
            floatBottom = top
            floatSide = "none"
        }
        val typefaces = mutableMapOf<Triple<List<String>, Int, Boolean>, Typeface>()
        fun paint(style: NativeCssStyle): TextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            textSize = style.size * scale
            val publisherColor = config.color ?: if (config.publisherColors) cssColor(style.color) ?: Color.BLACK else Color.BLACK
            color = if (config.publisherNightShade && isDarkNeutral(publisherColor)) {
                Color.rgb(0xE8, 0xE8, 0xEA)
            } else publisherColor
            val families = if (config.overridePublisherFont) emptyList() else listOf(style.family) + style.fontFallbacks
            val weight = if (config.overridePublisherFont) max(config.readerFontWeight, style.weight) else style.weight
            typeface = typefaces.getOrPut(Triple(families, weight, style.italic)) {
                if (config.overridePublisherFont && !style.italic && weight == config.readerFontWeight) config.typeface
                else families.firstNotNullOfOrNull { family ->
                    val embedded = config.embeddedTypefaces.filter { it.family.equals(family, ignoreCase = true) }
                        .minByOrNull { (if (it.italic == style.italic) 0 else 1000) + abs(it.weight - weight) }
                    if (embedded != null) {
                        if (embedded.weight == weight && embedded.italic == style.italic) embedded.typeface
                        else Typeface.create(embedded.typeface, weight, style.italic)
                    } else if (family in setOf("serif", "sans-serif", "monospace")) {
                        Typeface.create(Typeface.create(family, Typeface.NORMAL), weight, style.italic)
                    } else null
                } ?: Typeface.create(config.typeface, weight, style.italic)
            }
            letterSpacing = readerLetterSpacingEm(style.letterSpacing, style.size)
            isUnderlineText = style.underline
        }
        try {
            val backdrop = if (config.publisherColors) body.backgroundImage?.let { image(it).first } else null
            var y = top; var previousMargin = 0f
            var columnX = right; var verticalY = top
            val inlineByBlock = document.inlineImages.iterator()
            var nextInline = if (inlineByBlock.hasNext()) inlineByBlock.next() else null
            var runIndex = 0
            var rubyIndex = 0
            document.blocks.forEach { block ->
                checkActive()
                while (runIndex < document.runs.size && document.runs[runIndex].end <= block.start) runIndex++
                val runs = document.runs.subList(runIndex, document.runs.size).asSequence().takeWhile { it.start < block.end }
                while (rubyIndex < document.ruby.size && document.ruby[rubyIndex].end <= block.start) rubyIndex++
                val rubies = document.ruby.subList(rubyIndex, document.ruby.size).asSequence().takeWhile { it.start < block.end }
                val blockStyle = block.style.resolveMargins(right - left, scale)
                if (block.style.pageBreakBefore) { flush(); y = top; columnX = right; verticalY = top }
                if (block.style.clearSide == "both" || block.style.clearSide == floatSide && floatSide != "none") {
                    y = max(y, floatBottom)
                }
                if (block.imagePath != null) {
                    val (bitmap, intrinsicWidth, intrinsicHeight) = image(block.imagePath)
                    val st = blockStyle
                    val art = block.imageStyle ?: st
                    val fullPage = document.viewportImage || st.vertical || document.blocks.size == 1 && st.width == null && art.width == null && art.height == null
                    val bleed = art.bleed ?: st.bleed
                    val artAreaLeft = if (bleed != null) 0f else left
                    val artAreaRight = if (bleed != null) config.width.toFloat() else right
                    val usable = artAreaRight - artAreaLeft - (st.marginLeft + st.marginRight) * scale
                    val boxWidth = (st.width?.pixels(usable, scale) ?: usable).coerceAtMost(usable)
                    require(boxWidth > 1f)
                    val (w, h) = if (document.stretchViewportImage) boxWidth to (bottom - top)
                        else dimensions(art, boxWidth, bottom - top, intrinsicWidth, intrinsicHeight, fullPage)
                    y = max(y, floatBottom)
                    val spacing = collapsed(previousMargin, st.marginTop * scale)
                    if (fullPage || y + spacing + h > bottom) { flush(); y = top }
                    else y = max(top, y + spacing)
                    val artLeft = when {
                        st.floatSide == "left" -> artAreaLeft + st.marginLeft * scale
                        st.floatSide == "right" || st.align in setOf("end", "right") -> artAreaRight - st.marginRight * scale - w
                        fullPage || st.align == "center" -> artAreaLeft + st.marginLeft * scale + (usable - w) / 2f
                        else -> artAreaLeft + st.marginLeft * scale
                    }
                    val artTop = when {
                        bleed == "lefttopright" && items.isEmpty() -> 0f
                        fullPage -> top + (bottom - top - h) / 2f
                        else -> y
                    }
                    items += NativeEpubImageItem(block.start, block.end, block.imagePath, bitmap,
                        RectF(artLeft, artTop, artLeft + w, artTop + h))
                    when {
                        fullPage -> { flush(); y = top; columnX = right; verticalY = top }
                        st.floatSide != "none" -> {
                            floatSide = st.floatSide
                            floatBottom = y + h + st.marginBottom * scale
                            floatEdge = if (floatSide == "left") artLeft + w + st.marginRight * scale else artLeft - st.marginLeft * scale
                        }
                        else -> y = max(top, artTop + h)
                    }
                    previousMargin = if (st.floatSide == "none") st.marginBottom * scale else 0f
                } else if (block.ruleColor != null) {
                    val group = block.group?.style?.resolveMargins(right - left, scale)
                    val ruleLeft = left + ((group?.marginLeft ?: 0f) + (group?.padding?.left ?: 0f) + (group?.border ?: 0f)) * scale
                    val ruleRight = right - ((group?.marginRight ?: 0f) + (group?.padding?.right ?: 0f) + (group?.border ?: 0f)) * scale
                    y += max(previousMargin, 4 * scale)
                    if (y + scale > bottom) { flush(); y = top }
                    items += NativeEpubRuleItem(block.start, block.end, ruleLeft, ruleRight, y, scale,
                        if (config.publisherColors) cssColor(block.ruleColor) ?: Color.GRAY else config.color ?: Color.GRAY)
                    block.group?.let {
                        addGroupBounds(it, RectF(left + (group?.marginLeft ?: 0f) * scale, y,
                            right - (group?.marginRight ?: 0f) * scale, y + scale))
                    }
                    y += scale
                    previousMargin = 4 * scale
                } else if (!block.style.vertical) {
                    val bubble = block.outerStyles.singleOrNull()?.resolveMargins(right - left, scale)
                    val group = block.group?.style?.resolveMargins(right - left, scale)
                    val groupLeft = left + (group?.marginLeft ?: 0f) * scale
                    val groupRight = right - (group?.marginRight ?: 0f) * scale
                    val startsGroup = block.group?.start == block.start
                    val groupTop = if (startsGroup) ((group?.padding?.top ?: 0f) + (group?.border ?: 0f)) * scale else 0f
                    val groupBottom = if (block.group?.end == block.end) ((group?.padding?.bottom ?: 0f) + (group?.border ?: 0f)) * scale else 0f
                    val st = blockStyle.let { original ->
                        if (!block.isParagraph) original else original.copy(
                            marginTop = if (config.paragraphSpacingPx != null) 0f else original.marginTop,
                            marginBottom = if (config.paragraphSpacingPx != null) 0f else original.marginBottom,
                            indent = config.firstLineIndentChars?.times(original.size) ?: original.indent
                        )
                    }
                    val outline = bubble ?: st
                    val insetLeft = (outline.border + outline.padding.left) * scale
                    val insetRight = (outline.border + outline.padding.right) * scale
                    val insetTop = (outline.border + outline.padding.top) * scale
                    val insetBottom = (outline.border + outline.padding.bottom) * scale
                    var blockLeft = groupLeft + (st.marginLeft + (bubble?.marginLeft ?: 0f) + (group?.padding?.left ?: 0f) + (group?.border ?: 0f)) * scale + insetLeft
                    val availableWidth = groupRight - blockLeft - (st.marginRight + (bubble?.marginRight ?: 0f) + (group?.padding?.right ?: 0f) + (group?.border ?: 0f)) * scale - insetRight
                    var width = (st.width?.pixels(availableWidth, scale) ?: availableWidth).coerceAtMost(availableWidth).toInt()
                    require(width > config.fontPx)
                    val text = SpannableStringBuilder(document.text.substring(block.start, block.end))
                    runs.forEach { run ->
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
                        if (config.publisherColors) run.style.shadow?.let { shadow ->
                            cssColor(shadow.color)?.let { color ->
                                text.setSpan(NativeShadowSpan(shadow.blur * scale, shadow.x * scale, shadow.y * scale, color),
                                    a, b, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                            }
                        }
                    }
                    rubies.filter { it.start >= block.start && it.end <= block.end }.forEach { rt ->
                        text.setSpan(NativeRubySpan(document.text.substring(rt.start, rt.end), rt.annotation), rt.start - block.start, rt.end - block.start, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE) }
                    var hasInlineImages = false
                    while (true) {
                        val asset = nextInline?.takeIf { it.offset < block.end } ?: break
                        nextInline = if (inlineByBlock.hasNext()) inlineByBlock.next() else null
                        require(asset.offset >= block.start)
                        val (bitmap, intrinsicWidth, intrinsicHeight) = image(asset.path)
                        val marginLeft = asset.style.marginLeft * scale
                        val marginRight = asset.style.marginRight * scale
                        val (imageWidth, imageHeight) = dimensions(asset.style, width - marginLeft - marginRight,
                            bottom - top, intrinsicWidth, intrinsicHeight)
                        val font = paint(asset.style)
                        val imageTop = when (asset.style.baseline) {
                            "middle" -> -(imageHeight + font.textSize * .5f) / 2f
                            "super" -> -imageHeight + font.ascent() / 2f
                            "sub" -> -imageHeight + font.descent()
                            else -> -imageHeight
                        }
                        val offset = asset.offset - block.start
                        text.setSpan(NativeInlineImageSpan(asset.path, bitmap, imageWidth, imageHeight, imageTop,
                            marginLeft, marginRight), offset, offset + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                        hasInlineImages = true
                    }
                    if (st.indent > 0) text.setSpan(LeadingMarginSpan.Standard((st.indent * scale).toInt(), 0), 0, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    if (bubble != null) {
                        val used = ceil(Layout.getDesiredWidth(text, paint(st))).toInt().coerceIn(1, width)
                        if (bubble.floatSide == "right") blockLeft += width - used
                        width = used
                    }
                    val alignment = when (st.align) { "center" -> Layout.Alignment.ALIGN_CENTER
                        "right" -> if (st.direction == "rtl") Layout.Alignment.ALIGN_NORMAL else Layout.Alignment.ALIGN_OPPOSITE
                        "left" -> if (st.direction == "rtl") Layout.Alignment.ALIGN_OPPOSITE else Layout.Alignment.ALIGN_NORMAL
                        "end" -> Layout.Alignment.ALIGN_OPPOSITE; else -> Layout.Alignment.ALIGN_NORMAL }
                    val adaptivePunctuation = !hasInlineImages && floatBottom <= y && block.isParagraph && st.direction != "rtl" &&
                        st.align in setOf("start", "left") && text.isNotEmpty()
                    if (adaptivePunctuation) text.setSpan(ReaderNaturalAlignmentSpan(), 0, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    fun shape(leftIndents: IntArray? = null, rightIndents: IntArray? = null) =
                        StaticLayout.Builder.obtain(text, 0, text.length, paint(st), width)
                        .setAlignment(alignment).setTextDirection(if (st.direction == "rtl") TextDirectionHeuristics.RTL else TextDirectionHeuristics.LTR)
                        .setIncludePad(false).setLineSpacing(0f, config.lineHeightOverride ?: st.lineHeight)
                        .setBreakStrategy(Layout.BREAK_STRATEGY_HIGH_QUALITY)
                        .setIndents(leftIndents, rightIndents)
                        .setJustificationMode(if (st.align == "justify") Layout.JUSTIFICATION_MODE_INTER_WORD else Layout.JUSTIFICATION_MODE_NONE).build()
                    if (bubble != null) y = max(y, floatBottom)
                    y = max(top, y + collapsed(previousMargin, (st.marginTop + (bubble?.marginTop ?: 0f)) * scale))
                    if (startsGroup) y = max(top, y + (group?.marginTop ?: 0f) * scale + groupTop)
                    val leftIndent = if (floatSide == "left") ceil(max(0f, floatEdge - blockLeft)).toInt() else 0
                    val rightIndent = if (floatSide == "right") ceil(max(0f, blockLeft + width - floatEdge)).toInt() else 0
                    if (y < floatBottom && width - leftIndent - rightIndent < config.fontPx * 2) y = floatBottom
                    var layout = shape()
                    if (y < floatBottom && leftIndent + rightIndent > 0) {
                        if (y + layout.getLineBottom(0) > bottom) {
                            flush(); y = top
                        } else {
                            fun coveredLines(shaped: StaticLayout): Int =
                                (0 until shaped.lineCount).takeWhile { y + shaped.getLineTop(it) < floatBottom }.size
                            var covered = coveredLines(layout)
                            for (attempt in 1..3) {
                                layout = shape(IntArray(covered + 1) { if (it < covered) leftIndent else 0 },
                                    IntArray(covered + 1) { if (it < covered) rightIndent else 0 })
                                val needed = coveredLines(layout)
                                if (needed == covered) break
                                covered = needed
                            }
                            require(coveredLines(layout) == covered) { "Unstable float line metrics" }
                        }
                    }
                    if (bubble != null || st.avoidBreak) {
                        val totalHeight = insetTop + layout.height + insetBottom
                        require(totalHeight <= bottom - top) { "Unbreakable block taller than page" }
                        if (y + totalHeight > bottom) { flush(); y = top }
                    }
                    y += insetTop
                    var first = 0
                    while (first < layout.lineCount) {
                        val lineHeight = layout.getLineBottom(first) - layout.getLineTop(first)
                        require(lineHeight + insetTop + insetBottom + groupTop + groupBottom <= bottom - top) { "Line taller than page" }
                        if (y + lineHeight + insetBottom + groupBottom > bottom) { flush(); y = top + insetTop + if (first == 0) groupTop else 0f }
                        var last = first + 1
                        while (last < layout.lineCount && y + layout.getLineBottom(last) - layout.getLineTop(first) + insetBottom + groupBottom <= bottom) last++
                        val fragmentHeight = layout.getLineBottom(last - 1) - layout.getLineTop(first)
                        block.group?.let {
                            val before = if (first == 0) groupTop + st.marginTop * scale else 0f
                            val after = if (last == layout.lineCount) groupBottom + st.marginBottom * scale else 0f
                            addGroupBounds(it, RectF(groupLeft, max(top, y - insetTop - before), groupRight,
                                min(bottom, y + fragmentHeight + insetBottom + after)))
                        }
                        val decoration = box(outline, RectF(blockLeft - insetLeft, y - insetTop,
                            blockLeft + width + insetRight, y + fragmentHeight + insetBottom))
                        items += NativeEpubHorizontalItem(block.start + layout.getLineStart(first), block.start + layout.getLineEnd(last - 1), layout,
                            first, last, blockLeft, y, null, adaptivePunctuation, decoration)
                        y += fragmentHeight + insetBottom + if (last == layout.lineCount) groupBottom else 0f
                        first = last
                        if (first < layout.lineCount) { flush(); y = top + insetTop }
                    }
                    previousMargin = config.paragraphSpacingPx?.takeIf { block.isParagraph }
                        ?: (st.marginBottom + (bubble?.marginBottom ?: 0f) +
                            if (block.group?.end == block.end) group?.marginBottom ?: 0f else 0f) * scale
                } else {
                    val st = block.style.let { original ->
                        if (!block.isParagraph) original else original.copy(
                            marginTop = if (config.paragraphSpacingPx != null) 0f else original.marginTop,
                            marginBottom = if (config.paragraphSpacingPx != null) 0f else original.marginBottom,
                            indent = config.firstLineIndentChars?.times(original.size) ?: original.indent
                        )
                    }
                    require(st.align == "start" && st.direction == "ltr" && st.marginLeft == 0f && st.marginRight == 0f && st.background == null) { "Unsupported vertical block decoration" }
                    val verticalRuns = runs.toList()
                    val em = max(st.size, verticalRuns.maxOfOrNull { it.style.size } ?: st.size) * scale
                    val hasRuby = rubies.any()
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
            return NativeEpubChapterLayout(document, pages, cfg, images, backdrop)
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
