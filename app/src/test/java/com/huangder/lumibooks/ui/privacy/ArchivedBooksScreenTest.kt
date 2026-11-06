package com.huangder.lumibooks.ui.privacy

import android.app.Application
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.huangder.lumibooks.R
import com.huangder.lumibooks.domain.model.Book
import com.huangder.lumibooks.domain.model.BookFormat
import com.huangder.lumibooks.ui.settings.PrivacySettingsContent
import com.huangder.lumibooks.ui.theme.EBookReaderTheme
import com.huangder.lumibooks.ui.theme.MotionPreference
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [35], application = Application::class)
class ArchivedBooksScreenTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = RuntimeEnvironment.getApplication()
    private val book = Book("archive", "Archived title", "Author", "book.epub", null,
        BookFormat.EPUB, 1, .42f, createdAt = 1, isArchived = true)

    @Test fun pageDisplaysMetadataAndRestoresWithoutOpeningReader() {
        var state by mutableStateOf(ArchivedBooksUiState(listOf(book), isLoading = false))
        var restored: String? = null
        compose.setContent {
            EBookReaderTheme(motionPreference = MotionPreference.REDUCED) {
                ArchivedBooksContent(state, {}, { restored = it; state = state.copy(books = emptyList()) }, {})
            }
        }
        compose.onNodeWithText(book.title).assertIsDisplayed().assertHasNoClickAction()
        compose.onNodeWithText("42%").assertExists()
        compose.onNodeWithText(context.getString(R.string.restore_book)).performClick()
        compose.runOnIdle { assertEquals(book.id, restored) }
        compose.onNodeWithText(context.getString(R.string.archive_empty)).assertExists()
        compose.onNodeWithText(book.title).assertDoesNotExist()
    }

    @Test fun loadFailureOffersRetryAndRestoringDisablesTheButton() {
        var state by mutableStateOf(ArchivedBooksUiState(isLoading = false, errorRes = R.string.archive_load_failed))
        compose.setContent {
            EBookReaderTheme(motionPreference = MotionPreference.REDUCED) {
                ArchivedBooksContent(state, {}, {}, {
                    state = ArchivedBooksUiState(listOf(book), false, restoringIds = setOf(book.id))
                })
            }
        }
        compose.onNodeWithText(context.getString(R.string.archive_retry)).performClick()
        compose.onNodeWithText(context.getString(R.string.archive_restoring)).assertIsNotEnabled()
    }

    @Test fun privacyArchiveEntryWorksWithoutAPasscode() {
        var opened = false
        compose.setContent {
            EBookReaderTheme(motionPreference = MotionPreference.REDUCED) {
                PrivacySettingsContent(false, false, false,
                    onOpenHiddenLibrary = { error("Hidden-library gate must not open") },
                    onOpenArchivedBooks = { opened = true }, onChangePasscode = {}, onBiometricChange = {})
            }
        }
        compose.onNodeWithText(context.getString(R.string.archived_books_title)).performClick()
        compose.runOnIdle { assertTrue(opened) }
    }
}
