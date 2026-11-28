package com.huangder.lumibooks.ui.reader

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.layout.onLayoutRectChanged
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.huangder.lumibooks.domain.model.PageRenderMode
import com.huangder.lumibooks.domain.model.ReaderImageAdjustments
import kotlin.math.ceil
import kotlin.math.roundToInt

private const val PDF_TILE_SIZE = 768

/** Coordinates in a virtual, uncropped full-page bitmap. Only the requested tile is allocated. */
internal data class PdfTile(
    val page: Int,
    val pageWidth: Int,
    val left: Int,
    val top: Int,
    val width: Int,
    val height: Int
)

internal fun visiblePdfTiles(
    page: Int,
    pageBounds: Rect,
    viewport: Rect,
    fullRatio: Float,
    crop: RasterHorizontalCrop,
    fallbackWidth: Int,
    mode: PageRenderMode
): List<PdfTile> {
    val visible = pageBounds.intersect(viewport)
    if (visible.isEmpty || fallbackWidth * crop.width >= pageBounds.width) return emptyList()
    val density = when (mode) {
        PageRenderMode.NORMAL -> 1f
        PageRenderMode.HIGH -> 1.25f
        PageRenderMode.NATIVE -> 1.5f
    }
    val pageWidth = pageRenderTargetWidth(ceil(pageBounds.width / crop.width * density).toInt())
    val pageHeight = ceil(pageWidth / fullRatio).toInt()
    val croppedLeft = pageWidth * crop.left
    val croppedWidth = pageWidth * crop.width
    val left = croppedLeft + (visible.left - pageBounds.left) / pageBounds.width * croppedWidth
    val right = croppedLeft + (visible.right - pageBounds.left) / pageBounds.width * croppedWidth
    val top = (visible.top - pageBounds.top) / pageBounds.height * pageHeight
    val bottom = (visible.bottom - pageBounds.top) / pageBounds.height * pageHeight
    return buildList {
        for (y in tileOrigins(top, bottom, pageHeight)) {
            for (x in tileOrigins(left, right, pageWidth)) {
                add(PdfTile(page, pageWidth, x, y, minOf(PDF_TILE_SIZE, pageWidth - x),
                    minOf(PDF_TILE_SIZE, pageHeight - y)))
            }
        }
    }
}

private fun tileOrigins(start: Float, end: Float, limit: Int): IntProgression =
    ((start.toInt() / PDF_TILE_SIZE) * PDF_TILE_SIZE) until
        ceil(end).toInt().coerceAtMost(limit) step PDF_TILE_SIZE

/** The viewport-sized set of tiles moves over the existing whole-page fallback during scrolling. */
@Composable
internal fun PdfTileLayer(
    source: BitmapPageSource,
    page: Int,
    fullRatio: Float,
    crop: RasterHorizontalCrop,
    fitToViewport: Boolean,
    fallbackWidth: Int,
    mode: PageRenderMode,
    adjustments: ReaderImageAdjustments,
    modifier: Modifier
) {
    var rootBounds by remember { mutableStateOf(Rect.Zero) }
    val window = LocalWindowInfo.current.containerSize
    val visibleTiles = visiblePdfTiles(
        page, pdfDrawBounds(rootBounds, fullRatio * crop.width, fitToViewport),
        Rect(0f, 0f, window.width.toFloat(), window.height.toFloat()),
        fullRatio, crop, fallbackWidth, mode
    )
    val colorFilter = remember(adjustments) {
        ColorFilter.colorMatrix(ColorMatrix(adjustments.colorMatrixValues()))
    }
    Box(modifier.onLayoutRectChanged(throttleMillis = 16, debounceMillis = 0) { bounds ->
        val rect = bounds.boundsInRoot
        rootBounds = Rect(rect.left.toFloat(), rect.top.toFloat(), rect.right.toFloat(), rect.bottom.toFloat())
    }) {
        visibleTiles.forEach { tile ->
            key(source, tile) {
                var bitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
                LaunchedEffect(Unit) { bitmap = source.renderPdfTile(tile) }
                val image = rememberSharpenedBitmap(bitmap, adjustments.sharpen)?.asImageBitmap()
                if (image != null) {
                    Canvas(Modifier.matchParentSize()) {
                        val pageBounds = pdfDrawBounds(Rect(0f, 0f, size.width, size.height),
                            fullRatio * crop.width, fitToViewport)
                        val scaleX = pageBounds.width / (tile.pageWidth * crop.width)
                        val scaleY = pageBounds.height / ceil(tile.pageWidth / fullRatio)
                        val originX = pageBounds.left - tile.pageWidth * crop.left * scaleX
                        val left = (originX + tile.left * scaleX).roundToInt()
                        val right = (originX + (tile.left + tile.width) * scaleX).roundToInt()
                        val top = (pageBounds.top + tile.top * scaleY).roundToInt()
                        val bottom = (pageBounds.top + (tile.top + tile.height) * scaleY).roundToInt()
                        if (right > left && bottom > top) {
                            clipRect(pageBounds.left, pageBounds.top, pageBounds.right, pageBounds.bottom) {
                                drawImage(image, srcOffset = IntOffset(1, 1), srcSize = IntSize(tile.width, tile.height),
                                    dstOffset = IntOffset(left, top), dstSize = IntSize(right - left, bottom - top),
                                    colorFilter = colorFilter)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun pdfDrawBounds(bounds: Rect, ratio: Float, fitToViewport: Boolean): Rect {
    if (!fitToViewport) return bounds
    val width = minOf(bounds.width, bounds.height * ratio)
    val height = minOf(bounds.height, width / ratio)
    val left = bounds.left + (bounds.width - width) / 2
    val top = bounds.top + (bounds.height - height) / 2
    return Rect(left, top, left + width, top + height)
}
