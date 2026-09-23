package com.huangder.lumibooks.data.local

import android.app.Application
import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.test.core.app.ApplicationProvider
import com.huangder.lumibooks.domain.model.MenuAnimationStyle
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
class MenuAnimationStylePersistenceTest {
    @Test fun preferenceSurvivesReloadLaunchSnapshotAndBackupRestore() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.dataStore.edit { it.clear() }
        val manager = DataStoreManager(context)
        assertEquals("liquid", manager.menuAnimationStyle.first())

        manager.saveMenuAnimationStyle("normal")
        val reloaded = DataStoreManager(context)
        assertEquals("normal", reloaded.menuAnimationStyle.first())
        LaunchThemeController.updateThemeSnapshot(context, reloaded.launchThemeSnapshot.first())
        assertEquals("normal", LaunchThemeController.themeSnapshot(context).menuAnimationStyle)

        val backup = reloaded.exportPortablePreferences("menu-test-device")
        assertEquals("normal", backup.single { it.key == "menu_animation_style" }.value)
        context.dataStore.edit { it.clear() }
        reloaded.applyPortablePreferences(backup)
        assertEquals("normal", reloaded.menuAnimationStyle.first())

        reloaded.saveMenuAnimationStyle("unknown-future-style")
        assertEquals("liquid", reloaded.menuAnimationStyle.first())
        assertEquals(MenuAnimationStyle.LIQUID, MenuAnimationStyle.fromStoredValue(null))
        LaunchThemeController.updateThemeSnapshot(context, reloaded.launchThemeSnapshot.first())
        assertEquals("liquid", LaunchThemeController.themeSnapshot(context).menuAnimationStyle)
    }
}
