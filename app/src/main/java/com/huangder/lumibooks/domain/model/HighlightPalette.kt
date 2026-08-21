package com.huangder.lumibooks.domain.model

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** A named six-slot highlight palette. Null slots remain available for custom colors. */
data class HighlightPalette(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val colors: List<String?> = List(6) { null }
) {
    val normalizedColors: List<String?> get() = colors.take(6) + List((6 - colors.size).coerceAtLeast(0)) { null }
}

object HighlightPaletteCodec {
    fun encode(palettes: List<HighlightPalette>): String = JSONArray().apply {
        palettes.forEach { palette ->
            put(JSONObject().apply {
                put("id", palette.id)
                put("name", palette.name)
                put("colors", JSONArray().apply {
                    palette.normalizedColors.forEach { color ->
                        if (color == null) put(JSONObject.NULL) else put(color)
                    }
                })
            })
        }
    }.toString()

    fun decode(raw: String?): List<HighlightPalette> = runCatching {
        if (raw.isNullOrBlank()) return emptyList()
        val array = JSONArray(raw)
        buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val name = item.optString("name").trim()
                if (name.isBlank()) continue
                val colorsJson = item.optJSONArray("colors")
                val colors = buildList {
                    if (colorsJson != null) {
                        for (colorIndex in 0 until minOf(6, colorsJson.length())) {
                            val value = colorsJson.opt(colorIndex)
                            add(value as? String)
                        }
                    }
                }
                add(HighlightPalette(item.optString("id").ifBlank { UUID.randomUUID().toString() }, name, colors))
            }
        }
    }.getOrDefault(emptyList())
}
