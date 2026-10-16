package com.huangder.lumibooks.util.epub

import org.junit.Assert.*
import org.junit.Test

class EpubDocumentCacheTest {
    private fun document(size: Int) = EpubResource("chapter.xhtml", "application/xhtml+xml", ByteArray(size))

    @Test fun evictsByBytesAndKeepsRecentlyReadDocument() {
        val cache = EpubDocumentCache(10)
        val a = document(4)
        cache.put("a", a)
        cache.put("b", document(4))
        assertSame(a, cache["a"])
        cache.put("c", document(4))
        assertNull(cache["b"])
        assertSame(a, cache["a"])
        assertEquals(8, cache.sizeBytes)
    }

    @Test fun oversizedDocumentDoesNotEvictUsefulEntriesAndCloseClearsBytes() {
        val cache = EpubDocumentCache(10)
        val small = document(4)
        cache.put("a", small)
        val large = document(11)
        assertSame(large, cache.put("large", large))
        assertNull(cache["large"])
        assertSame(small, cache["a"])
        cache.put("a", document(6))
        assertEquals(6, cache.sizeBytes)
        cache.clear()
        assertEquals(0, cache.sizeBytes)
        assertNull(cache["a"])
    }
}
