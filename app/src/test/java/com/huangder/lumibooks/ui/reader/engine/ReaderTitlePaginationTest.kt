package com.huangder.lumibooks.ui.reader.engine

import android.app.Application
import android.graphics.Color
import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.AbsoluteSizeSpan
import android.text.style.StyleSpan
import android.view.View
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ReaderTitlePaginationTest {
    @Test fun wrappedTxtTitleKeepsBodyAboveBottomMargin() = verifyPages(true)

    @Test fun styledReflowTitleKeepsBodyAboveBottomMargin() = verifyPages(false)

    private fun verifyPages(txt: Boolean) = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val title = "【000】七宗罪病毒"
        val raw = SpannableStringBuilder(title + "\n\n" +
            ("七宗罪病毒，具有放大内心的欲望，引发身体变异、解除自我克制等病征。\n" +
                "贪吃的人，将会逐渐无节制的进食，贪财的人，将会不惜一切手段敛财。\n").repeat(12)).apply {
            setSpan(AbsoluteSizeSpan(32, true), 0, title.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            setSpan(StyleSpan(Typeface.BOLD), 0, title.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        val text = applyReaderPunctuationCompression(ReaderParagraphFormatter.applyFirstLineIndent(
            raw, 1f, 70f, 0f, true))
        for (height in listOf(1000, 1920)) {
            val pageView = PageContentView(context).apply {
                configure(70f, Color.BLACK, lineHeightMult = 1.5f, lineSpacingExtraPx = 7.5f,
                    marginLeftPx = 72f, marginRightPx = 72f, marginTopPx = 120f, marginBottomPx = 160f)
            }
            val engine = PageLayoutEngine().apply {
                configure(864, height, 70f, lineSpacingPx = 7.5f, lineSpacingMult = 1.5f,
                    marginLeftPx = 72f, marginRightPx = 72f, marginTopPx = 120f, marginBottomPx = 160f,
                    useDisplayDensityForSpans = txt)
                sharedTextPaint = pageView.textView.paint
            }
            val chapter = engine.layout(0, text)
            var nextOffset = 0
            for (page in chapter.pages) {
                assertEquals(nextOffset, page.startCharOffset)
                nextOffset = page.endCharOffset
                pageView.setPageContent(text, page.startCharOffset, page.endCharOffset)
                pageView.measure(View.MeasureSpec.makeMeasureSpec(864, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
                pageView.layout(0, 0, 864, height)
                val layout = pageView.textView.layout
                if (page.pageIndex == 0) assertTrue("Fixture must wrap the rendered title", layout.getLineEnd(0) < title.length)
                var last = layout.lineCount - 1
                while (last > 0 && layout.text.subSequence(layout.getLineStart(last), layout.getLineEnd(last)).isBlank()) last--
                val bottom = layout.getLineBaseline(last) + layout.getLineDescent(last)
                assertTrue("txt=$txt height=$height page=${page.pageIndex}: body bottom $bottom exceeds ${height - 280}",
                    bottom <= height - 280)
            }
            assertEquals(text.length, nextOffset)
        }
    }
}
