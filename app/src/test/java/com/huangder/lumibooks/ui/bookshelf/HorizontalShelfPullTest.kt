package com.huangder.lumibooks.ui.bookshelf

import android.app.Application
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.huangder.lumibooks.domain.model.Book
import com.huangder.lumibooks.domain.model.BookFormat
import com.huangder.lumibooks.ui.animation.OverscrollBounce
import com.huangder.lumibooks.ui.animation.OverscrollBounceState
import com.huangder.lumibooks.ui.bookshelf.spine.SpineBookshelf
import com.huangder.lumibooks.ui.bookshelf.spine.SpineShelfParams
import com.huangder.lumibooks.ui.bookshelf.spine.SpineShelfSession
import com.huangder.lumibooks.ui.privacy.HiddenLibraryPullState
import com.huangder.lumibooks.ui.theme.EBookReaderTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w400dp-h850dp-mdpi")
class HorizontalShelfPullTest {
    @get:Rule val compose = createComposeRule()
    private val bounce = OverscrollBounceState()
    private val pull = HiddenLibraryPullState().apply { thresholdPx = 100f }
    private val flow = CoverFlowState()
    private var opened by mutableStateOf(false)
    private var bookOpens = 0
    private val books = (0..5).map { index ->
        Book(id = "pull-$index", title = "Book $index", author = "Author", filePath = "book.txt",
            coverPath = null, format = BookFormat.TXT, lastReadTime = 0, readingProgress = 0f, createdAt = 1)
    }

    private fun content(spine: Boolean, capture: Boolean = true) {
        flow.updateBooks(books.map { it.id })
        SpineShelfSession.markDropPlayed("pull-test")
        compose.setContent {
            EBookReaderTheme(darkTheme = false, eInkMode = true) {
                OverscrollBounce(
                    modifier = Modifier.size(360.dp, 700.dp).testTag("shelf"),
                    state = bounce,
                    captureVerticalDrag = capture && !opened,
                    onTopPullDrag = pull::onDrag,
                    onTopPullRelease = { opened = pull.release(it) }
                ) {
                    if (spine) {
                        SpineBookshelf(SpineShelfParams(
                            books = books, folders = emptyList(), folderBookCounts = emptyMap(),
                            isLoading = false, isEditing = false, selectedBookIds = emptySet(),
                            deletingBookIds = emptySet(), topPadding = 0.dp, bottomPadding = 0.dp,
                            enabled = true, sceneKey = "pull-test",
                            actions = CoverFlowBookActions(
                                open = { _, _ -> bookOpens++ }, details = {}, favorite = {}, editInfo = {},
                                customCover = {}, removeCustomCover = {}, tags = {}, notes = {}, move = {}, delete = {}
                            ),
                            onFolderClick = {}, onSelectionToggle = {}, onHaptic = {}, onMenuVisibleChange = {}
                        ))
                    } else {
                        CoverFlowScene(books, flow, CoverFlowSceneLayout(), motionEnabled = false,
                            enabled = true, hiddenHeroId = null, downloadStates = emptyMap(),
                            deletingIds = emptySet(), onOpen = { _, _ -> bookOpens++ }, onHaptic = {},
                            modifier = Modifier.fillMaxSize())
                    }
                }
            }
        }
    }

    private fun pullDown() {
        compose.onNodeWithTag("shelf").performTouchInput {
            down(Offset(width / 2f, height * .25f))
            moveBy(Offset(0f, 320f), 240)
            up()
        }
    }

    @Test fun spinePullOpensHiddenLibraryWithoutOpeningABook() {
        content(spine = true)
        pullDown()
        compose.runOnIdle {
            assertTrue(opened)
            assertEquals(0, bookOpens)
            assertEquals(0f, bounce.offset, .01f)
        }
    }

    @Test fun coverFlowPullOpensHiddenLibraryWithoutChangingTheFocusedBook() {
        content(spine = false)
        pullDown()
        compose.runOnIdle {
            assertTrue(opened)
            assertEquals(0, bookOpens)
            assertEquals(0, flow.focusedIndex)
            assertEquals(0f, bounce.offset, .01f)
        }
    }

    @Test fun coverFlowHorizontalSwipeStillChangesBooksWithoutOpeningHiddenLibrary() {
        content(spine = false)
        compose.onNodeWithTag("shelf").performTouchInput {
            down(Offset(width * .85f, height / 2f))
            moveBy(Offset(-240f, 5f), 240)
            up()
        }
        compose.runOnIdle {
            assertFalse(opened)
            assertTrue(flow.focusedIndex > 0)
            assertEquals(0f, bounce.offset, .01f)
        }
    }

    @Test fun disabledPullDoesNotOpenHiddenLibrary() {
        content(spine = false, capture = false)
        pullDown()
        compose.runOnIdle {
            assertFalse(opened)
            assertEquals(0f, bounce.offset, .01f)
        }
    }
}
