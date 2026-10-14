package com.huangder.lumibooks.data.local

import com.huangder.lumibooks.domain.model.SelectionMenuSettings
import org.json.JSONArray
import org.json.JSONObject

internal fun decodeSelectionMenuSettings(raw: String?): SelectionMenuSettings {
    if (raw.isNullOrBlank()) return SelectionMenuSettings()
    return runCatching {
        val json = JSONObject(raw)
        val flags = json.optJSONObject("visibility") ?: json // Previous versions stored just the flags.
        val order = json.optJSONArray("order")
        SelectionMenuSettings.fromStored(
            order?.let { array -> List(array.length()) { array.optString(it) } }.orEmpty(),
            flags.keys().asSequence().mapNotNull { key ->
                (flags.opt(key) as? Boolean)?.let { key to it }
            }.toMap()
        )
    }.getOrDefault(SelectionMenuSettings())
}

internal fun encodeSelectionMenuSettings(settings: SelectionMenuSettings): String = JSONObject()
    .put("order", JSONArray(settings.order.map { it.key }))
    .put("visibility", JSONObject().apply {
        settings.order.forEach { put(it.key, settings.isVisible(it)) }
    }).toString()
