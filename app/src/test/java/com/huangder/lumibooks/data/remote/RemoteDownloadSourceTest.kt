package com.huangder.lumibooks.data.remote

import android.app.Application
import androidx.room.Room
import com.huangder.lumibooks.data.local.database.AppDatabase
import com.huangder.lumibooks.data.repository.BookRepositoryImpl
import com.huangder.lumibooks.data.repository.FolderRepositoryImpl
import com.huangder.lumibooks.data.sync.LocalNetworkAccessManager
import com.huangder.lumibooks.data.sync.SyncIdentityStore
import com.huangder.lumibooks.ui.home.BookImportPipeline
import com.huangder.lumibooks.util.AuthorizedStorageManager
import java.io.File
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class RemoteDownloadSourceTest {
    @Test
    fun samePathInTwoLibrariesKeepsBothDownloadsAndTheirContents() = runBlocking(Dispatchers.IO) {
        val context = RuntimeEnvironment.getApplication()
        val store = RemoteLibraryStore(context, RemoteCredentialStore(context))
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val identity = SyncIdentityStore(db.syncStateDao())
            val books = BookRepositoryImpl(db.bookDao(), db.syncStateDao(), identity)
            val folders = FolderRepositoryImpl(db.folderDao(), db.syncStateDao(), identity)
            val storage = AuthorizedStorageManager()
            val manager = RemoteDownloadManager(
                context, store, OpdsClient(), WebdavLibraryClient(), BookImportPipeline(books, folders, storage),
                storage, LocalNetworkAccessManager(context)
            )
            MockWebServer().use { server ->
                server.start()
                val libraryIds = listOf("library-a", "library-b")
                for (id in libraryIds) {
                    val root = server.url("/$id/")
                    val source = RemoteSource(id, RemoteSourceType.WEBDAV, id, root.toString())
                    val format = RemoteFormat(root.resolve("book.txt").toString(), "txt")
                    val item = RemoteBookItem(id, "webdav:book.txt", "book.txt", formats = listOf(format))
                    server.enqueue(MockResponse().setBody("Book from $id"))
                    val batch = withTimeout(15_000) {
                        val finished = async(start = CoroutineStart.UNDISPATCHED) { manager.batchFinished.first() }
                        manager.enqueue(source, listOf(item to format), RemoteDestination.AppStorage())
                        finished.await()
                    }
                    assertEquals(RemoteDownloadManager.BatchSummary(1, 0, 0), batch)
                }
                val completed = manager.states.value.values.filterIsInstance<RemoteDownloadState.Completed>()
                assertEquals(2, completed.size)
                val imports = store.load().importedBooks
                for (id in libraryIds) {
                    val key = RemoteBookFiles.remoteDocumentKey(id, "webdav:book.txt")
                    val bookId = requireNotNull(imports[key])
                    assertEquals(RemoteDownloadState.Completed(bookId, false), manager.states.value[key])
                    val book = requireNotNull(books.getBookById(bookId))
                    assertEquals("Book from $id", File(book.filePath).readText())
                }
            }
        } finally {
            db.close()
        }
    }
}
