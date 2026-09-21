package com.huangder.lumibooks.ui.reader

import android.graphics.Bitmap
import android.os.SystemClock
import android.os.Trace
import com.huangder.lumibooks.BuildConfig
import com.huangder.lumibooks.domain.model.PageRenderMode
import com.huangder.lumibooks.util.cache.WeightedLruCache
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

internal class RasterRenderSession(
    private val decoder: RasterPageDecoder,
    private val disk: RasterResumeCache? = null,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) : BitmapPageSource {
    override val pageCount = decoder.pageCount
    private val scheduler = RasterWorkScheduler(scope, decoder.parallelism, ::log)
    private val dimensions = ConcurrentHashMap<Int, RasterDimensions>()
    private val pageCache = bitmapCache(PAGE_BYTES)
    private val previewCache = bitmapCache(PREVIEW_BYTES)
    private val thumbnailCache = bitmapCache(THUMBNAIL_BYTES)
    private val caches = listOf(pageCache, previewCache, thumbnailCache)
    private val diskAttempted = ConcurrentHashMap.newKeySet<Int>()
    private val motion = MutableStateFlow(true)
    override val moving: StateFlow<Boolean> = motion
    private val visible = MutableStateFlow<Set<Int>>(emptySet())
    override val visiblePages: StateFlow<Set<Int>> = visible
    @Volatile private var viewport = RasterViewport()
    @Volatile private var closed = false
    @Volatile private var paused = false
    private var direction = 1
    private var settleJob: Job? = null
    private var prefetchJob: Job? = null
    private var persistJob: Job? = null
    private var prefetchPages = emptyList<Int>()
    private val diskWriter = Mutex()
    private val metrics = RasterLoadMetrics()

    override fun pageDrawn(pageIndex: Int, finalQuality: Boolean) = metrics.drawn(pageIndex, finalQuality)

    override suspend fun tiledPage(pageIndex: Int): RasterTileSource? {
        restore(pageIndex, priority = 0, background = false)
        val size = geometry(pageIndex, priority = 0, background = false) ?: return null
        return decoder.tileSource(pageIndex, size)
    }

    override fun cachedPage(pageIndex: Int): Bitmap? = caches.asSequence().flatMap { it.entries.asSequence() }
        .filter { it.key.page == pageIndex }.maxByOrNull { it.value.width }?.value

    override fun cachedReadablePage(pageIndex: Int): Bitmap? = pageCache.entries.asSequence()
        .filter { it.key.page == pageIndex }
        .maxByOrNull { it.value.width }
        ?.value

    override fun cachedAspectRatio(pageIndex: Int): Float? = dimensions[pageIndex]?.ratio
        ?: cachedPage(pageIndex)?.let { it.width.toFloat() / it.height }

    private fun cached(spec: RasterDecodeSpec): Bitmap? {
        caches.forEach { cache -> cache[spec]?.let { return it } }
        for (cache in caches) {
            val key = cache.entries.filter { it.key.page == spec.page && it.value.width >= spec.width && it.value.height >= spec.height }
                .minByOrNull { it.value.allocationByteCount }?.key
            if (key != null) return cache[key]
        }
        return null
    }

    private suspend fun geometry(page: Int, priority: Int, background: Boolean): RasterDimensions? {
        if (closed || paused || page !in 0 until pageCount) return null
        dimensions[page]?.let { return it }
        return scheduler.execute("geometry:$page", priority, background) {
            dimensions[page] ?: runCatching { decoder.dimensions(page) }.getOrNull()?.also { dimensions[page] = it }
        }
    }

    override suspend fun pageAspectRatio(pageIndex: Int, background: Boolean): Float? {
        if (background) motion.first { !it }
        return geometry(pageIndex, if (background) 5 else 0, background)?.ratio
    }

    private suspend fun restore(page: Int, priority: Int, background: Boolean) {
        if (disk == null || page in diskAttempted) return
        scheduler.execute("disk:$page", priority, background) {
            if (diskAttempted.add(page)) {
                disk.read(page)?.let { image ->
                    put(pageCache, PAGE_BYTES, RasterDecodeSpec(page, image.width, image.height), image)
                    log("disk_hit page=$page")
                }
            }
        }
    }

    override suspend fun renderPreview(pageIndex: Int, targetWidthPx: Int): Bitmap? {
        cachedPage(pageIndex)?.let { return it }
        return render(pageIndex, targetWidthPx, PageRenderMode.NORMAL, preview = true, priority = 0)
    }

    override suspend fun renderPage(pageIndex: Int, targetWidthPx: Int, mode: PageRenderMode): Bitmap? {
        return render(pageIndex, targetWidthPx, mode, preview = false, priority = 1)
    }

    override suspend fun renderThumbnail(pageIndex: Int, targetWidthPx: Int): Bitmap? =
        render(pageIndex, targetWidthPx, PageRenderMode.NORMAL, preview = true, priority = 0, thumbnail = true)

    private suspend fun render(
        page: Int, width: Int, mode: PageRenderMode, preview: Boolean, priority: Int,
        background: Boolean = false, thumbnail: Boolean = false
    ): Bitmap? {
        if (closed || paused || page !in 0 until pageCount) return null
        restore(page, priority, background)
        if (preview && !thumbnail) cachedPage(page)?.let { return it }
        val size = geometry(page, priority, background) ?: return null
        val spec = decoder.spec(page, size, width, mode, preview)
        cached(spec)?.let { log("memory_hit page=$page"); return it }
        val queuedAt = SystemClock.elapsedRealtime()
        return scheduler.execute(spec, priority, background, large = !preview && (mode != PageRenderMode.NORMAL || width > viewport.width)) {
            cached(spec) ?: run {
                val started = SystemClock.elapsedRealtime()
                Trace.beginSection("Raster.decode.$page")
                val bitmap = try { decoder.decode(spec) } catch (error: Throwable) {
                    if (error is CancellationException) throw error
                    android.util.Log.w("RasterReader", "Decode failed: $page", error)
                    null
                } finally { Trace.endSection() }
                bitmap?.also {
                    val cache = when { thumbnail -> thumbnailCache; preview -> previewCache; else -> pageCache }
                    val budget = when { thumbnail -> THUMBNAIL_BYTES; preview -> PREVIEW_BYTES; else -> PAGE_BYTES }
                    put(cache, budget, spec, it)
                }
                log("decode page=$page preview=$preview queueMs=${started - queuedAt} decodeMs=${SystemClock.elapsedRealtime() - started} bytes=${bitmap?.allocationByteCount ?: 0}")
                bitmap
            }
        }
    }

    @Synchronized override fun updateViewport(viewport: RasterViewport) {
        if (closed || this.viewport == viewport) return
        if (viewport.anchor != this.viewport.anchor) direction = if (viewport.anchor > this.viewport.anchor) 1 else -1
        this.viewport = viewport
        visible.value = viewport.visiblePages
        metrics.viewport(viewport.visiblePages)
        if (!paused) scheduleViewport(viewport)
    }

    private fun scheduleViewport(viewport: RasterViewport) {
        motion.value = true
        settleJob?.cancel()
        persistJob?.cancel()
        val neighbors = rasterPrefetchWindow(viewport, direction, pageCount)
        prefetchPages = neighbors
        prefetchJob?.cancel()
        prefetchJob = scope.launch {
            coroutineScope {
                viewport.visiblePages.forEach { page ->
                    launch {
                        restore(page, priority = 0, background = false)
                        val size = geometry(page, priority = 0, background = false) ?: return@launch
                        // PDF renders immediately. Tiled CBZ pages get a brief SSIV head start in
                        // PdfPageItem, which still starts NORMAL decoding on timeout or tile error.
                        if (decoder.tileSource(page, size) == null) {
                            render(page, viewport.width, PageRenderMode.NORMAL, false, 0)
                        }
                    }
                }
                neighbors.forEach { page ->
                    launch {
                        val size = geometry(page, priority = 3, background = true) ?: return@launch
                        if (decoder.tileSource(page, size) == null) {
                            render(page, viewport.width, PageRenderMode.NORMAL, false, 3, background = true)
                        }
                    }
                }
            }
        }
        settleJob = scope.launch {
            delay(RASTER_SETTLE_MS)
            synchronized(this@RasterRenderSession) {
                if (closed || paused || viewport != this@RasterRenderSession.viewport || viewport.scrolling) return@launch
                motion.value = false
                persistJob = scope.launch {
                    delay(RASTER_PERSIST_MS - RASTER_SETTLE_MS)
                    persistWindow(viewport, decodeMissing = true)
                }
            }
        }
    }

    private suspend fun persistWindow(snapshot: RasterViewport, decodeMissing: Boolean) {
        val cache = disk ?: return
        if (!cache.enabled || snapshot.visiblePages.isEmpty()) return
        val pages = rasterResumeWindow(snapshot.anchor, pageCount, snapshot.spread)
        diskWriter.withLock {
            val revision = cache.setWindow(pages)
            for (page in pages) {
                currentCoroutineContext().ensureActive()
                if (snapshot != viewport) return
                val size = dimensions[page] ?: if (decodeMissing) geometry(page, 4, true) else null
                if (size == null) continue
                val spec = decoder.spec(page, size, snapshot.width, PageRenderMode.NORMAL, false)
                val image = cached(spec) ?: if (decodeMissing && !closed)
                    render(page, snapshot.width, PageRenderMode.NORMAL, false, 4, background = true) else null
                if (image != null) cache.write(page, image, spec, revision)
            }
        }
    }

    @Synchronized override fun saveResumeSnapshot() {
        if (closed) return
        paused = true
        motion.value = true
        settleJob?.cancel()
        prefetchJob?.cancel()
        persistJob?.cancel()
        prefetchPages = emptyList()
        val snapshot = viewport
        scope.launch { persistWindow(snapshot, decodeMissing = false) }
    }

    @Synchronized override fun resumeLoading() {
        if (closed || !paused) return
        paused = false
        scheduleViewport(viewport)
    }

    @Synchronized override fun close() {
        if (closed) return
        closed = true
        motion.value = false
        settleJob?.cancel()
        prefetchJob?.cancel()
        persistJob?.cancel()
        scheduler.close()
        scope.launch {
            try {
                scheduler.awaitClosed()
                persistWindow(viewport, decodeMissing = false)
            } finally {
                decoder.close()
                disk?.close()
                caches.forEach { it.clear() }
                scope.cancel()
            }
        }
    }

    private fun put(cache: WeightedLruCache<RasterDecodeSpec, Bitmap>, budget: Long, spec: RasterDecodeSpec, bitmap: Bitmap) {
        // The shared LRU intentionally retains one oversized item; raster pages must opt out.
        if (bitmap.allocationByteCount.toLong() <= budget) cache.put(spec, bitmap)
    }

    private companion object {
        const val PAGE_BYTES = 64L * 1024 * 1024
        const val PREVIEW_BYTES = 4L * 1024 * 1024
        const val THUMBNAIL_BYTES = 8L * 1024 * 1024
        fun bitmapCache(budget: Long) = WeightedLruCache<RasterDecodeSpec, Bitmap>(budget) { it.allocationByteCount.toLong() }
        fun log(message: String) { if (BuildConfig.DEBUG) android.util.Log.d("RasterLoad", message) }
    }
}
