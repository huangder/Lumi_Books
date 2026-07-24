package com.huangder.lumibooks.tts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TtsTextExtractorTest {
    private val extractor = TtsTextExtractor()

    @Test
    fun splitIntoSentences_keepsChinesePunctuationAndClosingQuote() {
        val result = extractor.splitIntoSentences("他说：“你好！”然后离开。下一句？")

        assertEquals(listOf("他说：“你好！”", "然后离开。", "下一句？"), result)
    }

    @Test
    fun splitIntoSentences_removesImagePlaceholderAndBlankText() {
        assertTrue(extractor.splitIntoSentences(" \uFFFC\n ").isEmpty())
    }

    @Test
    fun splitIntoSentences_hardSplitsLongUnpunctuatedText() {
        val result = extractor.splitIntoSentences("字".repeat(450))

        assertEquals(listOf(200, 200, 50), result.map(String::length))
    }

    @Test
    fun splitIntoSegments_preservesSourceCharacterOffsets() {
        val result = extractor.splitIntoSegments("前言。正文！", baseCharacterOffset = 100)

        assertEquals(listOf("前言。", "正文！"), result.map { it.text })
        assertEquals(listOf(100, 103), result.map { it.startCharacterOffset })
        assertEquals(listOf(103, 106), result.map { it.endCharacterOffset })
    }

    @Test
    fun splitIntoSegments_startsAtBorrowedPrefixEndWithoutRepeatingIt() {
        val result = extractor.splitIntoSegments(
            text = "被借用的句尾。下一句。",
            baseCharacterOffset = 100,
            startCharacterOffset = 107
        )

        assertEquals(listOf("下一句。"), result.map { it.text })
        assertEquals(107, result.single().startCharacterOffset)
    }

    @Test
    fun splitIntoSegments_marksLengthBoundAsNonContinuable() {
        val result = extractor.splitIntoSegments("x".repeat(200))

        assertEquals(1, result.size)
        assertFalse(result.single().canContinueAcrossPage)
    }

    @Test
    fun pageResumeFingerprint_changesWithPageLayoutOrText() {
        val location = TtsPageLocation(chapterIndex = 2, pageIndex = 3)
        val original = buildTtsPageFingerprint(location, startCharacterOffset = 100, text = "正文")

        assertEquals(original, buildTtsPageFingerprint(location, 100, "正文"))
        assertFalse(original == buildTtsPageFingerprint(location.copy(pageIndex = 4), 100, "正文"))
        assertFalse(original == buildTtsPageFingerprint(location, 101, "正文"))
        assertFalse(original == buildTtsPageFingerprint(location, 100, "正文变化"))
    }

    // ── isTrailing ──────────────────────────────────────────────────────────

    @Test
    fun isTrailing_trueForUnterminatedChinese() {
        assertTrue(extractor.isTrailing("这是一个未完成"))
    }

    @Test
    fun isTrailing_trueForUnterminatedEnglish() {
        assertTrue(extractor.isTrailing("This sentence is not"))
    }

    @Test
    fun isTrailing_falseForFullStop() {
        assertFalse(extractor.isTrailing("这是完整句子。"))
        assertFalse(extractor.isTrailing("Complete."))
    }

    @Test
    fun isTrailing_falseForQuestionExclamation() {
        assertFalse(extractor.isTrailing("真的吗？"))
        assertFalse(extractor.isTrailing("Wonderful!"))
    }

    @Test
    fun isTrailing_falseForClosingQuoteAfterTerminator() {
        assertFalse(extractor.isTrailing("他说：“你好！”"))
    }

    @Test
    fun isTrailing_falseForBlankText() {
        assertFalse(extractor.isTrailing("   "))
    }

    // ── mergeTrailingSentence ───────────────────────────────────────────────

    @Test
    fun mergeTrailingSentence_mergesChineseCrossPage() {
        val trailing = extractor.splitIntoSentences("这是第一句。这是未完成").last()
        val continuation = "的句子跨越了页面。第三句开始。"

        val result = extractor.mergeTrailingSentence(trailing, continuation)

        assertEquals("这是未完成的句子跨越了页面。", result.mergedText)
        assertEquals(9, result.consumedSourceChars) // "的句子跨越了页面。" = 9 raw chars
    }

    @Test
    fun mergeTrailingSentence_mergesEnglishCrossPage() {
        val trailing = "This sentence crosses"
        val continuation = " a page boundary. Next sentence here."

        val result = extractor.mergeTrailingSentence(trailing, continuation)

        assertEquals("This sentence crosses a page boundary.", result.mergedText)
        assertEquals(17, result.consumedSourceChars) // " a page boundary." = 17 raw chars
    }

    @Test
    fun mergeTrailingSentence_stopsAtParagraphBoundary() {
        val trailing = "未完成句子"
        val continuation = "继续\n\n新段落。"

        val result = extractor.mergeTrailingSentence(trailing, continuation)

        assertEquals("未完成句子继续", result.mergedText)
        assertEquals(2, result.consumedSourceChars)
    }

    @Test
    fun mergeTrailingSentence_noMergeWhenAlreadyTerminated() {
        val trailing = "完整句子。"
        val continuation = "下一句！"

        val result = extractor.mergeTrailingSentence(trailing, continuation)

        // The merge function still appends, but the trailing already ends with terminator.
        // isTrailing would return false, so the controller wouldn't call this.
        // This test verifies mergeTrailingSentence still works deterministically.
        assertEquals("完整句子。下一句！", result.mergedText)
        assertEquals(4, result.consumedSourceChars)
    }

    @Test
    fun mergeTrailingSentence_respectsMaxTotalLength() {
        val trailing = "x".repeat(195)
        val continuation = "y".repeat(50)

        val result = extractor.mergeTrailingSentence(trailing, continuation, maxTotalLength = 200)

        assertEquals(200, result.mergedText.length)
        assertEquals(5, result.consumedSourceChars) // only 5 'y's appended
    }

    @Test
    fun mergeTrailingSentence_consumedCharsSkipsBorrowedPrefix() {
        val trailing = "跨页"
        val continuation = "的第一句。第二句。"

        val result = extractor.mergeTrailingSentence(trailing, continuation)

        assertEquals("跨页的第一句。", result.mergedText)
        assertEquals(5, result.consumedSourceChars)
    }

    @Test
    fun mergeTrailingSentence_handlesCarriageReturnLineFeed() {
        val trailing = "Unfinished"
        val continuation = " line\r\n\r\nNew paragraph."

        val result = extractor.mergeTrailingSentence(trailing, continuation)

        assertEquals("Unfinished line", result.mergedText)
        assertEquals(5, result.consumedSourceChars)
    }

    @Test
    fun mergeTrailingSentence_emptyContinuationReturnsTrailing() {
        val trailing = "未完成"
        val result = extractor.mergeTrailingSentence(trailing, "")

        assertEquals("未完成", result.mergedText)
        assertEquals(0, result.consumedSourceChars)
    }

    @Test
    fun mergeTrailingSentence_skipsObjectReplacementChar() {
        val trailing = "未完成"
        val continuation = "\uFFFC的句子。"

        val result = extractor.mergeTrailingSentence(trailing, continuation)

        assertEquals("未完成的句子。", result.mergedText)
        // \uFFFC skipped (1 raw char), then "的句子。" = 4 raw chars → consumed = 5
        assertEquals(5, result.consumedSourceChars)
    }
}
