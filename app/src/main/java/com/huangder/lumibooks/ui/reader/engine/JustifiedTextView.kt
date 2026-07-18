package com.huangder.lumibooks.ui.reader.engine

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.text.Layout
import android.text.Selection
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.AbsoluteSizeSpan
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

    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 56f
        color = 0xFF333333.toInt()
    }

    private var spannable: Spannable? = null
    private var layout: StaticLayout? = null

    // ── 配置 ──
    private var gravity = Gravity.TOP

    fun setTextSize(px: Float) {
        textPaint.textSize = px
        defaultTextSize = px
        rebuildLayout()
    }

    fun setTextColor(color: Int) {
        textPaint.color = color
        invalidate()
    }

    fun setTypeface(tf: Typeface) {
        textPaint.typeface = tf
        rebuildLayout()
    }

    fun setLetterSpacing(ratio: Float) {
        textPaint.letterSpacing = ratio
        rebuildLayout()
    }

    fun setLineSpacing(addPx: Float, mult: Float) {
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
        val s = spannable
        if (s == null || s.isEmpty()) {
            layout = null
            return
        }
        val w = (width - paddingLeft - paddingRight).coerceAtLeast(1)
        layout = StaticLayout.Builder.obtain(s, 0, s.length, textPaint, w)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(lineSpacingExtra, lineSpacingMult)
            .setIncludePad(false)
            .setBreakStrategy(Layout.BREAK_STRATEGY_HIGH_QUALITY)
            .setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NONE)
            .build()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        rebuildLayout()
    }

    // ── 绘制 ──

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val sl = layout ?: return
        val s = spannable ?: return
        val textStr = s.toString()

        var skippedFFFC = 0  // 🔥 统计跳过的 U+FFFC 字符（图片加载失败）

        val saveCount = canvas.save()
        canvas.translate(paddingLeft.toFloat(), paddingTop.toFloat())

        val viewWidth = (width - paddingLeft - paddingRight).toFloat()

        for (i in 0 until sl.lineCount) {
            val lineStart = sl.getLineStart(i)
            val lineEnd = sl.getLineEnd(i)
            if (lineStart >= lineEnd) continue

            val lineTop = sl.getLineTop(i).toFloat()
            val baseline = sl.getLineBaseline(i).toFloat()

            // 高亮背景由底层的 TextView 绘制（文字颜色透明但 BackgroundColorSpan 仍可见）

            // 计算 LeadingMarginSpan 缩进
            var indentPx = 0f
            run {
                val spans = s.getSpans(lineStart, lineStart + 1, LeadingMarginSpan::class.java)
                for (span in spans) {
                    // LeadingMarginSpan.Standard(first, rest)：首行用 first，续行用 rest
                    val isFirstLineOfParagraph = (i == 0 || (lineStart > 0 && textStr[lineStart - 1] == '\n'))
                    indentPx = if (isFirstLineOfParagraph) {
                        // 需要获取 first 参数，但 LeadingMarginSpan 接口没有直接暴露
                        // 使用 getLeadingMargin(true) 获取首行缩进
                        span.getLeadingMargin(true).toFloat()
                    } else {
                        span.getLeadingMargin(false).toFloat()
                    }
                    break  // 只取第一个
                }
            }

            // 去掉末尾换行符的宽度
            val effectiveEnd = if (lineEnd > lineStart && textStr[lineEnd - 1] == '\n') lineEnd - 1 else lineEnd
            if (effectiveEnd <= lineStart) continue

            // 检查行内是否有 ImageSpan（图片行不做两端对齐）
            var hasImageInLine = false
            for (chk in lineStart until effectiveEnd) {
                if (s.getSpans(chk, chk + 1, android.text.style.ImageSpan::class.java).isNotEmpty()) {
                    hasImageInLine = true
                    break
                }
            }

            if (hasImageInLine) {
                // 图片行：逐 span 绘制 ImageSpan，跳过文字
                var x = indentPx
                var idx = lineStart
                while (idx < effectiveEnd) {
                    val imgSpans = s.getSpans(idx, idx + 1, android.text.style.ImageSpan::class.java)
                    if (imgSpans.isNotEmpty()) {
                        val drawable = imgSpans[0].drawable
                        if (drawable != null) {
                            val spanEnd = s.getSpanEnd(imgSpans[0])
                            // 使用 drawable 已有的 bounds（EpubImageGetter 按页面宽度缩放设置的）
                            val imgW = drawable.bounds.width().toFloat()
                            val imgH = drawable.bounds.height().toFloat()
                            val lineHeight = sl.getLineBottom(i) - sl.getLineTop(i)
                            val imgTop = lineTop + (lineHeight - imgH) / 2f
                            drawable.setBounds(x.toInt(), imgTop.toInt(), (x + imgW).toInt(), (imgTop + imgH).toInt())
                            drawable.draw(canvas)
                            x += imgW
                            idx = if (spanEnd > idx) spanEnd else idx + 1
                            continue
                        }
                    }
                    idx++
                }
                continue  // 跳过普通文字的逐字绘制
            }

            // 普通文字行：逐字绘制 + 两端对齐
            // 用 StaticLayout.getLineWidth 获取精确行宽（已考虑所有 span）
            val lineWidth = sl.getLineWidth(i)
            // 计算有效字符数（排除 U+FFFC 对象替换字符，这些是加载失败的图片占位符）
            val effectiveCharCount = (lineStart until effectiveEnd).count { textStr[it] != '￼' }
            val gapCount = if (effectiveCharCount > 1) effectiveCharCount - 1 else 0
            val availableWidth = viewWidth - indentPx
            val extraSpace = availableWidth - lineWidth
            val extraPerChar = if (extraSpace > 0f && gapCount > 0 && extraSpace < viewWidth * 0.15f) {
                extraSpace / gapCount
            } else {
                0f
            }

            // 逐字绘制（从缩进位置开始）
            var x = indentPx
            for (idx in lineStart until effectiveEnd) {
                // 跳过 U+FFFC（图片加载失败的占位字符，避免显示 "obj"）
                if (textStr[idx] == '￼') {
                    skippedFFFC++
                    continue
                }

                applySpanStyles(s, idx, textPaint)

                val charStr = textStr[idx].toString()
                canvas.drawText(charStr, x, baseline, textPaint)

                val charWidth = textPaint.measureText(charStr)
                x += charWidth + extraPerChar

                resetPaintStyle()
            }
        }

        if (skippedFFFC > 0) {
            android.util.Log.d("JustifiedTextView", "onDraw: skipped $skippedFFFC U+FFFC placeholder(s) — images failed to load")
        }

        canvas.restoreToCount(saveCount)
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
        textPaint.textSize = defaultTextSize
    }

    private var defaultTextColor = 0xFF333333.toInt()
    private var defaultTextSize = 56f

    fun setDefaultTextColor(color: Int) {
        defaultTextColor = color
        textPaint.color = color
    }

    // ── 文字选择支持 ──

    /**
     * 获取指定坐标处的字符偏移量
     */
    fun getOffsetForPosition(x: Float, y: Float): Int {
        val sl = layout ?: return 0
        val tx = x - paddingLeft
        val ty = y - paddingTop
        if (tx < 0 || ty < 0) return 0

        val line = sl.getLineForVertical(ty.toInt())
        return sl.getOffsetForHorizontal(line, tx).coerceIn(0, (spannable?.length ?: 1) - 1)
    }

    /**
     * 获取字符的视觉边界（用于选区高亮）
     */
    fun getCharBounds(offset: Int): android.graphics.RectF? {
        val sl = layout ?: return null
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
