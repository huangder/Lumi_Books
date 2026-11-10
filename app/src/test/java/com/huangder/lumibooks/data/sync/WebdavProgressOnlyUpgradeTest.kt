package com.huangder.lumibooks.data.sync

import android.app.Application
import androidx.room.Room
import com.huangder.lumibooks.data.backup.PortableSnapshot
import com.huangder.lumibooks.data.backup.PortableSnapshotManager
import com.huangder.lumibooks.data.local.DataStoreManager
import com.huangder.lumibooks.data.local.database.AppDatabase
import com.huangder.lumibooks.data.local.entity.NoteEntity
import com.huangder.lumibooks.data.repository.BookRepositoryImpl
import com.huangder.lumibooks.domain.model.Book
import com.huangder.lumibooks.domain.model.BookFormat
import com.huangder.lumibooks.domain.model.WebdavConfig
import com.huangder.lumibooks.mineru.MineruTokenStore
import com.huangder.lumibooks.translation.TranslationTokenStore
import com.huangder.lumibooks.tts.ExternalTtsTokenStore
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class WebdavProgressOnlyUpgradeTest {
    @Test fun enabledExistingUserIsPromptedOnceAndFreshInstallIsNeverPromptedOnLaterEnable() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val existing = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        val fresh = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val enabled = WebdavConfig(enabled = true)
            val store = WebdavSyncUpgradeStore(existing.syncStateDao())
            assertTrue(store.shouldPrompt(enabled))
            assertTrue(WebdavSyncUpgradeStore(existing.syncStateDao()).shouldPrompt(enabled))
            store.acknowledge()
            assertFalse(WebdavSyncUpgradeStore(existing.syncStateDao()).shouldPrompt(enabled))
            val newInstall = WebdavSyncUpgradeStore(fresh.syncStateDao())
            assertFalse(newInstall.shouldPrompt(WebdavConfig()))
            assertFalse(newInstall.shouldPrompt(enabled))
        } finally {
            existing.close()
            fresh.close()
        }
    }

    @Test fun progressOnlyRoundTripWithIndependentImportsUploadsNoBookOrNoteAndKeepsLocalIds() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val databases = List(2) { Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build() }
        val repos = databases.map { BookRepositoryImpl(it.bookDao(), it.syncStateDao(), SyncIdentityStore(it.syncStateDao())) }
        val managers = databases.map { db -> PortableSnapshotManager(context, db, DataStoreManager(context),
            SyncIdentityStore(db.syncStateDao()), MineruTokenStore(context), ExternalTtsTokenStore(context), TranslationTokenStore(context)) }
        val remoteFiles = ConcurrentHashMap<String, String>()
        val uploads = CopyOnWriteArrayList<String>()
        val server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.requestUrl!!.encodedPath
                return when (request.method) {
                    "MKCOL" -> MockResponse().setResponseCode(201)
                    "GET" -> remoteFiles[path]?.let { MockResponse().setBody(it).setHeader("ETag", "\"v1\"") }
                        ?: MockResponse().setResponseCode(404)
                    "PUT" -> {
                        uploads += path
                        remoteFiles[path] = request.body.readUtf8()
                        MockResponse().setResponseCode(201)
                    }
                    "PROPFIND" -> MockResponse().setResponseCode(207).setBody("<d:multistatus xmlns:d=\"DAV:\"/>")
                    else -> MockResponse().setResponseCode(204)
                }
            }
        }
        server.start()
        try {
            val a = Book("phone-a", "Original title", "Author", "/a.epub", null, BookFormat.EPUB,
                lastReadTime = 200, readingProgress = .6f, locatorJson = "chapter-6", createdAt = 10)
            val b = a.copy(id = "phone-b", title = "Renamed", filePath = "/b.epub", createdAt = 300,
                lastReadTime = 400, readingProgress = 0f, locatorJson = "start").prepareFirstContentLink(false)
            repos[0].insertBook(a)
            databases[0].noteDao().insertNote(NoteEntity(bookId = a.id, chapterIndex = 0, startPosition = 0,
                endPosition = 1, selectedText = "private annotation", note = "local note", color = "red",
                createdAt = 1, syncId = "note"))
            val identity = SyncBookIdentity("a".repeat(64), "EPUB", 42)
            val initial = resolveWebdavContentIdentities(listOf(a), SyncManifest(), mapOf(a.id to identity), emptyMap(), "library")
            val manifest = SyncManifest(bookIdentities = initial.identities)
            val second = resolveWebdavContentIdentities(listOf(b), manifest, mapOf(b.id to identity), emptyMap(), "library")
            val config = WebdavConfig(enabled = true, serverUrl = server.url("/").toString(), username = "test",
                syncBookFiles = false, syncProfileAndSettings = false, syncLibraryOrganization = true,
                syncReadingProgressOnly = true)
            val syncs = managers.map { WebdavPortableStateSync(context, WebdavClient(), it) }
            syncs[0].sync(config, "test", initial.wireIds)
            assertEquals(.6f, PortableSnapshot.fromJson(remoteFiles.getValue("/LumiBooks/state-v1/state.json")).books.single().readingProgress)
            syncs[1].sync(config, "test")
            assertTrue(databases[1].bookDao().getAllBooksSnapshot().isEmpty())
            repos[1].insertBook(b)
            syncs[1].sync(config, "test", second.wireIds)
            assertEquals(.6f, repos[1].getBookById(b.id)!!.readingProgress)
            assertEquals("chapter-6", repos[1].getBookById(b.id)!!.locatorJson)
            assertEquals(b.id, databases[1].bookDao().getAllBooksSnapshot().single().id)
            assertFalse(repos[1].getBookById(b.id)!!.isCloudOnly)
            // Older releases may already have materialized a state-only placeholder locally.
            val cloudId = second.wireIds.getValue(b.id)
            repos[1].insertBook(a.copy(id = cloudId, filePath = "", isCloudOnly = true))
            databases[1].noteDao().insertNote(NoteEntity(bookId = cloudId, chapterIndex = 0, startPosition = 0,
                endPosition = 1, selectedText = "old cloud annotation", note = "keep me", color = "red",
                createdAt = 1, syncId = "old-cloud-note"))
            syncs[1].sync(config, "test", second.wireIds)
            assertEquals(.6f, repos[1].getBookById(b.id)!!.readingProgress)
            assertEquals(b.id, databases[1].bookDao().getAllBooksSnapshot().filterNot { it.isCloudOnly }.single().id)
            assertNotNull(repos[1].getBookById(cloudId))
            assertEquals("old-cloud-note", databases[1].noteDao().getNotesByBookId(b.id).first().single().syncId)
            repos[1].updateBook(repos[1].getBookById(b.id)!!.copy(lastReadTime = 500, readingProgress = 0f, locatorJson = null))
            syncs[1].sync(config, "test", second.wireIds)
            syncs[0].sync(config, "test", initial.wireIds)
            assertEquals(0f, repos[0].getBookById(a.id)!!.readingProgress)
            assertNull(repos[0].getBookById(a.id)!!.locatorJson)
            assertEquals(a.id, databases[0].bookDao().getAllBooksSnapshot().single().id)
            val uploaded = PortableSnapshot.fromJson(remoteFiles.getValue("/LumiBooks/state-v1/state.json"))
            assertTrue(uploaded.notes.isEmpty())
            assertTrue(uploaded.bookmarks.isEmpty())
            assertTrue(uploaded.readingRecords.isEmpty())
            assertTrue(uploaded.assets.isEmpty())
            assertTrue(uploads.none { "/books/" in it || "/assets/" in it })
            assertTrue(databases[0].noteDao().getNotesByBookId(a.id).first().isNotEmpty())
        } finally {
            server.shutdown()
            databases.forEach { it.close() }
        }
    }
}
