package com.huangder.lumibooks.util.word

import android.app.Application
import android.net.Uri
import androidx.room.Room
import com.huangder.lumibooks.data.backup.PortableSnapshotManager
import com.huangder.lumibooks.data.local.DataStoreManager
import com.huangder.lumibooks.data.local.database.AppDatabase
import com.huangder.lumibooks.data.repository.BookRepositoryImpl
import com.huangder.lumibooks.data.repository.FolderRepositoryImpl
import com.huangder.lumibooks.data.sync.SyncIdentityStore
import com.huangder.lumibooks.data.sync.webdavBookFileName
import com.huangder.lumibooks.mineru.MineruTokenStore
import com.huangder.lumibooks.translation.TranslationTokenStore
import com.huangder.lumibooks.tts.ExternalTtsTokenStore
import com.huangder.lumibooks.ui.home.BookImportPipeline
import com.huangder.lumibooks.util.AuthorizedStorageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class WordImportStorageTest {
    @get:Rule val folder = TemporaryFolder()

    @Test
    fun `import deduplicates by Word bytes and backup restores the originals`() = runBlocking(Dispatchers.IO) {
        val context = RuntimeEnvironment.getApplication()
        val sourceDb = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        val targetDb = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val sourceIdentity = SyncIdentityStore(sourceDb.syncStateDao())
            val books = BookRepositoryImpl(sourceDb.bookDao(), sourceDb.syncStateDao(), sourceIdentity)
            val folders = FolderRepositoryImpl(sourceDb.folderDao(), sourceDb.syncStateDao(), sourceIdentity)
            val pipeline = BookImportPipeline(books, folders, AuthorizedStorageManager())
            val originals = listOf("docx", "doc").associateWith { extension ->
                val bytes = checkNotNull(javaClass.getResourceAsStream("/word/reading.$extension")).use { it.readBytes() }
                if (extension == "docx") withoutTitle(bytes) else bytes
            }
            fun candidate(name: String, bytes: ByteArray): BookImportPipeline.Document {
                val file = folder.newFile(name).apply { writeBytes(bytes) }
                return BookImportPipeline.Document(uri = Uri.fromFile(file), name = file.name, sourceSize = file.length())
            }
            val documents = listOf(
                candidate("中文书名.DOCX", originals.getValue("docx")),
                candidate("copy.docx", originals.getValue("docx")),
                candidate("reading.doc", originals.getValue("doc")),
                candidate("bad.doc", "broken".toByteArray())
            )
            val result = pipeline.import(context, documents, true, null, false)
            assertEquals(listOf("IMPORTED", "DUPLICATE", "IMPORTED", "FAILED"), result.outcomes.map { it.status.name })
            assertEquals(result.outcomes[0].bookId, result.outcomes[1].bookId)
            assertEquals(2, books.getAllBooks().first().size)
            for (book in books.getAllBooks().first()) {
                val extension = book.format.name.lowercase()
                if (extension == "docx") assertEquals("中文书名", book.title)
                assertTrue(originals.getValue(extension).contentEquals(File(book.filePath).readBytes()))
                assertTrue(webdavBookFileName(book, book.id).endsWith(".$extension"))
            }
            fun snapshots(db: AppDatabase) = PortableSnapshotManager(context, db, DataStoreManager(context),
                SyncIdentityStore(db.syncStateDao()), MineruTokenStore(context), ExternalTtsTokenStore(context),
                TranslationTokenStore(context))
            val backup = snapshots(sourceDb).capture()
            val assets = backup.assetSources.mapValues { (_, asset) ->
                folder.newFile().apply {
                    asset.openStream().use { input -> outputStream().use { input.copyTo(it) } }
                }
            }
            snapshots(targetDb).apply(backup.snapshot, assets, replace = false)
            val restored = targetDb.bookDao().getAllBooksSnapshot()
            assertEquals(setOf("DOCX", "DOC"), restored.map { it.format }.toSet())
            for (book in restored) {
                assertTrue(originals.getValue(book.format.lowercase()).contentEquals(File(book.filePath).readBytes()))
            }
        } finally {
            targetDb.close()
            sourceDb.close()
        }
    }

    private fun withoutTitle(bytes: ByteArray): ByteArray {
        val output = java.io.ByteArrayOutputStream()
        ZipInputStream(bytes.inputStream()).use { input ->
            ZipOutputStream(output).use { zip ->
                generateSequence { input.nextEntry }.forEach { entry ->
                    zip.putNextEntry(ZipEntry(entry.name))
                    val value = input.readBytes()
                    if (entry.name == "docProps/core.xml") {
                        zip.write(value.toString(Charsets.UTF_8)
                            .replace(Regex("<dc:title>.*?</dc:title>"), "<dc:title />").toByteArray())
                    } else {
                        zip.write(value)
                    }
                    zip.closeEntry()
                }
            }
        }
        return output.toByteArray()
    }
}
