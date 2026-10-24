package com.huangder.lumibooks.domain.model

import java.text.BreakIterator
import java.util.Locale

enum class LongPressSelectionRange(val storageKey: String) {
    PARAGRAPH("paragraph"), LINE("line"), SENTENCE("sentence"), CHARACTER("character"), WORD("word");

    companion object {
        val DEFAULT = WORD
        fun fromKey(key: String?): LongPressSelectionRange =
            entries.firstOrNull { it.storageKey == key } ?: DEFAULT
    }
}

/** All ranges use UTF-16 offsets, with an inclusive last index like Kotlin's `until`. */
internal fun CharSequence.selectionRangeAt(
    offset: Int,
    range: LongPressSelectionRange,
    visualLine: IntRange? = null
): IntRange? {
    if (isEmpty()) return null
    val value = toString()
    var index = offset.coerceIn(0, lastIndex)
    // Layout hit testing can return the line's trailing newline.
    if (range != LongPressSelectionRange.CHARACTER) {
        while (index > 0 && value[index] in "\r\n\u2029") index--
    }
    fun character(): IntRange {
        val breaker = BreakIterator.getCharacterInstance(Locale.getDefault()).apply { setText(value) }
        val start = breaker.preceding(index + 1).coerceAtLeast(0)
        val end = breaker.following(index).takeIf { it != BreakIterator.DONE } ?: length
        return start until end
    }
    fun paragraph(): IntRange {
        var start = index
        var end = index + 1
        while (start > 0 && value[start - 1] !in "\r\n\u2029") start--
        while (end < length && value[end] !in "\r\n\u2029") end++
        return start until end
    }
    val selected = when (range) {
        LongPressSelectionRange.CHARACTER -> character()
        LongPressSelectionRange.PARAGRAPH -> paragraph()
        LongPressSelectionRange.LINE -> visualLine ?: paragraph()
        LongPressSelectionRange.SENTENCE -> {
            fun terminates(at: Int): Boolean = when (value[at]) {
                '\r', '\n', '\u2029', '。', '！', '？', '!', '?' -> true
                '.' -> !(value.getOrNull(at - 1)?.isLetterOrDigit() == true &&
                    value.getOrNull(at + 1)?.isLetterOrDigit() == true)
                else -> false
            }
            fun closing(char: Char) = char in "。！？!?…\"'”’」』）)]"
            var start = index
            while (start > 0 && !terminates(start - 1)) start--
            while (start < index && value[start] in "”’」』）)]") start++
            var end = index
            while (end < length && !terminates(end)) end++
            if (end < length && value[end] !in "\r\n\u2029") {
                end++
                while (end < length && closing(value[end])) end++
            }
            start until end
        }
        LongPressSelectionRange.WORD -> {
            val breaker = BreakIterator.getWordInstance(Locale.getDefault()).apply { setText(value) }
            val start = breaker.preceding(index + 1).coerceAtLeast(0)
            val end = breaker.following(index).takeIf { it != BreakIterator.DONE } ?: length
            start until end
        }
    }
    var start = selected.first.coerceIn(0, length)
    var end = (selected.last + 1).coerceIn(start, length)
    if (range != LongPressSelectionRange.CHARACTER) {
        while (start < end && value[start].isWhitespace()) start++
        while (end > start && value[end - 1].isWhitespace()) end--
    }
    return if (end > start) start until end else character()
}
