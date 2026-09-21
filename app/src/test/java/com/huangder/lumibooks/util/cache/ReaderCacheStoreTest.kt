package com.huangder.lumibooks.util.cache

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import com.huangder.lumibooks.util.BookFileAccess
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class ReaderCacheStoreTest {
    private class MirrorProvider : ContentProvider() {
        val sources = HashMap<String, File>()
        var opens = 0

        override fun onCreate(): Boolean = true

        override fun query(
            uri: Uri,
            projection: Array<out String>?,
            selection: String?,
            selectionArgs: Array<out String>?,
            sortOrder: String?
        ): Cursor {
            val file = sources[uri.path] ?: throw FileNotFoundException(uri.toString())
            val columns = projection ?: arrayOf(OpenableColumns.SIZE)
            return MatrixCursor(columns).apply {
                addRow(columns.map { column ->
                    when (column) {
                        OpenableColumns.SIZE -> file.length()
                        DocumentsContract.Document.COLUMN_LAST_MODIFIED -> file.lastModified()
                        else -> null
                    }
                }.toTypedArray())
            }
        }

        override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
            val file = sources[uri.path] ?: throw FileNotFoundException(uri.toString())
            opens++
            return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        }

        override fun getType(uri: Uri): String = "application/vnd.comicbook+zip"
        override fun insert(uri: Uri, values: ContentValues?): Uri? = null
        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
        override fun update(
            uri: Uri,
            values: ContentValues?,
            selection: String?,
            selectionArgs: Array<out String>?
        ): Int = 0
    }

    private lateinit var context: Context
    private lateinit var store: ReaderCacheStore
    private lateinit var root: File
    private lateinit var provider: MirrorProvider

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication().applicationContext
        store = ReaderCacheStore.get(context)
        store.clear()
        root = File(context.cacheDir, "reader_cache")
        provider = Robolectric.setupContentProvider(MirrorProvider::class.java, MIRROR_AUTHORITY)
    }

    @After
    fun tearDown() {
        store.clear()
    }

    @Test
    fun `metadata write recreates cache root deleted during process lifetime`() {
        val source = File(context.filesDir, "reader-cache-source.txt").apply {
            parentFile?.mkdirs()
            writeText("content")
        }
        val fingerprint = BookFingerprint.resolve(context, source.absolutePath)
        val generationBeforeClear = store.currentGeneration()

        assertTrue(root.deleteRecursively())
        store.writeMetadata("test", fingerprint, JSONObject().put("value", "restored"))

        assertTrue(root.isDirectory)
        assertEquals("restored", store.readMetadata("test", fingerprint)?.getString("value"))
        assertEquals(generationBeforeClear + 1L, store.currentGeneration())
        source.delete()
    }

    @Test
    fun `generation advances once when cache root disappears`() {
        val initialGeneration = store.currentGeneration()

        assertTrue(root.deleteRecursively())
        val rebuiltGeneration = store.currentGeneration()

        assertEquals(initialGeneration + 1L, rebuiltGeneration)
        assertEquals(rebuiltGeneration, store.currentGeneration())
        assertEquals(rebuiltGeneration, store.currentGeneration())
    }

    @Test
    fun `session mirrors are copied once and deleted after the final lease`() {
        val source = sourceFile("shared.cbz", "comic-data")
        val uri = register("shared", source)

        val first = store.acquireContentUriMirror(uri.toString(), MirrorLifetime.SESSION)!!
        val second = store.acquireContentUriMirror(uri.toString(), MirrorLifetime.SESSION)!!

        assertEquals(first.file, second.file)
        assertEquals(1, provider.opens)
        assertEquals("comic-data", first.file.readText())
        first.close()
        assertTrue(second.file.isFile)
        first.close()
        assertTrue(second.file.isFile)
        second.close()
        assertFalse(second.file.exists())
    }

    @Test
    fun `seekable sources release their shared session mirror on close`() {
        val uri = register("seekable", sourceFile("seekable.cbz", "seekable-data"))
        val first = BookFileAccess.openSeekable(context, uri.toString(), false, MirrorLifetime.SESSION)
        val second = BookFileAccess.openSeekable(context, uri.toString(), false, MirrorLifetime.SESSION)
        val mirror = File(first.path)

        assertEquals(first.path, second.path)
        first.close()
        assertTrue(mirror.isFile)
        second.close()
        assertFalse(mirror.exists())
    }

    @Test
    fun `clear defers deletion of an active session mirror`() {
        val uri = register("active", sourceFile("active.cbz", "active-data"))
        val lease = store.acquireContentUriMirror(uri.toString(), MirrorLifetime.SESSION)!!

        store.clear()

        assertTrue(lease.file.isFile)
        lease.close()
        assertFalse(lease.file.exists())
    }

    @Test
    fun `invalidation keeps an active mirror readable until release`() {
        val uri = register("invalidated", sourceFile("invalidated.cbz", "still-readable"))
        val lease = store.acquireContentUriMirror(uri.toString(), MirrorLifetime.SESSION)!!

        store.invalidate(uri.toString())

        assertEquals("still-readable", lease.file.readText())
        lease.close()
        assertFalse(lease.file.exists())
    }

    @Test
    fun `changed source gets a distinct session mirror`() {
        val source = sourceFile("changed.cbz", "first")
        val uri = register("changed", source)
        val first = store.acquireContentUriMirror(uri.toString(), MirrorLifetime.SESSION)!!
        source.writeText("second-version")
        source.setLastModified(source.lastModified() + 2_000L)

        val second = store.acquireContentUriMirror(uri.toString(), MirrorLifetime.SESSION)!!

        assertNotEquals(first.file, second.file)
        assertEquals(2, provider.opens)
        first.close()
        second.close()
    }

    @Test
    fun `concurrent session requests share one copy`() {
        val uri = register("concurrent", sourceFile("concurrent.cbz", "parallel"))
        val executor = Executors.newFixedThreadPool(2)
        try {
            val futures = List(2) {
                executor.submit(Callable {
                    store.acquireContentUriMirror(uri.toString(), MirrorLifetime.SESSION)!!
                })
            }
            val leases = futures.map { it.get() }
            assertEquals(leases[0].file, leases[1].file)
            assertEquals(1, provider.opens)
            leases.forEach { it.close() }
            assertFalse(leases[0].file.exists())
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun `failed session copy leaves no partial file`() {
        val missing = Uri.parse("content://$MIRROR_AUTHORITY/missing")

        assertThrows(IOException::class.java) {
            store.acquireContentUriMirror(missing.toString(), MirrorLifetime.SESSION)
        }
        assertTrue(root.listFiles().orEmpty().none { it.name.endsWith(".tmp") })
    }

    @Test
    fun `legacy persistent comic mirrors are removed`() {
        val book = File(root, "comic_legacy.book").apply { writeText("old") }
        val metadata = File(root, "comic_legacy.json").apply { writeText("{}") }

        store.cleanupStaleMirrorsForTesting()

        assertFalse(book.exists())
        assertFalse(metadata.exists())
    }

    private fun sourceFile(name: String, content: String): File =
        File(context.cacheDir, name).apply {
            writeText(content)
            setLastModified(System.currentTimeMillis())
        }

    private fun register(name: String, source: File): Uri {
        val uri = Uri.parse("content://$MIRROR_AUTHORITY/$name")
        provider.sources[uri.path!!] = source
        return uri
    }

    private companion object {
        const val MIRROR_AUTHORITY = "com.huangder.lumibooks.test.mirror"
    }
}
