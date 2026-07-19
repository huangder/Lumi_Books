package com.huangder.lumibooks.util.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.charset.Charset

class TxtParserTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun parsesUtf8ChaptersWithoutStoringContentInMetadata() {
        val file = writeText(
            "utf8.txt",
            "第1章 开始\n这里是开篇正文\n\n第2章 继续\n这里是后续正文",
            Charsets.UTF_8
        )
        val parser = TxtParser()

        val book = parser.parse(file.absolutePath)

        assertEquals(2, book.chapters.size)
        assertEquals("", book.chapters.first().content)
        assertTrue(parser.getChapterContent(0).contains("开篇正文"))
        assertTrue(parser.getChapterContent(1).contains("后续正文"))
    }

    @Test
    fun detectsGbkAndUtf16Bom() {
        val gbk = writeText(
            "gbk.txt",
            "第1章 甲\n中文甲\n第2章 乙\n中文乙",
            Charset.forName("GBK")
        )
        val utf16 = temporaryFolder.newFile("utf16.txt").apply {
            outputStream().use { output ->
                output.write(byteArrayOf(0xFF.toByte(), 0xFE.toByte()))
                output.write("第1章 一\n正文一\n第2章 二\n正文二".toByteArray(Charsets.UTF_16LE))
            }
        }

        val gbkParser = TxtParser().also { it.parse(gbk.absolutePath) }
        val utf16Parser = TxtParser().also { it.parse(utf16.absolutePath) }

        assertTrue(gbkParser.getChapterContent(1).contains("中文乙"))
        assertTrue(utf16Parser.getChapterContent(0).contains("正文一"))
        assertTrue(utf16Parser.getChapterContent(1).contains("正文二"))
    }

    @Test
    fun splitsFallbackTextNearTargetSize() {
        val text = buildString {
            repeat(100) { index -> append("段落$index ").append("内容".repeat(20)).append('\n') }
        }
        val file = writeText("fallback.txt", text, Charsets.UTF_8)
        val parser = TxtParser()

        parser.parse(file.absolutePath)

        assertTrue(parser.getChapterCount() >= 2)
        for (index in 0 until parser.getChapterCount()) {
            assertTrue(parser.getChapterContent(index).length <= 32_000)
        }
    }

    @Test
    fun preservesUtf8CharactersWhenSplittingOneLongLine() {
        val text = "大".repeat(70_000)
        val file = writeText("long-line.txt", text, Charsets.UTF_8)
        val parser = TxtParser()

        parser.parse(file.absolutePath)

        val restored = buildString {
            for (index in 0 until parser.getChapterCount()) {
                val chapter = parser.getChapterContent(index).toString()
                assertTrue(chapter.length <= 32_000)
                append(chapter)
            }
        }
        assertEquals(text, restored)
    }

    @Test
    fun escapesHtmlOnDemand() {
        val file = writeText("escape.txt", "A & B < C > D", Charsets.UTF_8)
        val parser = TxtParser()
        parser.parse(file.absolutePath)

        val html = parser.getChapterHtml(0)

        assertTrue(html.contains("A &amp; B &lt; C &gt; D"))
        assertFalse(html.contains("A & B < C > D"))
    }

    @Test
    fun indexesFourteenMegabyteTxtWithoutReadingWholeBookIntoChapters() {
        val file = temporaryFolder.newFile("large.txt")
        val block = ByteArray(64 * 1024) { 'a'.code.toByte() }
        file.outputStream().buffered().use { output ->
            repeat(14 * 1024 * 1024 / block.size) { output.write(block) }
        }
        val parser = TxtParser()

        val book = parser.parse(file.absolutePath)

        assertTrue(book.chapters.size > 100)
        assertTrue(book.chapters.all { it.content.isEmpty() && it.htmlContent.isEmpty() })
        assertTrue(parser.getChapterContent(0).isNotEmpty())
        assertTrue(parser.getChapterContent(book.chapters.lastIndex / 2).isNotEmpty())
        assertTrue(parser.getChapterContent(book.chapters.lastIndex).isNotEmpty())
    }

    @Test(timeout = 5_000)
    fun indexesManyLineNovelWithoutOpeningTheFilePerLine() {
        val file = temporaryFolder.newFile("many-lines.txt")
        file.bufferedWriter(Charsets.UTF_8).use { writer ->
            repeat(2_500) { chapter ->
                writer.append("第${chapter + 1}章 测试章节").append('\n')
                repeat(50) { line ->
                    writer.append("这是第${chapter + 1}章第${line + 1}行正文，")
                    writer.append("用于验证大量短行不会触发逐行文件打开。".repeat(4))
                    writer.append('\n')
                }
            }
        }
        val parser = TxtParser()

        val book = parser.parse(file.absolutePath)

        assertEquals(2_500, book.chapters.size)
        assertTrue(parser.getChapterContent(1_250).contains("第1251章"))
    }

    private fun writeText(name: String, text: String, charset: Charset): File {
        return temporaryFolder.newFile(name).apply { writeBytes(text.toByteArray(charset)) }
    }
}
