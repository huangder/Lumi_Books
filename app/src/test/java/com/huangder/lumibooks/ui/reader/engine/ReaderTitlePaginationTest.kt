package com.huangder.lumibooks.ui.reader.engine

import android.app.Application
import android.graphics.Color
import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.AbsoluteSizeSpan
import android.text.style.RelativeSizeSpan
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

    @Test fun threeLineChapterTitleKeepsBodyAboveFooter() {
        for (spacing in listOf(0f, 12f, 30f)) {
            verifyPages(true, "第356章 那小子已经把灵元宗拿捏死了？", spacing)
        }
    }

    @Test fun reflowHeadingWithDifferentFontsKeepsAllTextAboveFooter() {
        for (font in listOf(Typeface.SERIF, Typeface.SANS_SERIF, Typeface.MONOSPACE)) {
            verifyPages(false, "第356章 那小子已经把灵元宗拿捏死了？", 12f, font, relativeTitle = true)
        }
    }

    private fun verifyPages(
        txt: Boolean,
        title: String = "【000】七宗罪病毒",
        paragraphSpacing: Float = 0f,
        font: Typeface = Typeface.DEFAULT,
        relativeTitle: Boolean = false
    ) = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val raw = SpannableStringBuilder(title + "\n\n" +
            ("七宗罪病毒，具有放大内心的欲望，引发身体变异、解除自我克制等病征。\n" +
                "贪吃的人，将会逐渐无节制的进食，贪财的人，将会不惜一切手段敛财。\n").repeat(12)).apply {
            setSpan(if (relativeTitle) RelativeSizeSpan(1.4f) else AbsoluteSizeSpan(32, true),
                0, title.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            setSpan(StyleSpan(Typeface.BOLD), 0, title.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        val text = applyReaderPunctuationCompression(ReaderParagraphFormatter.applyFirstLineIndent(
            raw, 1f, 70f, paragraphSpacing, true))
        for (height in listOf(1000, 1920)) {
            val pageView = PageContentView(context).apply {
                configure(70f, Color.BLACK, lineHeightMult = 1.5f, lineSpacingExtraPx = 7.5f, typeface = font,
                    marginLeftPx = 72f, marginRightPx = 72f, marginTopPx = 120f, marginBottomPx = 160f)
            }
            val engine = PageLayoutEngine().apply {
                configure(864, height, 70f, lineSpacingPx = 7.5f, lineSpacingMult = 1.5f,
                    typeface = font,
                    marginLeftPx = 72f, marginRightPx = 72f, marginTopPx = 120f, marginBottomPx = 160f,
                    useDisplayDensityForSpans = txt)
                sharedTextPaint = pageView.textView.paint
            }
            val chapter = engine.layout(0, text)
            assertEquals(pageView.textView.paint.density, chapter.staticLayout.paint.density, 0f)
            var nextOffset = 0
            for (page in chapter.pages) {
                assertEquals(nextOffset, page.startCharOffset)
                nextOffset = page.endCharOffset
                pageView.setPageContent(text, page.startCharOffset, page.endCharOffset)
                pageView.measure(View.MeasureSpec.makeMeasureSpec(864, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
                pageView.layout(0, 0, 864, height)
                val layout = pageView.textView.layout
                if (page.pageIndex == 0) {
                    val minimumTitleLines = if (title.startsWith("第356章")) 3 else 2
                    assertTrue("Fixture must render at least $minimumTitleLines title lines",
                        layout.getLineForOffset(title.length - 1) + 1 >= minimumTitleLines)
                }
                assertEquals(text.subSequence(pageView.chapterStartOffset, page.endCharOffset).toString(),
                    pageView.textView.text.toString())
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
