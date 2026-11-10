package com.huangder.lumibooks.ui.animation

import androidx.compose.runtime.MonotonicFrameClock
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** JVM tests cannot allocate Android bitmaps; the fade only needs a non-null capture. */
private object FakeImage : ImageBitmap {
    override val width = 4
    override val height = 4
    override val config = androidx.compose.ui.graphics.ImageBitmapConfig.Argb8888
    override val hasAlpha = false
    override val colorSpace = androidx.compose.ui.graphics.colorspace.ColorSpaces.Srgb
    override fun readPixels(buffer: IntArray, startX: Int, startY: Int, width: Int, height: Int,
        bufferOffset: Int, stride: Int) = Unit
    override fun prepareToDraw() = Unit
}

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ReaderFadeStateTest {
    private fun TestScope.fade() = ReaderFadeState(
        CoroutineScope(coroutineContext + object : MonotonicFrameClock {
            override suspend fun <R> withFrameNanos(onFrame: (Long) -> R): R {
                delay(16)
                return onFrame(testScheduler.currentTime * 1_000_000L)
            }
        })
    )

    @Test fun `the reader opens only under an opaque cover and the cover clears after its first page`() = runTest {
        val state = fade()
        var navigated = false
        state.open("a", Color.White, null) { navigated = true }
        assertTrue(state.visible)
        advanceTimeBy(ReaderFadeState.FADE_IN_MS / 2L)
        assertFalse(navigated)
        advanceTimeBy(ReaderFadeState.FADE_IN_MS / 2L + 60L)
        assertTrue(navigated)
        assertEquals(1f, state.alpha.value, 0f)
        // Still loading: the cover stays.
        advanceTimeBy(500)
        assertTrue(state.visible)
        state.markReaderDrawn("a")
        advanceUntilIdle()
        assertFalse(state.visible)
        assertTrue(state.owns("a"))
    }

    @Test fun `a page drawn before the cover is opaque clears it right after`() = runTest {
        val state = fade()
        state.open("a", Color.White, null) { state.markReaderDrawn("a") }
        advanceUntilIdle()
        assertFalse(state.visible)
    }

    @Test fun `closing fades a capture out after popping, or the paper without one`() = runTest {
        val state = fade()
        state.open("a", Color.White, null) { }
        state.markReaderDrawn("a")
        advanceUntilIdle()
        var popped = false
        state.close(FakeImage) { popped = true }
        assertTrue(state.visible)
        assertFalse(state.owns("a"))
        advanceTimeBy(40)
        assertTrue(popped)
        advanceUntilIdle()
        assertFalse(state.visible)

        var poppedPlain = false
        state.close(null) { poppedPlain = true }
        // Without a capture the paper still covers the cut to the library and then fades.
        assertTrue(state.visible)
        assertEquals(null, state.image)
        advanceTimeBy(40)
        assertTrue(poppedPlain)
        assertTrue(state.visible)
        advanceUntilIdle()
        assertFalse(state.visible)
    }

    @Test fun `text appearing over plain paper fades in more slowly than over its stored page`() = runTest {
        suspend fun TestScope.revealTime(page: ImageBitmap?): Long {
            val state = fade()
            state.open("a", Color.White, page) { }
            advanceUntilIdle()
            val start = testScheduler.currentTime
            state.markReaderDrawn("a")
            advanceUntilIdle()
            assertFalse(state.visible)
            return testScheduler.currentTime - start
        }
        val overPage = revealTime(FakeImage)
        val overPaper = revealTime(null)
        assertTrue("paper reveal $overPaper should exceed page reveal $overPage", overPaper > overPage)
        assertTrue(overPaper >= ReaderFadeState.REVEAL_TEXT_MS)
    }

    @Test fun `a capture-less close fades in the reader's latest paper`() = runTest {
        val state = fade()
        state.open("a", Color.White, null) { }
        state.markReaderDrawn("a")
        advanceUntilIdle()
        state.updatePaper("a", Color.Black)
        state.updatePaper("b", Color.Red)
        assertEquals(Color.Black, state.paperColor)
    }
}
