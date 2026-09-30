package com.huangder.lumibooks.ui.reader.engine

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.text.SpannableString
import android.text.Spanned
import android.text.TextPaint
import android.view.View
import com.huangder.lumibooks.domain.model.Note
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ReaderUnderlineRenderingTest {
    @Test fun allStylesDrawInBothDirectionsAndRestorePaint() {
        for (vertical in listOf(false, true)) for (mode in 1..4) {
            val bitmap = Bitmap.createBitmap(160, 160, Bitmap.Config.ARGB_8888)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.RED; style = Paint.Style.STROKE; strokeWidth = 1f }
            drawReaderUnderline(Canvas(bitmap), paint, mode, 10f, 150f, 50f, vertical, 1f)
            val hits = (10..149).count { along -> (47..54).any { cross ->
                Color.alpha(bitmap.getPixel(if (vertical) cross else along, if (vertical) along else cross)) > 0
            } }
            assertTrue("Missing line for mode $mode vertical $vertical: $hits", hits > 65)
            assertNull(paint.pathEffect)
            bitmap.recycle()
        }
    }

    @Test fun manualStyleSnapshotsSurviveWaveStraightWaveAndReopening() {
        val context = RuntimeEnvironment.getApplication()
        for (mode in listOf(3, 1, 3, 2, 4)) {
            val reader = ReadView(context)
            val note = Note(bookId = "book", chapterIndex = 0, startPosition = 0, endPosition = 5,
                selectedText = "abcde", note = "", color = "#ff0000", createdAt = 1, type = "underline",
                styleSnapshotJson = """{"underlineMode":$mode}""")
            reader.setSavedNotes(listOf(note))
            val method = ReadView::class.java.getDeclaredMethod("buildHighlights", Int::class.javaPrimitiveType).apply { isAccessible = true }
            @Suppress("UNCHECKED_CAST") val highlights = method.invoke(reader, 0) as List<Triple<Int, Int, Int>>
            assertEquals(1, highlights.size)
            val flag = highlights.single().third ushr 24
            assertEquals(mapOf(1 to 0xFD, 2 to 0xFC, 3 to 0xFE, 4 to 0xFB)[mode], flag)
        }
    }

    @Test fun verticalLinesCoverEveryColumnAndPageAtDifferentFontSizes() {
        val context = RuntimeEnvironment.getApplication()
        val text = "天地玄黄宇宙洪荒日月盈昃辰宿列张".repeat(8)
        for (size in listOf(18f, 32f)) {
            val pages = VerticalTextLayouter.layout(text, TextPaint().apply { textSize = size; density = 1f },
                160, 220, 0f, 1.25f, 0f)
            assertTrue(pages.size > 1)
            for (mode in 1..4) for (page in pages) {
                val spannable = SpannableString(text.substring(page.startOffset, page.endOffset)).apply {
                    setSpan(WaveUnderlineSpan(Color.RED, mode), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
                val view = VerticalTextView(context).apply {
                    configure(size, Color.BLACK, Typeface.DEFAULT, Color.BLUE)
                    setPage(spannable, page.geometry, page.startOffset)
                    measure(View.MeasureSpec.makeMeasureSpec(160, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(220, View.MeasureSpec.EXACTLY))
                    layout(0, 0, 160, 220)
                }
                val bitmap = Bitmap.createBitmap(160, 220, Bitmap.Config.ARGB_8888)
                view.draw(Canvas(bitmap))
                page.geometry.glyphs.groupBy { it.columnIndex }.values.forEach { glyphs ->
                    val left = (glyphs.minOf { it.bounds.left }.toInt() - 3).coerceAtLeast(0)
                    val right = (glyphs.minOf { it.bounds.left }.toInt() + 10).coerceAtMost(159)
                    for (glyph in glyphs) {
                        val top = glyph.bounds.top.toInt().coerceAtLeast(0)
                        val bottom = glyph.bounds.bottom.toInt().coerceAtMost(219)
                        assertTrue("Missing mode $mode font $size page ${page.startOffset} glyph ${glyph.startOffset}",
                            (top..bottom).any { y -> (left..right).any { x ->
                                val pixel = bitmap.getPixel(x, y)
                                Color.red(pixel) > 180 && Color.green(pixel) < 80 && Color.alpha(pixel) > 0
                            } })
                    }
                }
                bitmap.recycle()
            }
        }
    }
}
