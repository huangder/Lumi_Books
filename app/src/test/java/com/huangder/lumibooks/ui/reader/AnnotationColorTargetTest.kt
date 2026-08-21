package com.huangder.lumibooks.ui.reader

import org.junit.Assert.assertEquals
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
}
