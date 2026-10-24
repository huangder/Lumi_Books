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
import com.huangder.lumibooks.domain.model.LiquidGlassStyle
import com.huangder.lumibooks.ui.components.LocalLiquidGlassControlEdgeEnabled
import com.huangder.lumibooks.ui.theme.EBookReaderTheme
import com.huangder.lumibooks.util.LaunchThemeController
import kotlinx.coroutines.flow.first
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
class LiquidGlassStyleSettingTest {
    @get:Rule val compose = createComposeRule()

    @Test fun selectingEitherOptionPersistsAndUpdatesThemeImmediately() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.dataStore.edit { it.clear() }
        val manager = DataStoreManager(context)
        var edgeEnabled = true
        compose.setContent {
            val style by manager.liquidGlassStyle.collectAsState(initial = LiquidGlassStyle.DEFAULT)
            val scope = rememberCoroutineScope()
            EBookReaderTheme(appTheme = "liquid_glass", liquidGlassStyle = style) {
                edgeEnabled = LocalLiquidGlassControlEdgeEnabled.current
                LiquidGlassStyleSettingRow(style) { value ->
                    scope.launch { manager.saveLiquidGlassStyle(LiquidGlassStyle.fromStoredValue(value)) }
                }
            }
        }
        val defaultLabel = context.getString(R.string.liquid_glass_style_default)
        val originalLabel = context.getString(R.string.liquid_glass_style_original)
        compose.onNodeWithText(context.getString(R.string.liquid_glass_style)).assertIsDisplayed()
        compose.onNodeWithText(defaultLabel).performClick()
        compose.onNodeWithText(originalLabel).performClick()
        compose.waitUntil { !edgeEnabled }
        assertEquals(LiquidGlassStyle.ORIGINAL, manager.liquidGlassStyle.first())
        assertEquals(LiquidGlassStyle.ORIGINAL, LaunchThemeController.themeSnapshot(context).liquidGlassStyle)
        compose.onNodeWithText(originalLabel).assertIsDisplayed().performClick()
        compose.onNodeWithText(defaultLabel).performClick()
        compose.waitUntil { edgeEnabled }
        assertEquals(LiquidGlassStyle.DEFAULT, manager.liquidGlassStyle.first())
    }
}
