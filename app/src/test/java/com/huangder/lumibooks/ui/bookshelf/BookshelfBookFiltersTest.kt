package com.huangder.lumibooks.ui.bookshelf

import com.huangder.lumibooks.domain.model.Book
import com.huangder.lumibooks.domain.model.BookFormat
import org.junit.Assert.assertEquals
import org.junit.Test

class BookshelfBookFiltersTest {
    @Test
    fun epubMobiIncludesOnlyEpubAndMobiBooks() {
        val books = BookFormat.entries.map { format -> book(format) }

        assertEquals(
            listOf(BookFormat.EPUB, BookFormat.MOBI),
            books.filter(Book::isEpubMobi).map { it.format }
        )
    }

    private fun book(format: BookFormat) = Book(
        id = format.name,
        title = format.name,
        author = "Author",
        filePath = "/book.${format.name.lowercase()}",
        coverPath = null,
        format = format,
        lastReadTime = 0L,
        readingProgress = 0f,
        createdAt = 0L
    )
}
