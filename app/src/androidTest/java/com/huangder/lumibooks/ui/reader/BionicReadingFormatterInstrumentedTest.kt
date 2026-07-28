package com.huangder.lumibooks.ui.reader

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BionicReadingFormatterInstrumentedTest {
    @Test
    fun chineseFixationsFollowWordBoundaries() {
        val text = "他沿着水缸连走几圈"
        val chunks = BionicReadingFormatter.fixationRanges(text).map { range ->
            text.substring(range.first, range.last + 1)
        }

        assertEquals(listOf("他沿着", "走几圈"), chunks)
    }
}
