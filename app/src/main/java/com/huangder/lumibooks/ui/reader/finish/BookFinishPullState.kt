package com.huangder.lumibooks.ui.reader.finish

import android.os.SystemClock
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.pointer.util.VelocityTracker1D

/**
 * A finger pulling the finish page in from the last page of the book.
 *
 * Reader views keep ownership of the touch stream and only report how far the finger moved in
 * the forward reading direction; the overlay follows [distancePx] and settles on [release].
 */
@Stable
class BookFinishPullState {
    var distancePx by mutableFloatStateOf(0f)
        private set
    var dragging by mutableStateOf(false)
        private set
    /** Increments on every release so the overlay can react exactly once. */
    var releaseSerial by mutableIntStateOf(0)
        private set
    var releaseCancelled = false
        private set
    /** Forward velocity at release, in px/s; positive means "keep going". */
    var releaseVelocity = 0f
        private set
    private val tracker = VelocityTracker1D(isDataDifferential = false)

    fun update(distance: Float) {
        if (!dragging) {
            tracker.resetTracking()
            dragging = true
        }
        val clamped = distance.coerceAtLeast(0f)
        tracker.addDataPoint(SystemClock.uptimeMillis(), clamped)
        distancePx = clamped
    }

    fun release(cancelled: Boolean) {
        if (!dragging) return
        releaseVelocity = runCatching { tracker.calculateVelocity() }.getOrDefault(0f)
        releaseCancelled = cancelled
        dragging = false
        releaseSerial++
    }
}
