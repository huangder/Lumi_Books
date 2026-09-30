package com.huangder.lumibooks.highlight

import com.huangder.lumibooks.domain.model.RuleStyle
import org.json.JSONObject

object RuleStyleJson {
    fun encode(style: RuleStyle): String = JSONObject().apply {
        style.textColor?.let { put("textColor", it) }
        put("underlineMode", style.underlineMode)
        put("underlineOffset", style.underlineOffset.toDouble())
        put("underlineWidth", style.underlineWidth.toDouble())
        put("fontWeight", style.fontWeight)
        put("italic", style.italic)
        if (style.fontType.isNotBlank()) put("fontType", style.fontType)
    }.toString()

    fun decode(raw: String?): RuleStyle? = raw?.let {
        runCatching {
            val json = JSONObject(it)
            RuleStyle(
                textColor = if (json.has("textColor") && !json.isNull("textColor")) {
                    json.optInt("textColor")
                } else null,
                underlineMode = json.optInt("underlineMode"),
                underlineOffset = json.optDouble("underlineOffset", 2.0).toFloat(),
                underlineWidth = json.optDouble("underlineWidth", 1.0).toFloat(),
                fontWeight = json.optInt("fontWeight", 400),
                italic = json.optBoolean("italic"),
                fontType = json.optString("fontType")
            )
        }.getOrNull()
    }
}
