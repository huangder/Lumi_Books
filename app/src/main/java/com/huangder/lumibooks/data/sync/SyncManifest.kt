package com.huangder.lumibooks.data.sync

import org.json.JSONObject

/** File metadata for a single synced item. */
data class SyncFileEntry(
    val fileName: String,
    val sha256: String,
    val sizeBytes: Long,
    val lastModified: Long   // epoch millis
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("fileName", fileName)
        put("sha256", sha256)
        put("sizeBytes", sizeBytes)
        put("lastModified", lastModified)
    }

    companion object {
        fun fromJson(json: JSONObject): SyncFileEntry = SyncFileEntry(
            fileName = json.getString("fileName"),
            sha256 = json.getString("sha256"),
            sizeBytes = json.getLong("sizeBytes"),
            lastModified = json.getLong("lastModified")
        )
    }
}

/**
 * Manifest stored at `{syncPath}/manifest.json` on the WebDAV server.
 * Used for fast diff — only transfer changed files.
 */
data class SyncManifest(
    val books: Map<String, SyncFileEntry> = emptyMap(),   // bookId → book file entry
    val data: Map<String, SyncFileEntry> = emptyMap()     // bookId → reading data entry
) {
    fun toJson(): String = JSONObject().apply {
        put("books", JSONObject().apply {
            for ((k, v) in books) put(k, v.toJson())
        })
        put("data", JSONObject().apply {
            for ((k, v) in data) put(k, v.toJson())
        })
    }.toString(2)

    companion object {
        fun fromJson(jsonString: String): SyncManifest {
            if (jsonString.isBlank()) return SyncManifest()
            val root = JSONObject(jsonString)
            fun readMap(key: String): Map<String, SyncFileEntry> {
                val obj = root.optJSONObject(key) ?: return emptyMap()
                val map = mutableMapOf<String, SyncFileEntry>()
                for (k in obj.keys()) {
                    map[k] = SyncFileEntry.fromJson(obj.getJSONObject(k))
                }
                return map
            }
            return SyncManifest(
                books = readMap("books"),
                data = readMap("data")
            )
        }
    }
}
