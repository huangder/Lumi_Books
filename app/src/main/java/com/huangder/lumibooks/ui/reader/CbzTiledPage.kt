package com.huangder.lumibooks.ui.reader

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import android.view.MotionEvent
import androidx.compose.runtime.Composable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import com.huangder.lumibooks.domain.model.ReaderImageAdjustments
import com.davemorrissey.labs.subscaleview.decoder.DecoderFactory
import com.davemorrissey.labs.subscaleview.decoder.ImageRegionDecoder
import com.davemorrissey.labs.subscaleview.decoder.ImageDecoder
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.viewinterop.AndroidView
import com.davemorrissey.labs.subscaleview.ImageSource
import com.davemorrissey.labs.subscaleview.DefaultOnImageEventListener
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView
import com.davemorrissey.labs.subscaleview.decoder.SkiaImageDecoder
import com.davemorrissey.labs.subscaleview.decoder.SkiaImageRegionDecoder
import com.davemorrissey.labs.subscaleview.decoder.SkiaPooledImageRegionDecoder
import kotlinx.coroutines.Dispatchers

private val CbzTileDispatcher = Dispatchers.IO.limitedParallelism(2)

internal class ReaderTileImageView(context: Context) : SubsamplingScaleImageView(context) {
    var imageAdjustments = ReaderImageAdjustments()
        set(value) { if (field != value) { field = value; filterPaint.colorFilter = value.colorFilter(); invalidate() } }
    private val filterPaint = android.graphics.Paint()
    override fun onDraw(canvas: android.graphics.Canvas) {
        if (filterPaint.colorFilter == null) { super.onDraw(canvas); return }
        val save = canvas.saveLayer(android.graphics.RectF(canvas.clipBounds), filterPaint)
        super.onDraw(canvas)
        canvas.restoreToCount(save)
    }
    override fun onTouchEvent(event: MotionEvent): Boolean = false
    override fun performClick(): Boolean = false
}

@Composable
internal fun CbzTiledPage(
    source: RasterTileSource,
    zoomScale: Float,
    foreground: Boolean,
    fallback: Bitmap?,
    crop: RasterHorizontalCrop = RasterHorizontalCrop.FULL,
    fitToViewport: Boolean = true,
    adjustments: ReaderImageAdjustments = ReaderImageAdjustments(),
    modifier: Modifier = Modifier,
    onImageLoaded: () -> Unit = {},
    onError: (Throwable) -> Unit = {}
) {
    Box(modifier = modifier) {
        if (fallback != null) {
            RasterCroppedBitmap(
                bitmap = fallback,
                crop = crop,
                fitToViewport = fitToViewport,
                modifier = Modifier.fillMaxSize()
                , colorFilter = ColorFilter.colorMatrix(ColorMatrix(adjustments.colorMatrixValues()))
            )
        }
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                ReaderTileImageView(context).apply {
                    val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
                    val lowRam = activityManager.isLowRamDevice
                    val config = if (lowRam) Bitmap.Config.RGB_565 else Bitmap.Config.ARGB_8888
                    bitmapDecoderFactory = SkiaImageDecoder.Factory(config)
                    regionDecoderFactory = if (lowRam) {
                        SkiaImageRegionDecoder.Factory(config)
                    } else {
                        SkiaPooledImageRegionDecoder.Factory(config)
                    }
                    isPanEnabled = false
                    isZoomEnabled = false
                    isQuickScaleEnabled = false
                    backgroundDispatcher = CbzTileDispatcher
                    minimumScaleType = SubsamplingScaleImageView.SCALE_TYPE_CENTER_INSIDE
                    setBackgroundColor(android.graphics.Color.TRANSPARENT)
                    // The Compose parent already supplies the themed loading background. Keeping
                    // the tile layer transparent lets the NORMAL fallback remain visible while
                    // SSIV is still filling tiles.
                    tileBackgroundColor = android.graphics.Color.TRANSPARENT
                }
            },
            update = { view ->
                view.imageAdjustments = adjustments
                view.setBackgroundColor(android.graphics.Color.TRANSPARENT)
                view.tileBackgroundColor = android.graphics.Color.TRANSPARENT
                view.downSampling = if (foreground) 1 else 4
                view.externalScale = pdfZoomRenderBucket(zoomScale)
                val key = Triple(source.uri, adjustments.sharpen, crop)
                if (view.tag != key) {
                    val strength = adjustments.sharpen
                    val lowRam = (view.context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager).isLowRamDevice
                    val config = if (lowRam && strength == 0f) Bitmap.Config.RGB_565 else Bitmap.Config.ARGB_8888
                    view.bitmapDecoderFactory = DecoderFactory {
                        val decoder = SkiaImageDecoder.Factory(config).make()
                        object : ImageDecoder {
                            override fun decode(context: Context, uri: android.net.Uri, sampleSize: Int): Bitmap {
                                val original = decoder.decode(context, uri, sampleSize)
                                return sharpenReaderBitmap(original, strength).also { if (it !== original) original.recycle() }
                            }
                        }
                    }
                    view.regionDecoderFactory = DecoderFactory {
                        val decoder = if (lowRam) SkiaImageRegionDecoder.Factory(config).make()
                            else SkiaPooledImageRegionDecoder.Factory(config).make()
                        SharpenedRegionDecoder(decoder, strength)
                    }
                    view.tag = key
                    @Suppress("DEPRECATION")
                    view.setOnImageEventListener(object : DefaultOnImageEventListener {
                        override fun onImageLoaded() = onImageLoaded()

                        override fun onImageLoadError(e: Throwable) {
                            Log.w("RasterReader", "CBZ tiled image failed: ${source.uri}", e)
                            onError(e)
                        }

                        override fun onTileLoadError(e: Throwable) {
                            Log.w("RasterReader", "CBZ tile failed: ${source.uri}", e)
                            onError(e)
                        }
                    })
                    view.setImage(ImageSource.uri(source.uri).region(
                        crop.sourceRect(source.dimensions.width, source.dimensions.height)
                    ))
                }
            },
            onRelease = { it.recycle() }
        )
    }
}

/** Decode a one-pixel halo around every tile to keep sharpening seamless. */
internal class SharpenedRegionDecoder(
    private val decoder: ImageRegionDecoder, private val amount: Float
) : ImageRegionDecoder {
    private var dimensions = android.graphics.Point()
    override fun init(context: Context, uri: android.net.Uri): android.graphics.Point =
        decoder.init(context, uri).also { dimensions = it }
    override val isReady get() = decoder.isReady
    override fun recycle() = decoder.recycle()
    override fun decodeRegion(sRect: android.graphics.Rect, sampleSize: Int): Bitmap {
        if (amount == 0f) return decoder.decodeRegion(sRect, sampleSize)
        val halo = android.graphics.Rect(
            (sRect.left - sampleSize).coerceAtLeast(0), (sRect.top - sampleSize).coerceAtLeast(0),
            (sRect.right + sampleSize).coerceAtMost(dimensions.x), (sRect.bottom + sampleSize).coerceAtMost(dimensions.y)
        )
        val original = decoder.decodeRegion(halo, sampleSize)
        val sharp = sharpenReaderBitmap(original, amount)
        val left = (sRect.left - halo.left) / sampleSize
        val top = (sRect.top - halo.top) / sampleSize
        val width = ((sRect.width() + sampleSize - 1) / sampleSize).coerceAtMost(sharp.width - left)
        val height = ((sRect.height() + sampleSize - 1) / sampleSize).coerceAtMost(sharp.height - top)
        val cropped = Bitmap.createBitmap(sharp, left, top, width, height)
        if (sharp !== original) original.recycle()
        if (cropped !== sharp) sharp.recycle()
        return cropped
    }
}
