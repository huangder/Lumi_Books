package com.huangder.lumibooks.ui.reader

import android.os.SystemClock
import com.huangder.lumibooks.BuildConfig

/** Measures the first Compose draw containing an image, not just decode completion. */
internal class RasterLoadMetrics {
    private data class Sample(val enteredAt: Long, var first: Boolean = false, var full: Boolean = false)
    private val visible = mutableMapOf<Int, Sample>()

    @Synchronized fun viewport(pages: Set<Int>) {
        if (!BuildConfig.DEBUG) return
        visible.keys.retainAll(pages)
        pages.forEach { visible.getOrPut(it) { Sample(SystemClock.elapsedRealtime()) } }
    }

    @Synchronized fun drawn(page: Int, finalQuality: Boolean) {
        if (!BuildConfig.DEBUG) return
        val sample = visible[page] ?: return
        val elapsed = SystemClock.elapsedRealtime() - sample.enteredAt
        if (!sample.first) {
            sample.first = true
            android.util.Log.d("RasterLoad", "draw page=$page stage=first elapsedMs=$elapsed")
        }
        if (finalQuality && !sample.full) {
            sample.full = true
            android.util.Log.d("RasterLoad", "draw page=$page stage=full elapsedMs=$elapsed")
        }
    }
}
