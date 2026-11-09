package com.huangder.lumibooks.util.epub

import android.app.Application
import android.net.Uri
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class EpubImageResourceRepairTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    private fun fixture(images: Map<String, String>, declared: Map<String, String> = images.mapValues { "image/jpeg" }): EpubRenderSession {
        val entries = epub3Entries().toMutableMap()
        entries["OPS/package.opf"] = entries.getValue("OPS/package.opf").replace("</manifest>",
            declared.entries.mapIndexed { index, (path, mime) ->
                "<item id='repair-$index' href='$path' media-type='$mime'/>"
            }.joinToString("") + "</manifest>")
        images.forEach { (path, bytes) -> entries["OPS/$path"] = bytes }
        val file = temporaryFolder.newFile("images-${System.nanoTime()}.epub")
        writeEpub(file, entries)
        return EpubRenderSession.open(file.absolutePath)
    }

    @Test fun uniqueDeclaredSameDirectoryImageRepairsExtensionAndKeepsMimeAndPreview() {
        fixture(mapOf("Images/logo.jpg" to "jpeg-payload")).use { session ->
            val image = requireNotNull(session.read("OPS/Images/logo.png"))
            assertEquals("OPS/Images/logo.jpg", image.path)
            assertEquals("image/jpeg", image.mediaType)
            assertEquals("jpeg-payload", image.bytes.toString(Charsets.UTF_8))
            val url = requireNotNull(session.imageUrl(0, "../Images/logo.png"))
            assertArrayEquals(image.bytes, session.readImageUrl(url)!!.bytes)
            val response = requireNotNull(session.assetLoader.shouldInterceptRequest(Uri.parse(url)))
            assertEquals("image/jpeg", response.mimeType)
            assertArrayEquals(image.bytes, response.data.use { it.readBytes() })
        }
    }

    @Test fun exactFileWinsAndAmbiguousCandidatesRemainMissing() {
        fixture(mapOf("Images/logo.png" to "exact", "Images/logo.jpg" to "other")).use {
            assertEquals("exact", it.read("OPS/Images/logo.png")!!.bytes.toString(Charsets.UTF_8))
            assertNull(it.read("OPS/Images/logo.gif"))
        }
    }

    @Test fun repairNeverCrossesDirectoriesOrPromotesUndeclaredOrNonImageResources() {
        fixture(mapOf("Images/logo.jpg" to "image")).use {
            assertNull(it.read("OPS/Elsewhere/logo.png"))
            assertNull(it.read("OPS/Images/logo.css"))
            assertNull(it.read("OPS/Images/logo.svg"))
        }
        fixture(mapOf("Images/logo.jpg" to "unlisted"), emptyMap()).use {
            assertNull(it.read("OPS/Images/logo.png"))
        }
        fixture(mapOf("Images/logo.jpg" to "not-image"), mapOf("Images/logo.jpg" to "text/plain")).use {
            assertNull(it.read("OPS/Images/logo.png"))
        }
    }
}
