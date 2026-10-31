package com.huangder.lumibooks.ui.reader

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.LayoutCoordinates

internal val LocalRasterInkPages =
    staticCompositionLocalOf<SnapshotStateMap<Int, LayoutCoordinates>?> { null }

/** Actual page canvas bounds in the document overlay, excluding list-item letterboxing. */
internal data class RasterDocumentInkPage(
    val origin: Offset,
    val width: Float,
    val height: Float,
    val crop: RasterHorizontalCrop
) {
    fun toOriginal(position: Offset) = PdfInkPoint(
        crop.left + ((position.x - origin.x) / width).coerceIn(0f, 1f) * crop.width,
        ((position.y - origin.y) / height).coerceIn(0f, 1f)
    )

    fun toDisplay(point: PdfInkPoint) = Offset(
        origin.x + (point.x - crop.left) / crop.width * width,
        origin.y + point.y * height
    )
}
