package com.huangder.lumibooks.ui.reader

import android.app.Application
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.huangder.lumibooks.ui.components.LiquidGlassDialogHost
import com.huangder.lumibooks.ui.theme.LocalAppTheme
import com.huangder.lumibooks.ui.theme.LocalLiquidGlassCapability
import com.huangder.lumibooks.ui.theme.LiquidGlassCapability
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
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
        val weight = mutableStateOf(400f)
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
        compose.onNode(weightSlider).assert(SemanticsMatcher("tenth variable weight steps") {
            it.config[SemanticsProperties.ProgressBarRangeInfo].steps == 6999
        })
        compose.runOnIdle { assertEquals(550f, weight.value, 0f); font.value = "kaiti" }
        val staticSlider = SemanticsMatcher("static font weight range") {
            it.config.getOrNull(SemanticsProperties.ProgressBarRangeInfo)?.range == 100f..900f
        }
        compose.waitUntil(10_000) { compose.onAllNodes(staticSlider).fetchSemanticsNodes().size == 1 }
        compose.onNode(staticSlider).assert(SemanticsMatcher("font switch preserves weight") {
            it.config[SemanticsProperties.ProgressBarRangeInfo].current == 550f &&
                it.config[SemanticsProperties.ProgressBarRangeInfo].steps == 7999
        })
        compose.runOnIdle { font.value = "serif" }
        compose.waitUntil(10_000) { compose.onAllNodes(weightSlider).fetchSemanticsNodes().size == 1 }
        compose.onNode(weightSlider).assert(SemanticsMatcher("restored weight") {
            it.config[SemanticsProperties.ProgressBarRangeInfo].current == 550f
        })
        compose.runOnIdle { font.value = "kaiti" }
        compose.waitUntil(10_000) { compose.onAllNodes(staticSlider).fetchSemanticsNodes().size == 1 }
        compose.onNode(staticSlider).performSemanticsAction(SemanticsActions.SetProgress) { it(570.4f) }
        compose.runOnIdle { assertEquals(570.4f, weight.value, 0f) }
    }

    @Test fun horizontalDragCommitsItsLastValueWhenThePointerStreamIsCancelled() {
        val weight = mutableStateOf(400f)
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
            assertEquals(750f, weight.value, 0f)
        }
    }

    @Test fun staticFontPreviewsTenthsAndCommitsOnRelease() {
        val weight = mutableStateOf(400f)
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
            assertEquals(0f, weight.value * 10f % 1f, 0f)
        }
        compose.onAllNodes(slider)[0].performTouchInput { up() }
        compose.runOnIdle {
            assertEquals(1, commits)
            assertEquals(800f, weight.value, 0f)
        }
    }

    @Test fun tappingWeightValueAllowsPreciseNumericInput() {
        val weight = mutableStateOf(400f)
        compose.setContent {
            // Match the reader's hosted glass dialog instead of creating a
            // separate Android dialog window in Robolectric.
            CompositionLocalProvider(
                LocalAppTheme provides "liquid_glass",
                LocalLiquidGlassCapability provides LiquidGlassCapability(false, false)
            ) {
                val backdrop = rememberLayerBackdrop()
                LiquidGlassDialogHost(backdrop = backdrop, modifier = Modifier.fillMaxSize()) {
                    ThemeSettingsSheet(
                        visible = true,
                        currentFontSize = 20f,
                        currentFontType = "kaiti",
                        currentBodyFontWeight = weight.value,
                        onBodyFontWeightChange = { weight.value = it },
                        currentTheme = "white",
                        eInkModeEnabled = true,
                        onFontSizeChange = {},
                        onThemeChange = {},
                        onOpenAdvanced = {},
                        onDismiss = {}
                    )
                }
            }
        }

        compose.onNodeWithTag("reader-body-font-weight-value").performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().size == 1
        }
        compose.onNode(hasSetTextAction()).performTextReplacement("425.7")
        compose.onNodeWithText("OK").performClick()
        compose.runOnIdle { assertEquals(425.7f, weight.value, 0.001f) }
        compose.onNode(hasSetTextAction()).assertDoesNotExist()
    }

    @Test fun typographyBrightnessAndMaskControlsUseCompactFullWidthRowsAndKeepTenths() {
        val fontSize = mutableStateOf(18f)
        val weight = mutableStateOf(400f)
        val brightness = mutableStateOf(-1f)
        val mask = mutableStateOf(0f)
        compose.setContent {
            ThemeSettingsSheet(
                visible = true,
                currentFontSize = fontSize.value,
                currentBodyFontWeight = weight.value,
                currentFontType = "kaiti",
                currentBrightness = brightness.value,
                currentDarkMaskOpacity = mask.value,
                onFontSizeChange = { fontSize.value = it },
                onFontSizePreview = { fontSize.value = it },
                onBodyFontWeightChange = { weight.value = it },
                onBodyFontWeightPreview = { weight.value = it },
                onBrightnessChange = { brightness.value = it },
                onDarkMaskOpacityChange = { mask.value = it },
                currentTheme = "day",
                eInkModeEnabled = true,
                onThemeChange = {}, onOpenAdvanced = {}, onDismiss = {}
            )
        }
        fun slider(range: ClosedFloatingPointRange<Float>) = SemanticsMatcher("slider $range") {
            it.config.getOrNull(SemanticsProperties.ProgressBarRangeInfo)?.range == range
        }
        val fontSlider = slider(12f..33f)
        val weightSlider = slider(100f..900f)
        val brightnessSlider = slider(0f..100f)
        val maskSlider = slider(0f..80f)
        compose.waitUntil(10_000) {
            listOf(fontSlider, weightSlider, brightnessSlider, maskSlider).all {
                compose.onAllNodes(it).fetchSemanticsNodes().size == 1
            }
        }
        val fontBounds = compose.onNode(fontSlider).fetchSemanticsNode().boundsInRoot
        val weightBounds = compose.onNode(weightSlider).fetchSemanticsNode().boundsInRoot
        val brightnessBounds = compose.onNode(brightnessSlider).fetchSemanticsNode().boundsInRoot
        val maskBounds = compose.onNode(maskSlider).fetchSemanticsNode().boundsInRoot
        compose.runOnIdle {
            val rows = listOf(fontBounds, weightBounds, brightnessBounds, maskBounds)
            rows.zipWithNext().forEach { (previous, next) ->
                assertTrue("Controls must occupy separate rows", next.top >= previous.bottom)
                assertTrue("Rows should stay compact", next.top - previous.top <= 70f * 3f)
                assertEquals(previous.left, next.left, 1f)
            }
            assertTrue("Font slider should use the available width", fontBounds.width >= 300f * 3f)
            assertEquals(fontBounds.width, weightBounds.width, 1f)
            assertEquals(fontBounds.width, maskBounds.width, 1f)
        }

        compose.onNode(fontSlider).performSemanticsAction(SemanticsActions.SetProgress) { it(32.3f) }
        compose.onNode(weightSlider).performSemanticsAction(SemanticsActions.SetProgress) { it(425.7f) }
        compose.onNode(maskSlider).performSemanticsAction(SemanticsActions.SetProgress) { it(73f) }
        compose.runOnIdle {
            assertEquals(32.3f, fontSize.value, 0f)
            assertEquals(425.7f, weight.value, 0f)
            assertEquals(0.73f, mask.value, 0.0001f)
        }

        compose.onNodeWithTag("reader-brightness-auto-toggle").performClick()
        compose.runOnIdle { assertEquals(0.8f, brightness.value, 0.0001f) }
    }
}
