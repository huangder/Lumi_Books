package com.huangder.lumibooks.util

import android.app.Application
import android.graphics.Typeface
import android.text.Spanned
import android.text.style.StyleSpan
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

    private fun withChapter(html: String, indent: Float, verify: (Spanned) -> Unit) {
        val file = File.createTempFile("mobi_indent", ".mobi")
        val parser = MobiParser().apply {
            contentWidth = 800
            paragraphSpacingDp = 0f
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
