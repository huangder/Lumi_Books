package com.huangder.lumibooks.ui.settings

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.huangder.lumibooks.R
import com.huangder.lumibooks.domain.model.ReaderThemeSettings
import com.huangder.lumibooks.domain.model.ReaderThemeSuites
import com.huangder.lumibooks.ui.theme.EBookReaderTheme
import com.huangder.lumibooks.ui.theme.MotionPreference
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "zh-rCN-w360dp-h800dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ReaderSettingsPanelsLayoutTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var view: View
    private val context get() = RuntimeEnvironment.getApplication()

    private fun setPanel(content: @androidx.compose.runtime.Composable () -> Unit) {
        compose.setContent {
            EBookReaderTheme(appTheme = "lumi", motionPreference = MotionPreference.REDUCED) {
                view = LocalView.current
                Box(Modifier.width(360.dp).height(800.dp).background(Color(0xFFFBFBFC))) { content() }
            }
        }
    }

    @Test fun textPanelGroupsControlsIntoTitledSections() {
        setPanel {
            TextPanel(
                ReaderThemeSettings(), emptyList(), emptyList(), false, false,
                onThemeModeChange = {}, onPreviewUpdate = {}, onUpdate = {}
            )
        }
        listOf(
            R.string.reader_panel_section_text,
            R.string.reader_panel_section_spacing,
            R.string.reader_panel_section_margins,
            R.string.reader_annotation_geometry_title
        ).forEach { compose.onNodeWithText(context.getString(it)).performScrollTo().assertIsDisplayed() }
        // Sections are headings so TalkBack can jump between them.
        assertEquals(4, compose.onAllNodes(isHeading()).fetchSemanticsNodes().size)
        listOf(
            R.string.label_highlight_width, R.string.label_highlight_height, R.string.label_highlight_offset_y,
            R.string.label_underline_thickness, R.string.label_underline_offset_y
        ).forEach { compose.onNodeWithText(context.getString(it)).assertExists() }
        // Paired sliders share one row: font size sits beside font weight.
        val size = compose.onNodeWithText(context.getString(R.string.label_font_size)).fetchSemanticsNode().boundsInRoot
        val weight = compose.onNodeWithText(context.getString(R.string.body_font_weight)).fetchSemanticsNode().boundsInRoot
        assertEquals(size.center.y, weight.center.y, 1f)
        saveScreenshot("text-panel.png")
    }

    @Test fun backgroundPanelSplitsColorAndImageSections() {
        setPanel {
            BackgroundPanel(
                ReaderThemeSettings(), emptyList(), true, true, false,
                onThemeModeChange = {}, onPreviewUpdate = {}, onUpdate = {}, onSelectColor = {},
                onAddColor = {}, onAddPhoto = {}, onRemovePhoto = {}
            )
        }
        compose.onNodeWithText(context.getString(R.string.reader_panel_section_background_color)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.reader_panel_section_background_image)).assertIsDisplayed()
        val opacity = compose.onNodeWithText(context.getString(R.string.background_photo_opacity))
            .fetchSemanticsNode().boundsInRoot
        val blur = compose.onNodeWithText(context.getString(R.string.background_photo_blur))
            .fetchSemanticsNode().boundsInRoot
        assertEquals(opacity.center.y, blur.center.y, 1f)
        // The light/dark switch shares the header row with the panel title.
        val title = compose.onNodeWithText(context.getString(R.string.reader_background_settings))
            .fetchSemanticsNode().boundsInRoot
        val dark = compose.onNodeWithText(context.getString(R.string.reader_theme_mode_dark))
            .fetchSemanticsNode().boundsInRoot
        assertEquals(title.center.y, dark.center.y, 2f)
        saveScreenshot("background-panel.png")
    }

    @Test fun animationPanelOffersModesAsOneSegmentedControl() {
        val selected = mutableStateOf("slide")
        val eInk = mutableStateOf(false)
        setPanel {
            AnimationSettingsPanel(
                mode = selected.value,
                eInkMode = eInk.value,
                durationMs = 300,
                onModeChange = { selected.value = it },
                onDurationPreview = {},
                onDurationChange = {}
            )
        }
        compose.onNodeWithText(context.getString(R.string.reader_page_animation_mode)).assertIsDisplayed()
        compose.onNodeWithText("300 ms").assertIsDisplayed()
        listOf(
            R.string.page_animation_slide_short, R.string.page_animation_scroll_short,
            R.string.page_animation_fade_short, R.string.page_animation_curl_short,
            R.string.page_animation_wave_short
        ).forEach { compose.onNodeWithText(context.getString(it)).assertIsDisplayed() }
        compose.onNodeWithText(context.getString(R.string.page_animation_curl_short)).performClick()
        compose.runOnIdle { assertEquals("curl", selected.value) }
        saveScreenshot("animation-panel.png")

        compose.runOnIdle { eInk.value = true; selected.value = "fade" }
        compose.onNodeWithText(context.getString(R.string.page_animation_slide_short)).assertDoesNotExist()
        compose.onNodeWithText(context.getString(R.string.page_animation_curl_short)).assertDoesNotExist()
        compose.onNodeWithText(context.getString(R.string.page_animation_fade_short)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.page_animation_wave_short)).assertIsDisplayed()
    }

    @Test fun suiteRowsShowActiveCheckOrInlineSetCurrent() {
        var activated = 0
        val suite = ReaderThemeSuites.defaults().first { it.id == ReaderThemeSuites.DAY_ID }
        val active = mutableStateOf(false)
        setPanel {
            ThemeSuiteRow(
                suite = suite, backgrounds = emptyList(), active = active.value,
                onEdit = {}, onActivate = { activated++ }, onRename = {}, onDelete = {}, onMove = {}
            )
        }
        // The row merges its children for TalkBack, so look at the unmerged tree.
        compose.onNodeWithTag("theme_suite_active_check", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag("theme_suite_set_current").performClick()
        compose.runOnIdle { assertEquals(1, activated); active.value = true }
        compose.onNodeWithTag("theme_suite_active_check", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("theme_suite_set_current", useUnmergedTree = true).assertDoesNotExist()
    }

    private fun saveScreenshot(name: String) {
        compose.runOnIdle {
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            try {
                view.draw(Canvas(bitmap))
                val directory = File("build/reader-settings-panels").apply { mkdirs() }
                File(directory, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            } finally {
                bitmap.recycle()
            }
        }
    }
}
