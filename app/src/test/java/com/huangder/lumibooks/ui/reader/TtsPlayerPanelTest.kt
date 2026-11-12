package com.huangder.lumibooks.ui.reader

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsProperties
import com.huangder.lumibooks.tts.ReaderTtsPosition
import com.huangder.lumibooks.tts.ReaderTtsDock
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.huangder.lumibooks.R
import com.huangder.lumibooks.tts.TtsPlaybackState
import com.huangder.lumibooks.tts.TtsProsodyMode
import com.huangder.lumibooks.ui.theme.*
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "en-w360dp-h640dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TtsPlayerPanelTest {
    @get:Rule val compose = createComposeRule()
    private val readerMenu = mutableStateOf(false)
    private val playback = mutableStateOf(TtsPlaybackState.PAUSED)
    private val followPageTurns = mutableStateOf(false)
    private val fromPagePrompt = mutableStateOf(false)
    private var startsFromPage = 0
    private val chapterTitle = mutableStateOf("Chapter one")
    private lateinit var view: View
    private var plays = 0
    private var previous = 0
    private var next = 0
    private var stops = 0
    private var fullscreens = 0
    private var selectedRate: Float? = null
    private var selectedTimer: Int? = null
    private lateinit var menus: com.huangder.lumibooks.ui.components.LiquidGlassMenuHostState
    private val position = mutableStateOf(ReaderTtsPosition())
    private val footerTop = mutableStateOf<Float?>(580f)
    private val catalogBounds = mutableStateOf(Rect(24f, 440f, 336f, 488f))
    private val revealKey = mutableStateOf(0)

    private fun label(id: Int) = RuntimeEnvironment.getApplication().getString(id)
    private fun control(id: Int) = when (id) {
        R.string.tts_return_to_reading_page -> compose.onNodeWithText(label(R.string.tts_return_to_progress))
        R.string.tts_from_this_page, R.string.tts_speech_rate, R.string.tts_pitch_label,
        R.string.tts_timer_label, R.string.tts_follow_page_turns -> compose.onNodeWithText(label(id))
        else -> compose.onNodeWithContentDescription(label(id))
    }
    private fun more(id: Int) = control(id).performSemanticsAction(SemanticsActions.OnClick) { it() }

    private fun show(glass: Boolean = false, dark: Boolean = false, motion: Boolean = false,
        floating: Boolean = true, eInk: Boolean = false, width: Int = 360, androidTts: Boolean = true) {
        compose.setContent {
            EBookReaderTheme(appTheme = if (glass) "liquid_glass" else "lumi", darkTheme = dark) {
                CompositionLocalProvider(
                    LocalMotionEnabled provides motion,
                    LocalEInkMode provides eInk,
                    LocalAppTheme provides if (glass) "liquid_glass" else "lumi",
                    LocalLiquidGlassCapability provides LiquidGlassCapability(true, false)
                ) {
                    view = LocalView.current
                    com.huangder.lumibooks.ui.components.LiquidGlassMenuHost(Modifier.width(width.dp).fillMaxHeight()) {
                    menus = com.huangder.lumibooks.ui.components.LocalLiquidGlassMenuHost.current!!
                    Box(Modifier.width(width.dp).fillMaxHeight().background(if (dark) Color(0xFF242424) else Color(0xFFE5E8EA))) {
                        val panel: @Composable (ReaderTtsPlacement?) -> Unit = { placement ->
                            TtsPlayerPanel(
                                placement = placement,
                                chapterTitle = chapterTitle.value,
                                playbackState = playback.value,
                                speechRate = 1f, speechRateMode = TtsProsodyMode.OVERRIDE,
                                pitch = 1f, pitchMode = TtsProsodyMode.OVERRIDE, usesAndroidTts = androidTts,
                                sleepTimerRemainingMs = null,
                                followUserPageTurns = followPageTurns.value,
                                onFollowUserPageTurnsChange = { followPageTurns.value = it },
                                showFromCurrentPage = fromPagePrompt.value,
                                onStartFromCurrentPage = { startsFromPage++; fromPagePrompt.value = false },
                                onPlayPause = { plays++ }, onStop = { stops++ },
                                onOpenFullscreen = { fullscreens++ },
                                onSkipForward = { next++ }, onSkipBackward = { previous++ },
                                onRateChange = { selectedRate = it }, onRateModeChange = {}, onPitchChange = {}, onPitchModeChange = {},
                                onSetSleepTimer = { selectedTimer = it }, onCancelSleepTimer = {},
                                canReturnToProgress = true, readerMenuVisible = readerMenu.value,
                                readerBackgroundColor = if (dark) Color.Black else Color.White,
                                readerContentColor = if (dark) Color.White else Color.Black
                            )
                        }
                        if (floating) {
                            ReaderTtsFloatingHost(true, readerMenu.value, position.value,
                                { position.value = it }, footerTop.value,
                                catalogBoundsInWindow = catalogBounds.value, revealKey = revealKey.value) { panel(it) }
                        } else TtsPlayerOverlay(true,
                            Modifier.align(Alignment.BottomStart).padding(horizontal = 24.dp, vertical = 24.dp)) {
                            panel(null)
                        }
                    }
                    }
                }
            }
        }
    }

    @Test fun pageFollowCheckUsesLiveStateAndSurvivesReaderPageChanges() {
        show()
        more(R.string.tts_expand_controls)
        control(R.string.tts_follow_page_turns).assertIsNotSelected()
        // The host retains the menu spec while reader/controller state changes.
        compose.runOnIdle { followPageTurns.value = true }
        control(R.string.tts_follow_page_turns).assertIsSelected()
        compose.runOnIdle { menus.dismiss() }
        compose.runOnIdle { chapterTitle.value = "Chapter two" }
        more(R.string.tts_expand_controls)
        control(R.string.tts_follow_page_turns).assertIsSelected().performClick()
        compose.runOnIdle { assertFalse(followPageTurns.value) }
        more(R.string.tts_expand_controls)
        control(R.string.tts_follow_page_turns).assertIsNotSelected()
    }

    @Test fun fromPagePromptOpensInwardAndLeavesTheDockedHandleInPlace() {
        show(width = 320)
        for (dock in listOf(ReaderTtsDock.LEFT, ReaderTtsDock.RIGHT)) {
            compose.runOnIdle {
                position.value = ReaderTtsPosition(if (dock == ReaderTtsDock.LEFT) 0f else 1f,
                    .5f, dock = dock, saved = true)
                fromPagePrompt.value = true
            }
            val handle = compose.onNodeWithTag("reader-tts-edge-handle")
            val button = compose.onNodeWithTag("reader-tts-from-current-page")
            val handleBounds = handle.fetchSemanticsNode().boundsInRoot
            val buttonBounds = button.fetchSemanticsNode().boundsInRoot
            assertEquals(handleBounds.center.y, buttonBounds.center.y, .01f)
            assertTrue(if (dock == ReaderTtsDock.LEFT) buttonBounds.left > handleBounds.right
                else buttonBounds.right < handleBounds.left)
            button.assertIsDisplayed().performClick()
            handle.assertIsDisplayed()
            button.assertDoesNotExist()
        }
        assertEquals(2, startsFromPage)
    }

    @Test fun compactControlsExpandOnDemandAndKeepPlaybackActions() {
        show()
        control(R.string.tts_from_this_page).assertDoesNotExist()
        control(R.string.tts_fullscreen).assertIsDisplayed().assertIsEnabled()
        control(R.string.tts_fullscreen).performClick()
        compose.runOnIdle { assertEquals(1, fullscreens) }
        control(R.string.tts_play).performClick()
        compose.runOnIdle { assertEquals(1, plays) }
        val before = control(R.string.tts_play).fetchSemanticsNode().boundsInRoot
        capture("compact")
        more(R.string.tts_expand_controls)
        control(R.string.tts_from_this_page).assertIsDisplayed()
        assertEquals(before, control(R.string.tts_play).fetchSemanticsNode().boundsInRoot)
        capture("expanded")
        val playY = before.center.y
        listOf(R.string.tts_previous_sentence, R.string.tts_next_sentence, R.string.tts_stop).forEach {
            control(it).assertIsDisplayed()
            val bounds = control(it).fetchSemanticsNode().boundsInRoot
            assertEquals(playY, bounds.center.y, .01f)
            assertEquals("Transport buttons must stay circular", bounds.width, bounds.height, .01f)
            assertEquals("Transport buttons must match the capsule height", 48f, bounds.height, .01f)
            control(it).performClick()
        }
        compose.runOnIdle {
            assertEquals(1, previous)
            assertEquals(1, next)
            assertEquals(1, stops)
        }
        control(R.string.tts_expand_controls).assertIsDisplayed()
        control(R.string.tts_from_this_page).assertDoesNotExist()
        more(R.string.tts_expand_controls)
        control(R.string.tts_return_to_reading_page).assertIsDisplayed()
    }

    @Test fun moreDoubleTapStopsOnceWithoutExpandingAndHasAccessibleExit() {
        show(floating = true)
        control(R.string.tts_expand_controls).performTouchInput { doubleClick() }
        compose.waitForIdle()
        assertEquals(1, stops)
        control(R.string.tts_from_this_page).assertDoesNotExist()
        val actions = control(R.string.tts_expand_controls).fetchSemanticsNode().config[SemanticsActions.CustomActions]
        assertTrue(actions.any { it.label == label(R.string.tts_stop) })
    }

    @Test fun footerMovesCapsuleUpAndRightSideActionsExpandLeftWithoutScrolling() {
        position.value = ReaderTtsPosition(1f, 1f, saved = true)
        show(floating = true)
        val before = compose.onNodeWithTag("reader-tts-capsule").fetchSemanticsNode().boundsInRoot
        assertTrue(before.bottom <= 568f)
        more(R.string.tts_expand_controls)
        val previousBounds = control(R.string.tts_previous_sentence).fetchSemanticsNode().boundsInRoot
        val capsule = compose.onNodeWithTag("reader-tts-capsule").fetchSemanticsNode().boundsInRoot
        assertTrue(previousBounds.right <= capsule.left)
        assertTrue(previousBounds.left >= 0f)
        capture("floating-expanded-left")
        compose.runOnIdle { footerTop.value = 480f }
        compose.waitForIdle()
        val moved = compose.onNodeWithTag("reader-tts-capsule").fetchSemanticsNode().boundsInRoot
        assertTrue(moved.bottom <= 468f)
        assertEquals(before.left, moved.left, .01f)
    }

    @Test fun dragDoesNotClickAndDockedHandleRestoresOnBothSides() {
        show(floating = true, motion = true)
        val more = control(R.string.tts_expand_controls)
        more.performTouchInput { swipe(center, center + Offset(100f, -100f), 500) }
        compose.waitForIdle()
        assertEquals(0, plays)
        assertEquals(0, stops)
        assertTrue(position.value.saved)
        control(R.string.tts_from_this_page).assertDoesNotExist()
        for (side in listOf(ReaderTtsDock.LEFT, ReaderTtsDock.RIGHT)) {
            compose.runOnIdle { position.value = ReaderTtsPosition(if (side == ReaderTtsDock.LEFT) 0f else 1f, .5f, side, true) }
            compose.waitForIdle()
            compose.onNodeWithTag("reader-tts-edge-handle").assertIsDisplayed()
            val handleBounds = compose.onNodeWithTag("reader-tts-edge-handle").fetchSemanticsNode().boundsInRoot
            assertEquals("Docked handle must reach its screen edge",
                if (side == ReaderTtsDock.LEFT) 0f else 360f,
                if (side == ReaderTtsDock.LEFT) handleBounds.left else handleBounds.right, .01f)
            capture("handle-${side.name.lowercase()}")
            compose.onNodeWithTag("reader-tts-edge-handle").performTouchInput {
                swipe(center, center + Offset(if (side == ReaderTtsDock.LEFT) 80f else -80f, -60f), 500)
            }
            compose.waitForIdle()
            compose.onNodeWithTag("reader-tts-edge-handle").assertIsDisplayed()
            control(R.string.tts_play).assertDoesNotExist()
            assertTrue(position.value.dock != ReaderTtsDock.NONE)
            assertTrue(position.value.xFraction > 0f && position.value.xFraction < 1f)
            control(R.string.tts_restore_reader_controls).performClick()
            compose.waitForIdle()
            assertEquals(ReaderTtsDock.NONE, position.value.dock)
            control(R.string.tts_expand_controls).assertIsDisplayed()
        }
    }

    @Test fun realSingleTapWaitsForDoubleTapWindowAndEInkMenuHandoffStillWorks() {
        show(floating = true, eInk = true)
        compose.mainClock.autoAdvance = false
        control(R.string.tts_expand_controls).performTouchInput { click() }
        compose.waitForIdle()
        control(R.string.tts_from_this_page).assertDoesNotExist()
        advance(600)
        control(R.string.tts_previous_sentence).assertIsDisplayed()
        compose.runOnIdle { readerMenu.value = true }
        advance(600)
        control(R.string.tts_play).assertIsDisplayed()
        control(R.string.tts_previous_sentence).assertDoesNotExist()
        compose.runOnIdle { readerMenu.value = false }
        advance(600)
        control(R.string.tts_expand_controls).assertIsDisplayed()
        control(R.string.tts_previous_sentence).assertDoesNotExist()
        compose.mainClock.autoAdvance = true
    }

    @Test fun narrowWindowKeepsAllThreeActionsVisibleAtMiddlePosition() {
        position.value = ReaderTtsPosition(.5f, .5f, saved = true)
        show(floating = true, width = 320)
        more(R.string.tts_expand_controls)
        listOf(R.string.tts_previous_sentence, R.string.tts_next_sentence, R.string.tts_stop).forEach {
            control(it).assertIsDisplayed()
            val bounds = control(it).fetchSemanticsNode().boundsInRoot
            assertTrue(bounds.left >= 0 && bounds.right <= 320f)
        }
        capture("narrow-expanded")
    }

    @Test fun fullscreenUsesPlaybackInkAndPressDoesNotAddAnOverlay() {
        show(floating = true)
        val fullscreen = control(R.string.tts_fullscreen).fetchSemanticsNode().boundsInRoot
        val frame = frame()
        var matchingInk = 0
        for (y in fullscreen.top.toInt() until fullscreen.bottom.toInt()) {
            for (x in fullscreen.left.toInt() until fullscreen.right.toInt()) {
                if (frame.getPixel(x, y) == 0xFF332A24.toInt()) matchingInk++
            }
        }
        frame.recycle()
        assertTrue("Fullscreen arrow must use the same dark ink as playback", matchingInk > 0)
        for (id in listOf(R.string.tts_play, R.string.tts_fullscreen, R.string.tts_expand_controls)) {
            val before = frame()
            control(id).performTouchInput { down(center) }
            compose.mainClock.advanceTimeBy(120)
            compose.waitForIdle()
            val pressed = frame()
            assertTrue("Pressed controls must not draw a dark overlay", before.sameAs(pressed))
            control(id).performTouchInput { up() }
            before.recycle()
            pressed.recycle()
        }
    }

    @Test fun glassMenuMovesOnlyCapsuleAboveCatalogAndReturnsToSavedPosition() {
        show(glass = true)
        val before = compose.onNodeWithTag("reader-tts-capsule").fetchSemanticsNode().boundsInRoot
        val saved = position.value
        capture("glass-compact")
        more(R.string.tts_expand_controls)
        capture("glass-expanded")
        compose.runOnIdle { readerMenu.value = true }
        control(R.string.tts_play).assertIsDisplayed()
        control(R.string.tts_from_this_page).assertDoesNotExist()
        control(R.string.tts_previous_sentence).assertDoesNotExist()
        val moved = compose.onNodeWithTag("reader-tts-capsule").fetchSemanticsNode().boundsInRoot
        assertEquals(catalogBounds.value.left, moved.left, .01f)
        assertEquals(catalogBounds.value.top - 12f, moved.bottom, .01f)
        assertEquals(saved, position.value)
        capture("glass-menu-capsule")
        more(R.string.tts_expand_controls)
        control(R.string.tts_from_this_page).assertDoesNotExist()
        compose.runOnIdle { readerMenu.value = false }
        control(R.string.tts_expand_controls).assertIsDisplayed()
        control(R.string.tts_from_this_page).assertDoesNotExist()
        assertEquals(before, compose.onNodeWithTag("reader-tts-capsule").fetchSemanticsNode().boundsInRoot)
    }

    @Test fun menuLeavesAnAlreadyHigherCapsuleAtItsRememberedCoordinates() {
        position.value = ReaderTtsPosition(.95f, .2f, saved = true)
        show(motion = true)
        val before = compose.onNodeWithTag("reader-tts-capsule").fetchSemanticsNode().boundsInRoot
        val saved = position.value
        // Even a gap smaller than the usual 12dp is already above the catalog.
        for (gap in listOf(0f, 6f, 80f)) {
            compose.runOnIdle {
                catalogBounds.value = Rect(24f, before.bottom + gap, 336f, before.bottom + gap + 48f)
                readerMenu.value = true
            }
            val during = compose.onNodeWithTag("reader-tts-capsule").fetchSemanticsNode().boundsInRoot
            assertEquals(before, during)
            assertEquals(saved, position.value)
            control(R.string.tts_previous_sentence).assertDoesNotExist()
            compose.runOnIdle { readerMenu.value = false }
            assertEquals(before, compose.onNodeWithTag("reader-tts-capsule").fetchSemanticsNode().boundsInRoot)
        }
    }

    @Test fun menuCopyFadesAtItsOwnAnchorAndOriginalReturnsOnlyAfterMenuExit() {
        position.value = ReaderTtsPosition(.85f, 1f, saved = true)
        show(motion = true)
        val original = compose.onNodeWithTag("reader-tts-capsule").fetchSemanticsNode().boundsInRoot
        val saved = position.value
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { readerMenu.value = true }
        advance(64)
        val capsules = compose.onAllNodesWithTag("reader-tts-capsule").fetchSemanticsNodes()
        assertEquals("Opening crossfades two separate capsules", 2, capsules.size)
        assertTrue("The original must fade in place", capsules.any { it.boundsInRoot == original })
        val copy = capsules.single { it.boundsInRoot != original }.boundsInRoot
        assertEquals("The copy must start at the catalog's x, without flying diagonally",
            catalogBounds.value.left, copy.left, .01f)
        advance(500)
        compose.onNodeWithTag("reader-tts-original-overlay").assertDoesNotExist()
        compose.onNodeWithTag("reader-tts-menu-overlay").assertIsDisplayed()
        compose.runOnIdle { readerMenu.value = false }
        advance(64)
        compose.onNodeWithTag("reader-tts-original-overlay").assertDoesNotExist()
        val exiting = compose.onNodeWithTag("reader-tts-capsule").fetchSemanticsNode().boundsInRoot
        assertEquals(catalogBounds.value.left, exiting.left, .01f)
        advance(128)
        compose.onNodeWithTag("reader-tts-menu-overlay").assertDoesNotExist()
        val returning = compose.onNodeWithTag("reader-tts-capsule").fetchSemanticsNode().boundsInRoot
        assertEquals(original.left, returning.left, .01f)
        assertTrue("The original should fade in while moving down from slightly above",
            returning.top < original.top && returning.top >= original.top - 12f)
        capture("original-return-downward")
        advance(500)
        compose.onNodeWithTag("reader-tts-menu-overlay").assertDoesNotExist()
        assertEquals(original, compose.onNodeWithTag("reader-tts-capsule").fetchSemanticsNode().boundsInRoot)
        assertEquals(saved, position.value)
        compose.mainClock.autoAdvance = true
    }

    @Test fun reopeningDuringMenuExitDoesNotFlashOriginalCapsule() {
        show(motion = true)
        compose.runOnIdle { readerMenu.value = true }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { readerMenu.value = false }
        advance(64)
        compose.runOnIdle { readerMenu.value = true }
        advance(64)
        compose.onNodeWithTag("reader-tts-original-overlay").assertDoesNotExist()
        advance(500)
        compose.onNodeWithTag("reader-tts-menu-overlay").assertIsDisplayed()
        compose.onAllNodesWithTag("reader-tts-capsule").assertCountEquals(1)
        compose.mainClock.autoAdvance = true
    }

    @Test fun animatedMenuHandoffAndRapidToggleFinishInTheRequestedState() {
        show(dark = true, motion = true)
        compose.waitForIdle()
        capture("dark-compact")
        more(R.string.tts_expand_controls)
        control(R.string.tts_from_this_page).assertIsDisplayed()
        more(R.string.tts_collapse_controls)
        control(R.string.tts_from_this_page).assertDoesNotExist()
        compose.mainClock.autoAdvance = false
        control(R.string.tts_expand_controls).performSemanticsAction(SemanticsActions.OnClick) { it() }
        advance(112)
        capture("enter-112ms")
        control(R.string.tts_collapse_controls).performSemanticsAction(SemanticsActions.OnClick) { it() }
        advance(64)
        control(R.string.tts_expand_controls).performSemanticsAction(SemanticsActions.OnClick) { it() }
        advance(600)
        control(R.string.tts_from_this_page).assertIsDisplayed()
        compose.runOnIdle { readerMenu.value = true }
        advance(600)
        control(R.string.tts_play).assertIsDisplayed()
        control(R.string.tts_from_this_page).assertDoesNotExist()
        compose.runOnIdle { readerMenu.value = false }
        advance(600)
        control(R.string.tts_expand_controls).assertIsDisplayed()
        control(R.string.tts_from_this_page).assertDoesNotExist()
        compose.mainClock.autoAdvance = true
    }

    @Test fun restoringRightHandleFadesAFullWidthCapsuleWithoutSqueezingButtons() {
        position.value = ReaderTtsPosition(1f, .5f, ReaderTtsDock.RIGHT, true)
        show(motion = true)
        compose.mainClock.autoAdvance = false
        control(R.string.tts_restore_reader_controls).performSemanticsAction(SemanticsActions.OnClick) { it() }
        advance(64)
        val capsule = compose.onNodeWithTag("reader-tts-capsule").fetchSemanticsNode().boundsInRoot
        assertEquals(128f, capsule.width, .01f)
        assertTrue(capsule.left >= 0f && capsule.right <= 360f)
        assertTrue("Right restore must reveal the capsule from its final right-side position", capsule.left > 180f)
        for (id in listOf(R.string.tts_play, R.string.tts_fullscreen, R.string.tts_expand_controls)) {
            val button = control(id).fetchSemanticsNode().boundsInRoot
            assertEquals(40f, button.width, .01f)
            assertTrue(button.left >= capsule.left + 4f && button.right <= capsule.right - 4f)
        }
        capture("right-restore-64ms")
        advance(400)
        compose.onNodeWithTag("reader-tts-edge-handle").assertDoesNotExist()
        compose.mainClock.autoAdvance = true
    }

    @Test fun listenButtonRevealsSavedHandleButMenuHandoffDoesNotOverwriteIt() {
        position.value = ReaderTtsPosition(0f, .5f, ReaderTtsDock.LEFT, true)
        show(motion = true)
        val saved = position.value
        compose.runOnIdle { readerMenu.value = true }
        control(R.string.tts_play).assertIsDisplayed()
        compose.onNodeWithTag("reader-tts-edge-handle").assertDoesNotExist()
        assertEquals(saved, position.value)
        compose.runOnIdle { readerMenu.value = false }
        compose.onNodeWithTag("reader-tts-edge-handle").assertIsDisplayed()
        compose.runOnIdle { revealKey.value++ }
        control(R.string.tts_play).assertIsDisplayed()
        compose.onNodeWithTag("reader-tts-edge-handle").assertDoesNotExist()
        assertEquals(ReaderTtsDock.NONE, position.value.dock)
    }

    @Test fun rateAndTimerAreNestedMenusAndPlaybackStaysUsable() {
        show()
        more(R.string.tts_expand_controls)
        val capsule = compose.onNodeWithTag("reader-tts-capsule").fetchSemanticsNode().boundsInRoot
        val menu = compose.onNodeWithTag("menu-primary").fetchSemanticsNode().boundsInRoot
        assertTrue("Menu must avoid the playback capsule", !menu.overlaps(capsule))
        capture("menu-primary")
        control(R.string.tts_speech_rate).performClick()
        capture("menu-rate")
        compose.onNodeWithText("1.25x").assertIsDisplayed().performClick()
        assertEquals(1.25f, selectedRate!!, .001f)
        control(R.string.tts_from_this_page).assertDoesNotExist()
        more(R.string.tts_expand_controls)
        control(R.string.tts_timer_label).performClick()
        capture("menu-timer")
        control(R.string.tts_play).performClick()
        assertEquals(1, plays)
        compose.onNodeWithTag("menu-submenu").assertExists()
    }

    @Test fun menusReadTheLiveAnchorAfterMovingTheCapsule() {
        show()
        for (saved in listOf(ReaderTtsPosition(.1f, .1f, saved = true),
                ReaderTtsPosition(.95f, .1f, saved = true), ReaderTtsPosition(.1f, .95f, saved = true),
                ReaderTtsPosition(.95f, .95f, saved = true))) {
            compose.runOnIdle { position.value = saved }
            more(R.string.tts_expand_controls)
            val capsule = compose.onNodeWithTag("reader-tts-capsule").fetchSemanticsNode().boundsInRoot
            val menu = compose.onNodeWithTag("menu-primary").fetchSemanticsNode().boundsInRoot
            assertFalse("Menu cannot cover playback controls", menu.overlaps(capsule))
            assertTrue(menu.left >= 0f && menu.right <= 360f)
            if (saved.yFraction < .5f) assertTrue(menu.top >= capsule.bottom)
            else assertTrue(menu.bottom <= capsule.top)
            more(R.string.tts_collapse_controls)
        }
    }

    @Test fun narrowNestedMenuAvoidsCapsuleAndExternalTtsDisablesPitch() {
        position.value = ReaderTtsPosition(.95f, .5f, saved = true)
        show(width = 320, androidTts = false)
        more(R.string.tts_expand_controls)
        control(R.string.tts_pitch_label).assertIsNotEnabled()
        control(R.string.tts_timer_label).performClick()
        val capsule = compose.onNodeWithTag("reader-tts-capsule").fetchSemanticsNode().boundsInRoot
        val child = compose.onNodeWithTag("menu-submenu").fetchSemanticsNode().boundsInRoot
        assertTrue(child.left >= 0f && child.right <= 320f)
        assertFalse(child.overlaps(capsule))
        capture("narrow-nested")
        compose.onNodeWithText(RuntimeEnvironment.getApplication().getString(R.string.time_minutes, 10)).performClick()
        assertEquals(10, selectedTimer)
    }

    @Test fun nestedAnimationFramesCoverOpeningReturningAndDismissingTogether() {
        show(motion = true)
        compose.mainClock.autoAdvance = false
        more(R.string.tts_expand_controls)
        advance(1200)
        val primary = compose.onNodeWithTag("menu-primary").fetchSemanticsNode().boundsInRoot
        val rendered = frame()
        assertTrue("A menu opened before the source was drawn must still render",
            rendered.getPixel((primary.left + 10f).toInt(), (primary.top + 50f).toInt()) != 0xFFE5E8EA.toInt())
        rendered.recycle()
        capture("nested-primary")
        control(R.string.tts_speech_rate).performSemanticsAction(SemanticsActions.OnClick) { it() }
        for (index in 0..5) {
            advance(80)
            capture("nested-open-$index")
        }
        compose.runOnIdle { menus.back() }
        for (index in 0..4) {
            advance(64)
            capture("nested-return-$index")
        }
        control(R.string.tts_speech_rate).performSemanticsAction(SemanticsActions.OnClick) { it() }
        advance(800)
        compose.runOnIdle { menus.dismiss() }
        for (index in 0..7) {
            advance(64)
            capture("nested-dismiss-$index")
        }
        advance(1000)
        compose.runOnIdle { assertEquals(null, menus.displayedMenu) }
        compose.mainClock.autoAdvance = true
    }

    private fun advance(milliseconds: Long) {
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(milliseconds)
        compose.waitForIdle()
    }

    private fun frame(): Bitmap {
        lateinit var result: Bitmap
        compose.runOnIdle {
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            result = bitmap
        }
        return result
    }

    private fun capture(name: String) {
        val bitmap = frame()
        compose.runOnIdle {
            val file = File("build/reports/tts-controls/$name.png")
            file.parentFile?.mkdirs()
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            assertTrue(bitmap.width > 0 && bitmap.height > 0)
            bitmap.recycle()
        }
    }
}
