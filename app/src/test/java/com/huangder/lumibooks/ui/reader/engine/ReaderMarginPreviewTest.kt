package com.huangder.lumibooks.ui.reader.engine

import android.app.Application
import android.os.Looper
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode
import java.util.concurrent.atomic.AtomicReference

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class ReaderMarginPreviewTest {
    @Test fun continuousMarginChangesKeepTextAndChapterUntilPaginationIsReady() {
        verifyPreview(twoPageSpread = false)
    }

    @Test fun continuousSpreadMarginChangesKeepBothPagesUntilPaginationIsReady() {
        verifyPreview(twoPageSpread = true)
    }

    private fun verifyPreview(twoPageSpread: Boolean) {
        val reader = ReadView(RuntimeEnvironment.getApplication())
        val text = (1..300).joinToString("\n") { "第${it}段：调节页边距时正文保持可见，并保留当前阅读位置。" }
        val pending = AtomicReference<CompletableDeferred<CharSequence?>?>(null)
        reader.setContentProvider { pending.get()?.await() ?: text }
        reader.slotManager.backgroundPreparationEnabled = false
        fun configure(left: Float, right: Float, top: Float, bottom: Float, chapter: Int = 0) {
            reader.configure(
                fontSizePx = 24f, theme = "day", chapterCount = 2,
                startChapter = chapter, startPage = if (chapter == 1) 3 else 0,
                marginLeftDp = left, marginRightDp = right,
                marginTopDp = top, marginBottomDp = bottom,
                twoPageSpread = twoPageSpread,
                width = if (twoPageSpread) 1200 else 400, height = 800
            )
        }
        try {
            configure(20f, 20f, 20f, 20f, chapter = 1)
            awaitPage(reader)
            val slot = reader.slotManager.getCurSlot()
            val visibleText = slot.contentView.textView.text.toString()
            val rightText = slot.rightContentView?.textView?.text.toString()
            val anchor = slot.contentView.chapterStartOffset
            assertTrue(visibleText.isNotBlank())
            if (twoPageSpread) assertTrue(rightText.isNotBlank())
            assertTrue(anchor > 0)

            val replacement = CompletableDeferred<CharSequence?>()
            pending.set(replacement)
            for (margins in listOf(
                listOf(40f, 20f, 20f, 20f),
                listOf(40f, 40f, 20f, 20f),
                listOf(40f, 40f, 40f, 20f),
                listOf(40f, 40f, 40f, 40f)
            )) {
                // Simulate an older Compose position snapshot during live updates.
                configure(margins[0], margins[1], margins[2], margins[3])
                shadowOf(Looper.getMainLooper()).idle()
                assertFalse(slot.isLoaded)
                assertEquals(1, slot.chapterIndex)
                assertEquals(anchor, reader.slotManager.pendingStartCharOffset)
                assertEquals(visibleText, slot.contentView.textView.text.toString())
                if (twoPageSpread) assertEquals(rightText, slot.rightContentView?.textView?.text.toString())
            }

            replacement.complete(text)
            awaitPage(reader)
            assertEquals(1, slot.chapterIndex)
            val page = reader.layoutEngine.getChapterLayout(1)!!.pages[slot.pageIndex]
            assertTrue("Repagination lost the original character anchor",
                anchor in page.startCharOffset until page.endCharOffset)
            assertEquals(text.substring(page.startCharOffset, page.endCharOffset).trimEnd(),
                slot.contentView.textView.text.toString().trimEnd())

            // Explicit navigation still clears the old page while loading another chapter.
            pending.set(CompletableDeferred())
            reader.slotManager.clearContentCache()
            reader.slotManager.initialize(0, 0)
            assertTrue(slot.contentView.textView.text.isNullOrEmpty())
        } finally {
            reader.slotManager.destroy()
        }
    }

    private fun awaitPage(reader: ReadView) {
        val deadline = System.nanoTime() + 10_000_000_000L
        while (!reader.slotManager.getCurSlot().isLoaded && System.nanoTime() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(10)
        }
        assertTrue("Current page did not finish loading", reader.slotManager.getCurSlot().isLoaded)
    }
}
