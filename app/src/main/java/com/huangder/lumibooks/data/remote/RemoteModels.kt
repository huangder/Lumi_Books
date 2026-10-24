package com.huangder.lumibooks.data.remote

import org.json.JSONArray
import org.json.JSONObject

enum class RemoteSourceType { WEBDAV, OPDS }

/** A user-configured remote library. The password is kept in [RemoteCredentialStore]. */
data class RemoteSource(
    val id: String,
    val type: RemoteSourceType,
    val name: String,
    val url: String,
    val username: String = "",
    val createdAt: Long = 0L
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("type", type.name)
        .put("name", name)
        .put("url", url)
        .put("username", username)
        .put("createdAt", createdAt)

    companion object {
        fun fromJson(json: JSONObject): RemoteSource? {
            val id = json.optString("id").takeIf(String::isNotBlank) ?: return null
            val type = runCatching { RemoteSourceType.valueOf(json.optString("type")) }.getOrNull()
                ?: return null
            val url = json.optString("url").takeIf(String::isNotBlank) ?: return null
            return RemoteSource(
                id = id,
                type = type,
                name = json.optString("name").ifBlank { url },
                url = url,
                username = json.optString("username"),
                createdAt = json.optLong("createdAt")
            )
        }
    }
}

/** One downloadable file of a remote book (OPDS entries often offer several formats). */
data class RemoteFormat(
    val url: String,
    /** Lower-case book extension understood by the importer, or null when unsupported. */
    val extension: String?,
    val mimeType: String? = null,
    val sizeBytes: Long = 0L,
    /** Human readable label for formats the app cannot import (for example AZW3). */
    val label: String? = null
) {
    val isSupported: Boolean get() = extension != null

    fun toJson(): JSONObject = JSONObject()
        .put("url", url)
        .put("extension", extension ?: JSONObject.NULL)
        .put("mimeType", mimeType ?: JSONObject.NULL)
        .put("sizeBytes", sizeBytes)
        .put("label", label ?: JSONObject.NULL)

    companion object {
        fun fromJson(json: JSONObject) = RemoteFormat(
            url = json.optString("url"),
            extension = json.optStringOrNull("extension"),
            mimeType = json.optStringOrNull("mimeType"),
            sizeBytes = json.optLong("sizeBytes"),
            label = json.optStringOrNull("label")
        )
    }
}

/** A book offered by a remote library: everything the details page and the download queue need. */
data class RemoteBookItem(
    val sourceId: String,
    /** Stable identity used to remember which remote books were already imported. */
    val key: String,
    val title: String,
    val authors: List<String> = emptyList(),
    val summary: String? = null,
    val publisher: String? = null,
    val language: String? = null,
    val published: String? = null,
    val categories: List<String> = emptyList(),
    val coverUrl: String? = null,
    val thumbnailUrl: String? = null,
    val formats: List<RemoteFormat> = emptyList(),
    /** WebDAV file name; OPDS books learn theirs from the download response. */
    val fileName: String? = null,
    /** WebDAV path relative to the library root, shown on the details page. */
    val remotePath: String? = null,
    val lastModified: Long = 0L
) {
    val supportedFormats: List<RemoteFormat> get() = formats.filter { it.isSupported }
    val preferredFormat: RemoteFormat? get() = supportedFormats.firstOrNull()
    val authorLabel: String get() = authors.joinToString(", ")

    fun toJson(): JSONObject = JSONObject()
        .put("sourceId", sourceId)
        .put("key", key)
        .put("title", title)
        .put("authors", JSONArray(authors))
        .put("summary", summary ?: JSONObject.NULL)
        .put("publisher", publisher ?: JSONObject.NULL)
        .put("language", language ?: JSONObject.NULL)
        .put("published", published ?: JSONObject.NULL)
        .put("categories", JSONArray(categories))
        .put("coverUrl", coverUrl ?: JSONObject.NULL)
        .put("thumbnailUrl", thumbnailUrl ?: JSONObject.NULL)
        .put("formats", JSONArray().apply { formats.forEach { put(it.toJson()) } })
        .put("fileName", fileName ?: JSONObject.NULL)
        .put("remotePath", remotePath ?: JSONObject.NULL)
        .put("lastModified", lastModified)

    companion object {
        fun fromJson(json: JSONObject): RemoteBookItem? {
            val key = json.optString("key").takeIf(String::isNotBlank) ?: return null
            val formats = json.optJSONArray("formats") ?: JSONArray()
            return RemoteBookItem(
                sourceId = json.optString("sourceId"),
                key = key,
                title = json.optString("title"),
                authors = json.optJSONArray("authors")?.strings().orEmpty(),
                summary = json.optStringOrNull("summary"),
                publisher = json.optStringOrNull("publisher"),
                language = json.optStringOrNull("language"),
                published = json.optStringOrNull("published"),
                categories = json.optJSONArray("categories")?.strings().orEmpty(),
                coverUrl = json.optStringOrNull("coverUrl"),
                thumbnailUrl = json.optStringOrNull("thumbnailUrl"),
                formats = (0 until formats.length()).mapNotNull { index ->
                    formats.optJSONObject(index)?.let(RemoteFormat::fromJson)
                },
                fileName = json.optStringOrNull("fileName"),
                remotePath = json.optStringOrNull("remotePath"),
                lastModified = json.optLong("lastModified")
            )
        }
    }
}

data class RemoteFolder(
    val title: String,
    val url: String,
    val subtitle: String? = null,
    val coverUrl: String? = null
)

/** One listing page of a remote library. */
data class RemotePage(
    val title: String,
    val url: String,
    val folders: List<RemoteFolder> = emptyList(),
    val books: List<RemoteBookItem> = emptyList(),
    val nextUrl: String? = null,
    /** OpenSearch URL template (with `{searchTerms}`) when the catalog supports search. */
    val searchTemplate: String? = null
)

internal fun JSONObject.optStringOrNull(name: String): String? =
    if (isNull(name)) null else optString(name).takeIf(String::isNotBlank)

internal fun JSONArray.strings(): List<String> =
    (0 until length()).mapNotNull { index -> optString(index).takeIf(String::isNotBlank) }
