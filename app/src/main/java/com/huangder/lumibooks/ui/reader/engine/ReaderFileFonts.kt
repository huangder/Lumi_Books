package com.huangder.lumibooks.ui.reader.engine

import android.graphics.Typeface
import android.util.LruCache
import java.io.File

private data class ReaderFontFileKey(val path: String, val length: Long, val modified: Long)
private val fileFonts = LruCache<ReaderFontFileKey, ReaderFileFont>(8)

/** Share the file and its VF instances across pagination, page views and settings previews. */
internal fun readerFileFont(file: File): ReaderFileFont {
    val key = ReaderFontFileKey(file.absolutePath, file.length(), file.lastModified())
    return synchronized(fileFonts) {
        fileFonts.get(key) ?: ReaderFileFont(file).also { fileFonts.put(key, it) }
    }
}

internal class ReaderFileFont(private val file: File) {
    // A large static CJK font must not be copied into a byte array just to
    // find that it has no axes. Cache absence as well as a detected weight range.
    val weightRange: ClosedFloatingPointRange<Float>? by lazy {
        variableWeightRange(file)
    }

    private val faces = LruCache<Int, Typeface>(8)

    @Synchronized
    fun resolve(requestedWeight: Int): ResolvedReaderTypeface? {
        val weight = requestedWeight.coerceIn(100, 900)
        val range = weightRange
        // All static weights reuse the same face; bold is applied by the renderer.
        val key = range?.let { weight.toFloat().coerceIn(it).toInt() } ?: 0
        val face = faces.get(key) ?: runCatching {
            val builder = Typeface.Builder(file)
            if (range != null) {
                builder.setFontVariationSettings("'wght' $key").setWeight(key)
            }
            builder.build()
        }.getOrNull()?.also { faces.put(key, it) } ?: return null
        return ResolvedReaderTypeface(face, range == null && weight >= 600)
    }
}
