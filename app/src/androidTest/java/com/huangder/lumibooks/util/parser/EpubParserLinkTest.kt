package com.huangder.lumibooks.util.parser

import android.text.Spanned
import android.text.style.URLSpan
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(AndroidJUnit4::class)
class EpubParserLinkTest {
    @Test
    fun resolvesCrossChapterAnchorToFinalTextOffset() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val epub = File(context.cacheDir, "epub-link-test.epub")
        createTestEpub(epub)

        try {
            val parser = EpubParser(context)
            parser.parse(epub.absolutePath)

            val source = parser.getChapterContent(0) as Spanned
            val link = source.getSpans(0, source.length, URLSpan::class.java).single()
            assertEquals("ch2.xhtml#note", link.url)

            val target = parser.resolveLink(0, link.url)
            requireNotNull(target)
            assertEquals(1, target.chapterIndex)

            val targetText = parser.getChapterContent(target.chapterIndex).toString()
            assertTrue(target.characterOffset > targetText.indexOf("Before target"))
            assertTrue(targetText.substring(target.characterOffset).trimStart().startsWith("Target note"))
        } finally {
            epub.delete()
        }
    }

    private fun createTestEpub(target: File) {
        val entries = linkedMapOf(
            "META-INF/container.xml" to """
                <?xml version="1.0"?>
                <container><rootfiles><rootfile full-path="OEBPS/content.opf"/></rootfiles></container>
            """.trimIndent(),
            "OEBPS/content.opf" to """
                <package>
                  <metadata><dc:title>Link test</dc:title><dc:creator>Test</dc:creator></metadata>
                  <manifest>
                    <item id="ch1" href="Text/ch1.xhtml" media-type="application/xhtml+xml"/>
                    <item id="ch2" href="Text/ch2.xhtml" media-type="application/xhtml+xml"/>
                  </manifest>
                  <spine><itemref idref="ch1"/><itemref idref="ch2"/></spine>
                </package>
            """.trimIndent(),
            "OEBPS/Text/ch1.xhtml" to """
                <html><head><title>One</title></head><body>
                  <p>Source text <a href="ch2.xhtml#note">open note</a></p>
                </body></html>
            """.trimIndent(),
            "OEBPS/Text/ch2.xhtml" to """
                <html><head><title>Two</title></head><body>
                  <p>Before target</p><p id="note">Target note text</p>
                </body></html>
            """.trimIndent()
        )

        ZipOutputStream(target.outputStream().buffered()).use { zip ->
            entries.forEach { (path, content) ->
                zip.putNextEntry(ZipEntry(path))
                zip.write(content.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
        }
    }
}
