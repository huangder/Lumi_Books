package com.huangder.lumibooks.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FolderPreviewPlannerTest {
    private val root = folder("root", "Root")
    private val child = folder("child", "Child", root.id)
    private val sibling = folder("sibling", "Sibling")
    private val folders = listOf(root, child, sibling)

    @Test
    fun selectsUpToFourBooksFromTheWholeSubtreeInLibraryOrder() {
        val books = (1..5).map { book("book-$it") }
        val links = listOf(
            BookFolderLink(books[0].id, root.id),
            BookFolderLink(books[1].id, child.id),
            BookFolderLink(books[2].id, sibling.id),
            BookFolderLink(books[3].id, child.id),
            BookFolderLink(books[4].id, root.id)
        )

        assertEquals(
            listOf("book-1", "book-2", "book-4", "book-5"),
            FolderPreviewPlanner.selectBookIds(books, folders, links, root.id)
        )
    }

    @Test
    fun emptyFolderDoesNotProduceSnapshotCandidates() {
        assertEquals(
            emptyList<String>(),
            FolderPreviewPlanner.selectBookIds(
                booksInLibraryOrder = listOf(book("book-1")),
                folders = folders,
                links = emptyList(),
                folderId = root.id
            )
        )
    }

    @Test
    fun slotsPreserveMissingBooksInsteadOfRefillingFromOtherBooks() {
        val first = book("book-1")
        val third = book("book-3")
        val slots = FolderPreviewPlanner.slots(
            previewBookIds = listOf(first.id, "deleted", third.id),
            booksById = mapOf(first.id to first, third.id to third)
        )

        assertEquals(4, slots.size)
        assertEquals(first, slots[0])
        assertNull(slots[1])
        assertEquals(third, slots[2])
        assertNull(slots.getOrNull(3))
    }

    @Test
    fun slotsClearBooksMovedOutOfTheFolderSubtree() {
        val first = book("book-1")
        val second = book("book-2")
        val slots = FolderPreviewPlanner.slots(
            previewBookIds = listOf(first.id, second.id),
            booksById = mapOf(first.id to first, second.id to second),
            presentBookIds = setOf(second.id)
        )

        assertNull(slots[0])
        assertEquals(second, slots[1])
        assertNull(slots[2])
        assertNull(slots[3])
    }

    @Test
    fun slotsKeepAllFourSnapshotPositions() {
        val books = (1..4).map { book("book-$it") }
        val slots = FolderPreviewPlanner.slots(
            previewBookIds = books.map { it.id },
            booksById = books.associateBy { it.id },
            presentBookIds = books.mapTo(mutableSetOf()) { it.id }
        )

        assertEquals(books, slots)
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
