package com.huangder.lumibooks.ui.reader.engine

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.text.Layout
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.BackgroundColorSpan
import android.text.style.ClickableSpan
import android.text.style.DynamicDrawableSpan
import android.text.style.ImageSpan
import android.text.style.URLSpan
import android.util.AttributeSet
import android.view.Gravity
import android.view.MotionEvent
import android.view.View

data class ReaderImageHit(
    val source: String,
    val leftPx: Float,
    val topPx: Float,
    val rightPx: Float,
    val bottomPx: Float,
    val naturalWidth: Int,
    val naturalHeight: Int,
    val link: String? = null,
    val hasAction: Boolean = false
)

/**
 * Hard line separators that Android's line breaker treats as mandatory breaks
 * (UAX #14 BK class). A line ending with any of them is the last line of its
 * paragraph, so it must never be stretched by justification.
 */
internal fun isReaderParagraphBreakChar(ch: Char): Boolean = when (ch) {
    '\n', '\r', '\u000B', '\u000C', '\u0085', '\u2028', '\u2029' -> true
    else -> false
}

/** True when the line's last character is a paragraph break. */
internal fun readerLineEndsParagraph(
    text: CharSequence,
    lineStart: Int,
    rawLineEnd: Int
): Boolean = rawLineEnd > lineStart && isReaderParagraphBreakChar(text[rawLineEnd - 1])

internal fun readerLineContentEnd(
    text: CharSequence,
    lineStart: Int,
    rawLineEnd: Int
): Int {
    var end = rawLineEnd
    if (end > lineStart && isReaderParagraphBreakChar(text[end - 1])) end--
    while (end > lineStart && (text[end - 1] == ' ' || text[end - 1] == '\t' || text[end - 1] == '\r' || text[end - 1] == '\u3000')) {
        end--
    }
    return end
}

/**
 * A line may be stretched only when it continues its paragraph.
 *
 * [pageEndsMidParagraph] forces the page's final line because the paragraph
 * keeps flowing on the next page; it must never override
 * [endsWithParagraphBreak], otherwise every paragraph-final line on such a page
 * would be stretched into unreadable letter spacing.
 */
internal fun shouldJustifyReaderLine(
    lineIndex: Int,
    lineCount: Int,
    endsWithParagraphBreak: Boolean,
    pageEndsMidParagraph: Boolean
): Boolean = !endsWithParagraphBreak &&
    (lineIndex < lineCount - 1 || pageEndsMidParagraph)

internal fun readerExplicitLetterSpacing(letterSpacingEm: Float, textSizePx: Float): Float =
    letterSpacingEm * textSizePx

internal fun readerHighlightCharacterEnd(
    x: Float,
    characterWidth: Float,
    hasFollowingCharacter: Boolean,
    letterSpacingPx: Float,
    justificationSpacingPx: Float
): Float = x + characterWidth + if (hasFollowingCharacter) {
    letterSpacingPx + justificationSpacingPx
} else {
    0f
}

/**
 * 中文两端对齐 TextView。
 *
 * 核心特性：
 * - 逐字绘制，行尾自动填充字间距，实现中文排版的两端对齐
 * - 支持 Spanned 文本（粗体、斜体、颜色、高亮等）
 * - 支持文字选择（通过 StaticLayout 偏移映射）
 * - 与 PageLayoutEngine 的 StaticLayout 分页保持一致
 */
class JustifiedTextView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    init {
        // 不消耗触摸事件，让事件穿透到 ReadView 处理翻页
        isClickable = false
        isFocusable = false
        isLongClickable = false
    }

    override fun onTouchEvent(event: MotionEvent): Boolean = false

    /**
     * 仅供测试观测：本 View 请求重绘的次数。
     *
     * 可见文字层是独立绘制的 View，颜色变化必须显式 invalidate()，否则硬件渲染会复用
     * 旧的显示列表（父级重绘不会重新录制本层），表现为「改完文字颜色要退出重进才生效」。
     */
    internal var redrawRequestCount = 0
        private set

    override fun invalidate() {
        redrawRequestCount++
        super.invalidate()
    }

    private val readerHighlightPainter = ReaderHighlightPainter(
        paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL },
        density = resources.displayMetrics.density
    )
    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 56f
        color = 0xFF333333.toInt()
        // URLSpan 按 linkColor 绘制，而 TextPaint 该字段默认 0（全透明）；不显式同步
        // 会让链接文字有位置、可点击却看不见。
        linkColor = color
        // dip 标记的 span（TXT 章首标题的 AbsoluteSizeSpan）按 paint.density 换算字号，
        // 而 TextPaint 的 density 默认是 1.0。本视图自己的 StaticLayout 是回退排版：
        // 选择层布局还没建好时（翻页槽位轮转后在同一帧里换了文本）绘制会落到它身上，
        // 那时标题会按 1/density 的字号排版、字形却仍按大字绘制，标题就挤成一团。
        density = readerSpanPaintDensity(resources.displayMetrics.density)
    }

    private var spannable: Spannable? = null
    private var layout: StaticLayout? = null

    /**
     * 建 layout 专用的画笔副本。
     *
     * StaticLayout 会保留这把画笔，并在 `getPrimaryHorizontal()` / `getLineRight()` 等查询时
     * 按它重新度量。若直接复用绘制画笔，坐标就会跟着「上一个字」的 span 状态漂移：绘制层
     * 按 span 逐字改字号（章首标题的 RelativeSizeSpan 把基准 65px 放大到 91px），布局坐标
     * 会在这基础上再放大一次（91×1.4＝127px），于是字形是 91px、位置却按 127px 排开，
     * 标题变成巨大字距。这里始终用基准字号的副本建 layout，绘制画笔怎么改都不影响坐标。
     */
    private val layoutPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)

    /** The selectable layer owns the canonical Layout; reuse it for visible glyphs. */
    private var sourceLayoutProvider: (() -> Layout?)? = null

    var readerJustificationMode: Int = Layout.JUSTIFICATION_MODE_INTER_CHARACTER
        set(value) {
            if (field == value) return
            field = value
            rebuildLayout()
            invalidate()
        }

    /**
     * 回退布局的断行策略。选择层（原生 TextView）按阅读对齐方式选 SIMPLE / HIGH_QUALITY，
     * 回退布局必须一致，否则回退帧的断行会和正式排版不同。
     */
    var readerBreakStrategy: Int = Layout.BREAK_STRATEGY_SIMPLE
        set(value) {
            if (field == value) return
            field = value
            rebuildLayout()
            invalidate()
        }

    fun setSourceLayoutProvider(provider: (() -> Layout?)?) {
        sourceLayoutProvider = provider
        invalidate()
    }

    private fun currentLayout(): Layout? {
        val supplied = sourceLayoutProvider?.invoke()
        val local = layout
        // A slot can receive new text before its selectable TextView has completed
        // the next layout pass. Never combine the new Spannable with the old
        // DynamicLayout: that makes glyph coordinates overlap until a later tap
        // happens to invalidate the page. The local StaticLayout is built
        // synchronously by the text setter and is the correct transient fallback.
        if (supplied != null && spannable != null && supplied.text === spannable) return supplied
        return local ?: supplied
    }

    /** TTS 褰撳墠鍙ラ珮浜壒ange锛?start, end, color锛夛紝鍦?onDraw 缁樺埗鏁翠綋鍦嗚搴?*/
    private var ttsHighlight: Triple<Int, Int, Int>? = null

    fun setTtsHighlight(start: Int, end: Int, color: Int) {
        if (ttsHighlight?.first == start && ttsHighlight?.second == end && ttsHighlight?.third == color) return
        ttsHighlight = Triple(start, end, color)
        invalidate()
    }

    fun clearTtsHighlight() {
        if (ttsHighlight == null) return
        ttsHighlight = null
        invalidate()
    }

    var justifyLastLine: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            invalidate()
        }

    // ── 配置 ──
    private var gravity = Gravity.TOP

    fun setTextSize(px: Float) {
        if (textPaint.textSize == px && defaultTextSize == px) return
        textPaint.textSize = px
        defaultTextSize = px
        rebuildLayout()
    }

    fun setTypeface(tf: Typeface) {
        if (textPaint.typeface === tf) return
        textPaint.typeface = tf
        rebuildLayout()
    }

    fun setFakeBold(enabled: Boolean) {
        if (textPaint.isFakeBoldText == enabled) return
        textPaint.isFakeBoldText = enabled
        rebuildLayout()
    }

    fun setLetterSpacing(ratio: Float) {
        if (textPaint.letterSpacing == ratio) return
        textPaint.letterSpacing = ratio
        rebuildLayout()
    }

    fun setLineSpacing(addPx: Float, mult: Float) {
        if (lineSpacingExtra == addPx && lineSpacingMult == mult) return
        lineSpacingExtra = addPx
        lineSpacingMult = mult
        rebuildLayout()
    }

    private var lineSpacingExtra = 0f
    private var lineSpacingMult = 1.5f

    // ── 文本设置 ──

    var text: CharSequence?
        get() = spannable
        set(value) {
            spannable = when (value) {
                is Spannable -> value
                null -> null
                else -> SpannableStringBuilder(value)
            }
            rebuildLayout()
            invalidate()
        }

    // ── StaticLayout 重建 ──

    private fun rebuildLayout() {
        // A selectable TextView may reuse its DynamicLayout after text/span
        // changes. Layout identity alone cannot validate cached glyph positions.
        val s = spannable
        if (s == null || s.isEmpty()) {
            layout = null
            return
        }
        // 绘制画笔回到基准字号：onDraw 会按 span 逐字改它，不能把上次的字号带进布局度量。
        textPaint.textSize = defaultTextSize
        // 显示大小 / 字体缩放变化后，dip span 必须按最新密度度量。
        textPaint.density = readerSpanPaintDensity(resources.displayMetrics.density)
        layoutPaint.set(textPaint)
        layoutPaint.textSize = defaultTextSize
        val w = (width - paddingLeft - paddingRight).coerceAtLeast(1)
        layout = StaticLayout.Builder.obtain(s, 0, s.length, layoutPaint, w)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(lineSpacingExtra, lineSpacingMult)
            .setIncludePad(false)
            .setBreakStrategy(readerBreakStrategy)
            .setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NONE)
            .setJustificationMode(readerJustificationMode)
            .build()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        rebuildLayout()
    }

    override fun setPadding(left: Int, top: Int, right: Int, bottom: Int) {
        if (left == paddingLeft && top == paddingTop && right == paddingRight && bottom == paddingBottom) {
            return
        }
        super.setPadding(left, top, right, bottom)
        rebuildLayout()
    }

    // ── 绘制 ──

    private fun drawWaveUnderlines(canvas: Canvas) {
        val s = spannable ?: return
        val sl = currentLayout() ?: return
        if (s.isEmpty() || sl.lineCount == 0) return
        val density = resources.displayMetrics.density
        val wavePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.8f * density
            strokeCap = Paint.Cap.ROUND
        }
        val save = canvas.save()
        canvas.translate(paddingLeft.toFloat(), paddingTop.toFloat())
        s.getSpans(0, s.length, WaveUnderlineSpan::class.java).forEach { span ->
            val spanStart = s.getSpanStart(span).coerceIn(0, s.length)
            val spanEnd = s.getSpanEnd(span).coerceIn(spanStart, s.length)
            if (spanStart >= spanEnd) return@forEach
            wavePaint.color = span.color
            val amplitude = 1.6f * density
            val wavelength = 5.5f * density
            val visible = readerVisibleLines(canvas, sl)
            if (visible.isEmpty()) return@forEach
            for (line in maxOf(visible.first, sl.getLineForOffset(spanStart))..
                minOf(visible.last, sl.getLineForOffset(spanEnd - 1))) {
                val layoutLineStart = sl.getLineStart(line)
                val rawLineEnd = sl.getLineEnd(line)
                val contentEnd = readerLineContentEnd(s, layoutLineStart, rawLineEnd)
                val lineStart = maxOf(spanStart, layoutLineStart)
                val lineEnd = minOf(spanEnd, contentEnd)
                if (lineStart >= lineEnd || !s.substring(lineStart, lineEnd).any { !it.isWhitespace() }) continue
                val range = justifiedHorizontalRange(sl, s, line, lineStart, lineEnd) ?: continue
                val x0 = range.first
                val x1 = range.second
                if (x1 <= x0) continue
                val baseline = sl.getLineBaseline(line).toFloat()
                val underlineCenter = baseline + textPaint.fontMetrics.descent.coerceAtLeast(1f) + 1f * density
                val path = android.graphics.Path()
                var x = x0
                var first = true
                while (x <= x1) {
                    val y = underlineCenter + amplitude * kotlin.math.sin((x - x0) / wavelength * 2.0 * Math.PI)
                    if (first) { path.moveTo(x, y.toFloat()); first = false } else { path.lineTo(x, y.toFloat()) }
                    x += 1f
                }
                canvas.drawPath(path, wavePaint)
            }
        }
        canvas.restoreToCount(save)
    }

    private fun drawTtsHighlightBackground(canvas: Canvas) {
        val tts = ttsHighlight ?: return
        val sl = currentLayout() ?: return
        val s = spannable ?: return
        if (s.isEmpty()) return
        val start = tts.first.coerceIn(0, s.length)
        val end = tts.second.coerceIn(start, s.length)
        if (start >= end) return
        val firstLine = sl.getLineForOffset(start)
        val lastLine = sl.getLineForOffset(end - 1)
        val density = resources.displayMetrics.density
        val gap = 1.5f * density
        val radius = 12f * density
        val top = sl.getLineTop(firstLine).toFloat() + gap
        val glyphBottom = sl.getLineBaseline(lastLine).toFloat() +
            sl.paint.fontMetrics.descent + 2f * density
        val bottom = minOf(glyphBottom, sl.getLineBottom(lastLine).toFloat() - gap)
        if (bottom <= top) return
        val save = canvas.save()
        canvas.translate(paddingLeft.toFloat(), paddingTop.toFloat())
        val oldColor = textPaint.color
        val oldStyle = textPaint.style
        textPaint.style = Paint.Style.FILL
        textPaint.color = tts.third
        canvas.drawRoundRect(
            android.graphics.RectF(0f, top, (width - paddingLeft - paddingRight).toFloat(), bottom),
            radius, radius, textPaint
        )
        textPaint.color = oldColor
        textPaint.style = oldStyle
        canvas.restoreToCount(save)
    }

    /** Maps offsets through the same per-character spacing used by [onDraw]. */
    private fun justifiedHorizontalRange(
        sl: Layout,
        text: Spannable,
        line: Int,
        segmentStart: Int,
        segmentEnd: Int
    ): Pair<Float, Float>? {
        val geometry = ReaderLineGeometry(
            layout = sl,
            text = text,
            justificationMode = readerJustificationMode,
            forceLastLineJustification = justifyLastLine
        )
        return geometry.horizontalRange(line, segmentStart, segmentEnd)?.let { it.left to it.right }
    }
    private val readerTextPainter = ReaderTextPainter()

    override fun onDraw(canvas: Canvas) {
        drawTtsHighlightBackground(canvas)
        drawWaveUnderlines(canvas)
        val sl = currentLayout() ?: return
        val s = spannable ?: return
        // The visible layer owns the glyph drawing. Calling TextView's draw
        // path here would paint the same characters once more when the
        // selectable layer is temporarily using a stale layout.
        val saveCount = canvas.save()
        canvas.translate(paddingLeft.toFloat(), paddingTop.toFloat())

        val geometry = ReaderLineGeometry(
            layout = sl,
            text = s,
            justificationMode = readerJustificationMode,
            forceLastLineJustification = justifyLastLine
        )
        // 高亮按行画圆角矩形（与选中态、已保存高亮同一套样式），
        // 逐字方块会在字与字之间留下缝隙、相邻行又贴在一起。
        drawHighlightBackgrounds(canvas, sl, s, geometry)

        readerTextPainter.draw(canvas, sl, s, geometry)

        canvas.restoreToCount(saveCount)
    }

    /**
     * 已保存高亮 / 搜索高亮 / 背景色 span：按行画圆角矩形，并绘制在文字下方。
     */
    private fun drawHighlightBackgrounds(
        canvas: Canvas,
        layout: Layout,
        text: Spanned,
        geometry: ReaderLineGeometry
    ) {
        text.getSpans(0, text.length, ReaderHighlightSpan::class.java).forEach { span ->
            readerHighlightPainter.drawRange(
                canvas = canvas,
                layout = layout,
                text = text,
                geometry = geometry,
                start = text.getSpanStart(span),
                end = text.getSpanEnd(span),
                color = span.color
            )
        }
        text.getSpans(0, text.length, ReaderSearchHighlightSpan::class.java).forEach { span ->
            readerHighlightPainter.drawRange(
                canvas = canvas,
                layout = layout,
                text = text,
                geometry = geometry,
                start = text.getSpanStart(span),
                end = text.getSpanEnd(span),
                color = span.color
            )
        }
        // 跨页选择期间由 ReadView 自持的瞬态选区，与已保存高亮同一套圆角样式。
        text.getSpans(0, text.length, ReaderSelectionHighlightSpan::class.java).forEach { span ->
            readerHighlightPainter.drawRange(
                canvas = canvas,
                layout = layout,
                text = text,
                geometry = geometry,
                start = text.getSpanStart(span),
                end = text.getSpanEnd(span),
                color = span.color
            )
        }
        text.getSpans(0, text.length, BackgroundColorSpan::class.java).forEach { span ->
            readerHighlightPainter.drawRange(
                canvas = canvas,
                layout = layout,
                text = text,
                geometry = geometry,
                start = text.getSpanStart(span),
                end = text.getSpanEnd(span),
                color = span.backgroundColor
            )
        }
    }

    /**
     * Returns an image hit using the exact coordinates used by [onDraw].
     * A URL/ClickableSpan overlapping the image is reported so callers can keep the
     * publisher action instead of opening the image preview.
     */
    fun getImageAtPosition(x: Float, y: Float): ReaderImageHit? {
        val sl = currentLayout() ?: return null
        val text = spannable ?: return null
        val tx = x - paddingLeft
        val ty = y - paddingTop
        if (tx < 0f || ty < 0f || ty >= sl.height) return null

        val line = sl.getLineForVertical(ty.toInt())
        val lineStart = sl.getLineStart(line)
        val lineEnd = readerLineContentEnd(text, lineStart, sl.getLineEnd(line))
        if (lineStart >= lineEnd) return null

        val geometry = ReaderLineGeometry(sl, text, readerJustificationMode, justifyLastLine)
        var index = lineStart
        while (index < lineEnd) {
            val image = text.getSpans(index, index + 1, ImageSpan::class.java).firstOrNull()
            if (image == null) {
                index++
                continue
            }
            val drawable = image.drawable
            val spanStart = text.getSpanStart(image).coerceAtLeast(index)
            val spanEnd = text.getSpanEnd(image).coerceAtLeast(spanStart + 1)
            val imageWidth = drawable.bounds.width().toFloat().coerceAtLeast(1f)
            val imageHeight = drawable.bounds.height().toFloat().coerceAtLeast(1f)
            val lineHeight = sl.getLineBottom(line) - sl.getLineTop(line)
            val cursorX = geometry.horizontalPosition(spanStart) ?: return null
            val imageTop = when (image.verticalAlignment) {
                DynamicDrawableSpan.ALIGN_BASELINE -> sl.getLineBaseline(line) - imageHeight
                DynamicDrawableSpan.ALIGN_CENTER -> sl.getLineTop(line) + (lineHeight - imageHeight) / 2f
                else -> sl.getLineBottom(line) - imageHeight
            }
            val imageBottom = imageTop + imageHeight
            if (tx in cursorX..(cursorX + imageWidth) && ty in imageTop..imageBottom) {
                val url = text.getSpans(spanStart, spanEnd, URLSpan::class.java)
                    .firstOrNull()?.url
                val hasClickableAction = text.getSpans(spanStart, spanEnd, ClickableSpan::class.java)
                    .isNotEmpty()
                return ReaderImageHit(
                    source = image.source.orEmpty(),
                    leftPx = paddingLeft + cursorX,
                    topPx = paddingTop + imageTop,
                    rightPx = paddingLeft + cursorX + imageWidth,
                    bottomPx = paddingTop + imageBottom,
                    naturalWidth = drawable.intrinsicWidth.coerceAtLeast(drawable.bounds.width()),
                    naturalHeight = drawable.intrinsicHeight.coerceAtLeast(drawable.bounds.height()),
                    link = url,
                    hasAction = hasClickableAction
                )
            }
            index = spanEnd
        }
        return null
    }

    /**
     * 按与 onDraw 相同的两端对齐坐标计算链接命中，避免使用普通 TextView
     * 的字符位置时，行内额外字距造成链接点击区域偏移。
     */
    fun getLinkAtPosition(x: Float, y: Float): String? {
        val sl = currentLayout() ?: return null
        val text = spannable ?: return null
        val tx = x - paddingLeft
        val ty = y - paddingTop
        if (tx < 0f || ty < 0f || ty >= sl.height) return null

        val line = sl.getLineForVertical(ty.toInt())
        val lineStart = sl.getLineStart(line)
        val rawLineEnd = sl.getLineEnd(line)
        val endsWithParagraphBreak = readerLineEndsParagraph(text, lineStart, rawLineEnd)
        val lineEnd = readerLineContentEnd(text, lineStart, rawLineEnd)
        if (lineStart >= lineEnd) return null

        val geometry = ReaderLineGeometry(sl, text, readerJustificationMode, justifyLastLine)
        for (index in lineStart until lineEnd) {
            val range = geometry.horizontalRange(line, index, index + 1) ?: continue
            if (tx >= range.left && tx <= range.right) {
                return text.getSpans(index, index + 1, URLSpan::class.java).firstOrNull()?.url
            }
        }
        return null
    }

    private var defaultTextColor = 0xFF333333.toInt()
    private var defaultTextSize = 56f

    /**
     * 设置正文默认颜色（同时也是 URLSpan 的链接颜色），并在颜色变化时请求重绘。
     *
     * 链接沿用正文色 + 下划线，与竖排 [VerticalTextView] 及原书排版的注入 CSS 一致。
     */
    fun setDefaultTextColor(color: Int) {
        val changed = defaultTextColor != color ||
            textPaint.color != color ||
            textPaint.linkColor != color
        defaultTextColor = color
        textPaint.color = color
        textPaint.linkColor = color
        layoutPaint.color = color
        layoutPaint.linkColor = color
        if (changed) invalidate()
    }

    // ── 文字选择支持 ──

    /**
     * 获取指定坐标处的字符偏移量
     */
    fun getOffsetForPosition(x: Float, y: Float): Int {
        val sl = currentLayout() ?: return 0
        val tx = x - paddingLeft
        val ty = y - paddingTop
        if (tx < 0 || ty < 0) return 0

        val line = sl.getLineForVertical(ty.toInt())
        val text = spannable ?: return 0
        return (ReaderLineGeometry(sl, text, readerJustificationMode, justifyLastLine)
            .offsetForHorizontal(line, tx) ?: 0).coerceIn(0, text.length)
    }

    fun getLineInfoForOffset(offset: Int): Pair<Int, Int>? {
        val sl = currentLayout() ?: return null
        val textLength = spannable?.length ?: return null
        if (textLength <= 0) return null
        val safeOffset = offset.coerceIn(0, textLength - 1)
        val line = sl.getLineForOffset(safeOffset)
        return line to sl.getLineStart(line)
    }

    /**
     * 获取字符的视觉边界（用于选区高亮）
     */
    fun getCharBounds(offset: Int): android.graphics.RectF? {
        val sl = currentLayout() ?: return null
        val s = spannable ?: return null
        if (offset < 0 || offset >= s.length) return null

        val line = sl.getLineForOffset(offset)
        val lineTop = sl.getLineTop(line).toFloat() + paddingTop
        val lineBottom = sl.getLineBottom(line).toFloat() + paddingTop

        val range = ReaderLineGeometry(sl, s, readerJustificationMode, justifyLastLine)
            .horizontalRange(line, offset, offset + 1) ?: return null
        return android.graphics.RectF(range.left + paddingLeft, lineTop, range.right + paddingLeft, lineBottom)
    }

    // ── 兼容 TextView 接口 ──

    val highlightColor: Int
        get() = 0x40007AFF.toInt()

    fun setTextIsSelectable(selectable: Boolean) {
        // JustifiedTextView 自身不处理选择，由上层 PageContentView 管理
    }
}
