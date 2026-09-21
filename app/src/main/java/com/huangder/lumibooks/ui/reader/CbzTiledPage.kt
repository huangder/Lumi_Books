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
    override fun onTouchEvent(event: MotionEvent): Boolean = false
    override fun performClick(): Boolean = false
}

@Composable
internal fun CbzTiledPage(
    source: RasterTileSource,
    zoomScale: Float,
    foreground: Boolean,
    fallback: Bitmap?,
    modifier: Modifier = Modifier,
    onImageLoaded: () -> Unit = {},
    onError: (Throwable) -> Unit = {}
) {
    Box(modifier = modifier) {
        if (fallback != null) {
            Image(
                bitmap = fallback.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
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
                view.setBackgroundColor(android.graphics.Color.TRANSPARENT)
                view.tileBackgroundColor = android.graphics.Color.TRANSPARENT
                view.downSampling = if (foreground) 1 else 4
                view.externalScale = pdfZoomRenderBucket(zoomScale)
                if (view.tag != source.uri) {
                    view.tag = source.uri
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
                    view.setImage(ImageSource.uri(source.uri))
                }
            },
            onRelease = { it.recycle() }
        )
    }
}
