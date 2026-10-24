package com.huangder.lumibooks.data.local

import android.app.Application
import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.core.app.ApplicationProvider
import com.huangder.lumibooks.domain.model.LongPressSelectionRange
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class LongPressSelectionRangePersistenceTest {
    @Test fun eachScopeSurvivesReloadAndReachesReaderSnapshot() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.dataStore.edit { it.clear() }
        val manager = DataStoreManager(context)
        assertEquals(LongPressSelectionRange.WORD, manager.longPressSelectionRange.first())
        context.dataStore.edit { it[stringPreferencesKey("long_press_selection_range")] = "invalid" }
        assertEquals(LongPressSelectionRange.WORD, manager.readerPreferences("test-book").first().longPressSelectionRange)
        for (scope in LongPressSelectionRange.entries) {
            manager.saveLongPressSelectionRange(scope)
            val reloaded = DataStoreManager(context)
            assertEquals(scope, reloaded.longPressSelectionRange.first())
            assertEquals(scope, reloaded.readerPreferences("test-book").first().longPressSelectionRange)
        }
    }
}
