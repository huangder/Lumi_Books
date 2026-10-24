package com.huangder.lumibooks.data.local

import android.app.Application
import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.test.core.app.ApplicationProvider
import com.huangder.lumibooks.util.LaunchThemeController
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class ReduceTransparencyPersistenceTest {
    @Test
    fun preferenceSurvivesReloadSnapshotAndBackupWithoutOverwritingVisualPreferences() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.dataStore.edit { it.clear() }
        val manager = DataStoreManager(context)
        assertFalse(manager.reduceTransparencyEnabled.first())

        manager.enableLiquidGlassTheme(0.8f)
        manager.saveMotionPreference("fast")
        manager.saveMenuAnimationStyle("normal")
        manager.saveReduceTransparencyEnabled(true)
        assertTrue(LaunchThemeController.themeSnapshot(context).reduceTransparencyEnabled)

        val reloaded = DataStoreManager(context)
        assertTrue(reloaded.reduceTransparencyEnabled.first())
        val snapshot = reloaded.launchThemeSnapshot.first()
        assertTrue(snapshot.reduceTransparencyEnabled)
        LaunchThemeController.updateThemeSnapshot(context, snapshot)
        assertTrue(LaunchThemeController.themeSnapshot(context).reduceTransparencyEnabled)

        val backup = reloaded.exportPortablePreferences("reduce-transparency-test")
        assertEquals("true", backup.single { it.key == "reduce_transparency_enabled" }.value)
        context.dataStore.edit { it.clear() }
        reloaded.applyPortablePreferences(backup)
        assertTrue(reloaded.reduceTransparencyEnabled.first())
        assertTrue(LaunchThemeController.themeSnapshot(context).reduceTransparencyEnabled)

        reloaded.saveReduceTransparencyEnabled(false)
        assertFalse(reloaded.reduceTransparencyEnabled.first())
        assertEquals("liquid_glass", reloaded.appTheme.first())
        assertEquals(0.8f, reloaded.liquidGlassTransparency.first(), 0.0001f)
        assertEquals("fast", reloaded.motionPreference.first())
        assertEquals("normal", reloaded.menuAnimationStyle.first())
    }

    @Test
    fun clearingSettingsRestoresTheDefaultInTheLaunchSnapshot() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.dataStore.edit { it.clear() }
        val manager = DataStoreManager(context)
        manager.saveReduceTransparencyEnabled(true)
        manager.clearAll()
        assertFalse(manager.reduceTransparencyEnabled.first())
        assertFalse(LaunchThemeController.themeSnapshot(context).reduceTransparencyEnabled)
        LaunchThemeController.updateThemeSnapshot(context, manager.launchThemeSnapshot.first())
        assertFalse(LaunchThemeController.themeSnapshot(context).reduceTransparencyEnabled)
    }
}
