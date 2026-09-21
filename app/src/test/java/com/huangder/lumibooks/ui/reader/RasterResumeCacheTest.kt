package com.huangder.lumibooks.ui.reader

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import com.huangder.lumibooks.util.cache.BookFingerprint
import com.huangder.lumibooks.util.cache.ReaderCacheStore
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RasterResumeCacheTest {
    private val context: Context get() = RuntimeEnvironment.getApplication().applicationContext
    private val fingerprint = BookFingerprint("raster-test", 500, 100, true)
    private val image: Bitmap get() = Bitmap.createBitmap(64, 96, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
    private fun spec(page: Int) = RasterDecodeSpec(page, 64, 96)
    private fun directory(fp: BookFingerprint = fingerprint) = File(context.cacheDir, "reader_cache/raster_${fp.key}")

    @Before fun clear() { ReaderCacheStore.get(context).clear() }

    @Test fun `normal image survives reopening and matches its pixels`() {
        val first = RasterResumeCache(context, fingerprint)
        val revision = first.setWindow(listOf(4, 5, 3))
        first.write(4, image, spec(4), revision)
        first.close()
        val second = RasterResumeCache(context, fingerprint)
        val restored = second.read(4)
        assertNotNull(restored)
        assertEquals(64, restored!!.width)
        assertEquals(Color.RED, restored.getPixel(30, 30))
    }

    @Test fun `moving window drops old pages and stale writes`() {
        val cache = RasterResumeCache(context, fingerprint)
        val old = cache.setWindow(listOf(4, 5))
        cache.write(4, image, spec(4), old)
        cache.setWindow(listOf(20, 21))
        cache.write(5, image, spec(5), old)
        assertNull(cache.read(4))
        assertNull(cache.read(5))
        assertFalse(File(directory(), "4.png").exists())
    }

    @Test fun `clear rejects an in flight writer and a fresh session can recover`() {
        val cache = RasterResumeCache(context, fingerprint)
        val revision = cache.setWindow(listOf(4))
        ReaderCacheStore.get(context).clear()
        cache.write(4, image, spec(4), revision)
        assertFalse(directory().exists())
        val fresh = RasterResumeCache(context, fingerprint)
        fresh.write(4, image, spec(4), fresh.setWindow(listOf(4)))
        assertNotNull(fresh.read(4))
    }

    @Test fun `new session prevents older session from overwriting resume position`() {
        val old = RasterResumeCache(context, fingerprint)
        val revision = old.setWindow(listOf(1))
        val fresh = RasterResumeCache(context, fingerprint)
        fresh.write(9, image, spec(9), fresh.setWindow(listOf(9)))
        old.write(1, image, spec(1), revision)
        old.setWindow(listOf(1))
        assertNull(fresh.read(1))
        assertNotNull(fresh.read(9))
    }

    @Test fun `source revision and unreliable sources are never reused`() {
        val cache = RasterResumeCache(context, fingerprint)
        cache.write(0, image, spec(0), cache.setWindow(listOf(0)))
        assertNull(RasterResumeCache(context, fingerprint.copy(lastModified = 101)).read(0))
        val unreliable = RasterResumeCache(context, fingerprint.copy(reliable = false))
        unreliable.write(0, image, spec(0), unreliable.setWindow(listOf(0)))
        assertNull(unreliable.read(0))
    }

    @Test fun `corrupt PNG is ignored and repaired by a later write`() {
        val cache = RasterResumeCache(context, fingerprint)
        val revision = cache.setWindow(listOf(0))
        cache.write(0, image, spec(0), revision)
        val png = File(directory(), "0.png")
        png.writeBytes(ByteArray(png.length().toInt()))
        assertNull(cache.read(0))
        cache.write(0, image, spec(0), revision)
        assertNotNull(cache.read(0))
    }

    @Test fun `partial files never count as valid pages and previews cannot be persisted as normal`() {
        val cache = RasterResumeCache(context, fingerprint)
        val revision = cache.setWindow(listOf(0))
        File(directory(), "0.interrupted.tmp").writeText("partial")
        assertNull(cache.read(0))
        cache.write(0, Bitmap.createBitmap(16, 24, Bitmap.Config.ARGB_8888), spec(0), revision)
        assertNull(cache.read(0))
    }

    @Test fun `oversized pages and old books respect disk budgets`() {
        val tiny = RasterResumeCache(context, fingerprint, maxBookBytes = 1)
        tiny.write(0, image, spec(0), tiny.setWindow(listOf(0)))
        assertNull(tiny.read(0))
        val first = RasterResumeCache(context, fingerprint)
        first.write(0, image, spec(0), first.setWindow(listOf(0)))
        val otherFingerprint = fingerprint.copy(identity = "second-book")
        val second = RasterResumeCache(context, otherFingerprint, maxBooks = 1)
        second.write(0, image, spec(0), second.setWindow(listOf(0)))
        assertFalse(directory().exists())
        assertNotNull(second.read(0))
    }
}
