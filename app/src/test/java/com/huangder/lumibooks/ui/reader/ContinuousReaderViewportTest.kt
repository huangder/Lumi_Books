package com.huangder.lumibooks.ui.reader

import android.app.Application
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.ImageSpan
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
class ContinuousReaderViewportTest {
    private fun image(text: SpannableStringBuilder, height: Int) {
        val start = text.length
        text.append("\uFFFC\n")
        text.setSpan(ImageSpan(ColorDrawable(Color.RED).apply { setBounds(0, 0, 100, height) }),
            start, start + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }

    @Test fun clippedViewportExcludesTextBelowTheBottomMarginAndTrailingChapterGap() {
        val text = "first\nsecond\nthird\nfourth"
        val layout = StaticLayout.Builder.obtain(text, 0, text.length,
            TextPaint().apply { textSize = 20f }, 300).setIncludePad(false).build()
        val visible = continuousVisibleCharacters(3, 0, text, layout, 300, 0, 0,
            0, layout.getLineTop(2))
        assertEquals(listOf(ReaderVisibleCharacters(3, 0, layout.getLineStart(2))), visible)
        assertEquals(ReaderReturnSide.RIGHT,
            readerReturnSideAtCharacter(3, layout.getLineStart(2), 3, 0, visible))
        assertTrue(continuousVisibleCharacters(3, -layout.height, text, layout,
            300, 0, 0, 0, 200).isEmpty())
    }

    @Test fun imageOnlyChaptersAndTheirGapsUseActualVisibleImages() {
        val text = SpannableStringBuilder().apply { image(this, 100); image(this, 200) }
        val visible = continuousVisibleCharacters(2, -110, text, null, 100, 10, 0, 0, 150)
        assertEquals(listOf(ReaderVisibleCharacters(2, 2, 4)), visible)
        assertNull(readerReturnSideAtCharacter(2, 2, 2, 2, visible))
        assertTrue(continuousVisibleCharacters(2, -100, text, null, 100, 10, 0, 0, 10).isEmpty())
    }

    @Test fun centeredCoverDoesNotCountBlankAreaAsVisibleContent() {
        val text = SpannableStringBuilder().apply { image(this, 100) }
        text.setSpan(EpubParser.CoverPageSpan(), 0, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        assertTrue(continuousVisibleCharacters(0, 0, text, null, 100, 0, 400, 0, 150).isEmpty())
        assertEquals(listOf(ReaderVisibleCharacters(0, 0, 2)),
            continuousVisibleCharacters(0, 0, text, null, 100, 0, 400, 150, 250))
    }
}
