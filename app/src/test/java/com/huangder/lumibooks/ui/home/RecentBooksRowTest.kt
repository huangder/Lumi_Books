package com.huangder.lumibooks.ui.home

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.dp
import com.huangder.lumibooks.ui.theme.AppSpace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w400dp-h850dp")
class RecentBooksRowTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var state: LazyListState
    private var stride = 0f

    private fun content() {
        compose.setContent {
            state = rememberLazyListState()
            stride = with(LocalDensity.current) { (96.dp + AppSpace.md).toPx() }
            Column {
                Box(Modifier.fillMaxWidth().padding(horizontal = AppSpace.lg)) {
                    Box(Modifier.fillMaxWidth().height(40.dp).testTag("card"))
                }
                RecentBooksRow(itemWidth = 96.dp, state = state) {
                    items(6) { Box(Modifier.size(96.dp, 140.dp).testTag("cover-$it")) }
                }
            }
        }
    }

    @Test
    fun lastCoverAlignsWithTheCardAtTheEnd() {
        content()
        compose.onNodeWithTag("recent_books_row").performScrollToIndex(5)
        val card = compose.onNodeWithTag("card").fetchSemanticsNode().boundsInRoot
        val cover = compose.onNodeWithTag("cover-5").fetchSemanticsNode().boundsInRoot
        assertEquals(card.right, cover.right, .5f)
        compose.runOnIdle { assertFalse(state.canScrollForward) }
    }

    @Test
    fun releasedRowCoastsAndGraduallySlowsDown() {
        content()
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("recent_books_row").performTouchInput {
            swipe(Offset(width * .85f, height / 2f), Offset(width * .65f, height / 2f), durationMillis = 100)
        }
        var releasedAt = 0f
        var first = 0f
        var second = 0f
        compose.runOnIdle { releasedAt = state.firstVisibleItemIndex * stride + state.firstVisibleItemScrollOffset }
        compose.mainClock.advanceTimeBy(160)
        compose.runOnIdle {
            assertTrue(state.isScrollInProgress)
            first = state.firstVisibleItemIndex * stride + state.firstVisibleItemScrollOffset
        }
        compose.mainClock.advanceTimeBy(160)
        compose.runOnIdle { second = state.firstVisibleItemIndex * stride + state.firstVisibleItemScrollOffset }
        assertTrue("The row keeps moving after release", first > releasedAt + 1f)
        assertTrue(second > first + 1f)
        assertTrue("Later movement must be slower", second - first < first - releasedAt)
        compose.mainClock.advanceTimeBy(900)
        compose.runOnIdle { assertFalse(state.isScrollInProgress) }
    }
}
