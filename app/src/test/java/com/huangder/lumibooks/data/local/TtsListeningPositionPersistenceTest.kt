package com.huangder.lumibooks.data.local

import android.app.Application
import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.test.core.app.ApplicationProvider
import com.huangder.lumibooks.tts.TtsListeningPosition
import com.huangder.lumibooks.tts.TtsStartChoice
import com.huangder.lumibooks.tts.TtsReaderResumeChoice
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class TtsListeningPositionPersistenceTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    @Test
    fun rememberedStartChoicesPersistAndCanBeResetToAskEachTime() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.dataStore.edit { it.clear() }
        val manager = DataStoreManager(context)
        assertNull(manager.ttsStartChoice.first())
        for (choice in TtsStartChoice.entries) {
            manager.saveTtsStartChoice(choice)
            assertEquals(choice, DataStoreManager(context).ttsStartChoice.first())
        }
        manager.saveTtsStartChoice(null)
        assertNull(DataStoreManager(context).ttsStartChoice.first())
        context.dataStore.edit { it[stringPreferencesKey("tts_start_choice")] = "unknown" }
        assertNull(manager.ttsStartChoice.first())
    }

    @Test
    fun openingBookChoicePersistsIndependentlyOfListeningStartChoice() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.dataStore.edit { it.clear() }
        val manager = DataStoreManager(context)
        assertNull(manager.ttsReaderResumeChoice.first())
        manager.saveTtsStartChoice(TtsStartChoice.CURRENT_PAGE)
        for (choice in TtsReaderResumeChoice.entries) {
            manager.saveTtsReaderResumeChoice(choice)
            val reopened = DataStoreManager(context)
            assertEquals(choice, reopened.ttsReaderResumeChoice.first())
            assertEquals(TtsStartChoice.CURRENT_PAGE, reopened.ttsStartChoice.first())
        }
        manager.saveTtsStartChoice(TtsStartChoice.SAVED_POSITION)
        assertEquals(TtsReaderResumeChoice.LISTENING_POSITION, manager.ttsReaderResumeChoice.first())
        manager.saveTtsReaderResumeChoice(null)
        assertNull(DataStoreManager(context).ttsReaderResumeChoice.first())
        assertEquals(TtsStartChoice.SAVED_POSITION, manager.ttsStartChoice.first())
        context.dataStore.edit { it[stringPreferencesKey("tts_reader_resume_choice")] = "unknown" }
        assertNull(manager.ttsReaderResumeChoice.first())
    }

    @Test
    fun oldRememberedOpeningChoicesResetOnceWithoutChangingAudioStartOrListeningAnchor() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val position = TtsListeningPosition("book", 9, 3, 456, 123456L)
        for (legacy in TtsReaderResumeChoice.entries) {
            context.dataStore.edit { preferences ->
                preferences.clear()
                preferences[stringPreferencesKey("tts_reader_resume_choice")] = legacy.storedValue
            }
            val manager = DataStoreManager(context)
            manager.saveTtsStartChoice(TtsStartChoice.SAVED_POSITION)
            manager.saveTtsListeningPosition(position)
            assertNull(manager.ttsReaderResumeChoice.first())
            assertEquals(TtsStartChoice.SAVED_POSITION, manager.ttsStartChoice.first())
            assertEquals(position, manager.ttsListeningPosition("book").first())
            manager.saveTtsReaderResumeChoice(legacy)
            assertEquals(legacy, DataStoreManager(context).ttsReaderResumeChoice.first())
        }
    }

    @Test
    // Use a fresh SDK sandbox so the process-wide DataStore delegate points at this
    // test's application directory when we copy its committed preferences file.
    @Config(sdk = [34], application = Application::class)
    fun diskSnapshotRestoresListeningPositionWithoutAnExitSave() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.dataStore.edit { it.clear() }
        val manager = DataStoreManager(context)
        val firstBook = TtsListeningPosition("book", 9, 3, 456, 123456L)
        val otherBook = TtsListeningPosition("other-book", 2, 0, 100, 123450L)
        manager.saveTtsListeningPosition(otherBook)
        manager.saveTtsListeningPosition(firstBook.copy(chapterIndex = 5))
        manager.saveTtsListeningPosition(firstBook)

        // Read only the committed file through a fresh DataStore, with no lifecycle/stop flush.
        val snapshot = temporaryFolder.root.resolve("reopened.preferences_pb")
        context.preferencesDataStoreFile("settings").copyTo(snapshot)
        val job = SupervisorJob()
        val reopened = PreferenceDataStoreFactory.create(
            scope = CoroutineScope(job + Dispatchers.IO), produceFile = { snapshot })
        try {
            val stored = reopened.data.first()
            for (position in listOf(firstBook, otherBook)) {
                assertEquals(position, TtsListeningPosition.decode(position.bookId,
                    stored[stringPreferencesKey("tts_listening_position_${position.bookId}")]!!))
            }
        } finally {
            job.cancel()
            job.join()
        }
    }

    @Test
    fun savedPositionCanBeLoadedByANewManagerWithoutTouchingReadingOrOtherBooks() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.dataStore.edit { it.clear() }
        val position = TtsListeningPosition("book", 9, 3, 456, 123456L)
        DataStoreManager(context).saveTtsListeningPosition(position)

        assertEquals(position, DataStoreManager(context).ttsListeningPosition("book").first())
        assertNull(DataStoreManager(context).ttsListeningPosition("other-book").first())
        assertEquals(setOf("tts_listening_position_book"),
            context.dataStore.data.first().asMap().keys.map { it.name }.toSet())
    }

    @Test
    fun corruptedOrNegativeLocationsAreIgnored() {
        for (raw in listOf("", "9|3|456", "-1|0|0|123", "0|-1|0|123", "0|0|-1|123", "0|0|0|0")) {
            assertNull(TtsListeningPosition.decode("book", raw))
        }
    }
}
