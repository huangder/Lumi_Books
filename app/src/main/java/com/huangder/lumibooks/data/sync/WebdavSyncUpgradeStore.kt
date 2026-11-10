package com.huangder.lumibooks.data.sync

import com.huangder.lumibooks.data.local.dao.SyncStateDao
import com.huangder.lumibooks.data.local.entity.SyncStateEntity
import com.huangder.lumibooks.domain.model.WebdavConfig
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Device-local rollout notice: never copied by backups or WebDAV state. */
@Singleton
class WebdavSyncUpgradeStore @Inject constructor(private val dao: SyncStateDao) {
    private val mutex = Mutex()

    suspend fun shouldPrompt(config: WebdavConfig): Boolean = mutex.withLock {
        val state = dao.getValue(KEY) ?: (if (config.enabled) "pending" else "done").also {
            dao.putState(SyncStateEntity(KEY, it))
        }
        state == "pending" && config.enabled
    }

    suspend fun acknowledge() {
        mutex.withLock { dao.putState(SyncStateEntity(KEY, "done")) }
    }

    private companion object {
        const val KEY = "webdav_content_identity_upgrade_v1"
    }
}
