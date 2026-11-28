package com.huangder.lumibooks.data.sync

import android.app.Application
import androidx.room.Room
import com.huangder.lumibooks.data.backup.PortableSnapshotManager
import com.huangder.lumibooks.data.local.DataStoreManager
import com.huangder.lumibooks.data.local.WebdavTokenStore
import com.huangder.lumibooks.data.local.database.AppDatabase
import com.huangder.lumibooks.data.local.entity.NoteEntity
import com.huangder.lumibooks.data.repository.BookRepositoryImpl
import com.huangder.lumibooks.data.repository.ReadingRepositoryImpl
import com.huangder.lumibooks.domain.model.Book
import com.huangder.lumibooks.domain.model.BookFormat
import com.huangder.lumibooks.domain.model.WebdavConfig
import com.huangder.lumibooks.mineru.MineruTokenStore
import com.huangder.lumibooks.translation.TranslationTokenStore
import com.huangder.lumibooks.tts.ExternalTtsTokenStore
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.async
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.mockwebserver.SocketPolicy
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class WebdavReconciliationTest {
    @Test fun archivedOriginalSurvivesRepeatedSyncAndOtherDeviceGetsOnlyTheRepairedBody() = scenario {
        val original = a.local("original", archived = true)
        val body = entry("legacy original.txt", text)
        val ghost = entry("missing ghost.txt", text)
        a.books.insertBook(cloud("main", body))
        a.books.insertBook(cloud("duplicate", ghost).copy(lastReadTime = 200, readingProgress = .7f))
        a.books.insertBook(cloud("state-only", ghost))
        a.note(original.id, "original-note")
        a.note("duplicate", "duplicate-note")
        a.note("state-only", "state-note")
        a.aliases.save(libraryKey, mapOf(original.id to "main"))
        b.books.insertBook(cloud("state-only", ghost).copy(filePath = ""))
        remote.manifest = SyncManifest(books = mapOf("main" to body, "duplicate" to ghost))
        repeat(2) {
            a.sync()
            val kept = a.books.getAllBooks().first().single()
            assertEquals(original.id, kept.id)
            assertEquals(original.filePath, kept.filePath)
            assertTrue(kept.isArchived)
            assertFalse(kept.isCloudOnly)
            assertEquals(text, File(kept.filePath).readText())
            assertEquals(.7f, kept.readingProgress)
            assertEquals(setOf("original-note", "duplicate-note", "state-note"), a.notes(kept.id))
            assertEquals(4, a.books.getBooksForWebdav().size)
            assertTrue(a.db.syncStateDao().getAllTombstones().isEmpty())
            assertEquals(text, remote.files["books/${body.fileName}"])
            assertTrue(remote.manifest.deletedBooks.isEmpty())
        }
        repeat(2) { b.sync() }
        assertEquals(listOf("main"), b.books.getAllBooks().first().map { it.id })
        assertTrue(b.books.searchBooks("state-only").first().isEmpty())
        assertTrue(b.manager.downloadBook("main") { "test" }.success)
        assertEquals(text, File(b.books.getBookById("main")!!.filePath).readText())
    }

    @Test fun distinctPhysicalCopiesAndAvailableRemoteCopiesAreNotFoldedByTitleOrDigest() = scenario {
        a.local("copy-a")
        a.local("copy-b")
        val first = entry("first.txt", text)
        val second = entry("second.txt", text)
        val different = entry("different.txt", text.reversed())
        remote.manifest = SyncManifest(books = mapOf("first" to first, "second" to second, "different" to different))
        remote.files["books/first.txt"] = text
        remote.files["books/second.txt"] = text
        remote.files["books/different.txt"] = text.reversed()
        a.sync()
        assertEquals(setOf("copy-a", "copy-b"), a.books.getAllBooks().first().filterNot { it.isCloudOnly }.map { it.id }.toSet())
        assertTrue(a.books.getAllBooks().first().any { it.id == "different" })
        assertTrue(remote.manifest.books.keys.containsAll(listOf("first", "second", "different")))
        b.local("independent")
        b.sync()
        val wire = b.aliases.read(libraryKey).getValue("independent")
        assertTrue(b.books.getAllBooks().first().map { it.id }.containsAll(
            (setOf("first", "second", "different") - wire) + "independent"
        ))
    }

    @Test fun onlyDefinitiveMissingResponsesHideAndRestoredBodyReappears() = scenario {
        val body = entry("any title.txt", text)
        remote.manifest = SyncManifest(books = mapOf("cloud" to body))
        remote.files["books/${body.fileName}"] = text
        a.sync()
        a.note("cloud", "kept")
        for (failure in listOf(401, 403, 503)) {
            remote.statuses["books/${body.fileName}"] = failure
            assertFalse(a.manager.fullSync { "test" }.success)
            assertEquals("cloud", a.books.getAllBooks().first().single().id)
        }
        remote.files.remove("books/${body.fileName}")
        for (missing in listOf(404, 410)) {
            remote.statuses["books/${body.fileName}"] = missing
            a.sync()
            assertTrue(a.books.getAllBooks().first().isEmpty())
            assertEquals(setOf("kept"), a.notes("cloud"))
            val requests = remote.downloads.get()
            assertFalse(a.manager.downloadBook("cloud") { "test" }.success)
            assertEquals(requests, remote.downloads.get())
            remote.statuses.clear()
            remote.files["books/${body.fileName}"] = text
            a.sync()
            assertEquals("cloud", a.books.getAllBooks().first().single().id)
        }
        remote.files["manifest.json"] = "{\"version\":2,\"books\":[]}"
        assertFalse(a.manager.fullSync { "test" }.success)
        assertEquals("cloud", a.books.getAllBooks().first().single().id)
    }

    @Test fun downloadUsesManifestNameAndDoesNotRegisterInterruptedOrCorruptBytes() = scenario {
        val body = entry("non UUID 中文 name.txt", text)
        val path = "books/${body.fileName}"
        val original = a.local("original", contents = "another local book")
        remote.manifest = SyncManifest(books = mapOf("cloud" to body))
        remote.files[path] = text
        a.sync()
        remote.interrupt = path
        assertFalse(a.manager.downloadBook("cloud") { "test" }.success)
        remote.interrupt = null
        for (corrupt in listOf(text.take(3), text.reversed())) {
            remote.files[path] = corrupt
            assertFalse(a.manager.downloadBook("cloud") { "test" }.success)
            assertEquals("", a.books.getBookById("cloud")!!.filePath)
            assertEquals("another local book", File(original.filePath).readText())
        }
        assertTrue(File(context.cacheDir.path).listFiles().orEmpty().none { it.name.startsWith("webdav-") && it.extension == "part" })
        remote.files[path] = text
        assertTrue(a.manager.downloadBook("cloud") { "test" }.success)
        assertEquals(text, File(a.books.getBookById("cloud")!!.filePath).readText())
    }

    @Test fun legacyReadingDataEmptyOrMalformedDoesNotClearLocalAnnotations() = scenario {
        val local = a.local("original")
        a.note(local.id, "local-note")
        val body = entry("body.txt", text)
        val data = SyncFileEntry("legacy-notes.json", "", 0, 1000)
        remote.manifest = SyncManifest(books = mapOf(local.id to body), data = mapOf(local.id to data))
        remote.files["books/${body.fileName}"] = text
        val path = "data/${data.fileName}"
        for (invalid in listOf("", "{", "{\"notes\":[null],\"readingProgress\":0.9,\"lastReadTime\":900}")) {
            remote.files[path] = invalid
            assertFalse(a.manager.fullSync { "test" }.success)
            assertEquals(setOf("local-note"), a.notes(local.id))
            assertEquals(0f, a.books.getBookById(local.id)!!.readingProgress)
        }
        remote.files[path] = "{\"notes\":[],\"bookmarks\":[]}"
        a.sync()
        assertEquals(setOf("local-note"), a.notes(local.id))
    }

    @Test fun pendingDeletionDoesNotDeleteSharedBodyCoverOrReadingData() = scenario {
        val body = entry("shared.txt", text).copy(cover = entry("shared.jpg", "cover"))
        val data = entry("shared.json", "{}")
        remote.manifest = SyncManifest(books = mapOf("removed" to body, "live" to body),
            data = mapOf("removed" to data, "live" to data))
        remote.files["books/shared.txt"] = text
        remote.files["covers/shared.jpg"] = "cover"
        remote.files["data/shared.json"] = "{}"
        dataStore.saveWebdavConfig(config.copy(syncReadingData = false, syncLibraryOrganization = false))
        a.aliases.savePendingDeletes(libraryKey, mapOf("removed" to body.fileName))
        a.sync()
        assertEquals(setOf("live"), remote.manifest.activeRemoteBooks().keys)
        assertTrue(remote.files.keys.containsAll(listOf("books/shared.txt", "covers/shared.jpg", "data/shared.json")))
    }

    @Test fun twoDevicesInterleavingManifestWritesRetryWithoutLosingEitherBook() = scenario {
        a.local("a", contents = "first device")
        b.local("b", contents = "second device")
        remote.manifest = SyncManifest()
        val reached = CountDownLatch(1)
        val continueWrite = CountDownLatch(1)
        remote.beforePut["manifest.json"] = {
            reached.countDown()
            check(continueWrite.await(30, TimeUnit.SECONDS))
        }
        kotlinx.coroutines.coroutineScope {
            val first = async(Dispatchers.IO) { a.sync() }
            try {
                assertTrue(withContext(Dispatchers.IO) { reached.await(30, TimeUnit.SECONDS) })
                b.sync()
            } finally {
                continueWrite.countDown()
            }
            first.await()
        }
        assertTrue(remote.conflicts.get() > 0)
        repeat(2) { a.sync(); b.sync() }
        assertEquals(2, remote.manifest.books.size)
        assertEquals(setOf("first device", "second device"), remote.manifest.books.values.map { remote.files["books/${it.fileName}"] }.toSet())
        assertEquals(2, a.books.getAllBooks().first().size)
        assertEquals(2, b.books.getAllBooks().first().size)
    }

    @Test fun twoDevicesInterleavingLegacyDataWritesKeepBothAnnotations() = scenario {
        val first = a.local("first")
        val second = b.local("second")
        a.note(first.id, "from-a")
        b.note(second.id, "from-b")
        val body = entry("shared.txt", text)
        val data = SyncFileEntry("legacy.json", "", 0, 10)
        remote.manifest = SyncManifest(books = mapOf("shared" to body), data = mapOf("shared" to data))
        remote.files["books/${body.fileName}"] = text
        val path = "data/${data.fileName}"
        remote.files[path] = "{\"notes\":[],\"bookmarks\":[]}"
        val reached = CountDownLatch(1)
        val continueWrite = CountDownLatch(1)
        remote.beforePut[path] = {
            reached.countDown()
            check(continueWrite.await(30, TimeUnit.SECONDS))
        }
        kotlinx.coroutines.coroutineScope {
            val firstSync = async(Dispatchers.IO) { a.sync() }
            try {
                assertTrue(withContext(Dispatchers.IO) { reached.await(30, TimeUnit.SECONDS) })
                b.sync()
            } finally {
                continueWrite.countDown()
            }
            firstSync.await()
        }
        assertTrue(remote.conflicts.get() > 0)
        b.sync()
        val expected = setOf("from-a", "from-b")
        val legacy = JSONObject(remote.files.getValue(path)).getJSONArray("notes")
        assertEquals(expected, (0 until legacy.length()).map { legacy.getJSONObject(it).getString("syncId") }.toSet())
        assertEquals(expected, a.notes(first.id))
        assertEquals(expected, b.notes(second.id))
    }

    @Test fun editedBookReplacesItsCloudCopyInsteadOfAddingAnother() = scenario {
        val book = a.local("edited")
        a.sync()
        val wire = remote.manifest.books.keys.single()
        var latest = text
        repeat(3) { round ->
            latest = text + "fixed typo $round\n"
            File(book.filePath).writeText(latest)
            repeat(2) { a.sync() }
            assertEquals(setOf(wire), remote.manifest.books.keys)
            assertEquals(digest(latest), remote.manifest.books.getValue(wire).sha256)
            assertEquals(digest(latest), remote.manifest.bookIdentities.getValue(wire).sha256)
            assertEquals(listOf(latest), remote.files.filterKeys { it.startsWith("books/") }.values.toList())
            assertEquals(setOf(wire), remote.manifest.data.keys)
            assertEquals(listOf(book.id), a.books.getBooksForWebdav().map { it.id })
            assertTrue(remote.manifest.deletedBooks.isEmpty())
        }
        assertEquals(remote.manifest.books.getValue(wire).fileName, a.books.getBookById(book.id)?.remoteFileName)
    }

    @Test fun otherDeviceReceivesTheEditAndNeverPublishesTheOldText() = scenario {
        val first = a.local("first")
        val second = b.local("second", managed = true)
        val kept = b.local("kept", contents = other)
        a.local("kept-source", contents = other)
        a.sync()
        b.sync()
        val edited = text + "fixed typo\n"
        val otherEdited = other + "fixed typo\n"
        File(first.filePath).writeText(edited)
        File(a.books.getBookById("kept-source")!!.filePath).writeText(otherEdited)
        a.sync()
        repeat(2) { b.sync() }
        // An app-managed copy follows the edit; a file outside app storage is left alone.
        assertEquals(edited, File(second.filePath).readText())
        assertEquals(other, File(kept.filePath).readText())
        assertEquals(setOf(second.id, kept.id), b.books.getBooksForWebdav().map { it.id }.toSet())
        repeat(2) { a.sync(); b.sync() }
        assertEquals(edited, File(first.filePath).readText())
        assertEquals(2, remote.manifest.books.size)
        assertEquals(setOf(digest(edited), digest(otherEdited)), remote.manifest.books.values.map { it.sha256 }.toSet())
        assertEquals(setOf(edited, otherEdited), remote.files.filterKeys { it.startsWith("books/") }.values.toSet())
        assertEquals(2, a.books.getBooksForWebdav().size)
        assertEquals(2, b.books.getBooksForWebdav().size)
    }

    @Test fun staleCopyNeverRefillsAMissingCloudBody() = scenario {
        val first = a.local("first")
        val second = b.local("second")
        a.sync()
        b.sync()
        val edited = text + "fixed typo\n"
        File(first.filePath).writeText(edited)
        a.sync()
        b.sync()
        remote.files.keys.removeIf { it.startsWith("books/") }
        b.sync()
        assertTrue(remote.files.keys.none { it.startsWith("books/") })
        assertEquals(digest(edited), remote.manifest.books.values.single().sha256)
        a.sync()
        assertEquals(listOf(edited), remote.files.filterKeys { it.startsWith("books/") }.values.toList())
        b.sync()
        assertEquals(text, File(second.filePath).readText())
        assertEquals(edited, File(first.filePath).readText())
        assertEquals(1, remote.manifest.books.size)
    }

    @Test fun openBookIsNotSwappedUntilItIsClosed() = scenario {
        val first = a.local("first")
        val second = b.local("second", managed = true)
        a.sync()
        b.sync()
        val edited = text + "fixed typo\n"
        File(first.filePath).writeText(edited)
        a.sync()
        WebdavOpenBooks.setOpen(second.id, true)
        try {
            b.sync()
            assertEquals(text, File(second.filePath).readText())
        } finally {
            WebdavOpenBooks.setOpen(second.id, false)
        }
        b.sync()
        assertEquals(edited, File(second.filePath).readText())
        assertEquals(digest(edited), remote.manifest.books.values.single().sha256)
    }

    @Test fun failedReplacementUploadKeepsThePublishedCopyAndRecoversLater() = scenario {
        val book = a.local("edited")
        a.sync()
        val wire = remote.manifest.books.keys.single()
        val published = remote.manifest.books.getValue(wire)
        val edited = text + "fixed typo\n"
        File(book.filePath).writeText(edited)
        val replacement = "books/" + webdavBookFileName(book, wire, digest(edited))
        remote.statuses[replacement] = 503
        assertFalse(a.manager.fullSync { "test" }.success)
        assertEquals(published, remote.manifest.books.getValue(wire).copy(metadata = published.metadata, cover = published.cover))
        assertEquals(text, remote.files["books/${published.fileName}"])
        assertEquals(published.fileName, a.books.getBookById(book.id)?.remoteFileName)
        remote.statuses.clear()
        a.sync()
        assertEquals(digest(edited), remote.manifest.books.getValue(wire).sha256)
        assertEquals(listOf(edited), remote.files.filterKeys { it.startsWith("books/") }.values.toList())
    }

    @Test fun manifestConflictDuringReplacementDoesNotRevertTheLocalEdit() = scenario {
        val first = a.local("first")
        val second = b.local("second", managed = true)
        a.sync()
        b.sync()
        val edited = text + "fixed typo\n"
        File(first.filePath).writeText(edited)
        remote.beforePut["manifest.json"] = {
            remote.manifest = remote.manifest.let { it.copy(data = it.data + ("elsewhere" to SyncFileEntry("elsewhere.json", "", 0, 1))) }
        }
        a.sync()
        assertTrue(remote.conflicts.get() > 0)
        assertEquals(edited, File(first.filePath).readText())
        assertEquals(digest(edited), remote.manifest.books.values.single().sha256)
        b.sync()
        assertEquals(edited, File(second.filePath).readText())
    }

    @Test fun cloudOnlyDeleteKeepsTheLocalBookUntilTheUserUploadsItAgain() = scenario {
        val book = a.local("kept")
        a.sync()
        File(book.filePath).writeText(text + "fixed typo\n")
        a.sync()
        val linked = a.books.getBookById(book.id)!!
        val result = a.manager.deleteRemoteBooks(listOf(linked), detachLocal = true, passwordReader = { "test" })
        assertTrue(result.message, result.success)
        assertTrue(remote.manifest.books.isEmpty())
        assertTrue(remote.files.keys.none { it.startsWith("books/") })
        repeat(2) {
            a.sync()
            val row = a.books.getBooksForWebdav().single()
            assertEquals(book.id, row.id)
            assertNull(row.remoteFileName)
            assertFalse(row.isCloudOnly)
            assertTrue(File(row.filePath).isFile)
            assertTrue(remote.manifest.books.isEmpty())
            assertTrue(remote.files.keys.none { it.startsWith("books/") })
            assertEquals(setOf(book.id), a.manager.blockedUploadIds())
        }
        // Another edit must not sneak the book back under a new identity either.
        File(book.filePath).writeText(text + "second fix\n")
        a.sync()
        assertTrue(remote.manifest.books.isEmpty())
        a.manager.requestReupload(listOf(book.id))
        a.sync()
        assertEquals(digest(text + "second fix\n"), remote.manifest.books.values.single().sha256)
        assertTrue(remote.manifest.deletedBooks.keys.none { it in remote.manifest.books })
        assertNotNull(a.books.getBookById(book.id)?.remoteFileName)
        assertTrue(a.manager.blockedUploadIds().isEmpty())
    }

    @Test fun copiesLeftBehindByEarlierEditsAreOfferedAndCleanedUp() = scenario {
        val book = a.local("edited")
        b.local("unrelated", contents = other)
        a.sync()
        val oldWire = remote.manifest.books.keys.single()
        // An older client forgot the link and published the edit as another book.
        a.aliases.save(libraryKey, emptyMap())
        a.aliases.saveSyncedHashes(libraryKey, emptyMap())
        a.books.clearRemoteAssociation(book.id)
        val edited = text + "fixed typo\n"
        File(book.filePath).writeText(edited)
        repeat(2) { a.sync() }
        assertEquals(2, remote.manifest.books.size)
        assertEquals(setOf(book.id, oldWire), a.books.getBooksForWebdav().map { it.id }.toSet())
        a.note(oldWire, "only-on-old-copy")

        val copies = a.manager.supersededCloudCopies()
        assertEquals(mapOf(oldWire to book.id), copies)
        a.manager.mergeSupersededCloudCopies(copies)
        val stale = a.books.getBookById(oldWire)!!
        assertTrue(a.manager.deleteRemoteBooks(listOf(stale), detachLocal = false, passwordReader = { "test" }).success)
        a.books.deleteBook(stale)
        repeat(2) { a.sync() }
        assertEquals(listOf(book.id), a.books.getBooksForWebdav().map { it.id })
        assertEquals(setOf("only-on-old-copy"), a.notes(book.id))
        assertEquals(digest(edited), remote.manifest.books.values.single().sha256)
        assertEquals(listOf(edited), remote.files.filterKeys { it.startsWith("books/") }.values.toList())
        assertTrue(a.manager.supersededCloudCopies().isEmpty())
    }

    private fun scenario(test: suspend Scenario.() -> Unit) = runBlocking {
        val remote = Dav()
        MockWebServer().use { server ->
            server.dispatcher = remote
            server.start()
            val scenario = Scenario(remote, server)
            try {
                scenario.dataStore.saveWebdavConfig(scenario.config)
                test(scenario)
            } finally {
                scenario.a.db.close()
                scenario.b.db.close()
            }
        }
    }

    private class Scenario(val remote: Dav, server: MockWebServer) {
        val context = RuntimeEnvironment.getApplication()
        val dataStore = DataStoreManager(context)
        val config = WebdavConfig(enabled = true, serverUrl = server.url("/").toString(), username = "test",
            syncProfileAndSettings = false, syncMode = "manual")
        val libraryKey = digest("${config.normalized().serverUrl}\ntest\nLumiBooks").take(24)
        val a = Device(context, dataStore, "a")
        val b = Device(context, dataStore, "b")
    }

    private class Device(private val context: Application, data: DataStoreManager, name: String) {
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        val identity = SyncIdentityStore(db.syncStateDao())
        val books = BookRepositoryImpl(db.bookDao(), db.syncStateDao(), identity)
        val aliases = WebdavBookIdentityStore(db.syncStateDao())
        private val client = WebdavClient()
        private val local = File(context.filesDir, name).apply { mkdirs() }
        private val snapshots = PortableSnapshotManager(context, db, data, identity,
            MineruTokenStore(context), ExternalTtsTokenStore(context), TranslationTokenStore(context))
        val manager = WebdavSyncManager(books, ReadingRepositoryImpl(db.readingRecordDao(), db.bookmarkDao(),
            db.noteDao(), db.syncStateDao(), identity, db), client, WebdavTokenStore(context), data,
            WebdavPortableStateSync(context, client, snapshots), LocalNetworkAccessManager(context), aliases,
            WebdavSyncLogStore(File(local, "logs")), WebdavSyncChanges(context, client), WebdavLocalBookState(db), context)

        suspend fun sync() {
            val result = manager.fullSync { "test" }
            assertTrue(result.message, result.success)
        }

        suspend fun local(id: String, archived: Boolean = false, contents: String = text, managed: Boolean = false): Book {
            val directory = if (managed) com.huangder.lumibooks.util.FileUtils.getBooksDirectory(context) else local
            val file = File(directory, "$id.txt").apply { writeText(contents) }
            return Book(id, "Same title", "", file.path, null, BookFormat.TXT, 0, 0f,
                createdAt = 1, metadataUpdatedAt = 50, isArchived = archived).also { books.insertBook(it) }
        }

        suspend fun note(book: String, id: String) = db.noteDao().insertNote(NoteEntity(bookId = book, chapterIndex = 0,
            startPosition = 0, endPosition = 1, selectedText = id, note = id, color = "red", createdAt = 1, syncId = id))

        suspend fun notes(book: String) = db.noteDao().getNotesByBookId(book).first().map { it.syncId }.toSet()
    }

    private class Dav : Dispatcher() {
        val files = ConcurrentHashMap<String, String>()
        val statuses = ConcurrentHashMap<String, Int>()
        val downloads = AtomicInteger()
        val conflicts = AtomicInteger()
        @Volatile var interrupt: String? = null
        val beforePut = ConcurrentHashMap<String, () -> Unit>()
        var manifest: SyncManifest
            get() = SyncManifest.fromJson(files.getValue("manifest.json"))
            set(value) { files["manifest.json"] = value.toJson() }

        override fun dispatch(request: RecordedRequest): MockResponse {
            val path = request.requestUrl!!.pathSegments.drop(1).joinToString("/")
            if (request.method == "PUT") beforePut.remove(path)?.invoke()
            return synchronized(this) {
                statuses[path]?.let { return@synchronized MockResponse().setResponseCode(it) }
                val old = files[path]
                val tag = old?.let { "\"${digest(it)}\"" }
                when (request.method) {
                    "MKCOL" -> MockResponse().setResponseCode(201)
                    "PROPFIND" -> MockResponse().setResponseCode(207).setBody("<d:multistatus xmlns:d=\"DAV:\"/>")
                    "GET", "HEAD" -> if (old == null) MockResponse().setResponseCode(404) else {
                        val response = MockResponse().setHeader("ETag", tag!!)
                        if (request.method == "GET") {
                            if (path.startsWith("books/")) downloads.incrementAndGet()
                            response.setBody(old)
                            if (interrupt == path) response.setSocketPolicy(SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY)
                        }
                        response
                    }
                    "PUT" -> if ((request.getHeader("If-Match")?.let { it != tag } == true) ||
                        (request.getHeader("If-None-Match") == "*" && old != null)) {
                        conflicts.incrementAndGet()
                        MockResponse().setResponseCode(412)
                    } else {
                        files[path] = request.body.readUtf8()
                        MockResponse().setResponseCode(201)
                    }
                    "DELETE" -> { files.remove(path); MockResponse().setResponseCode(204) }
                    else -> MockResponse().setResponseCode(405)
                }
            }
        }
    }

    private companion object {
        val text = "Book body, with enough text to interrupt a transfer.\n".repeat(100)
        val other = "A different book that shares nothing with the first one.\n".repeat(90)
        fun digest(text: String) = java.security.MessageDigest.getInstance("SHA-256")
            .digest(text.toByteArray()).joinToString("") { "%02x".format(it) }
        fun entry(name: String, contents: String) = SyncFileEntry(name, digest(contents), contents.toByteArray().size.toLong(), 10)
        fun cloud(id: String, entry: SyncFileEntry) = Book(id, id, "", "", null, BookFormat.TXT, 0, 0f,
            createdAt = 1, isCloudOnly = true, remoteFileName = entry.fileName,
            remoteFileSize = entry.sizeBytes, remoteFileSha256 = entry.sha256)
    }
}
