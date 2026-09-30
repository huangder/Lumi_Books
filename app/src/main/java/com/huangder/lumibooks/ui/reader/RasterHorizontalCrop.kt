package com.huangder.lumibooks.ui.reader

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

internal data class RasterHorizontalCrop(val left: Float = 0f, val right: Float = 1f) {
    val width: Float get() = right - left
    val isFull: Boolean get() = left == 0f && right == 1f

    fun sourceRect(width: Int, height: Int): Rect {
        val x0 = (left * width).roundToInt().coerceIn(0, width - 1)
        val x1 = (right * width).roundToInt().coerceIn(x0 + 1, width)
        return Rect(x0, 0, x1, height)
    }

    companion object { val FULL = RasterHorizontalCrop() }
}

data class RasterManualCrop(val leftPx: Int = 0, val rightPx: Int = 0) {
    fun normalized(): RasterManualCrop = RasterManualCrop(leftPx.coerceIn(0, 100000), rightPx.coerceIn(0, 100000))

    internal fun forWidth(pageWidth: Int): RasterHorizontalCrop {
        if (pageWidth <= 0) return RasterHorizontalCrop.FULL
        val left = leftPx.coerceIn(0, (pageWidth * 0.45f).toInt())
        val right = rightPx.coerceIn(0, (pageWidth * 0.45f).toInt())
        return RasterHorizontalCrop(left.toFloat() / pageWidth, 1f - right.toFloat() / pageWidth)
    }

    fun encode(): String = "${leftPx.coerceAtLeast(0)},${rightPx.coerceAtLeast(0)}"

    companion object {
        fun decode(value: String?): RasterManualCrop {
            val parts = value?.split(',') ?: return RasterManualCrop()
            if (parts.size != 2) return RasterManualCrop()
            return RasterManualCrop(parts[0].toIntOrNull() ?: 0, parts[1].toIntOrNull() ?: 0).normalized()
        }
    }
}

/** Detect only pale, nearly uniform side bands with a clear transition to page content. */
internal fun detectRasterHorizontalCrop(bitmap: Bitmap): RasterHorizontalCrop {
    val width = bitmap.width
    val height = bitmap.height
    if (width < 64 || height < 64) return RasterHorizontalCrop.FULL
    val rows = (0 until 192).map { index ->
        (height * index / 191).coerceIn(0, height - 1)
    }.distinct()
    val pixels = IntArray(width * rows.size)
    val row = IntArray(width)
    rows.forEachIndexed { index, y ->
        bitmap.getPixels(row, 0, width, 0, y, width, 1)
        row.copyInto(pixels, index * width)
    }

    fun reference(x: Int): IntArray {
        val channels = Array(3) { IntArray(rows.size) }
        rows.indices.forEach { y ->
            val color = pixels[y * width + x]
            channels[0][y] = Color.red(color)
            channels[1][y] = Color.green(color)
            channels[2][y] = Color.blue(color)
        }
        return channels.map { it.sorted()[it.size / 2] }.toIntArray()
    }

    fun matching(color: Int, ref: IntArray): Boolean =
        max(abs(Color.red(color) - ref[0]), max(abs(Color.green(color) - ref[1]), abs(Color.blue(color) - ref[2]))) <= 27

    fun sideMargin(fromLeft: Boolean): Int {
        val ref = reference(if (fromLeft) 0 else width - 1)
        if (ref.min() < 175 || ref.max() - ref.min() > 70) return 0
        val maxMargin = (width * 0.30f).toInt()
        val minMargin = max(2, (width * 0.015f).roundToInt())
        val edgeMatches = rows.indices.count { y ->
            matching(pixels[y * width + if (fromLeft) 0 else width - 1], ref)
        }
        if (edgeMatches < rows.size * 0.97f) return 0
        var boundary = -1
        var changed = 0
        for (distance in 0 until maxMargin) {
            val x = if (fromLeft) distance else width - 1 - distance
            val matches = rows.indices.count { y -> matching(pixels[y * width + x], ref) }
            changed = if (matches < rows.size * 0.90f) changed + 1 else 0
            if (changed >= 3) {
                boundary = distance - 2
                break
            }
        }
        if (boundary < minMargin) return 0
        for (distance in 0 until boundary) {
            val x = if (fromLeft) distance else width - 1 - distance
            val matches = rows.indices.count { y -> matching(pixels[y * width + x], ref) }
            if (matches < rows.size * 0.85f) return 0
        }
        // A small caption or rule can occupy too few rows to affect the uniformity score.
        // Require connected dark pixels to distinguish actual ink from isolated scan noise.
        for (y in rows.indices) {
            var previousDark = false
            for (distance in 0 until boundary) {
                val x = if (fromLeft) distance else width - 1 - distance
                val color = pixels[y * width + x]
                val dark = min(Color.red(color) - ref[0],
                    min(Color.green(color) - ref[1], Color.blue(color) - ref[2])) < -55
                if (dark && (previousDark || (y > 0 && run {
                    val above = pixels[(y - 1) * width + x]
                    min(Color.red(above) - ref[0],
                        min(Color.green(above) - ref[1], Color.blue(above) - ref[2])) < -55
                }))) return 0
                previousDark = dark
            }
        }
        val inside = (boundary + max(3, width / 50)).coerceAtMost(width - 1)
        val insideX = if (fromLeft) inside else width - 1 - inside
        val contrastingRows = rows.indices.count { y ->
            !matching(pixels[y * width + insideX], ref)
        }
        return if (contrastingRows >= rows.size * 0.18f) boundary else 0
    }

    val left = sideMargin(true)
    val right = sideMargin(false)
    if (left + right > width * 0.45f) return RasterHorizontalCrop.FULL
    return RasterHorizontalCrop(left.toFloat() / width, 1f - right.toFloat() / width)
}

internal suspend fun BitmapPageSource.detectPageHorizontalCrop(page: Int): RasterHorizontalCrop {
    val thumbnail = renderThumbnail(page, 512) ?: return RasterHorizontalCrop.FULL
    return withContext(Dispatchers.Default) {
        val sample = if (thumbnail.width > 512) {
            Bitmap.createScaledBitmap(thumbnail, 512, max(1, thumbnail.height * 512 / thumbnail.width), true)
        } else thumbnail
        try { detectRasterHorizontalCrop(sample) }
        finally { if (sample !== thumbnail) sample.recycle() }
    }
}

/** Saved ink stays in coordinates of the full, uncropped page. */
internal class RasterInkTransform(
    private val crop: RasterHorizontalCrop,
    private val fullRatio: Float,
    private val viewportWidth: Float,
    private val viewportHeight: Float,
    private val fitToViewport: Boolean
) {
    private fun fittedRect(ratio: Float): FloatArray {
        val width = min(viewportWidth, viewportHeight * ratio)
        val height = width / ratio
        return floatArrayOf((viewportWidth - width) / 2f, (viewportHeight - height) / 2f, width, height)
    }

    fun toDisplay(point: PdfInkPoint): PdfInkPoint {
        if (crop.isFull) return point
        if (!fitToViewport || fullRatio <= 0f) {
            return PdfInkPoint((point.x - crop.left) / crop.width, point.y)
        }
        val original = fittedRect(fullRatio)
        val cropped = fittedRect(fullRatio * crop.width)
        val sourceX = (point.x * viewportWidth - original[0]) / original[2]
        val sourceY = (point.y * viewportHeight - original[1]) / original[3]
        return PdfInkPoint(
            (cropped[0] + (sourceX - crop.left) / crop.width * cropped[2]) / viewportWidth,
            (cropped[1] + sourceY * cropped[3]) / viewportHeight
        )
    }

    fun toOriginal(point: PdfInkPoint): PdfInkPoint {
        if (crop.isFull) return point
        if (!fitToViewport || fullRatio <= 0f) {
            return PdfInkPoint(crop.left + point.x * crop.width, point.y)
        }
        val original = fittedRect(fullRatio)
        val cropped = fittedRect(fullRatio * crop.width)
        val sourceX = crop.left + (point.x * viewportWidth - cropped[0]) / cropped[2] * crop.width
        val sourceY = (point.y * viewportHeight - cropped[1]) / cropped[3]
        return PdfInkPoint(
            (original[0] + sourceX * original[2]) / viewportWidth,
            (original[1] + sourceY * original[3]) / viewportHeight
        )
    }
}
