package com.huangder.lumibooks.ui.home

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.huangder.lumibooks.R
import com.huangder.lumibooks.ui.home.remote.RemoteBrowserHeader
import com.huangder.lumibooks.ui.home.remote.RemoteSortMode
import com.huangder.lumibooks.ui.theme.EBookReaderTheme
import com.huangder.lumibooks.ui.theme.MotionPreference
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "en-w360dp-h740dp-mdpi")
class BrowserHeaderInteractionTest {
    @get:Rule val compose = createComposeRule()
    private val DpRect.width get() = right - left

    @Test fun remoteSearchAnimatesBothDirectionsAndKeepsClosingText() = verifySearch(remote = true)

    @Test fun folderSearchAnimatesBothDirectionsAndKeepsClosingText() = verifySearch(remote = false)

    private fun verifySearch(remote: Boolean) {
        val query = mutableStateOf<String?>(null)
        compose.setContent {
            EBookReaderTheme(appTheme = "lumi", motionPreference = MotionPreference.STANDARD) {
                CompositionLocalProvider(LocalDensity provides Density(1f, 1.5f)) {
                    Box(Modifier.width(360.dp)) {
                        if (remote) RemoteBrowserHeader(
                            path = "Library", summary = "3 books", searchAvailable = true,
                            searchQuery = query.value, loading = false, showSelectAll = true,
                            selectionMode = true, layoutMode = 1, sortMode = RemoteSortMode.DEFAULT,
                            sortAscending = true, onSearchOpen = { query.value = "needle" },
                            onSearchQueryChange = { query.value = it }, onSearchSubmit = {},
                            onSearchClose = { query.value = null }, onRefresh = {}, onSelectAll = {},
                            onExitSelection = {}, onSortModeChange = {}, onSortAscendingChange = {},
                            onLayoutModeChange = {}
                        ) else FolderBooksHeader(
                            path = "Library", levelSummary = "3 books", isScanning = false,
                            isSearching = query.value != null, searchQuery = query.value.orEmpty(),
                            showSelectAll = true, layoutMode = 1, sortMode = FolderBooksSortMode.NAME,
                            sortAscending = true, onRefresh = {}, onSearchOpen = { query.value = "needle" },
                            onSearchQueryChange = { query.value = it }, onSearchClose = { query.value = null },
                            onSelectAll = {}, onSortModeChange = {}, onSortAscendingChange = {},
                            onLayoutModeChange = {}
                        )
                    }
                }
            }
        }
        val context = ApplicationProvider.getApplicationContext<Application>()
        val sort = compose.onNodeWithContentDescription(context.getString(R.string.import_sort))
            .getUnclippedBoundsInRoot()
        val layout = compose.onNodeWithContentDescription(context.getString(R.string.import_layout_mode))
            .getUnclippedBoundsInRoot()
        assertTrue("Controls need a visible horizontal gap or a wrapped row gap",
            layout.left.value - sort.right.value >= 8f || layout.top.value - sort.bottom.value >= 8f)
        assertTrue("Controls must fit the narrow header", layout.right.value <= 360f)
        val pill = compose.onNodeWithTag(if (remote) "remote-search-pill" else "folder-search-pill")
        val collapsed = pill.getUnclippedBoundsInRoot().width.value
        compose.mainClock.autoAdvance = false
        pill.performClick()
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeBy(64)
        val opening = pill.getUnclippedBoundsInRoot().width.value
        assertTrue("Search must grow across intermediate frames", opening > collapsed + 1f)
        compose.mainClock.advanceTimeBy(1600)
        val expanded = pill.getUnclippedBoundsInRoot().width.value
        assertTrue("The field must expand beyond its intermediate width", expanded > opening + 1f)
        compose.runOnIdle { query.value = null }
        // A state write first needs a recomposition frame before animateFloatAsState receives
        // its new target. Sample actual layout frames rather than assuming a fixed 64 ms starts
        // at the animation's first frame (BoxWithConstraints also schedules subcomposition).
        val closingSamples = mutableListOf<Float>()
        var foundClosingFrame = false
        repeat(12) {
            if (!foundClosingFrame) {
                compose.mainClock.advanceTimeByFrame()
                val width = pill.getUnclippedBoundsInRoot().width.value
                closingSamples += width
                foundClosingFrame = width > collapsed + 1f && width < expanded - 1f
            }
        }
        assertTrue("Search must have an intermediate closing width between $collapsed and $expanded; frames=$closingSamples",
            foundClosingFrame)
        compose.onNodeWithText("needle").assertExists()
        compose.mainClock.advanceTimeBy(1600)
        pill.assertWidthIsEqualTo(44.dp)
        compose.onNodeWithText("needle").assertDoesNotExist()
        compose.mainClock.autoAdvance = true
    }
}
