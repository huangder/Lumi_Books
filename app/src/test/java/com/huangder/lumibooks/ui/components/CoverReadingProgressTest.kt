package com.huangder.lumibooks.ui.components

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.unit.dp
import com.huangder.lumibooks.R
import com.huangder.lumibooks.data.sync.BookDownloadState
import com.huangder.lumibooks.domain.model.Book
import com.huangder.lumibooks.domain.model.BookFormat
import com.huangder.lumibooks.ui.theme.LocalCoverReadingProgressEnabled
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "en-w360dp-h640dp-mdpi")
class CoverReadingProgressTest {
    @get:Rule val compose = createComposeRule()

    private val book = Book("test", "Title", "Author", "book.epub", null,
        BookFormat.EPUB, 0L, 0.42f, createdAt = 0L)

    @Test
    fun switchingMovesProgressAndFinishedStatusWithoutDuplicates() {
        val onCover = mutableStateOf(false)
        val currentBook = mutableStateOf(book)
        compose.setContent {
            CompositionLocalProvider(LocalCoverReadingProgressEnabled provides onCover.value) {
                Column {
                    Box(Modifier.size(72.dp, 96.dp).testTag("cover")) {
                        BookCoverStatusBadge(currentBook.value, compact = true)
                    }
                    Box(Modifier.testTag("metadata")) { BookProgressStatus(currentBook.value) }
                }
            }
        }
        assertStatus("42%", "metadata")
        compose.runOnIdle { onCover.value = true }
        assertStatus("42%", "cover")
        compose.runOnIdle { currentBook.value = book.copy(readingProgress = 1f) }
        val finished = RuntimeEnvironment.getApplication().getString(R.string.book_read_finished)
        assertStatus(finished, "cover")
        compose.runOnIdle { onCover.value = false }
        assertStatus(finished, "metadata")
    }

    @Test
    fun cloudAndDownloadStatusRemainVisibleWithCoverProgressEnabled() {
        val download = mutableStateOf<BookDownloadState?>(null)
        compose.setContent {
            CompositionLocalProvider(LocalCoverReadingProgressEnabled provides true) {
                val cloudBook = book.copy(isCloudOnly = true)
                Column {
                    Box(Modifier.size(72.dp, 96.dp)) { BookCoverStatusBadge(cloudBook) }
                    Box(Modifier.testTag("metadata")) { BookProgressStatus(cloudBook, download.value) }
                }
            }
        }
        val context = RuntimeEnvironment.getApplication()
        assertStatus(context.getString(R.string.book_detail_cloud_only), "metadata")
        compose.runOnIdle { download.value = BookDownloadState.Downloading(25L, 100L) }
        assertStatus(context.getString(R.string.book_download_progress, 25), "metadata")
    }

    private fun assertStatus(text: String, parentTag: String) {
        compose.onAllNodesWithText(text, useUnmergedTree = true).assertCountEquals(1)
        compose.onNode(hasText(text) and hasAnyAncestor(hasTestTag(parentTag)),
            useUnmergedTree = true).assertExists()
    }
}
