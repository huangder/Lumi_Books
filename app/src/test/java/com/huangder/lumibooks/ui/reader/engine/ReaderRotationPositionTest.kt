package com.huangder.lumibooks.ui.reader.engine

import android.app.Application
import android.os.Looper
import android.view.View
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class ReaderRotationPositionTest {
    private val text = (1..500).joinToString("\n") {
        "第${it}段：横竖屏往返应保留同一个字符位置，不能反复向前退页。"
    }

    @Test fun portraitLandscapePortraitReturnsToTheOriginalPage() = roundTrip(spread = false)

    @Test fun portraitSpreadPortraitReturnsToTheOriginalPage() = roundTrip(spread = true)

    private fun roundTrip(spread: Boolean) {
        val reader = createReader(spread)
        try {
            // Exercise several different page boundaries, rather than an accidentally aligned one.
            for (page in 3..8) {
                reader.jumpToChapter(1, page)
                awaitPage(reader)
                val original = reader.getCurrentLocation()
                val anchor = reader.getCurrentPageStartCharacterOffset()!!
                repeat(3) {
                    resize(reader, 900, 500)
                    assertContains(reader, anchor)
                    resize(reader, 500, 900)
                    assertEquals("Rotation changed the portrait page (spread=$spread)",
                        original, reader.getCurrentLocation())
                    assertEquals(anchor, reader.getCurrentPageStartCharacterOffset())
                }
            }
        } finally {
            reader.slotManager.destroy()
        }
    }

    @Test fun navigationInLandscapeReplacesThePreviousRotationAnchor() {
        val reader = createReader(spread = false)
        try {
            reader.jumpToChapter(1, 5)
            awaitPage(reader)
            resize(reader, 900, 500)
            val landscapePage = reader.getCurrentLocation()!!.second
            reader.jumpToChapter(1, landscapePage + 2)
            awaitPage(reader)
            val newAnchor = reader.getCurrentPageStartCharacterOffset()!!
            resize(reader, 500, 900)
            assertContains(reader, newAnchor)
            val portrait = reader.getCurrentLocation()
            resize(reader, 900, 500)
            assertEquals(landscapePage + 2, reader.getCurrentLocation()!!.second)
            resize(reader, 500, 900)
            assertEquals(portrait, reader.getCurrentLocation())
        } finally {
            reader.slotManager.destroy()
        }
    }

    @Test fun turningAPageInLandscapeReplacesThePreviousRotationAnchor() {
        val reader = createReader(spread = false)
        try {
            reader.jumpToChapter(1, 5)
            awaitPage(reader)
            resize(reader, 900, 500)
            reader.slotManager.backgroundPreparationEnabled = true
            val deadline = System.nanoTime() + 10_000_000_000L
            while (!reader.slotManager.getNextSlot().isLoaded && System.nanoTime() < deadline) {
                shadowOf(Looper.getMainLooper()).idle()
                Thread.sleep(10)
            }
            assertTrue(reader.slotManager.getNextSlot().isLoaded)
            reader.slotManager.shiftForward()
            val landscape = reader.getCurrentLocation()
            val newAnchor = reader.getCurrentPageStartCharacterOffset()!!
            resize(reader, 500, 900)
            assertContains(reader, newAnchor)
            resize(reader, 900, 500)
            assertEquals(landscape, reader.getCurrentLocation())
        } finally {
            reader.slotManager.destroy()
        }
    }

    @Test fun resizingAgainBeforePaginationCompletesKeepsTheOriginalAnchor() {
        var pending: CompletableDeferred<CharSequence?>? = null
        val reader = createReader(spread = false) { pending?.await() ?: text }
        try {
            reader.jumpToChapter(1, 5)
            awaitPage(reader)
            val original = reader.getCurrentLocation()
            resize(reader, 900, 500)
            pending = CompletableDeferred()
            reader.slotManager.clearContentCache()
            measure(reader, 860, 540)
            shadowOf(Looper.getMainLooper()).idle()
            assertFalse(reader.slotManager.getCurSlot().isLoaded)
            measure(reader, 500, 900)
            pending.complete(text)
            awaitPage(reader)
            assertEquals(original, reader.getCurrentLocation())
        } finally {
            reader.slotManager.destroy()
        }
    }

    private fun createReader(spread: Boolean, provider: suspend (Int) -> CharSequence? = { text }) =
        ReadView(RuntimeEnvironment.getApplication()).apply {
            setContentProvider(provider)
            slotManager.backgroundPreparationEnabled = false
            configure(24f, "day", 3, 1, 0,
                marginLeftDp = 20f, marginRightDp = 20f, marginTopDp = 20f, marginBottomDp = 20f,
                twoPageSpread = spread, width = 500, height = 900)
            resize(this, 500, 900)
        }

    private fun resize(reader: ReadView, width: Int, height: Int) {
        measure(reader, width, height)
        awaitPage(reader)
    }

    private fun measure(reader: ReadView, width: Int, height: Int) {
        reader.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
        reader.layout(0, 0, width, height)
    }

    private fun assertContains(reader: ReadView, anchor: Int) {
        val slot = reader.slotManager.getCurSlot()
        val page = reader.layoutEngine.getChapterLayout(slot.chapterIndex)!!.pages[slot.pageIndex]
        assertTrue("The new page lost character $anchor", anchor in page.startCharOffset until page.endCharOffset)
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
