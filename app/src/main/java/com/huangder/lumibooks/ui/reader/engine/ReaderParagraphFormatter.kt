package com.huangder.lumibooks.ui.reader.engine

import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.ImageSpan
import android.text.style.LeadingMarginSpan
import com.huangder.lumibooks.util.parser.EpubParser
import kotlin.math.roundToInt

object ReaderParagraphFormatter {
    fun applyFirstLineIndent(
        text: CharSequence,
        indentCharacters: Float,
        textSizePx: Float,
        paragraphSpacingPx: Float,
        skipFirstNonEmptyParagraph: Boolean
    ): CharSequence {
        val result = SpannableStringBuilder(text)
        if (result.isEmpty()) return result

        applyParagraphSpacing(result, paragraphSpacingPx.roundToInt().coerceAtLeast(0))

        result.getSpans(0, result.length, LeadingMarginSpan.Standard::class.java)
            .forEach(result::removeSpan)
        if (indentCharacters <= 0f || textSizePx <= 0f) return result

        val desiredIndentPx = indentCharacters * textSizePx

        // 第一遍：收集所有段落的 (段首, 首个非空白字符位置, 段尾) 三元组
        // 在修改文本前完成，避免删除操作影响后续索引计算
        data class ParaInfo(val start: Int, val firstContent: Int, val end: Int)
        val paragraphs = mutableListOf<ParaInfo>()
        var paraStart = 0
        for (i in 0..result.length) {
            if (i == result.length || result[i] == '\n') {
                var firstContent = paraStart
                while (firstContent < i && result[firstContent].isHorizontalIndent()) firstContent++
                if (firstContent < i) {   // 非空段落
                    val containsImage = result.getSpans(paraStart, i, ImageSpan::class.java).isNotEmpty()
                    if (!containsImage) paragraphs.add(ParaInfo(paraStart, firstContent, i))
                }
                paraStart = i + 1
            }
        }

        // 第二遍：倒序处理，删除原有段首空白并加 LeadingMarginSpan
        // 倒序保证删除字符后，前面段落的索引仍然有效
        paragraphs.asReversed().forEachIndexed { reversedIndex, para ->
            val forwardIndex = paragraphs.size - 1 - reversedIndex
            val shouldSkip = skipFirstNonEmptyParagraph && forwardIndex == 0
            if (shouldSkip) return@forEachIndexed

            // 删除已有的行首缩进字符（全角空格/半角空格/\t）
            val wsLen = para.firstContent - para.start
            if (wsLen > 0) result.delete(para.start, para.firstContent)

            // 直接用 LeadingMarginSpan 打上精确的缩进，不依赖字符测量
            val newEnd = para.end - wsLen
            result.setSpan(
                LeadingMarginSpan.Standard(desiredIndentPx.roundToInt(), 0),
                para.start,
                newEnd,
                Spannable.SPAN_INCLUSIVE_INCLUSIVE
            )
        }
        return result
    }

    private fun applyParagraphSpacing(result: SpannableStringBuilder, spacingPx: Int) {
        result.getSpans(0, result.length, EpubParser.ParagraphLineHeightSpan::class.java)
            .forEach(result::removeSpan)

        // HTML 段落常带多个连续换行。先统一为一个段落分隔符，避免 0dp 到 1dp
        // 时从“合并空行”突然切换成“保留并继续插入空行”。
        var index = result.length - 1
        while (index > 0) {
            if (result[index] == '\n' && result[index - 1] == '\n') {
                result.delete(index, index + 1)
            } else if (
                result[index] == '\n' && index > 1 &&
                result[index - 1] == '\r' && result[index - 2] == '\n'
            ) {
                result.delete(index - 1, index + 1)
            }
            index--
        }

        if (spacingPx <= 0) return

        val paragraphBreaks = buildList {
            for (i in 0 until result.length - 1) {
                if (result[i] == '\n') add(i)
            }
        }
        for (lineBreak in paragraphBreaks.asReversed()) {
            result.insert(lineBreak + 1, "\n")
            result.setSpan(
                EpubParser.ParagraphLineHeightSpan(spacingPx),
                lineBreak + 1,
                lineBreak + 2,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
    }

    private fun Char.isHorizontalIndent(): Boolean {
        return this == ' ' || this == '\t' || this == '\u00A0' || this == '\u3000'
    }
}
