package com.huangder.lumibooks.ui.reader

import android.net.Uri

internal const val RASTER_SETTLE_MS = 160L
internal const val RASTER_PERSIST_MS = 800L

internal data class RasterViewport(
    val visiblePages: Set<Int> = emptySet(),
    val anchor: Int = 0,
    val width: Int = 720,
    val spread: Boolean = false,
    val scrolling: Boolean = false,
    // Includes raw-delta scrolling and graphics-layer pan/zoom, which don't set isScrollInProgress.
    val positions: List<Float> = emptyList()
)

internal fun rasterCompleteSpreads(pages: Iterable<Int>, pageCount: Int, spread: Boolean): List<Int> =
    pages.filter { it in 0 until pageCount }.flatMap { page ->
        if (!spread) listOf(page) else {
            val index = CbzSpreadPlanner.spreadIndexOfPage(page, pageCount, true)
            val pair = CbzSpreadPlanner.spreadFor(index, pageCount, true)
            listOfNotNull(pair?.firstPage, pair?.secondPage)
        }
    }.distinct()

internal fun rasterResumeWindow(anchor: Int, pageCount: Int, spread: Boolean): List<Int> =
    rasterCompleteSpreads(listOf(anchor, anchor + 1, anchor - 1, anchor + 2, anchor - 2), pageCount, spread)

internal fun rasterPrefetchWindow(viewport: RasterViewport, direction: Int, pageCount: Int): List<Int> {
    val visible = viewport.visiblePages.ifEmpty { setOf(viewport.anchor) }
    val forward = if (direction >= 0) visible.max() else visible.min()
    val backward = if (direction >= 0) visible.min() else visible.max()
    val step = if (direction >= 0) 1 else -1
    return rasterCompleteSpreads(listOf(forward + step, forward + 2 * step, backward - step), pageCount, viewport.spread)
        .filter { it !in visible }
}

internal data class RasterDimensions(val width: Int, val height: Int) {
    val ratio: Float get() = width.toFloat() / height.coerceAtLeast(1)
}

internal data class RasterTileSource(val uri: Uri, val dimensions: RasterDimensions)

/** Physical output identity, deliberately independent of the caller's quality label. */
internal data class RasterDecodeSpec(val page: Int, val width: Int, val height: Int, val sample: Int = 1)

internal fun rasterPreviewSample(width: Int, height: Int, target: Int): Int {
    var sample = 1
    while (sample < (1 shl 29) && width / (sample * 2) >= target.coerceAtLeast(1)) sample *= 2
    while (sample < (1 shl 29) && (width.toLong() / sample) * (height.toLong() / sample) > 512_000L) sample *= 2
    return sample
}
