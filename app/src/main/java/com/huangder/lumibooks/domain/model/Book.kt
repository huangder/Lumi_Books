package com.ebook.reader.domain.model

data class Book(
    val id: String,
    val title: String,
    val author: String,
    val filePath: String,
    val coverPath: String?,
    val format: BookFormat,
    val lastReadTime: Long,
    val readingProgress: Float,
    val createdAt: Long
)

enum class BookFormat {
    EPUB, PDF, TXT
}
