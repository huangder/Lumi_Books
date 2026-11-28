package com.huangder.lumibooks.data.sync

import com.huangder.lumibooks.data.local.dao.SyncStateDao
import com.huangder.lumibooks.data.local.entity.SyncStateEntity
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/** Installation-local aliases also outlive book deletion, so tombstones use the same wire ID. */
@Singleton
class WebdavBookIdentityStore @Inject constructor(private val dao: SyncStateDao) {
    suspend fun read(libraryKey: String): Map<String, String> {
        val raw = dao.getValue("webdav_book_ids:$libraryKey") ?: return emptyMap()
        return runCatching {
            val json = JSONObject(raw)
            json.keys().asSequence().associateWith { json.getString(it) }
        }.getOrDefault(emptyMap())
    }

    suspend fun save(libraryKey: String, ids: Map<String, String>) {
        dao.putState(SyncStateEntity("webdav_book_ids:$libraryKey", JSONObject(ids).toString()))
    }

    suspend fun readPendingDeletes(libraryKey: String): Map<String, String> {
        val raw = dao.getValue("webdav_pending_deletes:$libraryKey") ?: return emptyMap()
        // Unreadable state must not block every later sync and delete.
        return runCatching {
            val json = JSONObject(raw)
            json.keys().asSequence().associateWith { json.getString(it) }
        }.getOrDefault(emptyMap())
    }

    suspend fun savePendingDeletes(libraryKey: String, files: Map<String, String>) {
        dao.putState(SyncStateEntity("webdav_pending_deletes:$libraryKey", JSONObject(files).toString()))
    }

    /** Local book ID to the body hash this device last confirmed equal to its cloud copy. */
    suspend fun readSyncedHashes(libraryKey: String): Map<String, String> = readMap("webdav_book_hashes:$libraryKey")

    suspend fun saveSyncedHashes(libraryKey: String, hashes: Map<String, String>) {
        dao.putState(SyncStateEntity("webdav_book_hashes:$libraryKey", JSONObject(hashes).toString()))
    }

    /** Remote body names replaced by an edit; removed once no active entry references them. */
    suspend fun readSupersededBodies(libraryKey: String): Set<String> = readSet("webdav_superseded_bodies:$libraryKey")

    suspend fun saveSupersededBodies(libraryKey: String, names: Set<String>) = saveSet("webdav_superseded_bodies:$libraryKey", names)

    /** Local book IDs the user asked to upload again after removing their cloud copy. */
    suspend fun readPendingReuploads(libraryKey: String): Set<String> = readSet("webdav_pending_reuploads:$libraryKey")

    suspend fun savePendingReuploads(libraryKey: String, ids: Set<String>) = saveSet("webdav_pending_reuploads:$libraryKey", ids)

    /** Local book IDs whose upload is blocked by a cloud deletion; shown as re-uploadable. */
    suspend fun readBlockedUploads(libraryKey: String): Set<String> = readSet("webdav_blocked_uploads:$libraryKey")

    suspend fun saveBlockedUploads(libraryKey: String, ids: Set<String>) = saveSet("webdav_blocked_uploads:$libraryKey", ids)

    private suspend fun readMap(key: String): Map<String, String> {
        val raw = dao.getValue(key) ?: return emptyMap()
        return runCatching {
            val json = JSONObject(raw)
            json.keys().asSequence().associateWith { json.getString(it) }
        }.getOrDefault(emptyMap())
    }

    private suspend fun readSet(key: String): Set<String> {
        val raw = dao.getValue(key) ?: return emptySet()
        return runCatching {
            val json = JSONArray(raw)
            (0 until json.length()).mapTo(mutableSetOf()) { json.getString(it) }
        }.getOrDefault(emptySet())
    }

    private suspend fun saveSet(key: String, values: Set<String>) {
        if (values.isEmpty()) dao.removeState(key) else dao.putState(SyncStateEntity(key, JSONArray(values).toString()))
    }
}
