package com.huangder.lumibooks.ui.settings

import android.app.Application
import android.content.Context
import androidx.compose.runtime.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.assertIsDisplayed
import androidx.datastore.preferences.core.edit
import androidx.test.core.app.ApplicationProvider
import com.huangder.lumibooks.R
import com.huangder.lumibooks.data.local.DataStoreManager
import com.huangder.lumibooks.data.local.dataStore
import com.huangder.lumibooks.domain.model.LiquidGlassSamplingQuality
import com.huangder.lumibooks.ui.theme.EBookReaderTheme
import com.huangder.lumibooks.ui.theme.LocalLiquidGlassSamplingQuality
import com.huangder.lumibooks.ui.theme.LocalAppTheme
import com.huangder.lumibooks.util.LaunchThemeController
import com.kyant.backdrop.LocalBackdropSamplingScale
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "zh-rCN")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LiquidGlassSamplingSettingTest {
    @get:Rule val compose = createComposeRule()

    @Test fun allFourOptionsPersistAndReachNestedThemeImmediately() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.dataStore.edit { it.clear() }
        val manager = DataStoreManager(context)
        var activeQuality = LiquidGlassSamplingQuality.BALANCED
        var bufferScale = 1f
        var glassActive = false
        compose.setContent {
            val quality by manager.liquidGlassSamplingQuality.collectAsState(initial = LiquidGlassSamplingQuality.BALANCED)
            val scope = rememberCoroutineScope()
            EBookReaderTheme(appTheme = "liquid_glass", liquidGlassSamplingQuality = quality) {
                // Reader and other nested themes must inherit the setting.
                EBookReaderTheme(appTheme = "liquid_glass") {
                    activeQuality = LocalLiquidGlassSamplingQuality.current
                    bufferScale = LocalBackdropSamplingScale.current
                    glassActive = LocalAppTheme.current == "liquid_glass"
                    LiquidGlassSamplingSettingRow(quality) { value ->
                        scope.launch { manager.saveLiquidGlassSamplingQuality(LiquidGlassSamplingQuality.fromStoredValue(value)) }
                    }
                }
            }
        }
        val labels = mapOf(
            LiquidGlassSamplingQuality.ORIGINAL to context.getString(R.string.liquid_glass_sampling_original),
            LiquidGlassSamplingQuality.CLEAR to context.getString(R.string.liquid_glass_sampling_clear),
            LiquidGlassSamplingQuality.BALANCED to context.getString(R.string.liquid_glass_sampling_balanced),
            LiquidGlassSamplingQuality.PERFORMANCE to context.getString(R.string.liquid_glass_sampling_performance)
        )
        compose.onNodeWithText(context.getString(R.string.liquid_glass_sampling_quality)).assertIsDisplayed()
        var selected = LiquidGlassSamplingQuality.BALANCED
        for (quality in listOf(LiquidGlassSamplingQuality.ORIGINAL, LiquidGlassSamplingQuality.CLEAR,
            LiquidGlassSamplingQuality.PERFORMANCE, LiquidGlassSamplingQuality.BALANCED)) {
            compose.onNodeWithText(labels.getValue(selected)).performClick()
            compose.onNodeWithText(labels.getValue(quality)).performClick()
            compose.waitUntil { activeQuality == quality }
            compose.runOnIdle { assertEquals(if (glassActive) quality.scale else 1f, bufferScale, 0f) }
            compose.waitUntil { LaunchThemeController.themeSnapshot(context).liquidGlassSamplingQuality == quality }
            assertEquals(quality, LaunchThemeController.themeSnapshot(context).liquidGlassSamplingQuality)
            selected = quality
        }
    }
}
