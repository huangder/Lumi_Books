package com.huangder.lumibooks.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint

object QuoteExcerptFormatter {
    data class RenderedQuote(
        val text: String,
        val bitmap: Bitmap
    )

    fun format(
        text: String,
        typeface: Typeface,
        textSizePx: Float,
        widthPx: Int,
        heightPx: Int,
        maxLines: Int,
        lineSpacingMultiplier: Float
    ): String {
        if (widthPx <= 0 || heightPx <= 0 || maxLines <= 0) return ""

        val paint = TextPaint(TextPaint.ANTI_ALIAS_FLAG).apply {
            this.typeface = typeface
            this.textSize = textSizePx
        }
        return formatToFit(text) { candidate ->
            val layout = createLayout(candidate, paint, widthPx, lineSpacingMultiplier)
            layout.lineCount <= maxLines && layout.height <= heightPx
        }
    }

    fun render(
        text: String,
        typeface: Typeface,
        textSizePx: Float,
        textColor: Int,
        widthPx: Int,
        heightPx: Int,
        maxLines: Int,
        lineSpacingMultiplier: Float
    ): RenderedQuote {
        val safeWidth = widthPx.coerceAtLeast(1)
        val safeHeight = heightPx.coerceAtLeast(1)
        val formatted = format(
            text = text,
            typeface = typeface,
            textSizePx = textSizePx,
            widthPx = safeWidth,
            heightPx = safeHeight,
            maxLines = maxLines,
            lineSpacingMultiplier = lineSpacingMultiplier
        )
        val paint = TextPaint(TextPaint.ANTI_ALIAS_FLAG).apply {
            this.typeface = typeface
            this.textSize = textSizePx
            color = textColor
        }
        val layout = createLayout(formatted, paint, safeWidth, lineSpacingMultiplier)
        val bitmap = Bitmap.createBitmap(safeWidth, safeHeight, Bitmap.Config.ARGB_8888)
        layout.draw(Canvas(bitmap))
        return RenderedQuote(text = formatted, bitmap = bitmap)
    }

    internal fun formatToFit(text: String, fits: (String) -> Boolean): String {
        val normalized = normalizeWhitespace(text)
        if (normalized.isEmpty() || fits(normalized)) return normalized

        var bestCompleteSentence: String? = null
        for (boundary in sentenceBoundaries(normalized)) {
            val candidate = normalized.substring(0, boundary).trimEnd()
            if (fits(candidate)) {
                bestCompleteSentence = candidate
            } else {
                break
            }
        }
        if (bestCompleteSentence != null) return bestCompleteSentence

        val codePointCount = normalized.codePointCount(0, normalized.length)
        var low = 0
        var high = codePointCount
        var best = if (fits(ELLIPSIS)) ELLIPSIS else ""
        while (low <= high) {
            val middle = (low + high) ushr 1
            val end = normalized.offsetByCodePoints(0, middle)
            val candidate = normalized.substring(0, end).trimEnd() + ELLIPSIS
            if (fits(candidate)) {
                best = candidate
                low = middle + 1
            } else {
                high = middle - 1
            }
        }
        return best
    }

    internal fun normalizeWhitespace(text: String): String {
        val result = StringBuilder(text.length)
        var pendingSpace = false
        text.forEach { character ->
            if (character.isWhitespace() || Character.isSpaceChar(character)) {
                pendingSpace = result.isNotEmpty()
            } else {
                if (pendingSpace) result.append(' ')
                result.append(character)
                pendingSpace = false
            }
        }
        return result.toString().trim()
    }

    private fun sentenceBoundaries(text: String): List<Int> {
        val boundaries = mutableListOf<Int>()
        var index = 0
        while (index < text.length) {
            if (text[index] in SENTENCE_ENDINGS) {
                var end = index + 1
                while (end < text.length && text[end] in SENTENCE_ENDINGS) end++
                while (end < text.length && text[end] in SENTENCE_CLOSERS) end++
                boundaries += end
                index = end
            } else {
                index++
            }
        }
        return boundaries
    }

    private fun createLayout(
        text: String,
        paint: TextPaint,
        widthPx: Int,
        lineSpacingMultiplier: Float
    ): StaticLayout {
        return StaticLayout.Builder.obtain(text, 0, text.length, paint, widthPx)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setIncludePad(true)
            .setLineSpacing(0f, lineSpacingMultiplier)
            .setBreakStrategy(Layout.BREAK_STRATEGY_HIGH_QUALITY)
            .setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NONE)
            .build()
    }

    private const val ELLIPSIS = "..."
    private val SENTENCE_ENDINGS = setOf('.', '?', '!', '\u3002', '\uff1f', '\uff01', '\u2026')
    private val SENTENCE_CLOSERS = setOf(
        '\'', '"', '\u2019', '\u201d', '\u3009', '\u300b', '\u300d', '\u300f',
        '\u3011', '\uff09', ')', ']'
    )
}
