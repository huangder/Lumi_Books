package com.huangder.lumibooks.domain.model

data class Note(
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
    val type: String = "highlight"
)
