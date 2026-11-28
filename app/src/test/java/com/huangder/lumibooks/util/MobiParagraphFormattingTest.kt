package com.huangder.lumibooks.util

import android.app.Application
import android.graphics.Paint
import android.graphics.Typeface
import android.text.Spanned
import android.text.style.StyleSpan
import com.huangder.lumibooks.util.parser.EpubParser.ParagraphLineHeightSpan
import com.huangder.lumibooks.util.parser.MobiParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class MobiParagraphFormattingTest {
    @Test
    fun configuredIndentReplacesPublisherWhitespace() {
        withChapter(
            "<p>\u3000\u3000<b>第一段</b>中间 空格</p>" +
                "<p> \t&nbsp;&nbsp;第二段</p>" +
                "<p>${"&nbsp;".repeat(40)}第三段</p>",
            indent = 2f
        ) { text ->
            assertEquals("\u3000\u3000第一段中间 空格\n\u3000\u3000第二段\n\u3000\u3000第三段\n", text.toString())
            val boldStart = text.toString().indexOf("第一段")
            assertTrue(text.getSpans(boldStart, boldStart + 3, StyleSpan::class.java)
                .any { it.style == Typeface.BOLD })
        }
    }

    @Test
    fun zeroIndentRemovesPublisherWhitespace() {
        withChapter("<p>\u3000\u3000第一段</p><p> \t&nbsp;第二段</p>", indent = 0f) { text ->
            assertEquals("第一段\n第二段\n", text.toString())
        }
    }

    @Test
    fun formattingSkipsHeadingsAndWhitespaceOnlyParagraphs() {
        withChapter("<h1>章节标题</h1><p> \t&nbsp;</p><p>正文</p>", indent = 2f) { text ->
            assertEquals("章节标题\n \t\u00A0\n\u3000\u3000正文\n", text.toString())
        }
    }

    @Test
    fun paragraphSpacingAddsOnlyTheConfiguredGap() {
        for (gap in listOf(2f, 8f, 24f)) {
            withChapter("<p>第一段</p><p>第二段</p>", indent = 0f, gap = gap) { text ->
                assertEquals("第一段\n\n第二段\n", text.toString())
                val spacer = text.getSpans(0, text.length, ParagraphLineHeightSpan::class.java).single()
                val metrics = Paint.FontMetricsInt().apply { ascent = -20; descent = 5 }
                spacer.chooseHeight(text, text.getSpanStart(spacer), text.getSpanEnd(spacer), 0, 0, metrics)
                assertEquals((gap * 2.75f).toInt(), metrics.descent - metrics.ascent)
            }
        }
    }

    private fun withChapter(html: String, indent: Float, gap: Float = 0f, verify: (Spanned) -> Unit) {
        val file = File.createTempFile("mobi_indent", ".mobi")
        val parser = MobiParser().apply {
            contentWidth = 800
            paragraphSpacingDp = gap
            firstLineIndentChars = indent
        }
        try {
            MobiTestFixtures.writeMobiFile(
                file,
                listOf("<html><body>$html</body></html>".toByteArray(Charsets.UTF_8))
            )
            parser.parse(file.absolutePath)
            verify(parser.getChapterContent(0) as Spanned)
        } finally {
            parser.close()
            file.delete()
        }
    }
}
