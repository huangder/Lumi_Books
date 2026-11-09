package com.huangder.lumibooks.ui.reader

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import com.huangder.lumibooks.R
import com.huangder.lumibooks.tts.*
import com.huangder.lumibooks.ui.components.LiquidGlassMenuHost
import com.huangder.lumibooks.ui.components.LocalLiquidGlassMenuHost
import com.huangder.lumibooks.ui.components.LiquidGlassMenuHostState
import com.huangder.lumibooks.ui.components.LocalPredictiveBackEnabled
import com.huangder.lumibooks.ui.theme.*
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.junit.Assert.*
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
class FullScreenTtsScreenTest {
    @get:Rule val compose = createComposeRule()
    private val chapter = TtsChapterSnapshot(0, 3,
        "一句话读完，下一句话依次跟上来。每一行带着轻轻的弹性，进入焦点。后面的句子依次跟随，保持自然的节奏。现在可以双击任何一句，从这里继续听。")
    private val initialText = listeningText(chapter, TtsPageContent(TtsPageLocation(0, 0), chapter.text, null, null), null)
    private val state = mutableStateOf(ListeningScreenState(
        title = "工程数学、复变函数、积分变换与场论", author = "许孟",
        cover = System.getenv("LUMI_TTS_REFERENCE_COVER"), text = initialText))
    private var exits = 0
    private var plays = 0
    private var seeks = 0
    private var progressSeeks = 0
    private var settingsOpened = 0
    private var lastProgress = -1f
    private lateinit var view: View
    private lateinit var dispatcher: OnBackPressedDispatcher
    private lateinit var menus: LiquidGlassMenuHostState
    private fun control(id: Int) = compose.onNodeWithContentDescription(RuntimeEnvironment.getApplication().getString(id))
    private fun show(animated: Boolean = false, entered: Boolean = true, ended: State<Boolean>? = null) {
        compose.setContent {
            EBookReaderTheme(darkTheme = true) {
                CompositionLocalProvider(LocalMotionEnabled provides animated, LocalPredictiveBackEnabled provides false) {
                    view = LocalView.current
                    dispatcher = LocalOnBackPressedDispatcherOwner.current!!.onBackPressedDispatcher
                    LiquidGlassMenuHost {
                        menus = LocalLiquidGlassMenuHost.current!!
                        FullScreenTtsScreen(state.value, ListeningActions(
                            toggleMode = { state.value = state.value.copy(subtitles = !state.value.subtitles) },
                            togglePlayback = { plays++ }, seek = { seeks++ }, progress = { progressSeeks++; lastProgress = it },
                            openSettings = { settingsOpened++ }),
                            entered = entered, sessionEnded = ended?.value == true, onExit = { exits++ })
                    }
                }
            }
        }
    }
    private fun advance(ms: Long) { compose.waitForIdle(); compose.mainClock.advanceTimeBy(ms); compose.waitForIdle() }
    private fun capture(name: String) = compose.runOnIdle {
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        val file = File("build/reports/tts-fullscreen/$name.png")
        file.parentFile?.mkdirs()
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    @Test fun coverModeSubtitleModeAndControlsKeepTheirOwnTouchActions() {
        show()
        capture("cover")
        control(R.string.tts_play).performTouchInput { click() }
        control(R.string.tts_full_subtitles).performTouchInput { click() }
        compose.onNodeWithTag("sentence-${initialText.sentences.first().id}").performTouchInput { doubleClick() }
        control(R.string.tts_full_progress).performTouchInput { swipe(centerLeft + Offset(20f, 0f), centerRight - Offset(20f, 0f), 500) }
        compose.runOnIdle { assertEquals("play", 1, plays); assertEquals("sentence seek", 1, seeks); assertEquals("progress seek", 1, progressSeeks); assertEquals("exit", 0, exits) }
        capture("subtitles")
    }

    @Test fun settingsButtonIsToTheRightOfLyricsAndDoesNotSwitchDisplayMode() {
        show()
        val lyrics = control(R.string.tts_full_subtitles).fetchSemanticsNode().boundsInRoot
        val settings = control(R.string.tts_page_settings_title)
        assertTrue(settings.fetchSemanticsNode().boundsInRoot.left >= lyrics.right)
        settings.performTouchInput { click() }
        compose.runOnIdle {
            assertEquals(1, settingsOpened)
            assertFalse(state.value.subtitles)
            assertEquals(0, exits)
        }
    }

    @Test fun subtitleSeekGestureCanChangeWhileThePageRemainsOpen() {
        state.value = state.value.copy(subtitles = true)
        show()
        val sentence = compose.onNodeWithTag("sentence-${initialText.sentences.first().id}")
        sentence.performTouchInput { click() }
        compose.runOnIdle { assertEquals("default single tap only reveals controls", 0, seeks) }
        sentence.performTouchInput { doubleClick() }
        compose.runOnIdle {
            assertEquals(1, seeks)
            state.value = state.value.copy(seekGesture = SubtitleSeekGesture.SINGLE_TAP)
        }
        sentence.performTouchInput { click() }
        compose.runOnIdle { assertEquals("single tap now seeks", 2, seeks); assertEquals(0, exits) }
    }

    @Test fun smallDragAndCancelledLargeDragRestoreButCoverDragDismissesOnce() {
        show()
        val page = compose.onNodeWithTag("listening-page")
        page.performTouchInput { swipe(Offset(30f, 60f), Offset(30f, 120f), 1000) }
        compose.runOnIdle { assertEquals(0, exits) }
        page.performTouchInput {
            down(Offset(30f, 60f)); moveTo(Offset(30f, 350f), 500); cancel()
        }
        compose.runOnIdle { assertEquals(0, exits) }
        compose.onNodeWithTag("listening-cover").performTouchInput {
            swipe(Offset(width / 2f, height * .85f), Offset(width / 2f, height * .85f + 200f), 500)
        }
        compose.runOnIdle { assertEquals(1, exits) }
    }

    @Test fun idleControlsHideWithoutMovingSubtitlesAndTouchOrPauseRestoresThem() {
        state.value = state.value.copy(subtitles = true, playback = TtsPlaybackState.PLAYING)
        compose.mainClock.autoAdvance = false
        show()
        advance(32)
        val row = compose.onNodeWithTag("sentence-${initialText.sentences.first().id}")
        val before = row.fetchSemanticsNode().boundsInRoot
        val viewport = compose.onNodeWithTag("listening-subtitle-list").fetchSemanticsNode().boundsInRoot
        val playback = compose.onNodeWithTag("listening-playback").fetchSemanticsNode().boundsInRoot
        assertTrue("lyrics extend through the bottom controls area", viewport.bottom >= playback.bottom)
        advance(3100)
        control(R.string.tts_pause).assertIsNotEnabled()
        assertEquals(before, row.fetchSemanticsNode().boundsInRoot)
        assertEquals(viewport, compose.onNodeWithTag("listening-subtitle-list").fetchSemanticsNode().boundsInRoot)
        compose.onNodeWithTag("listening-page").performTouchInput { click(Offset(10f, 400f)) }
        advance(32)
        control(R.string.tts_pause).assertIsEnabled()
        compose.runOnIdle { state.value = state.value.copy(playback = TtsPlaybackState.PAUSED) }
        advance(4000)
        control(R.string.tts_play).assertIsEnabled()
        compose.mainClock.autoAdvance = true
    }

    @Test fun rapidCloseAndSessionEndFinishOnlyOnce() {
        val ended = mutableStateOf(false)
        show(ended = ended)
        control(R.string.tts_full_close).performClick()
        compose.runOnIdle { ended.value = true }
        compose.runOnIdle { assertEquals(1, exits) }
    }

    @Test fun backClosesTheSettingsMenuBeforeDismissingTheActivity() {
        show()
        control(R.string.tts_speech_rate).performTouchInput { click() }
        compose.runOnIdle { assertNotNull(menus.activeMenu); dispatcher.onBackPressed() }
        compose.runOnIdle { assertNull(menus.activeMenu); assertEquals(0, exits); dispatcher.onBackPressed() }
        compose.runOnIdle { assertEquals(1, exits) }
    }

    @Test fun cancellingProgressDragDoesNotSeekOrDismiss() {
        show()
        control(R.string.tts_full_progress).performTouchInput {
            down(centerLeft + Offset(20f, 0f)); moveTo(center, 300); cancel()
        }
        compose.runOnIdle { assertEquals(0, progressSeeks); assertEquals(0, exits) }
        control(R.string.tts_play).assertIsEnabled()
    }

    @Test fun progressTracksRelativeMovementWithoutJumpingToTheTouchPosition() {
        state.value = state.value.copy(text = initialText.copy(progress = .4f))
        show()
        val progress = control(R.string.tts_full_progress)
        progress.performTouchInput { click(Offset(width * .9f, centerY)) }
        compose.runOnIdle { assertEquals(0, progressSeeks) }
        assertEquals(.4f, progress.fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo].current, .001f)
        progress.performTouchInput { swipe(Offset(width * .9f, centerY), Offset(width * .6f, centerY), 600) }
        compose.runOnIdle {
            assertEquals(1, progressSeeks)
            assertTrue("relative seek was $lastProgress", lastProgress in .08f.. .19f)
            assertEquals(0, exits)
        }
    }

    @Test fun coverTapSwitchesModeAndCoverPlaybackControlsStayVisibleWhenIdle() {
        state.value = state.value.copy(playback = TtsPlaybackState.PLAYING)
        compose.mainClock.autoAdvance = false
        show()
        advance(4100)
        control(R.string.tts_pause).assertIsEnabled()
        compose.onNodeWithText("Speed 1x").assertExists()
        compose.onNodeWithText("Pitch 1x").assertExists()
        compose.onNodeWithTag("listening-cover").performTouchInput { click() }
        advance(32)
        control(R.string.tts_full_cover).assertExists()
        compose.onNodeWithTag("sentence-${initialText.sentences.first().id}").assertExists()
        compose.mainClock.autoAdvance = true
    }

    @Test fun browsingSubtitlesDoesNotSeekAndTheNextSentenceReturnsToTheFocus() {
        val longChapter = chapter.copy(text = (1..30).joinToString("") { "这是第${it}条字幕，读完之后下一条会跟上来。" })
        val text = listeningText(longChapter, TtsPageContent(TtsPageLocation(0, 0), longChapter.text, null, null), null)
        state.value = state.value.copy(subtitles = true, text = text)
        show(animated = true)
        val first = compose.onNodeWithTag("sentence-${text.sentences[0].id}")
        val focusTop = first.fetchSemanticsNode().boundsInRoot.top
        compose.onNodeWithTag("listening-subtitle-list").performTouchInput {
            swipe(Offset(centerX, height * .6f), Offset(centerX, height * .2f), 600)
        }
        first.assertDoesNotExist()
        compose.runOnIdle { assertEquals(0, seeks); assertEquals(0, exits) }
        compose.mainClock.autoAdvance = false
        val list = compose.onNodeWithTag("listening-subtitle-list")
        val browsedPosition = list.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
        compose.runOnIdle { state.value = state.value.copy(text = text.copy(focus = 1)) }
        advance(250)
        val returningPosition = list.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
        assertTrue("return is still moving after 250 ms", returningPosition > 1.1f)
        assertTrue("return has started", returningPosition < browsedPosition)
        advance(1200)
        val next = compose.onNodeWithTag("sentence-${text.sentences[1].id}")
        next.assertIsDisplayed()
        assertEquals(focusTop, next.fetchSemanticsNode().boundsInRoot.top, 1f)
        compose.mainClock.autoAdvance = true
        next.performTouchInput { doubleClick() }
        compose.runOnIdle { assertEquals(1, seeks) }
    }

    @Test fun changingChaptersKeepsOutgoingSubtitlesUntilTheTransitionCompletes() {
        state.value = state.value.copy(subtitles = true)
        show(animated = true)
        compose.mainClock.autoAdvance = false
        val next = initialText.copy(chapter = 1, sentences = initialText.sentences.map { it.copy(chapter = 1) })
        compose.runOnIdle { state.value = state.value.copy(text = next) }
        advance(80)
        compose.onNodeWithTag("sentence-${initialText.sentences.first().id}").assertExists()
        compose.onNodeWithTag("sentence-${next.sentences.first().id}").assertExists()
        advance(600)
        compose.onNodeWithTag("sentence-${initialText.sentences.first().id}").assertDoesNotExist()
        compose.onNodeWithTag("sentence-${next.sentences.first().id}").assertIsDisplayed()
        compose.mainClock.autoAdvance = true
    }

    @Test fun visibleProgressMatchesCapsuleEdgesAndControlsKeepScreenClearance() {
        show()
        val page = compose.onNodeWithTag("listening-page").fetchSemanticsNode().boundsInRoot
        val settings = compose.onNodeWithTag("listening-settings").fetchSemanticsNode().boundsInRoot
        val track = compose.onNodeWithTag("listening-progress-track", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val playback = compose.onNodeWithTag("listening-playback").fetchSemanticsNode().boundsInRoot
        val close = control(R.string.tts_full_close).fetchSemanticsNode().boundsInRoot
        val cover = compose.onNodeWithTag("listening-cover").fetchSemanticsNode().boundsInRoot
        val metadata = compose.onNodeWithTag("listening-metadata").fetchSemanticsNode().boundsInRoot
        assertEquals("metadata has equal space above and below", metadata.top - cover.bottom, settings.top - metadata.bottom, 1f)
        assertEquals(settings.left, track.left, .5f)
        assertEquals(settings.right, track.right, .5f)
        val clearance = with(compose.density) { 16.dp.toPx() }
        assertTrue(close.top >= page.top + clearance - .5f)
        assertTrue(playback.bottom <= page.bottom - clearance + .5f)
        assertTrue(track.bottom < playback.top)
        assertTrue(settings.bottom < track.top)
    }

    @Test fun progressFillMovesContinuouslyBetweenPlaybackUpdates() {
        state.value = state.value.copy(text = initialText.copy(progress = .2f))
        show(animated = true)
        val fill = compose.onNodeWithTag("listening-progress-fill", useUnmergedTree = true)
        val start = fill.fetchSemanticsNode().boundsInRoot.width
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { state.value = state.value.copy(text = initialText.copy(progress = .8f)) }
        advance(160)
        val moving = fill.fetchSemanticsNode().boundsInRoot.width
        advance(160)
        val later = fill.fetchSemanticsNode().boundsInRoot.width
        advance(500)
        val end = fill.fetchSemanticsNode().boundsInRoot.width
        assertTrue(start < moving && moving < later && later < end)
        assertEquals(start * 4f, end, 2f)
        compose.mainClock.autoAdvance = true
    }

    @Test fun subtitlesFollowTheShrinkingCoverFromBelow() {
        show(animated = true)
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("listening-cover").performClick()
        advance(112)
        val first = compose.onNodeWithTag("sentence-${initialText.sentences.first().id}")
        val enteringTop = first.fetchSemanticsNode().boundsInRoot.top
        advance(200)
        val movingTop = first.fetchSemanticsNode().boundsInRoot.top
        advance(500)
        val focusTop = first.fetchSemanticsNode().boundsInRoot.top
        assertTrue("lyrics enter upward after the cover", enteringTop > movingTop && movingTop > focusTop)
        assertTrue("entry starts well below the focus", enteringTop - focusTop > with(compose.density) { 60.dp.toPx() })
        compose.mainClock.autoAdvance = true
    }

    @Test fun layersEnterInOrderAndAnInterruptedExitRestoresContinuously() {
        compose.mainClock.autoAdvance = false
        val motion = ListeningMotion(false)
        lateinit var scope: CoroutineScope
        compose.setContent {
            scope = rememberCoroutineScope()
            LaunchedEffect(Unit) { motion.show(true) }
        }
        advance(16)
        assertTrue(motion.groups.all { it.value == 1f })
        advance(96)
        assertTrue(motion.background.value < .7f)
        assertTrue(motion.groups[0].value < motion.groups[4].value)
        advance(1500)
        assertTrue(motion.groups.all { kotlin.math.abs(it.value) < .001f })
        var job: kotlinx.coroutines.Job? = null
        compose.runOnIdle { job = scope.launch { motion.hide(true) } }
        advance(80)
        assertTrue(motion.groups[4].value > motion.groups[0].value)
        assertTrue("exit background must move immediately", motion.background.value > 0f)
        compose.runOnIdle { job?.cancel(); scope.launch { motion.restore(true) } }
        advance(1500)
        assertEquals(0f, motion.background.value, .001f)
        assertTrue(motion.groups.all { kotlin.math.abs(it.value) < .001f })
        compose.mainClock.autoAdvance = true
    }
}
