package com.huangder.lumibooks.ui.bookshelf

import com.huangder.lumibooks.domain.model.Book
import com.huangder.lumibooks.domain.model.BookFolderLink
import com.huangder.lumibooks.domain.model.BookFormat
import com.huangder.lumibooks.domain.model.LibraryFolder
import org.junit.Assert.assertEquals
import org.junit.Test

class BookshelfFolderHierarchyTest {
    private val rootA = folder("root-a", "Root A")
    private val childA = folder("child-a", "Child A", rootA.id)
    private val grandchildA = folder("grandchild-a", "Grandchild A", childA.id)
    private val rootB = folder("root-b", "Root B")
    private val folders = listOf(rootA, childA, grandchildA, rootB)

    @Test
    fun resolvesChildrenAndBreadcrumbPath() {
        assertEquals(listOf(rootA, rootB), directChildFolders(folders, null))
        assertEquals(listOf(childA), directChildFolders(folders, rootA.id))
        assertEquals(listOf(rootA, childA, grandchildA), folderPath(folders, grandchildA.id))
    }

    @Test
    fun returnsOnlyBooksDirectlyAtRequestedLevel() {
        val rootBook = book("root")
        val directBook = book("direct")
        val descendantBook = book("descendant")
        val books = listOf(rootBook, directBook, descendantBook)
        val links = listOf(
            BookFolderLink(directBook.id, rootA.id),
            BookFolderLink(descendantBook.id, grandchildA.id)
        )

        assertEquals(listOf(rootBook), booksAtFolderLevel(books, links, null))
        assertEquals(listOf(directBook), booksAtFolderLevel(books, links, rootA.id))
        assertEquals(listOf(descendantBook), booksAtFolderLevel(books, links, grandchildA.id))
    }

    @Test
    fun countsBooksAcrossAllDescendantFolders() {
        val links = listOf(
            BookFolderLink("book-1", rootA.id),
            BookFolderLink("book-2", childA.id),
            BookFolderLink("book-3", grandchildA.id),
            BookFolderLink("book-4", rootB.id)
        )

        assertEquals(
            mapOf(rootA.id to 3, childA.id to 2, grandchildA.id to 1, rootB.id to 1),
            folderBookCounts(folders, links)
        )
    }

    @Test
    fun folderCategoryCollectsBooksFromTheWholeSubtree() {
        val directBook = book("direct")
        val childBook = book("child")
        val unrelatedBook = book("unrelated")
        val books = listOf(directBook, childBook, unrelatedBook)
        val links = listOf(
            BookFolderLink(directBook.id, rootA.id),
            BookFolderLink(childBook.id, grandchildA.id),
            BookFolderLink(unrelatedBook.id, rootB.id)
        )

        assertEquals(
            listOf(directBook, childBook),
            booksInFolderTree(books, links, folders, rootA.id)
        )
    }

    private fun folder(id: String, name: String, parentId: String? = null) =
        LibraryFolder(id = id, name = name, parentId = parentId, createdAt = 1L)

    private fun book(id: String) = Book(
        id = id,
        title = id,
        author = "Author",
        filePath = "/$id.epub",
        coverPath = null,
        format = BookFormat.EPUB,
        lastReadTime = 0L,
        readingProgress = 0f,
        createdAt = 0L
    )
}
