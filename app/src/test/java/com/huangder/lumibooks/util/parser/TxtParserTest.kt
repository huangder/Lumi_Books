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
    fun ignoresNumberedAppendixListsWhenBuildingChapterIndex() {
        val text = buildString {
            append("作品正文开头\n")
            repeat(180) { index ->
                append("这是正文第").append(index + 1).append("段，")
                append("用于确保末尾的编号问答不会被识别成整本书的目录。".repeat(3))
                append('\n')
            }
            append("同居三十题\n")
            for (number in 14..30) {
                append(number).append("、 测试问题").append(number).append('\n')
                append("这是对应问题的简短回答。\n")
            }
        }
        val file = writeText("numbered-appendix.txt", text, Charsets.UTF_8)
        val parser = TxtParser()

        val book = parser.parse(file.absolutePath)
        val restored = buildString {
            for (index in 0 until parser.getChapterCount()) {
                append(parser.getChapterContent(index))
            }
        }

        assertTrue(book.chapters.none { it.title.matches(Regex("^\\d{1,3}[.、\\s]")) })
        assertTrue(restored.contains("作品正文开头"))
        assertTrue(restored.contains("21、 测试问题21"))
        assertTrue(restored.contains("这是对应问题的简短回答"))
    }

    @Test
    fun recognizesStandaloneChineseNumeralsAsChapterHeadings() {
        val file = writeText(
            "chinese-numeral-headings.txt",
            "一\n第一部分正文\n二\n第二部分正文\n三\n第三部分正文",
            Charsets.UTF_8
        )
        val parser = TxtParser()

        val book = parser.parse(file.absolutePath)

        assertEquals(listOf("一", "二", "三"), book.chapters.map { it.title })
        assertTrue(parser.getChapterContent(1).contains("第二部分正文"))
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

    @Test
    fun decodesBig5WhenSelectedManually() {
        val file = writeText(
            "big5.txt",
            "第1章 開始\n這裡是繁體正文\n第2章 繼續\n這裡是後續內容",
            Charset.forName("Big5")
        )
        val parser = TxtParser().apply { selectedEncoding = TxtEncoding.BIG5 }

        val book = parser.parse(file.absolutePath)

        assertEquals("Big5", parser.activeCharsetName)
        assertEquals(2, book.chapters.size)
        assertTrue(parser.getChapterContent(0).contains("繁體正文"))
        assertTrue(parser.getChapterContent(1).contains("後續內容"))
    }

    @Test
    fun decodesShiftJisWhenSelectedManually() {
        val file = writeText(
            "shift-jis.txt",
            "Chapter 1\n日本語の本文です\nChapter 2\n次の章です",
            Charset.forName("Shift_JIS")
        )
        val parser = TxtParser().apply { selectedEncoding = TxtEncoding.SHIFT_JIS }

        val book = parser.parse(file.absolutePath)

        assertEquals("Shift_JIS", parser.activeCharsetName)
        assertEquals(2, book.chapters.size)
        assertTrue(parser.getChapterContent(0).contains("日本語の本文です"))
        assertTrue(parser.getChapterContent(1).contains("次の章です"))
    }

    @Test
    fun detectsUtf16WithoutBom() {
        val text = "第1章 开始\n第一段正文\n第2章 继续\n第二段正文"
        val littleEndian = writeText("utf16le-no-bom.txt", text, Charsets.UTF_16LE)
        val bigEndian = writeText("utf16be-no-bom.txt", text, Charsets.UTF_16BE)

        val littleEndianParser = TxtParser()
        val bigEndianParser = TxtParser()
        val littleEndianBook = littleEndianParser.parse(littleEndian.absolutePath)
        val bigEndianBook = bigEndianParser.parse(bigEndian.absolutePath)

        assertEquals("UTF-16LE", littleEndianParser.activeCharsetName)
        assertEquals("UTF-16BE", bigEndianParser.activeCharsetName)
        assertEquals(2, littleEndianBook.chapters.size)
        assertEquals(2, bigEndianBook.chapters.size)
        assertTrue(littleEndianParser.getChapterContent(1).contains("第二段正文"))
        assertTrue(bigEndianParser.getChapterContent(1).contains("第二段正文"))
    }

    @Test
    fun reparsesChaptersAfterSelectedEncodingChanges() {
        val file = writeText(
            "switch-encoding.txt",
            "第1章 開始\n繁體內容甲\n第2章 繼續\n繁體內容乙",
            Charset.forName("Big5")
        )
        val parser = TxtParser().apply { selectedEncoding = TxtEncoding.UTF_8 }

        parser.parse(file.absolutePath)
        parser.selectedEncoding = TxtEncoding.BIG5
        val reparsed = parser.parse(file.absolutePath)

        assertEquals("Big5", parser.activeCharsetName)
        assertEquals(2, reparsed.chapters.size)
        assertTrue(parser.getChapterContent(0).contains("繁體內容甲"))
        assertTrue(parser.getChapterContent(1).contains("繁體內容乙"))
    }

    @Test
    fun appliesEditorOperationsInOrder() {
        val operations = listOf<TxtEditOperation>(
            TxtSetChapterText(0, "Alpha alpha beta"),
            TxtReplaceText(
                chapterIndex = null,
                query = "alpha",
                replacement = "done",
                ignoreCase = true
            ),
            TxtReplaceText(
                chapterIndex = 1,
                query = "beta",
                replacement = "other",
                ignoreCase = false
            ),
            TxtReplaceRange(0, 0, 4, "X")
        )

        assertEquals("X done beta", applyTxtEditOperations(0, "original", operations))
        assertEquals("original", applyTxtEditOperations(2, "original", operations))
    }

    @Test
    fun rewritesMultipleChaptersAndPreservesUtf8BomAndLineEndings() {
        val file = temporaryFolder.newFile("rewrite-all.txt")
        file.outputStream().use { output ->
            output.write(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()))
            output.write(
                "第1章 开始\r\n正文甲\r\n\r\n第2章 继续\r\n正文乙\r\n"
                    .toByteArray(Charsets.UTF_8)
            )
        }
        val parser = TxtParser()
        parser.parse(file.absolutePath)

        val result = parser.rewriteWithOperations(
            listOf(
                TxtSetChapterText(0, "第1章 开始\r\n草稿甲"),
                TxtReplaceText(
                    chapterIndex = null,
                    query = "正文",
                    replacement = "内容",
                    ignoreCase = false
                )
            )
        )

        val bytes = file.readBytes()
        assertTrue(result.success)
        assertTrue(bytes.take(3) == listOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()))
        val text = String(bytes, 3, bytes.size - 3, Charsets.UTF_8)
        assertTrue(text.contains("草稿甲"))
        assertTrue(text.contains("内容乙"))
        assertTrue(text.contains("\r\n"))
        assertTrue(parser.getChapterContent(1).contains("内容乙"))
    }

    @Test
    fun replacesAllMatchesWithoutChangingGbkEncoding() {
        val charset = Charset.forName("GBK")
        val file = writeText(
            "rewrite-gbk.txt",
            "第1章 开始\n中文目标 保持正常\n第2章 继续\n中文目标 仍然正常",
            charset
        )
        val parser = TxtParser().apply { parse(file.absolutePath) }

        val result = parser.rewriteWithOperations(
            listOf(
                TxtReplaceText(
                    chapterIndex = null,
                    query = "目标",
                    replacement = "完成",
                    ignoreCase = false
                )
            )
        )

        val decoded = String(file.readBytes(), charset)
        assertTrue(result.success)
        assertEquals(2, result.changedChapterCount)
        assertTrue(decoded.contains("中文完成 保持正常"))
        assertTrue(decoded.contains("中文完成 仍然正常"))
        assertFalse(decoded.contains("目标"))
        assertTrue(parser.getChapterContent(0).contains("中文完成 保持正常"))
    }

    @Test
    fun editorRewriteCanCommitWithoutSynchronouslyReparsing() {
        val file = writeText(
            "editor-fast-save.txt",
            "第1章 开始\n正文甲\n第2章 继续\n正文乙",
            Charsets.UTF_8
        )
        val parser = TxtParser()
        parser.parse(file.absolutePath)

        val result = parser.rewriteWithOperations(
            operations = listOf(TxtSetChapterText(0, "第1章 开始\n已经保存")),
            reparseAfterWrite = false
        )

        assertTrue(result.success)
        assertTrue(file.readText(Charsets.UTF_8).contains("已经保存"))
        val reopened = TxtParser().apply { parse(file.absolutePath) }
        assertTrue(reopened.getChapterContent(0).contains("已经保存"))
    }

    @Test
    fun abortsRewriteWhenSelectedEncodingCannotRepresentDraft() {
        val file = writeText(
            "windows-1252.txt",
            "Chapter 1\r\nplain text\r\nChapter 2\r\nmore text",
            Charset.forName("windows-1252")
        )
        val original = file.readBytes()
        val parser = TxtParser().apply { selectedEncoding = TxtEncoding.WINDOWS_1252 }
        parser.parse(file.absolutePath)

        val result = parser.rewriteWithOperations(
            listOf(TxtSetChapterText(0, "Chapter 1\r\n无法编码"))
        )

        assertFalse(result.success)
        assertTrue(original.contentEquals(file.readBytes()))
    }

    private fun writeText(name: String, text: String, charset: Charset): File {
        return temporaryFolder.newFile(name).apply { writeBytes(text.toByteArray(charset)) }
    }
}
