package com.huangder.lumibooks.ui.reader

import android.app.Application
import android.graphics.Color
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import com.huangder.lumibooks.domain.model.ReaderImageAdjustments
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.Selection
import android.text.Spannable
import android.text.style.ImageSpan
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import com.huangder.lumibooks.domain.model.ReaderTextAlignment
import com.huangder.lumibooks.ui.reader.engine.ResolvedReaderTypeface
import com.huangder.lumibooks.util.parser.EpubParser
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlin.math.roundToInt
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
class ContinuousScrollReaderTest {
    @get:Rule val compose = createComposeRule()
    private val text = (1..120).joinToString("\n") { "Paragraph $it: reading text with a stable character anchor." }
    private val targetOffset = text.indexOf("Paragraph 70:")
    private val requests = MutableStateFlow<ContinuousScrollRequest?>(null)
    private val list = LazyListState()
    private var restored = false
    private lateinit var composeRoot: View
    private var chapterCount = 3
    private var currentChapter = 1
    private var loadChapter: (Int, Int?) -> CharSequence? = { _, _ -> text }
    private val selectionController = ContinuousSelectionController()

    private fun descendants(view: View): Sequence<View> = sequence {
        yield(view)
        if (view is ViewGroup) for (i in 0 until view.childCount) yieldAll(descendants(view.getChildAt(i)))
    }

    private fun visibleTextView(): ContinuousSelectableTextView =
        descendants(composeRoot.rootView).filterIsInstance<ContinuousSelectableTextView>()
            .first { it.isAttachedToWindow && it.text.isNotEmpty() }

    private fun show(
        initialOffset: Int? = null,
        mounted: androidx.compose.runtime.State<Boolean> = mutableStateOf(true),
        comicMode: androidx.compose.runtime.State<Boolean> = mutableStateOf(false),
        imageAdjustments: androidx.compose.runtime.State<ReaderImageAdjustments> = mutableStateOf(ReaderImageAdjustments()),
        leftMargin: androidx.compose.runtime.State<Float> = mutableStateOf(16f),
        background: androidx.compose.runtime.State<Int> = mutableStateOf(Color.WHITE),
        revision: androidx.compose.runtime.State<Long> = mutableStateOf(0L),
        fontSize: androidx.compose.runtime.State<Float> = mutableStateOf(18f),
        lineHeight: Float = 1.5f,
        verticalMargin: Float = 20f,
        mirrorProgress: Boolean = false,
        onSelectionChanging: () -> Unit = {},
        onSelection: (Int, ContinuousTextSelection) -> Unit = { _, _ -> },
        onSelectionCleared: () -> Unit = {},
        onChapterVisible: (Int, Float) -> Unit = { _, _ -> }
    ) {
        val reported = mutableStateOf(currentChapter to 0f)
        compose.setContent {
            composeRoot = androidx.compose.ui.platform.LocalView.current
            Box(Modifier.fillMaxSize()) {
                if (mounted.value) ContinuousScrollReader(
                    chapterCount = chapterCount,
                    currentChapter = if (mirrorProgress) reported.value.first else currentChapter,
                    initialChapterFraction = if (mirrorProgress) reported.value.second else 0f,
                    initialCharacterOffset = initialOffset,
                    fontSize = fontSize.value, lineHeight = lineHeight, letterSpacingDp = 0f,
                    textAlignment = ReaderTextAlignment.NATURAL,
                    readerTypeface = ResolvedReaderTypeface(Typeface.DEFAULT, false),
                    textColor = Color.BLACK, backgroundColor = background.value,
                    backgroundImagePath = null, backgroundImageOpacity = 0f, backgroundImageBlurDp = 0f,
                    marginLeft = leftMargin.value, marginRight = 16f, marginTop = verticalMargin, marginBottom = verticalMargin,
                    paragraphSpacing = 0f, firstLineIndent = 0f, bionicReadingEnabled = false,
                    contentRevision = revision.value,
                    loadChapterText = { index, width -> loadChapter(index, width) },
                    onContentSizeChanged = { _, _ -> },
                    notes = emptyList(), searchHighlight = null, scrollRequests = requests,
                    onSearchHighlightFinished = {}, onMenuToggle = {}, onLinkClick = { _, _, _, _ -> },
                    onImageLongPress = { _, _ -> }, selectionController = selectionController,
                    onSelectionChanging = onSelectionChanging, onSelection = onSelection,
                    onSelectionCleared = onSelectionCleared,
                    onChapterVisible = { index, fraction, _ ->
                        if (mirrorProgress) reported.value = index to fraction
                        onChapterVisible(index, fraction)
                    },
                    onRestoreComplete = { restored = true }, onSentenceDoubleTap = { _, _ -> },
                    ttsSentenceJumpEnabled = false, comicModeEnabled = comicMode.value, listState = list,
                    imageAdjustments = imageAdjustments.value
                )
            }
        }
    }

    private fun imageChapter(vararg heights: Int): CharSequence = SpannableStringBuilder().apply {
        heights.forEach { height ->
            val start = length
            append("\uFFFC\n")
            val drawable = ColorDrawable(Color.RED).apply { setBounds(0, 0, 240, height) }
            setSpan(ImageSpan(drawable), start, start + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }

    @Test fun mixedImageRowsDoNotMultiplyImageHeightByTextLineSpacing() {
        currentChapter = 0
        chapterCount = 1
        val mixed = SpannableStringBuilder("before\n").apply {
            append(imageChapter(100))
            append("caption one\n")
            append(imageChapter(800))
            append("caption two\nend")
        }
        loadChapter = { _, _ -> mixed }
        show(lineHeight = 2f)
        compose.waitUntil(15_000) { restored }
        compose.runOnIdle {
            val layout = visibleTextView().layout
            val small = layout.getLineForOffset(mixed.indexOf('\uFFFC'))
            val large = layout.getLineForOffset(mixed.lastIndexOf('\uFFFC'))
            val smallExtra = layout.getLineTop(small + 1) - layout.getLineTop(small) - 100
            val largeExtra = layout.getLineTop(large + 1) - layout.getLineTop(large) - 800
            assertTrue("small image extra=$smallExtra", smallExtra in 0..2)
            assertTrue("large image extra=$largeExtra", largeExtra in 0..2)
            assertTrue("body still has double line spacing",
                layout.getLineTop(1) >= visibleTextView().paint.fontSpacing * 1.8f)
        }
    }

    @Test fun selectionInMixedChapterSurvivesParentRecompositionAndImageAdjustment() {
        currentChapter = 0
        chapterCount = 1
        val background = mutableStateOf(Color.WHITE)
        val settings = mutableStateOf(ReaderImageAdjustments())
        loadChapter = { _, _ -> SpannableStringBuilder(text).append(imageChapter(100)) }
        show(background = background, imageAdjustments = settings)
        compose.waitUntil(15_000) { restored }
        compose.runOnIdle {
            val view = visibleTextView()
            view.requestFocus()
            Selection.setSelection(view.text as Spannable, 4, 18)
        }
        compose.waitForIdle()
        compose.runOnIdle {
            background.value = Color.LTGRAY
            settings.value = ReaderImageAdjustments(brightness = 0.2f)
        }
        compose.waitForIdle()
        compose.runOnIdle {
            val view = visibleTextView()
            assertEquals(4, Selection.getSelectionStart(view.text))
            assertEquals(18, Selection.getSelectionEnd(view.text))
        }
    }

    @Test
    @Config(shadows = [ContinuousSelectionMagnifierShadow::class])
    fun longPressCanSelectTextAgainAfterDismissingThePreviousSelection() {
        currentChapter = 0
        chapterCount = 1
        loadChapter = { _, _ -> SpannableStringBuilder("Selectable paragraph before image.\n")
            .append(imageChapter(100)).append(text) }
        show()
        compose.waitUntil(15_000) { restored }
        var target = Offset.Zero
        compose.runOnIdle {
            val view = visibleTextView()
            val location = IntArray(2).also(view::getLocationOnScreen)
            val rootLocation = IntArray(2).also(composeRoot::getLocationOnScreen)
            target = Offset(location[0] - rootLocation[0] + view.layout.getPrimaryHorizontal(4),
                location[1] - rootLocation[1] + view.layout.getLineBottom(0) / 2f)
        }
        repeat(2) {
            // TextView's long-press timer runs on the Android looper, independently
            // of Compose's virtual input-event clock. Keep the finger down while
            // advancing that timer before injecting ACTION_UP.
            compose.onRoot().performTouchInput { down(target) }
            compose.runOnIdle {
                org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(
                    java.time.Duration.ofMillis(android.view.ViewConfiguration.getLongPressTimeout() + 100L))
            }
            compose.onRoot().performTouchInput { up() }
            compose.waitForIdle()
            compose.runOnIdle {
                val view = visibleTextView()
                assertNotNull("long press $it creates a live selection: ${Selection.getSelectionStart(view.text)}..${Selection.getSelectionEnd(view.text)} selectable=${view.isTextSelectable} controller=${selectionController.selection}", selectionController.currentSelection())
                selectionController.clear()
                assertNull(selectionController.currentSelection())
            }
        }
    }

    @Test fun revisionKeepsVisibleTextWhileReplacementDecodeIsBlocked() {
        currentChapter = 0
        chapterCount = 1
        val revision = mutableStateOf(0L)
        val gate = java.util.concurrent.CountDownLatch(1)
        val delayDecode = java.util.concurrent.atomic.AtomicBoolean(false)
        loadChapter = { _, _ ->
            if (delayDecode.get()) gate.await(10, java.util.concurrent.TimeUnit.SECONDS)
            text
        }
        show(revision = revision, initialOffset = targetOffset)
        compose.waitUntil(15_000) { restored }
        try {
            compose.runOnIdle { delayDecode.set(true); revision.value++ }
            compose.waitForIdle()
            compose.runOnIdle {
                assertTrue("keep the displayed chapter during reflow", visibleTextView().text.isNotEmpty())
                assertTrue("keep the existing viewport", list.firstVisibleItemScrollOffset > 1000)
            }
        } finally {
            gate.countDown()
        }
    }

    @Test fun parentProgressUpdatesAndReflowPreserveCurrentCharacterInsteadOfChapterStart() {
        currentChapter = 0
        chapterCount = 1
        val font = mutableStateOf(18f)
        show(initialOffset = targetOffset, fontSize = font, mirrorProgress = true)
        compose.waitUntil(15_000) { restored }
        var character = 0
        compose.runOnIdle {
            val layout = visibleTextView().layout
            character = layout.getLineStart(layout.getLineForVertical(list.firstVisibleItemScrollOffset))
            font.value = 26f
        }
        compose.waitForIdle()
        compose.runOnIdle {
            val layout = visibleTextView().layout
            val expected = layout.getLineTop(layout.getLineForOffset(character))
            assertTrue("expected=$expected actual=${list.firstVisibleItemScrollOffset}",
                kotlin.math.abs(expected - list.firstVisibleItemScrollOffset) <= 2)
        }
    }

    @Test fun focusedTextCannotPullViewportBackToItsCursorAfterRecomposition() {
        currentChapter = 0
        chapterCount = 1
        val background = mutableStateOf(Color.WHITE)
        loadChapter = { _, _ -> SpannableStringBuilder(text).append(imageChapter(100)) }
        show(initialOffset = targetOffset, background = background, mirrorProgress = true)
        compose.waitUntil(15_000) { restored }
        var offset = 0
        compose.runOnIdle {
            offset = list.firstVisibleItemScrollOffset
            val view = visibleTextView()
            view.requestFocus()
            assertFalse(view.bringPointIntoView(0))
            assertFalse(view.requestRectangleOnScreen(android.graphics.Rect(0, 0, 40, 40), true))
            view.scrollTo(0, -200)
            assertEquals("TextView must not create its own offset inside the chapter", 0, view.scrollY)
            background.value = Color.LTGRAY
        }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(offset, list.firstVisibleItemScrollOffset) }
    }

    @Test
    @Config(shadows = [ContinuousSelectionMagnifierShadow::class])
    fun draggingSelectionHandleKeepsTheListStillAndTheSelectionAlive() {
        currentChapter = 0
        chapterCount = 1
        show()
        compose.waitUntil(15_000) { restored }
        var handle = Offset.Zero
        var target = Offset.Zero
        var listOffset = 0
        compose.runOnIdle {
            val view = visibleTextView()
            view.requestFocus()
            Selection.setSelection(view.text as Spannable, 2, 3)
        }
        compose.waitForIdle()
        compose.runOnIdle {
            val view = visibleTextView()
            val location = IntArray(2).also(view::getLocationOnScreen)
            val rootLocation = IntArray(2).also(composeRoot::getLocationOnScreen)
            val point = view.readerHandleCirclePosition(3, trailing = true)!!
            handle = Offset(location[0] - rootLocation[0] + point.x,
                location[1] - rootLocation[1] + point.y)
            target = handle + Offset(80f, 45f)
            listOffset = list.firstVisibleItemScrollOffset
        }
        compose.onRoot().performTouchInput { down(handle) }
        compose.runOnIdle {
            assertEquals("overlapping handle targets must choose the nearest handle",
                false, visibleTextView().readerDraggingStartHandle)
        }
        compose.onRoot().performTouchInput {
            moveTo(target, delayMillis = 400)
            up()
        }
        compose.waitForIdle()
        compose.runOnIdle {
            val selection = selectionController.currentSelection()
            assertNotNull("handle drag must keep the live selection", selection)
            assertEquals("dragging the right handle keeps the start", 2, selection!!.start)
            assertTrue("handle drag expands the range", selection.end > 3)
            assertEquals("handle drag does not scroll the chapter", listOffset, list.firstVisibleItemScrollOffset)
        }
    }

    private fun advanceSelectionScrollFrames(count: Int = 12) {
        repeat(count) {
            compose.runOnIdle {
                org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(
                    java.time.Duration.ofMillis(16))
            }
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
        }
    }

    @Test
    @Config(shadows = [ContinuousSelectionMagnifierShadow::class])
    fun holdingSelectionHandleAtBottomKeepsScrollingAndExtendingUntilReleased() {
        currentChapter = 0
        chapterCount = 1
        // Compose clips the reader above its footer; that is the selection edge,
        // even when the embedded Android view reports a larger visible rectangle.
        var menuSelection: ContinuousTextSelection? = null
        show(verticalMargin = 64f, mirrorProgress = true,
            onSelectionChanging = { menuSelection = null },
            onSelection = { _, selection -> menuSelection = selection })
        compose.waitUntil(15_000) { restored }
        var handle = Offset.Zero
        var bottom = Offset.Zero
        compose.runOnIdle {
            val view = visibleTextView()
            view.requestFocus()
            Selection.setSelection(view.text as Spannable, 2, 3)
        }
        compose.waitForIdle()
        compose.runOnIdle {
            val view = visibleTextView()
            val location = IntArray(2).also(view::getLocationOnScreen)
            val rootLocation = IntArray(2).also(composeRoot::getLocationOnScreen)
            val point = view.readerHandleCirclePosition(3, trailing = true)!!
            handle = Offset(location[0] - rootLocation[0] + point.x,
                location[1] - rootLocation[1] + point.y)
            bottom = Offset(handle.x, composeRoot.height - with(compose.density) { 64.dp.toPx() } - 2f)
        }
        compose.onRoot().performTouchInput { down(handle); moveTo(bottom, delayMillis = 100) }
        var firstEnd = 0
        compose.runOnIdle { firstEnd = selectionController.currentSelection()!!.end }
        advanceSelectionScrollFrames()
        var firstScroll = 0
        var secondEnd = 0
        compose.runOnIdle {
            firstScroll = list.firstVisibleItemScrollOffset
            secondEnd = selectionController.currentSelection()!!.end
            assertTrue("holding the handle must scroll inside the padded viewport", firstScroll > 0)
            assertTrue("the range follows newly revealed text", secondEnd > firstEnd)
            assertEquals(2, selectionController.currentSelection()!!.start)
            assertNull("do not reopen a stale menu under the held finger", menuSelection)
        }
        // No further MOVE events: keeping the finger down must keep selecting.
        advanceSelectionScrollFrames()
        compose.runOnIdle {
            assertTrue(list.firstVisibleItemScrollOffset > firstScroll)
            assertTrue(selectionController.currentSelection()!!.end > secondEnd)
        }
        compose.onRoot().performTouchInput { up() }
        compose.waitForIdle()
        var releasedScroll = 0
        var releasedEnd = 0
        compose.runOnIdle {
            releasedScroll = list.firstVisibleItemScrollOffset
            releasedEnd = selectionController.currentSelection()!!.end
        }
        advanceSelectionScrollFrames()
        compose.runOnIdle {
            assertEquals("release stops scrolling", releasedScroll, list.firstVisibleItemScrollOffset)
            assertEquals(releasedEnd, selectionController.currentSelection()!!.end)
            assertEquals(0, visibleTextView().scrollY)
            assertEquals("menu actions use the entire extended selection",
                visibleTextView().text.subSequence(2, releasedEnd).toString(), menuSelection?.selectedText)
        }
    }

    @Test
    @Config(shadows = [ContinuousSelectionMagnifierShadow::class])
    fun holdingSelectionHandleAtTopScrollsUpAndStopsWhenLeavingTheEdgeOrCancelling() {
        currentChapter = 0
        chapterCount = 1
        show(initialOffset = targetOffset, verticalMargin = 64f, mirrorProgress = true)
        compose.waitUntil(15_000) { restored }
        var handle = Offset.Zero
        var top = Offset.Zero
        var middle = Offset.Zero
        var initialScroll = 0
        var fixedEnd = 0
        compose.runOnIdle {
            val view = visibleTextView()
            val line = view.layout.getLineForVertical(
                list.firstVisibleItemScrollOffset + list.layoutInfo.viewportSize.height / 2)
            val start = view.layout.getLineStart(line) + 2
            fixedEnd = start + 12
            view.requestFocus()
            Selection.setSelection(view.text as Spannable, start, fixedEnd)
        }
        compose.waitForIdle()
        compose.runOnIdle {
            val view = visibleTextView()
            val location = IntArray(2).also(view::getLocationOnScreen)
            val rootLocation = IntArray(2).also(composeRoot::getLocationOnScreen)
            val point = view.readerHandleCirclePosition(fixedEnd - 12, trailing = false)!!
            handle = Offset(location[0] - rootLocation[0] + point.x,
                location[1] - rootLocation[1] + point.y)
            top = Offset(handle.x, with(compose.density) { 64.dp.toPx() } + 2f)
            middle = Offset(handle.x, composeRoot.height / 2f)
            initialScroll = list.firstVisibleItemScrollOffset
        }
        compose.onRoot().performTouchInput { down(handle); moveTo(top, delayMillis = 100) }
        var firstStart = 0
        compose.runOnIdle { firstStart = selectionController.currentSelection()!!.start }
        advanceSelectionScrollFrames()
        compose.runOnIdle {
            assertTrue("top edge scrolls backwards", list.firstVisibleItemScrollOffset < initialScroll)
            assertTrue(selectionController.currentSelection()!!.start < firstStart)
            assertEquals(fixedEnd, selectionController.currentSelection()!!.end)
        }
        compose.onRoot().performTouchInput { moveTo(middle, delayMillis = 100) }
        compose.waitForIdle()
        var stoppedScroll = 0
        compose.runOnIdle { stoppedScroll = list.firstVisibleItemScrollOffset }
        advanceSelectionScrollFrames()
        compose.runOnIdle {
            assertEquals("leaving the edge stops scrolling", stoppedScroll, list.firstVisibleItemScrollOffset)
        }
        compose.onRoot().performTouchInput { moveTo(top, delayMillis = 100) }
        advanceSelectionScrollFrames()
        compose.runOnIdle { assertTrue(list.firstVisibleItemScrollOffset < stoppedScroll) }
        compose.onRoot().performTouchInput { cancel() }
        compose.waitForIdle()
        compose.runOnIdle { stoppedScroll = list.firstVisibleItemScrollOffset }
        advanceSelectionScrollFrames()
        compose.runOnIdle {
            assertEquals("CANCEL stops the edge loop", stoppedScroll, list.firstVisibleItemScrollOffset)
            assertEquals(fixedEnd, selectionController.currentSelection()!!.end)
        }
    }

    @Test
    @Config(shadows = [ContinuousSelectionMagnifierShadow::class])
    fun clearingSelectionDuringEdgeScrollStopsQueuedScrolls() {
        currentChapter = 0
        chapterCount = 1
        show(verticalMargin = 64f)
        compose.waitUntil(15_000) { restored }
        var handle = Offset.Zero
        var bottom = Offset.Zero
        compose.runOnIdle {
            val view = visibleTextView()
            view.requestFocus()
            Selection.setSelection(view.text as Spannable, 2, 3)
        }
        compose.waitForIdle()
        compose.runOnIdle {
            val view = visibleTextView()
            val location = IntArray(2).also(view::getLocationOnScreen)
            val rootLocation = IntArray(2).also(composeRoot::getLocationOnScreen)
            val point = view.readerHandleCirclePosition(3, trailing = true)!!
            handle = Offset(location[0] - rootLocation[0] + point.x,
                location[1] - rootLocation[1] + point.y)
            bottom = Offset(handle.x, composeRoot.height - with(compose.density) { 64.dp.toPx() } - 2f)
        }
        compose.onRoot().performTouchInput { down(handle); moveTo(bottom, delayMillis = 100) }
        advanceSelectionScrollFrames()
        var stoppedScroll = 0
        compose.runOnIdle {
            assertTrue(list.firstVisibleItemScrollOffset > 0)
            selectionController.clear()
            stoppedScroll = list.firstVisibleItemScrollOffset
        }
        advanceSelectionScrollFrames()
        compose.runOnIdle {
            assertEquals(stoppedScroll, list.firstVisibleItemScrollOffset)
            assertNull(selectionController.currentSelection())
        }
        compose.onRoot().performTouchInput { cancel() }
    }

    @Test fun majorFontShrinkRecomposesAndRestoresTheOriginalChapterAnchor() {
        currentChapter = 0
        val font = mutableStateOf(36f)
        show(initialOffset = targetOffset, fontSize = font, mirrorProgress = true)
        compose.waitUntil(15_000) { restored }
        var character = 0
        compose.runOnIdle {
            val layout = visibleTextView().layout
            character = layout.getLineStart(layout.getLineForVertical(list.firstVisibleItemScrollOffset))
            font.value = 12f
        }
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals("still in the anchored chapter", 0, list.firstVisibleItemIndex)
            val layout = visibleTextView().layout
            val expected = layout.getLineTop(layout.getLineForOffset(character))
            assertTrue("expected=$expected actual=${list.firstVisibleItemScrollOffset}",
                kotlin.math.abs(expected - list.firstVisibleItemScrollOffset) <= 2)
        }
    }

    @Test fun clearingSelectionAndDetachingViewAlsoClearTheMenuSession() {
        currentChapter = 0
        chapterCount = 1
        val mounted = mutableStateOf(true)
        var clearCount = 0
        show(mounted = mounted, onSelectionCleared = { clearCount++ })
        compose.waitUntil(15_000) { restored }
        fun select() = compose.runOnIdle {
            val view = visibleTextView()
            view.requestFocus()
            Selection.setSelection(view.text as Spannable, 2, 12)
        }
        select()
        compose.waitForIdle()
        compose.runOnIdle {
            assertNotNull(selectionController.selection)
            assertNotNull(selectionController.currentSelection())
            // Menu actions must reject an empty live range even before the deferred
            // native selection-clear notification has reached Compose.
            Selection.removeSelection(visibleTextView().text as Spannable)
            assertNull(selectionController.currentSelection())
            selectionController.clear()
            assertNull(selectionController.selection)
            assertEquals(1, clearCount)
        }
        select()
        compose.waitForIdle()
        compose.runOnIdle { mounted.value = false }
        compose.waitForIdle()
        compose.runOnIdle {
            assertNull(selectionController.activeView)
            assertNull(selectionController.selection)
            assertEquals(2, clearCount)
        }
    }

    @Test fun crossChapterFlingKeepsItsSettledPositionAfterParentProgressFeedback() {
        currentChapter = 0
        chapterCount = 8
        val background = mutableStateOf(Color.WHITE)
        loadChapter = { chapter, _ -> "Chapter $chapter\n" + "Reading a short paragraph.\n".repeat(8) }
        show(background = background, mirrorProgress = true)
        compose.waitUntil(15_000) { restored }
        compose.onRoot().performTouchInput { swipeUp(durationMillis = 150) }
        compose.waitForIdle()
        var settled = 0 to 0
        compose.runOnIdle {
            settled = list.firstVisibleItemIndex to list.firstVisibleItemScrollOffset
            assertTrue("fling moved the reader: $settled", settled.first > 0 || settled.second > 0)
            background.value = Color.LTGRAY
        }
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals(settled, list.firstVisibleItemIndex to list.firstVisibleItemScrollOffset)
        }
    }

    @Test fun olderDecodeCannotReplaceNewerLayoutAfterSettingsChange() {
        currentChapter = 0
        chapterCount = 1
        val revision = mutableStateOf(0L)
        val loadVersion = java.util.concurrent.atomic.AtomicInteger(0)
        val oldStarted = java.util.concurrent.CountDownLatch(1)
        val releaseOld = java.util.concurrent.CountDownLatch(1)
        loadChapter = { _, _ ->
            val version = loadVersion.get()
            if (version == 1) {
                oldStarted.countDown()
                releaseOld.await(10, java.util.concurrent.TimeUnit.SECONDS)
            }
            "Version $version\n$text"
        }
        show(revision = revision)
        compose.waitUntil(15_000) { restored }
        compose.waitUntil(15_000) { visibleTextView().text.startsWith("Version 0") }
        try {
            compose.runOnIdle { loadVersion.set(1); revision.value = 1 }
            compose.waitForIdle()
            compose.waitUntil(10_000) { oldStarted.count == 0L }
            compose.runOnIdle { loadVersion.set(2); revision.value = 2 }
            compose.waitForIdle()
            releaseOld.countDown()
            compose.waitUntil(15_000) { visibleTextView().text.startsWith("Version 2") }
            compose.runOnIdle { assertFalse(visibleTextView().text.startsWith("Version 1")) }
        } finally { releaseOld.countDown() }
    }

    @Test fun coverAndBodyKeepTheSameReaderBackgroundWhileScrolling() {
        currentChapter = 0
        chapterCount = 2
        val background = mutableStateOf(Color.rgb(245, 239, 220))
        val bitmap = Bitmap.createBitmap(240, 60, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.RED)
            setHasAlpha(false)
        }
        val cover = SpannableStringBuilder("\uFFFC").apply {
            setSpan(ImageSpan(BitmapDrawable(null, bitmap).apply { setBounds(0, 0, 240, 60) }),
                0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            setSpan(EpubParser.CoverPageSpan(), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        loadChapter = { index, _ -> if (index == 0) cover else text }
        show(background = background)
        compose.waitUntil(15_000) { restored }
        fun capture(): Bitmap = compose.runOnIdle {
            Bitmap.createBitmap(composeRoot.width, composeRoot.height, Bitmap.Config.ARGB_8888)
                .also { composeRoot.draw(Canvas(it)) }
        }
        fun assertBackground() {
            val pixels = capture()
            assertEquals(background.value, pixels.getPixel(2, 2))
            assertEquals(background.value, pixels.getPixel(2, pixels.height - 3))
            pixels.recycle()
        }
        assertBackground()
        val coverPixels = capture()
        assertEquals("cover stays centered", Color.RED,
            coverPixels.getPixel(coverPixels.width / 2, coverPixels.height / 2))
        coverPixels.recycle()
        compose.runOnIdle { requests.tryEmit(ContinuousScrollRequest(1, characterOffset = 0)) }
        compose.waitUntil(15_000) { requests.value == null && list.firstVisibleItemIndex == 1 }
        assertBackground()
        compose.runOnIdle {
            background.value = Color.rgb(20, 42, 26)
            requests.tryEmit(ContinuousScrollRequest(0, characterOffset = 0))
        }
        compose.waitUntil(15_000) { requests.value == null && list.firstVisibleItemIndex == 0 }
        assertBackground()
    }

    @Test fun comicSettingKeepsTextMarginsAtChapterStartAndAfterScrolling() {
        currentChapter = 0
        chapterCount = 1
        val leftMargin = mutableStateOf(32f)
        show(comicMode = mutableStateOf(true), leftMargin = leftMargin)
        compose.waitUntil(15_000) { restored }

        fun descendants(view: View): Sequence<View> = sequence {
            yield(view)
            if (view is ViewGroup) for (i in 0 until view.childCount) yieldAll(descendants(view.getChildAt(i)))
        }
        fun assertMargins() {
            val textView = descendants(composeRoot.rootView).filterIsInstance<ContinuousSelectableTextView>().first()
            val rootLocation = IntArray(2).also(composeRoot::getLocationInWindow)
            val textLocation = IntArray(2).also(textView::getLocationInWindow)
            val left = with(compose.density) { leftMargin.value.dp.roundToPx() }
            val right = with(compose.density) { 16.dp.roundToPx() }
            val vertical = with(compose.density) { 20.dp.roundToPx() }
            assertEquals("text starts at the configured left margin", left, textLocation[0] - rootLocation[0])
            assertEquals("text ends at the configured right margin", right,
                composeRoot.width - left - textView.width)
            assertEquals("vertical margins belong to the viewport and survive scrolling",
                composeRoot.height - 2 * vertical, list.layoutInfo.viewportSize.height)
            assertEquals(0, list.layoutInfo.beforeContentPadding)
            assertEquals(0, list.layoutInfo.afterContentPadding)
        }

        compose.runOnIdle { assertMargins() }
        compose.runOnIdle { requests.tryEmit(ContinuousScrollRequest(0, characterOffset = targetOffset)) }
        compose.waitUntil(15_000) { list.firstVisibleItemScrollOffset > 1000 }
        compose.runOnIdle { assertMargins(); leftMargin.value = 48f }
        compose.waitForIdle()
        compose.runOnIdle { assertMargins() }
    }

    @Test fun mixedChapterUsesTextWidthWhileNeighboringComicImagesStayFullWidth() {
        currentChapter = 0
        chapterCount = 2
        val widths = ConcurrentHashMap<Int, Int>()
        loadChapter = { index, width ->
            if (width != null) widths[index] = width
            if (index == 0) SpannableStringBuilder(text).append(imageChapter(100)) else imageChapter(1200)
        }
        show(comicMode = mutableStateOf(true))
        val marginPx = with(compose.density) { 16.dp.roundToPx() }
        compose.waitUntil(15_000) { restored && widths.size == chapterCount }
        compose.runOnIdle {
            val viewportWidth = list.layoutInfo.viewportSize.width
            assertEquals(viewportWidth - 2 * marginPx, widths[0])
            assertEquals(viewportWidth, widths[1])
        }
        compose.runOnIdle { requests.tryEmit(ContinuousScrollRequest(1, characterOffset = 0)) }
        compose.waitUntil(15_000) { requests.value == null && list.firstVisibleItemIndex == 1 }
        compose.runOnIdle {
            assertEquals("pure image chapter restores the full-height viewport", composeRoot.height,
                list.layoutInfo.viewportSize.height)
        }
    }

    @Test fun comicImagePixelsUpdateWithoutMovingOrResizingThePage() {
        currentChapter = 0
        chapterCount = 1
        val settings = mutableStateOf(ReaderImageAdjustments())
        val bitmap = Bitmap.createBitmap(5, 3, Bitmap.Config.ARGB_8888).apply {
            for (y in 0..2) for (x in 0..4) {
                val shade = intArrayOf(80, 80, 120, 160, 160)[x]
                setPixel(x, y, Color.rgb(shade, shade, shade))
            }
        }
        val chapter = SpannableStringBuilder("\uFFFC").apply {
            setSpan(ImageSpan(BitmapDrawable(null, bitmap).apply { setBounds(0, 0, 5, 3) }, "comic.png"),
                0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        loadChapter = { _, _ -> chapter }
        show(comicMode = mutableStateOf(true), imageAdjustments = settings)
        compose.waitUntil(15_000) { restored }
        fun descendants(view: View): Sequence<View> = sequence {
            yield(view)
            if (view is ViewGroup) for (i in 0 until view.childCount) yieldAll(descendants(view.getChildAt(i)))
        }
        fun pixels(): Int {
            val image = descendants(composeRoot.rootView).filterIsInstance<ImageView>()
                .first { it.drawable is AdjustedReaderDrawable }
            val drawable = image.drawable
            val bounds = android.graphics.Rect(drawable.bounds)
            drawable.setBounds(0, 0, 5, 3)
            val output = Bitmap.createBitmap(5, 3, Bitmap.Config.ARGB_8888)
            drawable.draw(Canvas(output))
            drawable.bounds = bounds
            return Color.red(output.getPixel(1, 1))
        }
        var originalHeight = 0
        compose.runOnIdle {
            originalHeight = list.layoutInfo.visibleItemsInfo.first().size
            assertEquals(80, pixels())
            settings.value = ReaderImageAdjustments(brightness = 1f)
        }
        compose.runOnIdle { assertEquals(255, pixels()); settings.value = ReaderImageAdjustments(contrast = 2f) }
        compose.runOnIdle { assertTrue(pixels() < 80); settings.value = ReaderImageAdjustments(sharpen = 1f) }
        compose.waitUntil(10_000) { pixels() < 80 }
        compose.runOnIdle { settings.value = ReaderImageAdjustments() }
        compose.runOnIdle {
            assertEquals(80, pixels())
            assertEquals(originalHeight, list.layoutInfo.visibleItemsInfo.first().size)
            assertEquals(0, list.firstVisibleItemScrollOffset)
        }
    }

    @Test fun comicImagesFillViewportAndJoinWithinAndAcrossChapters() {
        currentChapter = 0
        val widths = ConcurrentHashMap<Int, Int>()
        loadChapter = { index, width ->
            if (width != null) widths[index] = width
            if (index == 0) imageChapter(80, 120) else imageChapter(90)
        }
        show(comicMode = mutableStateOf(true))
        compose.waitUntil(15_000) { restored && widths.size == chapterCount }
        compose.waitUntil(15_000) {
            list.layoutInfo.visibleItemsInfo.any { it.index == 1 } &&
                list.layoutInfo.viewportSize.height == composeRoot.height
        }
        compose.runOnIdle {
            val viewportWidth = list.layoutInfo.viewportSize.width
            assertTrue(viewportWidth > 0)
            assertTrue("decode images at full viewport width: $widths", widths.values.all { it == viewportWidth })
            assertEquals(0, list.layoutInfo.beforeContentPadding)
            assertEquals(0, list.layoutInfo.afterContentPadding)
            val first = list.layoutInfo.visibleItemsInfo.first { it.index == 0 }
            val second = list.layoutInfo.visibleItemsInfo.first { it.index == 1 }
            val expectedFirstHeight = (viewportWidth / 3f).roundToInt() + (viewportWidth / 2f).roundToInt()
            assertEquals("no gap between images or after the chapter", expectedFirstHeight, first.size)
            assertEquals("next chapter begins at the preceding image edge", first.offset + first.size, second.offset)
            assertEquals((viewportWidth * 90f / 240f).roundToInt(), second.size)
        }
    }

    @Test fun disablingComicModeRestoresReaderMarginsAndImageAndChapterSpacing() {
        currentChapter = 0
        val comicMode = mutableStateOf(true)
        val widths = ConcurrentHashMap<Int, Int>()
        loadChapter = { index, width ->
            if (width != null) widths[index] = width
            if (index == 0) imageChapter(80, 120) else imageChapter(90)
        }
        show(comicMode = comicMode)
        compose.waitUntil(15_000) { restored && widths.size == chapterCount }
        compose.runOnIdle { comicMode.value = false }
        val marginPx = with(compose.density) { 16.dp.roundToPx() }
        val imageGapPx = with(compose.density) { 8.dp.roundToPx() }
        val chapterGapPx = with(compose.density) { 28.dp.roundToPx() }
        val verticalMarginPx = with(compose.density) { 20.dp.roundToPx() }
        compose.waitForIdle()
        compose.runOnIdle {
            val contentWidth = list.layoutInfo.viewportSize.width - 2 * marginPx
            assertEquals("decode width after turning off comic mode: $widths", contentWidth, widths[0])
            assertEquals(composeRoot.height - 2 * verticalMarginPx, list.layoutInfo.viewportSize.height)
            assertEquals(0, list.layoutInfo.beforeContentPadding)
            assertEquals(0, list.layoutInfo.afterContentPadding)
            val first = list.layoutInfo.visibleItemsInfo.first { it.index == 0 }
            val expectedHeight = (contentWidth / 3f).roundToInt() + (contentWidth / 2f).roundToInt() +
                imageGapPx + chapterGapPx
            assertEquals(expectedHeight, first.size)
        }
    }

    @Test fun enteringContinuousReaderRestoresCharacterAfterWidthMeasurement() {
        show(initialOffset = targetOffset)
        compose.waitUntil(15_000) { restored }
        compose.runOnIdle {
            assertEquals(1, list.firstVisibleItemIndex)
            assertTrue("restored offset=${list.firstVisibleItemScrollOffset}", list.firstVisibleItemScrollOffset > 1000)
        }
    }

    @Test fun noteJumpUsesMeasuredTextInAnotherChapterAndCanRepeat() {
        show()
        compose.waitUntil(15_000) { restored }
        repeat(2) {
            compose.runOnIdle { requests.tryEmit(ContinuousScrollRequest(2, characterOffset = targetOffset)) }
            compose.waitUntil(15_000) { list.firstVisibleItemIndex == 2 && list.firstVisibleItemScrollOffset > 1000 }
            compose.runOnIdle { requests.tryEmit(ContinuousScrollRequest(1, characterOffset = 0)) }
            compose.waitUntil(15_000) { list.firstVisibleItemIndex == 1 && list.firstVisibleItemScrollOffset == 0 }
        }
    }

    @Test fun noteRequestedDuringReaderModeTransitionSurvivesMounting() {
        val mounted = mutableStateOf(false)
        show(mounted = mounted)
        compose.runOnIdle {
            requests.tryEmit(ContinuousScrollRequest(2, characterOffset = targetOffset))
            mounted.value = true
        }
        compose.waitForIdle()
        compose.waitUntil(15_000) { restored }
        compose.waitUntil(15_000) { list.firstVisibleItemIndex == 2 && list.firstVisibleItemScrollOffset > 1000 }
    }

    /**
     * 未解码章节必须预留占位高度。
     *
     * 空条目只有 28dp 时，一屏能塞下二十多章，滚动会连着跨过四五章：章节标题 / 进度与正文
     * 一起乱跳；可见区域全是空条目时列表总高度接近视口，还会表现为“完全滚不动”。
     */
    @Test fun unloadedChapterReservesPlaceholderHeightInsteadOfEmptyItem() {
        currentChapter = 0
        loadChapter = { _, _ -> null }
        show()
        compose.waitUntil(15_000) {
            list.layoutInfo.visibleItemsInfo.any { it.index == 0 && it.size > 200 }
        }
        compose.runOnIdle {
            val first = list.layoutInfo.visibleItemsInfo.first { it.index == 0 }
            assertTrue("placeholder height=${first.size}px", first.size > 200)
        }
    }

    /**
     * 解码失败不能写成空缓存：旧实现把空串写进 rawChapterTextCache，该章整场会话都渲染成
     * 空条目、且不再重试；现在失败不落缓存，后续重试成功要能正常显示。
     */
    @Test fun chapterThatFailsToDecodeIsRetriedAndRendersWhenItSucceedsLater() {
        val attempts = ConcurrentHashMap<Int, Int>()
        loadChapter = { index, _ ->
            val attempt = attempts.merge(index, 1, Int::plus) ?: 1
            if (attempt <= 2) null else text
        }
        show()
        compose.waitUntil(15_000) {
            list.layoutInfo.visibleItemsInfo.any { it.index == 1 && it.size > 2_000 }
        }
    }

    /**
     * 解码卡住时恢复流程仍要有界收尾（旧实现会一直等，表现为滚不动 / 卡死），
     * 而且迟到的解码结果仍要渲染出来。
     */
    @Test fun restoreFinishesWhileDecodeIsPendingAndLateTextStillLands() {
        val decodeGate = CompletableDeferred<Unit>()
        loadChapter = { _, _ ->
            runBlocking { decodeGate.await() }
            text
        }
        show()
        compose.waitUntil(15_000) { restored }
        compose.runOnIdle {
            val item = list.layoutInfo.visibleItemsInfo.firstOrNull { it.index == 1 }
            assertNotNull("restore should still land on the target chapter", item)
            assertTrue("chapter size=${item!!.size}", item.size < 2_000)
        }
        decodeGate.complete(Unit)
        compose.waitUntil(15_000) {
            list.layoutInfo.visibleItemsInfo.any { it.index == 1 && it.size > 2_000 }
        }
    }

    /**
     * 恢复定位期间用户自己拖动：必须让位给用户，不能把位置拉回目标章顶部。
     */
    @Test fun restoreYieldsToUserDragInsteadOfYankingBack() {
        val decodeGate = CompletableDeferred<Unit>()
        loadChapter = { _, _ ->
            runBlocking { decodeGate.await() }
            text
        }
        show()
        compose.onRoot().performTouchInput {
            down(Offset(centerX, centerY + 60f))
            repeat(4) { moveBy(Offset(0f, -30f), delayMillis = 60) }
            up()
        }
        compose.waitUntil(15_000) { restored }
        compose.runOnIdle {
            val index = list.firstVisibleItemIndex
            val offset = list.firstVisibleItemScrollOffset
            assertFalse(
                "restore must not snap back to the target chapter top (index=$index offset=$offset)",
                index == 1 && offset == 0
            )
        }
        decodeGate.complete(Unit)
    }

    /**
     * 正文在本条目之外补拉回来（恢复定位 / 相邻章节预加载）时，条目必须按缓存重新渲染。
     * 条目的 produceState 早就以“失败”结束，不会再跑，只有以缓存为准才能显示出来。
     */
    @Test fun chapterDecodedOutsideTheItemStillRenders() {
        var decodeAllowed = false
        // 其它章节用短正文，目标章用长正文：这样“占位 / 已渲染”能靠条目高度区分
        // （占位高度取全书典型高度，目标章一旦渲染出来会明显高得多）。
        val shortText = "Short chapter body."
        loadChapter = { index, _ ->
            when {
                index != 1 -> shortText
                decodeAllowed -> text
                else -> null
            }
        }
        show()
        compose.waitUntil(15_000) { restored }
        compose.runOnIdle {
            val item = list.layoutInfo.visibleItemsInfo.firstOrNull { it.index == 1 }
            assertTrue("chapter should still be a placeholder, size=${item?.size}", item == null || item.size < 2_000)
        }
        decodeAllowed = true
        compose.runOnIdle { requests.tryEmit(ContinuousScrollRequest(1, characterOffset = 0)) }
        compose.waitUntil(15_000) {
            list.layoutInfo.visibleItemsInfo.any { it.index == 1 && it.size > 2_000 }
        }
    }
}

/** Robolectric has no window Surface for PixelCopy. Only the magnifier window is
 * stubbed; touch dispatch, native word selection, handles and menu state stay real. */
@org.robolectric.annotation.Implements(android.widget.Magnifier::class)
class ContinuousSelectionMagnifierShadow {
    @org.robolectric.annotation.Implementation
    fun show(sourceX: Float, sourceY: Float) = Unit
    @org.robolectric.annotation.Implementation
    fun show(sourceX: Float, sourceY: Float, magnifierX: Float, magnifierY: Float) = Unit
    @org.robolectric.annotation.Implementation
    fun update() = Unit
    @org.robolectric.annotation.Implementation
    fun dismiss() = Unit
}
