package com.huangder.lumibooks.data.local

import android.app.Application
import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.core.app.ApplicationProvider
import com.huangder.lumibooks.util.epub.EpubRenderMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class DefaultReaderLayoutPersistenceTest {
    private lateinit var context: Context
    private lateinit var manager: DataStoreManager

    @Before fun setUp() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        context.dataStore.edit { it.clear() }
        manager = DataStoreManager(context)
    }

    @Test fun defaultPersistsAndReachesBothReaderPaths() = runBlocking {
        assertEquals(EpubRenderMode.READER_LAYOUT, manager.defaultRenderMode.first())
        for (mode in listOf(EpubRenderMode.BOOK_LAYOUT, EpubRenderMode.READER_LAYOUT)) {
            manager.saveDefaultRenderMode(mode)
            val reloaded = DataStoreManager(context)
            assertEquals(mode, reloaded.defaultRenderMode.first())
            assertEquals(mode, reloaded.renderMode("new-book").first())
            assertEquals(mode, reloaded.readerPreferences("new-book").first().renderMode)
        }
        context.dataStore.edit { it[stringPreferencesKey("default_render_mode")] = "unknown" }
        assertEquals(EpubRenderMode.READER_LAYOUT, manager.readerPreferences("new-book").first().renderMode)
    }

    @Test fun explicitAndLegacyBookChoicesOverrideGlobalDefault() = runBlocking {
        manager.saveRenderMode("explicit-reader", EpubRenderMode.READER_LAYOUT)
        context.dataStore.edit {
            it[booleanPreferencesKey("optimize_layout_legacy-reader")] = true
            it[booleanPreferencesKey("use_epub_css_legacy-book")] = true
            it[booleanPreferencesKey("optimize_layout_legacy-original")] = false
        }
        manager.saveDefaultRenderMode(EpubRenderMode.BOOK_LAYOUT)
        for (book in listOf("explicit-reader", "legacy-reader")) {
            assertEquals(EpubRenderMode.READER_LAYOUT, manager.renderMode(book).first())
            assertEquals(EpubRenderMode.READER_LAYOUT, manager.readerPreferences(book).first().renderMode)
            assertEquals(EpubRenderMode.READER_LAYOUT, manager.migrateRenderMode(book))
        }
        manager.saveDefaultRenderMode(EpubRenderMode.READER_LAYOUT)
        for (book in listOf("legacy-book", "legacy-original")) {
            assertEquals(EpubRenderMode.BOOK_LAYOUT, manager.readerPreferences(book).first().renderMode)
            assertEquals(EpubRenderMode.BOOK_LAYOUT, manager.migrateRenderMode(book))
        }
    }

    @Test fun migrationAndFormatInitializationRespectDefaultAndKeepBookChoices() = runBlocking {
        manager.initializeBookLayoutMode("format-fallback")
        assertEquals(EpubRenderMode.BOOK_LAYOUT, manager.renderMode("format-fallback").first())
        manager.saveDefaultRenderMode(EpubRenderMode.READER_LAYOUT)
        manager.initializeBookLayoutMode("markdown", useDefaultRenderMode = false)
        assertEquals(EpubRenderMode.BOOK_LAYOUT, manager.renderMode("markdown").first())
        manager.initializeBookLayoutMode("word")
        assertEquals(EpubRenderMode.READER_LAYOUT, manager.renderMode("word").first())
        manager.saveDefaultRenderMode(EpubRenderMode.BOOK_LAYOUT)
        manager.initializeBookLayoutMode("word")
        assertEquals(EpubRenderMode.READER_LAYOUT, manager.renderMode("word").first())
        assertEquals(EpubRenderMode.BOOK_LAYOUT, manager.migrateRenderMode("migrated"))
        manager.saveDefaultRenderMode(EpubRenderMode.READER_LAYOUT)
        assertEquals(EpubRenderMode.BOOK_LAYOUT, manager.readerPreferences("migrated").first().renderMode)
    }
}
