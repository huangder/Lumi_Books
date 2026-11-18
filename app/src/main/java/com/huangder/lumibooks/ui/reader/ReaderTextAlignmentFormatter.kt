package com.huangder.lumibooks.ui.reader

import android.os.Build
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.AlignmentSpan
import com.huangder.lumibooks.domain.model.ReaderTextAlignment
import com.huangder.lumibooks.util.parser.isChapterTitleParagraph
import com.huangder.lumibooks.util.parser.isHeadingParagraph
import com.huangder.lumibooks.util.parser.normalizeHeadingText

/**
 * 章首标题段落的标记 span，只在阅读器内部使用，没有任何绘制效果。
 * 可见文字层需要知道哪些行属于标题。标记放在文本 span 上而不是当作额外参数传递，
 * 分页切片、简繁转换、标点挤压和翻页槽位轮转都会连同 span 一起复制。
 */
internal class ReaderChapterTitleSpan

internal class ReaderNaturalAlignmentSpan

internal fun ReaderTextAlignment.usesFullLineJustification(): Boolean =
    this == ReaderTextAlignment.JUSTIFY

@Suppress("UNUSED_PARAMETER") // The custom painter supports character spacing on every SDK.
internal fun ReaderTextAlignment.readerJustificationMode(
    sdkInt: Int = Build.VERSION.SDK_INT
): Int = when {
    !usesFullLineJustification() -> Layout.JUSTIFICATION_MODE_NONE
    else -> Layout.JUSTIFICATION_MODE_INTER_CHARACTER
}

internal fun ReaderTextAlignment.readerBreakStrategy(): Int = Layout.BREAK_STRATEGY_SIMPLE

/**
 * 本章开头第一个标题段落的结束偏移（含段落换行）；本章没有可识别的标题时返回 0。
 *
 * 判定与 [com.huangder.lumibooks.util.parser.EpubParser] / MobiParser 的「标题不参与
 * 首行缩进」共用同一套规则：段落首字符同时带放大与加粗 span（`Html.fromHtml` 对
 * `<h1>`–`<h6>` 的转换结果，EPUB/MOBI 重排），或段落文本就等于解析出的章节名
 * （CSS 类排版的标题，也覆盖 TXT 合成标题）。开头空段与纯图片段（封面等）跳过。
 */
internal fun chapterTitleParagraphEnd(text: CharSequence, chapterTitle: String?): Int {
    val spanned = text as? Spanned ?: return 0
    if (spanned.isEmpty()) return 0
    val normalizedChapterTitle = normalizeHeadingText(chapterTitle.orEmpty())

    var paragraphStart = 0
    while (paragraphStart < spanned.length) {
        val breakIndex = indexOfParagraphBreak(spanned, paragraphStart)
        val paragraphEnd = if (breakIndex < 0) spanned.length else breakIndex
        var contentStart = paragraphStart
        while (contentStart < paragraphEnd && isParagraphFiller(spanned[contentStart])) contentStart++

        if (contentStart < paragraphEnd) {
            val isTitle = isHeadingParagraph(spanned, contentStart, paragraphEnd) ||
                isChapterTitleParagraph(
                    spanned.subSequence(contentStart, paragraphEnd),
                    normalizedChapterTitle
                )
            // 第一个有文字的段落决定本章有没有标题；不是标题就保持正文原样。
            if (!isTitle) return 0
            return when {
                breakIndex < 0 -> spanned.length
                spanned[breakIndex] == '\r' && spanned.getOrNull(breakIndex + 1) == '\n' -> breakIndex + 2
                else -> breakIndex + 1
            }
        }
        // 开头没有可见文字的段落（HTML 常见的占位空行、封面页的纯图片段）跳过，
        // 继续找后面真正作为标题的段落。
        paragraphStart = paragraphEnd + 1
    }
    return 0
}

private fun indexOfParagraphBreak(text: CharSequence, start: Int): Int {
    for (index in start until text.length) {
        val ch = text[index]
        if (ch == '\n' || ch == '\r') return index
    }
    return -1
}

/** 段落里的空白与图片占位（U+FFFC）不构成文字内容：封面图段落不该被当成标题。 */
private fun isParagraphFiller(ch: Char): Boolean = ch.isWhitespace() || ch == '\uFFFC'

/**
 * 保留章首标题的原书对齐，只在后续正文覆盖用户指定的对齐方式。
 */
internal fun applyReaderTextAlignment(
    text: CharSequence,
    alignment: ReaderTextAlignment,
    titleParagraphEnd: Int = 0
): CharSequence {
    val layoutAlignment = when (alignment) {
        ReaderTextAlignment.LEFT -> Layout.Alignment.ALIGN_NORMAL
        ReaderTextAlignment.CENTER -> Layout.Alignment.ALIGN_CENTER
        ReaderTextAlignment.RIGHT -> Layout.Alignment.ALIGN_OPPOSITE
        ReaderTextAlignment.NATURAL,
        ReaderTextAlignment.JUSTIFY -> null
    }
    if (text.isEmpty()) return text
    val result = SpannableStringBuilder(text)
    result.getSpans(0, result.length, ReaderChapterTitleSpan::class.java).forEach(result::removeSpan)
    result.getSpans(0, result.length, ReaderNaturalAlignmentSpan::class.java).forEach(result::removeSpan)
    val bodyStart = titleParagraphEnd.coerceIn(0, result.length)
    if (bodyStart > 0) {
        result.setSpan(ReaderChapterTitleSpan(), 0, bodyStart, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }
    if (bodyStart == result.length) return result
    if (alignment == ReaderTextAlignment.NATURAL) {
        result.setSpan(ReaderNaturalAlignmentSpan(), bodyStart, result.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        return result
    }

    result.getSpans(bodyStart, result.length, AlignmentSpan::class.java).forEach { span ->
        val start = result.getSpanStart(span)
        val flags = result.getSpanFlags(span)
        result.removeSpan(span)
        if (start < bodyStart) result.setSpan(span, start, bodyStart, flags)
    }
    if (layoutAlignment != null) {
        result.setSpan(
            AlignmentSpan.Standard(layoutAlignment),
            bodyStart,
            result.length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
    }
    return result
}
