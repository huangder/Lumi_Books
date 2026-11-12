package com.huangder.lumibooks.ui.reader.engine

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.text.Layout
import android.text.SpannableString
import android.text.StaticLayout
import android.text.TextPaint
import com.huangder.lumibooks.domain.model.HighlightRule
import com.huangder.lumibooks.domain.model.ReaderThemeSettings
import com.huangder.lumibooks.ui.reader.annotationGeometryJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ReaderAnnotationStyleRenderingTest {
    private val text = SpannableString("天地玄黄宇宙洪荒")
    private val layout: Layout = StaticLayout.Builder.obtain(
        text, 0, text.length, TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 24f }, 400
    ).setLineSpacing(0f, 2f).build()

    private fun highlightBounds(style: ReaderAnnotationStyle): Rect {
        val bitmap = Bitmap.createBitmap(420, layout.height + 40, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.translate(10f, 20f)
        ReaderHighlightPainter(Paint(Paint.ANTI_ALIAS_FLAG), density = 1f).drawRange(
            canvas, layout, text, ReaderLineGeometry(layout, text, Layout.JUSTIFICATION_MODE_NONE),
            0, 4, Color.RED, style = style
        )
        return inkBounds(bitmap).also { bitmap.recycle() } ?: error("highlight not drawn")
    }

    @Test fun defaultStyleKeepsOriginalHighlightGeometry() {
        assertEquals(highlightBounds(ReaderAnnotationStyle()), highlightBounds(ReaderAnnotationStyle.DEFAULT))
        assertEquals(ReaderAnnotationStyle.DEFAULT, ReaderThemeSettings().toAnnotationStyle())
    }

    @Test fun highlightOffsetWidthAndHeightMoveAndResizeTheBlock() {
        val base = highlightBounds(ReaderAnnotationStyle.DEFAULT)

        val shifted = highlightBounds(ReaderAnnotationStyle(highlightOffsetYDp = 5f))
        assertEquals(base.top + 5, shifted.top)
        assertEquals(base.bottom + 5, shifted.bottom)
        assertEquals(base.width(), shifted.width())

        val wider = highlightBounds(ReaderAnnotationStyle(highlightWidthDp = 4f))
        assertEquals(base.left - 4, wider.left)
        assertEquals(base.right + 4, wider.right)

        val taller = highlightBounds(ReaderAnnotationStyle(highlightHeightDp = 3f))
        assertTrue("taller=$taller base=$base", taller.height() > base.height())
        val shorter = highlightBounds(ReaderAnnotationStyle(highlightHeightDp = -2f))
        assertTrue("shorter=$shorter base=$base", shorter.height() < base.height())
    }

    @Test fun thickerUnderlinesScaleDoubleLineSpacing() {
        fun doubleLineSpan(scale: Float): Int {
            val bitmap = Bitmap.createBitmap(120, 60, Bitmap.Config.ARGB_8888)
            val paint = Paint().apply { color = Color.RED; style = Paint.Style.STROKE; strokeWidth = 1f }
            drawReaderUnderline(Canvas(bitmap), paint, HighlightRule.UNDERLINE_DOUBLE, 10f, 110f, 30f,
                vertical = false, density = 1f, patternScale = scale)
            return inkBounds(bitmap)!!.height().also { bitmap.recycle() }
        }
        assertTrue(doubleLineSpan(3f) > doubleLineSpan(1f))
        // Thinner-than-default lines keep the original spacing.
        assertEquals(doubleLineSpan(1f), doubleLineSpan(0.5f))
        assertEquals(1f, ReaderAnnotationStyle(underlineThicknessDp = 0.5f).underlinePatternScale)
        assertEquals(2f, ReaderAnnotationStyle(underlineThicknessDp = 2f).underlinePatternScale)
    }

    @Test fun bookLayoutPreviewUsesWebViewGeometry() {
        // WebView: highlight 1.5px inside the glyph box, underline 4px below it. Native: 2dp out, 1dp below.
        val defaults = ReaderAnnotationStyle.DEFAULT.webViewEquivalent()
        assertEquals(-3.5f, defaults.highlightHeightDp)
        assertEquals(3f, defaults.underlineOffsetYDp)
        assertEquals(0f, defaults.highlightWidthDp)
        assertEquals(0f, defaults.highlightOffsetYDp)
        val tuned = ReaderAnnotationStyle(highlightHeightDp = 2f, underlineThicknessDp = 2f, underlineOffsetYDp = 1f)
            .webViewEquivalent()
        assertEquals(-1.5f, tuned.highlightHeightDp)
        // The WebView underline band grows with thickness: centre moves to 4 * 2.
        assertEquals(1f + 8f - 1f, tuned.underlineOffsetYDp)
        assertEquals(2f, tuned.underlineThicknessDp)
    }

    @Test fun webViewGeometryPayloadCarriesEveryValue() {
        val json = annotationGeometryJson(ReaderAnnotationStyle(1f, 2f, -3f, 2.5f, 4f))
        assertEquals(1.0, json.getDouble("highlightWidth"), 0.0)
        assertEquals(2.0, json.getDouble("highlightHeight"), 0.0)
        assertEquals(-3.0, json.getDouble("highlightOffset"), 0.0)
        assertEquals(2.5, json.getDouble("underlineThickness"), 0.0)
        assertEquals(4.0, json.getDouble("underlineOffset"), 0.0)
    }

    private fun inkBounds(bitmap: Bitmap): Rect? {
        var left = Int.MAX_VALUE
        var top = Int.MAX_VALUE
        var right = -1
        var bottom = -1
        for (y in 0 until bitmap.height) for (x in 0 until bitmap.width) {
            if (Color.alpha(bitmap.getPixel(x, y)) > 128) {
                left = minOf(left, x); top = minOf(top, y)
                right = maxOf(right, x); bottom = maxOf(bottom, y)
            }
        }
        return if (right < 0) null else Rect(left, top, right + 1, bottom + 1).also { assertNotNull(it) }
    }
}
