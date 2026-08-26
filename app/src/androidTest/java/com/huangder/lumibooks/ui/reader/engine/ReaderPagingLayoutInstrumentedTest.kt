package com.huangder.lumibooks.ui.reader.engine

import android.content.Context
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

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
        // The visible renderer must stay left-aligned (NONE) so its line breaks
        // exactly match PageLayoutEngine's ALIGN_NORMAL StaticLayout. Justified
        // breaks would add a trailing-space reserve and shift the last line by
        // one character when letterSpacing > 0.
        assertEquals(Layout.JUSTIFICATION_MODE_NONE, page?.textView?.justificationMode)
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
