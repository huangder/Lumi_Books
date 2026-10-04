package com.huangder.lumibooks.util.parser

import android.app.Application
import android.text.Spanned
import android.text.style.LeadingMarginSpan
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class EpubParagraphFormattingTest {
    private val context: Application = ApplicationProvider.getApplicationContext()

    @Test
    fun zeroIndentRemovesPublisherLeadingWhitespace() {
        val parser = EpubParser(context).apply {
            paragraphSpacingDp = 0f
            firstLineIndentChars = 0f
        }

        val formatted = parser.formatParagraphFormattingForTest(
            "\u3000\u3000第一段\n \t\u00A0第二段"
        ) as Spanned

        assertEquals("第一段\n第二段", formatted.toString())
        assertTrue(
            formatted.getSpans(0, formatted.length, LeadingMarginSpan.Standard::class.java)
                .isEmpty()
        )
    }

    @Test
    fun configuredIndentReplacesPublisherWhitespaceAndSkipsChapterTitle() {
        val parser = EpubParser(context).apply {
            paragraphSpacingDp = 0f
            firstLineIndentChars = 1.5f
        }

        val formatted = parser.formatParagraphFormattingForTest(
            "第一章\n\u3000\u3000第一段\n\t第二段",
            chapterTitle = "第一章"
        ) as Spanned
        val indentSpans = formatted.getSpans(
            0,
            formatted.length,
            LeadingMarginSpan.Standard::class.java
        )

        assertEquals("第一章\n第一段\n第二段", formatted.toString())
        assertEquals(2, indentSpans.size)
        val expectedIndentPx = (1.5f * 18f * context.resources.displayMetrics.density).toInt()
        indentSpans.forEach { span ->
            assertEquals(expectedIndentPx, span.getLeadingMargin(true))
            assertTrue(formatted.getSpanStart(span) > "第一章".length)
        }
    }
}
