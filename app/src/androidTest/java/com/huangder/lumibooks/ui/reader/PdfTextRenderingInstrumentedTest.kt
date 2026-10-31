package com.huangder.lumibooks.ui.reader

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File

/** Compile locally only; repository policy prohibits installing instrumented test APKs. */
@RunWith(AndroidJUnit4::class)
class PdfTextRenderingInstrumentedTest {
    @Test fun extractedQuadsCoverActualPdfRendererInkWithCropAndEveryRotation() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        PDFBoxResourceLoader.init(context)
        val file = File.createTempFile("pdf-text-render", ".pdf", context.cacheDir)
        PDDocument().use { document ->
            listOf(0, 90, 180, 270).forEach { rotation ->
                val page = PDPage(PDRectangle(400f, 600f)).apply {
                    cropBox = PDRectangle(40f, 60f, 200f, 400f)
                    this.rotation = rotation
                }
                document.addPage(page)
                PDPageContentStream(document, page).use { stream ->
                    stream.beginText()
                    stream.setFont(PDType1Font.HELVETICA, 24f)
                    stream.newLineAtOffset(90f, 260f)
                    stream.showText("HELLO")
                    stream.endText()
                }
            }
            document.save(file)
        }
        val session = PdfTextSession(context, file.path)
        try {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                PdfRenderer(descriptor).use { renderer ->
                    for (pageIndex in 0..3) renderer.openPage(pageIndex).use { page ->
                        val model = session.page(pageIndex)!!
                        val bitmap = Bitmap.createBitmap(page.width * 2, page.height * 2, Bitmap.Config.ARGB_8888)
                        bitmap.eraseColor(Color.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        val pixels = IntArray(bitmap.width * bitmap.height)
                        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
                        var ink = 0
                        var covered = 0
                        pixels.forEachIndexed { index, pixel ->
                            if (Color.red(pixel) < 100 && Color.alpha(pixel) > 200) {
                                ink++
                                val x = (index % bitmap.width + 0.5f) / bitmap.width
                                val y = (index / bitmap.width + 0.5f) / bitmap.height
                                if (model.glyphs.any { it.quad.contains(x, y) }) covered++
                            }
                        }
                        assertTrue("page $pageIndex must have rendered letters", ink > 100)
                        assertTrue("page $pageIndex quads cover $covered/$ink dark pixels", covered > ink * 0.9f)
                        bitmap.recycle()
                    }
                }
            }
        } finally { session.close() }
    }
}
