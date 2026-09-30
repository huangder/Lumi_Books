package com.huangder.lumibooks.ui.reader

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ImageSpan
import android.view.View
import androidx.test.core.app.ApplicationProvider
import com.huangder.lumibooks.util.parser.EpubParser
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ContinuousComicLayoutTest {
    private fun mixedChapter() = SpannableStringBuilder("标题与前言\n\n").apply {
        for (color in listOf(Color.RED, Color.BLUE)) {
            val start = length
            append("\uFFFC\n\n")
            setSpan(ImageSpan(ColorDrawable(color).apply { setBounds(0, 0, 240, 120) }),
                start, start + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            setSpan(EpubParser.ParagraphLineHeightSpan(32), start + 2, start + 3, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        append("这里的文字必须保留，仍然按正文边距排版。")
    }

    private fun view(source: CharSequence, comic: Boolean): ContinuousSelectableTextView {
        val prepared = wrapReaderImages(SpannableStringBuilder(source)) as SpannableStringBuilder
        if (comic) sizeContinuousComicImages(prepared, 400, 32, 48)
        protectContinuousImageHeights(prepared, 1.8f, comic)
        return ContinuousSelectableTextView(ApplicationProvider.getApplicationContext()).apply {
            textSize = 16f
            setTextColor(Color.BLACK)
            setBackgroundColor(Color.WHITE)
            setPadding(32, 0, 48, 0)
            readerImageBleed = comic
            setLineSpacing(0f, 1f)
            text = prepared
            measure(View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
            layout(0, 0, 400, measuredHeight)
        }
    }

    @Test fun mixedComicImagesFillBothEdgesWithoutDeletingTextOrOffsets() {
        val source = mixedChapter()
        val reader = view(source, true)
        assertEquals(source.toString(), reader.text.toString())
        assertEquals(320, reader.layout.width)
        val text = reader.text as Spanned
        val images = text.getSpans(0, text.length, ImageSpan::class.java)
        val first = continuousImageBounds(reader.layout, images[0], Layout.JUSTIFICATION_MODE_NONE)!!
        val second = continuousImageBounds(reader.layout, images[1], Layout.JUSTIFICATION_MODE_NONE)!!
        assertEquals(-32f, first.left, 0.1f)
        assertEquals(400f, first.width(), 0.1f)
        assertEquals(200f, first.height(), 0.1f)
        assertEquals("consecutive images join without a spacer", first.bottom, second.top, 0.1f)
        val pixels = Bitmap.createBitmap(400, reader.height, Bitmap.Config.ARGB_8888)
        reader.draw(Canvas(pixels))
        for ((rect, color) in listOf(first to Color.RED, second to Color.BLUE)) {
            for (x in listOf(0, 1, 200, 398, 399)) {
                assertEquals("image reaches pixel $x", color, pixels.getPixel(x, rect.centerY().toInt()))
            }
        }
        pixels.recycle()
        assertEquals("parser drawable stays unchanged", 240,
            source.getSpans(0, source.length, ImageSpan::class.java)[0].drawable.bounds.width())
        val normal = view(source, false)
        val ordinary = normal.text as Spanned
        val normalImages = ordinary.getSpans(0, ordinary.length, ImageSpan::class.java)
        val a = continuousImageBounds(normal.layout, normalImages[0], Layout.JUSTIFICATION_MODE_NONE)!!
        val b = continuousImageBounds(normal.layout, normalImages[1], Layout.JUSTIFICATION_MODE_NONE)!!
        assertEquals(240f, a.width(), 0.1f)
        assertTrue("disabling comic restores paragraph spacing", b.top > a.bottom)
    }

    @Test fun innerSingleImagePositionDoesNotIncludeCoverCentering() {
        val image = SpannableStringBuilder("\uFFFC").apply {
            setSpan(ImageSpan(ColorDrawable(Color.RED).apply { setBounds(0, 0, 400, 100) }), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            setSpan(EpubParser.CoverPageSpan(), 0, 1, Spanned.SPAN_INCLUSIVE_INCLUSIVE)
        }
        val inner = captureContinuousViewportAnchor(3, -25, image, null, 400, 0, 900)!!
        assertEquals(0.25f, inner.imageFraction!!, 0.0001f)
        assertEquals(25, continuousViewportAnchorOffset(inner, image, null, 400, 0, 900))
    }
}
