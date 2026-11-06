package com.huangder.lumibooks.ui.bookshelf.spine

import android.app.Application
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.huangder.lumibooks.R
import com.huangder.lumibooks.domain.model.Book
import com.huangder.lumibooks.domain.model.BookFormat
import com.huangder.lumibooks.ui.bookshelf.CoverFlowBookActions
import com.huangder.lumibooks.ui.theme.EBookReaderTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "en-w360dp-h800dp-mdpi")
class SpineMenuTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun openingBookDetailsKeepsTheSpineMenuSelected() {
        val book = Book(
            id = "spine-menu-test-book",
            title = "Spine menu test",
            author = "Author",
            filePath = "book.txt",
            coverPath = null,
            format = BookFormat.TXT,
            lastReadTime = 0,
            readingProgress = 0f,
            createdAt = 1
        )
        var detailsBookId: String? = null
        var menuVisible = false
        lateinit var infoLabel: String
        val sceneKey = "spine-menu-test"
        SpineShelfSession.markDropPlayed(sceneKey)

        compose.mainClock.autoAdvance = false
        compose.setContent {
            infoLabel = LocalContext.current.getString(R.string.coverflow_info)
            EBookReaderTheme(appTheme = "lumi", darkTheme = false) {
                SpineBookshelf(
                    params = SpineShelfParams(
                        books = listOf(book),
                        folders = emptyList(),
                        folderBookCounts = emptyMap(),
                        isLoading = false,
                        isEditing = false,
                        selectedBookIds = emptySet(),
                        deletingBookIds = emptySet(),
                        topPadding = 0.dp,
                        bottomPadding = 0.dp,
                        enabled = true,
                        sceneKey = sceneKey,
                        actions = CoverFlowBookActions(
                            open = { _, _ -> },
                            details = { detailsBookId = it.id },
                            favorite = {},
                            editInfo = {},
                            customCover = {},
                            removeCustomCover = {},
                            tags = {},
                            notes = {},
                            move = {},
                            delete = {}
                        ),
                        onFolderClick = {},
                        onSelectionToggle = {},
                        onHaptic = {},
                        onMenuVisibleChange = { menuVisible = it }
                    ),
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        compose.waitForIdle()
        compose.onNodeWithContentDescription(book.title).performTouchInput { longClick() }
        compose.mainClock.advanceTimeBy(1_000)
        compose.onNodeWithText(infoLabel).assertIsDisplayed().performClick()

        compose.runOnIdle {
            assertEquals(book.id, detailsBookId)
            assertTrue(menuVisible)
        }
        compose.onNodeWithText(infoLabel).assertIsDisplayed()
    }
}
