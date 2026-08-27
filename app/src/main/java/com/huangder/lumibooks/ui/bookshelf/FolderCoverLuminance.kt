package com.huangder.lumibooks.ui.bookshelf

import android.graphics.BitmapFactory
import kotlin.math.max
import kotlin.math.roundToInt

internal object FolderCoverLuminance {
    const val DARK_ICON_THRESHOLD = 0.78

    fun usesDarkBadge(
        width: Int,
        height: Int,
        targetAspectRatio: Float = 0.75f,
        pixelAt: (x: Int, y: Int) -> Int
    ): Boolean {
        if (width <= 0 || height <= 0 || targetAspectRatio <= 0f) return false

        val sourceAspectRatio = width.toFloat() / height
        val cropLeft: Int
        val cropTop: Int
        val cropWidth: Int
        val cropHeight: Int
        if (sourceAspectRatio > targetAspectRatio) {
            cropHeight = height
            cropWidth = (height * targetAspectRatio).roundToInt().coerceIn(1, width)
            cropLeft = (width - cropWidth) / 2
            cropTop = 0
        } else {
            cropWidth = width
            cropHeight = (width / targetAspectRatio).roundToInt().coerceIn(1, height)
            cropLeft = 0
            cropTop = (height - cropHeight) / 2
        }

        val sampleWidth = max(1, (cropWidth * 0.2f).roundToInt())
        val sampleHeight = max(1, (cropHeight * 0.2f).roundToInt())
        val sampleLeft = cropLeft + cropWidth - sampleWidth
        val sampleTop = cropTop
        var luminanceTotal = 0.0
        var pixelCount = 0
        for (y in sampleTop until sampleTop + sampleHeight) {
            for (x in sampleLeft until sampleLeft + sampleWidth) {
                luminanceTotal += relativeLuminance(pixelAt(x, y))
                pixelCount++
            }
        }
        return pixelCount > 0 && luminanceTotal / pixelCount >= DARK_ICON_THRESHOLD
    }

    fun decodeUsesDarkBadge(path: String, targetAspectRatio: Float = 0.75f): Boolean? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sampleSize = 1
        while (max(bounds.outWidth, bounds.outHeight) / sampleSize > 256) {
            sampleSize *= 2
        }
        val bitmap = BitmapFactory.decodeFile(
            path,
            BitmapFactory.Options().apply { inSampleSize = sampleSize }
        ) ?: return null
        return try {
            usesDarkBadge(bitmap.width, bitmap.height, targetAspectRatio, bitmap::getPixel)
        } finally {
            bitmap.recycle()
        }
    }

    private fun relativeLuminance(pixel: Int): Double {
        val alpha = ((pixel ushr 24) and 0xFF) / 255.0
        val red = (((pixel ushr 16) and 0xFF) * alpha + 255.0 * (1.0 - alpha)) / 255.0
        val green = (((pixel ushr 8) and 0xFF) * alpha + 255.0 * (1.0 - alpha)) / 255.0
        val blue = ((pixel and 0xFF) * alpha + 255.0 * (1.0 - alpha)) / 255.0
        return 0.2126 * linearize(red) + 0.7152 * linearize(green) + 0.0722 * linearize(blue)
    }

    private fun linearize(channel: Double): Double =
        if (channel <= 0.04045) channel / 12.92 else Math.pow((channel + 0.055) / 1.055, 2.4)
}
