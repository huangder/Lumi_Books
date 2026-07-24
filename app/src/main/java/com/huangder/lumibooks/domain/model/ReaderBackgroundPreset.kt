package com.huangder.lumibooks.domain.model

import org.json.JSONArray
import org.json.JSONObject

enum class ReaderBackgroundType {
    COLOR,
    IMAGE
}

data class ReaderBackgroundPreset(
    val id: String,
    val type: ReaderBackgroundType,
    val value: String,
    val dominantColor: Int? = null
) {
    val selectionKey: String get() = "custom:$id"
}

object ReaderBackgroundPresetCodec {
    fun encode(presets: List<ReaderBackgroundPreset>): String {
        val array = JSONArray()
        presets.forEach { preset ->
            array.put(
                JSONObject().apply {
                    put("id", preset.id)
                    put("type", preset.type.name)
                    put("value", preset.value)
                    preset.dominantColor?.let { put("dominantColor", it) }
                }
            )
        }
        return array.toString()
    }

    fun decode(raw: String?): List<ReaderBackgroundPreset> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val id = item.optString("id")
                    val value = item.optString("value")
                    val type = runCatching {
                        ReaderBackgroundType.valueOf(item.optString("type"))
                    }.getOrNull()
                    val dominantColor = if (item.has("dominantColor")) {
                        item.optInt("dominantColor")
                    } else {
                        null
                    }
                    if (id.isNotBlank() && value.isNotBlank() && type != null) {
                        add(ReaderBackgroundPreset(id, type, value, dominantColor))
                    }
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }
}
