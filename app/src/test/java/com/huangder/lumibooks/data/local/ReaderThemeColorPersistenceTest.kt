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
class ReaderThemeColorPersistenceTest {
    @Test fun customTextColorsPersistInTheirSuiteAndDayNightMode() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.dataStore.edit { it.clear() }
        val manager = DataStoreManager(context)
        val before = manager.readerThemeSuiteState.first()
        val daySuite = before.suites.first { it.id == ReaderThemeSuites.DAY_ID }
        val sepiaSuite = before.suites.first { it.id == ReaderThemeSuites.SEPIA_ID }
        val layout = ReaderLayoutTarget.READER_LAYOUT
        val lightColor = 0xFF123456.toInt()
        val darkColor = 0xFFDCE1F2.toInt()
        manager.updateReaderThemeSuite(daySuite.id, layout,
            daySuite.settingsFor(layout, false).copy(textColor = lightColor), modeDark = false)
        manager.updateReaderThemeSuite(daySuite.id, layout,
            daySuite.settingsFor(layout, true).copy(textColor = darkColor), modeDark = true)

        val reloaded = DataStoreManager(context).readerThemeSuiteState.first()
        val saved = reloaded.suites.first { it.id == daySuite.id }
        assertEquals(lightColor, saved.settingsFor(layout, false).textColor)
        assertEquals(darkColor, saved.settingsFor(layout, true).textColor)
        val unchanged = reloaded.suites.first { it.id == sepiaSuite.id }
        assertEquals(sepiaSuite.settingsFor(layout, false), unchanged.settingsFor(layout, false))
        assertEquals(sepiaSuite.settingsFor(layout, true), unchanged.settingsFor(layout, true))

        manager.updateReaderThemeSuite(daySuite.id, layout,
            saved.settingsFor(layout, false).copy(textColor = null), modeDark = false)
        val automatic = DataStoreManager(context).readerThemeSuiteState.first()
            .suites.first { it.id == daySuite.id }
        assertEquals(null, automatic.settingsFor(layout, false).textColor)
        assertEquals(darkColor, automatic.settingsFor(layout, true).textColor)
    }
}
