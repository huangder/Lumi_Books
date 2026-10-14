package com.huangder.lumibooks.ui.reader

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.huangder.lumibooks.domain.model.BookFormat
import com.huangder.lumibooks.domain.model.PageRenderMode
import com.huangder.lumibooks.util.BookFileAccess
import com.huangder.lumibooks.util.cache.BookFingerprint
import com.huangder.lumibooks.util.parser.CbzArchiveOpener
import com.huangder.lumibooks.util.parser.OpenedCbzArchive
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.Closeable

/** Bitmaps are shared with caches and other consumers; callers must never recycle them. */
internal interface BitmapPageSource : Closeable {
    val pageCount: Int
    val moving: StateFlow<Boolean>
    val visiblePages: StateFlow<Set<Int>>
    fun updateViewport(viewport: RasterViewport)
    fun saveResumeSnapshot()
    fun resumeLoading()
    fun cachedPage(pageIndex: Int): Bitmap?
    fun cachedReadablePage(pageIndex: Int): Bitmap?
    fun cachedAspectRatio(pageIndex: Int): Float?
    fun pageDrawn(pageIndex: Int, finalQuality: Boolean)
    suspend fun tiledPage(pageIndex: Int): RasterTileSource?
    suspend fun pageAspectRatio(pageIndex: Int, background: Boolean = false): Float?
    suspend fun pageWidth(pageIndex: Int): Int?
    suspend fun horizontalCrop(pageIndex: Int): RasterHorizontalCrop
    suspend fun renderPreview(pageIndex: Int, targetWidthPx: Int): Bitmap?
    suspend fun renderPage(pageIndex: Int, targetWidthPx: Int, mode: PageRenderMode = PageRenderMode.NORMAL): Bitmap?
    suspend fun renderThumbnail(pageIndex: Int, targetWidthPx: Int): Bitmap?
}

internal object BitmapPageSourceFactory {
    suspend fun create(context: Context, filePath: String, format: BookFormat,
        epubComicIndex: com.huangder.lumibooks.util.epub.EpubComicIndex? = null): BitmapPageSource? {
        var owned: BitmapPageSource? = null
        try {
            return withContext(Dispatchers.IO) {
                val decoder = when (format) {
                    BookFormat.PDF -> openPdf(context, filePath)
                    BookFormat.CBZ -> CbzPageDecoder(CbzArchiveOpener.open(context, filePath))
                    BookFormat.EPUB -> CbzPageDecoder(com.huangder.lumibooks.util.epub.EpubComicArchive.open(
                        context, filePath, epubComicIndex ?: return@withContext null))
                    else -> return@withContext null
                }
                try {
                    val disk = runCatching {
                        RasterResumeCache(context, BookFingerprint.resolve(context, filePath),
                            namespace = if (epubComicIndex != null) "raster_epub_comic_v${com.huangder.lumibooks.util.epub.EpubComicIndex.VERSION}" else "raster")
                    }.getOrNull()
                    RasterRenderSession(decoder, disk).also { owned = it }
                } catch (error: Throwable) {
                    decoder.close()
                    throw error
                }
            }
        } catch (error: Throwable) {
            owned?.close()
            if (error is CancellationException) throw error
            android.util.Log.w("RasterReader", "Unable to open raster source", error)
            return null
        }
    }

    private fun openPdf(context: Context, filePath: String): PdfPageDecoder {
        val descriptor = BookFileAccess.openDescriptor(context, filePath)
        return try { PdfPageDecoder(descriptor, PdfRenderer(descriptor)) } catch (error: Throwable) {
            descriptor.close()
            throw error
        }
    }
}

/** Only the session scheduler may access these synchronous, potentially non-cancellable decoders. */
internal interface RasterPageDecoder : Closeable {
    val pageCount: Int
    val parallelism: Int
    fun dimensions(page: Int): RasterDimensions?
    fun spec(page: Int, dimensions: RasterDimensions, width: Int, mode: PageRenderMode, preview: Boolean): RasterDecodeSpec
    fun decode(spec: RasterDecodeSpec): Bitmap?
    fun tileSource(page: Int, dimensions: RasterDimensions): RasterTileSource? = null
}

internal class PdfPageDecoder(
    private val descriptor: ParcelFileDescriptor,
    private val renderer: PdfRenderer
) : RasterPageDecoder {
    override val pageCount = renderer.pageCount
    override val parallelism = 1
    private val memoryPixelBudget = pdfMemoryPixelBudget(Runtime.getRuntime().maxMemory())

    override fun dimensions(page: Int): RasterDimensions = renderer.openPage(page).use {
        RasterDimensions(it.width, it.height)
    }

    override fun spec(page: Int, dimensions: RasterDimensions, width: Int, mode: PageRenderMode, preview: Boolean): RasterDecodeSpec {
        val scale = if (preview) {
            minOf(width.toFloat() / dimensions.width,
                PDF_RENDER_MAX_DIMENSION.toFloat() / maxOf(dimensions.width, dimensions.height),
                kotlin.math.sqrt(512_000.0 / (dimensions.width.toDouble() * dimensions.height)).toFloat())
        } else pdfRenderScale(dimensions.width, dimensions.height, pageRenderTargetWidth(width), mode, memoryPixelBudget)
        return RasterDecodeSpec(page, (dimensions.width * scale).toInt().coerceAtLeast(1),
            (dimensions.height * scale).toInt().coerceAtLeast(1))
    }

    override fun decode(spec: RasterDecodeSpec): Bitmap? {
        return try {
            renderPdfWithMemoryFallback(spec) { attempt ->
                renderer.openPage(attempt.page).use { page ->
                    val bitmap = Bitmap.createBitmap(attempt.width, attempt.height, Bitmap.Config.ARGB_8888)
                    try {
                        bitmap.eraseColor(android.graphics.Color.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        bitmap
                    } catch (error: Throwable) {
                        bitmap.recycle()
                        throw error
                    }
                }
            }
        } catch (error: Throwable) {
            android.util.Log.w("RasterReader", "PDF decode failed: ${spec.page}", error)
            null
        }
    }

    override fun close() {
        runCatching { renderer.close() }
        runCatching { descriptor.close() }
    }
}

/** 降低分辨率再试，避免内存不足时反复分配同一张大位图。 */
internal fun <T> renderPdfWithMemoryFallback(spec: RasterDecodeSpec, render: (RasterDecodeSpec) -> T): T {
    var attempt = spec
    repeat(3) { retry ->
        try {
            return render(attempt)
        } catch (error: OutOfMemoryError) {
            if (retry == 2 || (attempt.width == 1 && attempt.height == 1)) throw error
            attempt = attempt.copy(width = (attempt.width / 2).coerceAtLeast(1),
                height = (attempt.height / 2).coerceAtLeast(1))
        }
    }
    error("PDF render attempts exhausted")
}

internal class CbzPageDecoder(private val archive: OpenedCbzArchive) : RasterPageDecoder {
    override val pageCount = archive.index.pages.size
    override val parallelism = 2

    override fun dimensions(page: Int): RasterDimensions? {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        archive.openEntry(archive.index.pages[page].entryName)?.use {
            BitmapFactory.decodeStream(it, null, options)
        }
        return if (options.outWidth > 0 && options.outHeight > 0) RasterDimensions(options.outWidth, options.outHeight) else null
    }

    override fun spec(page: Int, dimensions: RasterDimensions, width: Int, mode: PageRenderMode, preview: Boolean): RasterDecodeSpec {
        val sample = if (preview) rasterPreviewSample(dimensions.width, dimensions.height, width)
            else comicSampleSize(dimensions.width, dimensions.height, pageRenderTargetWidth(width), mode)
        // Codecs round odd sampled dimensions differently (PNG floors, JPEG may ceil).
        // Use the guaranteed lower bound; sample remains part of the decode identity.
        return RasterDecodeSpec(page, (dimensions.width / sample).coerceAtLeast(1),
            (dimensions.height / sample).coerceAtLeast(1), sample)
    }

    override fun decode(spec: RasterDecodeSpec): Bitmap? = archive.openEntry(archive.index.pages[spec.page].entryName)?.use {
        BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply {
            inSampleSize = spec.sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        })
    }

    override fun tileSource(page: Int, dimensions: RasterDimensions): RasterTileSource? {
        val entry = archive.index.pages.getOrNull(page) ?: return null
        return RasterTileSource(archive.entryUri(entry.entryName), dimensions)
    }

    override fun close() { runCatching { archive.close() } }
}
