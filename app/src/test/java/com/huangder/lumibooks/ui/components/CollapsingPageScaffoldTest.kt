package com.huangder.lumibooks.ui.components

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.huangder.lumibooks.ui.theme.EBookReaderTheme
import com.huangder.lumibooks.ui.theme.AppColors
import com.huangder.lumibooks.ui.theme.MotionPreference
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "en-w360dp-h740dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CollapsingPageScaffoldTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var state: CollapsingPageState
    private lateinit var listState: LazyListState
    private lateinit var view: View

    private fun page(grid: Boolean = false, scrollEnabled: Boolean = true, fontScale: Float = 1f,
        dark: Boolean = false, eInk: Boolean = false, reduceTransparency: Boolean = false) {
        compose.setContent {
            EBookReaderTheme(appTheme = "lumi", darkTheme = dark, eInkMode = eInk,
                reduceTransparencyEnabled = reduceTransparency, motionPreference = MotionPreference.REDUCED) {
                CompositionLocalProvider(LocalDensity provides Density(1f, fontScale)) {
                    view = LocalView.current
                    state = rememberCollapsingPageState()
                    listState = rememberLazyListState()
                    CollapsingPageScaffold("A long secondary page title", {}, state = state,
                        contentScrollEnabled = scrollEnabled, drawContentBehindHeader = true,
                        pinnedContent = { Text("Filter", Modifier.fillMaxWidth().height(48.dp), color = AppColors.TextPrimary) },
                        bottomBar = { Text("Save", Modifier.fillMaxWidth().height(48.dp), color = AppColors.TextPrimary) }) {
                        val topInset = LocalCollapsingPageTopInset.current
                        if (grid) LazyVerticalGrid(GridCells.Fixed(2), Modifier.fillMaxSize().testTag("rows"),
                            contentPadding = PaddingValues(top = topInset)) {
                            items(100) { Text("Row $it", Modifier.height(64.dp), color = AppColors.TextPrimary) }
                        } else LazyColumn(Modifier.fillMaxSize().testTag("rows"), state = listState,
                            contentPadding = PaddingValues(top = topInset)) {
                            items(100) { Text("Row $it", Modifier.height(64.dp), color = AppColors.TextPrimary) }
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    @Test fun listCollapsesAndReturnsToExpandedTitleWhileActionsStayVisible() {
        page()
        compose.runOnIdle { assertTrue(state.titleHeightPx > 0f); assertEquals(0f, state.progress, 0.001f) }
        compose.onNodeWithTag("rows").performTouchInput { swipeUp() }
        compose.runOnIdle { assertEquals(1f, state.progress, 0.001f) }
        compose.onNodeWithText("Filter").assertIsDisplayed()
        compose.onNodeWithText("Save").assertIsDisplayed()
        // Start below the fixed toolbar and filters so the list receives each drag.
        repeat(6) {
            compose.onNodeWithTag("rows").performTouchInput {
                swipeDown(startY = 220f, endY = 650f, durationMillis = 500)
            }
        }
        compose.runOnIdle { assertEquals(0f, state.progress, 0.001f) }
    }

    @Test fun gridAndLargeFontKeepRowsBelowTitleAndPinnedControls() {
        page(grid = true, fontScale = 1.5f)
        val filterBottom = compose.onNodeWithText("Filter").getUnclippedBoundsInRoot().bottom
        assertTrue(compose.onNodeWithText("Row 0").getUnclippedBoundsInRoot().top >= filterBottom)
        compose.onNodeWithTag("rows").performTouchInput { swipeUp() }
        compose.runOnIdle { assertEquals(1f, state.progress, 0.001f) }
        compose.onNodeWithText("Filter").assertIsDisplayed()
    }

    @Test fun previewCanDisableContentGesturesAndStillDragTitle() {
        page(scrollEnabled = false)
        compose.onNodeWithTag("rows").performTouchInput { swipeUp() }
        compose.runOnIdle { assertEquals(0f, state.progress, 0.001f) }
        compose.onRoot().performTouchInput { swipeUp(startY = 110f, endY = 10f) }
        compose.runOnIdle { assertTrue(state.progress > 0f) }
    }

    @Test fun programmaticScrollDoesNotDriveTheTitleDuringReportPrinting() {
        page()
        compose.onNodeWithTag("rows").performScrollToIndex(50)
        compose.runOnIdle { assertEquals(0f, state.progress, 0.001f) }
    }

    @Test fun toolbarActionsHaveSeparateTouchTargetsWithVisibleSpace() {
        compose.setContent {
            EBookReaderTheme(appTheme = "lumi", motionPreference = MotionPreference.REDUCED) {
                CollapsingPageScaffold("Notes", {}, actions = {
                    Box(Modifier.size(48.dp).testTag("search"))
                    Box(Modifier.size(48.dp).testTag("manage"))
                }) { Text("Content") }
            }
        }
        val search = compose.onNodeWithTag("search").getUnclippedBoundsInRoot()
        val manage = compose.onNodeWithTag("manage").getUnclippedBoundsInRoot()
        assertTrue("Toolbar controls need an 8dp gap", manage.left - search.right >= 8.dp)
    }

    @Test fun lightTitleTransitionsKeepFixedControlsClear() { verifyTheme("light") }
    @Test fun darkTitleTransitionsKeepFixedControlsClear() { verifyTheme("dark", dark = true) }
    @Test fun reducedTransparencyUsesOpaqueHeader() { verifyTheme("opaque", reduceTransparency = true) }
    @Test fun eInkUsesOpaqueHeader() { verifyTheme("eink", eInk = true) }

    private fun verifyTheme(name: String, dark: Boolean = false, eInk: Boolean = false, reduceTransparency: Boolean = false) {
        page(dark = dark, eInk = eInk, reduceTransparency = reduceTransparency)
        capture("$name-expanded")
        compose.runOnIdle { state.consumeDelta(-state.titleHeightPx * 0.5f) }
        compose.waitForIdle()
        capture("$name-half")
        compose.onNodeWithTag("rows").performTouchInput { swipeUp() }
        compose.onNodeWithText("Filter").assertIsDisplayed()
        compose.onNodeWithText("Save").assertIsDisplayed()
        compose.runOnIdle { assertEquals(1f, state.progress, 0.001f) }
        capture("$name-collapsed")
    }

    private fun capture(name: String) {
        compose.runOnIdle {
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            File("build/page-title-previews/$name.png").also { it.parentFile.mkdirs() }
                .outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }
}
