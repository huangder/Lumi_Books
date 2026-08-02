package com.huangder.lumibooks.util

import com.huangder.lumibooks.util.epub.MobiFile
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class MobiFileHeaderTest {

    @Test
    fun `parses palmdoc mobi and exth headers`() {
        val record0 = MobiTestFixtures.buildRecord0()
        val header = MobiFile.parseFirstRecord(record0, recordCount = 5)
        assertEquals(1, header.compression)
        assertEquals(65001, header.textEncoding)
        assertEquals("Raw Title", header.fullName)
        assertEquals("Test Author", header.author)
        assertEquals("MOBI Book", header.titleOverride)
        assertEquals(0, header.coverOffset)
        assertEquals(3, header.firstImageIndex)
        assertEquals(1, header.firstTextRecord)
        assertEquals(2, header.lastTextRecord)
    }

    @Test
    fun `rejects encrypted books`() {
        val record0 = MobiTestFixtures.buildRecord0()
        record0[12] = 1
        try {
            MobiFile.parseFirstRecord(record0)
            fail("expected encryption rejection")
        } catch (expected: IllegalArgumentException) {
            assertEquals(MobiFile.DRM_MESSAGE, expected.message)
        }
    }

    @Test
    fun `rejects missing mobi magic`() {
        val record0 = MobiTestFixtures.buildRecord0()
        record0[16] = 'X'.code.toByte()
        try {
            MobiFile.parseFirstRecord(record0)
            fail("expected magic rejection")
        } catch (expected: IllegalArgumentException) {
            // expected
        }
    }

    @Test
    fun `falls back to header content range when palmdoc count missing`() {
        val record0 = MobiTestFixtures.buildRecord0()
        // zero the PalmDOC text record count -> fallback uses 0xA4/0xA8
        record0[8] = 0
        record0[9] = 0
        val header = MobiFile.parseFirstRecord(record0, recordCount = 10)
        assertEquals(1, header.firstTextRecord)
        assertEquals(2, header.lastTextRecord)
    }
}
