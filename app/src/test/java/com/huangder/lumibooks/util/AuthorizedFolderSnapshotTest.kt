package com.huangder.lumibooks.util

import org.junit.Assert.assertEquals
import org.junit.Test

class AuthorizedFolderSnapshotTest {
    @Test
    fun jsonRoundTripPreservesTrees() {
        val snapshot = AuthorizedFolderSnapshot(
            trees = mapOf(
                TREE to AuthorizedFolderTreeSnapshot(
                    treeUri = TREE,
                    rootName = "Books",
                    scannedAt = 1_700_000_000_000L,
                    directories = listOf(
                        AuthorizedFolderDirectorySnapshot(
                            documentUri = "content://tree/root",
                            parentDocumentUri = null,
                            name = "Books",
                            relativePath = null
                        ),
                        AuthorizedFolderDirectorySnapshot(
                            documentUri = "content://tree/novels",
                            parentDocumentUri = "content://tree/root",
                            name = "Novels",
                            relativePath = "Novels"
                        )
                    ),
                    documents = listOf(
                        document(key = "primary:Books/a.epub", name = "a.epub", lastModified = 42L)
                    )
                )
            )
        )

        assertEquals(snapshot, AuthorizedFolderSnapshot.fromJson(snapshot.toJson()))
    }

    @Test
    fun damagedPayloadBehavesLikeNoSnapshot() {
        assertEquals(
            AuthorizedFolderSnapshot(),
            AuthorizedFolderSnapshot.fromJson("{ not valid json")
        )
    }

    @Test
    fun newerSchemaVersionBehavesLikeNoSnapshot() {
        val payload = """{"schemaVersion":99,"trees":[]}"""

        assertEquals(AuthorizedFolderSnapshot(), AuthorizedFolderSnapshot.fromJson(payload))
    }

    @Test
    fun newDocumentCountOnlyCountsUnknownFilesOnce() {
        val previous = listOf(document(key = "keep", name = "keep.epub"))
        val current = listOf(
            document(key = "keep", name = "keep.epub"),
            document(key = "added", name = "added.epub"),
            document(key = "added", name = "added.epub")
        )

        assertEquals(1, authorizedFolderNewDocumentCount(previous, current))
    }

    @Test
    fun renamedFileWithTheSameDocumentKeyIsNotNew() {
        val previous = listOf(document(key = "same-key", name = "old.epub"))
        val current = listOf(document(key = "same-key", name = "renamed.epub"))

        assertEquals(0, authorizedFolderNewDocumentCount(previous, current))
    }

    @Test
    fun filesWithoutDocumentKeyFallBackToTheirUri() {
        val previous = listOf(document(key = null, name = "a.epub", uri = "content://tree/a"))
        val current = listOf(document(key = null, name = "a.epub", uri = "content://tree/a"))

        assertEquals(0, authorizedFolderNewDocumentCount(previous, current))
        assertEquals(
            1,
            authorizedFolderNewDocumentCount(
                previous,
                current + document(key = null, name = "b.epub", uri = "content://tree/b")
            )
        )
    }

    private fun document(
        key: String?,
        name: String,
        uri: String = "content://tree/$name",
        lastModified: Long = 0L
    ) = AuthorizedFolderDocumentSnapshot(
        documentUri = uri,
        documentKey = key,
        parentDocumentUri = "content://tree/root",
        displayName = name,
        relativeDirectory = null,
        size = 10L,
        lastModified = lastModified
    )

    private companion object {
        const val TREE = "content://provider/tree/books"
    }
}
