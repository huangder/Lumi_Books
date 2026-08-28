package com.huangder.lumibooks.ui.reader.engine

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.TextPaint
import android.text.style.ImageSpan
import android.text.style.LeadingMarginSpan
import android.text.style.URLSpan
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.huangder.lumibooks.domain.model.ReaderWritingMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VerticalTextLayouterInstrumentedTest {

    @Test
    fun dashesUseVerticalPresentationFormsWithoutChangingSourceRanges() {
        val text = "\u2014\u2014\u2013"
        val pages = layout(text, width = 20, height = 60)
        val glyphs = pages.flatMap { it.geometry.glyphs }

        assertEquals(listOf(0 to 1, 1 to 2, 2 to 3), glyphs.map { it.startOffset to it.endOffset })
        assertEquals("\uFE31", verticalPresentationText(text.substring(0, 1)))
        assertEquals("\uFE31", verticalPresentationText(text.substring(1, 2)))
        assertEquals("\uFE32", verticalPresentationText(text.substring(2, 3)))
        assertTrue(glyphs.none { it.rotateClockwise })
    }

    @Test
    fun curlyQuotesUseVerticalCornerFormsWithoutChangingSourceRanges() {
        val text = "\u201C甲\u2018乙\u2019丙\u201D"
        val pages = layout(text, width = 40, height = 80)
        val glyphs = pages.flatMap { it.geometry.glyphs }

        assertEquals(text.indices.map { it to it + 1 }, glyphs.map { it.startOffset to it.endOffset })
        assertEquals("\uFE43", verticalPresentationText("\u201C"))
        assertEquals("\uFE44", verticalPresentationText("\u201D"))
        assertEquals("\uFE41", verticalPresentationText("\u2018"))
        assertEquals("\uFE42", verticalPresentationText("\u2019"))
        assertTrue(glyphs.none { it.rotateClockwise })
    }

    @Test
    fun pagesHaveContinuousSourceRangesAndRightToLeftColumns() {
        val text = "一二三四五六七八九十"
        val pages = layout(text, width = 50, height = 60)

        assertEquals(0, pages.first().startOffset)
        pages.zipWithNext().forEach { (current, next) ->
            assertEquals(current.endOffset, next.startOffset)
        }
        assertEquals(text.length, pages.last().endOffset)

        val firstPage = pages.first().geometry.glyphs
        val firstColumn = firstPage.filter { it.columnIndex == 0 }
        val secondColumn = firstPage.filter { it.columnIndex == 1 }
        assertTrue(firstColumn.isNotEmpty())
        assertTrue(secondColumn.isNotEmpty())
        assertTrue(firstColumn.first().bounds.left > secondColumn.first().bounds.left)
        assertTrue(firstColumn.zipWithNext().all { (a, b) -> a.bounds.top < b.bounds.top })
    }

    @Test
    fun graphemeClustersAreNeverSplitAcrossGlyphsOrPages() {
        val family = "👨‍👩‍👧‍👦"
        val combining = "e\u0301"
        val text = "甲${family}${combining}乙"
        val pages = layout(text, width = 20, height = 20)
        val clusters = pages.flatMap { page ->
            page.geometry.glyphs.map { text.substring(it.startOffset, it.endOffset) }
        }

        assertEquals(listOf("甲", family, combining, "乙"), clusters)
        assertEquals(text.length, pages.last().endOffset)
    }

    @Test
    fun verticalAdvanceUsesMeasuredClusterWidthForLatinText() {
        val paint = TextPaint().apply {
            textSize = 40f
            density = 1f
            typeface = android.graphics.Typeface.DEFAULT
        }
        val pages = VerticalTextLayouter.layout(
            text = "iW",
            paint = paint,
            width = 80,
            height = 200,
            lineSpacingExtra = 0f,
            lineSpacingMultiplier = 1f,
            letterSpacing = 0f
        )
        val glyphs = pages.single().geometry.glyphs
        val expected = paint.measureText("i")
        assertEquals(expected, glyphs[0].bounds.bottom - glyphs[0].bounds.top, 0.01f)
        assertTrue(glyphs[1].bounds.bottom - glyphs[1].bounds.top > glyphs[0].bounds.bottom - glyphs[0].bounds.top)
    }

    @Test
    fun indentationSpacingAndKinsokuAffectVerticalGeometry() {
        val text = SpannableStringBuilder("甲乙。）丁戊己（庚辛")
        text.setSpan(
            LeadingMarginSpan.Standard(40, 0),
            0,
            text.length,
            Spannable.SPAN_INCLUSIVE_INCLUSIVE
        )
        val pages = layout(text, width = 105, height = 100, lineHeight = 1.5f, extra = 5f, letter = 4f)
        val firstPage = pages.first()
        val glyphs = firstPage.geometry.glyphs

        assertEquals(40f, glyphs.first().bounds.top, 0.01f)
        assertEquals(24f, glyphs.first().bounds.bottom - glyphs.first().bounds.top, 0.01f)
        val columns = glyphs.groupBy { it.columnIndex }.toSortedMap()
        if (columns.size >= 2) {
            val firstX = columns.getValue(0).first().bounds.left
            val secondX = columns.getValue(1).first().bounds.left
            assertEquals(35f, firstX - secondX, 0.01f)
        }
        pages.forEach { page ->
            page.geometry.glyphs.groupBy { it.columnIndex }.values.forEach { column ->
                val first = text.substring(column.first().startOffset, column.first().endOffset)
                val last = text.substring(column.last().startOffset, column.last().endOffset)
                assertTrue("closing punctuation at column start: $first", !isVerticalColumnStartProhibited(first))
                assertTrue("opening punctuation at column end: $last", !isVerticalColumnEndProhibited(last))
            }
        }
    }

    @Test
    fun paragraphIndentIsNotRepeatedWhenParagraphContinuesOnNextPage() {
        val text = SpannableStringBuilder("甲乙丙丁戊己庚辛壬癸")
        text.setSpan(
            LeadingMarginSpan.Standard(40, 0),
            0,
            text.length,
            Spannable.SPAN_INCLUSIVE_INCLUSIVE
        )

        val pages = layout(text, width = 20, height = 60)

        assertTrue(pages.size > 1)
        assertEquals(40f, pages.first().geometry.glyphs.first().bounds.top, 0.01f)
        pages.drop(1).forEach { continuationPage ->
            assertEquals(0f, continuationPage.geometry.glyphs.first().bounds.top, 0.01f)
        }
    }

    @Test
    fun paragraphStartingOnLaterPageKeepsItsIndent() {
        val firstParagraph = "甲乙丙丁戊"
        val secondParagraph = "己庚辛"
        val text = SpannableStringBuilder("$firstParagraph\n$secondParagraph")
        val secondStart = firstParagraph.length + 1
        text.setSpan(
            LeadingMarginSpan.Standard(40, 0),
            0,
            firstParagraph.length,
            Spannable.SPAN_INCLUSIVE_INCLUSIVE
        )
        text.setSpan(
            LeadingMarginSpan.Standard(40, 0),
            secondStart,
            text.length,
            Spannable.SPAN_INCLUSIVE_INCLUSIVE
        )

        val pages = layout(text, width = 20, height = 60)
        val secondParagraphFirstGlyph = pages
            .flatMap { it.geometry.glyphs }
            .first { it.startOffset == secondStart }

        assertEquals(40f, secondParagraphFirstGlyph.bounds.top, 0.01f)
    }

    @Test
    fun adjacentImagesEachOccupyOnePageAndRetainLinkHitGeometry() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val text = SpannableStringBuilder("前￼￼后")
        val firstDrawable = ColorDrawable(Color.RED).apply { setBounds(0, 0, 200, 100) }
        val secondDrawable = ColorDrawable(Color.BLUE).apply { setBounds(0, 0, 100, 200) }
        text.setSpan(ImageSpan(firstDrawable, "first"), 1, 2, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        text.setSpan(URLSpan("https://example.test/image"), 1, 2, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        text.setSpan(ImageSpan(secondDrawable, "second"), 2, 3, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)

        val pages = layout(text, width = 120, height = 180)
        assertEquals(4, pages.size)
        assertEquals(1, pages[1].geometry.items.size)
        assertEquals(1, pages[2].geometry.items.size)
        assertTrue(pages[1].geometry.items.single() is VerticalImageLayout)
        assertTrue(pages[2].geometry.items.single() is VerticalImageLayout)
        assertEquals(1, pages[1].startOffset)
        assertEquals(2, pages[1].endOffset)
        assertEquals(2, pages[2].startOffset)
        assertEquals(3, pages[2].endOffset)

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val pageView = PageContentView(context)
            pageView.configure(
                fontSizePx = 20f,
                textColor = Color.BLACK,
                marginLeftPx = 0f,
                marginTopPx = 0f,
                marginRightPx = 0f,
                marginBottomPx = 0f,
                writingMode = ReaderWritingMode.VERTICAL_RL
            )
            pageView.setPageContent(text, 1, 2, verticalGeometry = pages[1].geometry)
            val imageBounds = pages[1].geometry.image!!.bounds
            val hit = pageView.getImageAt(imageBounds.centerX, imageBounds.centerY)
            assertNotNull(hit)
            assertEquals("first", hit?.source)
            assertEquals("https://example.test/image", hit?.link)
        }
    }

    @Test
    fun leadingLineBreakBeforeImageDoesNotCreateAnEmptyPage() {
        val text = SpannableStringBuilder("\n￼后")
        val drawable = ColorDrawable(Color.RED).apply { setBounds(0, 0, 100, 100) }
        text.setSpan(ImageSpan(drawable, "cover"), 1, 2, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)

        val pages = layout(text, width = 120, height = 180)

        assertEquals(2, pages.size)
        assertEquals(0, pages[0].startOffset)
        assertEquals(2, pages[0].endOffset)
        assertTrue(pages[0].geometry.items.single() is VerticalImageLayout)
        assertEquals(2, pages[1].startOffset)
        assertEquals(3, pages[1].endOffset)
    }

    private fun layout(
        text: CharSequence,
        width: Int,
        height: Int,
        lineHeight: Float = 1f,
        extra: Float = 0f,
        letter: Float = 0f
    ): List<VerticalPageSlice> {
        val paint = TextPaint().apply {
            textSize = 20f
            density = 1f
        }
        return VerticalTextLayouter.layout(
            text = text,
            paint = paint,
            width = width,
            height = height,
            lineSpacingExtra = extra,
            lineSpacingMultiplier = lineHeight,
            letterSpacing = letter
        )
    }
}
