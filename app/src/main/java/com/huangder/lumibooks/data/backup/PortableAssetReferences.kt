package com.huangder.lumibooks.data.backup

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

private val assetReference = Regex("asset://([a-f0-9]{64}(?:-[a-f0-9]{16})?)(?![A-Za-z0-9_-])")

internal fun mapPortableString(value: String, transform: (String) -> String): String {
    var changed = false
    fun visit(item: Any?): Any? = when (item) {
        is JSONObject -> item.apply {
            keys().asSequence().toList().forEach { key -> put(key, visit(get(key))) }
        }
        is JSONArray -> item.apply {
            for (index in 0 until length()) put(index, visit(get(index)))
        }
        is String -> transform(item).also { changed = changed || it != item }
        else -> item
    }

    val decoded = try {
        when (value.trimStart().firstOrNull()) {
            '{' -> JSONObject(value)
            '[' -> JSONArray(value)
            else -> return transform(value)
        }
    } catch (_: JSONException) {
        return transform(value)
    }
    val mapped = visit(decoded)
    return if (changed) mapped.toString() else value
}

internal fun PortablePreference.mapAssetReferences(mapId: (String) -> String?): PortablePreference {
    if (type != PortablePreference.TYPE_STRING) return this
    return copy(value = mapPortableString(value) { leaf ->
        assetReference.replace(leaf) { match ->
            mapId(match.groupValues[1]) ?: match.value
        }
    })
}

internal fun PortablePreference.assetReferenceIds(): Set<String> = buildSet {
    mapAssetReferences { id ->
        add(id)
        null
    }
}

internal fun portableAssetId(value: String): String? = assetReference.matchEntire(value)?.groupValues?.get(1)

internal fun PortableSnapshot.referencedAssetIds(): Set<String> = buildSet {
    books.forEach { book ->
        book.bodyAssetId?.let(::add)
        book.coverAssetId?.let(::add)
    }
    folders.forEach { folder -> folder.coverPath?.let(::portableAssetId)?.let(::add) }
    preferences.forEach { addAll(it.assetReferenceIds()) }
}
