package com.huangder.lumibooks.ui.reader

import android.app.Application
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
        compose.onNode(weightSlider).assert(SemanticsMatcher("fine variable weight steps") {
            it.config[SemanticsProperties.ProgressBarRangeInfo].steps == 699
        })
        compose.runOnIdle { assertEquals(550, weight.value); font.value = "kaiti" }
        val staticSlider = SemanticsMatcher("static font weight range") {
            it.config.getOrNull(SemanticsProperties.ProgressBarRangeInfo)?.range == 100f..900f
        }
        compose.waitUntil(10_000) { compose.onAllNodes(staticSlider).fetchSemanticsNodes().size == 1 }
        compose.onNode(staticSlider).assert(SemanticsMatcher("font switch preserves weight") {
            it.config[SemanticsProperties.ProgressBarRangeInfo].current == 550f &&
                it.config[SemanticsProperties.ProgressBarRangeInfo].steps == 7
        })
        compose.runOnIdle { font.value = "serif" }
        compose.waitUntil(10_000) { compose.onAllNodes(weightSlider).fetchSemanticsNodes().size == 1 }
        compose.onNode(weightSlider).assert(SemanticsMatcher("restored weight") {
            it.config[SemanticsProperties.ProgressBarRangeInfo].current == 550f
        })
        compose.runOnIdle { font.value = "kaiti" }
        compose.waitUntil(10_000) { compose.onAllNodes(staticSlider).fetchSemanticsNodes().size == 1 }
        compose.onNode(staticSlider).performSemanticsAction(SemanticsActions.SetProgress) { it(570f) }
        compose.runOnIdle { assertEquals(600, weight.value) }
    }

    @Test fun horizontalDragCommitsItsLastValueWhenThePointerStreamIsCancelled() {
        val weight = mutableStateOf(400)
        compose.setContent {
            ThemeSettingsSheet(visible = true, currentFontSize = 20f,
                currentFontType = "serif", currentBodyFontWeight = weight.value,
                onBodyFontWeightChange = { weight.value = it },
                currentTheme = "white", eInkModeEnabled = true,
                onFontSizeChange = {}, onThemeChange = {}, onOpenAdvanced = {}, onDismiss = {})
        }
        val weightSlider = SemanticsMatcher("variable weight range") {
            it.config.getOrNull(SemanticsProperties.ProgressBarRangeInfo)?.range == 200f..900f
        }
        compose.waitUntil(10_000) { compose.onAllNodes(weightSlider).fetchSemanticsNodes().size == 1 }
        compose.onNode(weightSlider).performTouchInput {
            down(Offset(width * 0.3f, centerY))
            moveTo(Offset(width * 0.8f, centerY), delayMillis = 100)
            cancel()
        }
        compose.runOnIdle {
            assertEquals(750, weight.value)
        }
    }

    @Test fun staticFontPreviewsInHundredWeightStepsAndCommitsOnRelease() {
        val weight = mutableStateOf(400)
        var previews = 0
        var commits = 0
        compose.setContent {
            ThemeSettingsSheet(visible = true, currentFontSize = 20f,
                currentFontType = "kaiti", currentBodyFontWeight = weight.value,
                onBodyFontWeightChange = { weight.value = it; commits++ },
                onBodyFontWeightPreview = { weight.value = it; previews++ },
                currentTheme = "white", eInkModeEnabled = true,
                onFontSizeChange = {}, onThemeChange = {}, onOpenAdvanced = {}, onDismiss = {})
        }
        val slider = SemanticsMatcher("static font weight range") {
            it.config.getOrNull(SemanticsProperties.ProgressBarRangeInfo)?.range == 100f..900f
        }
        compose.onNode(slider).performScrollTo().performTouchInput {
            down(Offset(width * 0.3f, centerY))
            moveTo(Offset(width * 0.8f, centerY), delayMillis = 100)
        }
        compose.runOnIdle {
            assertTrue(previews > 0)
            assertEquals(0, commits)
            assertEquals(0, weight.value % 100)
        }
        compose.onAllNodes(slider)[0].performTouchInput { up() }
        compose.runOnIdle {
            assertEquals(1, commits)
            assertEquals(800, weight.value)
        }
    }
}
