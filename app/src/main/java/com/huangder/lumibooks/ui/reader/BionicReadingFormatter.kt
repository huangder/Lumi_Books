package com.huangder.lumibooks.ui.reader

import android.graphics.Typeface
import android.icu.text.BreakIterator
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.StyleSpan
import java.util.Locale

/** Applies bionic-reading fixation spans without changing character offsets. */
object BionicReadingFormatter {
    fun format(text: CharSequence, enabled: Boolean): CharSequence {
        if (!enabled || text.isEmpty()) return text

        val result = SpannableStringBuilder(text)
        fixationRanges(text).forEach { range ->
            result.setSpan(
                StyleSpan(Typeface.BOLD),
                range.first,
                range.last + 1,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
        return result
    }

    internal fun fixationRanges(text: CharSequence): List<IntRange> {
        if (text.isEmpty()) return emptyList()
        val ranges = ArrayList<IntRange>()
        var index = 0

        while (index < text.length) {
            val codePoint = Character.codePointAt(text, index)
            when {
                isCjkCodePoint(codePoint) -> {
                    val runStart = index
                    while (index < text.length) {
                        val current = Character.codePointAt(text, index)
                        if (!isCjkCodePoint(current)) break
                        index += Character.charCount(current)
                    }
                    ranges += cjkFixationRanges(text, runStart, index)
                }

                Character.isLetterOrDigit(codePoint) -> {
                    val boundaries = ArrayList<Int>()
                    while (index < text.length) {
                        val current = Character.codePointAt(text, index)
                        if (isCjkCodePoint(current) || !Character.isLetterOrDigit(current)) break
                        boundaries += index
                        index += Character.charCount(current)
                    }
                    boundaries += index
                    val characterCount = boundaries.lastIndex
                    val fixationCount = (characterCount + 1) / 2
                    if (fixationCount > 0) {
                        ranges += boundaries.first() until boundaries[fixationCount]
                    }
                }

                else -> index += Character.charCount(codePoint)
            }
        }

        return ranges
    }

    /**
     * Chinese bionic reading uses word-sized gaze chunks rather than alternating characters.
     * A fixation chunk targets roughly two CJK characters, followed by a roughly three-character
     * saccade chunk. ICU word boundaries are kept intact whenever possible.
     */
    private fun cjkFixationRanges(text: CharSequence, runStart: Int, runEnd: Int): List<IntRange> {
        val runText = text.subSequence(runStart, runEnd).toString()
        val units = segmentCjkUnits(runText)
        if (units.isEmpty()) return emptyList()

        val ranges = ArrayList<IntRange>()
        var unitIndex = 0
        var fixation = true
        while (unitIndex < units.size) {
            val targetLength = if (fixation) 2 else 3
            val chunkStart = units[unitIndex].start
            var chunkEnd = chunkStart
            var characterCount = 0
            while (unitIndex < units.size && characterCount < targetLength) {
                val unit = units[unitIndex++]
                chunkEnd = unit.end
                characterCount += unit.characterCount
            }
            if (fixation && chunkEnd > chunkStart) {
                ranges += (runStart + chunkStart) until (runStart + chunkEnd)
            }
            fixation = !fixation
        }
        return ranges
    }

    private fun segmentCjkUnits(text: String): List<CjkUnit> {
        val units = ArrayList<CjkUnit>()
        val iterator = BreakIterator.getWordInstance(cjkLocale(text))
        iterator.setText(text)
        var start = iterator.first()
        var end = iterator.next()
        while (end != BreakIterator.DONE) {
            if (containsCjk(text, start, end)) {
                appendCjkUnit(units, text, start, end)
            }
            start = end
            end = iterator.next()
        }

        if (units.isNotEmpty()) return units

        var index = 0
        while (index < text.length) {
            val next = index + Character.charCount(Character.codePointAt(text, index))
            units += CjkUnit(index, next, 1)
            index = next
        }
        return units
    }

    private fun appendCjkUnit(units: MutableList<CjkUnit>, text: String, start: Int, end: Int) {
        val count = Character.codePointCount(text, start, end)
        if (count <= MAX_CJK_WORD_LENGTH) {
            units += CjkUnit(start, end, count)
            return
        }

        // Unknown long strings sometimes arrive as one "word". Split those into readable phrases
        // instead of making an entire sentence bold.
        var index = start
        while (index < end) {
            val remaining = Character.codePointCount(text, index, end)
            val take = minOf(FALLBACK_CJK_PHRASE_LENGTH, remaining)
            val next = Character.offsetByCodePoints(text, index, take)
            units += CjkUnit(index, next, take)
            index = next
        }
    }

    private fun containsCjk(text: String, start: Int, end: Int): Boolean {
        var index = start
        while (index < end) {
            val codePoint = Character.codePointAt(text, index)
            if (isCjkCodePoint(codePoint)) return true
            index += Character.charCount(codePoint)
        }
        return false
    }

    private fun cjkLocale(text: String): Locale {
        var hasKana = false
        var hasHangul = false
        var index = 0
        while (index < text.length) {
            when (Character.UnicodeScript.of(Character.codePointAt(text, index))) {
                Character.UnicodeScript.HIRAGANA,
                Character.UnicodeScript.KATAKANA -> hasKana = true
                Character.UnicodeScript.HANGUL -> hasHangul = true
                else -> Unit
            }
            index += Character.charCount(Character.codePointAt(text, index))
        }
        return when {
            hasKana -> Locale.JAPANESE
            hasHangul -> Locale.KOREAN
            else -> Locale.CHINESE
        }
    }

    private data class CjkUnit(val start: Int, val end: Int, val characterCount: Int)

    private const val MAX_CJK_WORD_LENGTH = 4
    private const val FALLBACK_CJK_PHRASE_LENGTH = 3

    private fun isCjkCodePoint(codePoint: Int): Boolean = when (Character.UnicodeScript.of(codePoint)) {
        Character.UnicodeScript.HAN,
        Character.UnicodeScript.HIRAGANA,
        Character.UnicodeScript.KATAKANA,
        Character.UnicodeScript.HANGUL -> true
        else -> false
    }
}
