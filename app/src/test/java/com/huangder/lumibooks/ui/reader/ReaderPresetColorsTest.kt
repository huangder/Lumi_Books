package com.huangder.lumibooks.ui.reader

import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderPresetColorsTest {
    @Test
    fun builtInPreviewColorsMatchBothReaderModes() {
        assertEquals(0xFFE8F5E9.toInt(), readerPresetBackgroundColor("green", false))
        assertEquals(0xFF142A1A.toInt(), readerPresetBackgroundColor("green", true))
        assertEquals(0xFF2E7D32.toInt(), readerPresetTextColor("green", false))
        assertEquals(0xFFC8E6C9.toInt(), readerPresetTextColor("green", true))

        assertEquals(0xFFF5E6D3.toInt(), readerPresetBackgroundColor("sepia", false))
        assertEquals(0xFF2B2118.toInt(), readerPresetBackgroundColor("sepia", true))
        assertEquals(0xFF4A3728.toInt(), readerPresetTextColor("sepia", false))
        assertEquals(0xFFE8D5BC.toInt(), readerPresetTextColor("sepia", true))

        assertEquals(0xFFFBFBFC.toInt(), readerPresetBackgroundColor("day", false))
        assertEquals(0xFF1A1A1A.toInt(), readerPresetBackgroundColor("day", true))
        assertEquals(0xFF333333.toInt(), readerPresetTextColor("day", false))
        assertEquals(0xFFCCCCCC.toInt(), readerPresetTextColor("day", true))

        assertEquals(0xFF1A1A1A.toInt(), readerPresetBackgroundColor("night", false))
        assertEquals(0xFF1A1A1A.toInt(), readerPresetBackgroundColor("night", true))
        assertEquals(0xFFCCCCCC.toInt(), readerPresetTextColor("night", false))
        assertEquals(0xFFCCCCCC.toInt(), readerPresetTextColor("night", true))
    }
}
