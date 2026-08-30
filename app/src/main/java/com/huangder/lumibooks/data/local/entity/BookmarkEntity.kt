package com.huangder.lumibooks.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "bookmarks",
    indices = [Index(value = ["syncId"], unique = true)]
)
data class BookmarkEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val bookId: String,
    val chapterIndex: Int,
    val position: Float,
    val locatorJson: String? = null,
    val title: String,
    val createdAt: Long,
    val syncId: String = "",
    val updatedAt: Long = createdAt
)
