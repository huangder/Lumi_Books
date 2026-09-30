package com.huangder.lumibooks.ui.home

import com.huangder.lumibooks.domain.model.Book
import com.huangder.lumibooks.domain.model.BookFormat
import org.junit.Assert.assertEquals
import org.junit.Test

class BookSortingTest {
    @Test
    fun pinnedBooksAlwaysComeFirst() {
        val books = listOf(
            book("z", "Zulu", "Author", createdAt = 30, pinned = false),
            book("a", "Alpha", "Author", createdAt = 10, pinned = true),
            book("b", "Beta", "Author", createdAt = 20, pinned = false)
        )

        assertEquals(listOf("a", "z", "b"), sortBooksForLibrary(books, SortBy.DATE_ADDED).map { it.id })
    }

    @Test
    fun titleAndAuthorSortIgnoreCaseAndUseIdAsTieBreak() {
        val books = listOf(
            book("b", "alpha", "zulu"),
            book("a", "Alpha", "Zulu"),
            book("c", "Beta", "alpha")
        )

        assertEquals(listOf("a", "b", "c"), sortBooksForLibrary(books, SortBy.TITLE).map { it.id })
        assertEquals(listOf("c", "a", "b"), sortBooksForLibrary(books, SortBy.AUTHOR).map { it.id })
    }

    @Test
    fun recentReadAndDateAddedSortDescending() {
        val books = listOf(
            book("old", "Old", "Author", lastRead = 10, createdAt = 30),
            book("new", "New", "Author", lastRead = 30, createdAt = 10),
            book("middle", "Middle", "Author", lastRead = 20, createdAt = 20)
        )

        assertEquals(listOf("new", "middle", "old"), sortBooksForLibrary(books, SortBy.LAST_READ).map { it.id })
        assertEquals(listOf("old", "middle", "new"), sortBooksForLibrary(books, SortBy.DATE_ADDED).map { it.id })
    }

    private fun book(
        id: String,
        title: String,
        author: String,
        lastRead: Long = 0L,
        createdAt: Long = 0L,
        pinned: Boolean = false
    ) = Book(
        id = id,
        title = title,
        author = author,
        filePath = "$id.txt",
        coverPath = null,
        format = BookFormat.TXT,
        lastReadTime = lastRead,
        readingProgress = 0f,
        createdAt = createdAt,
        isPinned = pinned
    )
}
