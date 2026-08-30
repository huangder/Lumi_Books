package com.huangder.lumibooks.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.huangder.lumibooks.domain.model.ReaderTextAlignment
import com.huangder.lumibooks.util.epub.EpubRenderMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReaderPreferencesSnapshotTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        runBlocking {
            context = ApplicationProvider.getApplicationContext()
            context.dataStore.edit { it.clear() }
        }
    }

    @After
    fun tearDown() {
        runBlocking { context.dataStore.edit { it.clear() } }
    }

    @Test
    fun snapshotUsesReaderDefaultsInOneEmission() = runBlocking {
        val snapshot = DataStoreManager(context).readerPreferences("book-id").first()

        assertEquals(16f, snapshot.fontSize)
        assertEquals(1.5f, snapshot.lineHeight)
        assertEquals(ReaderTextAlignment.NATURAL, snapshot.textAlignment)
        assertEquals(EpubRenderMode.READER_LAYOUT, snapshot.renderMode)
        assertTrue(snapshot.preserveEpubBackground)
        assertEquals("auto", snapshot.txtEncoding)
        assertFalse(snapshot.readerThemeSuiteBookScoped)
        assertNull(snapshot.readerThemeSuiteBookActiveId)
    }

    @Test
    fun bookScopedThemeSuiteSelectionPersistsPerBook() = runBlocking {
        val manager = DataStoreManager(context)

        manager.setReaderThemeSuiteBookScoped("book-a", enabled = true, activeSuiteId = "night")
        val bookA = manager.readerPreferences("book-a").first()
        val bookB = manager.readerPreferences("book-b").first()

        assertTrue(bookA.readerThemeSuiteBookScoped)
        assertEquals("night", bookA.readerThemeSuiteBookActiveId)
        assertFalse(bookB.readerThemeSuiteBookScoped)
        assertNull(bookB.readerThemeSuiteBookActiveId)

        manager.setReaderThemeSuiteBookScoped("book-a", enabled = false)
        val restored = manager.readerPreferences("book-a").first()
        assertFalse(restored.readerThemeSuiteBookScoped)
        assertNull(restored.readerThemeSuiteBookActiveId)
    }
}
