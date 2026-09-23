package com.huangder.lumibooks.data.local

import android.app.Application
import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.test.core.app.ApplicationProvider
import com.huangder.lumibooks.data.backup.PortablePreference
import com.huangder.lumibooks.util.LaunchThemeController
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class LumiEasterEggPersistenceTest {
    @Test fun lockedSelectionsAreRejectedAndUnlockSurvivesReloadAndOlderBackups() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.dataStore.edit { it.clear() }
        val manager = DataStoreManager(context)
        assertFalse(manager.lumiEasterEggUnlocked.first())
        manager.saveAppTheme("material3")
        manager.saveAppTheme("lumi_chan")
        assertEquals("material3", manager.appTheme.first())
        assertFalse(manager.saveAppIconStyle("lumi_chan"))
        assertEquals("lumi2", manager.appIconStyle.first())
        manager.applyPortablePreferences(listOf(PortablePreference("app_theme", "string", "lumi_chan", 1, "other")))
        assertEquals("lumi", manager.appTheme.first())

        manager.unlockLumiEasterEgg()
        manager.saveAppTheme("lumi_chan")
        assertTrue(manager.saveAppIconStyle("lumi_chan"))
        val reloaded = DataStoreManager(context)
        assertTrue(reloaded.lumiEasterEggUnlocked.first())
        assertEquals("lumi_chan", reloaded.appTheme.first())
        assertEquals("lumi_chan", reloaded.appIconStyle.first())
        val backup = reloaded.exportPortablePreferences("test-device")
        assertEquals("true", backup.single { it.key == "lumi_easter_egg_unlocked" }.value)
        reloaded.replacePortablePreferences(emptyList(), "older-backup")
        assertTrue(reloaded.lumiEasterEggUnlocked.first())
        reloaded.applyPortablePreferences(listOf(PortablePreference("lumi_easter_egg_unlocked", "boolean", "false", 2, "other")))
        assertTrue(reloaded.lumiEasterEggUnlocked.first())

        // Emulate a fresh installation restoring its portable backup.
        context.dataStore.edit { it.clear() }
        reloaded.applyPortablePreferences(backup)
        assertTrue(reloaded.lumiEasterEggUnlocked.first())
        assertEquals("lumi_chan", reloaded.appTheme.first())
        assertEquals("lumi_chan", reloaded.appIconStyle.first())
        LaunchThemeController.updateThemeSnapshot(context, reloaded.launchThemeSnapshot.first())
        assertEquals("lumi_chan", LaunchThemeController.themeSnapshot(context).appTheme)
        assertEquals("lumi_chan", LaunchThemeController.iconStyleSnapshot(context))
        assertTrue(LaunchThemeController.synchronizeLauncherComponents(context))
        val launcherIntent = android.content.Intent(android.content.Intent.ACTION_MAIN)
            .addCategory(android.content.Intent.CATEGORY_LAUNCHER).setPackage(context.packageName)
        val launchers = context.packageManager.queryIntentActivities(launcherIntent, 0)
        assertEquals(1, launchers.size)
        assertEquals("com.huangder.lumibooks.ui.splash.LumiChanSplashLauncherActivity", launchers.single().activityInfo.name)
    }
}
