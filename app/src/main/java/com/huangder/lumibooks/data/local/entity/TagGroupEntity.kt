package com.huangder.lumibooks.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "tag_groups", indices = [Index(value = ["type", "normalizedName"])])
data class TagGroupEntity(
    @PrimaryKey val id: String,
    val type: String,
    val name: String,
    val normalizedName: String,
    val updatedAt: Long,
    @ColumnInfo(defaultValue = "0") val isDeleted: Boolean = false
)
