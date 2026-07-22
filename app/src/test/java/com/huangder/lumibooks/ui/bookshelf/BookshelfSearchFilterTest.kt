package com.huangder.lumibooks.ui.bookshelf

import com.huangder.lumibooks.domain.model.Book
import com.huangder.lumibooks.domain.model.BookFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BookshelfSearchFilterTest {

    private val books = listOf(
        book(id = "one", title = "Android Design", author = "Alice"),
        book(id = "two", title = "春日读书", author = "陈某"),
        book(id = "three", title = "Kotlin Notes", author = "Bob")
    )
    private val tags = mapOf(
        "one" to listOf("技术"),
        "two" to listOf("散文", "春天")
    )

    @Test
    fun blankQueryDoesNotShowResults() {
        assertTrue(filterBookshelfSearchResults(books, "  ", tags).isEmpty())
    }

    @Test
    fun matchesTitleAndAuthorIgnoringCase() {
        assertEquals(
            listOf("one"),
            filterBookshelfSearchResults(books, "android", tags).map { it.id }
        )
        assertEquals(
            listOf("three"),
            filterBookshelfSearchResults(books, "BOB", tags).map { it.id }
        )
    }

    @Test
    fun matchesActualAssignedTagName() {
        assertEquals(
            listOf("two"),
            filterBookshelfSearchResults(books, "春天", tags).map { it.id }
        )
    }

    private fun book(id: String, title: String, author: String) = Book(
        id = id,
        title = title,
        author = author,
        filePath = "$id.epub",
        coverPath = null,
        format = BookFormat.EPUB,
        lastReadTime = 0L,
        readingProgress = 0f,
        createdAt = 0L
    )
}
