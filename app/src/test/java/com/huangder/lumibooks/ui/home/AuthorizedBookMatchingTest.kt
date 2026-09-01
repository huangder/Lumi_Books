package com.huangder.lumibooks.ui.home

import com.huangder.lumibooks.domain.model.Book
import com.huangder.lumibooks.domain.model.BookFormat
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AuthorizedBookMatchingTest {
    @Test
    fun stableDocumentKeyDeduplicatesDifferentTreeUris() {
        val first = authorizedDocumentIdentity(
            "com.android.externalstorage.documents:primary:lumi/book.epub",
            "content://provider/tree/primary%3Alumi/document/primary%3Alumi%2Fbook.epub"
        )
        val second = authorizedDocumentIdentity(
            "com.android.externalstorage.documents:primary:lumi/book.epub",
            "content://provider/tree/primary%3Alumi%2Fsub/document/primary%3Alumi%2Fbook.epub"
        )

        assertEquals(first, second)
    }

    @Test
    fun matchingUsesDocumentKeyBeforeUriAndHash() {
        val byKey = book("key", sourceDocumentKey = "document-key", sourceSha256 = "hash")
        val byUri = book("uri", sourceUri = "content://incoming", sourceSha256 = "hash")

        assertEquals(
            byKey,
            findMatchingAuthorizedBook(
                documentKey = "document-key",
                uri = "content://incoming",
                sha256 = "hash",
                books = listOf(byUri, byKey)
            )
        )
    }

    @Test
    fun hashMatchesOnlyOneAvailableBook() {
        val first = book("first", sourceSha256 = "same")
        val second = book("second", sourceSha256 = "same")

        assertEquals(
            first,
            findMatchingAuthorizedBook(null, "content://new", "same", listOf(first))
        )
        assertNull(
            findMatchingAuthorizedBook(null, "content://new", "same", listOf(first, second))
        )
        assertEquals(
            second,
            findMatchingAuthorizedBook(
                null,
                "content://new",
                "same",
                listOf(first, second),
                claimedBookIds = setOf(first.id)
            )
        )
    }

    @Test
    fun missingLocalHashesAreBackfilledOnce() = runTest {
        val oldBook = book("old", filePath = "/books/old.epub")
        val hashedBook = book("hashed", sourceSha256 = "existing")
        val cloudBook = book("cloud", filePath = "", isCloudOnly = true)
        val persisted = mutableListOf<Pair<String, String>>()

        val result = backfillMissingSourceHashes(
            books = listOf(oldBook, hashedBook, cloudBook),
            hashLocation = { path -> if (path == oldBook.filePath) "calculated" else null },
            persist = { id, hash -> persisted += id to hash }
        )

        assertEquals("calculated", result.first().sourceSha256)
        assertEquals(listOf("old" to "calculated"), persisted)
        assertEquals("existing", result[1].sourceSha256)
        assertNull(result[2].sourceSha256)
    }

    @Test
    fun unchangedAuthorizedDocumentCanReuseItsHash() {
        val book = book("stable", sourceSha256 = "stored").copy(sourceLastModified = 42L)

        assertEquals(false, shouldRefreshAuthorizedHash(book, 42L))
    }

    @Test
    fun changedAuthorizedDocumentRefreshesItsHash() {
        val book = book("changed", sourceSha256 = "stored").copy(sourceLastModified = 42L)

        assertEquals(true, shouldRefreshAuthorizedHash(book, 43L))
    }

    @Test
    fun unknownTimestampOrMissingHashRefreshesItsHash() {
        val hashedBook = book("unknown-time", sourceSha256 = "stored").copy(sourceLastModified = 42L)
        val missingHashBook = book("missing-hash").copy(sourceLastModified = 42L)

        assertEquals(true, shouldRefreshAuthorizedHash(hashedBook, 0L))
        assertEquals(true, shouldRefreshAuthorizedHash(missingHashBook, 42L))
    }

    private fun book(
        id: String,
        filePath: String = "/books/$id.epub",
        sourceUri: String? = null,
        sourceDocumentKey: String? = null,
        sourceSha256: String? = null,
        isCloudOnly: Boolean = false
    ) = Book(
        id = id,
        title = id,
        author = "Author",
        filePath = filePath,
        coverPath = null,
        format = BookFormat.EPUB,
        lastReadTime = 0L,
        readingProgress = 0f,
        createdAt = 0L,
        sourceUri = sourceUri,
        sourceDocumentKey = sourceDocumentKey,
        sourceSha256 = sourceSha256,
        isCloudOnly = isCloudOnly
    )
}
