package com.huangder.lumibooks.ui.privacy

import android.app.Application
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.huangder.lumibooks.R
import com.huangder.lumibooks.domain.model.Book
import com.huangder.lumibooks.domain.model.BookFormat
import com.huangder.lumibooks.ui.bookshelf.*
import com.huangder.lumibooks.ui.components.LiquidGlassMenuHost
import com.huangder.lumibooks.ui.theme.EBookReaderTheme
import com.huangder.lumibooks.ui.theme.MotionPreference
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class ArchivedBookMenuTest {
    @get:Rule val compose = createComposeRule()
    private val book = Book("book", "Title", "Author", "book.epub", null, BookFormat.EPUB, 1, .4f, createdAt = 1)
    private val context get() = RuntimeEnvironment.getApplication()

    @Test fun ordinaryLongPressCanArchiveAndDismissesTheMenu() {
        lateinit var state: BookContextMenuState
        var archived: Book? = null
        compose.setContent {
            EBookReaderTheme(motionPreference = MotionPreference.REDUCED) {
                state = rememberBookContextMenuState()
                BookContextMenuOverlay(state, onArchive = { archived = it })
            }
        }
        compose.runOnIdle { state.onLongPressConfirmed(book, Rect(20f, 30f, 110f, 170f), {}) }
        compose.mainClock.advanceTimeBy(1_000)
        compose.onNodeWithText(context.getString(R.string.archive_book)).performScrollTo().performClick()
        compose.runOnIdle { assertEquals(book, archived) }
        compose.mainClock.advanceTimeBy(1_000)
        compose.runOnIdle { assertEquals(ContextMenuPhase.Idle, state.phase) }
    }

    @Test fun hiddenLibraryNeverOffersArchive() {
        lateinit var state: BookContextMenuState
        compose.setContent {
            EBookReaderTheme(motionPreference = MotionPreference.REDUCED) {
                state = rememberBookContextMenuState()
                BookContextMenuOverlay(state, onArchive = {}, hiddenLibrary = true)
            }
        }
        compose.runOnIdle { state.onLongPressConfirmed(book.copy(isHidden = true), Rect(20f, 30f, 110f, 170f), {}) }
        compose.mainClock.advanceTimeBy(1_000)
        compose.onNodeWithText(context.getString(R.string.archive_book)).assertDoesNotExist()
    }

    @Test fun expandedListRowOffersArchive() {
        var archived = false
        compose.setContent {
            EBookReaderTheme(motionPreference = MotionPreference.REDUCED) {
                BookshelfSearchResultItem(book, emptyList(), expanded = true, isDeleting = false,
                    onExpandedChange = {}, onClick = {}, onEditInfo = {}, onDelete = {}, onFavorite = {},
                    onCustomCover = {}, onRemoveCustomCover = {}, onTags = {}, onBookmarksNotes = {},
                    onArchive = { archived = true })
            }
        }
        compose.onNodeWithText(context.getString(R.string.archive_book)).performScrollTo().performClick()
        compose.runOnIdle { org.junit.Assert.assertTrue(archived) }
    }

    @Test fun coverFlowMoreMenuArchivesTheSelectedBook() {
        var archived: Book? = null
        compose.setContent {
            EBookReaderTheme(motionPreference = MotionPreference.REDUCED) {
                LiquidGlassMenuHost {
                    CoverFlowActionBar(book, CoverFlowBookActions(
                        open = { _, _ -> }, details = {}, favorite = {}, editInfo = {}, customCover = {},
                        removeCustomCover = {}, tags = {}, notes = {}, move = {}, delete = {}, archive = { archived = it }
                    ), onOpen = {}, onDismiss = {})
                }
            }
        }
        compose.onNodeWithText(context.getString(R.string.coverflow_more)).performClick()
        compose.onNodeWithText(context.getString(R.string.archive_book)).performScrollTo().performClick()
        compose.runOnIdle { assertEquals(book, archived) }
    }
}
