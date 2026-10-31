package com.huangder.lumibooks.util

import android.app.Application
import android.content.Intent
import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Robolectric
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class BookFileAccessTest {
    @get:Rule val temporary = TemporaryFolder()
    private val context: Application = ApplicationProvider.getApplicationContext()
    private val authority = "books.test"
    private val document = DocumentsContract.buildDocumentUri(authority, "primary:Books/book.epub")

    class TreeOnlyProvider : ContentProvider() {
        lateinit var file: java.io.File
        lateinit var grantedDocument: Uri
        private fun checkAccess(uri: Uri) {
            if (uri != grantedDocument) throw SecurityException("Requires tree grant")
        }
        override fun onCreate() = true
        override fun getType(uri: Uri) = "application/epub+zip"
        override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor {
            checkAccess(uri)
            val columns = projection ?: arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            return MatrixCursor(columns).apply {
                addRow(columns.map { column -> when (column) {
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME -> "book.epub"
                    DocumentsContract.Document.COLUMN_SIZE -> file.length()
                    DocumentsContract.Document.COLUMN_LAST_MODIFIED -> 1234L
                    else -> null
                } }.toTypedArray<Any?>())
            }
        }
        override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
            checkAccess(uri)
            return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        }
        override fun insert(uri: Uri, values: ContentValues?): Uri? = null
        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
        override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0
    }

    @Test fun legacyUriCanBeReadThroughStreamsDescriptorsAndSeekableCache() {
        val tree = grantTree("primary:Books")
        val provider = Robolectric.setupContentProvider(TreeOnlyProvider::class.java, authority)
        provider.grantedDocument = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getDocumentId(document))
        provider.file = temporary.newFile().apply { writeText("book contents") }
        val location = document.toString()
        assertEquals("book contents", BookFileAccess.openInputStream(context, location).use { it.reader().readText() })
        assertEquals("book.epub", BookFileAccess.displayName(context, location))
        assertEquals(provider.file.length(), BookFileAccess.size(context, location))
        BookFileAccess.openDescriptor(context, location).use {
            assertEquals(provider.file.length(), it.statSize)
        }
        BookFileAccess.openSeekable(context, location).use {
            assertEquals("book contents", java.io.File(it.path).readText())
        }
    }

    private fun grantTree(root: String, writable: Boolean = false): Uri {
        val tree = DocumentsContract.buildTreeDocumentUri(authority, root)
        context.contentResolver.takePersistableUriPermission(tree,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or
                if (writable) Intent.FLAG_GRANT_WRITE_URI_PERMISSION else 0)
        return tree
    }

    @Test fun keepsWorkingDocumentUriWithoutTryingTreeGrants() {
        grantTree("primary:Books")
        val tried = mutableListOf<Uri>()
        assertEquals("opened", BookFileAccess.withDocumentAccess(context, document) {
            tried += it
            "opened"
        })
        assertEquals(listOf(document), tried)
    }

    @Test fun reopensLegacyDocumentUsingExistingTreeGrant() {
        val unrelated = grantTree("primary:Other")
        val tree = grantTree("primary:Books")
        val expected = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getDocumentId(document))
        val tried = mutableListOf<Uri>()
        val result = BookFileAccess.withDocumentAccess(context, document) {
            tried += it
            if (it != expected) throw SecurityException("Not granted")
            "book contents"
        }
        assertEquals("book contents", result)
        assertEquals(document, tried.first())
        assertEquals(expected, tried.last())
        assertEquals(setOf(unrelated, tree), context.contentResolver.persistedUriPermissions.map { it.uri }.toSet())
    }

    @Test fun reportsOriginalDenialWhenNoGrantedTreeCanReadDocument() {
        grantTree("primary:Other")
        val denied = SecurityException("Original permission denial")
        val result = assertThrows(SecurityException::class.java) {
            BookFileAccess.withDocumentAccess(context, document) {
                if (it == document) throw denied
                throw java.io.FileNotFoundException("Outside granted tree")
            }
        }
        assertSame(denied, result)
    }

    @Test fun readOnlyGrantCannotBeUsedForWriting() {
        grantTree("primary:Books")
        var attempts = 0
        assertThrows(SecurityException::class.java) {
            BookFileAccess.withDocumentAccess(context, document, writable = true) {
                attempts++
                throw SecurityException("Read only")
            }
        }
        assertEquals(1, attempts)
    }
}
