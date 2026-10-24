package com.huangder.lumibooks.data.local

import android.app.Application
import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.test.core.app.ApplicationProvider
import com.huangder.lumibooks.domain.model.ReaderVolumeKeyAction
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
class ReaderVolumeKeyActionPersistenceTest {
    @Test
    fun defaultsToSystemVolumeAndMigratesLegacyPageTurnSetting() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.dataStore.edit { it.clear() }
        val manager = DataStoreManager(context)

        assertEquals(
            ReaderVolumeKeyAction.SYSTEM_VOLUME to ReaderVolumeKeyAction.SYSTEM_VOLUME,
            manager.volumeKeyActions.first()
        )

        context.dataStore.edit { it[booleanPreferencesKey("volume_key_page_turn")] = true }

        assertEquals(
            ReaderVolumeKeyAction.PREVIOUS_PAGE to ReaderVolumeKeyAction.NEXT_PAGE,
            manager.volumeKeyActions.first()
        )
    }

    @Test
    fun savesMatchingActionsAndStopsUsingLegacyValue() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.dataStore.edit { it.clear() }
        val manager = DataStoreManager(context)
        context.dataStore.edit { it[booleanPreferencesKey("volume_key_page_turn")] = true }

        manager.saveVolumeKeyActions(
            ReaderVolumeKeyAction.BRIGHTNESS_UP,
            ReaderVolumeKeyAction.BRIGHTNESS_UP
        )

        assertEquals(
            ReaderVolumeKeyAction.BRIGHTNESS_UP to ReaderVolumeKeyAction.BRIGHTNESS_UP,
            manager.volumeKeyActions.first()
        )
        assertNull(context.dataStore.data.first()[booleanPreferencesKey("volume_key_page_turn")])
    }

    @Test
    fun advancedReaderSettingsResetVolumeActionsToSystemDefaults() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.dataStore.edit { it.clear() }
        val manager = DataStoreManager(context)
        manager.saveVolumeKeyActions(
            ReaderVolumeKeyAction.TOGGLE_BOOKMARK,
            ReaderVolumeKeyAction.FONT_SIZE_DOWN
        )

        manager.resetAdvancedReaderSettings()

        assertEquals(
            ReaderVolumeKeyAction.SYSTEM_VOLUME to ReaderVolumeKeyAction.SYSTEM_VOLUME,
            manager.volumeKeyActions.first()
        )
    }
}
