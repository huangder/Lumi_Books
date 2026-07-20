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
        val measuringPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            textSize = textSizePx
        }

        var paragraphStart = 0
        var nonEmptyParagraphIndex = 0

        fun formatParagraph(paragraphEnd: Int) {
            var firstContent = paragraphStart
            while (firstContent < paragraphEnd && result[firstContent].isHorizontalIndent()) {
                firstContent++
            }
            if (firstContent >= paragraphEnd) return

            val shouldSkip = skipFirstNonEmptyParagraph && nonEmptyParagraphIndex == 0
            nonEmptyParagraphIndex++
            if (shouldSkip) return

            val containsImage = result.getSpans(paragraphStart, paragraphEnd, ImageSpan::class.java)
                .isNotEmpty()
            if (containsImage) return

            var existingIndentPx = 0f
            for (index in paragraphStart until firstContent) {
                existingIndentPx += if (result[index] == '\t') {
                    textSizePx * 2f
                } else {
                    measuringPaint.measureText(result, index, index + 1)
                }
            }
            val remainingIndentPx = (desiredIndentPx - existingIndentPx)
                .coerceAtLeast(0f)
                .roundToInt()
            if (remainingIndentPx <= 0) return

            result.setSpan(
                LeadingMarginSpan.Standard(remainingIndentPx, 0),
                paragraphStart,
                paragraphEnd,
                Spannable.SPAN_INCLUSIVE_INCLUSIVE
            )
        }

        for (index in 0..result.length) {
            if (index == result.length || result[index] == '\n') {
                formatParagraph(index)
                paragraphStart = index + 1
            }
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
