package com.huangder.lumibooks.ui.reader

import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderFooterProgressTest {
    @Test
    fun calculatesWholeBookProgressFromChapterAndPage() {
        assertEquals(0, calculateBookProgressPercent(0, 0, 0, 1))
        assertEquals(2, calculateBookProgressPercent(0, 10, 0, 5))
        assertEquals(45, calculateBookProgressPercent(4, 10, 1, 4))
        assertEquals(100, calculateBookProgressPercent(9, 10, 3, 4))
    }
}
