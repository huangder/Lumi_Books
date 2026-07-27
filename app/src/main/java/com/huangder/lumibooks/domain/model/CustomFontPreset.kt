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
    val path: String
) {
    /** 字体类型 key，在 fontType 字段中使用 */
    val fontTypeKey: String get() = "custom:$id"

    /** 在 UI 中显示的名称，由外部根据列表下标计算（自定义1、自定义2…） */
    fun displayName(index: Int): String = "自定义${index + 1}"
}

object CustomFontPresetCodec {
    fun encode(presets: List<CustomFontPreset>): String {
        val array = JSONArray()
        presets.forEach { preset ->
            array.put(JSONObject().apply {
                put("id", preset.id)
                put("path", preset.path)
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
                    if (id.isNotBlank() && path.isNotBlank()) add(CustomFontPreset(id, path))
                }
            }
        } catch (_: Exception) { emptyList() }
    }
}
