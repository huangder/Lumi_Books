package com.huangder.lumibooks.data.sync

import com.huangder.lumibooks.data.local.dao.SyncStateDao
import com.huangder.lumibooks.data.local.entity.SyncStateEntity
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncIdentityStore @Inject constructor(
    private val syncStateDao: SyncStateDao
) {
    @Volatile
    private var cachedDeviceId: String? = null

    suspend fun deviceId(): String {
        cachedDeviceId?.let { return it }
        val stored = syncStateDao.getValue(DEVICE_ID_KEY)?.takeIf { it.isNotBlank() }
        val resolved = stored ?: UUID.randomUUID().toString().also {
            syncStateDao.putState(SyncStateEntity(DEVICE_ID_KEY, it))
        }
        cachedDeviceId = resolved
        return resolved
    }

    companion object {
        const val DEVICE_ID_KEY = "device_id"
    }
}
