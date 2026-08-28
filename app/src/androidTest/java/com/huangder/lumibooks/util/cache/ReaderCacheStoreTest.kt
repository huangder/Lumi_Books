package com.huangder.lumibooks.util.cache

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import java.io.RandomAccessFile
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReaderCacheStoreTest {
    private lateinit var context: Context
    private lateinit var store: ReaderCacheStore
    private lateinit var root: File

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        store = ReaderCacheStore.get(context)
        store.clear()
        root = File(context.cacheDir, "reader_cache")
    }

    @After
    fun tearDown() = store.clear()

    @Test
    fun localFingerprintChangesAfterSourceModification() {
        val source = File(context.cacheDir, "fingerprint.txt").apply { writeText("one") }
        val first = BookFingerprint.resolve(context, source.absolutePath)
        source.appendText("-two")
        source.setLastModified(first.lastModified + 2_000L)
        val second = BookFingerprint.resolve(context, source.absolutePath)

        assertTrue(first.reliable)
        assertNotEquals(first.key, second.key)
        source.delete()
    }

    @Test
    fun corruptedMetadataIsDiscarded() {
        val source = File(context.cacheDir, "metadata.txt").apply { writeText("content") }
        val fingerprint = BookFingerprint.resolve(context, source.absolutePath)
        store.writeMetadata("test", fingerprint, JSONObject().put("ok", true))
        val metadata = store.metadataFile("test", fingerprint)
        metadata.writeText("not-json")

        assertNull(store.readMetadata("test", fingerprint))
        assertFalse(metadata.exists())
        source.delete()
    }

    @Test
    fun mirrorLimitKeepsAtMostThreeBooksAndNinetySixMegabytes() {
        repeat(4) { index ->
            val base = "mirror_test_$index"
            RandomAccessFile(File(root, "$base.book"), "rw").use {
                it.setLength(30L * 1024L * 1024L)
            }
            File(root, "$base.json").writeText(
                JSONObject().put("accessedAt", index.toLong()).toString()
            )
        }

        store.enforceLimitsForTesting()

        val books = root.listFiles { file -> file.extension == "book" }.orEmpty()
        assertEquals(3, books.size)
        assertTrue(books.sumOf(File::length) <= ReaderCacheStore.MAX_BYTES)
    }
}
