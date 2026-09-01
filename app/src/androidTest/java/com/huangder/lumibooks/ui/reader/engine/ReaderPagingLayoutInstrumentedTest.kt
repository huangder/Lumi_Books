package com.huangder.lumibooks.ui.reader.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Typeface
import android.text.Layout
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.AlignmentSpan
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.huangder.lumibooks.util.parser.MobiParser
import com.huangder.lumibooks.domain.model.ReaderTextAlignment
import com.huangder.lumibooks.ui.reader.applyReaderTextAlignment
import com.huangder.lumibooks.ui.reader.readerJustificationMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlinx.coroutines.runBlocking

@RunWith(AndroidJUnit4::class)
class ReaderPagingLayoutInstrumentedTest {

    @Test
    fun centeredHeadingDoesNotCenterFollowingParagraphsAfterFormatting() {
        val source = SpannableStringBuilder(
            "\u7ae0\u8282\u6807\u9898\n" +
                "\u7b2c\u4e00\u6bb5\u6b63\u6587\n" +
                "\u7b2c\u4e8c\u6bb5\u6b63\u6587\n"
        )
        val headingEnd = source.indexOf('\n') + 1
        source.setSpan(
            AlignmentSpan.Standard(Layout.Alignment.ALIGN_CENTER),
            0,
            headingEnd,
            Spanned.SPAN_PARAGRAPH
        )

        val formatted = ReaderParagraphFormatter.applyFirstLineIndent(
            text = source,
            indentCharacters = 2f,
            textSizePx = 48f,
            paragraphSpacingPx = 8f,
            skipFirstNonEmptyParagraph = true
        ) as Spanned

        val alignment = formatted.getSpans(0, formatted.length, AlignmentSpan::class.java).single()
        assertEquals(formatted.toString().indexOf('\n') + 1, formatted.getSpanEnd(alignment))
    }

    @Test
    fun pagedTextViewCannotRetainInternalScrollOffsets() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        var page: PageContentView? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            page = PageContentView(context).also { it.textView.scrollTo(120, 80) }
        }

        assertEquals(0, page?.textView?.scrollX)
        assertEquals(0, page?.textView?.scrollY)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            page?.configure(
                fontSizePx = 36f,
                textColor = Color.BLACK,
                textAlignment = ReaderTextAlignment.NATURAL
            )
        }
        assertEquals(
            ReaderTextAlignment.NATURAL.readerJustificationMode(),
            page?.textView?.justificationMode
        )
    }

    @Test
    fun paginationUsesOnlySmallSafetyAllowance() = runBlocking {
        val engine = PageLayoutEngine()
        val text = "正文\n".repeat(120)
        val width = 420
        val height = 640
        val topBottom = 48
        engine.configure(
            width = width,
            height = height,
            fontSizePx = 32f,
            lineSpacingPx = 0f,
            lineSpacingMult = 1f,
            marginTopPx = topBottom.toFloat(),
            marginBottomPx = topBottom.toFloat(),
            chapterCount = 1
        )

        val chapter = engine.layout(0, text)
        val static = chapter.staticLayout
        var expectedLines = 0
        var used = 0
        val available = height - topBottom * 2 - 2
        while (expectedLines < static.lineCount) {
            val line = expectedLines
            val lineHeight = static.getLineBottom(line) - static.getLineTop(line)
            if (expectedLines > 0 && used + lineHeight > available) break
            used += lineHeight
            expectedLines++
        }

        assertEquals(expectedLines, chapter.pages.first().endLine)
    }

    @Test
    fun globalVerticalMarginsAreDeterministicAndProtectBottomInset() {
        val first = resolveReaderVerticalMargins(
            heightPx = 720,
            baseMarginTopPx = 56f,
            baseMarginBottomPx = 56f,
            fontSizePx = 36f,
            lineHeightMultiplier = 1f,
            lineSpacingExtraPx = 0f,
            typeface = Typeface.DEFAULT
        )
        val second = resolveReaderVerticalMargins(
            heightPx = 720,
            baseMarginTopPx = 56f,
            baseMarginBottomPx = 56f,
            fontSizePx = 36f,
            lineHeightMultiplier = 1f,
            lineSpacingExtraPx = 0f,
            typeface = Typeface.DEFAULT
        )
        val protected = resolveReaderVerticalMargins(
            heightPx = 720,
            baseMarginTopPx = 56f,
            baseMarginBottomPx = 56f,
            fontSizePx = 36f,
            lineHeightMultiplier = 1f,
            lineSpacingExtraPx = 0f,
            typeface = Typeface.DEFAULT,
            protectedBottomInsetPx = 48f
        )

        assertEquals(first, second)
        assertEquals(112, first.topPx + first.bottomPx)
        assertTrue("global balance should move some space above the text", first.topPx > 56)
        assertTrue("global balance should reduce the bottom remainder", first.bottomPx < 56)
        assertEquals(112, protected.topPx + protected.bottomPx)
        assertTrue("protected bottom inset must remain available", protected.bottomPx >= 48)
    }

    @Test
    fun pageContentKeepsOneVerticalOriginAcrossDifferentPageLengths() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        var firstBaseline = 0
        var secondBaseline = 0
        var firstTranslation = 0f
        var secondTranslation = 0f
        var firstOffset = 0f
        var secondOffset = 0f
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val page = PageContentView(context)
            page.configure(
                fontSizePx = 36f,
                textColor = Color.BLACK,
                lineHeightMult = 1f,
                lineSpacingExtraPx = 0f,
                marginLeftPx = 40f,
                marginTopPx = 56f,
                marginRightPx = 40f,
                marginBottomPx = 56f
            )
            page.measure(
                android.view.View.MeasureSpec.makeMeasureSpec(600, android.view.View.MeasureSpec.EXACTLY),
                android.view.View.MeasureSpec.makeMeasureSpec(720, android.view.View.MeasureSpec.EXACTLY)
            )
            page.layout(0, 0, 600, 720)

            fun setPage(text: String): Int {
                page.setPageContent(text, 0, text.length)
                page.measure(
                    android.view.View.MeasureSpec.makeMeasureSpec(600, android.view.View.MeasureSpec.EXACTLY),
                    android.view.View.MeasureSpec.makeMeasureSpec(720, android.view.View.MeasureSpec.EXACTLY)
                )
                page.layout(0, 0, 600, 720)
                val layout = requireNotNull(page.textView.layout)
                return page.textView.paddingTop + layout.getLineBaseline(0)
            }

            firstBaseline = setPage("第一行内容保持相同起点。\n".repeat(28))
            firstTranslation = page.textView.translationY
            firstOffset = page.getPageVerticalOffset()
            secondBaseline = setPage("第一行内容保持相同起点。\n第二行内容。")
            secondTranslation = page.textView.translationY
            secondOffset = page.getPageVerticalOffset()
        }

        assertEquals(firstBaseline, secondBaseline)
        assertEquals(0f, firstTranslation, 0f)
        assertEquals(0f, secondTranslation, 0f)
        assertEquals(0f, firstOffset, 0f)
        assertEquals(0f, secondOffset, 0f)
    }

    @Test
    fun finalLineInkStaysInsideBottomPadding() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        var page: PageContentView? = null
        val width = 600
        val height = 720
        val margin = 56
        val text = "最后一行也必须完整显示，不能被底部边界裁切。"
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            page = PageContentView(context).also {
                it.configure(
                    fontSizePx = 36f,
                    textColor = Color.BLACK,
                    lineHeightMult = 1f,
                    lineSpacingExtraPx = 0f,
                    marginLeftPx = 40f,
                    marginTopPx = margin.toFloat(),
                    marginRightPx = 40f,
                    marginBottomPx = margin.toFloat()
                )
                it.setPageContent(text, 0, text.length)
                it.measure(
                    android.view.View.MeasureSpec.makeMeasureSpec(width, android.view.View.MeasureSpec.EXACTLY),
                    android.view.View.MeasureSpec.makeMeasureSpec(height, android.view.View.MeasureSpec.EXACTLY)
                )
                it.layout(0, 0, width, height)
            }
        }

        val view = requireNotNull(page)
        val layout = requireNotNull(view.textView.layout)
        val lastLine = layout.lineCount - 1
        val inkBottom = view.textView.totalPaddingTop +
            layout.getLineBaseline(lastLine) + layout.getLineDescent(lastLine)
        assertTrue("last line must stay inside bottom padding", inkBottom <= height - margin)

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            bitmap.eraseColor(Color.WHITE)
            view.draw(Canvas(bitmap))
        }
        var lastInkRow = -1
        for (y in 0 until height) {
            for (x in 0 until width) {
                if (Color.alpha(bitmap.getPixel(x, y)) > 0 && bitmap.getPixel(x, y) != Color.WHITE) {
                    lastInkRow = y
                    break
                }
            }
        }
        assertTrue("rendered ink must not enter bottom padding", lastInkRow < height - margin)
        bitmap.recycle()
    }

    @Test
    fun equalMarginsProduceEqualTextPaddingAndContentWidth() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        var page: PageContentView? = null
        val text = "这是一段用于验证左右边距完全对称的中文正文。"
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            page = PageContentView(context).also {
                it.configure(
                    fontSizePx = 36f,
                    textColor = Color.BLACK,
                    marginLeftPx = 72f,
                    marginRightPx = 72f,
                    textAlignment = ReaderTextAlignment.NATURAL
                )
                it.setPageContent(text, 0, text.length)
                it.measure(
                    android.view.View.MeasureSpec.makeMeasureSpec(600, android.view.View.MeasureSpec.EXACTLY),
                    android.view.View.MeasureSpec.makeMeasureSpec(720, android.view.View.MeasureSpec.EXACTLY)
                )
                it.layout(0, 0, 600, 720)
            }
        }

        val textView = requireNotNull(page).textView
        assertEquals(textView.totalPaddingLeft, textView.totalPaddingRight)
        assertEquals(600 - 144, requireNotNull(textView.layout).width)
    }

    @Test
    fun mobiHeadingAlignmentDoesNotLeakIntoBody() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val parser = MobiParser(context)
        val converted = parser.htmlToSpanned(
            "<p align=\"center\">\u7b2c\u56db\u7ae0\u3000\u94a5\u5319\u4fdd\u7ba1\u5458</p>" +
                "<p align=\"justify\">\u6d77\u683c\u663e\u5f97\u9707\u60ca\u3002</p>" +
                "<p align=\"justify\">\u6b63\u6587\u7ee7\u7eed\u3002</p>"
        )
        val headingOffset = converted.toString().indexOf("\u7b2c\u56db\u7ae0")
        val bodyOffset = converted.toString().indexOf("\u6d77\u683c\u663e\u5f97\u9707\u60ca")
        val headingAlignments = converted.getSpans(
            headingOffset,
            headingOffset + 1,
            AlignmentSpan::class.java
        )
        val bodyAlignments = converted.getSpans(
            bodyOffset,
            bodyOffset + 1,
            AlignmentSpan::class.java
        )

        assertTrue(headingAlignments.any { it.alignment == Layout.Alignment.ALIGN_CENTER })
        assertTrue(bodyAlignments.none { it.alignment == Layout.Alignment.ALIGN_CENTER })
    }

    @Test
    fun naturalTextAlignmentPreservesPublisherAlignment() {
        val source = SpannableStringBuilder("Heading\nBody\n")
        source.setSpan(
            AlignmentSpan.Standard(Layout.Alignment.ALIGN_CENTER),
            0,
            source.indexOf('\n') + 1,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )

        val formatted = applyReaderTextAlignment(source, ReaderTextAlignment.NATURAL) as Spanned

        assertEquals(
            Layout.Alignment.ALIGN_CENTER,
            formatted.getSpans(0, 1, AlignmentSpan::class.java).single().alignment
        )
        assertTrue(formatted.getSpans(8, 9, AlignmentSpan::class.java).isEmpty())
    }

    @Test
    fun explicitTextAlignmentOverridesEveryParagraph() {
        val source = SpannableStringBuilder("Heading\nBody\n")
        source.setSpan(
            AlignmentSpan.Standard(Layout.Alignment.ALIGN_CENTER),
            0,
            source.indexOf('\n') + 1,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )

        val formatted = applyReaderTextAlignment(source, ReaderTextAlignment.RIGHT) as Spanned

        listOf(0, 8).forEach { offset ->
            assertEquals(
                Layout.Alignment.ALIGN_OPPOSITE,
                formatted.getSpans(offset, offset + 1, AlignmentSpan::class.java).single().alignment
            )
        }
    }
}
