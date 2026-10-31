package com.huangder.lumibooks.ui.reader

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.huangder.lumibooks.R
import com.huangder.lumibooks.ui.theme.AppColors
import com.huangder.lumibooks.ui.theme.EBookReaderTheme
import com.huangder.lumibooks.ui.theme.LocalMotionEnabled
import java.io.File
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "en-rUS-w320dp-h640dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ReaderTextColorSettingTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var view: View

    private fun label(id: Int) = RuntimeEnvironment.getApplication().getString(id)

    private fun mount(
        selected: MutableState<Int?>,
        effective: Color = Color(0xFF123456),
        appTheme: String = "lumi",
        darkTheme: Boolean = false
    ) {
        compose.setContent {
            EBookReaderTheme(appTheme = appTheme, darkTheme = darkTheme) {
                CompositionLocalProvider(LocalMotionEnabled provides false) {
                    view = LocalView.current
                    Column(Modifier.fillMaxWidth().background(AppColors.CardBg)) {
                        TextColorSetting(
                            currentOverride = selected.value,
                            effectiveTextColor = effective,
                            onColorChange = { selected.value = it }
                        )
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun autoAndEveryPresetSwatchUpdateSelection() {
        val selected = mutableStateOf<Int?>(null)
        mount(selected)

        val auto = compose.onNodeWithContentDescription(label(R.string.text_color_auto))
        auto.assertIsSelected()
        val presetsDescription = label(R.string.label_text_color)
        compose.onAllNodesWithContentDescription(presetsDescription).assertCountEquals(4)
        val presets = listOf(0xFF202124, 0xFF55565A, 0xFF4A3728, 0xFFF4F4F5)
        presets.forEachIndexed { index, color ->
            compose.onAllNodesWithContentDescription(presetsDescription)[index].performClick()
            compose.runOnIdle { assertEquals(color.toInt(), selected.value) }
            compose.onAllNodesWithContentDescription(presetsDescription)[index].assertIsSelected()
        }
        auto.assertIsNotSelected()

        auto.performClick()
        compose.runOnIdle { assertNull(selected.value) }
        auto.assertIsSelected()
    }

    @Test
    fun customDialogCancelApplyAndReopenPreserveColor() {
        val selected = mutableStateOf<Int?>(null)
        val effective = Color(0xFF336699)
        mount(selected, effective)
        val customDescription = label(R.string.text_color_custom)
        val custom = compose.onNodeWithContentDescription(customDescription)

        custom.performClick()
        compose.onNodeWithText(label(R.string.cancel)).assertIsDisplayed().performClick()
        compose.waitForIdle()
        compose.runOnIdle { assertNull(selected.value) }

        custom.performClick()
        compose.onNodeWithText(label(R.string.apply_text_color)).assertIsDisplayed().performClick()
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(effective.toArgb(), selected.value) }
        custom.assertIsSelected()

        val hsv = FloatArray(3).also { android.graphics.Color.colorToHSV(effective.toArgb(), it) }
        custom.performClick()
        val hue = compose.onNode(SemanticsMatcher("hue slider") {
            it.config.getOrNull(SemanticsProperties.ProgressBarRangeInfo)?.range == 0f..360f
        })
        hue.assert(SemanticsMatcher("reopened custom hue") {
            val current = it.config[SemanticsProperties.ProgressBarRangeInfo].current
            abs(current - hsv[0]) < 0.5f
        })
        compose.onNodeWithText(label(R.string.cancel)).performClick()
    }

    @Test
    @Config(qualifiers = "en-rUS-w240dp-h640dp-mdpi")
    fun compactWidthScrollsToCustomSwatchAndCapturesBothStates() {
        val selected = mutableStateOf<Int?>(null)
        mount(selected, appTheme = "lumi", darkTheme = true)
        val customDescription = label(R.string.text_color_custom)
        capture("dark-auto-presets")
        val custom = compose.onNodeWithContentDescription(customDescription)
        val scroll = compose.onNode(SemanticsMatcher("color swatch horizontal scroll") {
            it.config.getOrNull(SemanticsProperties.HorizontalScrollAxisRange) != null
        })
        scroll.performSemanticsAction(SemanticsActions.ScrollBy) { action -> action(500f, 0f) }
        compose.waitForIdle()
        custom.assertIsDisplayed()
        capture("dark-custom-revealed")
    }

    @Test
    @Config(qualifiers = "en-rUS-w320dp-h640dp-mdpi")
    fun liquidGlassCompactThemeKeepsColorControlsAccessible() {
        val selected = mutableStateOf<Int?>(null)
        mount(selected, appTheme = "liquid_glass", darkTheme = true)
        compose.onNodeWithContentDescription(label(R.string.text_color_auto)).assertIsSelected()
        compose.onAllNodesWithContentDescription(label(R.string.label_text_color)).assertCountEquals(4)
        compose.onNodeWithContentDescription(label(R.string.text_color_custom)).assertIsDisplayed()
        capture("glass-dark-presets")
    }

    private fun capture(name: String) {
        compose.runOnIdle {
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            val directory = File("build/reports/reader-colors").apply { mkdirs() }
            File(directory, "$name.png").outputStream().use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            bitmap.recycle()
        }
    }
}
