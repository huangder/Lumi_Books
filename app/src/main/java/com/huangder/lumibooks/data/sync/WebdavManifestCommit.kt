package com.huangder.lumibooks.data.sync

internal data class ManifestVersion(val manifest: SyncManifest, val fingerprint: String, val etag: String?)

internal class ManifestConflict : IllegalStateException("WebDAV manifest changed during synchronization")

internal fun String.isSafeWebdavFileName(): Boolean = isNotBlank() && this != "." && this != ".." &&
    none { it == '/' || it == '\\' || it.isISOControl() }

internal fun SyncManifest.unreferencedDeletedFiles(): Set<Pair<String, String>> {
    val active = buildSet {
        for (entry in activeRemoteBooks().values) {
            add("books" to entry.fileName)
            entry.cover?.let { add("covers" to it.fileName) }
        }
        for ((id, entry) in data) if (id !in deletedBooks) add("data" to entry.fileName)
    }
    return buildSet {
        fun target(directory: String, name: String?) {
            if (name != null && name.isSafeWebdavFileName()) add(directory to name)
        }
        for (entry in deletedBooks.values) {
            target("books", entry.fileName)
            target("covers", entry.coverFileName)
            target("data", entry.dataFileName)
        }
    } - active
}

/** Bodies replaced by an edit may go only once no active book entry still points at them. */
internal fun SyncManifest.unreferencedBookFiles(names: Set<String>): Set<String> {
    val active = activeRemoteBooks().values.mapTo(mutableSetOf()) { it.fileName }
    return names.filterTo(mutableSetOf()) { it.isSafeWebdavFileName() && it !in active }
}
