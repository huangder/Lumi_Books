package com.huangder.lumibooks.ui.reader

import android.graphics.Rect

/** A bounded display-list window for a TextView that measures an entire chapter. */
internal class ContinuousReaderDrawingWindow {
    val bounds = Rect()
    private val visible = Rect()

    /** Returns true only when the recorded content needs to be refreshed. */
    fun update(viewport: Rect, width: Int, height: Int): Boolean {
        visible.set(viewport)
        if (width <= 0 || height <= 0 || !visible.intersect(0, 0, width, height) || visible.isEmpty) {
            if (bounds.isEmpty) return false
            bounds.setEmpty()
            return true
        }
        if (bounds.right == width && bounds.bottom <= height &&
            bounds.height() <= visible.height() * 2 && bounds.contains(visible)) return false

        // Half a screen on either side lets the GPU reuse the display list while
        // scrolling. Both recording cost and retained commands stay screen-sized.
        val overscan = visible.height() / 2
        bounds.set(0, (visible.top - overscan).coerceAtLeast(0), width,
            (visible.bottom + overscan).coerceAtMost(height))
        return true
    }
}
