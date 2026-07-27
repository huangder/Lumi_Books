package com.huangder.lumibooks.ui.reader

import android.graphics.Typeface
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.StyleSpan

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
                    val boundaries = ArrayList<Int>()
                    while (index < text.length) {
                        val current = Character.codePointAt(text, index)
                        if (!isCjkCodePoint(current)) break
                        boundaries += index
                        index += Character.charCount(current)
                    }
                    boundaries += index
                    var characterIndex = 0
                    while (characterIndex < boundaries.lastIndex) {
                        ranges += boundaries[characterIndex] until boundaries[characterIndex + 1]
                        characterIndex += 2
                    }
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

    private fun isCjkCodePoint(codePoint: Int): Boolean = when (Character.UnicodeScript.of(codePoint)) {
        Character.UnicodeScript.HAN,
        Character.UnicodeScript.HIRAGANA,
        Character.UnicodeScript.KATAKANA,
        Character.UnicodeScript.HANGUL -> true
        else -> false
    }
}
