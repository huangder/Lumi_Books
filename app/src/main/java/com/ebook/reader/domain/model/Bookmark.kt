package com.ebook.reader.domain.model

data class Bookmark(
    val id: Long = 0,
    val bookId: String,
    val chapterIndex: Int,
    val position: Float,
    val title: String,
    val createdAt: Long
)
