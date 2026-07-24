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

    @Test
    fun mapsNestedVolumeTocLinksToTheirOwnChapterFiles() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val epub = File(context.cacheDir, "epub-volume-toc-test.epub")
        createVolumeTocEpub(epub)

        try {
            val content = EpubParser(context).parse(epub.absolutePath)
            val chapters = content.tocEntries.filterNot { it.isGroup }

            assertEquals(listOf("第一章", "第一章"), chapters.map { it.title })
            assertEquals(listOf(0, 1), chapters.map { it.chapterIndex })
        } finally {
            epub.delete()
        }
    }

    @Test
    fun preservesTocAnchorsForChaptersInTheSameSpineFile() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val epub = File(context.cacheDir, "epub-toc-anchor-test.epub")
        createAnchorTocEpub(epub)

        try {
            val parser = EpubParser(context)
            val content = parser.parse(epub.absolutePath)
            val entries = content.tocEntries.filterNot { it.isGroup }

            assertEquals(listOf(0, 0), entries.map { it.chapterIndex })
            assertEquals(listOf("volume-one", "volume-two"), entries.map { it.anchor })

            val secondTarget = parser.resolveLink(0, "#${entries[1].anchor}")
            requireNotNull(secondTarget)
            assertTrue(secondTarget.characterOffset > 0)
        } finally {
            epub.delete()
        }
    }

    @Test
    fun readsTheCorrectVolumeWhenTheOpfIsAtTheEpubRoot() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val epub = File(context.cacheDir, "epub-root-opf-volume-test.epub")
        createRootOpfVolumeEpub(epub)

        try {
            val parser = EpubParser(context)
            val content = parser.parse(epub.absolutePath)

            assertEquals(listOf(0, 1), content.tocEntries.map { it.chapterIndex })
            assertTrue(parser.getChapterContent(1).toString().contains("VOLUME_TWO"))
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

    private fun createVolumeTocEpub(target: File) {
        val entries = linkedMapOf(
            "META-INF/container.xml" to """
                <?xml version="1.0"?>
                <container><rootfiles><rootfile full-path="OEBPS/content.opf"/></rootfiles></container>
            """.trimIndent(),
            "OEBPS/content.opf" to """
                <package>
                  <metadata><dc:title>Volume TOC test</dc:title><dc:creator>Test</dc:creator></metadata>
                  <manifest>
                    <item id="vol1" href="Volume-1/chapter.xhtml" media-type="application/xhtml+xml"/>
                    <item id="vol2" href="Volume-2/chapter.xhtml" media-type="application/xhtml+xml"/>
                    <item id="nav" href="Volume-2/nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>
                  </manifest>
                  <spine><itemref idref="vol1"/><itemref idref="vol2"/></spine>
                </package>
            """.trimIndent(),
            "OEBPS/Volume-1/chapter.xhtml" to """
                <html><head><title>第一卷第一章</title></head><body><p>Volume one</p></body></html>
            """.trimIndent(),
            "OEBPS/Volume-2/chapter.xhtml" to """
                <html><head><title>第二卷第一章</title></head><body><p>Volume two</p></body></html>
            """.trimIndent(),
            "OEBPS/Volume-2/nav.xhtml" to """
                <html xmlns:epub="http://www.idpf.org/2007/ops"><body>
                  <nav epub:type="toc"><ol>
                    <li><a href="../Volume-1/chapter.xhtml">第一卷</a><ol>
                      <li><a href="../Volume-1/chapter.xhtml">第一章</a></li>
                    </ol></li>
                    <li><a href="chapter.xhtml">第二卷</a><ol>
                      <li><a href="chapter.xhtml">第一章</a></li>
                    </ol></li>
                  </ol></nav>
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

    private fun createAnchorTocEpub(target: File) {
        val entries = linkedMapOf(
            "META-INF/container.xml" to """
                <?xml version="1.0"?>
                <container><rootfiles><rootfile full-path="OEBPS/content.opf"/></rootfiles></container>
            """.trimIndent(),
            "OEBPS/content.opf" to """
                <package>
                  <metadata><dc:title>TOC anchor test</dc:title><dc:creator>Test</dc:creator></metadata>
                  <manifest>
                    <item id="book" href="Text/book.xhtml" media-type="application/xhtml+xml"/>
                    <item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>
                  </manifest>
                  <spine><itemref idref="book"/></spine>
                </package>
            """.trimIndent(),
            "OEBPS/Text/book.xhtml" to """
                <html><head><title>Complete book</title></head><body>
                  <h1 id="volume-one">第一卷第一章</h1><p>Volume one text.</p>
                  <h1 id="volume-two">第二卷第一章</h1><p>Volume two text.</p>
                </body></html>
            """.trimIndent(),
            "OEBPS/nav.xhtml" to """
                <html xmlns:epub="http://www.idpf.org/2007/ops"><body>
                  <nav epub:type="toc"><ol>
                    <li><a href="Text/book.xhtml#volume-one">第一卷第一章</a></li>
                    <li><a href="Text/book.xhtml#volume-two">第二卷第一章</a></li>
                  </ol></nav>
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

    private fun createRootOpfVolumeEpub(target: File) {
        val entries = linkedMapOf(
            "META-INF/container.xml" to """
                <?xml version="1.0"?>
                <container><rootfiles><rootfile full-path="content.opf"/></rootfiles></container>
            """.trimIndent(),
            "content.opf" to """
                <package>
                  <metadata><dc:title>Root OPF test</dc:title><dc:creator>Test</dc:creator></metadata>
                  <manifest>
                    <item id="one" href="1/Text/Section0001.xhtml" media-type="application/xhtml+xml"/>
                    <item id="two" href="2/Text/Section0001.xhtml" media-type="application/xhtml+xml"/>
                    <item id="ncx" href="toc.ncx" media-type="application/x-dtbncx+xml"/>
                  </manifest>
                  <spine toc="ncx"><itemref idref="one"/><itemref idref="two"/></spine>
                </package>
            """.trimIndent(),
            "toc.ncx" to """
                <ncx><navMap>
                  <navPoint><navLabel><text>Volume one</text></navLabel><content src="1/Text/Section0001.xhtml"/></navPoint>
                  <navPoint><navLabel><text>Volume two</text></navLabel><content src="2/Text/Section0001.xhtml"/></navPoint>
                </navMap></ncx>
            """.trimIndent(),
            "1/Text/Section0001.xhtml" to """
                <html><body><p>VOLUME_ONE</p></body></html>
            """.trimIndent(),
            "2/Text/Section0001.xhtml" to """
                <html><body><p>VOLUME_TWO</p></body></html>
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
