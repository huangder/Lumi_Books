package com.huangder.lumibooks.ui.home

import android.app.Application
import android.net.Uri
import androidx.room.Room
import com.huangder.lumibooks.data.local.database.AppDatabase
import com.huangder.lumibooks.data.repository.BookRepositoryImpl
import com.huangder.lumibooks.data.repository.FolderRepositoryImpl
import com.huangder.lumibooks.data.sync.SyncIdentityStore
import com.huangder.lumibooks.domain.model.Book
import com.huangder.lumibooks.domain.model.BookFormat
import com.huangder.lumibooks.util.AuthorizedStorageManager
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class BookMetadataImportAndRepairTest {
    @get:Rule val folder = TemporaryFolder()

    @Test
    fun importCombinesEmbeddedMetadataCatalogAndOriginalName() = runBlocking(Dispatchers.IO) {
        val context = RuntimeEnvironment.getApplication()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val identity = SyncIdentityStore(db.syncStateDao())
            val books = BookRepositoryImpl(db.bookDao(), db.syncStateDao(), identity)
            val folders = FolderRepositoryImpl(db.folderDao(), db.syncStateDao(), identity)
            val pipeline = BookImportPipeline(books, folders, AuthorizedStorageManager())

            fun document(file: File, originalName: String = file.name, author: String? = null) =
                BookImportPipeline.Document(
                    uri = Uri.fromFile(file), name = file.name, sourceSize = file.length(),
                    localFile = file, persistSourceUri = false, metadataFileName = originalName,
                    authorOverride = author
                )

            val files = listOf(
                document(epub("《文件书名》文件作者.epub", "<dc:title>书内书名</dc:title>"), author = "目录作者"),
                document(epub("《另一文件书名》文件作者.epub", "<dc:creator>书内作者</dc:creator>")),
                document(folder.newFile("本地副本 (2).txt").apply { writeText("TXT text.") }, "《三体》刘慈欣.txt"),
                document(folder.newFile("《文件回退》作者：张三.epub").apply { writeText("Damaged EPUB") }),
                document(folder.newFile("《Markdown 文件名》作者：李四.md").apply {
                    writeText("---\nauthor: 文档作者\n---\n# Section\n\nContent")
                })
            )
            val result = pipeline.import(context, files, true, null, false)
            assertEquals(5, result.imported)
            val records = result.outcomes.map { checkNotNull(books.getBookById(checkNotNull(it.bookId))) }
            assertEquals(
                listOf("书内书名" to "目录作者", "另一文件书名" to "书内作者", "三体" to "刘慈欣",
                    "文件回退" to "张三", "Markdown 文件名" to "文档作者"),
                records.map { it.title to it.author }
            )
            assertEquals("本地副本 (2).txt", records[2].sourceDisplayName)
        } finally {
            db.close()
        }
    }

    @Test
    fun previewAndApplyProtectEditsAndCurrentReadingState() = runBlocking(Dispatchers.IO) {
        val context = RuntimeEnvironment.getApplication()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val books = BookRepositoryImpl(db.bookDao(), db.syncStateDao(), SyncIdentityStore(db.syncStateDao()))
            val sourceFile = folder.newFile("stored.txt").apply { writeText("TXT body.") }
            val originalName = "《三体》刘慈欣.txt"
            val legacy = Book(
                id = "legacy", title = "《三体》刘慈欣", author = "Unknown Author", filePath = sourceFile.absolutePath,
                coverPath = "/cover.jpg", format = BookFormat.TXT, lastReadTime = 1L, readingProgress = 0.1f,
                createdAt = 1L, sourceDisplayName = originalName
            )
            val edited = legacy.copy(id = "edited", title = "自定义书名")
            val unreadable = legacy.copy(id = "missing", filePath = File(folder.root, "missing.txt").absolutePath)
            listOf(legacy, edited, unreadable).forEach { books.insertBook(it) }

            val preview = checkNotNull(previewBookMetadataRepair(context, legacy))
            val editedPreview = checkNotNull(previewBookMetadataRepair(context, edited))
            assertEquals("自定义书名", editedPreview.title)
            assertNull(previewBookMetadataRepair(context, unreadable))
            assertEquals(legacy, books.getBookById(legacy.id))

            books.updateReadingPosition(legacy.id, 0.7f, "new-locator")
            books.updateBookMetadata(edited.copy(author = "手动编辑"))
            assertTrue(books.compareAndSetBookMetadata(preview.book, preview.title, preview.author))
            assertFalse(books.compareAndSetBookMetadata(editedPreview.book, editedPreview.title, editedPreview.author))
            assertFalse(books.compareAndSetBookMetadata(preview.book, "旧值", "旧值", "/stale-cover.jpg"))

            val saved = checkNotNull(books.getBookById(legacy.id))
            assertEquals("三体" to "刘慈欣", saved.title to saved.author)
            assertEquals(0.7f, saved.readingProgress, 0f)
            assertEquals("new-locator", saved.locatorJson)
            assertEquals("/cover.jpg", saved.coverPath)
            assertEquals(originalName, saved.sourceDisplayName)
            assertEquals("手动编辑", books.getBookById(edited.id)?.author)
            assertNull(previewBookMetadataRepair(context, saved))
        } finally {
            db.close()
        }
    }

    private fun epub(name: String, fields: String): File = folder.newFile(name).apply {
        ZipOutputStream(outputStream()).use { zip ->
            val entries = mapOf(
                "META-INF/container.xml" to "<container><rootfiles><rootfile full-path=\"package.opf\"/></rootfiles></container>",
                "package.opf" to "<package><metadata xmlns:dc=\"http://purl.org/dc/elements/1.1/\">$fields</metadata></package>"
            )
            entries.forEach { (path, xml) ->
                zip.putNextEntry(ZipEntry(path))
                zip.write(xml.toByteArray())
                zip.closeEntry()
            }
        }
    }
}
