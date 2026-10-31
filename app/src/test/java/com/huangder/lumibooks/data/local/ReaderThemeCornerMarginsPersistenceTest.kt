package com.huangder.lumibooks.data.local

import android.app.Application
import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.core.app.ApplicationProvider
import com.huangder.lumibooks.domain.model.ReaderCornerMargins
import com.huangder.lumibooks.domain.model.ReaderLayoutTarget
import com.huangder.lumibooks.domain.model.ReaderThemeSuiteCodec
import com.huangder.lumibooks.domain.model.ReaderThemeSuites
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class ReaderThemeCornerMarginsPersistenceTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private val layout = ReaderLayoutTarget.READER_LAYOUT

    @Test fun marginsPersistIndependentlyAcrossSuitesModesAndLayouts() = runBlocking {
        context.dataStore.edit { it.clear() }
        val manager = DataStoreManager(context)
        manager.migrateReaderThemeSuites()
        val suite = manager.readerThemeSuiteState.first().suites.first { it.id == "day" }
        val light = ReaderCornerMargins(11f, 12f, 13f, 14f)
        val dark = ReaderCornerMargins(21f, 22f, 23f, 24f)
        val book = ReaderCornerMargins(31f, 32f, 33f, 34f)
        manager.updateReaderThemeSuite("day", layout, suite.settings.copy(cornerMargins = light), false)
        manager.updateReaderThemeSuite("day", layout, suite.settings.copy(cornerMargins = dark), true)
        manager.updateReaderThemeSuite("day", ReaderLayoutTarget.BOOK_LAYOUT,
            suite.bookLayoutSettings.copy(cornerMargins = book))

        val saved = DataStoreManager(context).readerThemeSuiteState.first().suites
        val day = saved.first { it.id == "day" }
        assertEquals(light, day.settingsFor(layout, false).cornerMargins)
        assertEquals(dark, day.settingsFor(layout, true).cornerMargins)
        assertEquals(book, day.bookLayoutSettings.cornerMargins)
        assertEquals(ReaderCornerMargins(), saved.first { it.id == "sepia" }.settings.cornerMargins)

        manager.resetAdvancedReaderSettings("day", layout, false)
        val reset = manager.readerThemeSuiteState.first().suites.first { it.id == "day" }
        assertEquals(ReaderCornerMargins(), reset.settingsFor(layout, false).cornerMargins)
        assertEquals(dark, reset.settingsFor(layout, true).cornerMargins)
        assertEquals(book, reset.bookLayoutSettings.cornerMargins)
    }

    @Test fun activatingSuiteAppliesMarginsAndClearsInheritedEdges() = runBlocking {
        context.dataStore.edit { it.clear() }
        val manager = DataStoreManager(context)
        manager.migrateReaderThemeSuites()
        val suites = manager.readerThemeSuiteState.first().suites
        for ((id, margins) in listOf(
            "day" to ReaderCornerMargins(0f, 30f, 40f, 50f),
            "sepia" to ReaderCornerMargins(topDp = 60f)
        )) {
            manager.updateReaderThemeSuite(id, layout,
                suites.first { it.id == id }.settings.copy(cornerMargins = margins))
        }
        manager.setActiveReaderThemeSuite("day")
        assertEquals(0f, context.dataStore.data.first()[floatPreferencesKey("reader_corner_margin_left")])
        manager.setActiveReaderThemeSuite("sepia")
        val preferences = context.dataStore.data.first()
        assertNull(preferences[floatPreferencesKey("reader_corner_margin_left")])
        assertNull(preferences[floatPreferencesKey("reader_corner_margin_right")])
        assertNull(preferences[floatPreferencesKey("reader_corner_margin_bottom")])
        assertEquals(60f, preferences[floatPreferencesKey("reader_corner_margin_top")])
        manager.setActiveReaderThemeSuite("day")
        assertEquals(40f, context.dataStore.data.first()[floatPreferencesKey("reader_corner_margin_top")])
    }

    @Test fun migrationPreservesGlobalMarginsAndDoesNotOverwriteLaterEdits() = runBlocking {
        context.dataStore.edit {
            it.clear()
            it[intPreferencesKey("reader_theme_suites_version")] = 4
            it[stringPreferencesKey("reader_theme_suites")] = ReaderThemeSuiteCodec.encode(ReaderThemeSuites.defaults())
            it[floatPreferencesKey("reader_corner_margin_left")] = 0f
            it[floatPreferencesKey("reader_corner_margin_top")] = 42f
        }
        val manager = DataStoreManager(context)
        manager.migrateReaderThemeSuites()
        val inherited = ReaderCornerMargins(leftDp = 0f, topDp = 42f)
        manager.readerThemeSuiteState.first().suites.forEach { suite ->
            assertEquals(inherited, suite.settingsFor(layout, false).cornerMargins)
            assertEquals(inherited, suite.settingsFor(layout, true).cornerMargins)
            assertEquals(inherited, suite.bookLayoutSettings.cornerMargins)
        }
        val day = manager.readerThemeSuiteState.first().suites.first { it.id == "day" }
        val changed = inherited.copy(bottomDp = 55f)
        manager.updateReaderThemeSuite("day", layout, day.settings.copy(cornerMargins = changed), false)
        manager.migrateReaderThemeSuites()
        assertEquals(changed, manager.readerThemeSuiteState.first().suites.first { it.id == "day" }
            .settingsFor(layout, false).cornerMargins)
    }
}
