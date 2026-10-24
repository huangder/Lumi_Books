package com.huangder.lumibooks.ui.reader

import android.app.Application
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import com.huangder.lumibooks.R
import com.huangder.lumibooks.domain.model.ReaderCornerContent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "en-rUS-w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AdvancedSettingsSheetTest {
    @get:Rule val compose = createComposeRule()

    @Test fun bookLayoutShowsSpacingAndCommitsChanges() = verifyBookSpacing()

    @Test
    @Config(qualifiers = "en-rUS-w891dp-h411dp-xxhdpi")
    fun compactScreenKeepsBookSpacingAccessible() = verifyBookSpacing()

    @Test fun marginPreviewOnlyUpdatesDuringConfirmedHorizontalDrags() {
        val margin = mutableFloatStateOf(38f)
        var previewCount = 0
        var commitCount = 0
        val context = RuntimeEnvironment.getApplication()
        compose.setContent {
            AdvancedSettingsSheet(
                visible = true, previewText = "Live margin preview text.",
                currentLineHeight = 1.5f, currentLetterSpacing = 0f,
                currentFontType = "system", currentBackgroundSelection = "day", customBackgrounds = emptyList(),
                currentMarginLeft = margin.floatValue, currentMarginRight = 38f,
                currentMarginTop = 64f, currentMarginBottom = 64f,
                currentBgColor = Color.White, currentBackgroundImagePath = null,
                currentTextColor = Color.Black, currentTextColorOverride = null, currentFontSizeSp = 16f,
                eInkModeEnabled = true,
                onLineHeightChange = {}, onLetterSpacingChange = {}, onFontTypeChange = {},
                onBackgroundSelect = {}, onAddBackgroundColor = { _, _ -> },
                onAddBackgroundImage = { _, _ -> }, onDeleteBackground = {},
                onMarginLeftChange = { margin.floatValue = it; commitCount++ },
                onMarginLeftPreview = { margin.floatValue = it; previewCount++ },
                onMarginRightChange = {}, onMarginTopChange = {}, onMarginBottomChange = {},
                readerTopLeftContent = ReaderCornerContent.NONE, readerTopRightContent = ReaderCornerContent.NONE,
                readerBottomLeftContent = ReaderCornerContent.NONE, readerBottomRightContent = ReaderCornerContent.NONE,
                onReaderCornerContentChange = { _, _ -> }, onTextColorChange = {}, onResetSettings = {}, onDismiss = {}
            )
        }
        compose.onNodeWithText(context.getString(R.string.reader_settings_page_layout))
            .performScrollTo().performClick()
        val marginSlider = compose.onAllNodes(SemanticsMatcher("horizontal margin slider") {
            it.config.getOrNull(SemanticsProperties.ProgressBarRangeInfo)?.range == 0f..160f
        })[0]
        val label = compose.onNodeWithText(context.getString(R.string.label_margin_left))
        marginSlider.performScrollTo()
        marginSlider.performTouchInput { down(center) }
        label.assertIsDisplayed()
        marginSlider.performTouchInput { moveBy(Offset(0f, -120f)); up() }
        compose.runOnIdle {
            assertEquals(0, previewCount)
            assertEquals(0, commitCount)
        }

        marginSlider.performScrollTo()
        marginSlider.performTouchInput { down(center); moveBy(Offset(120f, 0f)) }
        compose.runOnIdle { assertTrue(previewCount > 0) }
        marginSlider.performTouchInput { up() }
        label.assertIsDisplayed()
        compose.runOnIdle { assertEquals(1, commitCount) }
    }

    @Test fun typographyPreviewShowsOnlyTheActiveSliderAndCommitsOnRelease() {
        var previewCount = 0
        var commitCount = 0
        var latestPreview = 1.5f
        val context = RuntimeEnvironment.getApplication()
        compose.setContent {
            AdvancedSettingsSheet(
                visible = true, previewText = "Live typography preview text.",
                currentLineHeight = latestPreview, currentLetterSpacing = 0f,
                currentFontType = "system", currentBackgroundSelection = "day", customBackgrounds = emptyList(),
                currentMarginLeft = 38f, currentMarginRight = 38f,
                currentMarginTop = 64f, currentMarginBottom = 64f,
                currentBgColor = Color.White, currentBackgroundImagePath = null,
                currentTextColor = Color.Black, currentTextColorOverride = null, currentFontSizeSp = 16f,
                preservePublisherLayout = true, eInkModeEnabled = true,
                onLineHeightChange = { latestPreview = it; commitCount++ },
                onLineHeightPreview = { latestPreview = it; previewCount++ },
                onLetterSpacingChange = {}, onFontTypeChange = {},
                onBackgroundSelect = {}, onAddBackgroundColor = { _, _ -> },
                onAddBackgroundImage = { _, _ -> }, onDeleteBackground = {},
                onMarginLeftChange = {}, onMarginRightChange = {}, onMarginTopChange = {}, onMarginBottomChange = {},
                readerTopLeftContent = ReaderCornerContent.NONE, readerTopRightContent = ReaderCornerContent.NONE,
                readerBottomLeftContent = ReaderCornerContent.NONE, readerBottomRightContent = ReaderCornerContent.NONE,
                onReaderCornerContentChange = { _, _ -> }, onTextColorChange = {}, onResetSettings = {}, onDismiss = {}
            )
        }
        compose.onNodeWithText(context.getString(R.string.reader_settings_text_spacing))
            .performScrollTo().performClick()
        val lineSliderMatcher = SemanticsMatcher("line height slider") {
            it.config.getOrNull(SemanticsProperties.ProgressBarRangeInfo)?.range == 1f..2.5f
        }
        val lineSlider = compose.onNode(lineSliderMatcher).performScrollTo()
        lineSlider.performTouchInput { down(center); moveBy(Offset(120f, 0f)) }
        compose.runOnIdle {
            assertTrue("Dragging should update the reader before release", previewCount > 0)
            assertEquals(0, commitCount)
        }
        assertEquals(2, compose.onAllNodes(lineSliderMatcher).fetchSemanticsNodes().size)
        compose.onAllNodes(lineSliderMatcher)[0].performTouchInput { up() }
        compose.runOnIdle {
            assertEquals(1, commitCount)
        }
        assertEquals(1, compose.onAllNodes(lineSliderMatcher).fetchSemanticsNodes().size)
    }

    private fun verifyBookSpacing() {
        val lineHeight = mutableFloatStateOf(1.5f)
        val context = RuntimeEnvironment.getApplication()
        compose.setContent {
            AdvancedSettingsSheet(
                visible = true, previewText = "A preview paragraph with enough words to fill several lines.",
                currentLineHeight = lineHeight.floatValue, currentLetterSpacing = 0f,
                currentFontType = "system", currentBackgroundSelection = "day", customBackgrounds = emptyList(),
                currentMarginLeft = 38f, currentMarginRight = 38f,
                currentMarginTop = 64f, currentMarginBottom = 64f,
                currentBgColor = Color.White, currentBackgroundImagePath = null,
                currentTextColor = Color.Black, currentTextColorOverride = null, currentFontSizeSp = 16f,
                preservePublisherLayout = true, publisherSuiteActive = true, eInkModeEnabled = true,
                onLineHeightChange = { lineHeight.floatValue = it }, onLetterSpacingChange = {},
                onFontTypeChange = {}, onBackgroundSelect = {}, onAddBackgroundColor = { _, _ -> },
                onAddBackgroundImage = { _, _ -> }, onDeleteBackground = {},
                onMarginLeftChange = {}, onMarginRightChange = {}, onMarginTopChange = {}, onMarginBottomChange = {},
                readerTopLeftContent = ReaderCornerContent.NONE, readerTopRightContent = ReaderCornerContent.NONE,
                readerBottomLeftContent = ReaderCornerContent.NONE, readerBottomRightContent = ReaderCornerContent.NONE,
                onReaderCornerContentChange = { _, _ -> }, onTextColorChange = {}, onResetSettings = {}, onDismiss = {}
            )
        }
        val editingLabel = compose.onNodeWithText(context.getString(R.string.reader_layout_editing_book))
        editingLabel.assertIsDisplayed()
        val labelTopDp = editingLabel.fetchSemanticsNode().boundsInRoot.top / context.resources.displayMetrics.density
        // The compact preview leaves the settings visible, even on a short screen.
        assertTrue("Settings start too low: $labelTopDp", labelTopDp < 400f)
        compose.onNodeWithText(context.getString(R.string.reader_settings_text_spacing)).performScrollTo().performClick()
        for (label in listOf(R.string.label_line_height, R.string.label_letter_spacing,
            R.string.label_paragraph_spacing, R.string.label_first_line_indent)) {
            compose.onNodeWithText(context.getString(label)).performScrollTo().assertIsDisplayed()
        }
        val lineSlider = SemanticsMatcher("line height slider") {
            it.config.getOrNull(SemanticsProperties.ProgressBarRangeInfo)?.range == 1f..2.5f
        }
        compose.onNode(lineSlider).performScrollTo()
            .performSemanticsAction(SemanticsActions.SetProgress) { it(1.8f) }
        compose.runOnIdle { assertEquals(1.8f, lineHeight.floatValue, 0.01f) }
    }
}
