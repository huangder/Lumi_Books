package com.huangder.lumibooks.util.parser

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.text.Spanned
import android.text.style.ImageSpan
import androidx.test.core.app.ApplicationProvider
import java.io.ByteArrayOutputStream
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class EpubImageLifetimeTest {
    @get:Rule val temporary = TemporaryFolder()

    private fun parser(): EpubParser {
        val file = temporary.newFile("illustrated.epub")
        val bitmap = Bitmap.createBitmap(600, 400, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
        val image = ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
        bitmap.recycle()
        val entries = mapOf(
            "META-INF/container.xml" to """<container xmlns="urn:oasis:names:tc:opendocument:xmlns:container"><rootfiles><rootfile full-path="book.opf"/></rootfiles></container>""",
            "book.opf" to """<package xmlns="http://www.idpf.org/2007/opf" version="2.0"><metadata xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:title>Image lifetime</dc:title></metadata><manifest><item id="body" href="body.xhtml" media-type="application/xhtml+xml"/><item id="image" href="image.png" media-type="image/png"/></manifest><spine><itemref idref="body"/></spine></package>""",
            "body.xhtml" to """<html xmlns="http://www.w3.org/1999/xhtml"><body><p>Before image</p><img src="image.png"/><p>After image</p></body></html>"""
        )
        ZipOutputStream(file.outputStream()).use { zip ->
            for ((path, contents) in entries) {
                zip.putNextEntry(ZipEntry(path)); zip.write(contents.toByteArray()); zip.closeEntry()
            }
            zip.putNextEntry(ZipEntry("image.png")); zip.write(image); zip.closeEntry()
        }
        return EpubParser(ApplicationProvider.getApplicationContext()).also { it.parse(file.absolutePath) }
    }

    private fun image(text: CharSequence): ImageSpan = (text as Spanned)
        .getSpans(0, text.length, ImageSpan::class.java).single()

    @Test fun clearingLayoutCacheDoesNotRecyclePixelsOwnedByVisibleImage() {
        val parser = parser()
        try {
            val drawable = image(parser.getChapterContent(0, 300, 0)).drawable
            val source = drawable as ReaderImagePixelSource
            val pixels = requireNotNull(source.acquireReaderBitmap())
            parser.clearHtmlCache()
            assertFalse("visible pixels survive cache eviction", pixels.isRecycled)
            assertSame(pixels, source.acquireReaderBitmap())
            val frame = Bitmap.createBitmap(300, 200, Bitmap.Config.ARGB_8888)
            drawable.draw(Canvas(frame))
            assertEquals(Color.RED, frame.getPixel(150, 100))
            frame.recycle()
        } finally { parser.close() }
    }

    @Test fun simultaneousWidthRequestsKeepTheirOwnImageGeometry() {
        val parser = parser()
        val executor = Executors.newFixedThreadPool(2)
        try {
            parser.contentWidth = 410
            repeat(4) {
                val narrow = executor.submit(Callable { image(parser.getChapterContent(0, 240, 0)).drawable.bounds.width() })
                val wide = executor.submit(Callable { image(parser.getChapterContent(0, 480, 0)).drawable.bounds.width() })
                assertEquals(240, narrow.get().toInt())
                assertEquals(480, wide.get().toInt())
                assertEquals("requests do not change preview defaults", 410, parser.contentWidth)
            }
        } finally { executor.shutdownNow(); parser.close() }
    }
}
