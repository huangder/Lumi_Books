package com.huangder.lumibooks.ui.home

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.huangder.lumibooks.R
import com.huangder.lumibooks.data.remote.RemoteBookItem
import com.huangder.lumibooks.data.remote.RemoteFolder
import com.huangder.lumibooks.data.remote.RemoteFormat
import com.huangder.lumibooks.data.remote.RemotePage
import com.huangder.lumibooks.ui.home.remote.RemoteBrowserScreen
import com.huangder.lumibooks.ui.home.remote.RemoteLevel
import com.huangder.lumibooks.ui.home.remote.RemoteSortMode
import com.huangder.lumibooks.ui.theme.EBookReaderTheme
import com.huangder.lumibooks.ui.theme.MotionPreference
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "en-w393dp-h852dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RemoteBookPickerTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = ApplicationProvider.getApplicationContext<Application>()
    private val first = RemoteBookItem("source", "first", "First book",
        formats = listOf(RemoteFormat("https://example.com/first.epub", "epub")))
    private val second = first.copy(key = "second", title = "Second book")
    private var selection by mutableStateOf<Set<String>>(emptySet())
    private var query by mutableStateOf<String?>(null)
    private var openedBook: RemoteBookItem? = null
    private var openedFolders = 0
    private var downloads = 0
    private lateinit var view: View

    private fun page(layout: Int = 1, books: List<RemoteBookItem> = listOf(first, second)) {
        compose.setContent {
            view = LocalView.current
            EBookReaderTheme(appTheme = "lumi", motionPreference = MotionPreference.REDUCED) {
                RemoteBrowserScreen(
                    levels = listOf(RemoteLevel("https://example.com", "Remote library", loading = false,
                        page = RemotePage("Remote library", "https://example.com",
                            folders = listOf(RemoteFolder("Fiction", "https://example.com/fiction")), books = books))),
                    searchAvailable = false, searchQuery = query, selection = selection,
                    importedKeys = emptySet(), downloadStates = emptyMap(), layoutMode = layout,
                    sortMode = RemoteSortMode.DEFAULT, sortAscending = true, navigatedForward = true,
                    contentBackdrop = null, onBack = {}, onSearchOpen = { query = "" },
                    onSearchQueryChange = { query = it }, onSearchSubmit = {}, onSearchClose = { query = null },
                    onRefresh = {}, onOpenFolder = { openedFolders++ }, onOpenBook = { openedBook = it },
                    onToggleBook = { selection = if (it.key in selection) selection - it.key else selection + it.key },
                    onSelectAll = { visible -> selection = visible.mapTo(linkedSetOf()) { it.key } },
                    onSortModeChange = {}, onSortAscendingChange = {}, onLayoutModeChange = {},
                    onLoadMore = {}, onRetry = {}, onDownloadSelected = { downloads++ }
                )
            }
        }
    }

    @Test fun tapSelectsImmediatelyAndFoldersRemainNavigable() {
        page()
        compose.onNodeWithText(context.getString(R.string.remote_library_download_selected, 0)).assertIsNotEnabled()
        compose.onNodeWithText(first.title).performClick().assertIsSelected()
        compose.runOnIdle { assertEquals(setOf(first.key), selection) }
        compose.onNodeWithText(context.getString(R.string.remote_library_download_selected, 1))
            .assertIsEnabled().performClick()
        compose.onNodeWithText("Fiction").performClick()
        compose.runOnIdle {
            assertEquals(1, downloads)
            assertEquals(1, openedFolders)
            assertEquals(null, openedBook)
        }
        capture("remote-list-selected")
        compose.onNodeWithText(first.title).performClick().assertIsNotSelected()
    }

    @Test fun localSearchAndSelectAllUseVisibleBooks() {
        page()
        compose.onNodeWithTag("remote-search-pill").performClick()
        compose.onNode(hasSetTextAction()).performTextInput("Second")
        compose.onNodeWithText(first.title).assertDoesNotExist()
        compose.onNodeWithText(second.title).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.import_select_all)).performClick()
        compose.runOnIdle { assertEquals(setOf(second.key), selection) }
        compose.onNodeWithContentDescription(context.getString(R.string.import_search_clear)).performClick()
        compose.onNodeWithText(first.title).assertIsDisplayed()
    }

    @Test fun gridSelectsOnTapAndKeepsDetailsOnLongPress() {
        page(layout = 3)
        compose.onNodeWithText(first.title).performClick().assertIsSelected()
        compose.onNodeWithText(second.title).performTouchInput { longClick() }
        compose.runOnIdle {
            assertEquals(setOf(first.key), selection)
            assertEquals(second, openedBook)
        }
        capture("remote-grid-selected")
    }

    @Test fun unsupportedBookOpensDetailsWithoutBeingSelected() {
        page(books = listOf(first.copy(formats = emptyList())))
        compose.onNodeWithText(first.title).performClick()
        compose.runOnIdle {
            assertEquals(first.key, openedBook?.key)
            assertEquals(emptySet<String>(), selection)
        }
        compose.onNodeWithText(context.getString(R.string.import_select_all)).assertIsNotEnabled()
    }

    private fun capture(name: String) {
        compose.runOnIdle {
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            File("build/book-picker-previews/$name.png").also { it.parentFile.mkdirs() }
                .outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }
}
