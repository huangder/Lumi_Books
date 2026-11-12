package com.huangder.lumibooks.data.local

import android.app.Application
import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.core.app.ApplicationProvider
import com.huangder.lumibooks.domain.model.ReaderLayoutTarget
import com.huangder.lumibooks.domain.model.ReaderThemeSuiteCodec
import com.huangder.lumibooks.domain.model.ReaderThemeSuites
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class ReaderThemeFontPersistenceTest {
    @Test fun legacySepiaRestoresPublisherFontAndKeepsLaterFontSelections() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manager = DataStoreManager(context)
        val sepiaId = ReaderThemeSuites.SEPIA_ID
        val layout = ReaderLayoutTarget.BOOK_LAYOUT
        val defaults = ReaderThemeSuites.defaults()
        val sepia = defaults.first { it.id == sepiaId }
        val legacy = sepia.bookLayoutSettings.copy(fontType = "serif", fontSize = 23f)
        val day = defaults.first { it.id == ReaderThemeSuites.DAY_ID }
        val customDay = day.withSettings(layout, day.bookLayoutSettings.copy(fontType = "serif"))
        context.dataStore.edit {
            it.clear()
            it[intPreferencesKey("reader_theme_suites_version")] = 5
            it[stringPreferencesKey("reader_theme_suites")] =
                ReaderThemeSuiteCodec.encode(listOf(sepia.withSettings(layout, legacy), customDay))
        }

        manager.migrateReaderThemeSuites()
        val migrated = manager.readerThemeSuiteState.first().suites
        val migratedSepia = migrated.first { it.id == sepiaId }
        assertEquals(legacy.copy(fontType = "system"), migratedSepia.settingsFor(layout))
        assertEquals(sepia.settings, migratedSepia.settings)
        assertEquals(customDay.settingsFor(layout), migrated.first { it.id == day.id }.settingsFor(layout))

        manager.updateReaderThemeSuite(sepiaId, layout, legacy)
        manager.migrateReaderThemeSuites()
        val edited = manager.readerThemeSuiteState.first().suites.first { it.id == sepiaId }
        assertEquals(legacy, edited.settingsFor(layout))
    }
}
