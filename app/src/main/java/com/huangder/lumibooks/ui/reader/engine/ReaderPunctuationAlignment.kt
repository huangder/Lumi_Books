package com.huangder.lumibooks.ui.reader.engine

import android.text.Layout
import android.text.Spannable
import android.text.Spanned
import android.text.style.LeadingMarginSpan
import android.text.style.ReplacementSpan
import com.huangder.lumibooks.ui.reader.ReaderChapterTitleSpan
import com.huangder.lumibooks.ui.reader.ReaderNaturalAlignmentSpan

private const val NO_LINE_START = "、。，．：；！？）］｝】〕〉》」』”’…—・,.:;!?)]}"
private const val EDGE_TOLERANCE_PX = 0.5f

private class ReaderLineContinuation(val text: CharSequence)

internal fun Spannable.setReaderLineContinuation(followingText: CharSequence) {
    if (isEmpty() || followingText.isEmpty()) return
    setSpan(ReaderLineContinuation(followingText), length - 1, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
}

internal fun isReaderHeadingLine(text: CharSequence, start: Int): Boolean =
    text is Spanned && start in text.indices &&
        text.getSpans(start, start + 1, ReaderChapterTitleSpan::class.java).isNotEmpty()

internal fun readerLineCanStretch(layout: Layout, text: CharSequence, line: Int, continues: Boolean): Boolean {
    val start = layout.getLineStart(line)
    return layout.getParagraphAlignment(line) == Layout.Alignment.ALIGN_NORMAL &&
        !isReaderHeadingLine(text, start) &&
        shouldJustifyReaderLine(line, layout.lineCount,
            readerLineEndsParagraph(text, start, layout.getLineEnd(line)), continues)
}

internal fun readerPunctuationGap(layout: Layout, text: CharSequence, line: Int, gap: Float): Boolean {
    if (gap <= EDGE_TOLERANCE_PX || !gap.isFinite() || text !is Spanned) return false
    val start = layout.getLineStart(line)
    if (start !in text.indices || text.getSpans(start, start + 1, ReaderNaturalAlignmentSpan::class.java).isEmpty()) return false
    val end = layout.getLineEnd(line)
    val continuation = if (end == text.length && end > start) {
        text.getSpans(end - 1, end, ReaderLineContinuation::class.java).lastOrNull()?.text
    } else null
    val following = continuation ?: text
    val first = if (continuation != null) 0 else end
    if (first >= following.length) return false
    val codePoint = Character.codePointAt(following, first)
    when (Character.UnicodeScript.of(codePoint)) {
        Character.UnicodeScript.HAN, Character.UnicodeScript.HIRAGANA, Character.UnicodeScript.KATAKANA -> Unit
        else -> return false
    }
    var afterFirst = first + Character.charCount(codePoint)
    while (afterFirst < following.length) {
        val next = Character.codePointAt(following, afterFirst)
        when (Character.getType(next)) {
            Character.NON_SPACING_MARK.toInt(), Character.COMBINING_SPACING_MARK.toInt(),
            Character.ENCLOSING_MARK.toInt() -> afterFirst += Character.charCount(next)
            else -> break
        }
    }
    var afterPunctuation = afterFirst
    while (afterPunctuation < following.length && following[afterPunctuation] in NO_LINE_START) afterPunctuation++
    if (afterPunctuation == afterFirst) return false
    val margin = if (following is Spanned) {
        if (following.getSpans(first, afterPunctuation, ReplacementSpan::class.java).isNotEmpty()) return false
        following.getSpans(first, afterPunctuation, LeadingMarginSpan::class.java).sumOf { it.getLeadingMargin(true) }
    } else 0
    val firstWidth = Layout.getDesiredWidth(following, first, afterFirst, layout.paint) - margin
    val groupWidth = Layout.getDesiredWidth(following, first, afterPunctuation, layout.paint) - margin
    return firstWidth > 0f && firstWidth <= gap + EDGE_TOLERANCE_PX && groupWidth > gap + EDGE_TOLERANCE_PX
}
