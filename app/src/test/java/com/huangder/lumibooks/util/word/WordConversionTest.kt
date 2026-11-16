package com.huangder.lumibooks.util.word

import com.huangder.lumibooks.util.epub.EpubPackageReader
import org.apache.poi.poifs.filesystem.POIFSFileSystem
import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.RandomAccessFile
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

class WordConversionTest {
    @get:Rule val folder = TemporaryFolder()

    @Test
    fun `both Word formats retain the text, structure, assets and stable anchors`() {
        for (extension in listOf("docx", "doc")) {
            val original = fixture(extension)
            val first = convert(original, extension)
            val rebuilt = convert(original, extension)
            assertTrue(first.readBytes().contentEquals(rebuilt.readBytes()))
            val book = EpubPackageReader.read(first.absolutePath)
            assertEquals("Lumi Word sample", book.title)
            assertEquals("Lumi Books", book.author)
            ZipFile(first).use { zip ->
                val body = Jsoup.parse(zip.text("OEBPS/document.xhtml"))
                val nav = Jsoup.parse(zip.text("OEBPS/nav.xhtml"))
                assertTrue(body.text().contains("这里是可以搜索和选中的正文。"))
                assertTrue(body.text().contains("列表项目"))
                assertEquals(listOf("单元格甲", "单元格乙"), body.select("td").map { it.text() })
                val image = body.select("img").single().attr("src")
                assertTrue(zip.getEntry("OEBPS/$image").size > 0L)
                if (extension == "docx") {
                    assertTrue(body.select("a[href]").any { it.attr("href") == "https://example.com/word-fixture" })
                    val note = body.select("a[href]").single { it.attr("epub:type") == "noteref" }
                    assertTrue(body.getElementById(note.attr("href").removePrefix("#"))?.text()?.contains("脚注内容") == true)
                }
                val links = nav.select("nav a")
                assertEquals(listOf("第一章 开始阅读", "第二章 继续阅读"), links.map { it.text() })
                assertTrue(links.all { body.getElementById(it.attr("href").substringAfter('#')) != null })
            }
        }
    }

    @Test
    fun `only packaged images and safe user-activated links survive`() {
        val stage = folder.newFolder()
        val target = File(stage, "safe.epub")
        val image = File(stage, "image.png").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        WordEpubWriter.write("""
            <style>.emphasis {font-weight:bold;background:url(https://example.com/pixel)}</style>
            <h1 id="section">标题</h1><p class="emphasis">正文</p>
            <script>example()</script><iframe src="https://example.com"></iframe>
            <img src="images/image-1.png" onload="example()"/><img src="https://example.com/remote.png"/>
            <a href="javascript:example()">unsafe</a><a href="#section">anchor</a>
            <a href="https://example.com/" ping="https://example.com/pixel">external</a>
        """.trimIndent(), WordMetadata(), mapOf("images/image-1.png" to image), "test", target)
        ZipFile(target).use { zip ->
            val body = Jsoup.parse(zip.text("OEBPS/document.xhtml"))
            assertEquals("font-weight:bold", body.selectFirst("p")?.attr("style"))
            assertEquals(listOf("images/image-1.png"), body.select("img").eachAttr("src"))
            assertEquals(listOf("#section", "https://example.com/"), body.select("a[href]").eachAttr("href"))
            assertTrue(body.select("script, iframe, [onload], [ping]").isEmpty())
        }
    }

    @Test
    fun `custom Word outlines and inline images survive with missing or misleading properties`() {
        val original = fixture("docx")
        val custom = folder.newFile("中文书名.docx")
        ZipFile(original).use { source ->
            ZipOutputStream(custom.outputStream()).use { target ->
                for (entry in source.entries().asSequence()) {
                    target.putNextEntry(ZipEntry(entry.name))
                    val bytes = source.getInputStream(entry).use { it.readBytes() }
                    val xml = bytes.toString(Charsets.UTF_8)
                    val replacement = when (entry.name) {
                        "docProps/core.xml" -> xml.replace(Regex("<dc:title>.*?</dc:title>"), "<dc:title />")
                            .replace("xmlns:dc=", "xmlns:metadata=").replace("dc:", "metadata:")
                            .replace("Lumi Books", "中文作者").toByteArray()
                        "word/styles.xml" -> xml.replace("</w:styles>", """
                            <w:style w:type="paragraph" w:styleId="自定义章"><w:name w:val="自定义大章"/>
                            <w:pPr><w:outlineLvl w:val="0"/></w:pPr></w:style>
                            </w:styles>
                        """.trimIndent()).toByteArray()
                        "word/document.xml" -> xml.replaceFirst("<w:body>", "<w:body><w:p><w:r><w:t>第一章之前的导言</w:t></w:r></w:p>")
                            .replaceFirst("<w:pStyle w:val=\"Heading1\" />", "<w:pStyle w:val=\"自定义章\" />")
                            .replace("<w:pStyle w:val=\"Heading1\" />", "<w:outlineLvl w:val=\"1\" />")
                            .toByteArray()
                        "[Content_Types].xml" -> xml.replace("image/png", "application/octet-stream").toByteArray()
                        else -> bytes
                    }
                    target.write(replacement)
                    target.closeEntry()
                }
            }
        }
        val converted = convert(custom, "docx")
        val publication = EpubPackageReader.read(converted.absolutePath, fallbackTitle = "")
        assertEquals("", publication.title)
        assertEquals("中文作者", publication.author)
        assertEquals(listOf("Word", "第一章 开始阅读", "第二章 继续阅读"), publication.navigation.map { it.title })
        assertEquals(listOf(1, 1, 2), publication.navigation.map { it.level })
        ZipFile(converted).use { zip ->
            val body = Jsoup.parse(zip.text("OEBPS/document.xhtml"))
            assertTrue(body.text().contains("第一章之前的导言"))
            assertEquals("第一章 开始阅读", body.selectFirst("h1")?.text())
            assertEquals("第二章 继续阅读", body.selectFirst("h2")?.text())
            val image = body.select("img").single().attr("src")
            assertTrue(zip.getEntry("OEBPS/$image").size > 0L)
        }
    }

    @Test
    fun `plain chapter paragraphs generate a usable outline without Word styles`() {
        val target = folder.newFile("chapters.epub")
        WordEpubWriter.write("<p>第一章 开始</p><p>正文</p><p>第二章 结束</p><p>正文</p>",
            WordMetadata(), emptyMap(), "test", target)
        val navigation = EpubPackageReader.read(target.absolutePath).navigation
        assertEquals(listOf("第一章 开始", "第二章 结束"), navigation.map { it.title })
        assertTrue(navigation.all { "#lumi-heading-" in it.href })
    }

    @Test
    fun `encrypted damaged and oversized inputs produce distinct failures`() {
        val encrypted = folder.newFile("encrypted.docx")
        POIFSFileSystem().use { fs ->
            fs.root.createDocument("EncryptedPackage", byteArrayOf(1).inputStream())
            encrypted.outputStream().use(fs::writeFilesystem)
        }
        val damaged = folder.newFile("damaged.doc").apply { writeText("broken") }
        val huge = folder.newFile("huge.docx")
        RandomAccessFile(huge, "rw").use { it.setLength(MAX_WORD_BYTES + 1) }
        for ((file, expected) in listOf(encrypted to WordFailure.ENCRYPTED, damaged to WordFailure.INVALID, huge to WordFailure.LIMIT)) {
            assertEquals(expected, assertThrows(WordException::class.java) { convert(file, file.extension) }.reason)
        }
    }

    @Test
    fun `an interrupted conversion leaves no publication and can be retried`() {
        val original = fixture("docx")
        val stage = folder.newFolder()
        val output = File(stage, "result.epub")
        try {
            Thread.currentThread().interrupt()
            assertThrows(java.util.concurrent.CancellationException::class.java) {
                WordConverter.convert(original, "docx", "test", stage, output)
            }
        } finally {
            Thread.interrupted()
        }
        assertFalse(output.exists())
        WordConverter.convert(original, "docx", "test", stage, output)
        assertTrue(output.isFile)
    }

    private fun fixture(extension: String): File = folder.newFile("reading.$extension").apply {
        checkNotNull(WordConversionTest::class.java.getResourceAsStream("/word/reading.$extension")).use { input ->
            outputStream().use { input.copyTo(it) }
        }
    }

    private fun convert(source: File, extension: String): File {
        val stage = folder.newFolder()
        return File(stage, "result.epub").also { WordConverter.convert(source, extension, "sample", stage, it) }
    }

    private fun ZipFile.text(path: String): String = getInputStream(getEntry(path)).bufferedReader().use { it.readText() }
}
