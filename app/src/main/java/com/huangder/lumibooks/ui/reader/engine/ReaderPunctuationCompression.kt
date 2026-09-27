package com.huangder.lumibooks.ui.reader.engine

import android.graphics.Paint
import android.graphics.Rect
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextPaint
import android.text.style.MetricAffectingSpan
import android.text.style.ReplacementSpan

/**
 * 全角标点挤压：标点只占半个汉字宽，不再和汉字一样宽。
 *
 * 通过 [MetricAffectingSpan] 只修改"测量"结果，分页、翻页、选择手柄使用的字宽都会同步变小；
 * 可见字形由 [ReaderTextPainter] 用原字宽绘制，所以标点本身不会被横向压扁，
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

internal class ReaderPunctuationCompressionSpan(private val glyph: String) : MetricAffectingSpan() {
    override fun updateMeasureState(paint: TextPaint) {
        paint.isSubpixelText = true
        val spacing = paint.letterSpacing
        val ratio = readerPunctuationMetrics(paint, glyph, 0, glyph.length).scale
        paint.textScaleX *= ratio
        // Letter spacing is in em and is also multiplied by textScaleX.
        // Negative tracking is compensated in the slot, without changing the
        // paragraph's tracking rules (Minikin justification uses those rules).
        paint.letterSpacing = spacing / ratio
    }

    // TextLine uses draw state even for caret/selection measurements. Keep both
    // states identical; ReaderTextPainter excludes this span when painting ink.
    override fun updateDrawState(paint: TextPaint) = updateMeasureState(paint)
}

/** 字形墨迹相对绘制原点的位置与宽度。 */
internal data class ReaderGlyphInk(val left: Float, val width: Float) {
    val right: Float get() = left + width
}

/** measureText rounds up on Android; shaping advances must stay fractional. */
internal fun readerNaturalAdvance(paint: Paint, text: CharSequence, start: Int, end: Int): Float {
    val chars = CharArray(end - start) { text[start + it] }
    return paint.getTextRunAdvances(chars, 0, chars.size, 0, chars.size, false, null, 0)
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
internal fun readerCompressedSlotWidth(
    naturalAdvance: Float,
    inkWidth: Float,
    textSize: Float = 0f,
    letterSpacingPx: Float = 0f
): Float {
    val compressed = if (naturalAdvance.isFinite() && naturalAdvance > 0f) {
        naturalAdvance * READER_PUNCTUATION_COMPRESSION
    } else {
        0f
    }
    val ink = if (inkWidth.isFinite() && inkWidth > 0f) inkWidth else 0f
    if (compressed == 0f && ink == 0f) return 0f
    val bearing = maxOf(1f, textSize.takeIf { it.isFinite() }?.times(0.025f) ?: 0f)
    val negativeTracking = if (letterSpacingPx.isFinite()) maxOf(0f, -letterSpacingPx) else 0f
    return maxOf(compressed, ink + bearing * 2f) + negativeTracking * 2f
}

/** [readerCompressedSlotWidth] 的 Paint 版本：直接量取 [start, end) 的推进量与墨迹。 */
internal fun readerPunctuationSlotWidth(
    paint: Paint,
    text: CharSequence,
    start: Int,
    end: Int
): Float {
    return readerPunctuationMetrics(paint, text, start, end).advance
}

private data class PunctuationMetrics(val scale: Float, val advance: Float)

/** Use the actual shaped advance after scaling, including font hinting/rounding. */
private fun readerPunctuationMetrics(
    paint: Paint, text: CharSequence, start: Int, end: Int
): PunctuationMetrics {
    if (start >= end || start < 0 || end > text.length) return PunctuationMetrics(1f, 0f)
    val spacing = paint.letterSpacing
    val subpixel = paint.isSubpixelText
    val scale = paint.textScaleX
    val spacingPx = spacing * paint.textSize * paint.textScaleX
    paint.letterSpacing = 0f
    paint.isSubpixelText = true
    return try {
        val natural = readerNaturalAdvance(paint, text, start, end)
        if (!natural.isFinite() || natural <= 0f) return PunctuationMetrics(1f, 0f)
        val ink = readerGlyphInk(paint, text, start, end)
        val target = readerCompressedSlotWidth(natural, ink.width, paint.textSize, spacingPx)
        var ratio = target / natural
        paint.textScaleX = scale * ratio
        var advance = readerNaturalAdvance(paint, text, start, end)
        // Hinted fonts need not scale advances linearly. Keep the actual slot
        // large enough for the unscaled ink instead of correcting draw positions.
        repeat(3) {
            if (advance + 0.01f < target && advance > 0f) {
                ratio *= (target + 0.25f) / advance
                paint.textScaleX = scale * ratio
                advance = readerNaturalAdvance(paint, text, start, end)
            }
        }
        PunctuationMetrics(ratio, advance)
    } finally {
        paint.letterSpacing = spacing
        paint.isSubpixelText = subpixel
        paint.textScaleX = scale
    }
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
@Suppress("UNUSED_PARAMETER") // Retained for the chapter-loading API; both renderers now share spans.
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

    val result = SpannableStringBuilder(text)
    // Reapplication and mode switches keep exactly one measuring span per mark.
    result.getSpans(0, result.length, ReaderPunctuationCompressionSpan::class.java)
        .forEach(result::removeSpan)
    for (index in 0 until result.length) {
        if (isReaderCompressiblePunctuation(result[index]) &&
            result.getSpans(index, index + 1, ReplacementSpan::class.java).isEmpty()) {
            result.setSpan(
                ReaderPunctuationCompressionSpan(result[index].toString()),
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
