package com.huangder.lumibooks.domain.model

import org.json.JSONArray
import org.json.JSONObject

/**
 * 用户导入的自定义字体条目。
 * @param id    UUID，同时作为文件名区分（custom_{id}.ttf）
 * @param path  字体文件的绝对路径
 */
data class CustomFontPreset(
    val id: String,
    val path: String,
    val name: String = ""
) {
    /** 字体类型 key，在 fontType 字段中使用 */
    val fontTypeKey: String get() = "custom:$id"

    /** 在 UI 中显示的名称；旧数据没有名称时继续显示兼容性的默认名称。 */
    fun displayName(fallbackName: String): String {
        val customName = name.trim()
        if (customName.isBlank()) return fallbackName
        val count = customName.codePointCount(0, customName.length)
        return customName.substring(0, customName.offsetByCodePoints(0, count.coerceAtMost(6)))
    }
}

object CustomFontPresetCodec {
    fun encode(presets: List<CustomFontPreset>): String {
        val array = JSONArray()
        presets.forEach { preset ->
            array.put(JSONObject().apply {
                put("id", preset.id)
                put("path", preset.path)
                put("name", preset.name)
            })
        }
        return array.toString()
    }

    fun decode(raw: String?): List<CustomFontPreset> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    val id = item.optString("id")
                    val path = item.optString("path")
                    val name = item.optString("name")
                    if (id.isNotBlank() && path.isNotBlank()) add(CustomFontPreset(id, path, name))
                }
            }
        } catch (_: Exception) { emptyList() }
    }
}
