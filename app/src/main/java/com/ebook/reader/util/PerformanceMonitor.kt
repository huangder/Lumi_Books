package com.ebook.reader.util

import android.util.Log

object PerformanceMonitor {
    private const val TAG = "PerformanceMonitor"
    private val timers = mutableMapOf<String, Long>()

    fun startTimer(label: String) {
        timers[label] = System.currentTimeMillis()
        Log.d(TAG, "Started timer: $label")
    }

    fun endTimer(label: String): Long {
        val startTime = timers[label] ?: return 0
        val duration = System.currentTimeMillis() - startTime
        timers.remove(label)
        Log.d(TAG, "Timer $label: ${duration}ms")
        return duration
    }

    fun measureTime(label: String, block: () -> Unit): Long {
        startTimer(label)
        block()
        return endTimer(label)
    }

    suspend fun measureTimeSuspend(label: String, block: suspend () -> Unit): Long {
        startTimer(label)
        block()
        return endTimer(label)
    }
}
