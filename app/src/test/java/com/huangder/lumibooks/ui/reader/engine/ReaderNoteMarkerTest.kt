package com.huangder.lumibooks.ui.reader.engine

import android.app.Application
import android.graphics.RectF
import android.text.Layout
import android.text.SpannableString
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import com.huangder.lumibooks.domain.model.Note
import com.huangder.lumibooks.ui.reader.applyMarkdownAnnotationSpans
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class ReaderNoteMarkerTest {
    private fun note(start: Int, end: Int, text: String, type: String = "highlight") = Note(
        bookId = "book", chapterIndex = 0, startPosition = start, endPosition = end,
        selectedText = "x", note = text, color = "#40FFEB3B", createdAt = 1, type = type
    )

    private fun layout(text: CharSequence, width: Int = 200): StaticLayout {
        val paint = TextPaint().apply { textSize = 20f }
        return StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .build()
    }

    @Test fun onlyNotesWithTextGetMarker() {
        assertTrue(note(0, 3, "想法").hasReaderNoteMarker())
        assertTrue(note(0, 3, "想法", type = "underline").hasReaderNoteMarker())
        assertFalse(note(0, 3, "").hasReaderNoteMarker())
        assertFalse(note(0, 3, "   ").hasReaderNoteMarker())
    }

    @Test fun notesEndingOnSameLineShareOneMarkerAfterLineEnd() {
        val text = SpannableString("短句一。短句二。")
        text.setSpan(ReaderNoteMarkerSpan("甲", 3), 2, 3, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        text.setSpan(ReaderNoteMarkerSpan("乙", 7), 6, 7, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        val layout = layout(text)
        assertEquals(1, layout.lineCount)

        val markers = ReaderNoteMarkers.collect(layout, text, null)

        assertEquals(1, markers.size)
        val marker = markers.single()
        assertEquals("甲\n\n乙", marker.text)
        assertTrue(marker.rect.left > layout.getLineRight(0))
        val baseline = layout.getLineBaseline(0).toFloat()
        assertTrue(marker.rect.top < baseline && marker.rect.bottom > layout.getLineTop(0))
    }

    @Test fun markersOnDifferentLinesAndVisibleLineFilter() {
        val text = SpannableString("第一行文字\n第二行文字")
        text.setSpan(ReaderNoteMarkerSpan("一"), 1, 2, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        text.setSpan(ReaderNoteMarkerSpan("二"), 8, 9, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        val layout = layout(text)

        assertEquals(2, ReaderNoteMarkers.collect(layout, text, null).size)
        assertEquals(listOf("二"), ReaderNoteMarkers.collect(layout, text, null, lines = 1..1).map { it.text })
    }

    @Test fun hitAreaIsExpandedToMinimumTouchSize() {
        val marker = ReaderNoteMarker(0, RectF(100f, 100f, 110f, 110f), listOf("n"), emptyList())
        assertNotNull(ReaderNoteMarkers.hit(listOf(marker), 94f, 105f, 32f))
        assertNull(ReaderNoteMarkers.hit(listOf(marker), 80f, 105f, 32f))
    }

    @Test fun pagedReaderAddsSentinelOnlyForNotesWithText() {
        val reader = ReadView(RuntimeEnvironment.getApplication())
        reader.setSavedNotes(listOf(note(0, 5, "想法"), note(6, 9, "")))
        val method = ReadView::class.java.getDeclaredMethod("buildHighlights", Int::class.javaPrimitiveType)
            .apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        val highlights = method.invoke(reader, 0) as List<Triple<Int, Int, Int>>
        val markers = highlights.filter { it.third == PageContentView.NOTE_MARKER_SENTINEL }
        assertEquals(listOf(Triple(0, 5, PageContentView.NOTE_MARKER_SENTINEL)), markers)
    }

    @Test fun markerAnchorsToLastVisibleCharacterNotTrailingBreak() {
        val text = "前文。笔记句子。\n\n下一段"
        // Note selection includes the paragraph break after the sentence.
        assertEquals(8, readerNoteMarkerEnd(text, 3, 10))
        // All-whitespace selections keep their original end.
        assertEquals(10, readerNoteMarkerEnd(text, 8, 10))
    }

    @Test fun markdownMarkerOnlyInBlockWhereNoteEnds() {
        val notes = listOf(note(5, 15, "跨块笔记"))
        val first = SpannableString("0123456789")
        val second = SpannableString("abcdefghij")
        applyMarkdownAnnotationSpans(first, 0, notes)
        applyMarkdownAnnotationSpans(second, 10, notes)

        assertTrue(first.getSpans(0, first.length, ReaderNoteMarkerSpan::class.java).isEmpty())
        val span = second.getSpans(0, second.length, ReaderNoteMarkerSpan::class.java).single()
        assertEquals(4, second.getSpanStart(span))
        assertEquals("跨块笔记", span.text)
    }
}
