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
    suspend fun renderPreview(pageIndex: Int, targetWidthPx: Int): Bitmap?
    suspend fun renderPage(pageIndex: Int, targetWidthPx: Int, mode: PageRenderMode = PageRenderMode.NORMAL): Bitmap?
    suspend fun renderThumbnail(pageIndex: Int, targetWidthPx: Int): Bitmap?
}

internal object BitmapPageSourceFactory {
    suspend fun create(context: Context, filePath: String, format: BookFormat): BitmapPageSource? {
        var owned: BitmapPageSource? = null
        try {
            return withContext(Dispatchers.IO) {
                val decoder = when (format) {
                    BookFormat.PDF -> openPdf(context, filePath)
                    BookFormat.CBZ -> CbzPageDecoder(CbzArchiveOpener.open(context, filePath))
                    else -> return@withContext null
                }
                try {
                    val disk = runCatching {
                        RasterResumeCache(context, BookFingerprint.resolve(context, filePath))
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

    override fun dimensions(page: Int): RasterDimensions = renderer.openPage(page).use {
        RasterDimensions(it.width, it.height)
    }

    override fun spec(page: Int, dimensions: RasterDimensions, width: Int, mode: PageRenderMode, preview: Boolean): RasterDecodeSpec {
        val scale = if (preview) {
            minOf(width.toFloat() / dimensions.width,
                kotlin.math.sqrt(512_000.0 / (dimensions.width.toDouble() * dimensions.height)).toFloat())
        } else pdfRenderScale(dimensions.width, dimensions.height, pageRenderTargetWidth(width), mode)
        val outputWidth = (dimensions.width * scale).toInt().coerceAtLeast(1)
        return RasterDecodeSpec(page, outputWidth, (outputWidth / dimensions.ratio).toInt().coerceAtLeast(1))
    }

    override fun decode(spec: RasterDecodeSpec): Bitmap? {
        var bitmap: Bitmap? = null
        return try {
            renderer.openPage(spec.page).use { page ->
                Bitmap.createBitmap(spec.width, spec.height, Bitmap.Config.ARGB_8888).also {
                    bitmap = it
                    it.eraseColor(android.graphics.Color.WHITE)
                    page.render(it, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                }
            }
        } catch (error: Throwable) {
            bitmap?.recycle()
            android.util.Log.w("RasterReader", "PDF decode failed: ${spec.page}", error)
            null
        }
    }

    override fun close() {
        runCatching { renderer.close() }
        runCatching { descriptor.close() }
    }
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
