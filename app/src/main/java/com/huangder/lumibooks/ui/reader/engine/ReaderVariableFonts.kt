package com.huangder.lumibooks.ui.reader.engine

import android.content.Context
import android.graphics.Typeface
import android.graphics.fonts.Font
import android.graphics.fonts.FontFamily
import android.graphics.Paint
import android.graphics.text.TextRunShaper
import android.graphics.fonts.FontStyle
import android.os.Build
import android.util.LruCache
import androidx.core.content.res.ResourcesCompat
import com.huangder.lumibooks.R
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Read the OpenType axis, rather than treating every bold-capable font as variable. */
internal fun variableWeightRange(buffer: ByteBuffer): ClosedFloatingPointRange<Float>? = runCatching {
    val data = buffer.duplicate().order(ByteOrder.BIG_ENDIAN)
    readVariableWeightRange(data::getInt) { data.getShort(it).toInt() and 0xffff }
}.getOrNull()

/** Inspect only table records and axis metadata, even for very large CJK fonts. */
internal fun variableWeightRange(file: File): ClosedFloatingPointRange<Float>? = runCatching {
    RandomAccessFile(file, "r").use { data ->
        readVariableWeightRange(
            { data.seek(it.toLong()); data.readInt() },
            { data.seek(it.toLong()); data.readUnsignedShort() }
        )
    }
}.getOrNull()

private inline fun readVariableWeightRange(
    intAt: (Int) -> Int,
    unsignedShortAt: (Int) -> Int
): ClosedFloatingPointRange<Float>? {
    val base = if (intAt(0) == 0x74746366) intAt(12) else 0 // First face of a TTC.
    val tables = unsignedShortAt(base + 4)
    for (i in 0 until tables) {
        val record = base + 12 + i * 16
        if (intAt(record) != 0x66766172) continue // fvar
        val offset = intAt(record + 8)
        val axesStart = unsignedShortAt(offset + 4)
        val count = unsignedShortAt(offset + 8)
        val size = unsignedShortAt(offset + 10)
        if (size < 20) return null
        for (axis in 0 until count) {
            val a = offset + axesStart + axis * size
            if (intAt(a) != 0x77676874) continue // wght
            val min = intAt(a + 4) / 65536f
            val max = intAt(a + 12) / 65536f
            return if (min >= 1f && max <= 1000f && max > min) min..max else null
        }
    }
    return null
}

private data class WeightAxis(val range: ClosedFloatingPointRange<Float>?)
private val axisCache = LruCache<String, WeightAxis>(32)

internal fun readerVariableWeightRange(context: Context, fontType: String,
    customFontPath: String?): ClosedFloatingPointRange<Float>? {
    if (fontType.startsWith("custom") && customFontPath != null) {
        return readerFileFont(File(customFontPath)).weightRange
    }
    val file = customFontPath?.let(::File)
    val key = "$fontType:${file?.absolutePath}:${file?.length()}:${file?.lastModified()}"
    synchronized(axisCache) { axisCache.get(key)?.let { return it.range } }
    val range = runCatching {
    when {
        fontType == "serif" -> context.resources.openRawResource(R.font.source_serif4_regular).use {
            variableWeightRange(ByteBuffer.wrap(it.readBytes()))
        }
        fontType in listOf("system", "sans_serif", "monospace") && Build.VERSION.SDK_INT >= 31 -> {
            val name = if (fontType == "monospace") "monospace" else "sans-serif"
            val glyphs = TextRunShaper.shapeTextRun("Aa", 0, 2, 0, 2, 0f, 0f, false,
                Paint().apply { typeface = Typeface.create(name, Typeface.NORMAL) })
            val font = glyphs.getFont(0)
            variableWeightRange(font.buffer)
        }
        else -> null
    }
    }.getOrNull()
    synchronized(axisCache) { axisCache.put(key, WeightAxis(range)) }
    return range
}

private val serifCache = LruCache<String, Typeface>(16)

/** Real VF instances, with matching italic and bold faces for StyleSpan/bionic text. */
internal fun readerSerifTypeface(context: Context, requestedWeight: Int = 400): Typeface {
    val weight = requestedWeight.coerceIn(200, 900)
    val cacheKey = weight.toString()
    synchronized(serifCache) {
        serifCache.get(cacheKey)?.let { return it }
        val face = if (Build.VERSION.SDK_INT >= 29) {
            var family: FontFamily.Builder? = null
            for (w in setOf(weight, 700)) for (italic in listOf(false, true)) {
                val font = Font.Builder(context.resources,
                    if (italic) R.font.source_serif4_italic else R.font.source_serif4_regular)
                    .setFontVariationSettings("'wght' $w")
                    .setWeight(w)
                    .setSlant(if (italic) FontStyle.FONT_SLANT_ITALIC else FontStyle.FONT_SLANT_UPRIGHT)
                    .build()
                if (family == null) family = FontFamily.Builder(font) else family.addFont(font)
            }
            // Missing CJK glyphs use the phone's serif family.
            Typeface.CustomFallbackBuilder(family!!.build())
                .setSystemFallback("serif")
                .setStyle(FontStyle(weight, FontStyle.FONT_SLANT_UPRIGHT)).build()
        } else {
            val resource = android.util.TypedValue()
            context.resources.getValue(R.font.source_serif4_regular, resource, true)
            Typeface.Builder(context.assets, resource.string.toString())
                .setFontVariationSettings("'wght' $weight").setWeight(weight)
                .setFallback("serif").build()
                ?: ResourcesCompat.getFont(context, R.font.source_serif4) ?: Typeface.SERIF
        }
        serifCache.put(cacheKey, face)
        return face
    }
}
