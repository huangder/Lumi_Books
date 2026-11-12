package com.huangder.lumibooks.ui.reader

import android.content.Context
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.drawable.Drawable
import coil.imageLoader
import coil.request.Disposable
import coil.request.ImageRequest
import com.huangder.lumibooks.util.ReaderBackgroundBlurTransformation
import java.io.File
import kotlin.math.roundToInt

/** One decoded photo, drawn in spread coordinates by live pages and animation captures. */
internal class ReaderSpreadBackground(private val context: Context, private val invalidate: () -> Unit) {
    private var request: Disposable? = null
    private var key: Pair<String?, Float>? = null
    private var generation = 0
    private var image: Drawable? = null
    private var failed = false
    var color: Int = android.graphics.Color.WHITE
        private set
    var opacity: Float = 1f
        private set
    val hasImage get() = key?.first != null

    fun configure(color: Int, path: String?, opacity: Float, blurDp: Float) {
        val paintChanged = this.color != color || this.opacity != opacity.coerceIn(0f, 1f)
        this.color = color
        this.opacity = opacity.coerceIn(0f, 1f)
        val next = path to blurDp.coerceIn(0f, 40f)
        if (key == next && !failed) { if (paintChanged) invalidate(); return }
        key = next; failed = false
        val token = ++generation
        request?.dispose()
        if (path == null) { image = null; invalidate(); return }
        if (!File(path).exists()) { failed = true; if (paintChanged) invalidate(); return }
        request = context.imageLoader.enqueue(ImageRequest.Builder(context).data(File(path))
            .size(maxOf(context.resources.displayMetrics.widthPixels, context.resources.displayMetrics.heightPixels))
            .allowHardware(false).apply {
                val radius = (next.second * context.resources.displayMetrics.density).roundToInt()
                if (radius > 0) transformations(ReaderBackgroundBlurTransformation(radius))
            }.target(onSuccess = { drawable ->
                if (token == generation) { image = drawable; invalidate() }
            }, onError = { if (token == generation) failed = true }).build())
    }

    fun draw(canvas: Canvas, width: Int, height: Int) {
        canvas.drawColor(color)
        val drawable = image ?: return
        if (width <= 0 || height <= 0 || drawable.intrinsicWidth <= 0 || drawable.intrinsicHeight <= 0) return
        val scale = maxOf(width.toFloat() / drawable.intrinsicWidth, height.toFloat() / drawable.intrinsicHeight)
        val w = (drawable.intrinsicWidth * scale).roundToInt()
        val h = (drawable.intrinsicHeight * scale).roundToInt()
        val old = Rect(drawable.bounds); val oldAlpha = drawable.alpha
        val save = canvas.save()
        canvas.clipRect(0, 0, width, height)
        drawable.setBounds((width - w) / 2, (height - h) / 2, (width - w) / 2 + w, (height - h) / 2 + h)
        drawable.alpha = (opacity * 255).roundToInt()
        drawable.draw(canvas)
        drawable.bounds = old; drawable.alpha = oldAlpha
        canvas.restoreToCount(save)
    }

    fun release() { generation++; request?.dispose(); request = null; image = null; key = null }
}
