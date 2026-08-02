package com.huangder.lumibooks.util

import com.huangder.lumibooks.util.epub.MobiText
import com.huangder.lumibooks.util.parser.MobiParser
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.charset.Charset

class MobiParserSessionTest {

    // 1x1 transparent PNG
    private val tinyPng = byteArrayOf(
        0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
        0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52,
        0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01,
        0x08, 0x06, 0x00, 0x00, 0x00, 0x1F, 0x15, 0xC4.toByte(),
        0x89.toByte(), 0x00, 0x00, 0x00, 0x0D, 0x49, 0x44, 0x41,
        0x54, 0x78, 0x9C.toByte(), 0x63, 0x60, 0x00, 0x00, 0x00,
        0x02, 0x00, 0x01, 0xE2.toByte(), 0x21, 0xBC.toByte(), 0x33, 0x00,
        0x00, 0x00, 0x00, 0x49, 0x45, 0x4E, 0x44, 0xAE.toByte(),
        0x42, 0x60, 0x82.toByte()
    )

    private val chapterOne =
        "<html><head><guide><reference type=\"start\" title=\"START\" filepos=0000000000 /></guide>" +
            "</head><body><h1>Chapter One</h1><p>Hello <b>MOBI</b> world.</p><mbp:pagebreak/></body></html>"
    private val chapterTwo =
        "<h2>Chapter Two</h2><p><img recindex=\"1\"/>Image here.</p>" +
            "<a filepos=0000000042>link</a>"

    @Test
    fun `parses synthetic mobi and exposes chapters`() {
        val temp = File.createTempFile("mobi_parse_test", ".mobi")
        try {
            MobiTestFixtures.writeMobiFile(
                temp,
                listOf(chapterOne.toByteArray(Charsets.UTF_8), chapterTwo.toByteArray(Charsets.UTF_8)),
                images = listOf(tinyPng)
            )
            val parser = MobiParser(null)
            val content = parser.parse(temp.absolutePath)
            try {
                assertEquals("MOBI Book", content.title)
                assertEquals("Test Author", content.author)
                assertEquals(2, content.chapters.size)
                assertEquals("Chapter One", content.chapters[0].title)
                assertEquals("Chapter Two", content.chapters[1].title)

                // filepos -> chapter mapping over rawml byte offsets
                val rawml = (chapterOne + chapterTwo).toByteArray(Charsets.UTF_8)
                val chapterTwoOffset = MobiTestFixtures.indexOfBytes(
                    rawml, "Chapter Two".toByteArray(Charsets.UTF_8)
                )
                assertTrue(chapterTwoOffset > 0)
                assertEquals(0, parser.sessionFileposToChapter(0))
                assertEquals(1, parser.sessionFileposToChapter(chapterTwoOffset.toLong()))
                assertEquals(1, parser.sessionFileposToChapter((rawml.size - 1).toLong()))
            } finally {
                parser.close()
            }
        } finally {
            temp.delete()
        }
    }

    @Test
    fun `session accessors round trip and link resolution`() {
        val temp = File.createTempFile("mobi_session_test", ".mobi")
        try {
            MobiTestFixtures.writeMobiFile(
                temp,
                listOf(chapterOne.toByteArray(Charsets.UTF_8), chapterTwo.toByteArray(Charsets.UTF_8)),
                images = listOf(tinyPng)
            )
            val parser = MobiParser(null)
            try {
                parser.parse(temp.absolutePath)
                val session = parser.openRenderSession()
                try {
                    assertEquals(2, session.chapterCount)
                    // stable hrefs + chapterUrl/chapterIndexForUrl round trip
                    assertEquals("chapter-000.html", session.chapterHref(0))
                    assertEquals("chapter-001.html", session.chapterHref(1))
                    assertEquals(0, session.chapterIndexForUrl(session.chapterUrl(0)))
                    assertEquals(1, session.chapterIndexForUrl(session.chapterUrl(1, "frag")))
                    assertNull(session.chapterIndexForUrl("https://example.com/chapter-000.html"))

                    // internal links resolve across chapters
                    assertEquals(0 to null, session.resolveInternalLink(1, session.chapterUrl(0)))
                    assertEquals(1 to "frag", session.resolveInternalLink(0, session.chapterUrl(1, "frag")))

                    // images: recindex -> session URL -> resource bytes
                    val imageUrl = session.imageUrl(1, "recindex:1")
                    assertNotNull(imageUrl)
                    val resource = session.readImageUrl(imageUrl!!)
                    assertNotNull(resource)
                    assertEquals("image/png", resource!!.mediaType)
                    assertTrue(resource.bytes.contentEquals(tinyPng))

                    // transformed chapter document search text (rawml -> XHTML pipeline)
                    val text = session.searchText(0)
                    assertTrue(text.contains("Chapter One"))
                    assertTrue(text.contains("MOBI"))
                } finally {
                    session.close()
                }
            } finally {
                parser.close()
            }
        } finally {
            temp.delete()
        }
    }

    @Test
    fun `full book search returns chapter locators`() = runBlocking {
        val temp = File.createTempFile("mobi_search_test", ".mobi")
        try {
            MobiTestFixtures.writeMobiFile(
                temp,
                listOf(chapterOne.toByteArray(Charsets.UTF_8), chapterTwo.toByteArray(Charsets.UTF_8)),
                images = listOf(tinyPng)
            )
            val parser = MobiParser(null)
            try {
                parser.parse(temp.absolutePath)
                val matches = parser.searchBook("MOBI", 10)
                assertEquals(1, matches.size)
                assertEquals(0, matches[0].chapterIndex)
                assertEquals("chapter-000.html", matches[0].locator.href)
                assertTrue(matches[0].context.contains("MOBI"))

                val none = parser.searchBook("nonexistent-term", 10)
                assertTrue(none.isEmpty())
            } finally {
                parser.close()
            }
        } finally {
            temp.delete()
        }
    }

    @Test
    fun `gbk fallback decodes misdeclared utf8`() {
        val gbkBytes = "\u4E2D\u6587".toByteArray(Charset.forName("GBK"))
        val decoded = MobiText.decode(gbkBytes, com.huangder.lumibooks.util.epub.MobiFile.TEXT_ENCODING_UTF8)
        assertEquals("\u4E2D\u6587", decoded)
    }
}
