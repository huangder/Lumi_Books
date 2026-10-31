package com.huangder.lumibooks.ui.reader

import android.app.Application
import androidx.room.Room
import com.huangder.lumibooks.data.backup.PortableSnapshot
import com.huangder.lumibooks.data.backup.PortableSnapshotManager
import com.huangder.lumibooks.data.local.DataStoreManager
import com.huangder.lumibooks.data.local.database.AppDatabase
import com.huangder.lumibooks.data.repository.ReadingRepositoryImpl
import com.huangder.lumibooks.data.sync.SyncIdentityStore
import com.huangder.lumibooks.domain.model.Note
import com.huangder.lumibooks.mineru.MineruTokenStore
import com.huangder.lumibooks.translation.TranslationTokenStore
import com.huangder.lumibooks.tts.ExternalTtsTokenStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class PdfAnnotationPersistenceTest {
    @Test fun pdfAnchorsNotesStylesAndTagsSurviveRestartBackupAndRangeEdits() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val preferences = DataStoreManager(context)
        val databaseName = "pdf-annotations.db"
        var db = Room.databaseBuilder(context, AppDatabase::class.java, databaseName).build()
        fun repository() = ReadingRepositoryImpl(db.readingRecordDao(), db.bookmarkDao(), db.noteDao(),
            db.syncStateDao(), SyncIdentityStore(db.syncStateDao()), db)
        fun manager() = PortableSnapshotManager(context, db, preferences, SyncIdentityStore(db.syncStateDao()),
            MineruTokenStore(context), ExternalTtsTokenStore(context), TranslationTokenStore(context))
        val model = buildPdfTextPage(3, "portable-file", (0..5).map { index ->
            val x = 0.1f + index * 0.02f
            PdfPositionedText(('a' + index).toString(), PdfTextQuad(listOf(
                PdfTextPoint(x, 0.1f), PdfTextPoint(x + 0.02f, 0.1f),
                PdfTextPoint(x + 0.02f, 0.12f), PdfTextPoint(x, 0.12f))), 0)
        })
        val locator = model.locator(0, 6)!!.encode()
        try {
            repository().insertNote(Note(bookId = "pdf-book", chapterIndex = 3, startPosition = 0, endPosition = 6,
                startLocatorJson = locator, endLocatorJson = locator, selectedText = "abcdef", note = "My note",
                color = readerHighlightColorReference(2, "underline"), createdAt = 1, type = "underline",
                isNote = true, tags = listOf("study"), styleSnapshotJson = "{\"underlineMode\":2}"))
            db.close()
            db = Room.databaseBuilder(context, AppDatabase::class.java, databaseName).build()
            val saved = repository().getNotesByBookId("pdf-book").first().single()
            assertEquals(locator, saved.startLocatorJson)
            assertTrue(PdfTextLocator.decode(saved.startLocatorJson)!!.matches(model))
            repository().applyAnnotationEdit(pdfAnnotationEditPlan(model, listOf(saved), "pdf-book",
                2, 4, "underline", "", remove = true))
            val records = repository().getNotesByBookId("pdf-book").first().sortedBy { it.startPosition }
            assertEquals(listOf("ab", "ef"), records.map { it.selectedText })
            assertEquals("My note", records.first().note)
            assertTrue(records.first().isNoteEntry)
            assertEquals(listOf("study"), records.first().tags)
            assertEquals(2, records.map { it.syncId }.toSet().size)
            val snapshot = PortableSnapshot.fromJson(manager().capture(includeBookFiles = false).snapshot.toJson())
            db.close()
            db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
            manager().apply(snapshot, emptyMap(), replace = true, replaceAssetDirectories = false)
            val restored = repository().getNotesByBookId("pdf-book").first().sortedBy { it.startPosition }
            assertEquals(records.map { it.startLocatorJson }, restored.map { it.startLocatorJson })
            assertEquals(records.map { it.color }, restored.map { it.color })
            assertEquals(records.map { it.styleSnapshotJson }, restored.map { it.styleSnapshotJson })
            restored.forEach { assertTrue(PdfTextLocator.decode(it.startLocatorJson)!!.matches(model)) }
            assertEquals("My note", restored.first().note)
            assertEquals(listOf("study"), restored.first().tags)
        } finally {
            db.close()
            context.deleteDatabase(databaseName)
        }
    }
}
