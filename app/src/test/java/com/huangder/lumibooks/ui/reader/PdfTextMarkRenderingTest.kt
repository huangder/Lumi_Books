package com.huangder.lumibooks.ui.reader

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Paint
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.huangder.lumibooks.ui.reader.engine.drawReaderUnderline
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PdfTextMarkRenderingTest {
    private val transform = PdfTextTransform(1f, 0f, 1f, 200f, 200f, true)
    private fun quad(left: Float, top: Float, right: Float, bottom: Float) = PdfTextQuad(listOf(
        PdfTextPoint(left, top), PdfTextPoint(right, top), PdfTextPoint(right, bottom), PdfTextPoint(left, bottom)))

    private fun bitmap(draw: DrawScope.() -> Unit): Bitmap =
        Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888).also {
            CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(it.asImageBitmap()), Size(200f, 200f), draw)
        }

    @Test fun highlightCoversTallMiddleGlyphAndPreservesReaderOpacity() {
        val model = PdfTextPage(0, "file", "abc", listOf(
            PdfTextGlyph(0, 1, 0, quad(.25f, .3f, .4f, .45f)),
            PdfTextGlyph(1, 2, 0, quad(.4f, .25f, .6f, .5f)),
            PdfTextGlyph(2, 3, 0, quad(.6f, .3f, .75f, .45f))))
        val image = bitmap {
            drawPdfTextMark(model.quads(0, 3).single(), transform, Color(0x40D6C58D), false, 0, false)
        }
        assertEquals(64, android.graphics.Color.alpha(image.getPixel(100, 50)))
        assertEquals(64, android.graphics.Color.alpha(image.getPixel(100, 99)))
        assertTrue(android.graphics.Color.alpha(image.getPixel(100, 100)) > 0)
        assertEquals(0, android.graphics.Color.alpha(image.getPixel(47, 48)))
        image.recycle()
    }

    @Test fun everyPdfUnderlineStyleMatchesTheReaderPixelForPixel() {
        for (mode in 1..4) {
            val actual = bitmap {
                drawPdfTextMark(quad(.25f, .25f, .75f, .5f), transform, Color.Red, true, mode, false)
            }
            val expected = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.RED; style = Paint.Style.STROKE
                strokeWidth = 1f; strokeCap = Paint.Cap.ROUND
            }
            drawReaderUnderline(android.graphics.Canvas(expected), paint, mode, 50f, 150f, 102f, false, 1f)
            val actualPixels = IntArray(40000)
            val expectedPixels = IntArray(40000)
            actual.getPixels(actualPixels, 0, 200, 0, 0, 200, 200)
            expected.getPixels(expectedPixels, 0, 200, 0, 0, 200, 200)
            assertArrayEquals("Underline mode $mode", expectedPixels, actualPixels)
            actual.recycle(); expected.recycle()
        }
    }

    @Test fun smallHighlightHasTightPaddingAndSmallCorners() {
        val image = bitmap {
            drawPdfTextMark(quad(.25f, .25f, .75f, .28f), transform, Color.Red, false, 0, false)
        }
        assertEquals(0, android.graphics.Color.alpha(image.getPixel(100, 48)))
        assertEquals(0, android.graphics.Color.alpha(image.getPixel(48, 53)))
        assertTrue(android.graphics.Color.alpha(image.getPixel(50, 51)) >= 60)
        image.recycle()
    }

    @Test fun closeLinesLeaveAGapInsteadOfStackingTheirOpacityIncludingRotatedText() {
        val lines = listOf(quad(.25f, .25f, .75f, .3f), quad(.25f, .29f, .75f, .34f))
        for (rotated in listOf(false, true)) {
            val actualLines = if (rotated) lines.map { line ->
                PdfTextQuad(line.points.map { PdfTextPoint(1f - it.y, it.x) })
            } else lines
            val image = bitmap {
                actualLines.forEach {
                    drawPdfTextMark(it, transform, Color.Red, false, 0, false, neighboringLines = actualLines)
                }
            }
            for (y in 48..70) {
                val pixel = if (rotated) image.getPixel(199 - y, 100) else image.getPixel(100, y)
                assertTrue("Lines overlap at $y, rotated=$rotated", android.graphics.Color.alpha(pixel) <= 64)
            }
            val gap = if (rotated) image.getPixel(140, 100) else image.getPixel(100, 59)
            assertTrue(android.graphics.Color.alpha(gap) < 40)
            image.recycle()
        }
    }
}
