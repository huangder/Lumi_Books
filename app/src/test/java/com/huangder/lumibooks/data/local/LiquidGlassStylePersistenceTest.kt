package com.huangder.lumibooks.data.local

import android.app.Application
import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.core.app.ApplicationProvider
import com.huangder.lumibooks.domain.model.LiquidGlassStyle
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
class LiquidGlassStylePersistenceTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()

    @Before fun clear() = runBlocking {
        context.dataStore.edit { it.clear() }
        context.getSharedPreferences("launch_theme_state", Context.MODE_PRIVATE).edit().clear().commit()
        Unit
    }

    @Test fun missingAndUnknownValuesKeepCurrentOutline() = runBlocking {
        val manager = DataStoreManager(context)
        assertEquals(LiquidGlassStyle.DEFAULT, manager.liquidGlassStyle.first())
        assertEquals(LiquidGlassStyle.DEFAULT, manager.launchThemeSnapshot.first().liquidGlassStyle)
        assertEquals(LiquidGlassStyle.DEFAULT, LaunchThemeController.themeSnapshot(context).liquidGlassStyle)
        context.dataStore.edit { it[stringPreferencesKey("liquid_glass_style")] = "future_style" }
        context.getSharedPreferences("launch_theme_state", Context.MODE_PRIVATE).edit()
            .putString("liquid_glass_style", "future_style").commit()
        assertEquals(LiquidGlassStyle.DEFAULT, manager.liquidGlassStyle.first())
        assertEquals(LiquidGlassStyle.DEFAULT, manager.launchThemeSnapshot.first().liquidGlassStyle)
        assertEquals(LiquidGlassStyle.DEFAULT, LaunchThemeController.themeSnapshot(context).liquidGlassStyle)
    }

    @Test fun bothStylesSurviveReloadSnapshotAndThemeChanges() = runBlocking {
        val manager = DataStoreManager(context)
        val transparency = manager.liquidGlassTransparency.first()
        val hdr = manager.liquidGlassHdrHighlightEnabled.first()
        for (style in listOf(LiquidGlassStyle.ORIGINAL, LiquidGlassStyle.DEFAULT)) {
            manager.saveLiquidGlassStyle(style)
            val reloaded = DataStoreManager(context)
            assertEquals(style, reloaded.liquidGlassStyle.first())
            assertEquals(style, LaunchThemeController.themeSnapshot(context).liquidGlassStyle)
            val snapshot = reloaded.launchThemeSnapshot.first()
            assertEquals(style, snapshot.liquidGlassStyle)
            LaunchThemeController.updateThemeSnapshot(context, snapshot)
            assertEquals(style, LaunchThemeController.themeSnapshot(context).liquidGlassStyle)
            for (theme in listOf("lumi", "material3", "liquid_glass")) {
                manager.saveAppTheme(theme)
                assertEquals(style, reloaded.liquidGlassStyle.first())
            }
            assertEquals(transparency, reloaded.liquidGlassTransparency.first())
            assertEquals(hdr, reloaded.liquidGlassHdrHighlightEnabled.first())
        }
    }

    @Test fun portablePreferencesRestoreBothStylesAndOldBackupsUseDefault() = runBlocking {
        val manager = DataStoreManager(context)
        for (style in LiquidGlassStyle.entries) {
            manager.saveLiquidGlassStyle(style)
            val backup = manager.exportPortablePreferences("source")
            assertEquals(style.storedValue, backup.single { it.key == "liquid_glass_style" }.value)
            context.dataStore.edit { it.clear() }
            manager.applyPortablePreferences(backup)
            assertEquals(style, manager.liquidGlassStyle.first())
            val snapshot = manager.launchThemeSnapshot.first()
            LaunchThemeController.updateThemeSnapshot(context, snapshot)
            assertEquals(style, LaunchThemeController.themeSnapshot(context).liquidGlassStyle)
        }
        manager.saveLiquidGlassStyle(LiquidGlassStyle.ORIGINAL)
        val oldBackup = manager.exportPortablePreferences("source").filterNot { it.key == "liquid_glass_style" }
        manager.replacePortablePreferences(oldBackup, "target")
        assertEquals(LiquidGlassStyle.DEFAULT, manager.liquidGlassStyle.first())
    }
}
