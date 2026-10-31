package com.huangder.lumibooks.ui.settings

import android.app.Application
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.huangder.lumibooks.R
import com.huangder.lumibooks.domain.model.SelectionMenuAction
import com.huangder.lumibooks.domain.model.SelectionMenuSettings
import com.huangder.lumibooks.ui.components.SelectionMenuSettingsDialog
import com.huangder.lumibooks.ui.components.SelectionMenuSettingsEditor
import com.huangder.lumibooks.ui.components.labelRes
import com.huangder.lumibooks.ui.theme.EBookReaderTheme
import com.huangder.lumibooks.ui.theme.LocalMotionEnabled
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "en-w360dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SelectionMenuSettingsEditorTest {
    @get:Rule val compose = createComposeRule()
    private fun text(id: Int, vararg args: Any) = RuntimeEnvironment.getApplication().getString(id, *args)
    private fun label(action: SelectionMenuAction) = text(action.labelRes())
    private fun handle(action: SelectionMenuAction) =
        compose.onNodeWithContentDescription(text(R.string.selection_menu_drag, label(action)))

    private fun switchFor(action: SelectionMenuAction) =
        compose.onNode(isToggleable() and hasAnyAncestor(hasTestTag("selection-menu-${action.storageKey}")))

    private fun showEditor(
        initial: SelectionMenuSettings = SelectionMenuSettings(),
        theme: String = "lumi",
        motion: Boolean = true,
        onReordering: (Boolean) -> Unit = {},
        editorModifier: Modifier = Modifier.fillMaxSize(),
        listState: LazyListState = LazyListState()
    ): () -> SelectionMenuSettings {
        var settings by mutableStateOf(initial)
        compose.setContent {
            EBookReaderTheme(appTheme = theme, darkTheme = false) {
                CompositionLocalProvider(LocalMotionEnabled provides motion) {
                    SelectionMenuSettingsEditor(
                        settings = settings,
                        onChange = { settings = it },
                        modifier = editorModifier,
                        listState = listState,
                        onReorderingChange = onReordering
                    )
                }
            }
        }
        return { settings }
    }

    @Test fun everyActionIsListedAndOnlyReplaceIsMarkedTxtOnly() {
        showEditor()
        SelectionMenuAction.entries.forEach { compose.onNodeWithText(label(it)).assertExists() }
        compose.onAllNodesWithText(text(R.string.selection_menu_txt_only)).assertCountEquals(1)
        compose.onNode(hasText(text(R.string.selection_menu_txt_only)) and
            hasAnyAncestor(hasTestTag("selection-menu-replace"))).assertExists()
    }

    @Test fun switchesHideActionsButNeverTheLastVisibleOne() {
        val hiddenAllButCopy = SelectionMenuAction.entries.filter { it != SelectionMenuAction.COPY }
            .fold(SelectionMenuSettings()) { settings, action -> settings.withVisibility(action, false) }
            .withVisibility(SelectionMenuAction.NOTE, true)
        val current = showEditor(hiddenAllButCopy)
        switchFor(SelectionMenuAction.NOTE).assertIsEnabled().performClick()
        compose.runOnIdle { assertFalse(current().isVisible(SelectionMenuAction.NOTE)) }
        switchFor(SelectionMenuAction.COPY).assertIsNotEnabled()
        switchFor(SelectionMenuAction.SEARCH).assertIsEnabled().performClick()
        compose.runOnIdle { assertTrue(current().isVisible(SelectionMenuAction.SEARCH)) }
        switchFor(SelectionMenuAction.COPY).assertIsEnabled()
    }

    @Test fun accessibilityActionsMoveRowsAndStopAtTheEnds() {
        val current = showEditor()
        fun actionsOf(action: SelectionMenuAction) =
            handle(action).fetchSemanticsNode().config[SemanticsActions.CustomActions].map { it.label }
        assertEquals(listOf(text(R.string.selection_menu_move_down, label(SelectionMenuAction.HIGHLIGHT))),
            actionsOf(SelectionMenuAction.HIGHLIGHT))
        handle(SelectionMenuAction.NOTE).fetchSemanticsNode().config[SemanticsActions.CustomActions]
            .first { it.label == text(R.string.selection_menu_move_up, label(SelectionMenuAction.NOTE)) }
            .action()
        compose.runOnIdle {
            assertEquals(listOf(SelectionMenuAction.HIGHLIGHT, SelectionMenuAction.NOTE, SelectionMenuAction.UNDERLINE),
                current().order.take(3))
        }
    }

    @Test fun longPressDragReordersAndCommitsOnRelease() = dragHighlightDown(motion = true)

    @Test fun reducedMotionDragStillReorders() = dragHighlightDown(motion = false)

    private fun dragHighlightDown(motion: Boolean) {
        val events = mutableListOf<Boolean>()
        val current = showEditor(motion = motion, onReordering = { events += it })
        compose.mainClock.autoAdvance = false
        handle(SelectionMenuAction.HIGHLIGHT).performTouchInput {
            down(center)
            advanceEventTime(viewConfiguration.longPressTimeoutMillis + 100)
        }
        compose.mainClock.advanceTimeBy(700)
        compose.onRoot().performTouchInput {
            repeat(20) { moveBy(androidx.compose.ui.geometry.Offset(0f, 8f)) }
        }
        compose.mainClock.advanceTimeBy(32)
        compose.runOnUiThread {
            assertEquals(listOf(true), events)
            assertEquals("Nothing is committed while dragging", SelectionMenuSettings(), current())
        }
        compose.onRoot().performTouchInput { up() }
        compose.mainClock.autoAdvance = true
        compose.runOnIdle {
            assertEquals(listOf(true, false), events)
            val order = current().order
            assertTrue("Highlight should have moved down: $order", order.indexOf(SelectionMenuAction.HIGHLIGHT) >= 2)
            assertEquals(SelectionMenuAction.entries.size, order.distinct().size)
        }
    }

    @Test fun fastDragBeyondShortViewportKeepsScrollingUntilRelease() {
        val listState = LazyListState()
        val events = mutableListOf<Boolean>()
        val current = showEditor(
            motion = false,
            onReordering = { events += it },
            editorModifier = Modifier.height(220.dp),
            listState = listState
        )
        compose.mainClock.autoAdvance = false
        handle(SelectionMenuAction.HIGHLIGHT).performTouchInput {
            down(center)
            advanceEventTime(viewConfiguration.longPressTimeoutMillis + 100)
        }
        compose.mainClock.advanceTimeBy(700)
        compose.onRoot().performTouchInput {
            moveBy(androidx.compose.ui.geometry.Offset(0f, 250f))
        }
        // Hold the pointer outside the viewport while real frame-driven scrolling runs.
        compose.mainClock.advanceTimeBy(1_600)
        compose.runOnUiThread {
            assertTrue("The held drag must scroll past its original slot", listState.firstVisibleItemIndex > 1)
            assertEquals(listOf(true), events)
            assertEquals("Auto-scroll must not save the draft", SelectionMenuSettings(), current())
        }
        compose.onRoot().performTouchInput { up() }
        compose.mainClock.autoAdvance = true
        compose.runOnIdle {
            assertEquals(listOf(true, false), events)
            assertTrue(current().order.indexOf(SelectionMenuAction.HIGHLIGHT) >= 6)
        }
    }

    @Test fun cancellingAnActiveDragDiscardsItsOrder() {
        val events = mutableListOf<Boolean>()
        val current = showEditor(motion = false, onReordering = { events += it })
        compose.mainClock.autoAdvance = false
        handle(SelectionMenuAction.HIGHLIGHT).performTouchInput {
            down(center)
            advanceEventTime(viewConfiguration.longPressTimeoutMillis + 100)
        }
        compose.mainClock.advanceTimeBy(700)
        compose.onRoot().performTouchInput {
            moveBy(androidx.compose.ui.geometry.Offset(0f, 160f))
        }
        compose.mainClock.advanceTimeBy(32)
        compose.runOnUiThread {
            assertEquals(listOf(true), events)
            assertEquals(SelectionMenuSettings(), current())
        }
        compose.onRoot().performTouchInput { cancel() }
        compose.mainClock.autoAdvance = true
        compose.runOnIdle {
            assertEquals(listOf(true, false), events)
            assertEquals("Cancelled drag must not save its order", SelectionMenuSettings(), current())
        }
    }

    @Test fun settingsScreenResetsToDefaultsAndSavesImmediately() {
        var saved: SelectionMenuSettings? = null
        var backs = 0
        val start = SelectionMenuSettings().moveBy(SelectionMenuAction.COPY, -7).withVisibility(SelectionMenuAction.NOTE, false)
        compose.setContent {
            EBookReaderTheme(appTheme = "liquid_glass", darkTheme = true) {
                SelectionMenuSettingsScreen(saved ?: start, { saved = it }, { backs++ })
            }
        }
        compose.onNodeWithText(text(R.string.selection_menu_settings)).assertExists()
        compose.onNodeWithText(text(R.string.highlight_reset_default)).performClick()
        compose.runOnIdle { assertEquals(SelectionMenuSettings(), saved) }
        compose.onNodeWithContentDescription(text(R.string.back)).performClick()
        compose.runOnIdle { assertEquals(1, backs) }
    }

    @Test fun readerSheetSavesOnlyOnConfirm() {
        var visible by mutableStateOf(true)
        var saved: SelectionMenuSettings? = null
        var dismissals = 0
        compose.setContent {
            EBookReaderTheme(appTheme = "lumi", darkTheme = false) {
                CompositionLocalProvider(LocalMotionEnabled provides false) {
                    SelectionMenuSettingsDialog(
                        visible = visible,
                        currentItems = SelectionMenuSettings(),
                        onDismiss = { dismissals++; visible = false },
                        onSave = { saved = it; visible = false }
                    )
                }
            }
        }
        switchFor(SelectionMenuAction.NOTE).performClick()
        compose.onNodeWithContentDescription(text(R.string.close)).performClick()
        compose.runOnIdle { assertEquals(1, dismissals); assertNull(saved) }

        compose.runOnIdle { visible = true }
        switchFor(SelectionMenuAction.NOTE).performClick()
        compose.onNodeWithText(text(R.string.confirm)).performClick()
        compose.runOnIdle {
            assertEquals(1, dismissals)
            assertEquals(SelectionMenuSettings().withVisibility(SelectionMenuAction.NOTE, false), saved)
        }
    }
}
