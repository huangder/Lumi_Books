package com.huangder.lumibooks.ui.home

import com.huangder.lumibooks.domain.model.Book
import java.util.Locale

enum class SortBy {
    LAST_READ, TITLE, AUTHOR, DATE_ADDED
}

internal fun sortBooksForLibrary(books: List<Book>, sortBy: SortBy): List<Book> =
    when (sortBy) {
        SortBy.LAST_READ -> books.sortedWith(
            compareByDescending<Book> { it.isPinned }
                .thenByDescending { it.lastReadTime }
                .thenBy { it.id }
        )
        SortBy.DATE_ADDED -> books.sortedWith(
            compareByDescending<Book> { it.isPinned }
                .thenByDescending { it.createdAt }
                .thenBy { it.id }
        )
        SortBy.TITLE -> books.sortedWith(
            compareByDescending<Book> { it.isPinned }
                .thenBy { it.title.lowercase(Locale.ROOT) }
                .thenBy { it.id }
        )
        SortBy.AUTHOR -> books.sortedWith(
            compareByDescending<Book> { it.isPinned }
                .thenBy { it.author.lowercase(Locale.ROOT) }
                .thenBy { it.id }
        )
    }
