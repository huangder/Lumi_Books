package com.huangder.lumibooks.util.markdown

import android.app.Application
import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import androidx.test.core.app.ApplicationProvider
import com.huangder.lumibooks.util.parser.MarkdownParser
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.FileNotFoundException
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class MarkdownStorageAccessTest {
    @get:Rule val temporary = TemporaryFolder()

    class TreeProvider : ContentProvider() {
        val files = mutableMapOf<String, File>()
        val opened = mutableListOf<String>()
        override fun onCreate() = true
        override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor {
            val columns = projection ?: arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val documentId = DocumentsContract.getDocumentId(uri)
            val children = if (uri.lastPathSegment == "children") when (documentId) {
                "root" -> listOf("root/book.md", "root/images")
                "root/images" -> listOf("root/images/a.png")
                else -> emptyList()
            } else listOf(documentId)
            return MatrixCursor(columns).apply {
                children.forEach { child ->
                    addRow(columns.map { column -> when (column) {
                        DocumentsContract.Document.COLUMN_DOCUMENT_ID -> child
                        DocumentsContract.Document.COLUMN_DISPLAY_NAME -> child.substringAfterLast('/')
                        DocumentsContract.Document.COLUMN_SIZE -> files[child]?.length() ?: 0L
                        DocumentsContract.Document.COLUMN_LAST_MODIFIED -> files[child]?.lastModified() ?: 0L
                        else -> null
                    } }.toTypedArray<Any?>())
                }
            }
        }
        override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
            val id = DocumentsContract.getDocumentId(uri)
            opened += id
            return ParcelFileDescriptor.open(files[id] ?: throw FileNotFoundException(id), ParcelFileDescriptor.MODE_READ_ONLY)
        }
        override fun getType(uri: Uri) = "text/markdown"
        override fun insert(uri: Uri, values: ContentValues?): Uri? = null
        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
        override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0
    }

    @Test fun authorizedTreeCopiesRelativeImagesButSingleFileCannot() {
        val context: Application = ApplicationProvider.getApplicationContext()
        val provider = Robolectric.setupContentProvider(TreeProvider::class.java, "markdown.test")
        provider.files["root/book.md"] = File(temporary.root, "book.md").apply {
            writeText("# Book\n\n![good](images/a.png)\n![escape](../secret.png)\n![network](https://example.org/image.png)")
        }
        provider.files["root/images/a.png"] = File(temporary.root, "a.png").apply { writeBytes(byteArrayOf(7, 8, 9)) }
        val tree = DocumentsContract.buildTreeDocumentUri("markdown.test", "root")
        val parent = DocumentsContract.buildDocumentUriUsingTree(tree, "root")
        val source = DocumentsContract.buildDocumentUriUsingTree(tree, "root/book.md")
        val single = MarkdownSnapshotStore.create(context, UUID.randomUUID().toString(), source.toString())
        assertTrue(MarkdownSnapshotStore.assets(single).isEmpty())
        assertEquals(listOf("root/book.md"), provider.opened)
        val authorized = MarkdownSnapshotStore.create(context, UUID.randomUUID().toString(), source.toString(), parent.toString())
        assertEquals(setOf("images/a.png"), MarkdownSnapshotStore.assets(authorized).keys)
        assertTrue(provider.opened.none { it.contains("secret") || it.contains("https") })
        provider.files.clear()
        assertTrue(runCatching { MarkdownSnapshotStore.sourceChanged(context, authorized, source.toString()) }.isFailure)
        val parser = MarkdownParser(context)
        try {
            assertEquals(1, parser.parse(authorized.path).chapters.size)
            assertTrue(parser.searchText.contains("Book"))
        } finally { parser.close() }
    }
}
