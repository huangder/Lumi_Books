package com.huangder.lumibooks.ui.animation

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w400dp-h850dp")
class OverscrollBounceTest {
    @get:Rule val compose = createComposeRule()
    private val bounce = OverscrollBounceState()
    private lateinit var list: LazyListState
    private var releasedOffset: Float? = null

    private fun content() {
        compose.setContent {
            list = rememberLazyListState()
            OverscrollBounce(
                modifier = Modifier.size(300.dp, 400.dp),
                state = bounce,
                onTopPullRelease = { releasedOffset = it }
            ) {
                LazyColumn(Modifier.testTag("list"), state = list) {
                    items(30) { Box(Modifier.fillMaxWidth().height(80.dp)) }
                }
            }
        }
    }

    @Test
    fun reversingTopPullRestoresTranslationBeforeScrollingTheList() {
        content()
        compose.onNodeWithTag("list").performTouchInput {
            down(Offset(width / 2f, height * .25f))
            moveBy(Offset(0f, 160f), 160)
        }
        var pulled = 0f
        compose.runOnIdle {
            pulled = bounce.offset
            assertTrue(pulled > 20f)
        }
        compose.onNodeWithTag("list").performTouchInput {
            moveBy(Offset(0f, -pulled / 2f), 100)
        }
        compose.runOnIdle {
            assertEquals(pulled / 2f, bounce.offset, 1f)
            assertEquals(0, list.firstVisibleItemIndex)
            assertEquals(0, list.firstVisibleItemScrollOffset)
        }
        compose.onNodeWithTag("list").performTouchInput {
            moveBy(Offset(0f, -pulled), 100)
        }
        compose.runOnIdle {
            assertEquals(0f, bounce.offset, .01f)
            assertTrue(list.firstVisibleItemScrollOffset > 0)
        }
        compose.onNodeWithTag("list").performTouchInput { up() }
        compose.runOnIdle { assertEquals(0f, releasedOffset!!, .01f) }
    }

    @Test
    fun reversingBottomPullRestoresTranslationBeforeScrollingTheList() {
        content()
        compose.onNodeWithTag("list").performScrollToIndex(29)
        compose.onNodeWithTag("list").performTouchInput {
            down(Offset(width / 2f, height * .75f))
            moveBy(Offset(0f, -160f), 160)
        }
        var pulled = 0f
        var index = 0
        var scroll = 0
        compose.runOnIdle {
            pulled = bounce.offset
            index = list.firstVisibleItemIndex
            scroll = list.firstVisibleItemScrollOffset
            assertTrue(pulled < -20f)
        }
        compose.onNodeWithTag("list").performTouchInput {
            moveBy(Offset(0f, -pulled / 2f), 100)
        }
        compose.runOnIdle {
            assertEquals(pulled / 2f, bounce.offset, 1f)
            assertEquals(index, list.firstVisibleItemIndex)
            assertEquals(scroll, list.firstVisibleItemScrollOffset)
        }
        compose.onNodeWithTag("list").performTouchInput {
            moveBy(Offset(0f, -pulled), 100)
        }
        compose.runOnIdle {
            assertEquals(0f, bounce.offset, .01f)
            assertTrue(list.canScrollForward)
        }
        compose.onNodeWithTag("list").performTouchInput { up() }
    }

    @Test
    fun releasingAnUnreversedTopPullStillReportsItsDistanceAndSpringsBack() {
        content()
        compose.onNodeWithTag("list").performTouchInput {
            down(Offset(width / 2f, height * .25f))
            moveBy(Offset(0f, 160f), 160)
            up()
        }
        compose.runOnIdle {
            assertTrue(releasedOffset!! > 20f)
            assertEquals(0f, bounce.offset, .01f)
            assertEquals(0, list.firstVisibleItemScrollOffset)
        }
    }
}
