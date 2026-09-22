package com.huangder.lumibooks.ui.reader.engine

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.text.Layout
import android.text.Selection
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.AbsoluteSizeSpan
import android.text.style.BackgroundColorSpan
import android.text.style.ClickableSpan
import android.text.style.DynamicDrawableSpan
import android.text.style.ImageSpan
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.text.style.URLSpan
import android.text.style.UnderlineSpan
import android.text.style.StrikethroughSpan
import android.text.style.LeadingMarginSpan
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import com.huangder.lumibooks.domain.model.ReaderTextAlignment
import com.huangder.lumibooks.util.parser.InlineFootnoteMarkerDrawable
import com.huangder.lumibooks.util.parser.ReaderImageSizing

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
    /**
     * 逐字坐标缓存：一次绘制会为高亮、选区、手柄多次索取同一行的坐标，
     * 每帧重新度量整行会在连续翻页时明显掉帧。
     */
    private val lineOffsetsCache = HashMap<Int, ReaderLineOffsets?>()
    private var lineOffsetsCacheLayout: Layout? = null

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
            lineOffsetsCache.clear()
            lineOffsetsCacheLayout = null
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
        lineOffsetsCache.clear()
        lineOffsetsCacheLayout = null
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
            for (line in sl.getLineForOffset(spanStart)..sl.getLineForOffset(spanEnd - 1)) {
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
        val bottom = sl.getLineBottom(lastLine).toFloat() - gap
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
    override fun onDraw(canvas: Canvas) {
        drawTtsHighlightBackground(canvas)
        drawWaveUnderlines(canvas)
        val sl = currentLayout() ?: return
        val s = spannable ?: return
        // The visible layer owns the glyph drawing. Calling TextView's draw
        // path here would paint the same characters once more when the
        // selectable layer is temporarily using a stale layout.
        val textStr = s.toString()

        var skippedFFFC = 0  // 🔥 统计跳过的 U+FFFC 字符（图片加载失败）

        val saveCount = canvas.save()
        canvas.translate(paddingLeft.toFloat(), paddingTop.toFloat())

        val viewWidth = (width - paddingLeft - paddingRight).toFloat()
        val geometry = ReaderLineGeometry(
            layout = sl,
            text = s,
            justificationMode = readerJustificationMode,
            forceLastLineJustification = justifyLastLine
        )
        // 高亮按行画圆角矩形（与选中态、已保存高亮同一套样式），
        // 逐字方块会在字与字之间留下缝隙、相邻行又贴在一起。
        drawHighlightBackgrounds(canvas, sl, s, geometry)

        for (i in 0 until sl.lineCount) {
            val lineStart = sl.getLineStart(i)
            val lineEnd = sl.getLineEnd(i)
            if (lineStart >= lineEnd) continue

            val lineTop = sl.getLineTop(i).toFloat()
            val baseline = sl.getLineBaseline(i).toFloat()

            // 计算 LeadingMarginSpan 缩进
            var indentPx = 0f
            run {
            val spans = s.getSpans(lineStart, lineStart + 1, LeadingMarginSpan::class.java)
            val isFirstLineOfParagraph = i == 0 ||
                (lineStart > 0 && textStr[lineStart - 1] == '\n')
            for (span in spans) {
                indentPx += span.getLeadingMargin(isFirstLineOfParagraph).toFloat()
            }
            if (spans.isNotEmpty() || isFirstLineOfParagraph) {
                android.util.Log.d(
                    "JustifiedDebug",
                    "line=$i start=$lineStart end=$lineEnd indent=$indentPx first=$isFirstLineOfParagraph " +
                        "spans=${spans.size} viewW=${width - paddingLeft - paddingRight} slW=${sl.getLineWidth(i)}"
                )
            }
        }

            // 换行点前的空格只参与语义，不参与可见字符的两端对齐。
            val effectiveEnd = readerLineContentEnd(textStr, lineStart, lineEnd)
            if (effectiveEnd <= lineStart) continue

            // 含被挤压全角标点的行由阅读器自己算逐字坐标：部分厂商 Layout 会把行末
            // 两端对齐多出来的空隙算进行末字符边界里（实测行末「马」被报成 845→916，
            // 而行宽只有 854），直接采用会出现行尾溢出或首尾重叠。其余行维持原样。
            val lineOffsets = computeCompressedLineOffsets(
                line = i,
                layout = sl,
                geometry = geometry,
                lineStart = lineStart,
                contentEnd = effectiveEnd
            )
            // 本行正文右缘。自定义字体的比例标点墨迹可能比挤压槽位更宽，居中后会在
            // 行末越过正文列右边缘（被平台裁掉半截），这里以行右缘作为绘制钳制上限。
            val lineContentRight = geometry.lineRange(i)?.right

            for (idx in lineStart until effectiveEnd) {
                // 行内图片：按排版坐标绘制，和触摸命中（PageContentView.getImageAt）保持同一套
                // 几何。旧的“整行图片从 x=0 排列并跳过该行文字”会让脚注/注释小图标全部贴左边距，
                // 同一行的文字整行消失，而且点按看到图标的位置命不中链接。
                if (textStr[idx] == '￼') {
                    val imageSpan = s.getSpans(idx, idx + 1, android.text.style.ImageSpan::class.java)
                        .firstOrNull()
                    val drawable = imageSpan?.drawable
                    val spanStart = imageSpan?.let { s.getSpanStart(it) }
                    val imageLeft = geometry.horizontalPosition(idx)
                        ?.takeIf { it.isFinite() }
                        ?: runCatching { sl.getPrimaryHorizontal(idx) }.getOrNull()
                    // 一个 ImageSpan 可能覆盖多个占位字符，只在 span 起点绘制一次。
                    if (imageSpan == null || drawable == null || spanStart != idx ||
                        imageLeft == null || !imageLeft.isFinite()
                    ) {
                        skippedFFFC++
                        continue
                    }
                    // 使用 drawable 已有的 bounds（EpubImageGetter 按页面宽度缩放设置的）
                    val slotW = drawable.bounds.width().toFloat()
                    val slotH = drawable.bounds.height().toFloat()
                    // 注释引用图标：按正文字号缩放（原图常是 72px 大图），在排版槽位内居中绘制
                    val isMarker = (drawable as? InlineFootnoteMarkerDrawable)
                        ?.isInlineFootnoteMarker == true
                    val lineBottom = sl.getLineBottom(i).toFloat()
                    val slotTop = when (imageSpan.verticalAlignment) {
                        DynamicDrawableSpan.ALIGN_BASELINE -> baseline - slotH
                        DynamicDrawableSpan.ALIGN_CENTER ->
                            lineTop + (lineBottom - lineTop - slotH) / 2f
                        else -> lineBottom - slotH
                    }
                    // 等比绘制：槽位已按图片原始宽高比算好，再平方化就会把横图纵向拉伸。
                    val imageRect = ReaderImageSizing.drawRect(
                        slotLeft = imageLeft,
                        slotTop = slotTop,
                        slotWidth = slotW,
                        slotHeight = slotH,
                        isInlineMarker = isMarker,
                        markerSizePx = defaultTextSize * ReaderImageSizing.INLINE_MARKER_EM
                    )
                    // 🔥 保存原始 bounds，绘制后恢复。防止屏幕坐标污染 StaticLayout 行高计算
                    val savedBounds = Rect(drawable.bounds)
                    drawable.setBounds(
                        imageRect.left.toInt(),
                        imageRect.top.toInt(),
                        (imageRect.left + imageRect.width).toInt(),
                        (imageRect.top + imageRect.height).toInt()
                    )
                    drawable.draw(canvas)
                    drawable.bounds = savedBounds
                    continue
                }

                applySpanStyles(s, idx, textPaint)

                val charStr = textStr[idx].toString()
                val charRange = geometry.horizontalRange(i, idx, idx + 1)
                    ?: continue
                val slotWidth = charRange.right - charRange.left
                val naturalAdvance = textPaint.measureText(charStr)
                // 槽位一律用字体实际字宽推算：厂商 Layout 给出的字符边界在行末不可信，
                // 用它做居中会把标点推进旁边的字里。
                val compressed = readerIsCompressedPunctuation(s, idx)
                val drawnSlot = if (compressed) {
                    naturalAdvance * READER_PUNCTUATION_COMPRESSION
                } else {
                    naturalAdvance
                }
                val usableSlot = drawnSlot.takeIf { it.isFinite() && it > 0.01f } ?: slotWidth
                // 标点被挤压成半宽槽位，但字形仍是原字宽：把字形挪回槽位内居中。
                val compressionShift = if (compressed) {
                    readerPunctuationDrawShift(textPaint, charStr, usableSlot)
                } else {
                    0f
                }
                val relaidOut = lineOffsets?.lefts?.getOrNull(idx - lineStart)
                var x = (relaidOut ?: charRange.left) + compressionShift
                if (compressed && lineContentRight != null && lineContentRight.isFinite()) {
                    // 只夹绘制位置：排版槽位、逐字坐标与选区/手柄几何都保持原样。
                    val inkRight = readerGlyphInk(textPaint, charStr).right
                    val maxX = lineContentRight - inkRight
                    if (x > maxX) x = maxX
                }
                canvas.drawText(charStr, x, baseline, textPaint)

                resetPaintStyle()
            }
        }

        if (skippedFFFC > 0) {
            android.util.Log.d("JustifiedTextView", "onDraw: skipped $skippedFFFC U+FFFC placeholder(s) — images failed to load")
        }

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
        // 高亮必须和文字用同一份逐字坐标（含挤压标点的重排），否则会和字形差几个像素。
        val offsets: (Int, Int, Int) -> ReaderLineOffsets? = { line, lineStart, contentEnd ->
            computeCompressedLineOffsets(line, layout, geometry, lineStart, contentEnd)
        }
        text.getSpans(0, text.length, ReaderHighlightSpan::class.java).forEach { span ->
            readerHighlightPainter.drawRange(
                canvas = canvas,
                layout = layout,
                text = text,
                geometry = geometry,
                start = text.getSpanStart(span),
                end = text.getSpanEnd(span),
                color = span.color,
                lineOffsets = offsets
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
                color = span.color,
                lineOffsets = offsets
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
                color = span.color,
                lineOffsets = offsets
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
                color = span.backgroundColor,
                lineOffsets = offsets
            )
        }
    }

    /**
     * 含被挤压全角标点的行：按字体实际字宽重新计算每个字的左边界。
     *
     * 两端对齐时，行内多余的空隙按"字与字之间"平均分配，整行正好落在
     * 行首与行右边缘之间；这样既不会越过右边距，也不会出现两个字重叠。
     * 行内字符无法逐个度量（例如图片占位）时返回 null，交回原有坐标。
     */
    private fun computeCompressedLineOffsets(
        line: Int,
        layout: Layout,
        geometry: ReaderLineGeometry,
        lineStart: Int,
        contentEnd: Int
    ): ReaderLineOffsets? {
        val text = spannable ?: return null
        if (lineOffsetsCacheLayout !== layout) {
            lineOffsetsCache.clear()
            lineOffsetsCacheLayout = layout
        }
        lineOffsetsCache[line]?.let { return it }
        val letterSpacingPx = readerExplicitLetterSpacing(textPaint.letterSpacing, defaultTextSize)
            .takeIf { it.isFinite() }
            ?: 0f
        val computed = readerLineOffsets(
            layout = layout,
            text = text,
            line = line,
            lineStart = lineStart,
            contentEnd = contentEnd,
            justificationMode = readerJustificationMode,
            forceLastLineJustification = justifyLastLine,
            letterSpacingPx = letterSpacingPx
        ) { index ->
            applySpanStyles(text, index, textPaint)
            // 用 CharSequence 重载避免逐字创建 String（连续翻页时的分配热点）。
            val advance = textPaint.measureText(text, index, index + 1)
            resetPaintStyle()
            advance
        }
        lineOffsetsCache[line] = computed
        return computed
    }

    /**
     * 根据 Spanned 中的 span 应用文字样式
     */
    private fun applySpanStyles(text: Spannable, index: Int, paint: TextPaint) {
        val spans = text.getSpans(index, index + 1, Any::class.java)
        for (span in spans) {
            when (span) {
                is StyleSpan -> {
                    when (span.style) {
                        Typeface.BOLD -> paint.isFakeBoldText = true
                        Typeface.ITALIC -> paint.textSkewX = -0.25f
                        Typeface.BOLD_ITALIC -> {
                            paint.isFakeBoldText = true
                            paint.textSkewX = -0.25f
                        }
                    }
                }
                is ForegroundColorSpan -> paint.color = span.foregroundColor
                is URLSpan -> {
                    paint.color = paint.linkColor
                    paint.isUnderlineText = true
                }
                is UnderlineSpan -> paint.isUnderlineText = true
                is StrikethroughSpan -> paint.isStrikeThruText = true
                is AbsoluteSizeSpan -> {
                    paint.textSize = if (span.dip) {
                        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, span.size.toFloat(), resources.displayMetrics)
                    } else {
                        span.size.toFloat()
                    }
                }
                is RelativeSizeSpan -> {
                    paint.textSize *= span.sizeChange
                }
            }
        }
    }

    /**
     * 重置 Paint 为默认样式
     */
    private fun resetPaintStyle() {
        textPaint.isFakeBoldText = false
        textPaint.textSkewX = 0f
        textPaint.isUnderlineText = false
        textPaint.isStrikeThruText = false
        textPaint.color = defaultTextColor
        textPaint.linkColor = defaultTextColor
        textPaint.textSize = defaultTextSize
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

        var cursorX = 0f
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
            val imageTop = sl.getLineTop(line) + (lineHeight - imageHeight) / 2f
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
            cursorX += imageWidth
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

        val isFirstLine = line == 0 || (lineStart > 0 && text[lineStart - 1] == '\n')
        val indentPx = text.getSpans(lineStart, lineStart + 1, LeadingMarginSpan::class.java)
            .sumOf { span -> span.getLeadingMargin(isFirstLine).toDouble() }
            .toFloat()

        val contentWidth = (width - paddingLeft - paddingRight).toFloat()
        val visibleCharacterCount = (lineStart until lineEnd).count { text[it] != '￼' }
        val gapCount = (visibleCharacterCount - 1).coerceAtLeast(0)
        val trailingWhitespaceWidth = if (lineEnd < rawLineEnd) {
            textPaint.measureText(text, lineEnd, rawLineEnd)
        } else {
            0f
        }
        val lineWidth = (sl.getLineWidth(line) - trailingWhitespaceWidth).coerceAtLeast(0f)
        val extraSpace = contentWidth - indentPx - lineWidth
        val shouldJustify = shouldJustifyReaderLine(
            lineIndex = line,
            lineCount = sl.lineCount,
            endsWithParagraphBreak = endsWithParagraphBreak,
            pageEndsMidParagraph = justifyLastLine
        )
        val extraPerCharacter = if (shouldJustify && extraSpace > 0f && gapCount > 0) {
            extraSpace / gapCount
        } else {
            0f
        }

        var cursorX = indentPx
        var visibleCharacterIndex = 0
        for (index in lineStart until lineEnd) {
            if (text[index] == '￼') continue
            applySpanStyles(text, index, textPaint)
            val characterWidth = textPaint.measureText(text, index, index + 1)
            val hasFollowingCharacter = visibleCharacterIndex < visibleCharacterCount - 1
            val hitEnd = cursorX + characterWidth + if (hasFollowingCharacter) {
                readerExplicitLetterSpacing(textPaint.letterSpacing, textPaint.textSize) + extraPerCharacter
            } else {
                0f
            }
            val link = if (tx >= cursorX && tx <= hitEnd) {
                text.getSpans(index, index + 1, URLSpan::class.java).firstOrNull()?.url
            } else {
                null
            }
            resetPaintStyle()
            if (link != null) return link
            cursorX = hitEnd
            visibleCharacterIndex++
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
        return sl.getOffsetForHorizontal(line, tx).coerceIn(0, (spannable?.length ?: 1) - 1)
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

        // 计算字符的水平位置
        val lineStart = sl.getLineStart(line)
        val textStr = s.toString()
        var x = paddingLeft.toFloat()
        for (idx in lineStart until offset) {
            x += textPaint.measureText(textStr, idx, idx + 1)
        }
        val charWidth = textPaint.measureText(textStr, offset, offset + 1)

        return android.graphics.RectF(x, lineTop, x + charWidth, lineBottom)
    }

    // ── 兼容 TextView 接口 ──

    val highlightColor: Int
        get() = 0x40007AFF.toInt()

    fun setTextIsSelectable(selectable: Boolean) {
        // JustifiedTextView 自身不处理选择，由上层 PageContentView 管理
    }
}
