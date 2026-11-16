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

    @Test
    fun customSortKeepsPinnedFirstAndPutsUnplacedBooksInFrontNewestFirst() {
        val books = listOf(
            book("a", "A", "x", createdAt = 1),
            book("b", "B", "x", createdAt = 2),
            book("c", "C", "x", createdAt = 3),
            book("p", "P", "x", createdAt = 4, pinned = true),
            book("new1", "N1", "x", createdAt = 50),
            book("new2", "N2", "x", createdAt = 60)
        )
        val order = listOf("gone", "c", "a", "p", "b")

        assertEquals(
            listOf("p", "new2", "new1", "c", "a", "b"),
            sortBooksForLibrary(books, SortBy.CUSTOM, order).map { it.id }
        )
    }

    @Test
    fun mergeCustomOrderRefillsTheSlotsOfTheReorderedSubset() {
        val global = listOf("a", "b", "c", "d", "e")

        // A folder holding b and d: they swap, everything else stays where it was.
        assertEquals(listOf("a", "d", "c", "b", "e"), mergeCustomOrder(global, listOf("d", "b")))
        // The whole shelf.
        assertEquals(listOf("e", "a", "b", "c", "d"), mergeCustomOrder(global, listOf("e", "a", "b", "c", "d")))
    }

    @Test
    fun mergeCustomOrderPlacesUnknownIdsInFrontInTheirShownOrder() {
        assertEquals(
            listOf("x", "b", "a", "c"),
            mergeCustomOrder(listOf("a", "b", "c"), listOf("x", "b", "a"))
        )
        assertEquals(listOf("b", "a"), mergeCustomOrder(emptyList(), listOf("b", "a")))
        assertEquals(listOf("a", "b"), mergeCustomOrder(listOf("a", "b"), emptyList()))
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
