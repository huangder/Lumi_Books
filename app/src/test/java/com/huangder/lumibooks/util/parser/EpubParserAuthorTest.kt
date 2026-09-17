package com.huangder.lumibooks.util.parser

import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * 作者占位文案的归属：`EpubPackageReader` 只还原包里真实存在的 dc:creator，
 * 缺失时由解析层补上占位作者，避免书库出现空作者。
 */
class EpubParserAuthorTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun keepsCreatorFromPackageMetadata() {
        val file = writeEpub(creator = "作者甲")

        assertEquals("作者甲", EpubParser().parse(file.absolutePath).author)
    }

    @Test
    fun fallsBackToPlaceholderWhenPackageHasNoCreator() {
        val file = writeEpub(creator = null)

        val author = EpubParser().parse(file.absolutePath).author

        assertTrue(author.isNotBlank())
        // 单测没有 Context，走的是解析层不依赖资源的兜底文案。
        assertEquals("Unknown author", author)
    }

    private fun writeEpub(creator: String?): File {
        val file = temporaryFolder.newFile("author.epub")
        val metadata = buildString {
            append("<dc:title>作者解析</dc:title>")
            if (creator != null) append("<dc:creator>$creator</dc:creator>")
        }
        val entries = linkedMapOf(
            "META-INF/container.xml" to
                "<container xmlns=\"urn:oasis:names:tc:opendocument:xmlns:container\">" +
                "<rootfiles><rootfile full-path=\"OPS/package.opf\"/></rootfiles></container>",
            "OPS/package.opf" to
                "<package xmlns=\"http://www.idpf.org/2007/opf\" version=\"2.0\">" +
                "<metadata xmlns:dc=\"http://purl.org/dc/elements/1.1/\">$metadata</metadata>" +
                "<manifest><item id=\"c0\" href=\"Text/c0.xhtml\" media-type=\"application/xhtml+xml\"/></manifest>" +
                "<spine><itemref idref=\"c0\"/></spine></package>",
            "OPS/Text/c0.xhtml" to
                "<html xmlns=\"http://www.w3.org/1999/xhtml\"><body><p>正文</p></body></html>"
        )
        ZipOutputStream(file.outputStream().buffered()).use { zip ->
            entries.forEach { (path, content) ->
                zip.putNextEntry(ZipEntry(path))
                zip.write(content.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
        }
        return file
    }
}
