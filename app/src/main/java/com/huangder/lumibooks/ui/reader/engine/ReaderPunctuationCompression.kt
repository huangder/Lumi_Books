package com.huangder.lumibooks.ui.reader.engine

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextPaint
import android.text.style.MetricAffectingSpan
import android.text.style.ReplacementSpan
import kotlin.math.roundToInt

/**
 * 全角标点挤压：标点只占半个汉字宽，不再和汉字一样宽。
 *
 * 通过 [MetricAffectingSpan] 只修改"测量"结果，分页、翻页、选择手柄使用的字宽都会同步变小；
 * 可见字形仍由 [JustifiedTextView] 用原字宽绘制，所以标点本身不会被横向压扁，
 * 只是占的位置变窄了。
 */
internal const val READER_PUNCTUATION_COMPRESSION = 0.5f

/** 参与挤压的全角标点：句读、闭合与开启的引号/括号。 */
internal val READER_COMPRESSIBLE_PUNCTUATION: Set<Char> = setOf(
    // 句读
    '、', '。', '，', '．', '：', '；', '！', '？',
    // 闭合引号、括号、书名号
    '」', '』', '】', '》', '〉', '〕', '〗', '〙', '〛', '）', '］', '｝', '”', '’',
    // 开启引号、括号、书名号
    '「', '『', '【', '《', '〈', '〔', '〖', '〘', '〚', '（', '［', '｛', '“', '‘'
)

internal class ReaderPunctuationCompressionSpan : MetricAffectingSpan() {
    override fun updateMeasureState(paint: TextPaint) {
        paint.textScaleX *= READER_PUNCTUATION_COMPRESSION
    }

    // 只改测量值：字形的绘制由阅读器自己负责，避免被横向压扁。
    override fun updateDrawState(paint: TextPaint) = Unit
}

/**
 * 供框架直接绘制文本的通道使用（如上下滚动模式的原生 TextView）。
 *
 * [getSize] 只占半个汉字宽（墨迹更宽时以墨迹为准，见 [readerCompressedSlotWidth]）；
 * [draw] 把原字形居中画进这个窄槽位，于是标点既不占满一个字，也不会被拉扁。
 *
 * 槽位必须覆盖墨迹：框架在行末按槽位预留推进量，而正文列右边缘就是平台的硬裁切线
 * （TextView.onDraw 会把画布裁到内容框），槽位窄于墨迹就会把标点在行尾切掉半截。
 */
internal class ReaderPunctuationReplacementSpan : ReplacementSpan() {
    override fun getSize(
        paint: Paint,
        text: CharSequence,
        start: Int,
        end: Int,
        fm: Paint.FontMetricsInt?
    ): Int {
        return readerPunctuationSlotWidth(paint, text, start, end).roundToInt().coerceAtLeast(1)
    }

    override fun draw(
        canvas: Canvas,
        text: CharSequence,
        start: Int,
        end: Int,
        x: Float,
        top: Int,
        y: Int,
        bottom: Int,
        paint: Paint
    ) {
        if (start >= end) return
        val glyph = text.subSequence(start, end).toString()
        val slotWidth = readerPunctuationSlotWidth(paint, text, start, end)
        val ink = readerGlyphInk(paint, glyph)
        val shift = readerPunctuationDrawShift(ink.left, ink.width, slotWidth)
        canvas.drawText(glyph, x + shift, y.toFloat(), paint)
    }
}

/** 字形墨迹相对绘制原点的位置与宽度。 */
internal data class ReaderGlyphInk(val left: Float, val width: Float) {
    val right: Float get() = left + width
}

/** 量取 [start, end) 区间字形的墨迹；无墨迹时返回零值。 */
internal fun readerGlyphInk(
    paint: Paint,
    text: CharSequence,
    start: Int = 0,
    end: Int = text.length
): ReaderGlyphInk {
    if (start >= end || start < 0 || end > text.length) return ReaderGlyphInk(0f, 0f)
    val bounds = Rect()
    paint.getTextBounds(text, start, end, bounds)
    if (bounds.isEmpty) return ReaderGlyphInk(0f, 0f)
    return ReaderGlyphInk(bounds.left.toFloat(), (bounds.right - bounds.left).toFloat())
}

/**
 * 被挤压标点的槽位宽度：常规全角标点取半个字宽，但槽位不得窄于字形墨迹。
 *
 * 比例标点（自定义字体里的 “ ” ‘ ’ 等）与满框字形，墨迹常常比半个字宽更宽；
 * 若仍按半字宽留槽位，框架绘制时字形会越过正文列右边缘，被平台在行末裁掉半截。
 */
internal fun readerCompressedSlotWidth(naturalAdvance: Float, inkWidth: Float): Float {
    val compressed = if (naturalAdvance.isFinite() && naturalAdvance > 0f) {
        naturalAdvance * READER_PUNCTUATION_COMPRESSION
    } else {
        0f
    }
    val ink = if (inkWidth.isFinite() && inkWidth > 0f) inkWidth else 0f
    return maxOf(compressed, ink).coerceAtLeast(0f)
}

/** [readerCompressedSlotWidth] 的 Paint 版本：直接量取 [start, end) 的推进量与墨迹。 */
internal fun readerPunctuationSlotWidth(
    paint: Paint,
    text: CharSequence,
    start: Int,
    end: Int
): Float {
    if (start >= end || start < 0 || end > text.length) return 0f
    val natural = paint.measureText(text, start, end)
    val ink = readerGlyphInk(paint, text, start, end)
    return readerCompressedSlotWidth(natural, ink.width)
}

/** 是否为需要挤压的全角标点。 */
internal fun isReaderCompressiblePunctuation(char: Char): Boolean =
    char in READER_COMPRESSIBLE_PUNCTUATION

/** 该位置的全角标点是否已按挤压处理。 */
internal fun readerIsCompressedPunctuation(
    text: CharSequence,
    index: Int,
    spanType: Class<out Any> = ReaderPunctuationCompressionSpan::class.java
): Boolean {
    if (index !in 0 until text.length) return false
    if (!isReaderCompressiblePunctuation(text[index])) return false
    if (text !is Spanned) return false
    return text.getSpans(index, index + 1, spanType).isNotEmpty()
}

/**
 * 给全角标点打上挤压 span。文本没有可挤压标点时原样返回，避免多余的对象重建。
 */
internal fun applyReaderPunctuationCompression(
    text: CharSequence,
    frameworkDrawsText: Boolean = false
): CharSequence {
    var hasPunctuation = false
    for (index in 0 until text.length) {
        if (isReaderCompressiblePunctuation(text[index])) {
            hasPunctuation = true
            break
        }
    }
    if (!hasPunctuation) return text

    val result = if (text is Spannable) {
        SpannableStringBuilder(text)
    } else {
        SpannableStringBuilder(text.toString())
    }
    // 两套 span 互斥：先清掉两种变体，避免已经把文本交给另一种绘制通道后
    // 再套用时留下「半字宽测量 + 整字宽绘制」的混用状态（行末标点会被裁掉半截）。
    result.getSpans(0, result.length, ReaderPunctuationReplacementSpan::class.java)
        .forEach(result::removeSpan)
    result.getSpans(0, result.length, ReaderPunctuationCompressionSpan::class.java)
        .forEach(result::removeSpan)
    for (index in 0 until result.length) {
        if (isReaderCompressiblePunctuation(result[index])) {
            result.setSpan(
                if (frameworkDrawsText) {
                    ReaderPunctuationReplacementSpan()
                } else {
                    ReaderPunctuationCompressionSpan()
                },
                index,
                index + 1,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
    }
    return result
}

/**
 * 挤压后的槽位只占半个汉字宽，而标点字形仍是原字宽：把字形水平挪一下，
 * 让墨迹在槽位里居中，这样标点左右两侧留出的空隙一样大，
 * 不会出现"左边正常、右边贴住下一个字"的观感。
 */
internal fun readerPunctuationDrawShift(
    paint: Paint,
    glyph: CharSequence,
    slotWidth: Float
): Float {
    val ink = readerGlyphInk(paint, glyph)
    return readerPunctuationDrawShift(ink.left, ink.width, slotWidth)
}

/**
 * [readerPunctuationDrawShift] 的纯计算部分：墨迹在槽位内居中所需的绘制原点位移。
 *
 * 槽位由 [readerCompressedSlotWidth] 保证不窄于墨迹，因此结果不会把墨迹推出槽位。
 */
internal fun readerPunctuationDrawShift(
    inkLeft: Float,
    inkWidth: Float,
    slotWidth: Float
): Float {
    if (!inkLeft.isFinite() || !inkWidth.isFinite() || inkWidth <= 0f) return 0f
    if (!slotWidth.isFinite() || slotWidth <= 0f) return 0f
    return (slotWidth - inkWidth) / 2f - inkLeft
}
