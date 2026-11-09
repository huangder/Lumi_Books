package com.huangder.lumibooks.data.sync

import android.app.Application
import androidx.room.Room
import com.huangder.lumibooks.data.backup.PortableSnapshotManager
import com.huangder.lumibooks.data.local.DataStoreManager
import com.huangder.lumibooks.data.local.database.AppDatabase
import com.huangder.lumibooks.data.repository.BookRepositoryImpl
import com.huangder.lumibooks.domain.model.Book
import com.huangder.lumibooks.domain.model.BookFormat
import com.huangder.lumibooks.domain.model.WebdavConfig
import com.huangder.lumibooks.mineru.MineruTokenStore
import com.huangder.lumibooks.translation.TranslationTokenStore
import com.huangder.lumibooks.tts.ExternalTtsTokenStore
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class WebdavProgressPersistenceTest {
    @Test
    fun `persisted wire alias survives detachment and deletion so tombstone keeps cloud identity`() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        val identities = SyncIdentityStore(db.syncStateDao())
        val repository = BookRepositoryImpl(db.bookDao(), db.syncStateDao(), identities)
        val manager = PortableSnapshotManager(context, db, DataStoreManager(context), identities,
            MineruTokenStore(context), ExternalTtsTokenStore(context), TranslationTokenStore(context))
        try {
            val original = book().copy(remoteLibraryKey = "library-a", remoteFileName = "cloud.epub")
            repository.insertBook(original)
            WebdavBookIdentityStore(db.syncStateDao()).save("library-a", mapOf("local" to "cloud"))

            // Receiving a remote deletion detaches a downloaded body without discarding its wire identity.
            repository.clearRemoteAssociation("local")
            val detached = repository.getBookById("local")!!
            assertNull(detached.remoteLibraryKey)
            assertFalse(detached.isCloudOnly)
            assertEquals(mapOf("local" to "cloud"), WebdavBookIdentityStore(db.syncStateDao()).read("library-a"))

            // A later local deletion must publish the cloud-ID tombstone even though no book row remains.
            repository.deleteBook(detached)
            val freshStore = WebdavBookIdentityStore(db.syncStateDao())
            assertEquals(mapOf("local" to "cloud"), freshStore.read("library-a"))
            assertTrue(freshStore.read("library-b").isEmpty())
            freshStore.save("library-b", mapOf("local" to "other-cloud"))
            assertEquals(mapOf("local" to "cloud"), freshStore.read("library-a"))
            assertEquals(mapOf("local" to "other-cloud"), freshStore.read("library-b"))

            val captured = manager.capture(includeBookFiles = false).snapshot
            assertTrue(captured.books.isEmpty())
            assertTrue(captured.tombstones.any { it.namespace == "book" && it.itemId == "local" })
            val wire = captured.remapWebdavBookIds(freshStore.read("library-a"))
            assertTrue(wire.books.isEmpty())
            assertTrue(wire.tombstones.any { it.namespace == "book" && it.itemId == "cloud" })
            assertFalse(wire.tombstones.any { it.namespace == "book" && it.itemId == "local" })
            assertTrue(db.bookDao().getAllBooksSnapshot().isEmpty())
        } finally {
            db.close()
        }
    }

    @Test
    fun `legacy data uses actual position time and preserves intentional rewind`() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        val repository = BookRepositoryImpl(db.bookDao(), db.syncStateDao(), SyncIdentityStore(db.syncStateDao()))
        try {
            // A freshly imported book has a newer import time but has never been read.
            repository.insertBook(book().copy(createdAt = 300, lastReadTime = 300, metadataUpdatedAt = 500))
            applySyncedReadingPosition(repository, "local", position(0.6, 200, "chapter-6"))
            assertEquals(0.6f, repository.getBookById("local")!!.readingProgress)
            assertEquals(200L, repository.getBookById("local")!!.lastReadTime)

            // A metadata-only upload containing an older position must not clear the locator.
            applySyncedReadingPosition(repository, "local", position(0.0, 100, null))
            assertEquals("chapter-6", repository.getBookById("local")!!.locatorJson)

            // Explicitly returning to the beginning is a new reading event, and clears old locators.
            applySyncedReadingPosition(repository, "local", position(0.0, 600, null))
            assertEquals(0f, repository.getBookById("local")!!.readingProgress)
            assertNull(repository.getBookById("local")!!.locatorJson)
            assertEquals(600L, repository.getBookById("local")!!.lastReadTime)
        } finally {
            db.close()
        }
    }

    @Test
    fun `state sync round trip keeps one local row and source binding under a different cloud ID`() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        val identities = SyncIdentityStore(db.syncStateDao())
        val repository = BookRepositoryImpl(db.bookDao(), db.syncStateDao(), identities)
        val manager = PortableSnapshotManager(context, db, DataStoreManager(context), identities,
            MineruTokenStore(context), ExternalTtsTokenStore(context), TranslationTokenStore(context))
        val remoteFiles = ConcurrentHashMap<String, String>()
        val server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.requestUrl!!.encodedPath
                return when (request.method) {
                    "MKCOL" -> MockResponse().setResponseCode(201)
                    "GET" -> remoteFiles[path]?.let { MockResponse().setBody(it).setHeader("ETag", "\"v1\"") }
                        ?: MockResponse().setResponseCode(404)
                    "PUT" -> { remoteFiles[path] = request.body.readUtf8(); MockResponse().setResponseCode(201) }
                    "PROPFIND" -> MockResponse().setResponseCode(207)
                        .setBody("<d:multistatus xmlns:d=\"DAV:\"/>")
                    else -> MockResponse().setResponseCode(204)
                }
            }
        }
        server.start()
        try {
            repository.insertBook(book().copy(
                createdAt = 300, lastReadTime = 300, sourceUri = "content://source/local",
                sourceDocumentKey = "source:local", sourceSha256 = "a".repeat(64)
            ))
            val initial = manager.capture(includeBookFiles = false).snapshot
            val cloud = initial.remapWebdavBookIds(mapOf("local" to "cloud")).let { snapshot ->
                snapshot.copy(books = snapshot.books.map { it.copy(
                    readingProgress = 0.65f, lastReadTime = 200, locatorJson = "chapter-6",
                    createdAt = 10, metadataUpdatedAt = 20
                ) })
            }
            remoteFiles["/LumiBooks/state-v1/state.json"] = cloud.toJson()
            val config = WebdavConfig(enabled = true, serverUrl = server.url("/").toString(),
                username = "test", syncProfileAndSettings = false, syncLibraryOrganization = true)
            val sync = WebdavPortableStateSync(context, WebdavClient(), manager)
            repeat(2) { sync.sync(config, "test", mapOf("local" to "cloud")) }

            val row = db.bookDao().getAllBooksSnapshot().single()
            assertEquals("local", row.id)
            assertEquals(0.65f, row.readingProgress)
            assertEquals("chapter-6", row.locatorJson)
            assertEquals("/local/book.epub", row.filePath)
            assertFalse(row.isCloudOnly)
            assertEquals("content://source/local", row.sourceUri)
            assertEquals("source:local", row.sourceDocumentKey)
            assertEquals("a".repeat(64), row.sourceSha256)
            val uploaded = com.huangder.lumibooks.data.backup.PortableSnapshot.fromJson(
                remoteFiles.getValue("/LumiBooks/state-v1/state.json"))
            assertEquals("cloud", uploaded.books.single().id)
            assertEquals(0.65f, uploaded.books.single().readingProgress)
        } finally {
            server.shutdown()
            db.close()
        }
    }

    private fun position(progress: Double, time: Long, locator: String?) = JSONObject().apply {
        put("readingProgress", progress); put("lastReadTime", time)
        if (locator != null) put("locatorJson", locator)
        put("metadata", JSONObject().put("createdAt", 10))
    }

    private fun book() = Book(id = "local", title = "Title", author = "Author",
        filePath = "/local/book.epub", coverPath = null, format = BookFormat.EPUB,
        lastReadTime = 0, readingProgress = 0f, createdAt = 10)
}
