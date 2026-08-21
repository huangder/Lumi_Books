package com.huangder.lumibooks.domain.model

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** A named six-slot highlight palette. A null slot is intentionally left unset. */
data class HighlightPalette(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val colors: List<String?> = List(6) { null }
) {
    val normalizedColors: List<String?> get() = colors.take(6).let { it + List(6 - it.size) { null } }
}

object HighlightPaletteCodec {
    fun encode(palettes: List<HighlightPalette>): String {
        val array = JSONArray()
        palettes.forEach { palette ->
            array.put(
                JSONObject().apply {
                    put("id", palette.id)
                    put("name", palette.name)
                    put("colors", JSONArray().apply {
                        palette.normalizedColors.forEach { color ->
                            if (color == null) put(JSONObject.NULL) else put(color)
                        }
                    })
                }
            )
        }
        return array.toString()
    }

    fun decode(raw: String?): List<HighlightPalette> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
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
                                add(value.takeUnless { it == null || it == JSONObject.NULL } as? String)
                            }
                        }
                    }
                    add(
                        HighlightPalette(
                            id = item.optString("id").ifBlank { UUID.randomUUID().toString() },
                            name = name,
                            colors = colors
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }
}
