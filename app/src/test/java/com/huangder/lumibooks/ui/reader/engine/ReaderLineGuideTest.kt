package com.huangder.lumibooks.ui.reader.engine

import android.graphics.RectF
import android.graphics.Color
import android.graphics.Bitmap
import android.graphics.Canvas
import java.io.File
import java.io.FileOutputStream
import android.text.StaticLayout
import android.text.TextPaint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ReaderLineGuideTest {
    @Test
    fun skipsBlankAndImageOnlyLines() {
        val text = "First line\n   \n\uFFFC\nLast line"
        val layout = StaticLayout.Builder.obtain(text, 0, text.length, TextPaint().apply {
            textSize = 20f
        }, 1000).build()

        val lines = readableGuideLines(text, layout, 12f, 212f, 5f)

        assertEquals(listOf(0, text.indexOf("Last")), lines.map { it.startOffset })
        assertEquals(12f, lines.first().bounds.left)
        assertEquals(212f, lines.first().bounds.right)
        val metrics = layout.paint.fontMetrics
        val expectedCenter = 5f + layout.getLineBaseline(0) +
            (metrics.ascent + metrics.descent) / 2f
        assertEquals(expectedCenter, lines.first().bounds.centerY(), 0.01f)
        assertEquals(listOf(text.indexOf("Last")), readableGuideLines(
            text, layout, 12f, 212f, 5f,
            visibleTop = lines.last().bounds.top,
            visibleBottom = lines.last().bounds.bottom
        ).map { it.startOffset })
    }

    @Test
    fun stepsThroughSpreadAndStopsAtBookBoundaries() {
        val spread = listOf("left first", "left last", "right first", "right last")
        assertEquals("right first", spread[readerGuideStepIndex(1, spread.size, 1)!!])
        assertEquals("left last", spread[readerGuideStepIndex(2, spread.size, -1)!!])
        assertNull(readerGuideStepIndex(0, spread.size, -1))
        assertNull(readerGuideStepIndex(spread.lastIndex, spread.size, 1))
    }

    @Test
    fun scrollsByActualNeighborSpacingAndFallsBackAtViewportEdge() {
        val current = ReaderGuideLine(10, RectF(0f, 100f, 300f, 132f))
        val next = ReaderGuideLine(22, RectF(0f, 139f, 300f, 183f))

        assertEquals(45f, readerGuideScrollDistance(
            current.bounds.centerY(), next.bounds.centerY(), current.bounds.height(), 1
        ))
        assertEquals(-32f, readerGuideScrollDistance(
            current.bounds.centerY(), null, current.bounds.height(), -1
        ))
    }

    @Test
    fun ignoresTextFarFromScrollGuideAnchor() {
        val lines = listOf(
            ReaderGuideLine(0, RectF(0f, 12f, 300f, 42f)),
            ReaderGuideLine(20, RectF(0f, 625f, 300f, 655f))
        )

        assertNull(readerGuideFocusedIndex(lines, 240f, 720f))
        assertEquals(0, readerGuideFocusedIndex(lines, 67f, 720f))
        assertEquals(1, readerGuideFocusedIndex(lines, 638f, 720f))
    }

    @Test
    fun lastLineKeepsAtLeastThePreviousLinePitch() {
        val text = "First line\nLast line"
        val layout = StaticLayout.Builder.obtain(text, 0, text.length, TextPaint().apply {
            textSize = 20f
        }, 1000).setLineSpacing(0f, 1.5f).build()

        val lines = readableGuideLines(text, layout, 0f, 300f, 0f)
        val last = lines.last()

        assertEquals(lines.first().bounds.height(), last.bounds.height(), 1f)
        val metrics = layout.paint.fontMetrics
        val expectedCenter = layout.getLineBaseline(1) +
            (metrics.ascent + metrics.descent) / 2f
        assertEquals(expectedCenter, last.bounds.centerY(), 0.01f)
    }

    @Test
    fun roundedWindowHasSubtleInnerShadowWithoutFillingItsCenter() {
        val bitmap = Bitmap.createBitmap(200, 100, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.WHITE)
        drawReaderGuideOverlay(Canvas(bitmap), 200f, 100f,
            RectF(20f, 20f, 180f, 80f), Color.TRANSPARENT, 1f)

        assertEquals(Color.WHITE, bitmap.getPixel(100, 50))
        val edgeReds = (20..30).map { Color.red(bitmap.getPixel(100, it)) }
        org.junit.Assert.assertTrue("inner edge reds=$edgeReds", edgeReds.any { it in 220..254 })
        assertEquals(Color.WHITE, bitmap.getPixel(100, 18))
    }

    @Test
    fun rendersGuideAlignmentPreview() {
        val text = "The downside of this, though, is that it leads\n" +
            "to less flexibility. Fail to gauge the correct\n" +
            "number of orders up front and the result is\n" +
            "disastrous. Not only do you have to hurry\n" +
            "to assemble new products, but you also have"
        val paint = TextPaint().apply {
            textSize = 42f
            color = Color.rgb(48, 43, 36)
            isAntiAlias = true
        }
        val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, 690)
            .setLineSpacing(0f, 1.6f).setIncludePad(false).build()
        val bitmap = Bitmap.createBitmap(800, 700, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.rgb(241, 229, 203))
        val canvas = Canvas(bitmap)
        canvas.save()
        canvas.translate(55f, 115f)
        layout.draw(canvas)
        canvas.restore()
        val focused = readableGuideLines(text, layout, 36f, 764f, 115f)[2].bounds
        drawReaderGuideOverlay(canvas, 800f, 700f, focused,
            readerGuideShadeColor(Color.rgb(241, 229, 203), 2), 2f)
        val output = File("build/reports/line-guide-preview.png")
        output.parentFile?.mkdirs()
        FileOutputStream(output).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }

        assertEquals(115f + layout.getLineBaseline(2) +
            (paint.fontMetrics.ascent + paint.fontMetrics.descent) / 2f,
            focused.centerY(), 0.01f)
    }

    @Test
    fun shadeFollowsThemeBrightnessAndDimLevel() {
        val lightShade = readerGuideShadeColor(Color.WHITE, 2)
        val darkShade = readerGuideShadeColor(Color.BLACK, 2)

        assertEquals(Color.WHITE, lightShade or -0x1000000)
        assertEquals(Color.BLACK, darkShade or -0x1000000)
        assertEquals(readerGuideDimAlpha(2), Color.alpha(lightShade))
        assertEquals(readerGuideDimAlpha(2), Color.alpha(darkShade))
        assertEquals(0, Color.alpha(readerGuideShadeColor(Color.WHITE, 0)))
    }
}
