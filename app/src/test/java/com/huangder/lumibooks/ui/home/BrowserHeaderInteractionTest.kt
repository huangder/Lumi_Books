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

    @Test fun remotePickerKeepsToolsBesideSearchAndSupportsClearAndSubmit() = verifySearch(remote = true)

    @Test fun folderPickerKeepsToolsBesideSearchAndSupportsClear() = verifySearch(remote = false)

    private fun verifySearch(remote: Boolean) {
        val query = mutableStateOf<String?>(null)
        var submissions = 0
        compose.setContent {
            EBookReaderTheme(appTheme = "lumi", motionPreference = MotionPreference.STANDARD) {
                CompositionLocalProvider(LocalDensity provides Density(1f, 1.5f)) {
                    Box(Modifier.width(360.dp)) {
                        if (remote) RemoteBrowserHeader(
                            path = "Library", summary = "3 books", searchAvailable = true,
                            searchQuery = query.value, loading = false,
                            layoutMode = 1, sortMode = RemoteSortMode.DEFAULT,
                            sortAscending = true, onSearchOpen = { query.value = "needle" },
                            onSearchQueryChange = { query.value = it }, onSearchSubmit = { submissions++ },
                            onSearchClose = { query.value = null }, onRefresh = {},
                            onSortModeChange = {}, onSortAscendingChange = {},
                            onLayoutModeChange = {}
                        ) else FolderBooksHeader(
                            path = "Library", levelSummary = "3 books", isScanning = false,
                            isSearching = query.value != null, searchQuery = query.value.orEmpty(),
                            layoutMode = 1, sortMode = FolderBooksSortMode.NAME,
                            sortAscending = true, onRefresh = {}, onSearchOpen = { query.value = "needle" },
                            onSearchQueryChange = { query.value = it }, onSearchClose = { query.value = null },
                            onSortModeChange = {}, onSortAscendingChange = {},
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
        val refresh = compose.onNodeWithContentDescription(context.getString(R.string.import_folder_books_refresh))
            .getUnclippedBoundsInRoot()
        assertTrue("Tools must stay in one row", layout.top == sort.top && refresh.top == sort.top)
        assertTrue("Tools must have a gap", layout.left > sort.right && refresh.left > layout.right)
        assertTrue("Tools must fit the narrow header", refresh.right.value <= 360f)
        val pill = compose.onNodeWithTag(if (remote) "remote-search-pill" else "folder-search-pill")
        val initialWidth = pill.getUnclippedBoundsInRoot().width
        assertTrue("Search fills the remaining toolbar width", initialWidth > 100.dp)
        pill.performClick()
        compose.onNode(hasSetTextAction()).assertTextEquals("needle")
        pill.assertWidthIsEqualTo(initialWidth)
        compose.onNodeWithContentDescription(context.getString(R.string.import_search_clear)).performClick()
        compose.onNode(hasSetTextAction()).assert(hasText("")).performTextInput("new query")
        if (remote) {
            compose.onNode(hasSetTextAction()).performImeAction()
            compose.runOnIdle { org.junit.Assert.assertEquals(1, submissions) }
        }
        compose.runOnIdle { query.value = null }
        pill.assertWidthIsEqualTo(initialWidth)
        compose.onNodeWithText("new query").assertDoesNotExist()
    }
}
