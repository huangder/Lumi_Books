package com.huangder.lumibooks.ui.reader

import com.huangder.lumibooks.domain.model.PdfPageMode
import org.junit.Assert.assertEquals
import org.junit.Test

class PdfPageModeTest {
    @Test
    fun `normalizes supported and unknown keys`() {
        assertEquals(PdfPageMode.VERTICAL_SCROLL, PdfPageMode.fromKey("vertical"))
        assertEquals(PdfPageMode.VERTICAL_PAGING, PdfPageMode.fromKey("vertical_paging"))
        assertEquals(PdfPageMode.HORIZONTAL_PAGING, PdfPageMode.fromKey("horizontal"))
        assertEquals(PdfPageMode.VERTICAL_SCROLL, PdfPageMode.fromKey("unknown"))
        assertEquals("vertical", PdfPageMode.normalizeKey(null))
    }

    @Test
    fun `cycles continuous vertical paging and horizontal paging`() {
        assertEquals(PdfPageMode.VERTICAL_PAGING, PdfPageMode.VERTICAL_SCROLL.next())
        assertEquals(PdfPageMode.HORIZONTAL_PAGING, PdfPageMode.VERTICAL_PAGING.next())
        assertEquals(PdfPageMode.VERTICAL_SCROLL, PdfPageMode.HORIZONTAL_PAGING.next())
    }
}
