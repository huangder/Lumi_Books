package com.huangder.lumibooks.ui.reader

import android.app.Application
import android.graphics.Color
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.UnderlineSpan
import com.huangder.lumibooks.domain.model.Note
import com.huangder.lumibooks.ui.reader.engine.WaveUnderlineSpan
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class ContinuousUnderlineAnnotationsTest {
    @Test fun changingManualStylesPreservesRuleAndPublisherUnderlinesWithoutDuplicates() {
        val content = SpannableStringBuilder("manual rule publisher")
        val rule = WaveUnderlineSpan(Color.BLUE, 2)
        val publisher = UnderlineSpan()
        content.setSpan(rule, 7, 11, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        content.setSpan(publisher, 12, 21, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        val manual = Note(bookId = "book", chapterIndex = 0, startPosition = 0, endPosition = 6,
            selectedText = "manual", note = "", color = "#ff0000", createdAt = 1, type = "underline")
        val generated = manual.copy(startPosition = 7, endPosition = 11,
            origin = Note.ORIGIN_HIGHLIGHT_RULE, sourceRuleId = "rule", sourceMatchKey = "match")
        for (mode in listOf(3, 1, 3, 2, 4)) {
            val updated = manual.copy(styleSnapshotJson = """{"underlineMode":$mode}""")
            updateContinuousAnnotations(content, listOf(updated, generated), null, 0f)
            val spans = content.getSpans(0, content.length, WaveUnderlineSpan::class.java)
            assertEquals(2, spans.size)
            assertTrue(rule in spans)
            assertEquals(mode, spans.single { it.fromSavedAnnotation }.mode)
            assertEquals(12, content.getSpanStart(publisher))
        }
        updateContinuousAnnotations(content, emptyList(), null, 0f)
        assertEquals(listOf(rule), content.getSpans(0, content.length, WaveUnderlineSpan::class.java).toList())
    }
}
