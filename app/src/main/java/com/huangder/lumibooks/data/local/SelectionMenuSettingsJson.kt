package com.huangder.lumibooks.data.local

import com.huangder.lumibooks.domain.model.SelectionMenuAction
import com.huangder.lumibooks.domain.model.SelectionMenuSettings
import org.json.JSONArray
import org.json.JSONObject

/*
 * Persisted form of the selection menu (DataStore key "selection_menu_items").
 *
 * Current format: {"order": ["copy", "note", ...], "visibility": {"copy": true, ...}}
 * Earlier releases stored only visibility as a flat object: {"copy": true, "note": false, ...}
 * and always used the default order. Both are accepted on read; only the current one is written.
 */

private const val ORDER_FIELD = "order"
private const val VISIBILITY_FIELD = "visibility"

fun encodeSelectionMenuSettings(settings: SelectionMenuSettings): String {
    val visibility = JSONObject()
    SelectionMenuAction.entries.forEach { visibility.put(it.storageKey, settings.isVisible(it)) }
    return JSONObject()
        .put(ORDER_FIELD, JSONArray(settings.order.map(SelectionMenuAction::storageKey)))
        .put(VISIBILITY_FIELD, visibility)
        .toString()
}

/** Never throws: missing, empty or unreadable data yields the default settings. */
fun decodeSelectionMenuSettings(raw: String?): SelectionMenuSettings {
    if (raw.isNullOrBlank()) return SelectionMenuSettings()
    val root = runCatching { JSONObject(raw) }.getOrNull() ?: return SelectionMenuSettings()
    val orderArray = root.optJSONArray(ORDER_FIELD)
    val visibilityObject = root.optJSONObject(VISIBILITY_FIELD)
    val isCurrentFormat = orderArray != null || visibilityObject != null
    val order = orderArray?.let(::readOrder).orEmpty()
    val hidden = readHidden(if (isCurrentFormat) visibilityObject else root)
    return SelectionMenuSettings.create(order, hidden)
}

/** Unknown keys are skipped; duplicates and missing actions are resolved by the model. */
private fun readOrder(array: JSONArray): List<SelectionMenuAction> =
    (0 until array.length()).mapNotNull { index ->
        (array.opt(index) as? String)?.let(SelectionMenuAction::fromStorageKey)
    }

/** Only real booleans count; an absent or malformed entry keeps the action visible. */
private fun readHidden(visibility: JSONObject?): Set<SelectionMenuAction> {
    if (visibility == null) return emptySet()
    return SelectionMenuAction.entries.filterTo(mutableSetOf()) { action ->
        visibility.opt(action.storageKey) == false
    }
}
