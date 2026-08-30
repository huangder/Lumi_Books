package com.huangder.lumibooks.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "notes",
    indices = [Index(value = ["syncId"], unique = true)]
)
data class NoteEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val bookId: String,
    val chapterIndex: Int,
    val startPosition: Int,
    val endPosition: Int,
    val startLocatorJson: String? = null,
    val endLocatorJson: String? = null,
    val selectedText: String,
    val note: String,
    val color: String,
    val createdAt: Long,
    val type: String = "highlight",
    val syncId: String = "",
    val updatedAt: Long = createdAt
)
