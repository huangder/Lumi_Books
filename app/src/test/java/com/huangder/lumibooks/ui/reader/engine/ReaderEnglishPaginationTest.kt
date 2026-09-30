package com.huangder.lumibooks.ui.reader.engine

import android.app.Application
import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.view.View
import com.huangder.lumibooks.domain.model.ReaderTextAlignment
import com.huangder.lumibooks.ui.reader.BionicReadingFormatter
import com.huangder.lumibooks.ui.reader.prepareReaderEnglishHyphenation
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33, 35], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ReaderEnglishPaginationTest {
    @Test fun pageBoundariesKeepHyphensAndEverySourceCharacter() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val raw = ("Complaints of their inattention to what was going forward. " +
            "As all conversation was thereby at an end, Elizabeth soon afterwards left the room. ").repeat(12)
        var hyphenatedPages = 0
        for (font in listOf(Typeface.DEFAULT, readerSerifTypeface(context, 550))) for (height in listOf(140, 300)) for (width in listOf(240, 360)) for (bionic in listOf(false, true)) {
            val text = SpannableStringBuilder(BionicReadingFormatter.format(prepareReaderEnglishHyphenation(raw), bionic))
            val engine = PageLayoutEngine().apply {
                configure(width, height, 32f, lineSpacingPx = 0f, lineSpacingMult = 1.2f,
                    typeface = font, marginLeftPx = 20f, marginRightPx = 20f,
                    marginTopPx = 20f, marginBottomPx = 20f, textAlignment = ReaderTextAlignment.JUSTIFY)
            }
            val chapter = engine.layout(0, text)
            val full = chapter.staticLayout!!
            val edit = full.javaClass.getMethod("getEndHyphenEdit", Int::class.javaPrimitiveType)
            for (line in 0 until full.lineCount) {
                assertEquals("Captured line edit $line", edit.invoke(full, line),
                    ReaderNativeLine.measure(full, line, android.text.Layout.JUSTIFICATION_MODE_NONE)?.endHyphenEdit ?: 0)
            }
            val view = PageContentView(context).apply {
                configure(32f, android.graphics.Color.BLACK, lineHeightMult = 1.2f,
                    lineSpacingExtraPx = 0f, typeface = font, marginLeftPx = 20f, marginRightPx = 20f,
                    marginTopPx = 20f, marginBottomPx = 20f, textAlignment = ReaderTextAlignment.JUSTIFY)
            }
            var nextOffset = 0
            for (page in chapter.pages) {
                assertEquals(nextOffset, page.startCharOffset)
                nextOffset = page.endCharOffset
                view.setPageContent(text, page.startCharOffset, page.endCharOffset, endHyphenEdit = page.endHyphenEdit)
                view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
                view.layout(0, 0, width, height)
                val layout = view.textView.layout
                val last = layout.lineCount - 1
                assertTrue("Reflow clipped the end of page ${page.pageIndex}",
                    layout.getLineBaseline(last) + layout.getLineDescent(last) <= height - 40)
                assertEquals(raw.substring(page.startCharOffset, page.endCharOffset), view.textView.text.toString())
                if (page.endHyphenEdit != 0) {
                    hyphenatedPages++
                    val geometry = ReaderLineGeometry(layout, view.textView.text,
                        android.text.Layout.JUSTIFICATION_MODE_INTER_WORD, true)
                    assertEquals(page.endHyphenEdit, geometry.nativeLine(last)!!.endHyphenEdit)
                    assertTrue(geometry.lineRange(last)!!.right <= width - 39f)
                }
            }
            assertEquals(raw.length, nextOffset)
        }
        assertTrue("Fixture must split words across pages", hyphenatedPages > 0)
    }
}
