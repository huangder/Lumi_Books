package com.huangder.lumibooks.ui.navigation

import android.app.Application
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Text
import androidx.compose.foundation.clickable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.LayoutDirection
import com.huangder.lumibooks.ui.components.ConfigurableNavigationBack
import com.huangder.lumibooks.ui.animation.mainTabEntrance
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w400dp-h850dp")
class MainTabsPagerTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var state: PagerState
    private lateinit var backDispatcher: OnBackPressedDispatcher
    private val backEvents = mutableListOf<Int>()
    private val compositions = IntArray(3)
    private val placements = IntArray(3)
    private val rowStates = arrayOfNulls<LazyListState>(3)
    private lateinit var selection: MainTabSelection

    @Composable
    private fun Content() {
        state = rememberPagerState { mainTabRoutes.size }
        backDispatcher = LocalOnBackPressedDispatcherOwner.current!!.onBackPressedDispatcher
        val scope = rememberCoroutineScope()
        selection = remember { MainTabSelection() }
        Column(Modifier.fillMaxSize()) {
            MainTabsPager(state, userScrollEnabled = true, modifier = Modifier.weight(1f).testTag("pager"), selection = selection) { page ->
                SideEffect { compositions[page]++ }
                BackHandler { backEvents += page }
                var clicks by rememberSaveable { mutableIntStateOf(0) }
                val rowState = rememberLazyListState()
                SideEffect { rowStates[page] = rowState }
                Column(Modifier.fillMaxSize().background(Color.White).onGloballyPositioned { placements[page]++ }) {
                    Text("page-$page count-$clicks", Modifier.mainTabEntrance().clickable { clicks++ }.testTag("counter-$page"))
                    LazyRow(Modifier.mainTabEntrance().fillMaxWidth().height(80.dp).testTag("row-$page"), state = rowState) {
                        items(20) { item -> Text("book-$item", Modifier.size(100.dp, 80.dp)) }
                    }
                    LazyColumn(Modifier.fillMaxSize().testTag("list-$page")) {
                        items(60) { item -> Text("item-$page-$item", Modifier.fillMaxWidth().height(60.dp)) }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().height(48.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                mainTabRoutes.forEachIndexed { index, route ->
                    Text(route, Modifier.clickable { scope.launch { selection.select(state, index, true) } }.testTag("tab-$index"))
                }
            }
        }
    }

    @Test
    fun tabClicksFadeDirectlyToTheirDestinationWithoutSlidingThroughTheMiddle() {
        compose.setContent { Content() }
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("tab-2").performClick()
        compose.mainClock.advanceTimeByFrame()
        compose.runOnIdle {
            assertEquals(2, state.settledPage)
            assertEquals(0f, state.currentPageOffsetFraction, 0f)
            assertTrue(selection.running)
            assertEquals(0, selection.fromPage)
            assertEquals(2, selection.toPage)
            assertEquals(0f, selection.alpha(1), 0f)
        }
        compose.mainClock.advanceTimeBy(750)
        compose.runOnIdle { assertTrue(!selection.running) }
        compose.mainClock.autoAdvance = true
        compose.onNodeWithTag("pager").performTouchInput { swipeRight() }
        compose.runOnIdle { assertEquals(1, state.settledPage) }
    }

    @Test
    fun emptyAreasDoNotSendTapsToRetainedPages() {
        var clicks = 0
        compose.setContent {
            state = rememberPagerState { mainTabRoutes.size }
            MainTabsPager(state, true, Modifier.testTag("pager")) { page ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    if (page == 1) Box(Modifier.size(150.dp).clickable { clicks++ })
                }
            }
        }
        compose.onNodeWithTag("pager").performTouchInput { click(center) }
        compose.runOnIdle { assertEquals(0, clicks) }
        compose.onNodeWithTag("pager").performTouchInput { swipeLeft() }
        compose.onNodeWithTag("pager").performTouchInput { click(center) }
        compose.runOnIdle { assertEquals(1, clicks) }
    }

    @Test
    fun draggingDoesNotRecomposeOrRepositionPageContents() {
        compose.setContent { Content() }
        lateinit var initialCompositions: IntArray
        lateinit var initialPlacements: IntArray
        compose.runOnIdle {
            assertTrue("All three pages must be ready before dragging", compositions.all { it > 0 })
            initialCompositions = compositions.copyOf()
            initialPlacements = placements.copyOf()
        }
        compose.onNodeWithTag("pager").performTouchInput {
            down(Offset(width * .9f, height * .7f))
            moveTo(Offset(width * .7f, height * .7f), delayMillis = 100)
            moveTo(Offset(width * .4f, height * .7f), delayMillis = 100)
            moveTo(Offset(width * .2f, height * .7f), delayMillis = 100)
        }
        compose.runOnIdle {
            assertTrue(state.currentPage + state.currentPageOffsetFraction > .6f)
            assertTrue("Page composition must stay out of drag frames", initialCompositions.contentEquals(compositions))
            assertTrue("Glass/global-position callbacks must stay out of drag frames", initialPlacements.contentEquals(placements))
        }
        compose.onNodeWithTag("pager").performTouchInput { up() }
        compose.onNodeWithTag("counter-1").performClick()
        compose.onNodeWithText("page-1 count-1").assertIsDisplayed()
    }

    @Test
    fun rightToLeftLayoutKeepsSwipeDirectionAndClickCoordinates() {
        compose.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) { Content() }
        }
        compose.onNodeWithTag("pager").performTouchInput { swipeRight() }
        compose.runOnIdle { assertEquals(1, state.settledPage) }
        compose.onNodeWithTag("counter-1").performClick()
        compose.onNodeWithText("page-1 count-1").assertIsDisplayed()
    }

    @Test
    fun pageTracksFingerBeforeReleaseAndShortDragReturns() {
        compose.setContent { Content() }
        compose.onNodeWithTag("pager").performTouchInput {
            down(Offset(width * .8f, height * .7f))
            moveTo(Offset(width * .7f, height * .7f), delayMillis = 100)
            moveTo(Offset(width * .6f, height * .7f), delayMillis = 100)
        }
        compose.runOnIdle {
            assertEquals(0, state.currentPage)
            assertTrue("The page must move while the finger is still down", state.currentPageOffsetFraction > .1f)
            assertTrue(state.currentPageOffsetFraction < .3f)
        }
        compose.onNodeWithTag("pager").performTouchInput {
            advanceEventTime(200)
            up()
        }
        compose.runOnIdle {
            assertEquals(0, state.settledPage)
            assertEquals(0f, state.currentPageOffsetFraction, .001f)
        }
    }

    @Test
    fun swipesAndTabClicksSharePagesAndClampAtBothEnds() {
        compose.setContent { Content() }
        compose.onNodeWithTag("pager").performTouchInput { swipeRight() }
        compose.runOnIdle { assertEquals(0, state.settledPage) }
        compose.onNodeWithTag("pager").performTouchInput { swipeLeft() }
        compose.runOnIdle { assertEquals(1, state.settledPage) }
        compose.onNodeWithTag("pager").performTouchInput { swipeLeft() }
        compose.runOnIdle { assertEquals(2, state.settledPage) }
        compose.onNodeWithTag("pager").performTouchInput { swipeLeft() }
        compose.runOnIdle { assertEquals(2, state.settledPage) }
        compose.onNodeWithTag("tab-0").performClick()
        compose.runOnIdle { assertEquals(0, state.settledPage) }
        compose.onNodeWithTag("tab-2").performClick()
        compose.onNodeWithTag("pager").performTouchInput { swipeRight() }
        compose.runOnIdle { assertEquals(1, state.settledPage) }
    }

    @Test
    fun verticalListsAndBookRowsKeepTheirOwnGestures() {
        compose.setContent { Content() }
        compose.onNodeWithTag("list-0").performTouchInput { swipeUp() }
        compose.runOnIdle { assertEquals(0, state.settledPage) }
        compose.onNodeWithTag("row-0").performTouchInput { swipeLeft() }
        compose.runOnIdle { assertEquals(0, state.settledPage) }
    }

    @Test
    fun bookRowKeepsFastFlingsAndDragsAtBothEdges() {
        compose.setContent { Content() }
        compose.onNodeWithTag("tab-1").performClick()
        // Start near the end so this fling reaches the edge with velocity remaining.
        compose.onNodeWithTag("row-1").performScrollToIndex(14)
        repeat(3) {
            compose.onNodeWithTag("row-1").performTouchInput { swipeLeft(durationMillis = 80) }
            compose.runOnIdle {
                assertEquals(1, state.settledPage)
                assertEquals(0f, state.currentPageOffsetFraction, .001f)
            }
        }
        compose.runOnIdle { assertTrue(!rowStates[1]!!.canScrollForward) }
        compose.onNodeWithTag("row-1").performTouchInput { swipeLeft(durationMillis = 700) }
        compose.runOnIdle { assertEquals(1, state.settledPage) }
        compose.onNodeWithTag("row-1").performScrollToIndex(0)
        repeat(2) {
            compose.onNodeWithTag("row-1").performTouchInput { swipeRight(durationMillis = 80) }
            compose.runOnIdle {
                assertEquals(1, state.settledPage)
                assertEquals(0f, state.currentPageOffsetFraction, .001f)
            }
        }
        // Gestures outside the row must continue to change the main page.
        compose.onNodeWithTag("pager").performTouchInput { swipeLeft() }
        compose.runOnIdle { assertEquals(2, state.settledPage) }
    }

    @Test
    fun nonPredictiveBackReachesTheVisiblePage() {
        lateinit var activityBackDispatcher: OnBackPressedDispatcher
        compose.setContent {
            activityBackDispatcher = LocalOnBackPressedDispatcherOwner.current!!.onBackPressedDispatcher
            ConfigurableNavigationBack(predictiveBackEnabled = false, bridgeEnabled = true) {
                Content()
            }
        }
        compose.onNodeWithTag("tab-1").performClick()
        compose.runOnIdle {
            activityBackDispatcher.onBackPressed()
            assertEquals(listOf(1), backEvents)
        }
        compose.onNodeWithTag("tab-0").performClick()
        compose.runOnIdle {
            activityBackDispatcher.onBackPressed()
            assertEquals(listOf(1, 0), backEvents)
        }
    }

    @Test
    fun retainedNeighboursDoNotInterceptBackAndPageStateRestores() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent { Content() }
        compose.onNodeWithTag("counter-0").performClick()
        compose.runOnIdle { backDispatcher.onBackPressed() }
        compose.onNodeWithTag("tab-1").performClick()
        compose.runOnIdle {
            assertEquals(1, state.settledPage)
            backDispatcher.onBackPressed()
            assertEquals(listOf(0, 1), backEvents)
        }
        restoration.emulateSavedInstanceStateRestore()
        compose.runOnIdle { assertEquals(1, state.settledPage) }
        compose.onNodeWithTag("tab-0").performClick()
        compose.onNodeWithText("page-0 count-1").assertIsDisplayed()
        compose.runOnIdle { backDispatcher.onBackPressed(); assertEquals(listOf(0, 1, 0), backEvents) }
    }
}
