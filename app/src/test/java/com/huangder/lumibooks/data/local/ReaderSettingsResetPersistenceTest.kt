package com.huangder.lumibooks.data.local

import android.app.Application
import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.test.core.app.ApplicationProvider
import com.huangder.lumibooks.domain.model.ReaderLayoutTarget
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
class ReaderSettingsResetPersistenceTest {
    @Test
    fun resetReaderLayoutPersistsOnlyTheSelectedMode() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.dataStore.edit { it.clear() }
        val manager = DataStoreManager(context)
        val suiteId = ReaderThemeSuites.DAY_ID
        val original = manager.readerThemeSuiteState.first().suites.first { it.id == suiteId }
        manager.updateReaderThemeSuite(
            suiteId, ReaderLayoutTarget.READER_LAYOUT,
            original.settingsFor(ReaderLayoutTarget.READER_LAYOUT, false).copy(marginBottom = 110f),
            modeDark = false
        )
        manager.updateReaderThemeSuite(
            suiteId, ReaderLayoutTarget.READER_LAYOUT,
            original.settingsFor(ReaderLayoutTarget.READER_LAYOUT, true).copy(marginBottom = 170f),
            modeDark = true
        )

        manager.resetAdvancedReaderSettings(suiteId, ReaderLayoutTarget.READER_LAYOUT, false)

        val reloaded = DataStoreManager(context).readerThemeSuiteState.first()
            .suites.first { it.id == suiteId }
        assertEquals(64f, reloaded.settingsFor(ReaderLayoutTarget.READER_LAYOUT, false).marginBottom)
        assertEquals(170f, reloaded.settingsFor(ReaderLayoutTarget.READER_LAYOUT, true).marginBottom)
    }

    @Test
    fun resetBookLayoutPersistsWithoutChangingReaderLayout() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.dataStore.edit { it.clear() }
        val manager = DataStoreManager(context)
        val suiteId = ReaderThemeSuites.PUBLISHER_ID
        val original = manager.readerThemeSuiteState.first().suites.first { it.id == suiteId }
        manager.updateReaderThemeSuite(
            suiteId, ReaderLayoutTarget.BOOK_LAYOUT,
            original.settingsFor(ReaderLayoutTarget.BOOK_LAYOUT).copy(marginTop = 180f)
        )

        manager.resetAdvancedReaderSettings(suiteId, ReaderLayoutTarget.BOOK_LAYOUT)

        val reloaded = DataStoreManager(context).readerThemeSuiteState.first()
            .suites.first { it.id == suiteId }
        assertEquals(64f, reloaded.settingsFor(ReaderLayoutTarget.BOOK_LAYOUT).marginTop)
        assertEquals(original.settings.marginTop, reloaded.settings.marginTop)
    }
}
