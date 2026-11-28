package com.huangder.lumibooks.util.parser

import android.app.Application
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ImageSpan
import android.text.style.LeadingMarginSpan
import android.text.style.URLSpan
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
    fun changingIndentNormalizesAnchoredBodyAndKeepsLinkTargets() = withBook(
        """<html><body><h1>第一章</h1>
            <p id="first">　　<a id="nested"></a>&nbsp;第一段正文</p>
            <p>　　<a id="second"></a>第二段正文</p>
            <p><a href="#second">跳到第二段</a></p></body></html>"""
    ) { parser ->
        parser.paragraphSpacingDp = 4f
        for (indent in listOf(2f, 0f, 1.5f)) {
            parser.firstLineIndentChars = indent
            parser.clearHtmlCache()
            val preview = parser.getChapterContentPreview(0, 400, 0, 1000, 24f)
            val full = parser.getChapterContent(0, 400, 0, 24f)
            val pagination = parser.getChapterContentForPagination(0, 400, 200, 24f)
            assertTrue(preview.isComplete)
            for (text in listOf(preview.text, full, pagination)) {
                assertEquals("第一章\n\n第一段正文\n\n第二段正文\n\n跳到第二段\n", text.toString())
                val spanned = text as Spanned
                for (paragraph in listOf("第一段正文", "第二段正文")) {
                    val start = text.indexOf(paragraph)
                    val span = spanned.getSpans(start, start + 1, LeadingMarginSpan::class.java)
                        .singleOrNull()
                    assertEquals((indent * 24f).roundToInt(), span?.getLeadingMargin(true) ?: 0)
                }
            }
            assertEquals(full.indexOf("第一段正文"), parser.resolveLink(0, "#first")?.characterOffset)
            assertEquals(full.indexOf("第一段正文"), parser.resolveLink(0, "#nested")?.characterOffset)
            assertEquals(full.indexOf("第二段正文"), parser.resolveLink(0, "#second")?.characterOffset)
        }
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

    @Test
    fun inlineFootnoteImageDoesNotRemoveIndentFromItsBodyParagraph() {
        for (markerAt in listOf(0, 6, 60)) {
            withBook(footnoteChapter(markerAt)) { parser ->
                val textSizePx = 24f
                val text = parser.getChapterContent(0, 320, 0, textSizePx) as Spanned
                val marker = text.getSpans(0, text.length, ImageSpan::class.java).single {
                    (it.drawable as? InlineFootnoteMarkerDrawable)?.isInlineFootnoteMarker == true
                }
                val noteStart = text.toString().lastIndexOf('\n', text.getSpanStart(marker)) + 1
                val noteEnd = text.toString().indexOf('\n', noteStart)
                val indents = text.getSpans(noteStart, noteEnd, LeadingMarginSpan::class.java)
                assertEquals("Footnote at $markerAt removed the paragraph indent", 1, indents.size)
                assertEquals(noteStart, text.getSpanStart(indents.single()))
                assertEquals(48, indents.single().getLeadingMargin(true))
                assertEquals(0, indents.single().getLeadingMargin(false))
                assertTrue("Publisher whitespace was not removed", text[noteStart] != '\u3000')

                // The heading and standalone illustration must keep their original placement.
                assertTrue(text.getSpans(0, "第一章".length, LeadingMarginSpan::class.java).isEmpty())
                val illustration = text.getSpans(0, text.length, ImageSpan::class.java).single { it !== marker }
                assertTrue(text.getSpans(text.getSpanStart(illustration), text.getSpanEnd(illustration),
                    LeadingMarginSpan::class.java).isEmpty())
            }
        }
    }

    @Test
    fun weReadFootnotesStayInlineAndResolveAlongsideLinkedFootnotes() = withBook(
        """<html><body><p>正文<span class="reader_footer_note js_readerFooterNote"
            data-wr-footernote="中文 &amp; &lt;cite&gt;English&lt;/cite&gt;"></span>后文<a epub:type="noteref" href="#fn">[1]</a></p>
            <p>${"正文内容".repeat(100)}</p>
            <p>下一段<span class="reader_footer_note" data-wr-footernote="第二条注释"></span>末尾</p>
            <aside epub:type="footnote" id="fn">链接注释</aside></body></html>"""
    ) { parser ->
        val text = parser.getChapterContent(0) as Spanned
        assertTrue(text.contains("正文注后文"))
        assertTrue(text.contains("下一段注末尾"))
        assertTrue(!text.contains("链接注释"))
        val links = text.getSpans(0, text.length, URLSpan::class.java).sortedBy(text::getSpanStart)
        assertEquals(3, links.size)
        assertTrue(!parser.getChapterContentPreview(0, 320, 0, 70).isComplete)
        assertEquals(
            listOf("中文 & <cite>English</cite>", "链接注释", "第二条注释"),
            links.map { link ->
                assertTrue(parser.isFootnoteHref(0, link.url))
                parser.resolveFootnoteText(0, link.url)
            }
        )
    }

    @Test
    fun zeroIndentRemovesPublisherWhitespaceFromParagraphWithFootnoteImage() =
        withBook(footnoteChapter(6)) { parser ->
            parser.firstLineIndentChars = 0f
            val text = parser.getChapterContent(0, 320, 0, 24f) as Spanned
            assertTrue("Footnote paragraph kept its publisher indent", !text.contains('\u3000'))
            assertTrue(text.getSpans(0, text.length, LeadingMarginSpan::class.java).isEmpty())
            assertEquals(2, text.getSpans(0, text.length, ImageSpan::class.java).size)
        }

    @Test
    fun unclassifiedInlineImageDoesNotSuppressIndentOfVisibleText() {
        // Missing-image placeholders may have no footnote drawable metadata.
        val input = SpannableStringBuilder("　　正文\uFFFC后文\n\uFFFC")
        for (index in input.indices.filter { input[it] == '\uFFFC' }) {
            val drawable = ColorDrawable(Color.RED).apply { setBounds(0, 0, 16, 16) }
            input.setSpan(ImageSpan(drawable), index, index + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        val parser = EpubParser(context).apply {
            firstLineIndentChars = 1.5f
            paragraphSpacingDp = 4f
        }
        val text = parser.formatParagraphFormattingForTest(input, textSizePx = 24f) as Spanned
        assertEquals("正文\uFFFC后文\n\n\uFFFC", text.toString())
        val indent = text.getSpans(0, text.length, LeadingMarginSpan::class.java).single()
        assertEquals(0, text.getSpanStart(indent))
        assertEquals("正文\uFFFC后文".length, text.getSpanEnd(indent))
        assertEquals(36, indent.getLeadingMargin(true))
        assertEquals(2, text.getSpans(0, text.length, ImageSpan::class.java).size)
    }

    @Test
    fun pagedFootnoteParagraphIndentsItsStartButNotItsContinuation() =
        withBook(footnoteChapter(60)) { parser ->
            val text = parser.getChapterContent(0, 320, 0, 24f)
            val page = PageContentView(context).apply {
                configure(fontSizePx = 24f, textColor = Color.BLACK,
                    marginLeftPx = 0f, marginRightPx = 0f, marginTopPx = 0f, marginBottomPx = 0f)
            }
            val noteStart = text.indexOf("注释段落")
            val end = text.indexOf("后续正文") + "后续正文".length
            fun show(start: Int) {
                page.setPageContent(text, start, end)
                page.measure(View.MeasureSpec.makeMeasureSpec(320, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(2000, View.MeasureSpec.EXACTLY))
                page.layout(0, 0, 320, 2000)
            }
            show(noteStart)
            assertEquals(48f, page.textView.layout.getPrimaryHorizontal(0), 0.01f)
            val continuation = noteStart + page.textView.layout.getLineStart(1)
            assertTrue(continuation > noteStart && text[continuation - 1] != '\n')
            show(continuation)
            assertEquals(0f, page.textView.layout.getPrimaryHorizontal(0), 0.01f)
            val next = page.textView.text.indexOf("后续正文")
            assertEquals(48f, page.textView.layout.getPrimaryHorizontal(next), 0.01f)
        }

    private fun footnoteChapter(markerAt: Int): String {
        val body = "注释段落" + "这是一段带有行内脚注图标的长正文。".repeat(6)
        val reference = """<a epub:type="noteref" href="#note-1"><img src="marker.svg"/></a>"""
        return """<html xmlns:epub="http://www.idpf.org/2007/ops"><body><h1>第一章</h1>
            <p>普通正文</p><p>　　${body.take(markerAt)}$reference${body.drop(markerAt)}</p>
            <p>后续正文</p><p><img src="illustration.svg"/></p>
            <aside epub:type="footnote" id="note-1">这是一条注释。</aside></body></html>"""
    }

    private fun withBook(
        html: String = """<html><body><p>　　${"这是用于测试首行缩进的长段落".repeat(12)}</p><p>下一段正文</p></body></html>""",
        verify: (EpubParser) -> Unit
    ) {
        val file = File.createTempFile("reader-indent", ".epub", context.cacheDir)
        writeEpub(file, mapOf(
            "META-INF/container.xml" to """<container xmlns="urn:oasis:names:tc:opendocument:xmlns:container"><rootfiles><rootfile full-path="book.opf"/></rootfiles></container>""",
            "book.opf" to """<package xmlns="http://www.idpf.org/2007/opf" version="2.0"><metadata xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:title>缩进测试</dc:title></metadata><manifest><item id="body" href="body.xhtml" media-type="application/xhtml+xml"/></manifest><spine><itemref idref="body"/></spine></package>""",
            "body.xhtml" to html,
            "marker.svg" to """<svg xmlns="http://www.w3.org/2000/svg" width="16" height="16"><rect width="16" height="16" fill="red"/></svg>""",
            "illustration.svg" to """<svg xmlns="http://www.w3.org/2000/svg" width="200" height="100"><rect width="200" height="100" fill="blue"/></svg>"""
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
