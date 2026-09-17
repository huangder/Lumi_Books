package com.huangder.lumibooks.ui.reader

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.huangder.lumibooks.domain.model.BookFormat
import com.huangder.lumibooks.util.BookFileAccess
import com.huangder.lumibooks.util.cache.WeightedLruCache
import com.huangder.lumibooks.util.parser.CbzArchiveOpener
import com.huangder.lumibooks.util.parser.OpenedCbzArchive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.Closeable

/**
 * 通用位图页数据源：PDF（PdfRenderer）与 CBZ（ZIP 内图片）共用同一套阅读管线。
 *
 * [targetWidthPx] 只是质量提示，实现按各自的画质与内存策略收敛；返回 null 表示单页解码失败，
 * 调用方应以占位而不是崩溃处理。缓存内的位图由数据源自己持有，调用方不得 recycle。
 */
internal interface BitmapPageSource : Closeable {
    val pageCount: Int
    /**
     * 页面宽高比，用于在图片解码完成前就按正确尺寸占位。
     * 占位尺寸与最终图片一致，快速滚动时列表不会因为页面变高/变矮而抖动。
     */
    suspend fun pageAspectRatio(pageIndex: Int): Float?
    /**
     * 低分辨率预览：解码/渲染成本很低，用于在整页就绪前先铺满页面区域。
     * 快速滚动时页面不会出现空白块，只会先糊后清晰。
     */
    suspend fun renderPreview(pageIndex: Int, targetWidthPx: Int): Bitmap?
    suspend fun renderPage(pageIndex: Int, targetWidthPx: Int): Bitmap?
    suspend fun renderThumbnail(pageIndex: Int, targetWidthPx: Int): Bitmap?
}

internal object BitmapPageSourceFactory {
    suspend fun create(
        context: Context,
        filePath: String,
        format: BookFormat
    ): BitmapPageSource? = withContext(Dispatchers.IO) {
        runCatching {
            when (format) {
                BookFormat.PDF -> openPdf(context, filePath)
                BookFormat.CBZ -> CbzBitmapPageSource(CbzArchiveOpener.open(context, filePath))
                else -> null
            }
        }.getOrNull()
    }

    private fun openPdf(context: Context, filePath: String): PdfBitmapPageSource {
        val descriptor = BookFileAccess.openDescriptor(context, filePath)
        return try {
            PdfBitmapPageSource(descriptor, PdfRenderer(descriptor))
        } catch (error: Throwable) {
            runCatching { descriptor.close() }
            throw error
        }
    }
}

internal class PdfBitmapPageSource(
    private val descriptor: ParcelFileDescriptor,
    private val renderer: PdfRenderer
) : BitmapPageSource {
    override val pageCount: Int = renderer.pageCount

    private val pageCache = WeightedLruCache<Int, Bitmap>(PAGE_CACHE_BYTES) {
        it.allocationByteCount.toLong().coerceAtLeast(0L)
    }
    private val thumbnailCache = WeightedLruCache<Int, Bitmap>(THUMBNAIL_CACHE_BYTES) {
        it.allocationByteCount.toLong().coerceAtLeast(0L)
    }
    private val previewCache = WeightedLruCache<Int, Bitmap>(PREVIEW_CACHE_BYTES) {
        it.allocationByteCount.toLong().coerceAtLeast(0L)
    }
    private val aspectRatios = java.util.concurrent.ConcurrentHashMap<Int, Float>()
    private val renderLock = Mutex()

    override suspend fun pageAspectRatio(pageIndex: Int): Float? {
        if (pageIndex !in 0 until pageCount) return null
        aspectRatios[pageIndex]?.let { return it }
        return withContext(Dispatchers.IO) {
            renderLock.withLock {
                runCatching {
                    renderer.openPage(pageIndex).use { page ->
                        (page.width.toFloat() / page.height.toFloat().coerceAtLeast(1f))
                            .takeIf { it > 0f }
                            ?.also { aspectRatios[pageIndex] = it }
                    }
                }.getOrNull()
            }
        }
    }

    override suspend fun renderPage(pageIndex: Int, targetWidthPx: Int): Bitmap? =
        render(pageIndex, targetWidthPx, pageCache)

    override suspend fun renderThumbnail(pageIndex: Int, targetWidthPx: Int): Bitmap? =
        render(pageIndex, targetWidthPx, thumbnailCache)

    override suspend fun renderPreview(pageIndex: Int, targetWidthPx: Int): Bitmap? =
        render(pageIndex, targetWidthPx, previewCache)

    private suspend fun render(
        pageIndex: Int,
        targetWidthPx: Int,
        cache: WeightedLruCache<Int, Bitmap>
    ): Bitmap? {
        if (pageIndex !in 0 until pageCount) return null
        cache[pageIndex]?.let { return it }
        return renderLock.withLock {
            cache[pageIndex] ?: run {
                val rendered = withContext(Dispatchers.IO) { drawPage(pageIndex, targetWidthPx) }
                if (rendered != null && currentCoroutineContext().isActive) {
                    cache.put(pageIndex, rendered)
                    rendered
                } else {
                    rendered?.takeIf { !it.isRecycled }?.recycle()
                    null
                }
            }
        }
    }

    /** PdfRenderer is single-threaded; every caller reaches it through [renderLock]. */
    private fun drawPage(pageIndex: Int, targetWidthPx: Int): Bitmap? {
        var bitmap: Bitmap? = null
        return try {
            renderer.openPage(pageIndex).use { page ->
                val requestedScale = (targetWidthPx.toFloat() / page.width.toFloat())
                    .coerceIn(MIN_RENDER_SCALE, MAX_RENDER_SCALE)
                // 超大页面按像素总量再收一次，避免一次分配几十 MB。
                val pixels = page.width.toFloat() * page.height.toFloat() * requestedScale * requestedScale
                val scale = if (pixels > MAX_PAGE_PIXELS) {
                    requestedScale * kotlin.math.sqrt(MAX_PAGE_PIXELS / pixels)
                } else {
                    requestedScale
                }
                val renderWidth = (page.width * scale).toInt().coerceAtLeast(1)
                val renderHeight = (page.height.toFloat() / page.width.toFloat() * renderWidth)
                    .toInt()
                    .coerceAtLeast(1)
                Bitmap.createBitmap(renderWidth, renderHeight, Bitmap.Config.ARGB_8888).also { target ->
                    bitmap = target
                    target.eraseColor(android.graphics.Color.WHITE)
                    page.render(target, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                }
            }
        } catch (error: Throwable) {
            bitmap?.takeIf { !it.isRecycled }?.recycle()
            if (error is kotlinx.coroutines.CancellationException) throw error
            android.util.Log.w(TAG, "Unable to render PDF page $pageIndex", error)
            null
        }
    }

    override fun close() {
        pageCache.clear()
        thumbnailCache.clear()
        previewCache.clear()
        runCatching { renderer.close() }
        runCatching { descriptor.close() }
    }

    private companion object {
        const val TAG = "PdfBitmapPageSource"
        const val PAGE_CACHE_BYTES = 64L * 1024L * 1024L
        const val THUMBNAIL_CACHE_BYTES = 8L * 1024L * 1024L
        const val PREVIEW_CACHE_BYTES = 4L * 1024L * 1024L
        const val MIN_RENDER_SCALE = 0.2f
        const val MAX_RENDER_SCALE = 2f
        const val MAX_PAGE_PIXELS = 4_000_000f
    }
}

/**
 * CBZ 页源：ZipFile 保持打开，按需从压缩包解码单页，不落盘解压。
 * 解码并发限制为 2，避免快速滑动时同时分配多张大图。
 */
internal class CbzBitmapPageSource(
    private val archive: OpenedCbzArchive
) : BitmapPageSource {
    override val pageCount: Int = archive.index.pages.size

    private val pageCache = WeightedLruCache<Int, Bitmap>(PAGE_CACHE_BYTES) {
        it.allocationByteCount.toLong().coerceAtLeast(0L)
    }
    private val thumbnailCache = WeightedLruCache<Int, Bitmap>(THUMBNAIL_CACHE_BYTES) {
        it.allocationByteCount.toLong().coerceAtLeast(0L)
    }
    private val previewCache = WeightedLruCache<Int, Bitmap>(PREVIEW_CACHE_BYTES) {
        it.allocationByteCount.toLong().coerceAtLeast(0L)
    }
    private val aspectRatios = java.util.concurrent.ConcurrentHashMap<Int, Float>()
    private val decodeLimiter = Semaphore(MAX_CONCURRENT_DECODES)

    override suspend fun pageAspectRatio(pageIndex: Int): Float? {
        val page = archive.index.pages.getOrNull(pageIndex) ?: return null
        aspectRatios[pageIndex]?.let { return it }
        return decodeLimiter.withPermit {
            aspectRatios[pageIndex] ?: run {
                val ratio = withContext(Dispatchers.IO) {
                    runCatching {
                        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        archive.openEntry(page.entryName)?.use { stream ->
                            BitmapFactory.decodeStream(stream, null, bounds)
                        }
                        if (bounds.outWidth > 0 && bounds.outHeight > 0) {
                            bounds.outWidth.toFloat() / bounds.outHeight.toFloat()
                        } else {
                            null
                        }
                    }.getOrNull()
                }
                ratio?.takeIf { it > 0f }?.also { aspectRatios[pageIndex] = it }
            }
        }
    }

    override suspend fun renderPage(pageIndex: Int, targetWidthPx: Int): Bitmap? =
        decode(pageIndex, targetWidthPx, pageCache, MAX_RENDER_DIMENSION)

    override suspend fun renderThumbnail(pageIndex: Int, targetWidthPx: Int): Bitmap? =
        decode(pageIndex, targetWidthPx, thumbnailCache, MAX_RENDER_DIMENSION)

    override suspend fun renderPreview(pageIndex: Int, targetWidthPx: Int): Bitmap? =
        decode(pageIndex, targetWidthPx, previewCache, MAX_RENDER_DIMENSION)

    private suspend fun decode(
        pageIndex: Int,
        targetWidthPx: Int,
        cache: WeightedLruCache<Int, Bitmap>,
        maxDimensionPx: Int
    ): Bitmap? {
        val page = archive.index.pages.getOrNull(pageIndex) ?: return null
        cache[pageIndex]?.let { return it }
        return decodeLimiter.withPermit {
            cache[pageIndex] ?: run {
                val bitmap = withContext(Dispatchers.IO) {
                    decodeEntry(page.entryName, targetWidthPx, maxDimensionPx)
                }
                if (bitmap != null && currentCoroutineContext().isActive) {
                    cache.put(pageIndex, bitmap)
                    bitmap
                } else {
                    bitmap?.takeIf { !it.isRecycled }?.recycle()
                    null
                }
            }
        }
    }

    private fun decodeEntry(
        entryName: String,
        targetWidthPx: Int,
        maxDimensionPx: Int
    ): Bitmap? {
        var decoded: Bitmap? = null
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            archive.openEntry(entryName)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, bounds)
            }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            val options = BitmapFactory.Options().apply {
                inSampleSize = sampleSizeFor(
                    bounds.outWidth,
                    bounds.outHeight,
                    targetWidthPx,
                    maxDimensionPx
                )
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            decoded = archive.openEntry(entryName)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, options)
            }
            decoded?.let { scaleDown(it, targetWidthPx, maxDimensionPx) }
        } catch (error: Throwable) {
            decoded?.takeIf { !it.isRecycled }?.recycle()
            android.util.Log.w("CbzBitmapPageSource", "Unable to decode comic page $entryName", error)
            null
        }
    }

    /** Keeps the decoded page close to the requested width while bounding peak memory. */
    private fun sampleSizeFor(
        width: Int,
        height: Int,
        targetWidthPx: Int,
        maxDimensionPx: Int
    ): Int {
        var sampleSize = 1
        val effectiveTarget = targetWidthPx.coerceAtLeast(MIN_TARGET_WIDTH_PX)
        // 只要还明显高于目标宽度就继续降采样：宁可略小于目标宽度（显示时再放大），
        // 也不要为了凑够目标宽度而整页解码 4K 原图（单页可达 30MB+）。
        while (width / sampleSize > effectiveTarget * SAMPLE_OVERSHOOT_TOLERANCE &&
            sampleSize < MAX_SAMPLE_SIZE
        ) {
            sampleSize *= 2
        }
        while (maxOf(width, height) / sampleSize > maxDimensionPx) sampleSize *= 2
        return sampleSize
    }

    private fun scaleDown(bitmap: Bitmap, targetWidthPx: Int, maxDimensionPx: Int): Bitmap {
        val effectiveTarget = targetWidthPx.coerceAtLeast(MIN_TARGET_WIDTH_PX)
        val widthScale = if (bitmap.width > effectiveTarget * 1.5f) {
            effectiveTarget.toFloat() / bitmap.width.toFloat()
        } else {
            1f
        }
        val longestSide = maxOf(bitmap.width, bitmap.height).toFloat()
        val dimensionScale = if (longestSide > maxDimensionPx) {
            maxDimensionPx / longestSide
        } else {
            1f
        }
        val scale = minOf(widthScale, dimensionScale)
        if (scale >= 1f) return bitmap
        val scaled = Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * scale).toInt().coerceAtLeast(1),
            (bitmap.height * scale).toInt().coerceAtLeast(1),
            true
        )
        if (scaled !== bitmap) bitmap.recycle()
        return scaled
    }

    override fun close() {
        pageCache.clear()
        thumbnailCache.clear()
        previewCache.clear()
        runCatching { archive.close() }
    }

    private companion object {
        const val PAGE_CACHE_BYTES = 64L * 1024L * 1024L
        const val THUMBNAIL_CACHE_BYTES = 8L * 1024L * 1024L
        const val PREVIEW_CACHE_BYTES = 4L * 1024L * 1024L
        const val MAX_CONCURRENT_DECODES = 2
        const val MAX_RENDER_DIMENSION = 4096
        const val MIN_TARGET_WIDTH_PX = 320
        const val MAX_SAMPLE_SIZE = 16
        const val SAMPLE_OVERSHOOT_TOLERANCE = 1.25f
    }
}
