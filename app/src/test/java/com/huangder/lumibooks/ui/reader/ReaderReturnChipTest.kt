package com.huangder.lumibooks.ui.reader

import android.app.Application
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ReaderReturnChipTest {
    @get:Rule val compose = createComposeRule()

    @Test fun lightReturnHasNumberAndAccessibleTouchAreaWithoutVisibleReturnText() = verify(false, false, false)
    @Test fun darkHeadphoneChipJumpsOnlyWhenClicked() = verify(true, false, true)
    @Test fun eInkHeadphoneNumberUsesBlackInk() = verify(true, true, true)
    @Test fun customReaderTextColorIsPreserved() = verify(false, false, false, Color(0xFF886633))

    private fun verify(dark: Boolean, eInk: Boolean, headphones: Boolean,
        ink: Color = if (eInk || !dark) Color.Black else Color.White) {
        var clicks = 0
        val pageLabel = if (headphones) "6-50.3%" else "23"
        compose.setContent {
            ReaderReturnChip(pageLabel, ReaderReturnSide.RIGHT, headphones,
                if (dark) Color.Black else Color.White, ink,
                eInk, "target", { clicks++ })
        }
        compose.onNodeWithContentDescription("target").assertIsDisplayed()
            .assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp)
        compose.onNodeWithText("返回").assertDoesNotExist()
        compose.onNodeWithText(pageLabel, useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { getLayout ->
                val results = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
                getLayout(results)
                assertEquals(ink, results.single().layoutInput.style.color)
            }
        compose.runOnIdle { assertEquals(0, clicks) }
        compose.onNodeWithContentDescription("target").performClick()
        compose.runOnIdle { assertEquals(1, clicks) }
    }

    @Test fun clickFinishesFadingBeforeNavigationAndCannotBeRepeated() {
        compose.mainClock.autoAdvance = false
        var clicks = 0
        compose.setContent { ReaderReturnChip("23", ReaderReturnSide.LEFT, false,
            Color.White, Color.Black, false, "return", { clicks++ }) }
        compose.onNodeWithContentDescription("return").performClick()
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithContentDescription("return").assertIsNotEnabled()
        compose.mainClock.advanceTimeBy(64)
        compose.onNodeWithText("23", useUnmergedTree = true).assertExists()
        compose.runOnIdle { assertEquals(0, clicks) }
        compose.mainClock.advanceTimeBy(160)
        compose.runOnIdle { assertEquals(1, clicks) }
        compose.onNodeWithContentDescription("return").performClick()
        compose.mainClock.advanceTimeBy(200)
        compose.runOnIdle { assertEquals(1, clicks) }
    }

    @Test fun unknownNumberLeavesAClickableIcon() {
        compose.setContent { ReaderReturnChip(null, ReaderReturnSide.LEFT, true,
            Color.White, Color.Black, false, "listen", {}) }
        compose.onNodeWithContentDescription("listen").assertIsDisplayed().assertHasClickAction()
        compose.onAllNodes(hasText("23")).assertCountEquals(0)
    }

    @Test fun headphoneFadeLeavesTheNewReadingReturnEntryClickable() {
        val headphones = mutableStateOf(true)
        var jumps = 0
        var returns = 0
        compose.setContent {
            ReaderReturnChip(if (headphones.value) "12" else "34",
                if (headphones.value) ReaderReturnSide.LEFT else ReaderReturnSide.RIGHT,
                headphones.value, Color.White, Color.Black, false,
                if (headphones.value) "listen" else "return", {
                    if (headphones.value) {
                        jumps++
                        headphones.value = false
                    } else returns++
                }, entryKey = if (headphones.value) "listening" else "reading")
        }
        compose.onNodeWithContentDescription("listen").performClick()
        compose.runOnIdle { assertEquals(1, jumps) }
        compose.onNodeWithContentDescription("return").assertIsDisplayed().assertIsEnabled()
        compose.onNodeWithText("34", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithContentDescription("return").performClick()
        compose.runOnIdle { assertEquals(1, returns); assertEquals(1, jumps) }
    }

    @Test fun fifteenSecondsCountsOnlyVisibleTimeAndDisposalCancelsTheHint() {
        compose.mainClock.autoAdvance = false
        val visible = mutableStateOf(true)
        val mounted = mutableStateOf(true)
        var dismissals = 0
        compose.setContent {
            if (mounted.value) ListeningResumeHintExpiry("target", visible.value, { dismissals++ },
                elapsedMillis = { compose.mainClock.currentTime })
        }
        compose.mainClock.advanceTimeBy(10_000)
        compose.runOnIdle { visible.value = false }
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(40_000)
        compose.runOnIdle { assertEquals(0, dismissals); visible.value = true }
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(4_000)
        compose.runOnIdle { assertEquals(0, dismissals) }
        compose.mainClock.advanceTimeBy(2_000)
        compose.runOnIdle { assertEquals(1, dismissals); mounted.value = false }
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeBy(15_000)
        compose.runOnIdle { assertEquals(1, dismissals) }
    }

    @Test fun newReadingReturnGetsItsOwnFifteenSecondBudget() {
        compose.mainClock.autoAdvance = false
        val target = mutableStateOf("listening")
        var dismissals = 0
        compose.setContent {
            ListeningResumeHintExpiry(target.value, true, { dismissals++ },
                elapsedMillis = { compose.mainClock.currentTime })
        }
        compose.mainClock.advanceTimeBy(10_000)
        compose.runOnIdle { target.value = "reading" }
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(14_000)
        compose.runOnIdle { assertEquals(0, dismissals) }
        compose.mainClock.advanceTimeBy(2_000)
        compose.runOnIdle { assertEquals(1, dismissals) }
    }
}
