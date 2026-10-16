package com.huangder.lumibooks.ui.reader

import android.app.Application
import android.view.View
import android.widget.TextView
import android.text.SpannableString
import android.text.Spanned
import android.text.style.StrikethroughSpan
import android.text.style.UnderlineSpan
import android.text.style.ForegroundColorSpan
import androidx.test.core.app.ApplicationProvider
import com.huangder.lumibooks.domain.model.Note
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MarkdownAnnotationTapTest {
    private fun textView(content: CharSequence = "Marked text and ordinary text") = TextView(ApplicationProvider.getApplicationContext()).apply {
        text = content
        textSize = 20f
        setPadding(12, 12, 12, 12)
        measure(View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(300, View.MeasureSpec.AT_MOST))
        layout(0, 0, measuredWidth, measuredHeight)
    }
    private fun note(type: String) = Note(bookId = "md", chapterIndex = 0,
        startPosition = 100, endPosition = 106, selectedText = "Marked", note = "",
        color = "#FFCA28", createdAt = 0, type = type)

    @Test fun highlightAndUnderlineTapsResolveTheWholeAnnotation() {
        val view = textView()
        val x = view.totalPaddingLeft + view.layout.getPrimaryHorizontal(2)
        val y = view.totalPaddingTop + view.layout.getLineBottom(0) / 2f
        for (type in listOf("highlight", "underline")) {
            val annotation = note(type)
            assertEquals(annotation, markdownAnnotationAt(view, 100, listOf(annotation), x, y))
            assertNull(markdownAnnotationAt(view, 100, listOf(annotation.copy(isStale = true)), x, y))
        }
    }

    @Test fun blankPaddingAndUnmarkedTextDoNotResolveToTheNearestAnnotation() {
        val view = textView()
        val notes = listOf(note("highlight"))
        val y = view.totalPaddingTop + view.layout.getLineBottom(0) / 2f
        assertNull(markdownAnnotationAt(view, 100, notes, 0f, y))
        assertNull(markdownAnnotationAt(view, 100, notes, 20f, 0f))
        assertNull(markdownAnnotationAt(view, 100, notes, view.width - 1f, y))
        val x = view.totalPaddingLeft + view.layout.getPrimaryHorizontal(15)
        assertNull(markdownAnnotationAt(view, 100, notes, x, y))
    }

    @Test fun documentStylesAreNotUserAnnotations() {
        val view = textView(SpannableString("Marked text and ordinary text").apply {
            setSpan(StrikethroughSpan(), 0, 6, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            setSpan(UnderlineSpan(), 0, 6, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            setSpan(ForegroundColorSpan(0xff00ff00.toInt()), 0, 6, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        })
        val x = view.totalPaddingLeft + view.layout.getPrimaryHorizontal(2)
        val y = view.totalPaddingTop + view.layout.getLineBottom(0) / 2f
        assertNull(markdownAnnotationAt(view, 100, emptyList(), x, y))
        assertNull(markdownAnnotationAt(view, 100, listOf(note("underline").copy(startPosition = 115, endPosition = 119)), x, y))
    }
}
