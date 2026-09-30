package com.huangder.lumibooks.ui.reader

import android.app.Application
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "en-rUS-w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ReaderWeightSliderTest {
    @get:Rule val compose = createComposeRule()

    @Test fun switchingFontsShowsTheirAxisAndUpdatesWeight() {
        val font = mutableStateOf("serif")
        val weight = mutableStateOf(400)
        compose.setContent {
            ThemeSettingsSheet(visible = true, currentFontSize = 20f,
                currentFontType = font.value, currentBodyFontWeight = weight.value,
                onBodyFontWeightChange = { weight.value = it },
                currentTheme = "white", eInkModeEnabled = true,
                onFontSizeChange = {}, onThemeChange = {}, onOpenAdvanced = {}, onDismiss = {})
        }
        val weightSlider = SemanticsMatcher("variable weight range") {
            it.config.getOrNull(SemanticsProperties.ProgressBarRangeInfo)?.range == 200f..900f
        }
        compose.waitUntil(10_000) { compose.onAllNodes(weightSlider).fetchSemanticsNodes().size == 1 }
        compose.onNode(weightSlider).performSemanticsAction(SemanticsActions.SetProgress) { it(550f) }
        compose.runOnIdle { assertEquals(550, weight.value); font.value = "kaiti" }
        compose.waitUntil(10_000) { compose.onAllNodes(weightSlider).fetchSemanticsNodes().isEmpty() }
        compose.runOnIdle { font.value = "serif" }
        compose.waitUntil(10_000) { compose.onAllNodes(weightSlider).fetchSemanticsNodes().size == 1 }
        compose.onNode(weightSlider).assert(SemanticsMatcher("restored weight") {
            it.config[SemanticsProperties.ProgressBarRangeInfo].current == 550f
        })
    }
}
