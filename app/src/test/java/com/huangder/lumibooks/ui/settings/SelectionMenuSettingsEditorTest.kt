package com.huangder.lumibooks.ui.settings

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.huangder.lumibooks.R
import com.huangder.lumibooks.domain.model.SelectionMenuAction
import com.huangder.lumibooks.domain.model.SelectionMenuSettings
import com.huangder.lumibooks.ui.components.SelectionMenuSettingsEditor
import com.huangder.lumibooks.ui.components.SelectionMenuSettingsDialog
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class SelectionMenuSettingsEditorTest {
    @get:Rule val compose = createComposeRule()
    private val saved = mutableListOf<SelectionMenuSettings>()
    private val listState = LazyListState()
    private var dragging = false

    private fun show(initial: SelectionMenuSettings) {
        compose.setContent {
            var settings by remember { mutableStateOf(initial) }
            MaterialTheme {
                Box(Modifier.width(360.dp).height(600.dp)) {
                    SelectionMenuSettingsEditor(settings, { saved += it; settings = it }, listState = listState,
                        onDraggingChange = { dragging = it })
                }
            }
        }
    }

    @Test fun accessibilityActionMovesTheExcerptRowBeforeDictionary() {
        show(SelectionMenuSettings())
        val context = RuntimeEnvironment.getApplication()
        val label = context.getString(R.string.excerpt_title)
        val dragLabel = context.getString(R.string.selection_menu_drag, label)
        compose.onNodeWithTag("selection_menu_list").performScrollToNode(hasContentDescription(dragLabel))
        val actions = compose.onNodeWithContentDescription(dragLabel).fetchSemanticsNode().config[SemanticsActions.CustomActions]
        compose.runOnIdle { actions.first { it.label == context.getString(R.string.selection_menu_move_up, label) }.action() }
        val excerptTop = compose.onNodeWithText(label).fetchSemanticsNode().boundsInRoot.top
        val dictionaryTop = compose.onNodeWithText(context.getString(R.string.menu_dictionary)).fetchSemanticsNode().boundsInRoot.top
        assertTrue(excerptTop < dictionaryTop)
    }

    @Test fun longPressHandleMovesTheFirstToolDown() {
        show(SelectionMenuSettings())
        val context = RuntimeEnvironment.getApplication()
        val highlight = context.getString(R.string.menu_highlight)
        val underline = context.getString(R.string.menu_underline)
        val rowDistance = compose.onNodeWithText(underline).fetchSemanticsNode().boundsInRoot.top -
            compose.onNodeWithText(highlight).fetchSemanticsNode().boundsInRoot.top
        compose.onNodeWithContentDescription(context.getString(R.string.selection_menu_drag, highlight))
            .performTouchInput {
                down(center)
                advanceEventTime(650)
                repeat(5) { moveBy(Offset(0f, rowDistance / 4), delayMillis = 60) }
                up()
            }
        val highlightTop = compose.onNodeWithText(highlight).fetchSemanticsNode().boundsInRoot.top
        val underlineTop = compose.onNodeWithText(context.getString(R.string.menu_underline)).fetchSemanticsNode().boundsInRoot.top
        assertTrue("Drag distance=$rowDistance; highlight=$highlightTop underline=$underlineTop", highlightTop > underlineTop)
    }

    @Test fun cardFollowsSmallFingerMovementBeforeChangingOrder() {
        show(SelectionMenuSettings())
        val context = RuntimeEnvironment.getApplication()
        val label = context.getString(R.string.menu_highlight)
        val handle = compose.onNodeWithContentDescription(context.getString(R.string.selection_menu_drag, label))
        val before = compose.onNodeWithText(label).fetchSemanticsNode().boundsInRoot.top
        val movement = handle.fetchSemanticsNode().boundsInRoot.height / 3f
        handle.performTouchInput { down(center); advanceEventTime(650); moveBy(Offset(0f, movement)) }
        val after = compose.onNodeWithText(label).fetchSemanticsNode().boundsInRoot.top
        assertEquals("The card must follow the finger before crossing a row", movement, after - before, 2f)
        compose.runOnIdle { assertTrue(saved.isEmpty()) }
        handle.performTouchInput { up() }
    }

    @Test fun reorderingFirstItemKeepsTheViewportAndSavesOnceOnRelease() {
        show(SelectionMenuSettings())
        val context = RuntimeEnvironment.getApplication()
        val label = context.getString(R.string.menu_highlight)
        val underline = context.getString(R.string.menu_underline)
        val rowDistance = compose.onNodeWithText(underline).fetchSemanticsNode().boundsInRoot.top -
            compose.onNodeWithText(label).fetchSemanticsNode().boundsInRoot.top
        val handle = compose.onNodeWithContentDescription(context.getString(R.string.selection_menu_drag, label))
        handle.performTouchInput {
            down(center); advanceEventTime(650)
            repeat(9) { moveBy(Offset(0f, rowDistance / 4f), delayMillis = 60) }
        }
        compose.runOnIdle {
            assertTrue("Dragging must only preview order", saved.isEmpty())
            assertEquals(0, listState.firstVisibleItemIndex)
            assertEquals(0, listState.firstVisibleItemScrollOffset)
        }
        handle.performTouchInput { up() }
        compose.runOnIdle {
            assertEquals(1, saved.size)
            assertEquals(SelectionMenuAction.HIGHLIGHT, saved.single().order[2])
            assertEquals(0, listState.firstVisibleItemIndex)
            assertEquals(0, listState.firstVisibleItemScrollOffset)
        }
        compose.onNodeWithText(underline).assertIsDisplayed()
    }

    @Test fun cancellingDragDiscardsThePreviewWithoutSaving() {
        show(SelectionMenuSettings())
        val context = RuntimeEnvironment.getApplication()
        val label = context.getString(R.string.menu_highlight)
        val underline = context.getString(R.string.menu_underline)
        val rowDistance = compose.onNodeWithText(underline).fetchSemanticsNode().boundsInRoot.top -
            compose.onNodeWithText(label).fetchSemanticsNode().boundsInRoot.top
        val handle = compose.onNodeWithContentDescription(context.getString(R.string.selection_menu_drag, label))
        handle.performTouchInput {
            down(center); advanceEventTime(650)
            repeat(5) { moveBy(Offset(0f, rowDistance / 4f), delayMillis = 60) }
            cancel()
        }
        compose.runOnIdle { assertTrue(saved.isEmpty()) }
        assertTrue(compose.onNodeWithText(label).fetchSemanticsNode().boundsInRoot.top <
            compose.onNodeWithText(underline).fetchSemanticsNode().boundsInRoot.top)
    }

    @Test fun aSingleLargeMoveCanPlaceTheSecondToolFirstWithoutEnteringTheHeader() {
        show(SelectionMenuSettings())
        val context = RuntimeEnvironment.getApplication()
        val highlight = context.getString(R.string.menu_highlight)
        val underline = context.getString(R.string.menu_underline)
        val firstTop = compose.onNodeWithText(highlight).fetchSemanticsNode().boundsInRoot.top
        val secondTop = compose.onNodeWithText(underline).fetchSemanticsNode().boundsInRoot.top
        val handle = compose.onNodeWithContentDescription(context.getString(R.string.selection_menu_drag, underline))
        handle.performTouchInput {
            down(center); advanceEventTime(650)
            moveBy(Offset(0f, -(secondTop - firstTop) * 1.4f))
        }
        assertTrue(compose.onNodeWithText(underline).fetchSemanticsNode().boundsInRoot.top >= firstTop)
        handle.performTouchInput { up() }
        compose.runOnIdle {
            assertEquals(SelectionMenuAction.UNDERLINE, saved.single().order.first())
            assertEquals(0, listState.firstVisibleItemIndex)
            assertEquals(0, listState.firstVisibleItemScrollOffset)
        }
        compose.onNodeWithText(underline).assertIsDisplayed()
        compose.onNodeWithText(highlight).assertIsDisplayed()
    }

    @Test fun holdingAtTheBottomAndTopScrollsWithoutLosingTheDraggedCard() {
        show(SelectionMenuSettings())
        val context = RuntimeEnvironment.getApplication()
        val note = context.getString(R.string.menu_note)
        val handle = compose.onNodeWithContentDescription(context.getString(R.string.selection_menu_drag, note))
        val list = compose.onNodeWithTag("selection_menu_list")
        val viewport = list.fetchSemanticsNode().boundsInRoot
        handle.performTouchInput { down(center); advanceEventTime(650); moveBy(Offset.Zero) }
        compose.runOnIdle { assertTrue("Long press must acquire the handle", dragging) }
        compose.mainClock.autoAdvance = false
        handle.performTouchInput {
            moveBy(Offset(0f, viewport.height))
        }
        repeat(100) { compose.waitForIdle(); compose.mainClock.advanceTimeByFrame() }
        compose.runOnIdle {
            assertTrue(listState.firstVisibleItemIndex > 0)
            assertTrue(saved.isEmpty())
        }
        val lowerBounds = compose.onNodeWithText(note).fetchSemanticsNode().boundsInRoot
        assertTrue(lowerBounds.top >= viewport.top && lowerBounds.bottom <= viewport.bottom)
        list.performTouchInput { up() }
        compose.mainClock.autoAdvance = true
        compose.runOnIdle { assertEquals(SelectionMenuAction.NOTE, saved.single().order.last()) }

        handle.performTouchInput { down(center); advanceEventTime(650); moveBy(Offset.Zero) }
        compose.runOnIdle { assertTrue("Long press must reacquire the handle", dragging) }
        compose.mainClock.autoAdvance = false
        handle.performTouchInput {
            moveBy(Offset(0f, -viewport.height))
        }
        repeat(100) { compose.waitForIdle(); compose.mainClock.advanceTimeByFrame() }
        list.performTouchInput { up() }
        compose.mainClock.autoAdvance = true
        compose.runOnIdle {
            assertEquals(SelectionMenuAction.NOTE, saved.last().order.first())
            assertEquals(2, saved.size)
            assertEquals(0, listState.firstVisibleItemIndex)
        }
        compose.onNodeWithText(note).assertIsDisplayed()
    }

    @Test fun lastSwitchIsLockedUntilAnotherToolIsEnabled() {
        show(SelectionMenuSettings.fromStored(emptyList(),
            SelectionMenuAction.entries.associate { it.key to (it == SelectionMenuAction.COPY) }))
        val context = RuntimeEnvironment.getApplication()
        val copyLabel = context.getString(R.string.menu_copy)
        val excerptLabel = context.getString(R.string.excerpt_title)
        fun scrollTo(label: String) {
            compose.onNodeWithTag("selection_menu_list").performScrollToNode(hasContentDescription(label))
        }
        val copy = compose.onNodeWithContentDescription(copyLabel)
        scrollTo(copyLabel)
        copy.assertIsOn().assertIsNotEnabled()
        scrollTo(excerptLabel)
        compose.onNodeWithContentDescription(context.getString(R.string.excerpt_title))
            .performClick().assertIsOn()
        scrollTo(copyLabel)
        copy.assertIsEnabled().performClick().assertIsOff()
        scrollTo(excerptLabel)
        compose.onNodeWithContentDescription(context.getString(R.string.excerpt_title))
            .assertIsOn().assertIsNotEnabled()
    }

    @Test fun tappingTheSheetTitleDoesNotCancelChanges() {
        var dismissed = false
        compose.setContent {
            MaterialTheme {
                SelectionMenuSettingsDialog(true, SelectionMenuSettings(), { dismissed = true }, {})
            }
        }
        val context = RuntimeEnvironment.getApplication()
        compose.onNodeWithText(context.getString(R.string.selection_menu_settings)).performTouchInput { click() }
        compose.runOnIdle { assertFalse(dismissed) }
    }
}
