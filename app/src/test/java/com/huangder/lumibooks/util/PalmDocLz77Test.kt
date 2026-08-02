package com.huangder.lumibooks.util

import com.huangder.lumibooks.util.epub.PalmDocLz77
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class PalmDocLz77Test {

    @Test
    fun `literal run copies next bytes`() {
        val compressed = byteArrayOf(
            0x05,
            'H'.code.toByte(), 'e'.code.toByte(), 'l'.code.toByte(), 'l'.code.toByte(), 'o'.code.toByte()
        )
        assertEquals("Hello", String(PalmDocLz77.decompress(compressed), Charsets.UTF_8))
    }

    @Test
    fun `single chars 0x09 to 0x7f are emitted literally`() {
        assertEquals("ab", String(PalmDocLz77.decompress(byteArrayOf(0x61, 0x62)), Charsets.UTF_8))
    }

    @Test
    fun `zero byte control emits zero`() {
        assertArrayEquals(byteArrayOf(0), PalmDocLz77.decompress(byteArrayOf(0)))
    }

    @Test
    fun `0xc0 to 0xff emits space plus xor byte`() {
        // 0xC1 -> ' ' + (0xC1 xor 0x80) = ' ' + 'A'
        assertEquals(" A", String(PalmDocLz77.decompress(byteArrayOf(0xC1.toByte())), Charsets.UTF_8))
    }

    @Test
    fun `back reference copies from the output window`() {
        // literals 'a','b','c' then distance=3 length=6: pair = (3 << 3) | 3 = 0x1B
        val compressed = byteArrayOf(
            'a'.code.toByte(), 'b'.code.toByte(), 'c'.code.toByte(),
            0x80.toByte(), 0x1B
        )
        assertEquals("abcabcabc", String(PalmDocLz77.decompress(compressed), Charsets.UTF_8))
    }

    @Test
    fun `overlapping back reference with distance one`() {
        // 'A' then distance=1 length=5: pair = (1 << 3) | 2 = 0x0A
        val compressed = byteArrayOf('A'.code.toByte(), 0x80.toByte(), 0x0A)
        assertEquals("AAAAAA", String(PalmDocLz77.decompress(compressed), Charsets.UTF_8))
    }

    @Test
    fun `partial literal run at end does not crash`() {
        assertEquals("ab", String(PalmDocLz77.decompress(byteArrayOf(0x08, 'a'.code.toByte(), 'b'.code.toByte())), Charsets.UTF_8))
    }
}
