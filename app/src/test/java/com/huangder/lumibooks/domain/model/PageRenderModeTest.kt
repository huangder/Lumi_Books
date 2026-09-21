package com.huangder.lumibooks.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class PageRenderModeTest {
    @Test
    fun `keys round trip`() {
        PageRenderMode.entries.forEach { mode ->
            assertEquals(mode, PageRenderMode.fromKey(mode.key))
            assertEquals(mode.key, PageRenderMode.normalizeKey(mode.key))
        }
    }

    @Test
    fun `unknown or empty keys fall back to normal`() {
        assertEquals(PageRenderMode.NORMAL, PageRenderMode.fromKey(null))
        assertEquals(PageRenderMode.NORMAL, PageRenderMode.fromKey(""))
        assertEquals(PageRenderMode.NORMAL, PageRenderMode.fromKey("ultra"))
        assertEquals(PageRenderMode.NORMAL.key, PageRenderMode.normalizeKey("ultra"))
    }

    @Test
    fun `next cycles through all three modes`() {
        assertEquals(PageRenderMode.HIGH, PageRenderMode.NORMAL.next())
        assertEquals(PageRenderMode.NATIVE, PageRenderMode.HIGH.next())
        assertEquals(PageRenderMode.NORMAL, PageRenderMode.NATIVE.next())
    }
}
