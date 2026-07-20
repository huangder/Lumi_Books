package com.huangder.lumibooks.ui.reader.engine

import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.LeadingMarginSpan
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReaderParagraphFormatterTest {
    @Test
    fun indentsTxtBodyButNotTitle() {
        val text = "标题\n\n第一段\n第二段"

        val result = ReaderParagraphFormatter.applyFirstLineIndent(
            text = text,
            indentCharacters = 2f,
            textSizePx = 16f,
            paragraphSpacingPx = 0f,
            skipFirstNonEmptyParagraph = true
        ) as Spanned

        val spans = result.getSpans(0, result.length, LeadingMarginSpan.Standard::class.java)
        assertEquals(2, spans.size)
        val resultText = result.toString()
        assertEquals(setOf(resultText.indexOf("第一段"), resultText.indexOf("第二段")), spans.map(result::getSpanStart).toSet())
        assertTrue(spans.all { it.getLeadingMargin(true) == 32 })
    }

    @Test
    fun existingWhitespaceIsSupplementedToRequestedIndent() {
        val text = "第一段\n 第二段\n　　第三段"

        val result = ReaderParagraphFormatter.applyFirstLineIndent(
            text = text,
            indentCharacters = 2f,
            textSizePx = 20f,
            paragraphSpacingPx = 0f,
            skipFirstNonEmptyParagraph = false
        ) as Spanned

        val spans = result.getSpans(0, result.length, LeadingMarginSpan.Standard::class.java)
        val marginsByStart = spans.associate { result.getSpanStart(it) to it.getLeadingMargin(true) }
        assertEquals(40, marginsByStart.getValue(0))
        assertTrue(marginsByStart.getValue(text.indexOf(" 第二段")) in 25..39)
        assertTrue(text.indexOf("　　第三段") !in marginsByStart)
    }

    @Test
    fun zeroIndentRemovesPreviouslyAppliedReaderIndent() {
        val text = SpannableStringBuilder("正文").apply {
            setSpan(
                LeadingMarginSpan.Standard(40, 0),
                0,
                length,
                Spannable.SPAN_INCLUSIVE_INCLUSIVE
            )
        }

        val result = ReaderParagraphFormatter.applyFirstLineIndent(
            text = text,
            indentCharacters = 0f,
            textSizePx = 16f,
            paragraphSpacingPx = 0f,
            skipFirstNonEmptyParagraph = false
        ) as Spanned

        assertTrue(result.getSpans(0, result.length, LeadingMarginSpan.Standard::class.java).isEmpty())
    }

    @Test
    fun paragraphSpacingNormalizesExistingBlankLinesBeforeAddingExactGap() {
        val result = ReaderParagraphFormatter.applyFirstLineIndent(
            text = "第一段\n\n\n第二段",
            indentCharacters = 0f,
            textSizePx = 16f,
            paragraphSpacingPx = 3.2f,
            skipFirstNonEmptyParagraph = false
        ) as Spanned

        assertEquals("第一段\n\n第二段", result.toString())
        val spans = result.getSpans(
            0,
            result.length,
            com.huangder.lumibooks.util.parser.EpubParser.ParagraphLineHeightSpan::class.java
        )
        assertEquals(1, spans.size)
        assertEquals(3, spans.single().extraHeightPx)
        assertEquals(result.toString().indexOf('\n') + 1, result.getSpanStart(spans.single()))
        assertEquals(Spannable.SPAN_EXCLUSIVE_EXCLUSIVE, result.getSpanFlags(spans.single()))
    }

    @Test
    fun zeroParagraphSpacingLeavesOnlyOneParagraphBreak() {
        val result = ReaderParagraphFormatter.applyFirstLineIndent(
            text = "第一段\n\n第二段",
            indentCharacters = 0f,
            textSizePx = 16f,
            paragraphSpacingPx = 0f,
            skipFirstNonEmptyParagraph = false
        ) as Spanned

        assertEquals("第一段\n第二段", result.toString())
        assertTrue(
            result.getSpans(
                0,
                result.length,
                com.huangder.lumibooks.util.parser.EpubParser.ParagraphLineHeightSpan::class.java
            ).isEmpty()
        )
    }

    @Test
    fun continuationOnlyPageDoesNotKeepCopiedFirstLineIndent() {
        val fullText = SpannableStringBuilder("上一段仍在继续，没有开始新段落")
        fullText.setSpan(
            LeadingMarginSpan.Standard(32, 0),
            0,
            fullText.length,
            Spannable.SPAN_INCLUSIVE_INCLUSIVE
        )

        val pageSpans = renderPageAndReadIndents(fullText, start = 4, end = fullText.length)

        assertTrue(pageSpans.isEmpty())
    }

    @Test
    fun continuationPageIndentsOnlyParagraphStartingInsidePage() {
        val fullText = SpannableStringBuilder("上一段仍在继续\n新的段落从这里开始")
        val paragraphBreak = fullText.indexOf("\n")
        fullText.setSpan(
            LeadingMarginSpan.Standard(32, 0),
            0,
            paragraphBreak,
            Spannable.SPAN_INCLUSIVE_INCLUSIVE
        )
        fullText.setSpan(
            LeadingMarginSpan.Standard(32, 0),
            paragraphBreak + 1,
            fullText.length,
            Spannable.SPAN_INCLUSIVE_INCLUSIVE
        )

        val pageStart = 4
        val pageText = renderPage(fullText, start = pageStart, end = fullText.length)
        val pageSpans = pageText.getSpans(0, pageText.length, LeadingMarginSpan::class.java)

        assertEquals(1, pageSpans.size)
        assertEquals(paragraphBreak - pageStart + 1, pageText.getSpanStart(pageSpans.single()))
    }

    @Test
    fun pageStartingAfterParagraphSpacerDoesNotKeepBoundaryLineHeightSpan() {
        val formatted = ReaderParagraphFormatter.applyFirstLineIndent(
            text = "第一段\n第二段\n第三段",
            indentCharacters = 2f,
            textSizePx = 16f,
            paragraphSpacingPx = 4f,
            skipFirstNonEmptyParagraph = false
        )
        val pageStart = formatted.toString().indexOf("第二段")

        val pageText = renderPage(formatted, start = pageStart, end = formatted.length)
        val spacingSpans = pageText.getSpans(
            0,
            pageText.length,
            com.huangder.lumibooks.util.parser.EpubParser.ParagraphLineHeightSpan::class.java
        )

        assertTrue(spacingSpans.all { span ->
            val start = pageText.getSpanStart(span)
            val end = pageText.getSpanEnd(span)
            start < end && (start until end).all { pageText[it] == '\n' || pageText[it] == '\r' }
        })
    }

    @Test
    fun paragraphSpacingSpanNeverCompressesVisibleTextLine() {
        val span = com.huangder.lumibooks.util.parser.EpubParser.ParagraphLineHeightSpan(4)
        val textMetrics = android.graphics.Paint.FontMetricsInt().apply {
            top = -22
            ascent = -20
            descent = 5
            bottom = 7
        }

        span.chooseHeight("正文", 0, 2, 0, 0, textMetrics)

        assertEquals(-22, textMetrics.top)
        assertEquals(-20, textMetrics.ascent)
        assertEquals(5, textMetrics.descent)
        assertEquals(7, textMetrics.bottom)

        val spacerMetrics = android.graphics.Paint.FontMetricsInt().apply {
            top = -22
            ascent = -20
            descent = 5
            bottom = 7
        }
        span.chooseHeight("\n", 0, 1, 0, 0, spacerMetrics)
        assertEquals(0, spacerMetrics.ascent)
        assertEquals(4, spacerMetrics.descent)
    }

    @Test
    fun staticLayoutKeepsSpacerSmallAndVisibleLinesAtNormalHeight() {
        val formatted = ReaderParagraphFormatter.applyFirstLineIndent(
            text = "第一段正文\n第二段正文",
            indentCharacters = 0f,
            textSizePx = 32f,
            paragraphSpacingPx = 4f,
            skipFirstNonEmptyParagraph = false
        ) as Spanned
        val textPaint = android.text.TextPaint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 32f
        }
        val layout = android.text.StaticLayout.Builder.obtain(
            formatted,
            0,
            formatted.length,
            textPaint,
            600
        ).setIncludePad(false).build()
        val spacingSpan = formatted.getSpans(
            0,
            formatted.length,
            com.huangder.lumibooks.util.parser.EpubParser.ParagraphLineHeightSpan::class.java
        ).single()
        val spacerLine = layout.getLineForOffset(formatted.getSpanStart(spacingSpan))
        val spacerHeight = layout.getLineBottom(spacerLine) - layout.getLineTop(spacerLine)

        assertTrue(spacerHeight in 3..6)
        for (line in 0 until layout.lineCount) {
            val start = layout.getLineStart(line)
            val end = layout.getLineEnd(line)
            val containsVisibleText = (start until end).any { index ->
                formatted[index] != '\n' && formatted[index] != '\r'
            }
            if (containsVisibleText) {
                assertTrue(layout.getLineBottom(line) - layout.getLineTop(line) > 20)
            }
        }
    }

    private fun renderPageAndReadIndents(
        fullText: CharSequence,
        start: Int,
        end: Int
    ): Array<LeadingMarginSpan> {
        val pageText = renderPage(fullText, start, end)
        return pageText.getSpans(0, pageText.length, LeadingMarginSpan::class.java)
    }

    private fun renderPage(fullText: CharSequence, start: Int, end: Int): Spanned {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        lateinit var pageText: Spanned
        instrumentation.runOnMainSync {
            val pageView = PageContentView(instrumentation.targetContext)
            pageView.setPageContent(fullText, start, end)
            pageText = pageView.textView.text as Spanned
        }
        return pageText
    }
}
