package com.huangder.lumibooks.ui.reader

import android.app.Application
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.ImageSpan
import android.text.style.LineHeightSpan
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ContinuousImageLayoutTest {
    private fun image(text: SpannableStringBuilder, height: Int, source: String? = null) {
        val start = text.length
        text.append("\uFFFC\n")
        val drawable = ColorDrawable(Color.RED).apply { setBounds(0, 0, 240, height) }
        val span = if (source == null) ImageSpan(drawable) else ImageSpan(drawable, source)
        text.setSpan(span, start, start + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }

    @Test fun mediaChapterPreservesOrderEvenWithoutSourcePaths() {
        val text = SpannableStringBuilder()
        image(text, 800)
        image(text, 1400, "../images/page.png")
        val images = continuousChapterImages(text)
        assertEquals(listOf(800, 1400), images.map { it.drawable.bounds.height() })
        assertEquals(0, continuousImageCharacterTop(text, 480, 8, 0))
        assertEquals(1608, continuousImageCharacterTop(text, 480, 8, 2))
        assertEquals(800, continuousImageCharacterTop(text, 240, 0, 2))
        assertNull(continuousImageCharacterTop(text, 0, 8, 2))
    }

    @Test fun noteAfterIllustrationsUsesCurrentTextLayoutRatherThanChapterStartOrTextRatio() {
        val text = SpannableStringBuilder("before\n")
        image(text, 800)
        text.append("paragraph ".repeat(100))
        val noteOffset = text.length
        text.append("note target\nend")
        protectContinuousImageHeights(text)
        fun layout(width: Int) = StaticLayout.Builder.obtain(text, 0, text.length,
            TextPaint().apply { textSize = 20f }, width).setIncludePad(false).build()
        val oldLayout = layout(600)
        val currentLayout = layout(300)
        val target = continuousCharacterTop(currentLayout, noteOffset)
        assertTrue(target > 800)
        assertTrue(target > continuousCharacterTop(oldLayout, noteOffset))
        assertEquals(currentLayout.getLineTop(currentLayout.getLineForOffset(noteOffset)), target)
    }

    @Test fun mixedChapterKeepsTextAndImageSpans() {
        val text = SpannableStringBuilder("before\n")
        image(text, 800)
        text.append("after")
        assertTrue(continuousChapterImages(text).isEmpty())
        protectContinuousImageHeights(text)
        assertEquals("before\n\uFFFC\nafter", text.toString())
        assertEquals(1, text.getSpans(0, text.length, ImageSpan::class.java).size)
        assertTrue(continuousChapterImages("plain novel").isEmpty())
    }

    @Test fun imageRowsReserveFullHeightDespitePublisherLineHeight() {
        val text = SpannableStringBuilder()
        image(text, 800)
        image(text, 1400)
        text.append("after")
        text.setSpan(LineHeightSpan { _, _, _, _, _, fm ->
            fm.ascent = -20; fm.top = -20; fm.descent = 0; fm.bottom = 0
        }, 0, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        protectContinuousImageHeights(text)
        val layout = StaticLayout.Builder.obtain(text, 0, text.length,
            TextPaint().apply { textSize = 20f }, 300).setIncludePad(false).build()
        assertTrue(layout.getLineTop(1) >= 800)
        assertTrue(layout.getLineTop(2) >= 2200)
        assertEquals(layout.getLineTop(2), continuousCharacterTop(layout, 4))
        assertEquals(0, continuousCharacterTop(layout, -1))
        assertEquals(layout.getLineTop(2), continuousCharacterTop(layout, Int.MAX_VALUE))
    }
}
