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
 *
 * 标题段落固定按起始边（左）对齐，也不能被两端对齐拉伸；可见文字层需要知道哪些
 * 行属于标题。标记放在文本 span 上而不是当作额外参数传递，分页切片、简繁转换、
 * 标点挤压和翻页槽位轮转都会连同 span 一起复制。
 */
internal class ReaderChapterTitleSpan

internal fun ReaderTextAlignment.usesFullLineJustification(): Boolean =
    this == ReaderTextAlignment.NATURAL || this == ReaderTextAlignment.JUSTIFY

internal fun ReaderTextAlignment.readerJustificationMode(
    sdkInt: Int = Build.VERSION.SDK_INT
): Int = when {
    !usesFullLineJustification() -> Layout.JUSTIFICATION_MODE_NONE
    sdkInt >= Build.VERSION_CODES.VANILLA_ICE_CREAM ->
        Layout.JUSTIFICATION_MODE_INTER_CHARACTER
    else -> Layout.JUSTIFICATION_MODE_INTER_WORD
}

internal fun ReaderTextAlignment.readerBreakStrategy(): Int =
    if (usesFullLineJustification()) {
        Layout.BREAK_STRATEGY_HIGH_QUALITY
    } else {
        Layout.BREAK_STRATEGY_SIMPLE
    }

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
            return if (breakIndex < 0) spanned.length else breakIndex + 1
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
 * 正文按用户设置对齐，章首标题段落固定按起始边对齐。
 *
 * 标题段落单独挂一个 AlignmentSpan，正文的 AlignmentSpan 从标题段落之后开始，
 * 每个段落只落一个 span，结果不依赖框架对重叠 span 的取舍规则。
 *
 * [titleParagraphEnd] 为 0 表示本章没有识别出标题，行为与旧版一致。
 */
internal fun applyReaderTextAlignment(
    text: CharSequence,
    alignment: ReaderTextAlignment,
    titleParagraphEnd: Int = 0
): CharSequence {
    val titleEnd = titleParagraphEnd.coerceIn(0, text.length)
    val layoutAlignment = when (alignment) {
        ReaderTextAlignment.LEFT -> Layout.Alignment.ALIGN_NORMAL
        ReaderTextAlignment.CENTER -> Layout.Alignment.ALIGN_CENTER
        ReaderTextAlignment.RIGHT -> Layout.Alignment.ALIGN_OPPOSITE
        ReaderTextAlignment.NATURAL,
        ReaderTextAlignment.JUSTIFY -> null
    }
    // NATURAL 保留出版社自己的段落对齐；其余模式按用户设置覆盖。
    val overridesPublisherAlignment = alignment != ReaderTextAlignment.NATURAL
    // 没有标题、又不需要改段落对齐时原样返回，避免复制大章节（EPUB 单章可能 400K+）。
    if (titleEnd <= 0 && !overridesPublisherAlignment) return text

    val result = SpannableStringBuilder(text)
    if (overridesPublisherAlignment) {
        result.getSpans(0, result.length, AlignmentSpan::class.java).forEach(result::removeSpan)
    } else {
        // NATURAL：出版社把标题段落居中的情况也要按要求改成左对齐，正文段落的
        // 出版社对齐原样保留（跨到标题之外的部分按原范围补回）。
        result.getSpans(0, result.length, AlignmentSpan::class.java)
            .filter { result.getSpanStart(it) < titleEnd }
            .forEach { span ->
                val spanEnd = result.getSpanEnd(span)
                result.removeSpan(span)
                if (spanEnd > titleEnd) {
                    result.setSpan(span, titleEnd, spanEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
            }
    }

    if (titleEnd > 0) {
        // 标题一律左对齐：不跟随「居中 / 右对齐」，两端对齐下也不拉伸。
        result.setSpan(
            ReaderChapterTitleSpan(),
            0,
            titleEnd,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        result.setSpan(
            AlignmentSpan.Standard(Layout.Alignment.ALIGN_NORMAL),
            0,
            titleEnd,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
    }

    if (layoutAlignment != null && titleEnd < result.length) {
        result.setSpan(
            AlignmentSpan.Standard(layoutAlignment),
            titleEnd,
            result.length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
    }
    return result
}
