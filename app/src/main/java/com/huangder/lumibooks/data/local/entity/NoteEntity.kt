package com.huangder.lumibooks.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "notes")
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
    /** "highlight" = 高亮（背景色块）, "underline" = 划线 */
    val type: String = "highlight"
)
