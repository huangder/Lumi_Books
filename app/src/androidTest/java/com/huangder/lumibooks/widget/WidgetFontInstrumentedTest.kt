package com.huangder.lumibooks.widget

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.widget.FrameLayout
import android.widget.RemoteViews
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.core.content.res.ResourcesCompat
import com.huangder.lumibooks.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WidgetFontInstrumentedTest {
    @Test
    fun quoteRemoteViewsLoadsTheConfiguredTtcFace() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val parent = FrameLayout(context)
        listOf(R.layout.widget_quote_square, R.layout.widget_quote_wide).forEach { layout ->
            val view = RemoteViews(context.packageName, layout).apply(context, parent)
            val quote = view.findViewById<TextView>(R.id.widget_quote_text)

            assertEquals(300, quote.typeface.weight)
            assertTrue(quote.includeFontPadding)
            val paint = Paint().apply { typeface = quote.typeface }
            assertTrue(
                listOf('\u6e90', '\u6d41', '\u660e', '\u9ad4')
                    .all { paint.hasGlyph(it.toString()) }
            )
        }
    }

    @Test
    fun renderedQuoteUsesPixelsWithoutTouchingTheBottomEdge() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val typeface = requireNotNull(ResourcesCompat.getFont(context, R.font.gen_ryu_min2_light))

        val rendered = QuoteExcerptFormatter.render(
            text = "\u7cbe\u795e\u529b\u4f7f\u81ea\u5df1\u7684\u5e7d\u9b42\u62e5\u6709\u529b\u91cf\u3002",
            typeface = typeface,
            textSizePx = 48f,
            textColor = Color.BLACK,
            widthPx = 480,
            heightPx = 260,
            maxLines = 4,
            lineSpacingMultiplier = 1.24f
        )
        val pixels = IntArray(rendered.bitmap.width * rendered.bitmap.height)
        rendered.bitmap.getPixels(
            pixels,
            0,
            rendered.bitmap.width,
            0,
            0,
            rendered.bitmap.width,
            rendered.bitmap.height
        )

        assertTrue(pixels.any { Color.alpha(it) != 0 })
        val bottomRow = pixels.takeLast(rendered.bitmap.width)
        assertTrue(bottomRow.all { Color.alpha(it) == 0 })
    }
}
