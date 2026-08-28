package com.huangder.lumibooks.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import coil.size.Size
import coil.transform.Transformation
import java.io.File
import kotlin.math.max

/** Creates immutable blurred copies while keeping the imported source image intact. */
object ReaderBackgroundImageProcessor {
    fun createBlurredCopy(source: File, target: File, blurDp: Float, density: Float): Boolean {
        if (!source.isFile || blurDp <= 0f) return false
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(source.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return false
        var sampleSize = 1
        while (max(bounds.outWidth / sampleSize, bounds.outHeight / sampleSize) > 2048) {
            sampleSize *= 2
        }
        val bitmap = BitmapFactory.decodeFile(
            source.absolutePath,
            BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
        ) ?: return false
        return try {
            blur(bitmap, (blurDp * density).toInt().coerceIn(1, 160))
            target.parentFile?.mkdirs()
            target.outputStream().use { output ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 92, output)
            }
        } catch (_: Throwable) {
            false
        } finally {
            bitmap.recycle()
        }
    }

    fun blur(bitmap: Bitmap, radius: Int) {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        repeat(3) { pass(pixels, width, height, radius, true) }
        repeat(3) { pass(pixels, width, height, radius, false) }
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
    }

    private fun pass(pixels: IntArray, width: Int, height: Int, radius: Int, horizontal: Boolean) {
        val span = radius * 2 + 1
        val out = IntArray(if (horizontal) width else height)
        val major = if (horizontal) height else width
        val minor = if (horizontal) width else height
        for (majorIndex in 0 until major) {
            var a = 0; var r = 0; var g = 0; var b = 0
            fun sample(index: Int) {
                val i = if (horizontal) majorIndex * width + index else index * width + majorIndex
                val c = pixels[i]
                a += c ushr 24; r += c shr 16 and 0xff; g += c shr 8 and 0xff; b += c and 0xff
            }
            for (offset in -radius..radius) sample(offset.coerceIn(0, minor - 1))
            for (index in 0 until minor) {
                out[index] = (a / span shl 24) or (r / span shl 16) or
                    (g / span shl 8) or (b / span)
                val removeIndex = (index - radius).coerceIn(0, minor - 1)
                val addIndex = (index + radius + 1).coerceIn(0, minor - 1)
                val remove = if (horizontal) pixels[majorIndex * width + removeIndex]
                else pixels[removeIndex * width + majorIndex]
                val add = if (horizontal) pixels[majorIndex * width + addIndex]
                else pixels[addIndex * width + majorIndex]
                a += (add ushr 24) - (remove ushr 24)
                r += (add shr 16 and 0xff) - (remove shr 16 and 0xff)
                g += (add shr 8 and 0xff) - (remove shr 8 and 0xff)
                b += (add and 0xff) - (remove and 0xff)
            }
            for (index in 0 until minor) {
                val i = if (horizontal) majorIndex * width + index else index * width + majorIndex
                pixels[i] = out[index]
            }
        }
    }
}

/**
 * Coil transformation used when a baked background has not been generated yet.
 * The blur is part of the drawable pixels, so software curl snapshots retain it.
 */
class ReaderBackgroundBlurTransformation(radiusPx: Int) : Transformation {
    private val radius = radiusPx.coerceIn(1, 160)

    override val cacheKey: String = "reader-background-blur-v1-$radius"

    override suspend fun transform(input: Bitmap, size: Size): Bitmap {
        val output = Bitmap.createBitmap(input.width, input.height, Bitmap.Config.ARGB_8888)
        android.graphics.Canvas(output).drawBitmap(input, 0f, 0f, null)
        ReaderBackgroundImageProcessor.blur(output, radius)
        return output
    }
}
