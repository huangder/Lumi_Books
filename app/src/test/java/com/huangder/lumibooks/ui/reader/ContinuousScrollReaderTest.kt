package com.huangder.lumibooks.ui.reader

import android.app.Application
import android.graphics.Color
import android.graphics.Typeface
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import com.huangder.lumibooks.domain.model.ReaderTextAlignment
import com.huangder.lumibooks.ui.reader.engine.ResolvedReaderTypeface
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
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
    private var chapterCount = 3
    private var currentChapter = 1
    private var loadChapter: (Int, Int?) -> CharSequence? = { _, _ -> text }

    private fun show(
        initialOffset: Int? = null,
        mounted: androidx.compose.runtime.State<Boolean> = mutableStateOf(true),
        onChapterVisible: (Int, Float) -> Unit = { _, _ -> }
    ) {
        compose.setContent {
            Box(Modifier.fillMaxSize()) {
                if (mounted.value) ContinuousScrollReader(
                    chapterCount = chapterCount, currentChapter = currentChapter,
                    initialChapterFraction = 0f,
                    initialCharacterOffset = initialOffset,
                    fontSize = 18f, lineHeight = 1.5f, letterSpacingDp = 0f,
                    textAlignment = ReaderTextAlignment.NATURAL,
                    readerTypeface = ResolvedReaderTypeface(Typeface.DEFAULT, false),
                    textColor = Color.BLACK, backgroundColor = Color.WHITE,
                    backgroundImagePath = null, backgroundImageOpacity = 0f, backgroundImageBlurDp = 0f,
                    marginLeft = 16f, marginRight = 16f, marginTop = 20f, marginBottom = 20f,
                    paragraphSpacing = 0f, firstLineIndent = 0f, bionicReadingEnabled = false,
                    contentRevision = 0,
                    loadChapterText = { index, width -> loadChapter(index, width) },
                    onContentSizeChanged = { _, _ -> },
                    notes = emptyList(), searchHighlight = null, scrollRequests = requests,
                    onSearchHighlightFinished = {}, onMenuToggle = {}, onLinkClick = { _, _, _, _ -> },
                    onImageLongPress = { _, _ -> }, selectionController = ContinuousSelectionController(),
                    onSelectionChanging = {}, onSelection = { _, _ -> },
                    onChapterVisible = { index, fraction, _ -> onChapterVisible(index, fraction) },
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

    /**
     * 未解码章节必须预留占位高度。
     *
     * 空条目只有 28dp 时，一屏能塞下二十多章，滚动会连着跨过四五章：章节标题 / 进度与正文
     * 一起乱跳；可见区域全是空条目时列表总高度接近视口，还会表现为“完全滚不动”。
     */
    @Test fun unloadedChapterReservesPlaceholderHeightInsteadOfEmptyItem() {
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
