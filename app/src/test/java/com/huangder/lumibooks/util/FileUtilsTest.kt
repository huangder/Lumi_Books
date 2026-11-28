package com.huangder.lumibooks.util

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class FileUtilsTest {
    @Test
    fun formatsFileSizeWithTwoDecimalPlaces() {
        assertEquals("512.00 B", FileUtils.formatFileSize(512, decimalPlaces = 2, locale = Locale.US))
        assertEquals("1.38 KB", FileUtils.formatFileSize(1408, decimalPlaces = 2, locale = Locale.US))
        assertEquals("1.00 MB", FileUtils.formatFileSize(1048576, decimalPlaces = 2, locale = Locale.US))
        assertEquals("1,50 GB", FileUtils.formatFileSize(1610612736, decimalPlaces = 2, locale = Locale.GERMANY))
    }

    @Test
    fun usesWholeUnitsByDefault() {
        assertEquals("1 KB", FileUtils.formatFileSize(1536))
    }
}
