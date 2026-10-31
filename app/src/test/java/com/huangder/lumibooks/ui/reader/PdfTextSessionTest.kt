package com.huangder.lumibooks.ui.reader

import android.app.Application
import android.graphics.Bitmap
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26, 29, 35], application = Application::class)
class PdfTextSessionTest {
    @Test fun actualPdfTextRotationsCropAndCacheWorkOnOldAndroid() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        PDFBoxResourceLoader.init(context)
        val file = File.createTempFile("pdf-text-test", ".pdf", context.cacheDir)
        PDDocument().use { document ->
            for (rotation in listOf(0, 90, 180, 270)) {
                val page = PDPage(PDRectangle(400f, 600f)).apply {
                    cropBox = PDRectangle(40f, 60f, 200f, 400f)
                    this.rotation = rotation
                }
                document.addPage(page)
                PDPageContentStream(document, page).use { stream ->
                    stream.beginText()
                    stream.setFont(PDType1Font.HELVETICA, 14f)
                    stream.newLineAtOffset(90f, 260f)
                    stream.showText("Hello PDF")
                    stream.newLineAtOffset(0f, -30f)
                    stream.showText("Second line")
                    stream.endText()
                }
            }
            val scanned = PDPage(PDRectangle(200f, 400f))
            document.addPage(scanned)
            val image = Bitmap.createBitmap(32, 64, Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.BLACK) }
            PDPageContentStream(document, scanned).use { stream ->
                stream.drawImage(LosslessFactory.createFromImage(document, image), 0f, 0f, 200f, 400f)
            }
            image.recycle()
            document.save(file)
        }
        val session = PdfTextSession(context, file.path)
        try {
            val first = session.page(0)!!
            assertEquals("Hello PDF\nSecond line", first.text)
            assertEquals(0.25f, first.glyphs.first().quad.left, 0.001f) // CropBox X was subtracted exactly once
            assertTrue(first.glyphs.first().quad.top in 0.44f..0.50f)
            assertTrue((first.glyphs.first().quad.bottom - first.glyphs.first().quad.top) * 400f >= 13f)
            val matches = searchPdfText(session, 5, "line", { "page ${it + 1}" }, limit = 3)
            assertEquals(listOf(0, 1, 2), matches.map { it.chapterIndex })
            assertEquals(listOf("line", "line", "line"), matches.map { match ->
                session.page(match.chapterIndex)!!.text.substring(match.charOffset, match.charOffset + match.matchLength)
            })
            val cached = session.page(0)!!
            assertSame(cached, session.page(0))
            for (page in 1..3) {
                val extracted = session.page(page)!!
                assertEquals("Hello PDF\nSecond line", extracted.text)
                assertEquals(first.text, extracted.locator(0, extracted.text.length)?.quote)
                val glyph = extracted.glyphs.first()
                assertEquals(glyph, extracted.hit((glyph.quad.left + glyph.quad.right) / 2f,
                    (glyph.quad.top + glyph.quad.bottom) / 2f))
            }
            assertNotSame(first, session.page(0)) // three-page LRU
            assertTrue(session.page(4)!!.glyphs.isEmpty())
            assertEquals("", session.page(4)!!.text)
        } finally { session.close() }
        assertNull(session.page(0))
    }

    @Test fun fingerprintIsPortableAcrossPathsAndDetectsReplacedContent() {
        val directory = RuntimeEnvironment.getApplication().cacheDir
        val first = File.createTempFile("pdf-first", ".pdf", directory).apply { writeText("pdf content") }
        val second = File.createTempFile("pdf-copy", ".pdf", directory).apply { writeBytes(first.readBytes()) }
        assertEquals(pdfFileFingerprint(first), pdfFileFingerprint(second))
        second.writeText("new content")
        assertNotEquals(pdfFileFingerprint(first), pdfFileFingerprint(second))
    }
}
