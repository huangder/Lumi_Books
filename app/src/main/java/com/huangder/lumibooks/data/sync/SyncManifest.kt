package com.huangder.lumibooks.data.sync

import org.json.JSONObject

/** File metadata for a single synced item. */
data class SyncFileEntry(
    val fileName: String,
    val sha256: String,
    val sizeBytes: Long,
    val lastModified: Long,
    val metadata: SyncBookMetadata? = null,
    val cover: SyncFileEntry? = null
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("fileName", fileName)
        put("sha256", sha256)
        put("sizeBytes", sizeBytes)
        put("lastModified", lastModified)
        metadata?.let { put("metadata", it.toJson()) }
        cover?.let { put("cover", it.toJson()) }
    }

    companion object {
        fun fromJson(json: JSONObject): SyncFileEntry = SyncFileEntry(
            fileName = json.getString("fileName"),
            sha256 = json.getString("sha256"),
            sizeBytes = json.getLong("sizeBytes"),
            lastModified = json.getLong("lastModified"),
            metadata = json.optJSONObject("metadata")?.let(SyncBookMetadata::fromJson),
            cover = json.optJSONObject("cover")?.let(::fromJson)
        )
    }
}

data class SyncBookMetadata(
    val title: String,
    val author: String,
    val format: String,
    val createdAt: Long,
    val isFavorite: Boolean,
    val updatedAt: Long
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("title", title)
        put("author", author)
        put("format", format)
        put("createdAt", createdAt)
        put("isFavorite", isFavorite)
        put("updatedAt", updatedAt)
    }

    companion object {
        fun fromJson(json: JSONObject): SyncBookMetadata = SyncBookMetadata(
            title = json.optString("title"),
            author = json.optString("author"),
            format = json.optString("format", "TXT"),
            createdAt = json.optLong("createdAt"),
            isFavorite = json.optBoolean("isFavorite"),
            updatedAt = json.optLong("updatedAt", json.optLong("createdAt"))
        )
    }
}

data class DeletedBookEntry(
    val deletedAt: Long,
    val fileName: String?,
    val coverFileName: String?,
    val dataFileName: String?
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("deletedAt", deletedAt)
        fileName?.let { put("fileName", it) }
        coverFileName?.let { put("coverFileName", it) }
        dataFileName?.let { put("dataFileName", it) }
    }

    companion object {
        fun fromJson(json: JSONObject): DeletedBookEntry = DeletedBookEntry(
            deletedAt = json.optLong("deletedAt"),
            fileName = json.nullableString("fileName"),
            coverFileName = json.nullableString("coverFileName"),
            dataFileName = json.nullableString("dataFileName")
        )
    }
}

/**
 * Manifest stored at `{syncPath}/manifest.json` on the WebDAV server.
 * Used for fast diff — only transfer changed files.
 */
data class SyncManifest(
    val version: Int = CURRENT_VERSION,
    val books: Map<String, SyncFileEntry> = emptyMap(),   // bookId → book file entry
    val data: Map<String, SyncFileEntry> = emptyMap(),    // bookId → reading data entry
    val deletedBooks: Map<String, DeletedBookEntry> = emptyMap()
) {
    fun toJson(): String = JSONObject().apply {
        put("version", version)
        put("books", JSONObject().apply {
            for ((k, v) in books) put(k, v.toJson())
        })
        put("data", JSONObject().apply {
            for ((k, v) in data) put(k, v.toJson())
        })
        put("deletedBooks", JSONObject().apply {
            for ((k, v) in deletedBooks) put(k, v.toJson())
        })
    }.toString(2)

    companion object {
        const val CURRENT_VERSION = 2

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
            fun readDeletedBooks(): Map<String, DeletedBookEntry> {
                val obj = root.optJSONObject("deletedBooks") ?: return emptyMap()
                val map = mutableMapOf<String, DeletedBookEntry>()
                for (k in obj.keys()) {
                    map[k] = DeletedBookEntry.fromJson(obj.getJSONObject(k))
                }
                return map
            }
            return SyncManifest(
                version = root.optInt("version", 1),
                books = readMap("books"),
                data = readMap("data"),
                deletedBooks = readDeletedBooks()
            )
        }
    }
}

private fun JSONObject.nullableString(key: String): String? =
    takeIf { has(key) && !isNull(key) }
        ?.optString(key)
        ?.takeIf { it.isNotBlank() }

internal fun shouldApplyRemoteMetadata(
    localMetadataUpdatedAt: Long?,
    remoteMetadata: SyncBookMetadata?
): Boolean = localMetadataUpdatedAt == null ||
    (remoteMetadata != null && remoteMetadata.updatedAt > localMetadataUpdatedAt)

internal fun SyncManifest.activeRemoteBooks(): Map<String, SyncFileEntry> =
    books.filterKeys { it !in deletedBooks }
