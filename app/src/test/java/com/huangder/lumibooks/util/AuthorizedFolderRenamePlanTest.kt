package com.huangder.lumibooks.util

import android.app.Application
import android.net.Uri
import android.provider.DocumentsContract
import com.huangder.lumibooks.domain.model.Book
import com.huangder.lumibooks.domain.model.BookFormat
import com.huangder.lumibooks.domain.model.LibraryFolder
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class AuthorizedFolderRenamePlanTest {
    private val storage = AuthorizedStorageManager()
    private val tree = DocumentsContract.buildTreeDocumentUri("rename.test", "root")
    private fun uri(id: String) = DocumentsContract.buildDocumentUriUsingTree(tree, id)
    private val before get() = scan("old", "Old name")
    private val after get() = scan("new", "New name")

    // Deliberately opaque IDs: descendants cannot be repaired with a URI string prefix replace.
    private fun scan(prefix: String, name: String) = AuthorizedStorageManager.ScanResult(
        uri("$prefix-root"),
        listOf(
            AuthorizedStorageManager.ScannedDirectory(uri("$prefix-root"), tree, null, name, null),
            AuthorizedStorageManager.ScannedDirectory(uri("$prefix-child"), tree, uri("$prefix-root"), "Nested", "Nested")
        ),
        listOf(
            document(prefix, "direct", null),
            document(prefix, "nested", "Nested")
        )
    )

    private fun document(prefix: String, id: String, relative: String?) = AuthorizedStorageManager.ScannedDocument(
        uri("$prefix-$id"), tree, uri("$prefix-${if (relative == null) "root" else "child"}"),
        "same.epub", relative, 123L, 456L, storage.documentKey(uri("$prefix-$id"))
    )

    private fun book(id: String = "direct") = Book(
        id = "book-$id", title = "Custom title", author = "Author",
        filePath = uri("old-$id").toString(), coverPath = "/local/cover.jpg", format = BookFormat.EPUB,
        lastReadTime = 42, readingProgress = 0.6f, locatorJson = "saved locator", createdAt = 1,
        sourceUri = uri("old-$id").toString(), sourceDocumentKey = storage.documentKey(uri("old-$id")),
        sourceParentUri = uri("old-${if (id == "direct") "root" else "child"}").toString(),
        sourceSha256 = "unchanged hash", isMissing = true
    )

    @Test fun updatesDirectAndNestedBooksWithoutChangingReadingData() {
        val plan = AuthorizedFolderRenamePlan(before, after)
        listOf("direct", "nested").forEach { id ->
            val original = book(id)
            assertEquals(original.copy(
                filePath = uri("new-$id").toString(), sourceUri = uri("new-$id").toString(),
                sourceDocumentKey = storage.documentKey(uri("new-$id")),
                sourceParentUri = uri("new-${if (id == "direct") "root" else "child"}").toString(),
                isMissing = false
            ), plan.rebind(original))
        }
    }

    @Test fun updatesNestedFolderAndKeepsTheRenamedRootParent() {
        val root = LibraryFolder("folder", "Old name", "parent", 1,
            storageTreeUri = tree.toString(), storageDocumentUri = uri("old-root").toString(),
            storageParentUri = uri("root").toString())
        val child = root.copy(id = "child", name = "Nested", parentId = root.id,
            storageDocumentUri = uri("old-child").toString(), storageParentUri = root.storageDocumentUri)
        val plan = AuthorizedFolderRenamePlan(before, after)
        assertEquals(root.copy(name = "New name", storageDocumentUri = uri("new-root").toString()), plan.rebind(root))
        assertEquals(child.copy(storageDocumentUri = uri("new-child").toString(),
            storageParentUri = uri("new-root").toString()), plan.rebind(child))
    }

    @Test fun localCopiesAndMarkdownKeepTheirReadableLocalFiles() {
        val plan = AuthorizedFolderRenamePlan(before, after)
        listOf(BookFormat.EPUB, BookFormat.MARKDOWN).forEach { format ->
            val original = book().copy(format = format, filePath = "/local/generated.epub", isMissing = false)
            val updated = plan.rebind(original)
            assertEquals(original.filePath, updated.filePath)
            assertEquals(uri("new-direct").toString(), updated.sourceUri)
            assertEquals(uri("new-root").toString(), updated.sourceParentUri)
            assertEquals(original.sourceSha256, updated.sourceSha256)
        }
    }

    @Test fun stableIdsStillUpdateParentReferences() {
        val current = before.copy(directories = after.directories,
            documents = before.documents.mapIndexed { index, old -> old.copy(parentUri = after.documents[index].parentUri) })
        val updated = AuthorizedFolderRenamePlan(before, current).rebind(book())
        assertEquals(book().filePath, updated.filePath)
        assertEquals(uri("new-root").toString(), updated.sourceParentUri)
    }

    @Test fun unrelatedBooksAndFoldersAreUntouched() {
        val plan = AuthorizedFolderRenamePlan(before, after)
        val other = book().copy(filePath = uri("outside").toString(), sourceUri = null, sourceDocumentKey = "outside")
        assertEquals(other, plan.rebind(other))
        val folder = LibraryFolder("other", "Other", null, 1, storageDocumentUri = uri("outside").toString())
        assertEquals(folder, plan.rebind(folder))
    }

    @Test fun incompleteRescanCannotCommitPartialReferences() {
        assertThrows(IllegalArgumentException::class.java) {
            AuthorizedFolderRenamePlan(before, after.copy(documents = after.documents.take(1)))
        }
    }

    @Test fun identifiesTheGrantedRootEvenWithDifferentUriEncoding() {
        assertTrue(storage.isTreeRoot(tree, uri("root")))
        assertFalse(storage.isTreeRoot(tree, uri("old-root")))
    }
}
