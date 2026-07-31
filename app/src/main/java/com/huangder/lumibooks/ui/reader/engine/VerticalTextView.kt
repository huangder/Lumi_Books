package com.huangder.lumibooks.ui.reader.engine

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Selection
import android.text.Spannable
import android.text.TextPaint
import android.text.style.AbsoluteSizeSpan
import android.text.style.BackgroundColorSpan
import android.text.style.ClickableSpan
import android.text.style.ForegroundColorSpan
import android.text.style.ImageSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.URLSpan
import android.text.style.UnderlineSpan
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.min

internal class VerticalTextView(context: Context) : View(context) {
    private val paint = TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG)
    private var defaultTextSize = 56f
    private var defaultTextColor = 0xFF333333.toInt()
    private var defaultTypeface: Typeface = Typeface.DEFAULT
    private var accentColor = 0xFF007AFF.toInt()
    private var text: Spannable? = null
    private var geometry: VerticalPageGeometry? = null
    private var chapterStartOffset: Int = 0
    private var draggingStartHandle = false
    private var draggingEndHandle = false

    fun isSelectionHandleDragActive(): Boolean = draggingStartHandle || draggingEndHandle

    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean = true

        override fun onLongPress(e: MotionEvent) {
            selectWordAt(e.x, e.y)
            parent?.requestDisallowInterceptTouchEvent(true)
        }
    })

    fun configure(textSize: Float, textColor: Int, typeface: Typeface, accentColor: Int) {
        defaultTextSize = textSize
        defaultTextColor = textColor
        defaultTypeface = typeface
        this.accentColor = accentColor
        resetPaint()
        invalidate()
    }

    fun setPage(text: Spannable?, geometry: VerticalPageGeometry?, chapterStartOffset: Int) {
        this.text = text
        this.geometry = geometry
        this.chapterStartOffset = chapterStartOffset
        invalidate()
    }

    fun clearPage() {
        text = null
        geometry = null
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val spannable = text ?: return
        val page = geometry ?: return
        val save = canvas.save()
        canvas.translate(paddingLeft.toFloat(), paddingTop.toFloat())

        drawHighlightColumns(canvas, spannable, page)
        page.items.forEach { item ->
            when (item) {
                is VerticalGlyphLayout -> drawGlyph(canvas, spannable, item)
                is VerticalImageLayout -> drawImage(canvas, spannable, item)
            }
        }
        drawSelection(canvas, spannable, page)
        canvas.restoreToCount(save)
    }

    private fun drawGlyph(canvas: Canvas, spannable: Spannable, glyph: VerticalGlyphLayout) {
        val localStart = glyph.startOffset - chapterStartOffset
        val localEnd = glyph.endOffset - chapterStartOffset
        if (localStart !in 0 until spannable.length || localEnd !in 1..spannable.length) return
        applyStyles(spannable, localStart)
        val bounds = glyph.bounds
        val source = spannable.subSequence(localStart, localEnd).toString()
        val display = verticalPresentationText(source)
        val centerX = bounds.centerX
        val centerY = bounds.centerY
        val baseline = centerY - (paint.ascent() + paint.descent()) / 2f
        if (glyph.rotateClockwise) {
            canvas.save()
            canvas.rotate(90f, centerX, centerY)
            canvas.drawText(display, centerX - paint.measureText(display) / 2f, baseline, paint)
            canvas.restore()
        } else {
            canvas.drawText(display, centerX - paint.measureText(display) / 2f, baseline, paint)
        }
        resetPaint()
    }

    private fun drawHighlightColumns(
        canvas: Canvas,
        spannable: Spannable,
        page: VerticalPageGeometry
    ) {
        data class Run(
            val columnIndex: Int,
            val color: Int,
            var lastOffset: Int,
            var left: Float,
            var top: Float,
            var right: Float,
            var bottom: Float
        )

        val runs = mutableListOf<Run>()
        page.glyphs.forEach { glyph ->
            val localStart = glyph.startOffset - chapterStartOffset
            val localEnd = glyph.endOffset - chapterStartOffset
            if (localStart !in 0 until spannable.length || localEnd !in 1..spannable.length) return@forEach
            val color = highlightColorAt(spannable, localStart, localEnd) ?: return@forEach
            if (color ushr 24 == 0) return@forEach
            val current = runs.lastOrNull()
            if (
                current != null && current.columnIndex == glyph.columnIndex &&
                current.color == color && current.lastOffset == glyph.startOffset
            ) {
                current.lastOffset = glyph.endOffset
                current.left = minOf(current.left, glyph.bounds.left)
                current.top = minOf(current.top, glyph.bounds.top)
                current.right = maxOf(current.right, glyph.bounds.right)
                current.bottom = maxOf(current.bottom, glyph.bounds.bottom)
            } else {
                runs += Run(
                    columnIndex = glyph.columnIndex,
                    color = color,
                    lastOffset = glyph.endOffset,
                    left = glyph.bounds.left,
                    top = glyph.bounds.top,
                    right = glyph.bounds.right,
                    bottom = glyph.bounds.bottom
                )
            }
        }

        val density = resources.displayMetrics.density
        val columnGap = 1.5f * density
        val inlinePadding = 3f * density
        val maxRadius = 6f * density
        val oldColor = paint.color
        runs.forEach { run ->
            val bounds = RectF(
                run.left + columnGap,
                (run.top - inlinePadding).coerceAtLeast(0f),
                run.right - columnGap,
                (run.bottom + inlinePadding).coerceAtMost(page.height)
            )
            if (bounds.width() <= 0f || bounds.height() <= 0f) return@forEach
            paint.color = run.color
            val radius = minOf(maxRadius, bounds.width() / 2f, bounds.height() / 2f)
            canvas.drawRoundRect(bounds, radius, radius, paint)
        }
        paint.color = oldColor
    }

    private fun highlightColorAt(spannable: Spannable, start: Int, end: Int): Int? =
        spannable.getSpans(start, end, ReaderSearchHighlightSpan::class.java)
            .lastOrNull()?.color
            ?: spannable.getSpans(start, end, ReaderHighlightSpan::class.java)
                .lastOrNull()?.color
            ?: spannable.getSpans(start, end, BackgroundColorSpan::class.java)
                .lastOrNull()?.backgroundColor

    private fun drawImage(canvas: Canvas, spannable: Spannable, image: VerticalImageLayout) {
        val localStart = (image.startOffset - chapterStartOffset).coerceIn(0, spannable.length)
        val localEnd = (image.endOffset - chapterStartOffset).coerceIn(localStart, spannable.length)
        val span = spannable.getSpans(localStart, localEnd, ImageSpan::class.java).firstOrNull() ?: return
        val drawable = span.drawable ?: return
        val target = image.bounds
        val sourceWidth = drawable.intrinsicWidth.takeIf { it > 0 } ?: drawable.bounds.width().coerceAtLeast(1)
        val sourceHeight = drawable.intrinsicHeight.takeIf { it > 0 } ?: drawable.bounds.height().coerceAtLeast(1)
        val scale = min((target.right - target.left) / sourceWidth, (target.bottom - target.top) / sourceHeight)
        val width = sourceWidth * scale
        val height = sourceHeight * scale
        val left = target.centerX - width / 2f
        val top = target.centerY - height / 2f
        val oldBounds = Rect(drawable.bounds)
        drawable.setBounds(left.toInt(), top.toInt(), (left + width).toInt(), (top + height).toInt())
        drawable.draw(canvas)
        drawable.bounds = oldBounds
    }

    private fun drawSelection(canvas: Canvas, spannable: Spannable, page: VerticalPageGeometry) {
        val start = Selection.getSelectionStart(spannable)
        val end = Selection.getSelectionEnd(spannable)
        if (start < 0 || end <= start) return
        val selected = page.glyphs.filter { glyph ->
            val localStart = glyph.startOffset - chapterStartOffset
            val localEnd = glyph.endOffset - chapterStartOffset
            localStart < end && localEnd > start
        }
        if (selected.isEmpty()) return
        val oldColor = paint.color
        paint.color = (accentColor and 0x00FFFFFF) or 0x38000000
        selected.forEach { canvas.drawRect(it.bounds.left, it.bounds.top, it.bounds.right, it.bounds.bottom, paint) }
        paint.color = accentColor
        val radius = resources.displayMetrics.density * 5f
        canvas.drawCircle(selected.first().bounds.centerX, selected.first().bounds.top, radius, paint)
        canvas.drawCircle(selected.last().bounds.centerX, selected.last().bounds.bottom, radius, paint)
        paint.color = oldColor
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val spannable = text ?: return false
        val selectedBounds = selectionBounds()
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val slop = resources.displayMetrics.density * 28f
                selectedBounds?.let { (first, last) ->
                    val startDistance = distance(
                        event.x,
                        event.y,
                        first.centerX + paddingLeft,
                        first.top + paddingTop
                    )
                    val endDistance = distance(
                        event.x,
                        event.y,
                        last.centerX + paddingLeft,
                        last.bottom + paddingTop
                    )
                    draggingStartHandle = startDistance <= slop && startDistance <= endDistance
                    draggingEndHandle = endDistance <= slop && endDistance < startDistance
                    if (draggingStartHandle || draggingEndHandle) parent?.requestDisallowInterceptTouchEvent(true)
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (draggingStartHandle || draggingEndHandle) {
                    val glyph = glyphAt(event.x, event.y) ?: return true
                    val localStart = glyph.startOffset - chapterStartOffset
                    val localEnd = glyph.endOffset - chapterStartOffset
                    val oldStart = Selection.getSelectionStart(spannable).coerceAtLeast(0)
                    val oldEnd = Selection.getSelectionEnd(spannable).coerceAtLeast(oldStart + 1)
                    if (draggingStartHandle) Selection.setSelection(spannable, min(localStart, oldEnd - 1), oldEnd)
                    if (draggingEndHandle) Selection.setSelection(spannable, oldStart, localEnd.coerceAtLeast(oldStart + 1))
                    invalidate()
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                draggingStartHandle = false
                draggingEndHandle = false
            }
        }
        gestureDetector.onTouchEvent(event)
        return true
    }

    fun getLinkAt(x: Float, y: Float): String? {
        val spannable = text ?: return null
        val glyph = glyphAt(x, y) ?: return null
        val start = (glyph.startOffset - chapterStartOffset).coerceIn(0, spannable.length)
        val end = (glyph.endOffset - chapterStartOffset).coerceIn(start, spannable.length)
        return spannable.getSpans(start, end, URLSpan::class.java).firstOrNull()?.url
    }

    fun getImageAt(x: Float, y: Float): ReaderImageHit? {
        val spannable = text ?: return null
        val image = geometry?.image ?: return null
        val localX = x - paddingLeft
        val localY = y - paddingTop
        if (!image.bounds.contains(localX, localY)) return null
        val start = (image.startOffset - chapterStartOffset).coerceIn(0, spannable.length)
        val end = (image.endOffset - chapterStartOffset).coerceIn(start, spannable.length)
        val span = spannable.getSpans(start, end, ImageSpan::class.java).firstOrNull() ?: return null
        val url = spannable.getSpans(start, end, URLSpan::class.java).firstOrNull()?.url
        val hasAction = spannable.getSpans(start, end, ClickableSpan::class.java).isNotEmpty()
        val drawable = span.drawable
        return ReaderImageHit(
            source = span.source.orEmpty(),
            leftPx = image.bounds.left + paddingLeft,
            topPx = image.bounds.top + paddingTop,
            rightPx = image.bounds.right + paddingLeft,
            bottomPx = image.bounds.bottom + paddingTop,
            naturalWidth = drawable?.intrinsicWidth?.coerceAtLeast(0) ?: 0,
            naturalHeight = drawable?.intrinsicHeight?.coerceAtLeast(0) ?: 0,
            link = url,
            hasAction = hasAction
        )
    }

    fun selectionScreenBounds(): Pair<VerticalRect, VerticalRect>? = selectionBounds()?.let { (first, last) ->
        first.copy(
            left = first.left + paddingLeft,
            right = first.right + paddingLeft,
            top = first.top + paddingTop,
            bottom = first.bottom + paddingTop
        ) to last.copy(
            left = last.left + paddingLeft,
            right = last.right + paddingLeft,
            top = last.top + paddingTop,
            bottom = last.bottom + paddingTop
        )
    }

    private fun selectWordAt(x: Float, y: Float) {
        val spannable = text ?: return
        val glyph = glyphAt(x, y) ?: return
        val offset = (glyph.startOffset - chapterStartOffset).coerceIn(0, spannable.length - 1)
        val value = spannable.toString()
        val c = value[offset]
        val cjk = c.code in 0x3400..0x4DBF || c.code in 0x4E00..0x9FFF
        var start = offset
        var end = (glyph.endOffset - chapterStartOffset).coerceAtMost(value.length)
        if (cjk) {
            start = (start - 2).coerceAtLeast(0)
            end = (end + 2).coerceAtMost(value.length)
        } else {
            fun separator(char: Char): Boolean = char.isWhitespace() || (!char.isLetterOrDigit() && char != '\'' && char != '-')
            while (start > 0 && !separator(value[start - 1])) start--
            while (end < value.length && !separator(value[end])) end++
        }
        if (end > start) {
            Selection.setSelection(spannable, start, end)
            invalidate()
        }
    }

    private fun glyphAt(x: Float, y: Float): VerticalGlyphLayout? {
        val localX = x - paddingLeft
        val localY = y - paddingTop
        val glyphs = geometry?.glyphs.orEmpty()
        return glyphs.firstOrNull { it.bounds.contains(localX, localY) }
            ?: glyphs.minByOrNull { abs(it.bounds.centerX - localX) + abs(it.bounds.centerY - localY) }
    }

    private fun selectionBounds(): Pair<VerticalRect, VerticalRect>? {
        val spannable = text ?: return null
        val start = Selection.getSelectionStart(spannable)
        val end = Selection.getSelectionEnd(spannable)
        if (start < 0 || end <= start) return null
        val selected = geometry?.glyphs.orEmpty().filter {
            val localStart = it.startOffset - chapterStartOffset
            val localEnd = it.endOffset - chapterStartOffset
            localStart < end && localEnd > start
        }
        if (selected.isEmpty()) return null
        return selected.first().bounds to selected.last().bounds
    }

    private fun applyStyles(spannable: Spannable, offset: Int) {
        resetPaint()
        spannable.getSpans(offset, (offset + 1).coerceAtMost(spannable.length), Any::class.java).forEach { span ->
            when (span) {
                is StyleSpan -> when (span.style) {
                    Typeface.BOLD -> paint.isFakeBoldText = true
                    Typeface.ITALIC -> paint.textSkewX = -0.25f
                    Typeface.BOLD_ITALIC -> {
                        paint.isFakeBoldText = true
                        paint.textSkewX = -0.25f
                    }
                }
                is ForegroundColorSpan -> paint.color = span.foregroundColor
                is URLSpan -> {
                    paint.color = paint.linkColor
                    paint.isUnderlineText = true
                }
                is UnderlineSpan -> paint.isUnderlineText = true
                is StrikethroughSpan -> paint.isStrikeThruText = true
                is AbsoluteSizeSpan -> paint.textSize = if (span.dip) span.size * resources.displayMetrics.density else span.size.toFloat()
                is RelativeSizeSpan -> paint.textSize *= span.sizeChange
            }
        }
    }

    private fun resetPaint() {
        paint.textSize = defaultTextSize
        paint.color = defaultTextColor
        paint.linkColor = defaultTextColor
        paint.typeface = defaultTypeface
        paint.isFakeBoldText = false
        paint.textSkewX = 0f
        paint.isUnderlineText = false
        paint.isStrikeThruText = false
    }

    private fun distance(x1: Float, y1: Float, x2: Float, y2: Float): Float =
        kotlin.math.hypot(x1 - x2, y1 - y2)
}
