package com.huangder.lumibooks.ui.reader

import com.huangder.lumibooks.domain.model.Note
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HighlightAnchorResolverTest {
    @Test
    fun repairsBadZeroBasedRangeUsingUniqueQuote() {
        assertRange(
            text = "Opening text. Sentence A is here. Closing text.",
            start = 0,
            end = "Sentence A is here.".length,
            quote = "Sentence A is here.",
            expectedStart = 14
        )
    }

    @Test
    fun usesContextToDisambiguateRepeatedQuote() {
        val text = "first before repeated sentence after one. second before repeated sentence after two."
        val quote = "repeated sentence"
        val expected = text.lastIndexOf(quote)
        val result = HighlightAnchorResolver.resolve(
            chapterText = text,
            storedStart = 1,
            storedEnd = quote.length + 1,
            selectedText = quote,
            reference = HighlightTextReference(
                exact = quote,
                prefix = "second before ",
                suffix = " after two"
            )
        )
        assertEquals(ResolvedHighlightRange(expected, expected + quote.length), result)
    }

    @Test
    fun usesProgressionWhenRepeatedQuoteHasNoContext() {
        val text = "same sentence. middle middle middle. same sentence."
        val quote = "same sentence"
        val expected = text.lastIndexOf(quote)
        val result = HighlightAnchorResolver.resolve(
            chapterText = text,
            storedStart = 1,
            storedEnd = quote.length + 1,
            selectedText = quote,
            reference = HighlightTextReference(exact = quote, progression = 0.9)
        )
        assertEquals(ResolvedHighlightRange(expected, expected + quote.length), result)
    }

    @Test
    fun v2PositionRejectsWrongButTextuallyValidDuplicateRange() {
        val text = "same sentence. middle middle middle. same sentence."
        val quote = "same sentence"
        val expected = text.lastIndexOf(quote)
        val result = HighlightAnchorResolver.resolve(
            chapterText = text,
            storedStart = 0,
            storedEnd = quote.length,
            selectedText = quote,
            reference = HighlightTextReference(
                exact = quote,
                textPosition = expected,
                textLength = text.length,
                progression = 0.9
            )
        )
        assertEquals(ResolvedHighlightRange(expected, expected + quote.length), result)
    }

    @Test
    fun matchesHtmlAndReaderWhitespaceDifferences() {
        val text = "alpha\n\tbeta\u00A0gamma"
        val quote = "alpha beta\u200Bgamma"
        val result = HighlightAnchorResolver.resolve(text, 9, 12, quote)
        assertEquals(ResolvedHighlightRange(0, text.length), result)
    }

    @Test
    fun matchesSimplifiedAndTraditionalText() {
        val text = "閱讀器會保存這句話。"
        val quote = "阅读器会保存这句话。"
        val result = HighlightAnchorResolver.resolve(text, 0, 1, quote)
        assertEquals(ResolvedHighlightRange(0, text.length), result)
    }

    @Test
    fun returnsNullWhenQuoteCannotBeFound() {
        assertNull(HighlightAnchorResolver.resolve("chapter text", 0, 4, "missing sentence"))
    }

    @Test
    fun findsSavedHighlightWhenWebSelectionIsOnlyOneWordInsideIt() {
        val text = "Opening text. Sentence A is highlighted. Closing text."
        val sentence = "Sentence A is highlighted."
        val sentenceStart = text.indexOf(sentence)
        val selectedWord = "highlighted"
        val note = note(
            start = sentenceStart,
            end = sentenceStart + sentence.length,
            selectedText = sentence
        )

        val result = findOverlappingResolvedNote(
            chapterText = text,
            notes = listOf(note),
            chapterIndex = 2,
            storedStart = 0,
            storedEnd = selectedWord.length,
            selectedText = selectedWord,
            startLocatorJson = null,
            endLocatorJson = null
        )

        assertEquals(note, result)
    }

    @Test
    fun doesNotMatchHighlightWhenWebSelectionCannotBeResolved() {
        val note = note(start = 0, end = 10, selectedText = "chapter")

        val result = findOverlappingResolvedNote(
            chapterText = "chapter text",
            notes = listOf(note),
            chapterIndex = 2,
            storedStart = 0,
            storedEnd = 7,
            selectedText = "missing",
            startLocatorJson = null,
            endLocatorJson = null
        )

        assertNull(result)
    }

    private fun note(start: Int, end: Int, selectedText: String) = Note(
        id = 7,
        bookId = "book",
        chapterIndex = 2,
        startPosition = start,
        endPosition = end,
        selectedText = selectedText,
        note = "",
        color = "#FFEB3B",
        createdAt = 1
    )

    private fun assertRange(
        text: String,
        start: Int,
        end: Int,
        quote: String,
        expectedStart: Int
    ) {
        val result = HighlightAnchorResolver.resolve(text, start, end, quote)
        assertEquals(ResolvedHighlightRange(expectedStart, expectedStart + quote.length), result)
    }
}
