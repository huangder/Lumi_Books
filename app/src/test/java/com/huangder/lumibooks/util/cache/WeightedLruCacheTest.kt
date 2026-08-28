package com.huangder.lumibooks.util.cache

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WeightedLruCacheTest {
    @Test
    fun evictsLeastRecentlyUsedValuesByWeight() {
        val cache = WeightedLruCache<String, String>(10) { it.length.toLong() }
        cache.put("a", "12345")
        cache.put("b", "67890")
        assertEquals("12345", cache["a"])

        cache.put("c", "abcd")

        assertNull(cache["b"])
        assertEquals("12345", cache["a"])
        assertEquals("abcd", cache["c"])
    }

    @Test
    fun concurrentReadsAndWritesRemainConsistent() {
        val cache = WeightedLruCache<Int, String>(256) { it.length.toLong() }
        val start = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(4)
        val tasks = (0 until 4).map { worker ->
            pool.submit {
                start.await()
                repeat(500) { index ->
                    val key = worker * 500 + index
                    cache.put(key, "value-$key")
                    cache[key]
                }
            }
        }
        start.countDown()
        tasks.forEach { it.get() }
        pool.shutdown()

        assertTrue(cache.size > 0)
    }
}
