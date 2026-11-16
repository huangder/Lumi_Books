package com.huangder.lumibooks.ui.animation

import org.junit.Assert.*
import org.junit.Test

class BookReaderCornerRadiusTest {
    private val fit = BookReaderMotion.SCREEN_CORNER_FIT
    private val samples = (0..100).map { it / 100f }

    @Test fun `full screen window ends just inside the display corner`() {
        assertEquals(10f, BookReaderMotion.cornerRadius(10f, 40f, 0f), 0f)
        assertEquals(40f * fit, BookReaderMotion.cornerRadius(10f, 40f, 1f), 0.0001f)
        assertTrue(BookReaderMotion.cornerRadius(10f, 40f, 1f) < 40f)
    }

    @Test fun `radius changes monotonically towards the display corner`() {
        for ((start, screen) in listOf(10f to 40f, 60f to 30f)) {
            val values = samples.map { BookReaderMotion.cornerRadius(start, screen, it) }
            values.zipWithNext().forEach { (a, b) ->
                if (screen * fit > start) assertTrue("$a -> $b", b + 0.0001f >= a)
                else assertTrue("$a -> $b", b <= a + 0.0001f)
            }
        }
    }

    @Test fun `square displays keep the previous curve`() {
        samples.forEach {
            assertEquals(BookReaderMotion.cornerRadius(12f, it), BookReaderMotion.cornerRadius(12f, 0f, it), 0f)
        }
    }
}
