package com.ebook.reader.domain.model

data class Note(
    val id: Long = 0,
    val bookId: String,
    val chapterIndex: Int,
    val startPosition: Int,
    val endPosition: Int,
    val selectedText: String,
    val note: String,
    val color: String,
    val createdAt: Long
)
