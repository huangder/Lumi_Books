package com.huangder.lumibooks.ui.bookshelf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BookshelfFilterCodecTest {
    @Test fun `every filter survives being stored and read back`() {
        val filters = listOf(
            BookshelfFilter.All, BookshelfFilter.EpubMobi, BookshelfFilter.Pdf, BookshelfFilter.Markdown,
            BookshelfFilter.Txt, BookshelfFilter.Comic, BookshelfFilter.Favorites, BookshelfFilter.Tag("tag-1")
        )
        filters.forEach { assertEquals(it, BookshelfFilter.decode(it.encode())) }
    }

    @Test fun `unknown or empty values fall back to all books`() {
        assertEquals(BookshelfFilter.All, BookshelfFilter.decode(null))
        assertEquals(BookshelfFilter.All, BookshelfFilter.decode(""))
        assertEquals(BookshelfFilter.All, BookshelfFilter.decode("something-new"))
        assertEquals(BookshelfFilter.All, BookshelfFilter.decode("tag:"))
    }

    @Test fun `folder cards are told apart from books and dropped from saved orders`() {
        val card = coverFlowFolderCard(
            com.huangder.lumibooks.domain.model.LibraryFolder(id = "f1", name = "Folder", parentId = null, createdAt = 0L)
        )
        assertTrue(isCoverFlowFolderCard(card.id))
        assertFalse(isCoverFlowFolderCard("book-1"))
        assertEquals("f1", coverFlowFolderId(card.id))
        assertEquals(listOf("a", "b"), withoutCoverFlowFolderCards(listOf(card.id, "a", "b")))
    }
}
