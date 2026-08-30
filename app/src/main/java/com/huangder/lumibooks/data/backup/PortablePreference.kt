package com.huangder.lumibooks.data.backup

import org.json.JSONObject

data class PortablePreference(
    val key: String,
    val type: String,
    val value: String,
    val updatedAt: Long,
    val deviceId: String
) {
    val deleted: Boolean get() = type == TYPE_DELETED

    fun toJson(): JSONObject = JSONObject().apply {
        put("key", key)
        put("type", type)
        put("value", value)
        put("updatedAt", updatedAt)
        put("deviceId", deviceId)
    }

    companion object {
        const val TYPE_BOOLEAN = "boolean"
        const val TYPE_INT = "int"
        const val TYPE_LONG = "long"
        const val TYPE_FLOAT = "float"
        const val TYPE_DOUBLE = "double"
        const val TYPE_STRING = "string"
        const val TYPE_STRING_SET = "string_set"
        const val TYPE_DELETED = "deleted"

        fun fromJson(json: JSONObject) = PortablePreference(
            key = json.getString("key"),
            type = json.getString("type"),
            value = json.optString("value"),
            updatedAt = json.optLong("updatedAt"),
            deviceId = json.optString("deviceId")
        )
    }
}
