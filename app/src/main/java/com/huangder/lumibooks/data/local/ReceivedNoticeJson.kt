package com.huangder.lumibooks.data.local

import com.huangder.lumibooks.domain.model.ReceivedNotice
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

internal fun decodeReceivedNotices(value: String?): List<ReceivedNotice> {
    if (value == null) return emptyList()
    val array = try {
        JSONArray(value)
    } catch (_: JSONException) {
        return emptyList()
    }
    return (0 until array.length()).mapNotNull { index ->
        val item = array.optJSONObject(index) ?: return@mapNotNull null
        val id = item.optString("id").takeIf { it.isNotBlank() } ?: return@mapNotNull null
        ReceivedNotice(
            id = id,
            title = item.optString("title"),
            message = item.optString("message"),
            receivedAt = item.optLong("receivedAt"),
            imageUrl = item.opt("imageUrl") as? String
        )
    }.sortedByDescending { it.receivedAt }
}

internal fun encodeReceivedNotices(notices: List<ReceivedNotice>): String = JSONArray().apply {
    notices.forEach { notice ->
        put(JSONObject().apply {
            put("id", notice.id)
            put("title", notice.title)
            put("message", notice.message)
            put("receivedAt", notice.receivedAt)
            put("imageUrl", notice.imageUrl)
        })
    }
}.toString()
