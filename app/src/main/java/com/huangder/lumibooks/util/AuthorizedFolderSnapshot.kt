package com.huangder.lumibooks.util

import org.json.JSONArray
import org.json.JSONObject

/**
 * Stable identity for a SAF document, independent of the tree URI that exposed it.
 * Kept in the util layer so both the snapshot codec and the import matching agree on it.
 */
fun authorizedSnapshotIdentity(documentKey: String?, documentUri: String): String =
    documentKey?.takeIf(String::isNotBlank)?.let { "document:$it" } ?: "uri:$documentUri"

/** One book file remembered inside an authorized folder. */
data class AuthorizedFolderDocumentSnapshot(
    val documentUri: String,
    val documentKey: String?,
    val parentDocumentUri: String?,
    val displayName: String,
    val relativeDirectory: String?,
    val size: Long,
    val lastModified: Long
) {
    val identity: String get() = authorizedSnapshotIdentity(documentKey, documentUri)
}

/** One directory of an authorized folder, used to rebuild physical storage bindings. */
data class AuthorizedFolderDirectorySnapshot(
    val documentUri: String,
    val parentDocumentUri: String?,
    val name: String,
    val relativePath: String?
)

/** Everything remembered about a single authorized tree. */
data class AuthorizedFolderTreeSnapshot(
    val treeUri: String,
    val rootName: String,
    val scannedAt: Long,
    val directories: List<AuthorizedFolderDirectorySnapshot>,
    val documents: List<AuthorizedFolderDocumentSnapshot>
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("treeUri", treeUri)
        put("rootName", rootName)
        put("scannedAt", scannedAt)
        put(
            "directories",
            JSONArray().apply {
                directories.forEach { directory ->
                    put(
                        JSONObject().apply {
                            put("documentUri", directory.documentUri)
                            putNullable("parentDocumentUri", directory.parentDocumentUri)
                            put("name", directory.name)
                            putNullable("relativePath", directory.relativePath)
                        }
                    )
                }
            }
        )
        put(
            "documents",
            JSONArray().apply {
                documents.forEach { document ->
                    put(
                        JSONObject().apply {
                            put("documentUri", document.documentUri)
                            putNullable("documentKey", document.documentKey)
                            putNullable("parentDocumentUri", document.parentDocumentUri)
                            put("displayName", document.displayName)
                            putNullable("relativeDirectory", document.relativeDirectory)
                            put("size", document.size)
                            put("lastModified", document.lastModified)
                        }
                    )
                }
            }
        )
    }

    companion object {
        fun fromJson(json: JSONObject): AuthorizedFolderTreeSnapshot = AuthorizedFolderTreeSnapshot(
            treeUri = json.optString("treeUri"),
            rootName = json.optString("rootName"),
            scannedAt = json.optLong("scannedAt"),
            directories = json.optJSONArray("directories").mapDirectories { item ->
                AuthorizedFolderDirectorySnapshot(
                    documentUri = item.optString("documentUri"),
                    parentDocumentUri = item.nullableString("parentDocumentUri"),
                    name = item.optString("name"),
                    relativePath = item.nullableString("relativePath")
                )
            },
            documents = json.optJSONArray("documents").mapDocuments { item ->
                AuthorizedFolderDocumentSnapshot(
                    documentUri = item.optString("documentUri"),
                    documentKey = item.nullableString("documentKey"),
                    parentDocumentUri = item.nullableString("parentDocumentUri"),
                    displayName = item.optString("displayName"),
                    relativeDirectory = item.nullableString("relativeDirectory"),
                    size = item.optLong("size"),
                    lastModified = item.optLong("lastModified")
                )
            }
        )
    }
}

/**
 * Device-local memory of what every authorized folder contained the last time it was scanned.
 * It is a rebuildable cache: a damaged or newer payload simply behaves like "never scanned".
 */
data class AuthorizedFolderSnapshot(
    val trees: Map<String, AuthorizedFolderTreeSnapshot> = emptyMap()
) {
    val scannedAt: Long get() = trees.values.maxOfOrNull { it.scannedAt } ?: 0L

    fun toJson(): String = JSONObject().apply {
        put("schemaVersion", CURRENT_VERSION)
        put("trees", JSONArray().apply { trees.values.forEach { put(it.toJson()) } })
    }.toString()

    companion object {
        const val CURRENT_VERSION = 1

        /** Returns an empty snapshot when the payload is damaged or from a newer schema. */
        fun fromJson(raw: String): AuthorizedFolderSnapshot {
            val root = runCatching { JSONObject(raw) }.getOrNull() ?: return AuthorizedFolderSnapshot()
            if (root.optInt("schemaVersion", 0) != CURRENT_VERSION) return AuthorizedFolderSnapshot()
            val trees = LinkedHashMap<String, AuthorizedFolderTreeSnapshot>()
            root.optJSONArray("trees").mapTrees(AuthorizedFolderTreeSnapshot::fromJson).forEach { tree ->
                if (tree.treeUri.isNotBlank()) trees[tree.treeUri] = tree
            }
            return AuthorizedFolderSnapshot(trees)
        }
    }
}

/**
 * How many files the latest scan added compared with the previous snapshot.
 * Duplicated rows and files that only moved between authorized trees are not counted.
 */
fun authorizedFolderNewDocumentCount(
    previous: Collection<AuthorizedFolderDocumentSnapshot>,
    current: Collection<AuthorizedFolderDocumentSnapshot>
): Int {
    val known = previous.mapTo(mutableSetOf()) { it.identity }
    return current.asSequence().map { it.identity }.distinct().count { it !in known }
}

fun AuthorizedStorageManager.ScannedDirectory.toSnapshot() = AuthorizedFolderDirectorySnapshot(
    documentUri = uri.toString(),
    parentDocumentUri = parentUri?.toString(),
    name = name,
    relativePath = relativePath
)

fun AuthorizedStorageManager.ScannedDocument.toSnapshot() = AuthorizedFolderDocumentSnapshot(
    documentUri = uri.toString(),
    documentKey = documentKey,
    parentDocumentUri = parentUri.toString(),
    displayName = name,
    relativeDirectory = relativeDirectory,
    size = size,
    lastModified = lastModified
)

private fun JSONObject.putNullable(key: String, value: String?) {
    put(key, value ?: JSONObject.NULL)
}

private fun JSONObject.nullableString(key: String): String? =
    if (isNull(key)) null else optString(key, "").takeIf { it.isNotEmpty() }

private fun JSONArray?.mapDirectories(
    transform: (JSONObject) -> AuthorizedFolderDirectorySnapshot
): List<AuthorizedFolderDirectorySnapshot> {
    if (this == null) return emptyList()
    return (0 until length()).mapNotNull { index -> optJSONObject(index)?.let(transform) }
}

private fun JSONArray?.mapDocuments(
    transform: (JSONObject) -> AuthorizedFolderDocumentSnapshot
): List<AuthorizedFolderDocumentSnapshot> {
    if (this == null) return emptyList()
    return (0 until length()).mapNotNull { index -> optJSONObject(index)?.let(transform) }
}

private fun JSONArray?.mapTrees(
    transform: (JSONObject) -> AuthorizedFolderTreeSnapshot
): List<AuthorizedFolderTreeSnapshot> {
    if (this == null) return emptyList()
    return (0 until length()).mapNotNull { index -> optJSONObject(index)?.let(transform) }
}
