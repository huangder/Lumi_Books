package com.huangder.lumibooks.data.local.model

data class ContinueReadingWidgetData(
    val bookId: String,
    val title: String,
    val author: String,
    val coverPath: String?,
    val readingProgress: Float
)

data class QuoteWidgetData(
    val noteId: Long,
    val bookId: String,
    val selectedText: String,
    val noteText: String,
    val bookTitle: String,
    val bookAuthor: String
) {
    val isNote: Boolean get() = noteText.isNotBlank()
}
