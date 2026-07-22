package com.huangder.lumibooks.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "tags",
    indices = [Index(value = ["normalizedName"], unique = true)]
)
data class TagEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val normalizedName: String,
    val createdAt: Long
)
