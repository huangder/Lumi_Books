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
    private lateinit var view: View
    private var plays = 0
    private var previous = 0
    private var next = 0
    private var stops = 0
    private var fullscreens = 0
    private val position = mutableStateOf(ReaderTtsPosition())
    private val footerTop = mutableStateOf<Float?>(580f)
    private val catalogBounds = mutableStateOf(Rect(24f, 440f, 336f, 488f))
    private val revealKey = mutableStateOf(0)

    private fun label(id: Int) = RuntimeEnvironment.getApplication().getString(id)
    private fun control(id: Int) = compose.onNodeWithContentDescription(label(id))
    private fun more(id: Int) = control(id).performSemanticsAction(SemanticsActions.OnClick) { it() }

    private fun show(glass: Boolean = false, dark: Boolean = false, motion: Boolean = false,
        floating: Boolean = true, eInk: Boolean = false, width: Int = 360) {
        compose.setContent {
            EBookReaderTheme(appTheme = if (glass) "liquid_glass" else "lumi", darkTheme = dark) {
                CompositionLocalProvider(
                    LocalMotionEnabled provides motion,
                    LocalEInkMode provides eInk,
                    LocalAppTheme provides if (glass) "liquid_glass" else "lumi",
                    LocalLiquidGlassCapability provides LiquidGlassCapability(true, false)
                ) {
                    view = LocalView.current
                    Box(Modifier.width(width.dp).fillMaxHeight().background(if (dark) Color(0xFF242424) else Color(0xFFE5E8EA))) {
                        val panel: @Composable (ReaderTtsPlacement?) -> Unit = { placement ->
                            TtsPlayerPanel(
                                placement = placement,
                                chapterTitle = "Chapter one",
                                playbackState = playback.value,
                                speechRate = 1f, speechRateMode = TtsProsodyMode.OVERRIDE,
                                pitch = 1f, pitchMode = TtsProsodyMode.OVERRIDE, usesAndroidTts = true,
                                sleepTimerRemainingMs = null,
                                onPlayPause = { plays++ }, onStop = { stops++ },
                                onOpenFullscreen = { fullscreens++ },
                                onSkipForward = { next++ }, onSkipBackward = { previous++ },
                                onRateChange = {}, onRateModeChange = {}, onPitchChange = {}, onPitchModeChange = {},
                                onSetSleepTimer = {}, onCancelSleepTimer = {},
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
