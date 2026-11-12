package com.huangder.lumibooks.util.parser

import android.app.Application
import android.graphics.Color
import android.text.Spanned
import android.text.style.LeadingMarginSpan
import android.view.View
import androidx.test.core.app.ApplicationProvider
import com.huangder.lumibooks.ui.reader.engine.PageContentView
import com.huangder.lumibooks.util.epub.writeEpub
import java.io.File
import kotlin.math.roundToInt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
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
        val expectedIndentPx = (1.5f * 18f * context.resources.displayMetrics.density).roundToInt()
        indentSpans.forEach { span ->
            assertEquals(expectedIndentPx, span.getLeadingMargin(true))
            assertTrue(formatted.getSpanStart(span) > "第一章".length)
        }
    }

    @Test
    fun readerSizeControlsIndentInFullPreviewAndPaginationCaches() = withBook { parser ->
        val density = context.resources.displayMetrics.density
        for (fontSize in listOf(20f, 30f, 14f, 20f)) {
            val textSizePx = fontSize * density
            val expected = (2f * textSizePx).roundToInt()
            // A complete preview is also promoted into the full chapter cache.
            val preview = parser.getChapterContentPreview(0, 400, 0, 10000, textSizePx)
            assertTrue(preview.isComplete)
            val full = parser.getChapterContent(0, 400, 0, textSizePx)
            assertSame(preview.text, full)
            val pagination = parser.getChapterContentForPagination(0, 400, 180, textSizePx)
            for (text in listOf(preview.text, full, pagination)) {
                val spanned = text as Spanned
                val spans = spanned.getSpans(0, text.length, LeadingMarginSpan.Standard::class.java)
                assertEquals(2, spans.size)
                spans.forEach {
                    assertEquals("font=$fontSize", expected, it.getLeadingMargin(true))
                    assertEquals(0, it.getLeadingMargin(false))
                }
            }
        }
    }

    @Test
    fun pagedTextUsesCurrentIndentAndDoesNotIndentContinuationLines() = withBook { parser ->
        val textSizePx = 20f * context.resources.displayMetrics.density
        val text = parser.getChapterContent(0, 400, 0, textSizePx)
        val page = PageContentView(context).apply {
            configure(fontSizePx = textSizePx, textColor = Color.BLACK,
                marginLeftPx = 0f, marginRightPx = 0f, marginTopPx = 0f, marginBottomPx = 0f)
        }
        fun show(start: Int) {
            page.setPageContent(text, start, text.length)
            page.measure(View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY))
            page.layout(0, 0, 400, 1000)
        }
        show(0)
        val expected = (2f * textSizePx).roundToInt().toFloat()
        assertEquals(expected, page.textView.layout.getPrimaryHorizontal(0), 0.01f)
        val continuation = page.chapterStartOffset + page.textView.layout.getLineStart(1)
        assertTrue(continuation > 0 && text[continuation - 1] != '\n')
        show(continuation)
        assertEquals(0f, page.textView.layout.getPrimaryHorizontal(0), 0.01f)
        val secondParagraph = page.textView.text.indexOf("下一段")
        assertTrue(secondParagraph > 0)
        val secondLine = page.textView.layout.getLineForOffset(secondParagraph)
        assertEquals(expected, page.textView.layout.getPrimaryHorizontal(
            page.textView.layout.getLineStart(secondLine)), 0.01f)
    }

    private fun withBook(verify: (EpubParser) -> Unit) {
        val file = File.createTempFile("reader-indent", ".epub", context.cacheDir)
        writeEpub(file, mapOf(
            "META-INF/container.xml" to """<container xmlns="urn:oasis:names:tc:opendocument:xmlns:container"><rootfiles><rootfile full-path="book.opf"/></rootfiles></container>""",
            "book.opf" to """<package xmlns="http://www.idpf.org/2007/opf" version="2.0"><metadata xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:title>缩进测试</dc:title></metadata><manifest><item id="body" href="body.xhtml" media-type="application/xhtml+xml"/></manifest><spine><itemref idref="body"/></spine></package>""",
            "body.xhtml" to """<html><body><p>　　${"这是用于测试首行缩进的长段落".repeat(12)}</p><p>下一段正文</p></body></html>"""
        ))
        val parser = EpubParser(context).apply { firstLineIndentChars = 2f }
        try {
            parser.parse(file.absolutePath)
            verify(parser)
        } finally {
            parser.close()
            file.delete()
        }
    }
}
