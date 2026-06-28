package com.ebook.reader.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "books")
data class BookEntity(
    @PrimaryKey
    val id: String,
    val title: String,
    val author: String,
    val filePath: String,
    val coverPath: String?,
    val format: String,
    val lastReadTime: Long,
    val readingProgress: Float,
    val createdAt: Long
)

enum class BookFormat {
    EPUB, PDF, TXT;

    companion object {
        fun fromString(format: String): BookFormat {
            return when (format.uppercase()) {
                "EPUB" -> EPUB
                "PDF" -> PDF
                "TXT" -> TXT
                else -> TXT
            }
        }
    }
}
