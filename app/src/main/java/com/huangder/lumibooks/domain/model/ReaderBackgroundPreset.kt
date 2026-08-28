package com.huangder.lumibooks.domain.model

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import kotlin.math.abs

enum class ReaderBackgroundType {
    COLOR,
    IMAGE
}

data class ReaderBackgroundPreset(
    val id: String,
    val type: ReaderBackgroundType,
    val value: String,
    val dominantColor: Int? = null,
    val name: String = "",
    /** Original image remains untouched; this is the optional rendered copy. */
    val processedValue: String? = null,
    val processedBlurDp: Float? = null
) {
    val selectionKey: String get() = "custom:$id"

    fun displayName(index: Int): String = name.trim().ifBlank { "自定义${index + 1}" }
}

data class ReaderBackgroundImageSource(
    val path: String,
    val runtimeBlurDp: Float
)

/** Uses a baked blur only when it represents the exact blur requested by the theme. */
fun ReaderBackgroundPreset.resolveImageSource(requestedBlurDp: Float): ReaderBackgroundImageSource? {
    if (type != ReaderBackgroundType.IMAGE) return null
    val blurDp = requestedBlurDp.coerceIn(0f, 40f)
    val processedPath = processedValue
    val processedMatches = blurDp >= 0.01f &&
        processedPath != null &&
        processedBlurDp != null &&
        abs(processedBlurDp - blurDp) < 0.01f &&
        File(processedPath).isFile
    return if (processedMatches) {
        ReaderBackgroundImageSource(processedPath, 0f)
    } else {
        ReaderBackgroundImageSource(value, blurDp)
    }
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
                    put("name", preset.name)
                    preset.processedValue?.let { put("processedValue", it) }
                    preset.processedBlurDp?.let { put("processedBlurDp", it.toDouble()) }
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
                    val name = item.optString("name")
                    val processedValue = item.optString("processedValue").takeIf { it.isNotBlank() }
                    val processedBlurDp = if (item.has("processedBlurDp")) {
                        item.optDouble("processedBlurDp").toFloat()
                    } else {
                        null
                    }
                    if (id.isNotBlank() && value.isNotBlank() && type != null) {
                        add(ReaderBackgroundPreset(id, type, value, dominantColor, name, processedValue, processedBlurDp))
                    }
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }
}
