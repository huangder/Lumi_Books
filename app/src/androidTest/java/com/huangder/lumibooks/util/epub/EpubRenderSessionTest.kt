package com.huangder.lumibooks.util.epub

import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EpubRenderSessionTest {
    @Test
    fun assetLoaderServesOnlyTheActiveTokenAndRejectsTraversal() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val file = File(context.cacheDir, "session-" + UUID.randomUUID() + ".epub")
        writeFixture(file)

        EpubRenderSession.open(file.absolutePath).use { session ->
            val chapterResponse = session.assetLoader.shouldInterceptRequest(
                Uri.parse(session.chapterUrl(0))
            )
            assertNotNull(chapterResponse)
            val chapter = chapterResponse!!.data.bufferedReader().use { it.readText() }
            assertTrue(chapter.contains("window.LumiReader"))
            assertEquals("application/xhtml+xml", chapterResponse.mimeType)

            val cssUrl = "https://" + EpubRenderSession.ASSET_DOMAIN + "/epub/" + session.sessionToken + "/OPS/style.css"
            val cssResponse = session.assetLoader.shouldInterceptRequest(Uri.parse(cssUrl))
            assertNotNull(cssResponse)
            assertEquals("text/css", cssResponse!!.mimeType)

            val wrongTokenUrl = "https://" + EpubRenderSession.ASSET_DOMAIN + "/epub/wrong/OPS/chapter.xhtml"
            assertNull(session.assetLoader.shouldInterceptRequest(Uri.parse(wrongTokenUrl)))
            assertNull(session.read("../OPS/chapter.xhtml"))
            assertNull(session.read("https://example.com/chapter.xhtml"))
        }
        file.delete()
    }

    @Test
    fun splitsOneSpineDocumentIntoLogicalChapters() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val file = File(context.cacheDir, "logical-session-" + UUID.randomUUID() + ".epub")
        writeMultiSectionFixture(file)

        EpubRenderSession.open(file.absolutePath).use { session ->
            assertEquals(2, session.chapterCount)
            assertEquals("OPS/chapter.xhtml#second", session.chapterHref(1))
            assertEquals(1, session.chapterIndexForUrl(
                "https://${EpubRenderSession.ASSET_DOMAIN}/epub/${session.sessionToken}/OPS/chapter.xhtml#second"
            ))
            assertEquals(1 to "second", session.resolveInternalLink(0, "#second"))

            val first = session.assetLoader.shouldInterceptRequest(Uri.parse(session.chapterUrl(0)))
            val second = session.assetLoader.shouldInterceptRequest(Uri.parse(session.chapterUrl(1)))
            assertNotNull(first)
            assertNotNull(second)
            val firstHtml = first!!.data.bufferedReader().use { it.readText() }
            val secondHtml = second!!.data.bufferedReader().use { it.readText() }
            assertTrue(firstHtml.contains("First section"))
            assertTrue(!firstHtml.contains("Second section"))
            assertTrue(secondHtml.contains("Second section"))
            assertTrue(!secondHtml.contains("First section"))
            assertTrue(secondHtml.contains("<base href=\"https://${EpubRenderSession.ASSET_DOMAIN}/"))
        }
        file.delete()
    }

    private fun writeFixture(file: File) {
        val entries = linkedMapOf(
            "META-INF/container.xml" to """
                <container xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
                  <rootfiles><rootfile full-path="OPS/package.opf" media-type="application/oebps-package+xml"/></rootfiles>
                </container>
            """.trimIndent(),
            "OPS/package.opf" to """
                <package xmlns="http://www.idpf.org/2007/opf" version="3.0">
                  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:title>Session</dc:title></metadata>
                  <manifest>
                    <item id="c1" href="chapter.xhtml" media-type="application/xhtml+xml"/>
                    <item id="css" href="style.css" media-type="text/css"/>
                  </manifest>
                  <spine><itemref idref="c1"/></spine>
                </package>
            """.trimIndent(),
            "OPS/chapter.xhtml" to """
                <html xmlns="http://www.w3.org/1999/xhtml"><head><link rel="stylesheet" href="style.css"/></head>
                <body><p>Session content</p></body></html>
            """.trimIndent(),
            "OPS/style.css" to "p { color: rgb(1, 2, 3); }"
        )
        ZipOutputStream(file.outputStream().buffered()).use { zip ->
            entries.forEach { (path, content) ->
                zip.putNextEntry(ZipEntry(path))
                zip.write(content.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
        }
    }

    private fun writeMultiSectionFixture(file: File) {
        val entries = linkedMapOf(
            "META-INF/container.xml" to """
                <container xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
                  <rootfiles><rootfile full-path="OPS/package.opf" media-type="application/oebps-package+xml"/></rootfiles>
                </container>
            """.trimIndent(),
            "OPS/package.opf" to """
                <package xmlns="http://www.idpf.org/2007/opf" version="3.0">
                  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:title>Logical Session</dc:title></metadata>
                  <manifest>
                    <item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>
                    <item id="c1" href="chapter.xhtml" media-type="application/xhtml+xml"/>
                    <item id="css" href="style.css" media-type="text/css"/>
                  </manifest>
                  <spine><itemref idref="c1"/></spine>
                </package>
            """.trimIndent(),
            "OPS/nav.xhtml" to """
                <html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops">
                  <body><nav epub:type="toc"><ol>
                    <li><a href="chapter.xhtml#first">First</a></li>
                    <li><a href="chapter.xhtml#second">Second</a></li>
                  </ol></nav></body>
                </html>
            """.trimIndent(),
            "OPS/chapter.xhtml" to """
                <html xmlns="http://www.w3.org/1999/xhtml"><head><link rel="stylesheet" href="style.css"/></head><body>
                  <section id="first"><p>First section</p></section>
                  <section id="second"><p>Second section</p></section>
                </body></html>
            """.trimIndent(),
            "OPS/style.css" to "section { color: red; }"
        )
        ZipOutputStream(file.outputStream().buffered()).use { zip ->
            entries.forEach { (path, content) ->
                zip.putNextEntry(ZipEntry(path))
                zip.write(content.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
        }
    }
}
