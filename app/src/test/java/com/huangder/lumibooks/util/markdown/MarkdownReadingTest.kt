package com.huangder.lumibooks.util.markdown

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.huangder.lumibooks.data.backup.PortableSnapshotManager
import com.huangder.lumibooks.data.local.DataStoreManager
import com.huangder.lumibooks.data.sync.SyncIdentityStore
import com.huangder.lumibooks.mineru.MineruTokenStore
import com.huangder.lumibooks.tts.ExternalTtsTokenStore
import com.huangder.lumibooks.translation.TranslationTokenStore
import com.huangder.lumibooks.util.FileUtils
import com.huangder.lumibooks.data.local.database.AppDatabase
import com.huangder.lumibooks.data.local.entity.BookEntity
import com.huangder.lumibooks.data.local.entity.BookmarkEntity
import com.huangder.lumibooks.data.local.entity.NoteEntity
import com.huangder.lumibooks.domain.model.Book
import com.huangder.lumibooks.domain.model.BookFormat
import com.huangder.lumibooks.util.parser.MarkdownParser
import com.huangder.lumibooks.util.epub.EpubRenderMode
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class MarkdownReadingTest {
    @get:Rule val temporary = TemporaryFolder()
    private val context: Application get() = ApplicationProvider.getApplicationContext()
    private fun id() = UUID.randomUUID().toString()
    private fun source(text: String) = File(temporary.root, "original.markdown").apply { writeText(text) }

    @Test fun oneChapterOutlineSearchAndNativeTextAgree() = runBlocking {
        val original = source("# Title\n\n正文 **加粗** text\n\n## Second\n\nfind me\n\n###### Last\n\nend")
        val snapshot = MarkdownSnapshotStore.create(context, id(), original.path)
        val parser = MarkdownParser(context)
        try {
            val content = parser.parse(snapshot.path)
            assertEquals(1, content.chapters.size)
            assertEquals(listOf(1, 2, 6), content.tocEntries.map { it.level })
            assertTrue(content.tocEntries.all { it.chapterIndex == 0 })
            assertEquals(parser.searchText, parser.document!!.text)
            assertNotNull(parser.blockSource)
            assertEquals("", parser.getChapterHtml(0))
            val result = parser.searchBook("find me", 10).single()
            assertEquals(parser.searchText.indexOf("find me"), result.charOffset)
            assertEquals(parser.searchText.length, result.locator.textLength)
            assertEquals(parser.searchText.indexOf("Second"), parser.headingOffsets["second"])
            val fallback = parser.resolveLink(0, "#second")!!
            assertTrue(parser.getChapterContent(0).toString().substring(fallback.characterOffset).startsWith("Second"))
            parser.parse(snapshot.path)
            assertEquals(1, parser.getChapterCount())
        } finally { parser.close() }
    }

    @Test fun noHeadingStillOpensAndDoesNotInventOutline() {
        val snapshot = MarkdownSnapshotStore.create(context, id(), source("plain body").path)
        val parser = MarkdownParser(context)
        try {
            val book = parser.parse(snapshot.path)
            assertEquals("original", book.title)
            assertEquals(1, book.chapters.size)
            assertTrue(book.tocEntries.isEmpty())
            assertTrue(parser.searchText.contains("plain body"))
        } finally { parser.close() }
    }

    @Test fun scrollingReusesHeadingIndexAndBinaryLookupWorksAfterWarmOpen() {
        val original = source("Introduction\n\n" + (1..2000).joinToString("\n\n") { "## Heading $it\n\nBody $it" } +
            "\n\nReference[^a]\n\n[^a]: Footnote text")
        val parser = MarkdownParser()
        try {
            repeat(2) {
                parser.parse(original.path)
                val offsets = parser.headingOffsets
                assertNull(parser.headingAnchorAt(0))
                for (heading in parser.document!!.outline.filter { it.level in 1..6 }) {
                    assertSame("scroll queries must not rebuild the whole outline", offsets, parser.headingOffsets)
                    assertEquals(heading.anchor, parser.headingAnchorAt(heading.offset))
                    assertEquals(heading.anchor, parser.headingAnchorAt(heading.offset + 1))
                }
                val last = parser.document!!.outline.last { it.level in 1..6 }
                assertEquals(last.anchor, parser.headingAnchorAt(parser.searchText.length))
                assertNotNull(parser.resolveLink(0, "#footnote-a"))
            }
            original.writeText("No headings")
            parser.parse(original.path)
            assertTrue(parser.headingOffsets.isEmpty())
            assertNull(parser.headingAnchorAt(100))
        } finally { parser.close() }
        assertTrue(parser.headingOffsets.isEmpty())
        assertNull(parser.headingAnchorAt(100))
    }

    @Test fun snapshotsAndPackagesKeepImagesAndChecksums() {
        File(temporary.root, "images").mkdirs()
        val image = File(temporary.root, "images/picture.png").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val original = source("---\ntitle: My Book\nauthor: Me\n---\n![ok](images/picture.png)\n![escape](../secret.png)\n![remote](https://example.org/image.png)")
        val bookId = id()
        val snapshot = MarkdownSnapshotStore.create(context, bookId, original.path)
        assertEquals(snapshot.path, MarkdownSnapshotStore.create(context, bookId, original.path).path)
        assertEquals(setOf("images/picture.png"), MarkdownSnapshotStore.assets(snapshot).keys)
        assertFalse(MarkdownSnapshotStore.sourceChanged(context, snapshot, original.path))
        val pack = MarkdownSnapshotStore.packageFile(snapshot)
        ZipFile(pack).use { zip ->
            assertNotNull(zip.getEntry("document.md"))
            assertNotNull(zip.getEntry("assets/images/picture.png"))
            assertEquals(3, zip.size())
        }
        val restored = MarkdownSnapshotStore.unpack(context, id(), pack)
        assertEquals(original.readText(), restored.readText())
        assertArrayEquals(image.readBytes(), MarkdownSnapshotStore.assets(restored).getValue("images/picture.png").readBytes())
        assertEquals("My Book", MarkdownSnapshotStore.manifest(restored)?.getString("title"))
        original.appendText("\nChanged")
        assertTrue(MarkdownSnapshotStore.sourceChanged(context, snapshot, original.path))
        assertFalse(snapshot.readText().contains("Changed"))
        val newer = MarkdownSnapshotStore.create(context, bookId, original.path)
        assertTrue(FileUtils.deleteAppManagedBookFile(context, snapshot.path))
        assertFalse(newer.exists())
        assertFalse(snapshot.parentFile.exists())
        assertTrue(original.isFile)
        assertTrue(image.isFile)
    }

    @Test fun damagedAndTraversingPackagesAreRejected() {
        val original = source("# content")
        val snapshot = MarkdownSnapshotStore.create(context, id(), original.path)
        val manifest = File(snapshot.parentFile, "manifest.json").readText()
        fun archive(name: String, entryName: String, bytes: String): File = File(temporary.root, name).apply {
            ZipOutputStream(outputStream()).use { zip ->
                zip.putNextEntry(ZipEntry("manifest.json")); zip.write(manifest.toByteArray()); zip.closeEntry()
                zip.putNextEntry(ZipEntry(entryName)); zip.write(bytes.toByteArray()); zip.closeEntry()
            }
        }
        assertTrue(runCatching { MarkdownSnapshotStore.unpack(context, id(), archive("bad.mdpack", "document.md", "altered")) }.isFailure)
        assertTrue(runCatching { MarkdownSnapshotStore.unpack(context, id(), archive("escape.mdpack", "../document.md", original.readText())) }.isFailure)
    }

    @Test fun backupRestoresMarkdownAssetsAndStateSyncPreservesLocalSource() = runBlocking {
        val bookId = id()
        val original = source("# Backup\n\n![picture](picture.png)")
        File(temporary.root, "picture.png").writeBytes(byteArrayOf(4, 5, 6))
        val snapshot = MarkdownSnapshotStore.create(context, bookId, original.path)
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        try {
            db.bookDao().insertBook(BookEntity(bookId, "Backup", "", snapshot.path, null, "MARKDOWN", 0, 0f,
                createdAt = 1, sourceUri = original.path, sourceSha256 = MarkdownSnapshotStore.sha256(snapshot)))
            val preferences = DataStoreManager(context)
            preferences.initializeMarkdownRenderMode(bookId)
            assertEquals(EpubRenderMode.BOOK_LAYOUT, preferences.readerPreferences(bookId).first().renderMode)
            preferences.saveRenderMode(bookId, EpubRenderMode.READER_LAYOUT)
            preferences.initializeMarkdownRenderMode(bookId)
            assertEquals(EpubRenderMode.READER_LAYOUT, preferences.readerPreferences(bookId).first().renderMode)
            val manager = PortableSnapshotManager(context, db, preferences, SyncIdentityStore(db.syncStateDao()),
                MineruTokenStore(context), ExternalTtsTokenStore(context), TranslationTokenStore(context))
            val state = manager.capture(includeBookFiles = false)
            manager.apply(state.snapshot, emptyMap(), replace = false, replaceAssetDirectories = false)
            assertEquals(original.path, db.bookDao().getBookById(bookId)?.sourceUri)
            val bundle = manager.capture()
            val body = bundle.snapshot.assets.single { it.kind == "book" }
            assertTrue(body.fileName.endsWith(".mdpack"))
            val copies = bundle.assetSources.mapValues { (key, source) ->
                File(temporary.root, key).apply { source.openStream().use { input -> outputStream().use { input.copyTo(it) } } }
            }
            manager.apply(bundle.snapshot, copies, replace = true)
            val restored = db.bookDao().getBookById(bookId)!!
            assertEquals("MARKDOWN", restored.format)
            assertEquals(original.readText(), File(restored.filePath).readText())
            assertArrayEquals(byteArrayOf(4, 5, 6), MarkdownSnapshotStore.assets(File(restored.filePath)).getValue("picture.png").readBytes())
            assertFalse(restored.isCloudOnly)
            assertFalse(restored.isMissing)
        } finally { db.close() }
    }

    @Test fun updateMigratesMatchesAndPreservesStaleNotesAndBookmarks() = runBlocking {
        val original = source("# Old\n\nkeep this\n\nremove this")
        val bookId = id()
        val snapshot = MarkdownSnapshotStore.create(context, bookId, original.path)
        val parser = MarkdownParser(context)
        val oldText = try { parser.parse(snapshot.path); parser.searchText } finally { parser.close() }
        val keepAt = oldText.indexOf("keep this")
        val removeAt = oldText.indexOf("remove this")
        val book = Book(bookId, "Old", "", snapshot.path, null, BookFormat.MARKDOWN, 0, 0f,
            createdAt = 1, sourceUri = original.path)
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        try {
            db.bookDao().insertBook(BookEntity(bookId, "Old", "", snapshot.path, null, "MARKDOWN", 0, 0f, createdAt = 1))
            for ((key, at, quote) in listOf(Triple("keep", keepAt, "keep this"), Triple("remove", removeAt, "remove this"))) {
                val locator = MarkdownLocatorMigration.locator(oldText, MarkdownLocatorMigration.Match(at, at + quote.length))
                db.noteDao().insertNote(NoteEntity(bookId = bookId, chapterIndex = 0, startPosition = at,
                    endPosition = at + quote.length, selectedText = quote, note = "my note", color = "#FFFF00",
                    createdAt = 1, syncId = key, startLocatorJson = locator))
                db.bookmarkDao().insertBookmark(BookmarkEntity(bookId = bookId, chapterIndex = 0,
                    position = -(at + 1).toFloat(), title = key, createdAt = 1, syncId = key, locatorJson = locator))
            }
            db.bookmarkDao().insertBookmark(BookmarkEntity(bookId = bookId, chapterIndex = 0,
                position = -(removeAt + 1).toFloat(), title = "legacy", createdAt = 1,
                syncId = "legacy", locatorJson = "{}"))
            original.writeText("# New\n\ninserted\n\nkeep this")
            val updated = MarkdownUpdateManager(context, db).update(book)
            assertNotEquals(snapshot.path, updated.filePath)
            assertEquals(updated.filePath, db.bookDao().getBookById(bookId)?.filePath)
            assertTrue(snapshot.isFile)
            val notes = db.noteDao().getAllNotes().associateBy { it.syncId }
            assertEquals(2, notes.size)
            assertFalse(notes.getValue("keep").isStale)
            assertTrue(notes.getValue("keep").startPosition > keepAt)
            assertTrue(notes.getValue("remove").isStale)
            assertEquals("my note", notes.getValue("remove").note)
            assertEquals("remove this", notes.getValue("remove").selectedText)
            val bookmarks = db.bookmarkDao().getAllBookmarks().associateBy { it.syncId }
            assertFalse(bookmarks.getValue("keep").isStale)
            assertTrue(bookmarks.getValue("remove").isStale)
            assertEquals("remove this", JSONObject(bookmarks.getValue("remove").locatorJson!!).getString("exact"))
            assertTrue(JSONObject(bookmarks.getValue("legacy").locatorJson!!).getString("exact").startsWith("remove this"))
            original.appendText("\n\nremove this")
            MarkdownUpdateManager(context, db).update(updated)
            assertTrue(db.noteDao().getAllNotes().none { it.isStale })
            assertTrue(db.bookmarkDao().getAllBookmarks().none { it.isStale })
        } finally { db.close() }
    }
}
