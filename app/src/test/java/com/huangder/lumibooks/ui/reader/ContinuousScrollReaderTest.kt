package com.huangder.lumibooks.ui.reader

import android.app.Application
import android.graphics.Color
import android.graphics.Typeface
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import com.huangder.lumibooks.domain.model.ReaderTextAlignment
import com.huangder.lumibooks.ui.reader.engine.ResolvedReaderTypeface
import kotlinx.coroutines.flow.MutableStateFlow
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

    private fun show(initialOffset: Int? = null, mounted: androidx.compose.runtime.State<Boolean> = mutableStateOf(true)) {
        compose.setContent {
            Box(Modifier.fillMaxSize()) {
                if (mounted.value) ContinuousScrollReader(
                    chapterCount = 3, currentChapter = 1, initialChapterFraction = 0f,
                    initialCharacterOffset = initialOffset,
                    fontSize = 18f, lineHeight = 1.5f, letterSpacingDp = 0f,
                    textAlignment = ReaderTextAlignment.NATURAL,
                    readerTypeface = ResolvedReaderTypeface(Typeface.DEFAULT, false),
                    textColor = Color.BLACK, backgroundColor = Color.WHITE,
                    backgroundImagePath = null, backgroundImageOpacity = 0f, backgroundImageBlurDp = 0f,
                    marginLeft = 16f, marginRight = 16f, marginTop = 20f, marginBottom = 20f,
                    paragraphSpacing = 0f, firstLineIndent = 0f, bionicReadingEnabled = false,
                    contentRevision = 0, loadChapterText = { _, _ -> text }, onContentSizeChanged = { _, _ -> },
                    notes = emptyList(), searchHighlight = null, scrollRequests = requests,
                    onSearchHighlightFinished = {}, onMenuToggle = {}, onLinkClick = { _, _, _, _ -> },
                    onImageLongPress = { _, _ -> }, selectionController = ContinuousSelectionController(),
                    onSelectionChanging = {}, onSelection = { _, _ -> }, onChapterVisible = { _, _, _ -> },
                    onRestoreComplete = { restored = true }, onSentenceDoubleTap = { _, _ -> },
                    ttsSentenceJumpEnabled = false, listState = list
                )
            }
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
}
