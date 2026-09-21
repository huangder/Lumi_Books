package com.huangder.lumibooks.ui.reader

import android.graphics.Bitmap
import android.net.Uri
import com.huangder.lumibooks.util.cache.BookFingerprint
import com.huangder.lumibooks.util.cache.ReaderCacheStore
import com.huangder.lumibooks.domain.model.PageRenderMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@OptIn(ExperimentalCoroutinesApi::class)
class RasterRenderSessionTest {
    private class Decoder(private val tiled: Boolean = false) : RasterPageDecoder {
        override val pageCount = 1
        override val parallelism = 2
        var boundsReads = 0
        var decodes = 0
        var closed = false
        override fun dimensions(page: Int): RasterDimensions { boundsReads++; return RasterDimensions(200, 300) }
        override fun spec(page: Int, dimensions: RasterDimensions, width: Int, mode: PageRenderMode, preview: Boolean) =
            RasterDecodeSpec(page, if (preview) 20 else 200, if (preview) 30 else 300)
        override fun decode(spec: RasterDecodeSpec): Bitmap {
            check(!closed)
            decodes++
            return Bitmap.createBitmap(spec.width, spec.height, Bitmap.Config.ARGB_8888)
        }
        override fun tileSource(page: Int, dimensions: RasterDimensions): RasterTileSource? =
            if (tiled) RasterTileSource(Uri.EMPTY, dimensions) else null
        override fun close() { closed = true }
    }

    @Test fun `tiled visible page does not compete with SSIV until bitmap fallback is requested`() = runTest {
        val decoder = Decoder(tiled = true)
        val session = RasterRenderSession(decoder, scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler)))
        session.updateViewport(RasterViewport(setOf(0), width = 200, scrolling = true))
        runCurrent()
        assertEquals(0, decoder.decodes)
        assertNotNull(session.renderPage(0, 200))
        assertEquals(1, decoder.decodes)
        session.close()
        runCurrent()
    }

    @Test fun `visible page renders at readable quality while viewport is moving`() = runTest {
        val decoder = Decoder()
        val session = RasterRenderSession(decoder, scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler)))
        val viewport = RasterViewport(setOf(0), width = 200, scrolling = true)
        session.updateViewport(viewport)
        val full = async { session.renderPage(0, 200, PageRenderMode.HIGH) }
        runCurrent()
        assertTrue(full.isCompleted)
        assertEquals(200, full.await()!!.width)
        session.updateViewport(viewport.copy(positions = listOf(30f)))
        runCurrent()
        assertEquals(200, session.cachedReadablePage(0)!!.width)
        assertEquals(1, decoder.boundsReads)
        session.close()
        runCurrent()
        assertTrue(decoder.closed)
    }

    @Test fun `equivalent quality specs share decode and reuse full image for preview`() = runTest {
        val decoder = Decoder()
        val session = RasterRenderSession(decoder, scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler)))
        session.updateViewport(RasterViewport(setOf(0), width = 200))
        advanceTimeBy(160)
        runCurrent()
        val normal = async { session.renderPage(0, 200) }
        val native = async { session.renderPage(0, 200, PageRenderMode.NATIVE) }
        runCurrent()
        assertSame(normal.await(), native.await())
        assertSame(normal.await(), session.renderPreview(0, 20))
        assertEquals(1, decoder.decodes)
        assertEquals(1, decoder.boundsReads)
        session.close()
        runCurrent()
    }

    @Test fun `idle snapshot restores without native decode and close does not decode missing pages`() = runTest {
        val context = RuntimeEnvironment.getApplication().applicationContext
        ReaderCacheStore.get(context).clear()
        val fingerprint = BookFingerprint("session-roundtrip", 500, 100, true)
        val decoder = Decoder()
        val session = RasterRenderSession(decoder, RasterResumeCache(context, fingerprint),
            CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler)))
        session.updateViewport(RasterViewport(setOf(0), width = 200))
        advanceTimeBy(800)
        runCurrent()
        assertEquals(1, decoder.decodes)
        session.close()
        runCurrent()
        assertEquals(1, decoder.decodes)

        val restoredDecoder = Decoder()
        val restored = RasterRenderSession(restoredDecoder, RasterResumeCache(context, fingerprint),
            CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler)))
        restored.updateViewport(RasterViewport(setOf(0), width = 200))
        assertEquals(200, restored.renderPreview(0, 20)!!.width)
        advanceTimeBy(160)
        runCurrent()
        assertEquals(200, restored.renderPage(0, 200)!!.width)
        assertEquals(0, restoredDecoder.decodes)
        restored.close()
        runCurrent()
    }

    @Test fun `backgrounding keeps completed visible page and resume does not repeat decode`() = runTest {
        val decoder = Decoder()
        val session = RasterRenderSession(decoder, scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler)))
        session.updateViewport(RasterViewport(setOf(0), width = 200))
        runCurrent()
        assertEquals(1, decoder.decodes)
        session.saveResumeSnapshot()
        advanceTimeBy(2000)
        runCurrent()
        assertEquals(1, decoder.decodes)
        session.resumeLoading()
        runCurrent()
        assertEquals(1, decoder.decodes)
        session.close()
        runCurrent()
    }
}
