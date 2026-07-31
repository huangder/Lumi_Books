package com.huangder.lumibooks.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChineseConverterTest {
    @Test
    fun exportedMappingsMatchConverterDirections() {
        val simplified = ChineseConverter.mappingStrings("simplified")!!
        val traditional = ChineseConverter.mappingStrings("traditional")!!

        assertEquals(
            ChineseConverter.convert(simplified.first, "simplified"),
            simplified.second
        )
        assertEquals(
            ChineseConverter.convert(traditional.first, "traditional"),
            traditional.second
        )
        assertNull(ChineseConverter.mappingStrings("original"))
    }
}
