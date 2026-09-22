package com.huangder.lumibooks.ui.bookshelf

import com.huangder.lumibooks.domain.model.Book
import com.huangder.lumibooks.domain.model.BookFolderLink
import com.huangder.lumibooks.domain.model.BookFormat
import com.huangder.lumibooks.domain.model.LibraryFolder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
    fun coverFlowShowsTheWholeLibraryAtRootAndDirectBooksInsideAFolder() {
        val rootBook = book("root")
        val directBook = book("direct")
        val descendantBook = book("descendant")
        val unrelatedBook = book("unrelated")
        val books = listOf(rootBook, directBook, descendantBook, unrelatedBook)
        val links = listOf(
            BookFolderLink(directBook.id, rootA.id),
            BookFolderLink(descendantBook.id, grandchildA.id),
            BookFolderLink(unrelatedBook.id, rootB.id)
        )

        assertEquals(books, booksForCoverFlow(books, links, null))
        assertEquals(listOf(directBook), booksForCoverFlow(books, links, rootA.id))
        assertEquals(listOf(descendantBook), booksForCoverFlow(books, links, grandchildA.id))
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

    @Test
    fun flattenFolderTreeListsParentsBeforeChildrenWithDepth() {
        val rows = flattenFolderTree(folders)

        assertEquals(listOf(rootA.id, childA.id, grandchildA.id, rootB.id), rows.map { it.folder.id })
        assertEquals(listOf(0, 1, 2, 0), rows.map { it.depth })
        assertEquals(listOf(true, true, false, false), rows.map { it.hasChildren })
    }

    @Test
    fun flattenFolderTreeHidesTheSubtreeOfACollapsedFolder() {
        val collapsedRoot = flattenFolderTree(folders, collapsedFolderIds = setOf(rootA.id))

        assertEquals(listOf(rootA.id, rootB.id), collapsedRoot.map { it.folder.id })
        assertTrue(collapsedRoot.first().hasChildren)

        val collapsedChild = flattenFolderTree(folders, collapsedFolderIds = setOf(childA.id))

        assertEquals(listOf(rootA.id, childA.id, rootB.id), collapsedChild.map { it.folder.id })
        assertEquals(listOf(0, 1, 0), collapsedChild.map { it.depth })
        assertTrue(collapsedChild[1].hasChildren)
    }

    @Test
    fun flattenFolderTreeSkipsFoldersThatAreNotReachableFromTheRoot() {
        val folders = listOf(
            folder("root-a", "Root A"),
            folder("self", "Self", "self"),
            folder("orphan", "Orphan", "missing")
        )

        assertEquals(listOf("root-a"), flattenFolderTree(folders).map { it.folder.id })
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
