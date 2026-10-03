package com.huangder.lumibooks.data.local

import android.app.Application
import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.test.core.app.ApplicationProvider
import com.huangder.lumibooks.util.LaunchThemeController
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class MotionPreferencePersistenceTest {
    @Test
    fun fastPreferenceSurvivesReloadAndKeepsLegacyMotionEnabled() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.dataStore.edit { it.clear() }

        val manager = DataStoreManager(context)
        manager.saveMotionPreference("fast")

        val reloaded = DataStoreManager(context)
        assertEquals("fast", reloaded.motionPreference.first())
        assertTrue(reloaded.entranceAnimationsEnabled.first())

        LaunchThemeController.updateThemeSnapshot(context, reloaded.launchThemeSnapshot.first())
        assertEquals("fast", LaunchThemeController.themeSnapshot(context).motionPreference)
    }
}
