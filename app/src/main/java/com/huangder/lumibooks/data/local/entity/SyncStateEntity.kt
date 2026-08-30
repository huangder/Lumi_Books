package com.huangder.lumibooks.data.local.entity

import androidx.room.Entity

@Entity(tableName = "sync_state", primaryKeys = ["key"])
data class SyncStateEntity(
    val key: String,
    val value: String
)

@Entity(tableName = "sync_tombstones", primaryKeys = ["namespace", "itemId"])
data class SyncTombstoneEntity(
    val namespace: String,
    val itemId: String,
    val deletedAt: Long,
    val deviceId: String
)
