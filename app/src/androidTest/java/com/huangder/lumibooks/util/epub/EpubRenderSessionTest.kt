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
}
