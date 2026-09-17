package com.huangder.lumibooks.ui.reader.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderSpanMetricsTest {
    @Test
    fun keepsDisplayDensityForDipSpans() {
        assertEquals(3.25f, readerSpanPaintDensity(3.25f), 0.0001f)
        assertEquals(1f, readerSpanPaintDensity(1f), 0.0001f)
        assertEquals(2.75f, readerSpanPaintDensity(2.75f), 0.0001f)
    }

    @Test
    fun fallsBackToTextPaintDefaultForInvalidDensity() {
        assertEquals(1f, readerSpanPaintDensity(0f), 0.0001f)
        assertEquals(1f, readerSpanPaintDensity(-3f), 0.0001f)
        assertEquals(1f, readerSpanPaintDensity(Float.NaN), 0.0001f)
        assertEquals(1f, readerSpanPaintDensity(Float.POSITIVE_INFINITY), 0.0001f)
    }
}
