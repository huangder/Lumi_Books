package com.huangder.lumibooks.ui.home

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.huangder.lumibooks.ui.theme.AppSpace
import com.huangder.lumibooks.ui.theme.LocalIsDarkTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

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
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun scrollingExposesThePageBackgroundAtTheLeftEdge() =
        verifyStartFade(LayoutDirection.Ltr)

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun scrollingExposesThePageBackgroundAtTheRightEdgeInRtl() =
        verifyStartFade(LayoutDirection.Rtl)

    private fun verifyStartFade(direction: LayoutDirection) {
        lateinit var view: View
        compose.setContent {
            view = LocalView.current
            state = rememberLazyListState(initialFirstVisibleItemScrollOffset = 120)
            CompositionLocalProvider(
                LocalLayoutDirection provides direction,
                LocalIsDarkTheme provides true
            ) {
                Box(Modifier.size(280.dp, 80.dp).background(Color.Red)) {
                    RecentBooksRow(itemWidth = 500.dp, state = state, startUnderlap = 88.dp) {
                        item { Box(Modifier.size(500.dp, 80.dp).background(Color.Blue)) }
                    }
                }
            }
        }
        val bounds = compose.onNodeWithTag("recent_books_row").fetchSemanticsNode().boundsInRoot
        compose.runOnIdle {
            assertTrue(state.canScrollBackward)
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            try {
                view.draw(Canvas(bitmap))
                val y = bounds.center.y.toInt()
                val edge = if (direction == LayoutDirection.Rtl) bounds.right.toInt() - 1 else bounds.left.toInt()
                val edgePixel = bitmap.getPixel(edge, y)
                assertTrue(AndroidColor.red(edgePixel) >= 250)
                assertTrue(AndroidColor.green(edgePixel) <= 3 && AndroidColor.blue(edgePixel) <= 3)
                assertEquals(AndroidColor.BLUE, bitmap.getPixel(bounds.center.x.toInt(), y))
            } finally {
                bitmap.recycle()
            }
        }
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
