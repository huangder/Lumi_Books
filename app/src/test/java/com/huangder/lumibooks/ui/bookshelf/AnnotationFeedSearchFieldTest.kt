package com.huangder.lumibooks.ui.bookshelf

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.huangder.lumibooks.ui.theme.EBookReaderTheme
import com.huangder.lumibooks.ui.theme.MotionPreference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "en-w360dp-h740dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AnnotationFeedSearchFieldTest {
    @get:Rule val compose = createComposeRule()
    private val open = mutableStateOf(false)
    private val query = mutableStateOf("")

    private fun page(eInk: Boolean = false, reduced: Boolean = false) {
        compose.setContent {
            EBookReaderTheme(appTheme = "lumi", darkTheme = false, eInkMode = eInk,
                motionPreference = if (reduced) MotionPreference.REDUCED else MotionPreference.STANDARD) {
                Column {
                    AnnotationFeedSearchField(open.value, query.value, { query.value = it }, { open.value = false },
                        Modifier.fillMaxWidth().padding(vertical = 8.dp).testTag("search-region"))
                    Text("Filters", Modifier.testTag("filters"))
                }
            }
        }
        compose.waitForIdle()
    }

    private fun filtersTop() = compose.onNodeWithTag("filters").getUnclippedBoundsInRoot().top.value

    // AnimatedVisibility obtains its target size during Android measure/layout. Advancing the
    // Compose clock by one large chunk can run that whole interval before the new child is measured.
    // Flush each rendered frame so the assertion observes actual layout frames, not a stale bound.
    private fun transitionFrames(): List<Float> = List(24) {
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        filtersTop()
    }

    @Test fun searchExpandsAndShrinksInsteadOfAppearingAndDisappearingInOneFrame() {
        compose.mainClock.autoAdvance = false
        page()
        compose.onNode(hasSetTextAction()).assertDoesNotExist()
        val closedTop = filtersTop()
        compose.runOnIdle { open.value = true }
        val openingFrames = transitionFrames()
        val expandedTop = openingFrames.last()
        assertTrue("Search must reserve space when expanded: $openingFrames", expandedTop > closedTop)
        assertTrue("Filters should move gradually while search opens: $openingFrames",
            openingFrames.any { it > closedTop && it < expandedTop })
        compose.onNode(hasSetTextAction()).performTextInput("instant query")
        compose.runOnIdle { assertEquals("instant query", query.value); open.value = false }
        val closingFrames = transitionFrames()
        assertTrue("Search should retain space during its exit: $closingFrames",
            closingFrames.any { it > closedTop && it < expandedTop })
        assertEquals(closedTop, closingFrames.last(), 0.1f)
        compose.onNodeWithTag("search-region").assertDoesNotExist()
        compose.runOnIdle { open.value = true }
        transitionFrames()
        compose.onNode(hasSetTextAction()).assertTextEquals("instant query")
    }

    @Test fun eInkUsesAnImmediateTransitionAndKeepsTheFieldEditable() {
        page(eInk = true)
        compose.runOnIdle { open.value = true }
        compose.onNode(hasSetTextAction()).performTextInput("ink query")
        compose.runOnIdle { assertEquals("ink query", query.value); open.value = false }
        compose.onNodeWithTag("search-region").assertDoesNotExist()
    }

    @Test fun reducedMotionKeepsTypingAndClosingResponsive() {
        page(reduced = true)
        compose.runOnIdle { open.value = true }
        compose.onNode(hasSetTextAction()).performTextInput("reduced query")
        compose.runOnIdle { assertEquals("reduced query", query.value); open.value = false }
        compose.onNodeWithTag("search-region").assertDoesNotExist()
    }

}
