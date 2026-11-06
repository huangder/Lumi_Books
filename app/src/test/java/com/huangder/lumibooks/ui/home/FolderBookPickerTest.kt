package com.huangder.lumibooks.ui.home

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.view.View
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.test.core.app.ApplicationProvider
import com.huangder.lumibooks.R
import com.huangder.lumibooks.ui.theme.EBookReaderTheme
import com.huangder.lumibooks.ui.theme.MotionPreference
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "zh-rCN-w360dp-h740dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FolderBookPickerTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = ApplicationProvider.getApplicationContext<Application>()
    private var selection by mutableStateOf<Set<String>>(emptySet())
    private lateinit var view: View

    private fun page(fontScale: Float = 1f) {
        val files = listOf("夜航西飞.epub", "小王子.epub").mapIndexed { index, name ->
            AuthorizedFolderFile(Uri.parse("content://books/$index"), name, "content://books", "书籍",
                null, null, null, 1024L, 0L)
        }
        val byKey = files.associateBy { "file:${it.uri}" }
        val items = byKey.map { (key, file) ->
            FolderBooksItem(key, file.name, file = FolderBooksFile(key, file.name, file.treeUri,
                file.folderName, null, file.size, file.lastModified))
        }
        compose.setContent {
            view = LocalView.current
            EBookReaderTheme(appTheme = "lumi", motionPreference = MotionPreference.REDUCED) {
                CompositionLocalProvider(LocalDensity provides Density(1f, fontScale)) {
                    AuthorizedFolderBooksScreen(
                        items = items, breadcrumb = emptyList(), levelBooks = files, filesByKey = byKey,
                        pathByKey = emptyMap(), levelSummary = context.getString(R.string.folder_books_level_summary, 0, 2),
                        selection = selection, importedUris = emptySet(), coverPaths = emptyMap(),
                        isScanning = false, emptyLabel = "", isSearching = false, showingSearchResults = false,
                        searchQuery = "", layoutMode = 1, sortMode = FolderBooksSortMode.NAME, sortAscending = true,
                        onBack = {}, onOpenFolder = {}, onToggleBook = {
                            val uri = it.uri.toString()
                            selection = if (uri in selection) selection - uri else selection + uri
                        },
                        onSelectAll = {
                            val all = files.mapTo(linkedSetOf()) { it.uri.toString() }
                            selection = if (selection.containsAll(all)) emptySet() else all
                        },
                        onLayoutModeChange = {}, onSortModeChange = {}, onSortAscendingChange = {},
                        onSearchOpen = {}, onSearchQueryChange = {}, onSearchClose = {}, onRefresh = {}, onConfirm = {}
                    )
                }
            }
        }
    }

    @Test fun selectedCountMovesToConfirmAndSelectAllSitsAtItsRight() {
        page()
        compose.onNodeWithText("确认并导入").assertIsNotEnabled()
        compose.onNodeWithText("夜航西飞").performClick()
        val confirm = compose.onNodeWithText("确认导入1本").assertIsEnabled().getUnclippedBoundsInRoot()
        val selectAll = compose.onNodeWithText("全选").assertIsEnabled().getUnclippedBoundsInRoot()
        assertTrue("Select all belongs beside the narrower confirm button", selectAll.left > confirm.right)
        assertTrue("Both actions align along the bottom", selectAll.top == confirm.top)
        compose.onNodeWithText("0 个文件夹 · 2 本书").assertExists()
        compose.onAllNodesWithText("已选", substring = true).assertCountEquals(0)
        compose.onAllNodesWithText("全选").assertCountEquals(1)
        capture("folder-list-selected")
        compose.onNodeWithText("全选").performClick()
        compose.onNodeWithText("确认导入2本").assertIsEnabled()
        compose.onNodeWithText("全选").performClick()
        compose.onNodeWithText("确认并导入").assertIsNotEnabled()
    }

    @Test fun bottomActionsFitNarrowScreenWithLargeText() {
        page(fontScale = 1.5f)
        compose.onNodeWithText("全选").performClick()
        val confirm = compose.onNodeWithText("确认导入2本").assertIsDisplayed().getUnclippedBoundsInRoot()
        val selectAll = compose.onNodeWithText("全选").assertIsDisplayed().getUnclippedBoundsInRoot()
        assertTrue("Actions must not overlap", confirm.right < selectAll.left)
        assertTrue("Select all must stay inside the screen", selectAll.right.value <= 360f)
        capture("folder-list-large-text")
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
