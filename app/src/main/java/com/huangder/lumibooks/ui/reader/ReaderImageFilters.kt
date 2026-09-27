package com.huangder.lumibooks.ui.reader

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.text.Spanned
import android.text.style.ImageSpan
import android.view.View
import androidx.compose.runtime.*
import com.huangder.lumibooks.domain.model.ReaderImageAdjustments
import com.huangder.lumibooks.util.parser.InlineFootnoteMarkerDrawable
import com.huangder.lumibooks.util.parser.ReaderImagePixelSource
import kotlinx.coroutines.*
import kotlin.math.roundToInt

internal val ReaderUiState.imageAdjustments: ReaderImageAdjustments
    get() = ReaderImageAdjustments(imageBrightness, imageContrast, imageSharpen)

internal class ReaderSharpenTransformation(private val amount: Float) : coil.transform.Transformation {
    override val cacheKey = "reader-sharpen-v1:$amount"
    override suspend fun transform(input: Bitmap, size: coil.size.Size): Bitmap = withContext(Dispatchers.Default) {
        val context = currentCoroutineContext()
        sharpenReaderBitmap(input, amount) { context.ensureActive() }
    }
}

@Composable
internal fun rememberSharpenedBitmap(source: Bitmap?, amount: Float): Bitmap? {
    if (source == null || amount == 0f) return source
    val result by produceState<Bitmap?>(null, source, amount) {
        value = null
        value = withContext(Dispatchers.Default) {
            val context = currentCoroutineContext()
            sharpenReaderBitmap(source, amount) { context.ensureActive() }
        }
    }
    return result ?: source
}

internal fun ReaderImageAdjustments.colorMatrixValues(): FloatArray {
    val settings = normalized()
    val c = settings.contrast
    val offset = 127.5f * (1f - c) + settings.brightness * 255f
    return floatArrayOf(c, 0f, 0f, 0f, offset, 0f, c, 0f, 0f, offset,
        0f, 0f, c, 0f, offset, 0f, 0f, 0f, 1f, 0f)
}

internal fun ReaderImageAdjustments.colorFilter(): ColorMatrixColorFilter? =
    if (brightness == 0f && contrast == 1f) null else ColorMatrixColorFilter(colorMatrixValues())

/** Cross-kernel unsharp mask. Rows bound scratch memory; the source/cache is never mutated. */
internal fun sharpenReaderBitmap(source: Bitmap, amount: Float, checkCancelled: () -> Unit = {}): Bitmap {
    val strength = amount.coerceIn(0f, 1f)
    if (strength == 0f) return source
    val input = if (source.config == Bitmap.Config.HARDWARE) source.copy(Bitmap.Config.ARGB_8888, false) else source
    val width = input.width
    val height = input.height
    val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    output.density = source.density
    var above = IntArray(width)
    var current = IntArray(width)
    var below = IntArray(width)
    val row = IntArray(width)
    input.getPixels(current, 0, width, 0, 0, width, 1)
    current.copyInto(above)
    try {
        for (y in 0 until height) {
            checkCancelled()
            input.getPixels(below, 0, width, 0, (y + 1).coerceAtMost(height - 1), width, 1)
            for (x in 0 until width) {
                val center = current[x]
                fun neighbor(pixel: Int) = if (pixel ushr 24 == 0) center else pixel
                val left = neighbor(current[(x - 1).coerceAtLeast(0)])
                val right = neighbor(current[(x + 1).coerceAtMost(width - 1)])
                val top = neighbor(above[x])
                val bottom = neighbor(below[x])
                fun channel(shift: Int): Int {
                    val c = (center ushr shift) and 255
                    val sum = ((left ushr shift) and 255) + ((right ushr shift) and 255) +
                        ((top ushr shift) and 255) + ((bottom ushr shift) and 255)
                    return (c + strength * (4 * c - sum)).roundToInt().coerceIn(0, 255)
                }
                row[x] = (center and -0x1000000) or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
            }
            output.setPixels(row, 0, width, 0, y, width, 1)
            val old = above
            above = current
            current = below
            below = old
        }
        return output
    } catch (error: Throwable) {
        output.recycle()
        throw error
    } finally {
        if (input !== source) input.recycle()
    }
}

/** Retains the original pixels across edits, so adjustments never compound. */
internal class AdjustedReaderDrawable(val source: Drawable) : Drawable(), InlineFootnoteMarkerDrawable {
    override val isInlineFootnoteMarker: Boolean
        get() = (source as? InlineFootnoteMarkerDrawable)?.isInlineFootnoteMarker == true
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private var settings = ReaderImageAdjustments()
    private var rendered: Bitmap? = null
    private var originalPixels: Bitmap? = null
    private var job: Job? = null
    private var requestedSharpness = -1f
    private var onPixelsChanged: () -> Unit = {}
    private var imageScope: CoroutineScope? = null
    private var retryJob: Job? = null
    private var retryCount = 0
    init { bounds = Rect(source.bounds) }

    fun update(value: ReaderImageAdjustments, scope: CoroutineScope, onChanged: () -> Unit) {
        onPixelsChanged = onChanged
        imageScope = scope
        val next = value.normalized()
        val changed = settings != next
        settings = next
        paint.colorFilter = settings.colorFilter()
        val strength = settings.sharpen
        if (requestedSharpness != strength || (strength > 0f && rendered == null && job?.isActive != true)) {
            requestedSharpness = strength
            job?.cancel()
            if (strength == 0f) rendered = null
            if (strength > 0f) {
                job = scope.launch {
                    try {
                        val original = originalPixels ?: when (source) {
                            is BitmapDrawable -> source.bitmap
                            is ReaderImagePixelSource -> {
                                var pixels: Bitmap? = null
                                for (attempt in 0..3) {
                                    pixels = withContext(Dispatchers.IO) { source.acquireReaderBitmap() }
                                    if (pixels != null) break
                                    delay(250L * (attempt + 1))
                                }
                                pixels ?: return@launch
                            }
                            else -> {
                                val w = source.bounds.width().coerceAtLeast(1)
                                val h = source.bounds.height().coerceAtLeast(1)
                                Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { bitmap ->
                                    val previous = Rect(source.bounds)
                                    try {
                                        source.setBounds(0, 0, w, h)
                                        source.draw(Canvas(bitmap))
                                    } finally {
                                        source.bounds = previous
                                    }
                                }
                            }
                        }
                        originalPixels = original
                        val result = withContext(Dispatchers.Default) {
                            val coroutineContext = currentCoroutineContext()
                            sharpenReaderBitmap(original, strength) { coroutineContext.ensureActive() }
                        }
                        rendered = result
                        invalidateSelf()
                        onPixelsChanged()
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Exception) {
                        android.util.Log.w("ReaderImageFilters", "Image adjustment failed", error)
                    }
                }
            }
        }
        if (changed) {
            invalidateSelf()
            onChanged()
        }
    }

    override fun draw(canvas: Canvas) {
        val original = originalPixels?.takeUnless { it.isRecycled }
            ?: (source as? BitmapDrawable)?.bitmap?.takeUnless { it.isRecycled }
            ?: (source as? ReaderImagePixelSource)?.acquireReaderBitmap()
        if (original != null) originalPixels = original
        val bitmap = rendered?.takeUnless { it.isRecycled } ?: original
        if (bitmap != null) {
            retryCount = 0
            canvas.drawBitmap(bitmap, null, bounds, paint)
        } else {
            val save = if (paint.colorFilter == null && paint.alpha == 255) canvas.save()
                else canvas.saveLayer(android.graphics.RectF(canvas.clipBounds), paint)
            // The parser's lazy drawable is shared by all cached chapter spans.
            // ImageSpan measures bounds.bottom/right, not just height/width: a
            // leftover screen offset grows the line on every slot rotation.
            val previous = Rect(source.bounds)
            try {
                source.bounds = bounds
                source.draw(canvas)
            } finally {
                source.bounds = previous
                canvas.restoreToCount(save)
            }
            if (source is ReaderImagePixelSource && retryJob?.isActive != true && retryCount < 4) {
                retryCount++
                retryJob = imageScope?.launch {
                    delay(250L * retryCount)
                    invalidateSelf()
                    onPixelsChanged()
                }
            }
        }
    }
    override fun getIntrinsicWidth() = source.intrinsicWidth
    override fun getIntrinsicHeight() = source.intrinsicHeight
    override fun setAlpha(alpha: Int) { paint.alpha = alpha; invalidateSelf() }
    override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter; invalidateSelf() }
    @Deprecated("Deprecated in Java") override fun getOpacity() = PixelFormat.TRANSLUCENT
}

/** Create view-owned wrappers once; changing pixels must not replace the selectable buffer. */
internal fun wrapReaderImages(text: CharSequence?): CharSequence? {
    val spanned = text as? Spanned ?: return text
    val images = spanned.getSpans(0, spanned.length, ImageSpan::class.java)
    if (images.isEmpty()) return text
    val copy = android.text.SpannableStringBuilder(spanned)
    images.forEach { image ->
        if (image.drawable is AdjustedReaderDrawable) return@forEach
        val replacement = ImageSpan(AdjustedReaderDrawable(image.drawable), image.source.orEmpty(), image.verticalAlignment)
        val start = copy.getSpanStart(image)
        val end = copy.getSpanEnd(image)
        val flags = copy.getSpanFlags(image)
        copy.removeSpan(image)
        copy.setSpan(replacement, start, end, flags)
    }
    return copy
}

internal fun updateReaderImages(
    text: CharSequence?, settings: ReaderImageAdjustments, scope: CoroutineScope, onChanged: () -> Unit
) {
    val spanned = text as? Spanned ?: return
    spanned.getSpans(0, spanned.length, ImageSpan::class.java).forEach { image ->
        (image.drawable as? AdjustedReaderDrawable)?.update(settings, scope, onChanged)
    }
}

/** Copy spans, not text or geometry; never filter publisher text/backgrounds. */
internal fun adjustedReaderImages(
    text: CharSequence?, settings: ReaderImageAdjustments, scope: CoroutineScope, onChanged: () -> Unit
): CharSequence? {
    val copy = wrapReaderImages(text)
    updateReaderImages(copy, settings, scope, onChanged)
    return copy
}
