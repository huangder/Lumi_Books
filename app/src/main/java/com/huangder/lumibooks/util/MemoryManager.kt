package com.ebook.reader.util

import android.app.ActivityManager
import android.content.Context
import android.graphics.Bitmap
import android.os.Debug
import android.util.Log
import java.lang.ref.WeakReference

object MemoryManager {
    private const val TAG = "MemoryManager"
    private val bitmapCache = mutableMapOf<String, WeakReference<Bitmap>>()

    fun getMemoryInfo(context: Context): MemoryInfo {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)

        val runtime = Runtime.getRuntime()
        val usedMemory = runtime.totalMemory() - runtime.freeMemory()
        val maxMemory = runtime.maxMemory()

        return MemoryInfo(
            totalMemory = memoryInfo.totalMem,
            availableMemory = memoryInfo.availMem,
            usedMemory = usedMemory,
            maxMemory = maxMemory,
            isLowMemory = memoryInfo.lowMemory
        )
    }

    fun logMemoryUsage(context: Context) {
        val info = getMemoryInfo(context)
        Log.d(TAG, "Memory Usage:")
        Log.d(TAG, "  Total: ${formatBytes(info.totalMemory)}")
        Log.d(TAG, "  Available: ${formatBytes(info.availableMemory)}")
        Log.d(TAG, "  Used: ${formatBytes(info.usedMemory)}")
        Log.d(TAG, "  Max: ${formatBytes(info.maxMemory)}")
        Log.d(TAG, "  Low Memory: ${info.isLowMemory}")
    }

    fun cacheBitmap(key: String, bitmap: Bitmap) {
        bitmapCache[key] = WeakReference(bitmap)
    }

    fun getCachedBitmap(key: String): Bitmap? {
        return bitmapCache[key]?.get()
    }

    fun clearBitmapCache() {
        bitmapCache.clear()
    }

    fun formatBytes(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
            else -> "${bytes / (1024 * 1024 * 1024)} GB"
        }
    }

    data class MemoryInfo(
        val totalMemory: Long,
        val availableMemory: Long,
        val usedMemory: Long,
        val maxMemory: Long,
        val isLowMemory: Boolean
    )
}
