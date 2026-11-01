package com.huangder.lumibooks.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KindleBookFormatTest {
    @Test
    fun kindleFormatsSurvivePersistenceAndShareReaderCapabilities() {
        for (format in listOf(BookFormat.MOBI, BookFormat.AZW, BookFormat.AZW3)) {
            assertEquals(format, BookFormat.valueOf(format.name))
            assertEquals(format.name, com.huangder.lumibooks.data.local.entity.BookFormat.fromString(format.name.lowercase()).name)
            assertTrue(format.isKindle)
            assertTrue(format.supportsEpubFeatures)
            assertTrue(format.supportsBookLayout)
            assertTrue(format.supportsTextReading)
            assertTrue(format.supportsAnnotations)
            assertFalse(format.isRasterPageFormat)
        }
        assertFalse(BookFormat.EPUB.isKindle)
        assertFalse(BookFormat.PDF.supportsTextReading)
        assertFalse(BookFormat.CBZ.supportsAnnotations)
    }
}
