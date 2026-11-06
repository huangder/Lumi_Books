package com.huangder.lumibooks.data.local

import android.app.Application
import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.core.app.ApplicationProvider
import com.huangder.lumibooks.domain.model.LiquidGlassSamplingQuality
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
class LiquidGlassSamplingPersistenceTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()

    @Before fun clear() = runBlocking {
        context.dataStore.edit { it.clear() }
        context.getSharedPreferences("launch_theme_state", Context.MODE_PRIVATE).edit().clear().commit()
        Unit
    }

    @Test fun missingAndUnknownValuesUseBalancedInBothPreferenceStores() = runBlocking {
        val manager = DataStoreManager(context)
        assertEquals(LiquidGlassSamplingQuality.BALANCED, manager.liquidGlassSamplingQuality.first())
        assertEquals(LiquidGlassSamplingQuality.BALANCED, LaunchThemeController.themeSnapshot(context).liquidGlassSamplingQuality)
        context.dataStore.edit { it[stringPreferencesKey("liquid_glass_sampling_quality")] = "future" }
        context.getSharedPreferences("launch_theme_state", Context.MODE_PRIVATE).edit()
            .putString("liquid_glass_sampling_quality", "future").commit()
        assertEquals(LiquidGlassSamplingQuality.BALANCED, manager.liquidGlassSamplingQuality.first())
        assertEquals(LiquidGlassSamplingQuality.BALANCED, manager.launchThemeSnapshot.first().liquidGlassSamplingQuality)
        assertEquals(LiquidGlassSamplingQuality.BALANCED, LaunchThemeController.themeSnapshot(context).liquidGlassSamplingQuality)
    }

    @Test fun qualitySurvivesReloadAndThemeSwitchesWithoutChangingOpticsPreferences() = runBlocking {
        val manager = DataStoreManager(context)
        manager.saveLiquidGlassTransparency(0.7f)
        for (quality in LiquidGlassSamplingQuality.entries) {
            manager.saveLiquidGlassSamplingQuality(quality)
            val reloaded = DataStoreManager(context)
            assertEquals(quality, reloaded.liquidGlassSamplingQuality.first())
            assertEquals(quality, LaunchThemeController.themeSnapshot(context).liquidGlassSamplingQuality)
            val snapshot = reloaded.launchThemeSnapshot.first()
            assertEquals(quality, snapshot.liquidGlassSamplingQuality)
            LaunchThemeController.updateThemeSnapshot(context, snapshot)
            assertEquals(quality, LaunchThemeController.themeSnapshot(context).liquidGlassSamplingQuality)
            manager.saveAppTheme("lumi")
            manager.saveAppTheme("liquid_glass")
            assertEquals(quality, reloaded.liquidGlassSamplingQuality.first())
            assertEquals(0.7f, reloaded.liquidGlassTransparency.first(), 0f)
        }
    }
}
