package com.huangder.lumibooks.domain.model

data class Book(
    val id: String,
    val title: String,
    val author: String,
    val filePath: String,
    val coverPath: String?,
    val format: BookFormat,
    val lastReadTime: Long,
    val readingProgress: Float,
    val locatorJson: String? = null,
    val createdAt: Long,
    val isFavorite: Boolean = false
)

enum class BookFormat {
    EPUB, PDF, TXT, MOBI
}
