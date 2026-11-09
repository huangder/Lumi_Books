package com.huangder.lumibooks.data.sync

import com.huangder.lumibooks.data.local.dao.SyncStateDao
import com.huangder.lumibooks.data.local.entity.SyncStateEntity
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
}
