package com.huangder.lumibooks.mineru

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class MineruEpubBuilderTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun buildsEpubWithChaptersAndRelativeImages() {
        val work = temporaryFolder.newFolder("work")
        val markdown = File(work, "full.md").apply {
            writeText(
                """# 第一章
                    |正文内容。
                    |
                    |![插图](images/figure.png)
                    |
                    |## 第二章
                    |更多内容。
                """.trimMargin()
            )
        }
        File(work, "images").mkdirs()
        File(work, "images/figure.png").writeBytes(
            byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47)
        )
        val output = File(temporaryFolder.root, "result.epub")

        val result = MineruEpubBuilder().build(
            remoteResult = MineruRemoteResult(markdown, isZip = false),
            outputFile = output,
            workDirectory = work,
            title = "测试书",
            author = "作者"
        )

        assertEquals(2, result.chapterCount)
        ZipInputStream(output.inputStream()).use { zip ->
            assertEquals("mimetype", zip.nextEntry.name)
        }
        ZipFile(output).use { zip ->
            assertNotNull(zip.getEntry("META-INF/container.xml"))
            assertNotNull(zip.getEntry("OEBPS/content.opf"))
            assertNotNull(zip.getEntry("OEBPS/chapter-1.xhtml"))
            assertNotNull(zip.getEntry("OEBPS/chapter-2.xhtml"))
            assertNotNull(zip.getEntry("OEBPS/images/figure.png"))
            val opf = zip.getInputStream(zip.getEntry("OEBPS/content.opf")).bufferedReader().readText()
            assertTrue(opf.contains("<dc:title>测试书</dc:title>"))
        }
    }

    @Test(expected = MineruApiException::class)
    fun rejectsPathTraversalInDownloadedZip() {
        val archive = temporaryFolder.newFile("unsafe.zip")
        ZipOutputStream(archive.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("../full.md"))
            zip.write("# unsafe".toByteArray())
            zip.closeEntry()
        }

        MineruEpubBuilder().build(
            remoteResult = MineruRemoteResult(archive, isZip = true),
            outputFile = File(temporaryFolder.root, "unsafe.epub"),
            workDirectory = temporaryFolder.newFolder("unsafe-work"),
            title = "unsafe",
            author = ""
        )
    }

    @Test
    fun parsesUnknownModeAsDisabled() {
        assertEquals(MineruMode.DISABLED, MineruMode.fromKey("unknown"))
        assertEquals(MineruMode.AGENT, MineruMode.fromKey("agent"))
        assertEquals(MineruMode.PRECISE, MineruMode.fromKey("precise"))
    }
}
