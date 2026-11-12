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
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class WebdavBookDeletionTest {
    @Test
    fun queuedDeleteUsesTheCloudIdAfterTheLocalRowHasGone() {
        val cover = SyncFileEntry("cover.jpg", "cover", 20, 1)
        val book = SyncFileEntry("renamed.epub", "book", 100, 1, cover = cover)
        val manifest = SyncManifest(
            books = mapOf("wire" to book, "other" to book.copy(fileName = "other.epub")),
            data = mapOf("wire" to SyncFileEntry("progress.json", "data", 30, 1)),
            bookIdentities = mapOf("wire" to SyncBookIdentity("book", "EPUB", 100))
        )
        val pending = mapOf("local" to "renamed.epub")
        val ids = manifest.resolveBookDeletionIds(pending, mapOf("local" to "stale"))
        val updated = manifest.withBookDeletions(pending, ids, deletedAt = 10)

        assertEquals(mapOf("local" to "wire"), ids)
        assertEquals(setOf("other"), updated.activeRemoteBooks().keys)
        assertFalse("wire" in updated.data)
        assertFalse("wire" in updated.bookIdentities)
        assertEquals(
            DeletedBookEntry(10, "renamed.epub", "cover.jpg", "progress.json"),
            updated.deletedBooks["wire"]
        )
    }

    @Test
    fun retryKeepsTheOriginalDeletionAndCleanupTargets() {
        val original = DeletedBookEntry(10, "book.epub", "cover.jpg", "progress.json")
        val manifest = SyncManifest(deletedBooks = mapOf("wire" to original))
        val pending = mapOf("local" to "local.epub")
        val ids = manifest.resolveBookDeletionIds(pending, mapOf("local" to "wire"))

        assertEquals(manifest, manifest.withBookDeletions(pending, ids, deletedAt = 20))
    }

    @Test
    fun staleStateDoesNotRestoreABookDeletedInTheManifest() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val identityStore = SyncIdentityStore(db.syncStateDao())
            val books = BookRepositoryImpl(db.bookDao(), db.syncStateDao(), identityStore)
            val snapshots = PortableSnapshotManager(
                context, db, DataStoreManager(context), identityStore,
                MineruTokenStore(context), ExternalTtsTokenStore(context), TranslationTokenStore(context)
            )
            val stale = Book(
                "deleted", "Deleted book", "Author", "", null, BookFormat.EPUB,
                lastReadTime = 0, readingProgress = 0f, createdAt = 1, isCloudOnly = true
            )
            val live = stale.copy(id = "live", title = "Live book")
            books.insertBook(stale)
            books.insertBook(live)
            val remoteState = snapshots.capture(includeBookFiles = false).snapshot.toJson()
            db.bookDao().deleteBookById(stale.id)
            db.bookDao().deleteBookById(live.id)

            MockWebServer().use { server ->
                server.dispatcher = object : Dispatcher() {
                    override fun dispatch(request: RecordedRequest): MockResponse = when (request.method) {
                        "GET" -> MockResponse().setBody(remoteState).setHeader("ETag", "\"stale\"")
                        "MKCOL", "PUT" -> MockResponse().setResponseCode(201)
                        "PROPFIND" -> MockResponse().setResponseCode(207).setBody("<d:multistatus xmlns:d=\"DAV:\"/>")
                        else -> MockResponse().setResponseCode(404)
                    }
                }
                server.start()
                val config = WebdavConfig(
                    enabled = true,
                    serverUrl = server.url("/").toString(),
                    username = "test",
                    syncBookFiles = true,
                    syncLibraryOrganization = true,
                    syncProfileAndSettings = false
                )
                WebdavPortableStateSync(context, WebdavClient(), snapshots)
                    .sync(config, "test", deletedBookIds = setOf(stale.id))
            }
            assertEquals(listOf(live.id), db.bookDao().getAllBooksSnapshot().map { it.id })
        } finally {
            db.close()
        }
    }
}
