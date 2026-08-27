package com.huangder.lumibooks.data.local.entity

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
    val locatorJson: String? = null,
    val createdAt: Long,
    val isFavorite: Boolean = false,
    val isCloudOnly: Boolean = false,
    val remoteLibraryKey: String? = null,
    val remoteFileName: String? = null,
    val remoteFileSize: Long = 0L,
    val remoteFileSha256: String? = null,
    val metadataUpdatedAt: Long = createdAt
)

enum class BookFormat {
    EPUB, PDF, TXT, MOBI;

    companion object {
        fun fromString(format: String): BookFormat {
            return when (format.uppercase()) {
                "EPUB" -> EPUB
                "PDF" -> PDF
                "TXT" -> TXT
                "MOBI" -> MOBI
                else -> TXT
            }
        }
    }
}
