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

    @Test
    fun importedMappingsCoverPreviouslyMissingCharacters() {
        assertEquals(
            "\u919C\u55AC\u7522\u50D5",
            ChineseConverter.toTraditional("\u4E11\u4E54\u4EA7\u4EC6")
        )
        assertEquals(
            "\u4E11\u4E54\u4EA7\u4EC6",
            ChineseConverter.toSimplified("\u919C\u55AC\u7522\u50D5")
        )
    }
}
