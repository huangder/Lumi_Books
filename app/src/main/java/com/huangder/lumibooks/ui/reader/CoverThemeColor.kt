package com.huangder.lumibooks.ui.reader

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import com.huangder.lumibooks.util.parser.ReaderImageTransparencyInfo
import com.huangder.lumibooks.util.parser.pngHasAlphaChannel
import java.io.File
import kotlin.math.roundToInt

/** Extracts a stable opaque color from the top and bottom edges of a cover. */
internal fun extractCoverEdgeColor(path: String?): Int? {
    val file = path?.takeIf(String::isNotBlank)?.let(::File)?.takeIf(File::isFile) ?: return null
    // Cover cache filenames may end in .jpg even when their bytes are PNG.
    if (runCatching { file.inputStream().buffered().use(::pngHasAlphaChannel) }.getOrDefault(false)) return null
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.absolutePath, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

    var sampleSize = 1
    while (bounds.outWidth / sampleSize > 256 || bounds.outHeight / sampleSize > 256) {
        sampleSize *= 2
    }
    val bitmap = BitmapFactory.decodeFile(
        file.absolutePath,
        BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
    ) ?: return null
    return try {
        if (bitmap.hasAlpha()) return null
        coverEdgeColor(bitmap, 0xFFFBFBFC.toInt())
    } finally {
        bitmap.recycle()
    }
}

/** Extracts the same edge color from an already decoded cover drawable. */
internal fun readerCoverEdgeColor(drawable: Drawable, fallback: Int): Int {
    // Use original pixels/metadata; adjustment wrappers and raster targets are
    // always ARGB, even for opaque JPEGs, so their format alone says nothing.
    val original = generateSequence(drawable) { (it as? AdjustedReaderDrawable)?.source }.last()
    if ((original as? ReaderImageTransparencyInfo)?.hasAlphaChannel == true) return fallback
    val source = (original as? BitmapDrawable)?.bitmap
    if (source?.hasAlpha() == true) return fallback
    val bitmap = source?.takeUnless(Bitmap::isRecycled) ?: runCatching {
        val width = original.intrinsicWidth.coerceIn(1, 512)
        val height = original.intrinsicHeight.coerceIn(1, 512)
        Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { rendered ->
            val previous = Rect(original.bounds)
            try {
                original.setBounds(0, 0, width, height)
                original.draw(Canvas(rendered))
            } catch (error: Throwable) {
                rendered.recycle()
                throw error
            } finally {
                original.bounds = previous
            }
        }
    }.getOrNull() ?: return fallback
    return try {
        if (source == null && bitmapHasTransparentPixels(bitmap)) return fallback
        coverEdgeColor(bitmap, fallback)
    } finally {
        if (bitmap !== source && !bitmap.isRecycled) bitmap.recycle()
    }
}

private fun bitmapHasTransparentPixels(bitmap: Bitmap): Boolean {
    val row = IntArray(bitmap.width)
    for (y in 0 until bitmap.height) {
        bitmap.getPixels(row, 0, bitmap.width, 0, y, bitmap.width, 1)
        if (row.any { Color.alpha(it) < 255 }) return true
    }
    return false
}

private fun coverEdgeColor(bitmap: Bitmap, fallback: Int): Int {
    if (bitmap.width <= 0 || bitmap.height <= 0) return fallback

    val fallbackOpaque = fallback or Color.BLACK
    val bandHeight = (bitmap.height * 0.04f).roundToInt().coerceIn(1, 16)
    val stride = (bitmap.width / 256).coerceAtLeast(1)
    var red = 0L
    var green = 0L
    var blue = 0L
    var samples = 0L

    fun sampleBand(startY: Int, endY: Int) {
        for (y in startY until endY.coerceAtMost(bitmap.height)) {
            for (x in 0 until bitmap.width step stride) {
                val pixel = bitmap.getPixel(x, y)
                val alpha = Color.alpha(pixel)
                if (alpha == 0) continue
                val inverseAlpha = 255 - alpha
                red += (Color.red(pixel) * alpha + Color.red(fallbackOpaque) * inverseAlpha) / 255
                green += (Color.green(pixel) * alpha + Color.green(fallbackOpaque) * inverseAlpha) / 255
                blue += (Color.blue(pixel) * alpha + Color.blue(fallbackOpaque) * inverseAlpha) / 255
                samples++
            }
        }
    }

    sampleBand(0, bandHeight)
    sampleBand((bitmap.height - bandHeight).coerceAtLeast(0), bitmap.height)
    if (samples == 0L) return fallbackOpaque
    return Color.rgb(
        (red / samples).toInt().coerceIn(0, 255),
        (green / samples).toInt().coerceIn(0, 255),
        (blue / samples).toInt().coerceIn(0, 255)
    )
}
