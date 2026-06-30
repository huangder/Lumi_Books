package com.ebook.reader.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "reading_records",
    indices = [
        Index(value = ["bookId", "date"], unique = true)
    ]
)
data class ReadingRecordEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val bookId: String,
    val date: String,
    val duration: Long,
    val startTime: Long,
    val endTime: Long
)
