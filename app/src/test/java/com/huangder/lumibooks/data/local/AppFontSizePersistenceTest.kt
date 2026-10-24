package com.huangder.lumibooks.data.local

import android.app.Application
import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.core.app.ApplicationProvider
import com.huangder.lumibooks.domain.model.AppFontSize
import com.huangder.lumibooks.util.LaunchThemeController
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
class AppFontSizePersistenceTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()

    @Before fun clear() = runBlocking {
        context.dataStore.edit { it.clear() }
        context.getSharedPreferences("launch_theme_state", Context.MODE_PRIVATE).edit().clear().commit()
        Unit
    }

    @Test fun missingAndInvalidValuesUseStandard() = runBlocking {
        val manager = DataStoreManager(context)
        assertEquals(AppFontSize.STANDARD, manager.appFontSize.first())
        assertEquals(AppFontSize.STANDARD, LaunchThemeController.themeSnapshot(context).appFontSize)
        context.dataStore.edit { it[stringPreferencesKey("app_font_size")] = "invalid" }
        context.getSharedPreferences("launch_theme_state", Context.MODE_PRIVATE).edit()
            .putString("app_font_size", "invalid").commit()
        assertEquals(AppFontSize.STANDARD, manager.appFontSize.first())
        assertEquals(AppFontSize.STANDARD, manager.launchThemeSnapshot.first().appFontSize)
        assertEquals(AppFontSize.STANDARD, LaunchThemeController.themeSnapshot(context).appFontSize)
    }

    @Test fun everyLevelSurvivesReloadAndLaunchSnapshotWithoutChangingBookSettings() = runBlocking {
        val manager = DataStoreManager(context)
        val bodySize = manager.fontSize.first()
        val readerSettings = manager.readerThemeSuiteState.first()
        val pdfMode = manager.pdfPageMode.first()
        AppFontSize.entries.forEach { size ->
            manager.saveAppFontSize(size)
            val reloaded = DataStoreManager(context)
            assertEquals(size, reloaded.appFontSize.first())
            val snapshot = reloaded.launchThemeSnapshot.first()
            assertEquals(size, snapshot.appFontSize)
            LaunchThemeController.updateThemeSnapshot(context, snapshot)
            assertEquals(size, LaunchThemeController.themeSnapshot(context).appFontSize)
            assertEquals(bodySize, reloaded.fontSize.first())
            assertEquals(readerSettings, reloaded.readerThemeSuiteState.first())
            assertEquals(pdfMode, reloaded.pdfPageMode.first())
        }
    }
}
