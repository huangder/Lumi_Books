package com.huangder.lumibooks.ui.reader

import com.huangder.lumibooks.domain.model.Note
import com.huangder.lumibooks.domain.model.RuleStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AnnotationColorTargetTest {
    @Test
    fun highlightColorTargetCreatesHighlightNote() {
        assertEquals("highlight", AnnotationColorTarget.HIGHLIGHT.noteType)
    }

    @Test
    fun underlineColorTargetCreatesUnderlineNote() {
        assertEquals("underline", AnnotationColorTarget.UNDERLINE.noteType)
    }

    @Test
    fun changingUnderlineColorDoesNotChangeTextColor() {
        val style = RuleStyle(
            textColor = 0xFF112233.toInt(),
            underlineMode = 3,
            underlineOffset = 2f,
            underlineWidth = 1f,
            fontWeight = 700,
            italic = true
        )

        val updated = annotationStyleAfterColorChange(style, "underline", 0xFFCC5500.toInt())

        // The picked colour goes to the mark; the rule's text colour is left untouched.
        assertEquals(0xFFCC5500.toInt(), updated.highlightColor)
        assertEquals(style.textColor, updated.textColor)
        assertEquals(style.underlineMode, updated.underlineMode)
        assertEquals(style.fontWeight, updated.fontWeight)
        assertEquals(style.italic, updated.italic)
    }

    @Test
    fun changingHighlightColorUpdatesMarkColorOnly() {
        val style = RuleStyle(null, 0, 2f, 1f, 400, false, highlightColor = 0xFF00FF00.toInt())
        val updated = annotationStyleAfterColorChange(style, "highlight", 0xFFCC5500.toInt())
        assertEquals(0xFFCC5500.toInt(), updated.highlightColor)
        assertNull(updated.textColor)
    }

    @Test
    fun unresolvedColorKeepsPreviousMarkColor() {
        val style = RuleStyle(null, 0, 2f, 1f, 400, false, highlightColor = 0xFF00FF00.toInt())
        assertEquals(0xFF00FF00.toInt(), annotationStyleAfterColorChange(style, "highlight", null).highlightColor)
    }

    @Test
    fun manualUnderlineSnapshotsDoNotCarryTextColorIntoReaderText() {
        val note = Note(
            bookId = "book",
            chapterIndex = 0,
            startPosition = 0,
            endPosition = 4,
            selectedText = "text",
            note = "",
            color = "#FF00FF00",
            createdAt = 1L,
            type = "underline"
        )
        val style = RuleStyle(0xFFFF0000.toInt(), 3, 2f, 1f, 400, false)

        assertNull(annotationSnapshotStyleForRendering(note, style).textColor)
    }
}
