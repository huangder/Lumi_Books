package com.huangder.lumibooks.ui.reader

import android.app.Application
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.huangder.lumibooks.R
import com.huangder.lumibooks.domain.model.AppFontSize
import com.huangder.lumibooks.ui.components.LiquidGlassMenuHost
import com.huangder.lumibooks.ui.components.LiquidGlassMenuHostState
import com.huangder.lumibooks.ui.components.LocalLiquidGlassMenuHost
import com.huangder.lumibooks.ui.theme.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "zh-rCN-w600dp-h850dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RasterReaderActionsTest {
    @get:Rule val compose = createComposeRule()
    private var size by mutableStateOf(AppFontSize.STANDARD)
    private var systemScale by mutableStateOf(1f)
    private var width by mutableStateOf(360.dp)
    private var comic by mutableStateOf(false)
    private var theme by mutableStateOf("lumi")
    private var dark by mutableStateOf(false)
    private var annotations = 0
    private var bookmarks = 0
    private var adjustments = 0
    private var conversions = 0
    private lateinit var host: LiquidGlassMenuHostState
    private fun label(id: Int) = RuntimeEnvironment.getApplication().getString(id)

    private fun mount() {
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, systemScale)) {
                EBookReaderTheme(appFontSize = size, appTheme = theme, darkTheme = dark,
                    motionPreference = MotionPreference.REDUCED) {
                    CompositionLocalProvider(LocalAppTheme provides theme) {
                        LiquidGlassMenuHost(Modifier.fillMaxSize()) {
                            host = LocalLiquidGlassMenuHost.current!!
                            Box(Modifier.fillMaxSize().padding(top = 300.dp)) {
                                RasterReaderActionRow(comic, { annotations++ }, { bookmarks++ },
                                    { adjustments++ }, { conversions++ }, Modifier.width(width))
                            }
                        }
                    }
                }
            }
        }
    }

    private fun assertComplete(id: Int) {
        val text = label(id)
        val results = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(text, useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
        val result = results.single()
        assertEquals("$text must stay on one line", 1, result.lineCount)
        assertFalse("$text must remain complete: size=$size scale=$systemScale width=$width layout=${result.size} right=${result.getLineRight(0)}", result.isLineEllipsized(0))
        assertTrue("$text must fit horizontally", result.getLineLeft(0) >= -.5f &&
            result.getLineRight(0) <= result.size.width + .5f)
        assertTrue("$text must fit vertically", result.getLineBottom(0) <= result.size.height + .5f)
        compose.onNodeWithText(text).assertIsDisplayed()
    }

    private fun openMore() {
        compose.onNodeWithContentDescription(label(R.string.more_options)).performClick()
        compose.waitForIdle()
    }

    @Test fun narrowRowsKeepShortActionsCompleteAtEverySizeAndSystemScale() {
        mount()
        for (scale in listOf(1f, 2f)) for (level in AppFontSize.entries) {
            compose.runOnIdle { size = level; systemScale = scale; width = 272.dp }
            assertComplete(R.string.pdf_annotation_tool)
            assertComplete(R.string.reader_bookmark)
            val a = compose.onNodeWithText(label(R.string.pdf_annotation_tool)).fetchSemanticsNode().boundsInRoot
            val b = compose.onNodeWithText(label(R.string.reader_bookmark)).fetchSemanticsNode().boundsInRoot
            assertFalse("Actions must not overlap", a.overlaps(b))
            compose.onNodeWithText(label(R.string.pdf_annotation_tool)).performClick()
            compose.onNodeWithText(label(R.string.reader_bookmark)).performClick()
            // The adjustment entry remains available whether it fits inline or moves into More.
            val inline = compose.onAllNodesWithText(label(R.string.reader_image_adjustment_entry))
                .fetchSemanticsNodes().isNotEmpty()
            if (!inline) openMore()
            assertComplete(R.string.reader_image_adjustment_entry)
            compose.onNodeWithText(label(R.string.reader_image_adjustment_entry)).performClick()
            compose.waitForIdle()
        }
        compose.runOnIdle { assertEquals(12, annotations); assertEquals(12, bookmarks); assertEquals(12, adjustments) }
    }

    @Test fun adjustMovesToMoreOnlyWhenNeededAndPdfConversionRemainsAvailable() {
        mount()
        assertComplete(R.string.reader_image_adjustment_entry)
        compose.runOnIdle { size = AppFontSize.EXTRA_LARGE; systemScale = 2f }
        compose.onNodeWithText(label(R.string.reader_image_adjustment_entry)).assertDoesNotExist()
        openMore()
        assertComplete(R.string.reader_image_adjustment_entry)
        compose.runOnIdle { assertEquals(2, host.activeMenu!!.items.size) }
        compose.onNodeWithText(label(R.string.reader_image_adjustment_entry)).performClick()
        compose.waitForIdle()
        openMore()
        compose.onNodeWithText(label(R.string.pdf_convert_action)).performClick()
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(1, adjustments); assertEquals(1, conversions); width = 552.dp }
        assertComplete(R.string.reader_image_adjustment_entry)
    }

    @Test fun comicOverflowKeepsAdjustmentAvailableAcrossThemes() {
        mount()
        for (appearance in listOf("lumi", "material3", "liquid_glass")) for (isDark in listOf(false, true)) {
            compose.runOnIdle {
                theme = appearance; dark = isDark; comic = true
                size = AppFontSize.EXTRA_LARGE; systemScale = 2f; width = 272.dp
            }
            assertComplete(R.string.pdf_annotation_tool)
            assertComplete(R.string.reader_bookmark)
            openMore()
            compose.runOnIdle { assertEquals(1, host.activeMenu!!.items.size) }
            assertComplete(R.string.reader_image_adjustment_entry)
            compose.onNodeWithText(label(R.string.reader_image_adjustment_entry)).performClick()
            compose.waitForIdle()
        }
        compose.runOnIdle { assertEquals(6, adjustments); assertEquals(0, conversions); systemScale = 1f; size = AppFontSize.STANDARD }
        assertComplete(R.string.reader_image_adjustment_entry)
        compose.onNodeWithContentDescription(label(R.string.more_options)).assertDoesNotExist()
    }
}
