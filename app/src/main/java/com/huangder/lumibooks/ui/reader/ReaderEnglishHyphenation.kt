package com.huangder.lumibooks.ui.reader

import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.LocaleSpan
import com.huangder.lumibooks.domain.model.ReaderTextAlignment
import com.huangder.lumibooks.ui.reader.engine.ReaderPunctuationCompressionSpan
import java.util.Locale

/** Display-only locale/renderer marker; never adds hyphens to the source text. */
internal class ReaderEnglishHyphenationSpan : LocaleSpan(Locale.ENGLISH)

internal fun usesReaderEnglishHyphenation(text: CharSequence): Boolean =
    text is Spanned && text.isNotEmpty() &&
        text.getSpans(0, 1, ReaderEnglishHyphenationSpan::class.java).isNotEmpty()

/**
 * Unlabelled Latin chapters need an English hyphenation dictionary even on a
 * Chinese system. Explicit publisher locale spans take precedence. Keep mixed
 * CJK chapters on the reader's compact-punctuation renderer.
 */
internal fun prepareReaderEnglishHyphenation(text: CharSequence): CharSequence {
    if (usesReaderEnglishHyphenation(text)) return text
    var letters = 0
    var offset = 0
    while (offset < text.length) {
        val codePoint = Character.codePointAt(text, offset)
        if (Character.isLetter(codePoint)) {
            if (Character.UnicodeScript.of(codePoint) != Character.UnicodeScript.LATIN) return text
            letters++
        }
        offset += Character.charCount(codePoint)
    }
    if (letters == 0) return text
    return SpannableStringBuilder(text).apply {
        // English quotation marks retain their font's natural advance. Native
        // TextLine draws the complete shaped runs, including discretionary
        // hyphens; measurement-only punctuation scaling must not reach it.
        getSpans(0, length, ReaderPunctuationCompressionSpan::class.java).forEach(::removeSpan)
        setSpan(ReaderEnglishHyphenationSpan(), 0, length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE or (1 shl Spanned.SPAN_PRIORITY_SHIFT))
    }
}

internal fun ReaderTextAlignment.readerBreakStrategyForText(text: CharSequence): Int =
    if (usesReaderEnglishHyphenation(text)) Layout.BREAK_STRATEGY_HIGH_QUALITY else readerBreakStrategy()

internal fun ReaderTextAlignment.readerJustificationForText(text: CharSequence): Int =
    if (usesReaderEnglishHyphenation(text) && usesFullLineJustification())
        Layout.JUSTIFICATION_MODE_INTER_WORD else readerJustificationMode()

internal fun readerHyphenationFrequency(text: CharSequence): Int =
    if (usesReaderEnglishHyphenation(text)) Layout.HYPHENATION_FREQUENCY_FULL
    else Layout.HYPHENATION_FREQUENCY_NONE
