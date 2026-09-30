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
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Read the OpenType axis, rather than treating every bold-capable font as variable. */
internal fun variableWeightRange(buffer: ByteBuffer): ClosedFloatingPointRange<Float>? = runCatching {
    val data = buffer.duplicate().order(ByteOrder.BIG_ENDIAN)
    val base = if (data.getInt(0) == 0x74746366) data.getInt(12) else 0 // First face of a TTC.
    val tables = data.getShort(base + 4).toInt() and 0xffff
    for (i in 0 until tables) {
        val record = base + 12 + i * 16
        if (data.getInt(record) != 0x66766172) continue // fvar
        val offset = data.getInt(record + 8)
        val axesStart = data.getShort(offset + 4).toInt() and 0xffff
        val count = data.getShort(offset + 8).toInt() and 0xffff
        val size = data.getShort(offset + 10).toInt() and 0xffff
        if (size < 20) return@runCatching null
        for (axis in 0 until count) {
            val a = offset + axesStart + axis * size
            if (data.getInt(a) != 0x77676874) continue // wght
            val min = data.getInt(a + 4) / 65536f
            val max = data.getInt(a + 12) / 65536f
            return@runCatching if (min >= 1f && max <= 1000f && max > min) min..max else null
        }
    }
    null
}.getOrNull()

private data class WeightAxis(val range: ClosedFloatingPointRange<Float>?)
private val axisCache = LruCache<String, WeightAxis>(32)

internal fun readerVariableWeightRange(context: Context, fontType: String,
    customFontPath: String?): ClosedFloatingPointRange<Float>? {
    val file = customFontPath?.let(::File)
    val key = "$fontType:${file?.absolutePath}:${file?.length()}:${file?.lastModified()}"
    synchronized(axisCache) { axisCache.get(key)?.let { return it.range } }
    val range = runCatching {
    when {
        fontType == "serif" -> context.resources.openRawResource(R.font.source_serif4_regular).use {
            variableWeightRange(ByteBuffer.wrap(it.readBytes()))
        }
        fontType.startsWith("custom") && customFontPath != null ->
            variableWeightRange(ByteBuffer.wrap(File(customFontPath).readBytes()))
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
